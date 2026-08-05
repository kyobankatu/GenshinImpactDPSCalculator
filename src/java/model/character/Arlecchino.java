package model.character;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import mechanics.buff.Buff;
import mechanics.data.TalentDataManager;
import mechanics.data.TalentDataSource;
import mechanics.energy.ParticleType;
import model.entity.ArtifactSet;
import model.entity.Character;
import model.entity.SimulatorInitializedCharacterEffect;
import model.entity.SnapshotAwareCharacterEffect;
import model.entity.SwitchAwareCharacter;
import model.entity.Weapon;
import model.stats.StatsContainer;
import model.type.ActionType;
import model.type.CharacterId;
import model.type.Element;
import model.type.ICDTag;
import model.type.ICDType;
import model.type.StatType;
import simulation.CombatSimulator;
import simulation.action.AttackAction;
import simulation.action.CharacterActionKey;
import simulation.action.CharacterActionRequest;
import simulation.action.SkillActionMode;
import simulation.event.SimpleTimerEvent;

/**
 * Arlecchino's stationary single-target Masque of the Red Death slice.
 *
 * <p>Level-90 data, six polearm Normal Attacks, Charged and high Plunging
 * Attacks, All Is Ash, Blood-Debt Directive, locally tracked deterministic
 * Bond of Life percentage, Balemoon Rising, particles, A1, the permanent
 * combat Pyro bonus, and representable offensive C1-C6 behavior follow pinned
 * gcsim {@code ef41805d}. Mutable Bond, Directive, gate, generation, and
 * pending-event state is owner-bound and rollback-safe.</p>
 *
 * <p>Actual player HP, healing, damage intake, resistance and interruption
 * resistance effects, movement and geometry, multi-target or random target
 * selection, target-death collection, external Bond of Life integrations,
 * hitlag, stamina, low Plunge, and optional plunge collision selection are
 * excluded. Fixed-target collection and high-Plunge landing damage remain
 * deterministic.</p>
 */
public final class Arlecchino extends Character implements
        SimulatorInitializedCharacterEffect,
        SnapshotAwareCharacterEffect,
        SwitchAwareCharacter {
    private static final double FRAME = 1.0 / 60.0;
    private static final double EPSILON = 1e-9;
    private static final int[][] NORMAL_HIT_FRAMES = {
        { 11 }, { 16 }, { 17 }, { 24, 35 }, { 21 }, { 44 }
    };
    private static final int[] NORMAL_DURATIONS = {
        24, 31, 39, 55, 43, 59
    };
    private static final double[][] NORMAL_T9 = {
        { 0.872681 },
        { 0.957290 },
        { 1.201274 },
        { 0.682434, 0.682434 },
        { 1.285709 },
        { 1.568577 }
    };
    private static final double[][] NORMAL_C3 = {
        { 1.071520 },
        { 1.175407 },
        { 1.474982 },
        { 0.837925, 0.837925 },
        { 1.578656 },
        { 1.925974 }
    };

    private CombatSimulator initializedSimulator;
    private int normalAttackStep;
    private long skillGeneration;
    private long burstGeneration;
    private long directiveGeneration;
    private double bondRatio;
    private double skillDebtRatio;
    private double debtLimitExpirationTime = Double.NEGATIVE_INFINITY;
    private boolean directiveActive;
    private int directiveLevel;
    private double directiveExpirationTime = Double.NEGATIVE_INFINITY;
    private double nextParticleAllowedTime = Double.NEGATIVE_INFINITY;
    private double nextBondConsumeAllowedTime = Double.NEGATIVE_INFINITY;
    private double nextC2AllowedTime = Double.NEGATIVE_INFINITY;
    private double nextC4AllowedTime = Double.NEGATIVE_INFINITY;
    private double nextC6AllowedTime = Double.NEGATIVE_INFINITY;
    private double c6ExpirationTime = Double.NEGATIVE_INFINITY;
    private boolean resolvingSkillCleave;
    private boolean resolvingMasqueNormal;
    private long resolvingGeneration;
    private List<PendingEvent> pendingEvents = new ArrayList<>();

    /** Constructs repository-default C6 Arlecchino. */
    public Arlecchino(Weapon weapon, ArtifactSet artifacts) {
        this(weapon, artifacts, TalentDataManager.getInstance(), 6);
    }

    /** Constructs Arlecchino at an explicit constellation. */
    public Arlecchino(
            Weapon weapon,
            ArtifactSet artifacts,
            int constellation) {
        this(
                weapon,
                artifacts,
                TalentDataManager.getInstance(),
                constellation);
    }

    /**
     * Constructs Arlecchino with injectable static talent data.
     *
     * @param weapon equipped polearm, or {@code null}
     * @param artifacts equipped artifact set, or {@code null}
     * @param talentData static character-data source
     * @param constellation constellation level in {@code [0, 6]}
     */
    public Arlecchino(
            Weapon weapon,
            ArtifactSet artifacts,
            TalentDataSource talentData,
            int constellation) {
        super(talentData);
        if (constellation < 0 || constellation > 6) {
            throw new IllegalArgumentException(
                    "Arlecchino constellation must be between 0 and 6");
        }
        name = "Arlecchino";
        characterId = CharacterId.ARLECCHINO;
        element = Element.PYRO;
        this.weapon = weapon;
        this.artifacts = new ArtifactSet[] { artifacts };
        this.constellation = constellation;
        baseStats.set(StatType.BASE_HP,
                getTalentValue("Base HP", 13103.0));
        baseStats.set(StatType.BASE_ATK,
                getTalentValue("Base ATK", 342.0));
        baseStats.set(StatType.BASE_DEF,
                getTalentValue("Base DEF", 765.0));
        baseStats.add(StatType.CRIT_DMG,
                getTalentValue("Ascension CRIT DMG", 0.384));
        setSkillCD(getTalentValue("Skill Cooldown", 30.0));
        setBurstCD(getTalentValue("Burst Cooldown", 15.0));
    }

    /** Binds accepted-damage callbacks to one owning simulator. */
    @Override
    public void initializeForSimulator(CombatSimulator simulator) {
        if (simulator == null) {
            throw new IllegalArgumentException(
                    "Arlecchino simulator is required");
        }
        if (!simulator.getPartyMembers().contains(this)) {
            throw new IllegalArgumentException(
                    "Arlecchino must belong to the simulator party");
        }
        if (initializedSimulator != null
                && initializedSimulator != simulator) {
            throw new IllegalStateException(
                    "Arlecchino cannot be reused across simulators");
        }
        if (initializedSimulator == simulator) {
            return;
        }
        initializedSimulator = simulator;
        simulator.addDamageListener(this::handleAcceptedDamage);
    }

    /** Captures combo, Bond, Directive, gate, generation, and event state. */
    @Override
    public State captureCharacterState() {
        return new ArlecchinoState(
                this,
                normalAttackStep,
                skillGeneration,
                burstGeneration,
                directiveGeneration,
                bondRatio,
                skillDebtRatio,
                debtLimitExpirationTime,
                directiveActive,
                directiveLevel,
                directiveExpirationTime,
                nextParticleAllowedTime,
                nextBondConsumeAllowedTime,
                nextC2AllowedTime,
                nextC4AllowedTime,
                nextC6AllowedTime,
                c6ExpirationTime,
                pendingEvents);
    }

    /** Accepts state captured from this exact Arlecchino instance only. */
    @Override
    public boolean acceptsCharacterState(State state) {
        return state instanceof ArlecchinoState
                && ((ArlecchinoState) state).owner == this;
    }

    /** Restores owner state and reconstructs each unresolved event once. */
    @Override
    public void restoreCharacterState(
            State state,
            CombatSimulator simulator) {
        if (!acceptsCharacterState(state)) {
            throw new IllegalArgumentException(
                    "Unexpected Arlecchino state");
        }
        initializeForSimulator(simulator);
        ArlecchinoState restored = (ArlecchinoState) state;
        normalAttackStep = restored.normalAttackStep;
        skillGeneration = restored.skillGeneration;
        burstGeneration = restored.burstGeneration;
        directiveGeneration = restored.directiveGeneration;
        bondRatio = restored.bondRatio;
        skillDebtRatio = restored.skillDebtRatio;
        debtLimitExpirationTime = restored.debtLimitExpirationTime;
        directiveActive = restored.directiveActive;
        directiveLevel = restored.directiveLevel;
        directiveExpirationTime = restored.directiveExpirationTime;
        nextParticleAllowedTime = restored.nextParticleAllowedTime;
        nextBondConsumeAllowedTime =
                restored.nextBondConsumeAllowedTime;
        nextC2AllowedTime = restored.nextC2AllowedTime;
        nextC4AllowedTime = restored.nextC4AllowedTime;
        nextC6AllowedTime = restored.nextC6AllowedTime;
        c6ExpirationTime = restored.c6ExpirationTime;
        pendingEvents = copyEvents(restored.pendingEvents);
        resolvingSkillCleave = false;
        resolvingMasqueNormal = false;
        resolvingGeneration = 0L;
        double currentTime = simulator.getCurrentTime();
        pendingEvents.removeIf(event ->
                event.time < currentTime - EPSILON);
        for (PendingEvent event : new ArrayList<>(pendingEvents)) {
            scheduleEvent(simulator, event);
        }
    }

    /** Returns Arlecchino's 60-Energy Burst cost. */
    @Override
    public double getEnergyCost() {
        return getTalentValue("Energy Cost", 60.0);
    }

    /** Applies The Balemoon Alone May Know's permanent combat Pyro bonus. */
    @Override
    public void applyPassive(StatsContainer stats) {
        stats.add(
                StatType.PYRO_DMG_BONUS,
                getTalentValue("Combat Pyro DMG Bonus", 0.40));
    }

    /** Resets the polearm Normal string on switch-out. */
    @Override
    public void onSwitchOut(CombatSimulator simulator) {
        normalAttackStep = 0;
    }

    /** Resets the polearm Normal string on switch-in. */
    @Override
    public void onSwitchIn(CombatSimulator simulator) {
        normalAttackStep = 0;
    }

    /** Returns the locally tracked Bond of Life as a Max-HP ratio. */
    public double getBondOfLifeRatio() {
        return bondRatio;
    }

    /** Returns whether the fixed target owns an unexpired Directive. */
    public boolean hasActiveDirective(double currentTime) {
        return directiveActive
                && currentTime + EPSILON < directiveExpirationTime;
    }

    /** Returns the fixed target's current Directive level, or zero. */
    public int getDirectiveLevel(double currentTime) {
        return hasActiveDirective(currentTime) ? directiveLevel : 0;
    }

    /** Returns Bond collected within the current 145% Skill cap window. */
    public double getSkillDebtRatio() {
        return skillDebtRatio;
    }

    /** Returns the next timestamp eligible for a Normal Bond consumption. */
    public double getNextBondConsumeAllowedTime() {
        return nextBondConsumeAllowedTime;
    }

    /** Returns the next timestamp eligible for C2 Bloodfire. */
    public double getNextC2AllowedTime() {
        return nextC2AllowedTime;
    }

    /** Returns the next timestamp eligible for C4 Energy and cooldown. */
    public double getNextC4AllowedTime() {
        return nextC4AllowedTime;
    }

    /** Returns the active C6 Normal/Burst CRIT window expiration. */
    public double getC6ExpirationTime() {
        return c6ExpirationTime;
    }

    /** Returns the number of unresolved Arlecchino-owned events. */
    public int getPendingEventCount() {
        return pendingEvents.size();
    }

    /** Reports that actual player HP, healing, and damage intake are excluded. */
    public boolean isPlayerHpHealingRepresented() {
        return false;
    }

    /** Reports that A4, C1, and C2 defensive effects are excluded. */
    public boolean isDefensiveEffectsRepresented() {
        return false;
    }

    /** Reports that movement and target geometry are excluded. */
    public boolean isMovementGeometryRepresented() {
        return false;
    }

    /** Reports that multi-target and random selection are excluded. */
    public boolean isMultiTargetRandomnessRepresented() {
        return false;
    }

    /** Reports that weapon, artifact, and foreign Bond routes are excluded. */
    public boolean isExternalBondOfLifeRepresented() {
        return false;
    }

    /** Reports that stamina and hitlag are excluded. */
    public boolean isStaminaHitlagRepresented() {
        return false;
    }

    /** Reports that low Plunge and optional collision selection are excluded. */
    public boolean isLowPlungeRepresented() {
        return false;
    }

    /** Reports that A1 target-death collection is unavailable. */
    public boolean isTargetDeathCollectionRepresented() {
        return false;
    }

    /** Dispatches Arlecchino's represented typed action set. */
    @Override
    public void onAction(
            CharacterActionRequest request,
            CombatSimulator simulator) {
        if (request == null) {
            throw new IllegalArgumentException(
                    "Arlecchino action is required");
        }
        initializeForSimulator(simulator);
        if (request.getKey() == CharacterActionKey.SKILL
                && request.getSkillMode() != SkillActionMode.PRESS) {
            throw new IllegalArgumentException(
                    "Arlecchino only supports Press Skill in this slice");
        }
        if (request.getKey() != CharacterActionKey.NORMAL) {
            normalAttackStep = 0;
        }
        switch (request.getKey()) {
            case NORMAL:
                normalAttack(simulator);
                break;
            case CHARGE:
                chargedAttack(simulator);
                break;
            case PLUNGE:
                highPlunge(simulator);
                break;
            case SKILL:
                allIsAsh(simulator);
                break;
            case BURST:
                balemoonRising(simulator);
                break;
            default:
                throw new IllegalArgumentException(
                        "Unsupported action for Arlecchino: "
                                + request.getKey());
        }
    }

    private void normalAttack(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        int step = normalAttackStep;
        for (int hit = 0;
                hit < NORMAL_HIT_FRAMES[step].length;
                hit++) {
            queueEvent(simulator, new PendingEvent(
                    castTime + NORMAL_HIT_FRAMES[step][hit] * FRAME,
                    EventKind.NORMAL,
                    0L,
                    step,
                    hit));
        }
        normalAttackStep = (normalAttackStep + 1)
                % NORMAL_HIT_FRAMES.length;
        simulator.advanceTime(NORMAL_DURATIONS[step] * FRAME);
    }

    /** Queues fixed-target Directive collection before the Charged hit. */
    private void chargedAttack(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        queueEvent(simulator, new PendingEvent(
                castTime + 12.0 * FRAME,
                EventKind.COLLECT_DIRECTIVE,
                0L,
                0,
                0));
        queueEvent(simulator, new PendingEvent(
                castTime + 37.0 * FRAME,
                EventKind.CHARGED,
                0L,
                0,
                0));
        simulator.advanceTime(60.0 * FRAME);
    }

    /** Queues only the supported high-Plunge landing hit. */
    private void highPlunge(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        queueEvent(simulator, new PendingEvent(
                castTime + 48.0 * FRAME,
                EventKind.HIGH_PLUNGE,
                0L,
                0,
                0));
        simulator.advanceTime(81.0 * FRAME);
    }

    /** Queues both Skill hits, cooldown, C6, and Directive cap state. */
    private void allIsAsh(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        long generation = ++skillGeneration;
        queueEvent(simulator, new PendingEvent(
                castTime + 16.0 * FRAME,
                EventKind.SKILL_COOLDOWN,
                generation,
                0,
                0));
        queueEvent(simulator, new PendingEvent(
                castTime + 17.0 * FRAME,
                EventKind.SKILL_SPIKE,
                generation,
                0,
                0));
        queueEvent(simulator, new PendingEvent(
                castTime + 38.0 * FRAME,
                EventKind.SKILL_CLEAVE,
                generation,
                0,
                0));
        queueEvent(simulator, new PendingEvent(
                castTime + 38.0 * FRAME,
                EventKind.C6_SKILL,
                generation,
                0,
                0));
        queueEvent(simulator, new PendingEvent(
                castTime + 39.0 * FRAME,
                EventKind.DIRECTIVE_LIMIT,
                generation,
                0,
                0));
        simulator.advanceTime(77.0 * FRAME);
    }

    /** Queues Burst collection, Energy, damage, Skill reset, and local Bond reset. */
    private void balemoonRising(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        long generation = ++burstGeneration;
        markBurstCooldownUsed(
                castTime,
                simulator.getApplicableBuffs(this));
        queueEvent(simulator, new PendingEvent(
                castTime + 12.0 * FRAME,
                EventKind.BURST_ENERGY,
                generation,
                0,
                0));
        queueEvent(simulator, new PendingEvent(
                castTime + 22.0 * FRAME,
                EventKind.COLLECT_DIRECTIVE,
                generation,
                0,
                0));
        queueEvent(simulator, new PendingEvent(
                castTime + 107.0 * FRAME,
                EventKind.SKILL_RESET,
                generation,
                0,
                0));
        queueEvent(simulator, new PendingEvent(
                castTime + 110.0 * FRAME,
                EventKind.BURST,
                generation,
                0,
                0));
        queueEvent(simulator, new PendingEvent(
                castTime + 123.0 * FRAME,
                EventKind.BOND_RESET,
                generation,
                0,
                0));
        simulator.advanceTime(146.0 * FRAME);
    }

    /** Resolves one owner event after checking its source generation. */
    private void resolveEvent(
            CombatSimulator simulator,
            PendingEvent event) {
        switch (event.kind) {
            case NORMAL:
                resolveNormal(simulator, event);
                break;
            case CHARGED:
                resolveCharged(simulator, event);
                break;
            case HIGH_PLUNGE:
                resolveHighPlunge(simulator, event);
                break;
            case SKILL_COOLDOWN:
                if (event.generation == skillGeneration) {
                    markSkillUsed(
                            event.time,
                            simulator.getApplicableBuffs(this));
                }
                break;
            case SKILL_SPIKE:
                if (event.generation == skillGeneration) {
                    resolveSkillHit(simulator, event, false);
                }
                break;
            case SKILL_CLEAVE:
                if (event.generation == skillGeneration) {
                    resolveSkillHit(simulator, event, true);
                }
                break;
            case DIRECTIVE_LIMIT:
                if (event.generation == skillGeneration) {
                    skillDebtRatio = 0.0;
                    debtLimitExpirationTime = event.time
                            + getTalentValue(
                                    "Directive Cap Duration", 35.0);
                }
                break;
            case DIRECTIVE_TICK:
                if (event.generation == directiveGeneration
                        && hasActiveDirective(event.time)) {
                    resolveDirectiveTick(simulator, event);
                }
                break;
            case DIRECTIVE_UPGRADE:
                if (event.generation == directiveGeneration
                        && hasActiveDirective(event.time)
                        && directiveLevel == 1) {
                    directiveLevel = 2;
                }
                break;
            case DIRECTIVE_EXPIRE:
                if (event.generation == directiveGeneration) {
                    directiveActive = false;
                    directiveLevel = 0;
                    directiveGeneration++;
                }
                break;
            case COLLECT_DIRECTIVE:
                collectDirective(simulator, event.time);
                break;
            case PARTICLE:
                if (event.generation == skillGeneration) {
                    simulator.getEnergyDistributor().distributeParticles(
                            Element.PYRO,
                            event.index,
                            ParticleType.PARTICLE);
                }
                break;
            case C2_DAMAGE:
                resolveC2(simulator, event);
                break;
            case C6_SKILL:
                if (event.generation == skillGeneration) {
                    activateC6(event.time);
                }
                break;
            case BURST_ENERGY:
                if (event.generation == burstGeneration) {
                    spendBurstEnergy(event.time);
                }
                break;
            case SKILL_RESET:
                if (event.generation == burstGeneration) {
                    resetSkillCooldown(event.time);
                }
                break;
            case BURST:
                if (event.generation == burstGeneration) {
                    resolveBurst(simulator, event);
                }
                break;
            case BOND_RESET:
                if (event.generation == burstGeneration) {
                    bondRatio = 0.0;
                }
                break;
            default:
                throw new IllegalStateException(
                        "Unknown Arlecchino event kind");
        }
    }

    private void resolveNormal(
            CombatSimulator simulator,
            PendingEvent event) {
        if (simulator.getEnemy() == null) {
            return;
        }
        boolean masque = isMasqueActive();
        double[][] multipliers = constellation >= 3
                ? NORMAL_C3 : NORMAL_T9;
        String key = "N" + (event.index + 1);
        if (NORMAL_HIT_FRAMES[event.index].length > 1) {
            key += " Hit " + (event.variant + 1);
        }
        if (constellation >= 3) {
            key += " C3";
        }
        StatsContainer snapshot = captureLiveStats(
                event.time,
                ActionType.NORMAL);
        double additiveDamage = masque
                ? masqueIncrease() * bondRatio * snapshot.getTotalAtk()
                : 0.0;
        AttackAction action = createAction(
                "Invitation to a Beheading N" + (event.index + 1),
                getTalentValue(
                        key,
                        multipliers[event.index][event.variant]),
                masque ? Element.PYRO : Element.PHYSICAL,
                StatType.NORMAL_ATTACK_DMG_BONUS,
                ActionType.NORMAL,
                ICDType.Standard,
                ICDTag.NormalAttack,
                masque ? 1.0 : 0.0,
                snapshot,
                additiveDamage);
        resolvingMasqueNormal = masque;
        try {
            simulator.performActionWithoutTimeAdvance(characterId, action);
        } finally {
            resolvingMasqueNormal = false;
        }
    }

    private void resolveCharged(
            CombatSimulator simulator,
            PendingEvent event) {
        if (simulator.getEnemy() == null) {
            return;
        }
        boolean masque = isMasqueActive();
        AttackAction action = createAction(
                "Invitation to a Beheading Charged",
                getTalentValue(
                        constellation >= 3
                                ? "Charged Attack C3"
                                : "Charged Attack",
                        constellation >= 3
                                ? 2.048640 : 1.668480),
                masque ? Element.PYRO : Element.PHYSICAL,
                StatType.CHARGED_ATTACK_DMG_BONUS,
                ActionType.CHARGE,
                ICDType.ArlecchinoCharged,
                ICDTag.Arlecchino_Charged,
                masque ? 1.0 : 0.0,
                captureLiveStats(event.time, ActionType.CHARGE),
                0.0);
        simulator.performActionWithoutTimeAdvance(characterId, action);
    }

    private void resolveHighPlunge(
            CombatSimulator simulator,
            PendingEvent event) {
        if (simulator.getEnemy() == null) {
            return;
        }
        boolean masque = isMasqueActive();
        AttackAction action = createAction(
                "Invitation to a Beheading High Plunge",
                getTalentValue(
                        constellation >= 3
                                ? "High Plunge C3"
                                : "High Plunge",
                        constellation >= 3
                                ? 3.601998 : 2.933586),
                masque ? Element.PYRO : Element.PHYSICAL,
                StatType.PLUNGING_ATTACK_DMG_BONUS,
                ActionType.PLUNGE,
                ICDType.None,
                ICDTag.None,
                masque ? 1.0 : 0.0,
                captureLiveStats(event.time, ActionType.PLUNGE),
                0.0);
        simulator.performActionWithoutTimeAdvance(characterId, action);
    }

    private void resolveSkillHit(
            CombatSimulator simulator,
            PendingEvent event,
            boolean cleave) {
        if (simulator.getEnemy() == null) {
            return;
        }
        AttackAction action = createAction(
                cleave ? "All Is Ash (Cleave)" : "All Is Ash (Spike)",
                getTalentValue(
                        cleave
                                ? "All Is Ash Cleave"
                                : "All Is Ash Spike",
                        cleave ? 2.270520 : 0.252280),
                Element.PYRO,
                StatType.SKILL_DMG_BONUS,
                ActionType.SKILL,
                cleave
                        ? ICDType.None
                        : ICDType.ArlecchinoElementalArt,
                cleave
                        ? ICDTag.None
                        : ICDTag.Arlecchino_ElementalArt,
                1.0,
                captureLiveStats(event.time, ActionType.SKILL),
                0.0);
        action.setCountsAsSkillDmg(true);
        resolvingSkillCleave = cleave;
        resolvingGeneration = event.generation;
        try {
            simulator.performActionWithoutTimeAdvance(characterId, action);
        } finally {
            resolvingSkillCleave = false;
            resolvingGeneration = 0L;
        }
    }

    private void resolveDirectiveTick(
            CombatSimulator simulator,
            PendingEvent event) {
        if (simulator.getEnemy() == null) {
            return;
        }
        AttackAction action = createAction(
                "Blood-Debt Directive",
                getTalentValue("Blood-Debt Directive", 0.540600),
                Element.PYRO,
                StatType.SKILL_DMG_BONUS,
                ActionType.SKILL,
                ICDType.ArlecchinoElementalArt,
                ICDTag.Arlecchino_ElementalArt,
                1.0,
                captureLiveStats(event.time, ActionType.SKILL),
                0.0);
        action.setCountsAsSkillDmg(true);
        simulator.performActionWithoutTimeAdvance(characterId, action);
    }

    private void resolveC2(
            CombatSimulator simulator,
            PendingEvent event) {
        if (simulator.getEnemy() == null) {
            return;
        }
        AttackAction action = createAction(
                "Balemoon Bloodfire (C2)",
                getTalentValue("C2 Bloodfire", 9.0),
                Element.PYRO,
                null,
                ActionType.OTHER,
                ICDType.None,
                ICDTag.None,
                1.0,
                captureLiveStats(event.time, ActionType.OTHER),
                0.0);
        simulator.performActionWithoutTimeAdvance(characterId, action);
    }

    private void resolveBurst(
            CombatSimulator simulator,
            PendingEvent event) {
        if (simulator.getEnemy() == null) {
            return;
        }
        StatsContainer snapshot = captureLiveStats(
                event.time,
                ActionType.BURST);
        double additiveDamage = constellation >= 6
                ? snapshot.getTotalAtk()
                        * getTalentValue(
                                "C6 Burst ATK Bond Ratio", 7.0)
                        * bondRatio
                : 0.0;
        AttackAction action = createAction(
                "Balemoon Rising",
                getTalentValue(
                        constellation >= 5
                                ? "Balemoon Rising C5"
                                : "Balemoon Rising",
                        constellation >= 5 ? 7.408000 : 6.296800),
                Element.PYRO,
                StatType.BURST_DMG_BONUS,
                ActionType.BURST,
                ICDType.None,
                ICDTag.None,
                1.0,
                snapshot,
                additiveDamage);
        action.setCountsAsBurstDmg(true);
        simulator.performActionWithoutTimeAdvance(characterId, action);
    }

    /** Applies an accepted Cleave's Directive and particle packet. */
    private void handleAcceptedDamage(
            Character actor,
            AttackAction action,
            double damage,
            double time) {
        if (initializedSimulator == null
                || actor != this
                || damage <= 0.0) {
            return;
        }
        if (resolvingSkillCleave) {
            applyDirective(initializedSimulator, time);
            if (time + EPSILON >= nextParticleAllowedTime) {
                nextParticleAllowedTime = time
                        + getTalentValue("Particle Gate", 0.3);
                queueEvent(initializedSimulator, new PendingEvent(
                        time + getTalentValue(
                                "Particle Travel Frames", 100.0) * FRAME,
                        EventKind.PARTICLE,
                        resolvingGeneration,
                        (int) getTalentValue(
                                "Skill Particle Count", 5.0),
                        0));
            }
        }
        if (resolvingMasqueNormal
                && time + EPSILON >= nextBondConsumeAllowedTime) {
            nextBondConsumeAllowedTime = time + 2.0 * FRAME;
            bondRatio *= 1.0 - getTalentValue(
                    "Normal Bond Consumption", 0.075);
            reduceSkillCooldown(
                    time,
                    getTalentValue(
                            "Normal Skill CD Reduction", 0.8));
        }
    }

    /** Replaces the fixed target's Directive and queues its sourced timeline. */
    private void applyDirective(
            CombatSimulator simulator,
            double applicationTime) {
        invalidateDirectiveEvents(directiveGeneration);
        long generation = ++directiveGeneration;
        directiveActive = true;
        directiveLevel = constellation >= 2 ? 2 : 1;
        directiveExpirationTime = applicationTime
                + getTalentValue("Directive Duration", 30.0);
        double upgradeTime = applicationTime
                + getTalentValue("Directive Upgrade Delay", 5.0);
        queueEvent(simulator, new PendingEvent(
                upgradeTime,
                EventKind.DIRECTIVE_TICK,
                generation,
                0,
                0));
        if (constellation < 2) {
            queueEvent(simulator, new PendingEvent(
                    upgradeTime,
                    EventKind.DIRECTIVE_UPGRADE,
                    generation,
                    0,
                    0));
        }
        queueEvent(simulator, new PendingEvent(
                upgradeTime + 5.0,
                EventKind.DIRECTIVE_TICK,
                generation,
                1,
                0));
        queueEvent(simulator, new PendingEvent(
                directiveExpirationTime,
                EventKind.DIRECTIVE_EXPIRE,
                generation,
                0,
                0));
    }

    /** Collects one fixed-target Directive and activates supported constellations. */
    private void collectDirective(
            CombatSimulator simulator,
            double collectionTime) {
        if (!hasActiveDirective(collectionTime)) {
            return;
        }
        int collectedLevel = directiveLevel;
        double newBond = getTalentValue(
                collectedLevel >= 2
                        ? "Directive Due Bond"
                        : "Directive Ordinal Bond",
                collectedLevel >= 2 ? 1.30 : 0.65);
        if (collectionTime + EPSILON < debtLimitExpirationTime) {
            newBond = Math.min(
                    newBond,
                    Math.max(
                            0.0,
                            getTalentValue(
                                    "Directive Collection Cap", 1.45)
                                    - skillDebtRatio));
        }
        if (newBond > 0.0) {
            skillDebtRatio += newBond;
            bondRatio = Math.min(2.0, bondRatio + newBond);
        }
        long collectedGeneration = directiveGeneration;
        directiveActive = false;
        directiveLevel = 0;
        directiveGeneration++;
        invalidateDirectiveEvents(collectedGeneration);

        if (constellation >= 2
                && collectedLevel >= 2
                && collectionTime + EPSILON >= nextC2AllowedTime) {
            nextC2AllowedTime = collectionTime
                    + getTalentValue("C2 Trigger Gate", 10.0);
            queueEvent(simulator, new PendingEvent(
                    collectionTime
                            + getTalentValue(
                                    "C2 Impact Delay Frames", 50.0)
                                    * FRAME,
                    EventKind.C2_DAMAGE,
                    0L,
                    0,
                    0));
        }
        if (constellation >= 4
                && collectionTime + EPSILON >= nextC4AllowedTime) {
            nextC4AllowedTime = collectionTime
                    + getTalentValue("C4 Trigger Gate", 10.0);
            reduceBurstCooldown(
                    collectionTime,
                    getTalentValue("C4 Burst CD Reduction", 2.0));
            receiveFlatEnergy(
                    getTalentValue("C4 Flat Energy", 15.0));
        }
    }

    /** Starts C6's Normal/Burst CRIT window on an eligible Skill use. */
    private void activateC6(double activationTime) {
        if (constellation < 6
                || activationTime + EPSILON < nextC6AllowedTime) {
            return;
        }
        nextC6AllowedTime = activationTime
                + getTalentValue("C6 Trigger Gate", 15.0);
        c6ExpirationTime = activationTime
                + getTalentValue("C6 Duration", 20.0);
    }

    private boolean isMasqueActive() {
        return bondRatio + EPSILON >= getTalentValue(
                "Masque Bond Threshold", 0.30);
    }

    private double masqueIncrease() {
        double value = getTalentValue(
                constellation >= 3
                        ? "Masque Increase C3"
                        : "Masque Increase",
                constellation >= 3 ? 2.716 : 2.212);
        if (constellation >= 1) {
            value += getTalentValue("C1 Masque Increase", 1.0);
        }
        return value;
    }

    /** Creates one hit with its fixed hit-time snapshot and additive damage. */
    private AttackAction createAction(
            String displayName,
            double multiplier,
            Element hitElement,
            StatType bonusStat,
            ActionType actionType,
            ICDType icdType,
            ICDTag icdTag,
            double gauge,
            StatsContainer snapshot,
            double additiveDamage) {
        AttackAction action = additiveDamage == 0.0
                ? new AttackAction(
                        displayName,
                        multiplier,
                        hitElement,
                        StatType.BASE_ATK,
                        bonusStat,
                        0.0,
                        actionType)
                : new ArlecchinoAttackAction(
                        displayName,
                        multiplier,
                        hitElement,
                        bonusStat,
                        actionType,
                        additiveDamage);
        action.setICD(icdType, icdTag, gauge);
        action.setStatSnapshot(snapshot);
        return action;
    }

    private StatsContainer captureLiveStats(
            double currentTime,
            ActionType actionType) {
        StatsContainer stats = getEffectiveStats(currentTime);
        if (initializedSimulator != null) {
            for (Buff buff : initializedSimulator.getApplicableBuffs(this)) {
                if (!buff.isExpired(currentTime)) {
                    buff.apply(stats, currentTime);
                }
            }
        }
        if (constellation >= 6
                && currentTime + EPSILON < c6ExpirationTime
                && (actionType == ActionType.NORMAL
                        || actionType == ActionType.BURST)) {
            stats.add(
                    StatType.CRIT_RATE,
                    getTalentValue("C6 CRIT Rate", 0.10));
            stats.add(
                    StatType.CRIT_DMG,
                    getTalentValue("C6 CRIT DMG", 0.70));
        }
        return stats;
    }

    private void invalidateDirectiveEvents(long generation) {
        pendingEvents.removeIf(event ->
                event.generation == generation
                        && isDirectiveEvent(event.kind));
    }

    private static boolean isDirectiveEvent(EventKind kind) {
        return kind == EventKind.DIRECTIVE_TICK
                || kind == EventKind.DIRECTIVE_UPGRADE
                || kind == EventKind.DIRECTIVE_EXPIRE;
    }

    private void queueEvent(
            CombatSimulator simulator,
            PendingEvent event) {
        pendingEvents.add(event);
        scheduleEvent(simulator, event);
    }

    private void scheduleEvent(
            CombatSimulator simulator,
            PendingEvent event) {
        schedule(simulator, event.time, activeSimulator -> {
            if (!pendingEvents.remove(event)) {
                return;
            }
            resolveEvent(activeSimulator, event);
        });
    }

    private static void schedule(
            CombatSimulator simulator,
            double time,
            Consumer<CombatSimulator> effect) {
        simulator.registerEvent(new SimpleTimerEvent(time, 1.0) {
            @Override
            public void onTick(CombatSimulator activeSimulator) {
                finish();
                effect.accept(activeSimulator);
            }
        });
    }

    private static List<PendingEvent> copyEvents(
            List<PendingEvent> source) {
        List<PendingEvent> copy = new ArrayList<>();
        for (PendingEvent event : source) {
            copy.add(event.copy());
        }
        return copy;
    }

    private enum EventKind {
        NORMAL,
        CHARGED,
        HIGH_PLUNGE,
        SKILL_COOLDOWN,
        SKILL_SPIKE,
        SKILL_CLEAVE,
        DIRECTIVE_LIMIT,
        DIRECTIVE_TICK,
        DIRECTIVE_UPGRADE,
        DIRECTIVE_EXPIRE,
        COLLECT_DIRECTIVE,
        PARTICLE,
        C2_DAMAGE,
        C6_SKILL,
        BURST_ENERGY,
        SKILL_RESET,
        BURST,
        BOND_RESET
    }

    /** Preserves Masque and C6 additions through damage resolution. */
    private static final class ArlecchinoAttackAction
            extends AttackAction {
        private final double fixedAdditiveBaseDamage;

        private ArlecchinoAttackAction(
                String displayName,
                double multiplier,
                Element element,
                StatType bonusStat,
                ActionType actionType,
                double fixedAdditiveBaseDamage) {
            super(
                    displayName,
                    multiplier,
                    element,
                    StatType.BASE_ATK,
                    bonusStat,
                    0.0,
                    actionType);
            this.fixedAdditiveBaseDamage = fixedAdditiveBaseDamage;
        }

        @Override
        public void setAdditiveBaseDmgBonus(double value) {
        }

        @Override
        public double getAdditiveBaseDmgBonus() {
            return fixedAdditiveBaseDamage;
        }
    }

    /** Immutable future event with owner-generation metadata. */
    private static final class PendingEvent {
        private final double time;
        private final EventKind kind;
        private final long generation;
        private final int index;
        private final int variant;

        private PendingEvent(
                double time,
                EventKind kind,
                long generation,
                int index,
                int variant) {
            this.time = time;
            this.kind = kind;
            this.generation = generation;
            this.index = index;
            this.variant = variant;
        }

        private PendingEvent copy() {
            return new PendingEvent(
                    time,
                    kind,
                    generation,
                    index,
                    variant);
        }
    }

    /** Immutable snapshot of all Arlecchino-owned mutable runtime state. */
    private static final class ArlecchinoState implements State {
        private final Arlecchino owner;
        private final int normalAttackStep;
        private final long skillGeneration;
        private final long burstGeneration;
        private final long directiveGeneration;
        private final double bondRatio;
        private final double skillDebtRatio;
        private final double debtLimitExpirationTime;
        private final boolean directiveActive;
        private final int directiveLevel;
        private final double directiveExpirationTime;
        private final double nextParticleAllowedTime;
        private final double nextBondConsumeAllowedTime;
        private final double nextC2AllowedTime;
        private final double nextC4AllowedTime;
        private final double nextC6AllowedTime;
        private final double c6ExpirationTime;
        private final List<PendingEvent> pendingEvents;

        private ArlecchinoState(
                Arlecchino owner,
                int normalAttackStep,
                long skillGeneration,
                long burstGeneration,
                long directiveGeneration,
                double bondRatio,
                double skillDebtRatio,
                double debtLimitExpirationTime,
                boolean directiveActive,
                int directiveLevel,
                double directiveExpirationTime,
                double nextParticleAllowedTime,
                double nextBondConsumeAllowedTime,
                double nextC2AllowedTime,
                double nextC4AllowedTime,
                double nextC6AllowedTime,
                double c6ExpirationTime,
                List<PendingEvent> pendingEvents) {
            this.owner = owner;
            this.normalAttackStep = normalAttackStep;
            this.skillGeneration = skillGeneration;
            this.burstGeneration = burstGeneration;
            this.directiveGeneration = directiveGeneration;
            this.bondRatio = bondRatio;
            this.skillDebtRatio = skillDebtRatio;
            this.debtLimitExpirationTime = debtLimitExpirationTime;
            this.directiveActive = directiveActive;
            this.directiveLevel = directiveLevel;
            this.directiveExpirationTime = directiveExpirationTime;
            this.nextParticleAllowedTime = nextParticleAllowedTime;
            this.nextBondConsumeAllowedTime =
                    nextBondConsumeAllowedTime;
            this.nextC2AllowedTime = nextC2AllowedTime;
            this.nextC4AllowedTime = nextC4AllowedTime;
            this.nextC6AllowedTime = nextC6AllowedTime;
            this.c6ExpirationTime = c6ExpirationTime;
            this.pendingEvents = copyEvents(pendingEvents);
        }
    }
}

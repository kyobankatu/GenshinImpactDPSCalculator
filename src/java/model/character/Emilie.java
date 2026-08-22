package model.character;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import mechanics.buff.Buff;
import mechanics.buff.BuffId;
import mechanics.buff.SimpleBuff;
import mechanics.data.TalentDataManager;
import mechanics.data.TalentDataSource;
import mechanics.energy.ParticleType;
import mechanics.reaction.ReactionResult;
import model.entity.ArtifactSet;
import model.entity.Character;
import model.entity.ReactionAwareCharacter;
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
import simulation.action.HitlagProfile;
import simulation.action.SkillActionMode;
import simulation.event.SimpleTimerEvent;

/**
 * Emilie's stationary, fixed-target Lumidouce Case offensive slice.
 *
 * <p>Normal, Charged, high Plunge, Skill summon and Case cadence, scent
 * promotion, Cleardew Cologne, Burst replacement/restoration, particles,
 * A4, and representable C1-C6 behavior follow pinned gcsim
 * {@code ef41805d}. The only enemy is selected deterministically, so C4 adapts
 * the Burst target gate without introducing geometry or random targeting.</p>
 *
 * <p>Healing and player HP, movement and geometry, hitlag extension and stamina, low
 * Plunge, Arkhe/Pneuma, and unsupported target state are excluded instead of
 * being approximated. Lumidouce damage is live-stat damage; each queued impact
 * still owns the exact stats captured when that attack was emitted.</p>
 */
public final class Emilie extends Character implements
        ReactionAwareCharacter,
        SimulatorInitializedCharacterEffect,
        SnapshotAwareCharacterEffect,
        SwitchAwareCharacter {
    private static final double FRAME = 1.0 / 60.0;
    private static final double EPSILON = 1e-9;
    private static final int[] NORMAL_HIT_FRAMES = { 11, 16, 33, 34 };
    private static final int[] NORMAL_DURATIONS = { 20, 19, 40, 70 };
    private static final double[] NORMAL_T9 = {
        0.892163, 0.824823, 1.089473, 1.379798
    };

    /**
     * Basic-attack hitlag from gcsim pinned at
     * {@code 3647a07a7cc3004bc1e79d9bb5f7444de20dceaa}.
     */
    private static final HitlagProfile NORMAL_HITLAG =
            new HitlagProfile(0.06, 0.01, true, false, false);
    private static final HitlagProfile NORMAL_FINAL_HITLAG =
            new HitlagProfile(0.09, 0.01, true, false, false);
    private static final HitlagProfile CHARGED_HITLAG =
            new HitlagProfile(0.09, 0.01, true, false, false);

    private CombatSimulator initializedSimulator;
    private int normalAttackStep;
    private long caseGeneration;
    private int caseLevel;
    private int scentCount;
    private double caseExpirationTime = Double.NEGATIVE_INFINITY;
    private double scentResetExpirationTime = Double.NEGATIVE_INFINITY;
    private double nextScentAllowedTime = Double.NEGATIVE_INFINITY;
    private double nextC1ScentAllowedTime = Double.NEGATIVE_INFINITY;
    private double nextParticleAllowedTime = Double.NEGATIVE_INFINITY;
    private double nextBurstTargetAllowedTime = Double.NEGATIVE_INFINITY;
    private int priorCaseLevel = 1;
    private double c6ExpirationTime = Double.NEGATIVE_INFINITY;
    private double nextC6ActivationTime = Double.NEGATIVE_INFINITY;
    private int c6ScentCount;
    private AttackAction resolvingAction;
    private boolean resolvingParticleEligible;
    private boolean resolvingC2Eligible;
    private boolean resolvingC6ScentEligible;
    private List<PendingHit> pendingHits = new ArrayList<>();
    private List<PendingCommand> pendingCommands = new ArrayList<>();

    /** Constructs repository-default C6 Emilie. */
    public Emilie(Weapon weapon, ArtifactSet artifacts) {
        this(weapon, artifacts, TalentDataManager.getInstance(), 6);
    }

    /** Constructs Emilie at an explicit constellation. */
    public Emilie(
            Weapon weapon,
            ArtifactSet artifacts,
            int constellation) {
        this(weapon, artifacts, TalentDataManager.getInstance(), constellation);
    }

    /**
     * Constructs Emilie with injectable static talent data.
     *
     * @param weapon equipped polearm, or {@code null}
     * @param artifacts equipped artifact set, or {@code null}
     * @param talentData static character-data source
     * @param constellation constellation level in {@code [0, 6]}
     */
    public Emilie(
            Weapon weapon,
            ArtifactSet artifacts,
            TalentDataSource talentData,
            int constellation) {
        super(talentData);
        if (constellation < 0 || constellation > 6) {
            throw new IllegalArgumentException(
                    "Emilie constellation must be between 0 and 6");
        }
        name = "Emilie";
        characterId = CharacterId.EMILIE;
        element = Element.DENDRO;
        this.weapon = weapon;
        this.artifacts = new ArtifactSet[] { artifacts };
        this.constellation = constellation;
        baseStats.set(StatType.BASE_HP,
                getTalentValue("Base HP", 13568.0));
        baseStats.set(StatType.BASE_ATK,
                getTalentValue("Base ATK", 335.0));
        baseStats.set(StatType.BASE_DEF,
                getTalentValue("Base DEF", 730.0));
        baseStats.add(StatType.CRIT_DMG,
                getTalentValue("Ascension CRIT DMG", 0.384));
        setSkillCD(getTalentValue("Skill Cooldown", 14.0));
        setBurstCD(getTalentValue("Burst Cooldown", 13.5));
    }

    /** Binds all target-state listeners to exactly one owning simulator. */
    @Override
    public void initializeForSimulator(CombatSimulator simulator) {
        if (simulator == null) {
            throw new IllegalArgumentException("Emilie simulator is required");
        }
        if (!simulator.getPartyMembers().contains(this)) {
            throw new IllegalArgumentException(
                    "Emilie must belong to the simulator party");
        }
        if (initializedSimulator != null
                && initializedSimulator != simulator) {
            throw new IllegalStateException(
                    "Emilie cannot be reused across simulators");
        }
        if (initializedSimulator == simulator) {
            return;
        }
        initializedSimulator = simulator;
        simulator.addReactionListener(this);
        simulator.addDamageListener((actor, action, damage, time) ->
                handleAcceptedDamage(
                        simulator, actor, action, damage, time));
    }

    /** Captures Case resources, gates, generations, and reconstructable work. */
    @Override
    public State captureCharacterState() {
        return new EmilieState(
                this,
                normalAttackStep,
                caseGeneration,
                caseLevel,
                scentCount,
                caseExpirationTime,
                scentResetExpirationTime,
                nextScentAllowedTime,
                nextC1ScentAllowedTime,
                nextParticleAllowedTime,
                nextBurstTargetAllowedTime,
                priorCaseLevel,
                c6ExpirationTime,
                nextC6ActivationTime,
                c6ScentCount,
                pendingHits,
                pendingCommands);
    }

    /** Accepts state captured from this exact Emilie instance only. */
    @Override
    public boolean acceptsCharacterState(State state) {
        return state instanceof EmilieState
                && ((EmilieState) state).owner == this;
    }

    /** Restores Emilie-owned future hits and commands exactly once. */
    @Override
    public void restoreCharacterState(
            State state,
            CombatSimulator simulator) {
        if (!acceptsCharacterState(state)) {
            throw new IllegalArgumentException("Unexpected Emilie state");
        }
        initializeForSimulator(simulator);
        EmilieState restored = (EmilieState) state;
        normalAttackStep = restored.normalAttackStep;
        caseGeneration = restored.caseGeneration;
        caseLevel = restored.caseLevel;
        scentCount = restored.scentCount;
        caseExpirationTime = restored.caseExpirationTime;
        scentResetExpirationTime = restored.scentResetExpirationTime;
        nextScentAllowedTime = restored.nextScentAllowedTime;
        nextC1ScentAllowedTime = restored.nextC1ScentAllowedTime;
        nextParticleAllowedTime = restored.nextParticleAllowedTime;
        nextBurstTargetAllowedTime = restored.nextBurstTargetAllowedTime;
        priorCaseLevel = restored.priorCaseLevel;
        c6ExpirationTime = restored.c6ExpirationTime;
        nextC6ActivationTime = restored.nextC6ActivationTime;
        c6ScentCount = restored.c6ScentCount;
        pendingHits = copyHits(restored.pendingHits);
        pendingCommands = copyCommands(restored.pendingCommands);
        resolvingAction = null;
        resolvingParticleEligible = false;
        resolvingC2Eligible = false;
        resolvingC6ScentEligible = false;
        double currentTime = simulator.getCurrentTime();
        pendingHits.removeIf(hit -> hit.time < currentTime - EPSILON);
        pendingCommands.removeIf(command ->
                command.time < currentTime - EPSILON);
        for (PendingHit hit : new ArrayList<>(pendingHits)) {
            scheduleHit(simulator, hit);
        }
        for (PendingCommand command
                : new ArrayList<>(pendingCommands)) {
            scheduleCommand(simulator, command);
        }
    }

    /** Returns Emilie's 50-Energy Burst cost. */
    @Override
    public double getEnergyCost() {
        return getTalentValue("Energy Cost", 50.0);
    }

    /** Emilie's represented passives are target- and action-conditional. */
    @Override
    public void applyPassive(StatsContainer stats) {
    }

    /** Resets the four-hit Normal Attack string on switch-out. */
    @Override
    public void onSwitchOut(CombatSimulator simulator) {
        normalAttackStep = 0;
    }

    /** Resets the four-hit Normal Attack string on switch-in. */
    @Override
    public void onSwitchIn(CombatSimulator simulator) {
        normalAttackStep = 0;
    }

    /** Returns the active Case level, or zero when no Case is active. */
    public int getCaseLevel(double currentTime) {
        return isCaseActive(currentTime) ? caseLevel : 0;
    }

    /** Returns the current unconsumed scent count. */
    public int getScentCount() {
        return scentCount;
    }

    /** Returns the absolute active Case expiry timestamp. */
    public double getCaseExpirationTime() {
        return caseExpirationTime;
    }

    /** Returns the number of C6-generated scents in the current window. */
    public int getC6ScentCount() {
        return c6ScentCount;
    }

    /** Returns the number of unresolved Emilie-owned impacts. */
    public int getPendingHitCount() {
        return pendingHits.size();
    }

    /** Reports that A4 uses the simulator's typed fixed-target Burning state. */
    public boolean isA4BurningBonusRepresented() {
        return true;
    }

    /** Reports that Arkhe/Pneuma is excluded for lack of a typed contract. */
    public boolean isArkheRepresented() {
        return false;
    }

    /** Dispatches Emilie's represented typed action set. */
    @Override
    public void onAction(
            CharacterActionRequest request,
            CombatSimulator simulator) {
        if (request == null) {
            throw new IllegalArgumentException("Emilie action is required");
        }
        initializeForSimulator(simulator);
        if (request.getKey() == CharacterActionKey.SKILL
                && request.getSkillMode() != SkillActionMode.PRESS) {
            throw new IllegalArgumentException(
                    "Emilie supports Press Skill only");
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
                fragranceExtraction(simulator);
                break;
            case BURST:
                aromaticExplication(simulator);
                break;
            default:
                throw new IllegalArgumentException(
                        "Unsupported action for Emilie: " + request.getKey());
        }
    }

    /** Generates C1 scent only from represented Burning and Dendro events. */
    @Override
    public void onReaction(
            ReactionResult result,
            Character source,
            double time,
            CombatSimulator simulator) {
        if (simulator != initializedSimulator
                || constellation < 1
                || result == null
                || result.getKind() != ReactionResult.Kind.BURNING) {
            return;
        }
        generateC1Scent(time);
    }

    private void normalAttack(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        int step = normalAttackStep;
        boolean c6Enhanced = isC6Active(castTime);
        queueHit(simulator, new PendingHit(
                castTime + NORMAL_HIT_FRAMES[step] * FRAME,
                HitKind.NORMAL,
                step,
                captureLiveStats(castTime),
                c6Enhanced));
        normalAttackStep = (normalAttackStep + 1) % NORMAL_T9.length;
        simulator.advanceTime(NORMAL_DURATIONS[step] * FRAME);
    }

    private void chargedAttack(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        queueHit(simulator, new PendingHit(
                castTime + 26.0 * FRAME,
                HitKind.CHARGED,
                0,
                captureLiveStats(castTime),
                isC6Active(castTime)));
        simulator.advanceTime(50.0 * FRAME);
    }

    private void highPlunge(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        queueHit(simulator, new PendingHit(
                castTime + 49.0 * FRAME,
                HitKind.HIGH_PLUNGE,
                0,
                captureLiveStats(castTime),
                false));
        simulator.advanceTime(79.0 * FRAME);
    }

    private void fragranceExtraction(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        markSkillUsed(castTime, simulator.getApplicableBuffs(this));
        StatsContainer snapshot = captureLiveStats(castTime);
        queueCommand(simulator, new PendingCommand(
                castTime + 16.0 * FRAME,
                CommandKind.SKILL_CASE_SPAWN,
                0L));
        queueHit(simulator, new PendingHit(
                castTime + 37.0 * FRAME,
                HitKind.SKILL_SUMMON,
                0,
                snapshot,
                false));
        simulator.advanceTime(37.0 * FRAME);
    }

    private void aromaticExplication(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        markBurstCooldownUsed(castTime, simulator.getApplicableBuffs(this));
        queueCommand(simulator, new PendingCommand(
                castTime + 9.0 * FRAME,
                CommandKind.BURST_ENERGY,
                0L));
        queueCommand(simulator, new PendingCommand(
                castTime + 96.0 * FRAME,
                CommandKind.BURST_CASE_SPAWN,
                0L));
        simulator.advanceTime(111.0 * FRAME);
    }

    private void deploySkillCase(CombatSimulator simulator) {
        double currentTime = simulator.getCurrentTime();
        if (isCaseActive(currentTime) && caseLevel == 3) {
            scentResetExpirationTime = currentTime
                    + getTalentValue("Scent Reset Interval", 8.0);
            activateC6(currentTime);
            return;
        }
        long generation = ++caseGeneration;
        caseLevel = 1;
        scentCount = 0;
        caseExpirationTime = currentTime
                + getTalentValue("Case Duration", 22.0);
        scentResetExpirationTime = currentTime
                + getTalentValue("Scent Reset Interval", 8.0);
        queueCaseCadence(simulator, generation, currentTime);
        activateC6(currentTime);
    }

    private void deployBurstCase(CombatSimulator simulator) {
        double currentTime = simulator.getCurrentTime();
        priorCaseLevel = isCaseActive(currentTime) ? caseLevel : 1;
        long generation = ++caseGeneration;
        caseLevel = 3;
        double duration = getTalentValue("Burst Case Duration", 2.8);
        if (constellation >= 4) {
            duration += getTalentValue(
                    "C4 Burst Duration Extension", 2.0);
        }
        caseExpirationTime = currentTime + duration;
        scentResetExpirationTime = currentTime
                + getTalentValue("Scent Reset Interval", 8.0);
        nextBurstTargetAllowedTime = Double.NEGATIVE_INFINITY;
        queueCommand(simulator, new PendingCommand(
                currentTime + 0.3,
                CommandKind.BURST_CASE_ATTACK,
                generation));
        queueCommand(simulator, new PendingCommand(
                caseExpirationTime,
                CommandKind.BURST_CASE_RESTORE,
                generation));
        activateC6(currentTime);
    }

    private void restorePriorCase(
            CombatSimulator simulator,
            long generation) {
        if (generation != caseGeneration) {
            return;
        }
        double currentTime = simulator.getCurrentTime();
        long restoredGeneration = ++caseGeneration;
        caseLevel = Math.max(1, Math.min(2, priorCaseLevel));
        caseExpirationTime = currentTime
                + getTalentValue("Case Duration", 22.0);
        queueCaseCadence(simulator, restoredGeneration, currentTime);
    }

    private void queueCaseCadence(
            CombatSimulator simulator,
            long generation,
            double startTime) {
        queueCommand(simulator, new PendingCommand(
                startTime + getTalentValue("Case Attack Interval", 1.5),
                CommandKind.CASE_ATTACK,
                generation));
        queueCommand(simulator, new PendingCommand(
                startTime + 0.5,
                CommandKind.SCENT_CHECK,
                generation));
    }

    private void emitCaseAttack(
            CombatSimulator simulator,
            long generation) {
        double currentTime = simulator.getCurrentTime();
        if (generation != caseGeneration
                || !isCaseActive(currentTime)
                || caseLevel < 1
                || caseLevel > 2) {
            return;
        }
        StatsContainer snapshot = captureLiveStats(currentTime);
        HitKind kind = caseLevel == 1
                ? HitKind.CASE_LEVEL_ONE : HitKind.CASE_LEVEL_TWO;
        queueHit(simulator, new PendingHit(
                currentTime + 5.0 * FRAME,
                kind,
                0,
                snapshot,
                false));
        if (caseLevel == 2) {
            queueHit(simulator, new PendingHit(
                    currentTime + 23.0 * FRAME,
                    kind,
                    1,
                    snapshot,
                    false));
        }
        processScents(simulator, currentTime);
        double nextTime = currentTime
                + getTalentValue("Case Attack Interval", 1.5);
        if (nextTime < caseExpirationTime - EPSILON) {
            queueCommand(simulator, new PendingCommand(
                    nextTime,
                    CommandKind.CASE_ATTACK,
                    generation));
        }
    }

    private void emitBurstAttack(
            CombatSimulator simulator,
            long generation) {
        double currentTime = simulator.getCurrentTime();
        if (generation != caseGeneration
                || !isCaseActive(currentTime)
                || caseLevel != 3) {
            return;
        }
        if (currentTime + EPSILON >= nextBurstTargetAllowedTime) {
            queueHit(simulator, new PendingHit(
                    currentTime + 12.0 * FRAME,
                    HitKind.BURST_CASE,
                    0,
                    captureLiveStats(currentTime),
                    false));
            nextBurstTargetAllowedTime = currentTime
                    + (constellation >= 4
                            ? getTalentValue(
                                    "C4 Burst Target Gate", 0.4)
                            : 0.7);
        }
        double nextTime = currentTime + 0.3;
        if (nextTime < caseExpirationTime - EPSILON) {
            queueCommand(simulator, new PendingCommand(
                    nextTime,
                    CommandKind.BURST_CASE_ATTACK,
                    generation));
        }
    }

    private void checkScents(
            CombatSimulator simulator,
            long generation) {
        double currentTime = simulator.getCurrentTime();
        if (generation != caseGeneration
                || !isCaseActive(currentTime)
                || caseLevel < 1
                || caseLevel > 2) {
            return;
        }
        if (caseLevel > 1
                && currentTime + EPSILON >= scentResetExpirationTime) {
            caseLevel = 1;
            scentCount = 0;
        }
        if (simulator.isBurningActive()
                && currentTime + EPSILON >= nextScentAllowedTime) {
            generateScent();
            nextScentAllowedTime = currentTime
                    + getTalentValue("Scent Generation Cooldown", 2.0);
            scentResetExpirationTime = currentTime
                    + getTalentValue("Scent Reset Interval", 8.0);
        }
        processScents(simulator, currentTime);
        double nextTime = currentTime + 0.5;
        if (nextTime < caseExpirationTime - EPSILON) {
            queueCommand(simulator, new PendingCommand(
                    nextTime,
                    CommandKind.SCENT_CHECK,
                    generation));
        }
    }

    private void processScents(
            CombatSimulator simulator,
            double currentTime) {
        if (scentCount < 2) {
            return;
        }
        scentCount -= 2;
        if (caseLevel < 2) {
            caseLevel++;
            return;
        }
        queueHit(simulator, new PendingHit(
                currentTime + 18.0 * FRAME,
                HitKind.CLEARDEW,
                0,
                captureLiveStats(currentTime),
                false));
    }

    private void generateScent() {
        scentCount++;
    }

    private void generateC1Scent(double time) {
        if (time + EPSILON < nextC1ScentAllowedTime) {
            return;
        }
        nextC1ScentAllowedTime = time
                + getTalentValue("C1 Scent Cooldown", 2.9);
        generateScent();
    }

    private void activateC6(double currentTime) {
        if (constellation < 6
                || currentTime + EPSILON < nextC6ActivationTime) {
            return;
        }
        c6ScentCount = 0;
        c6ExpirationTime = currentTime
                + getTalentValue("C6 Duration", 5.0);
        nextC6ActivationTime = currentTime
                + getTalentValue("C6 Cooldown", 12.0);
    }

    private boolean isCaseActive(double currentTime) {
        return caseLevel > 0
                && currentTime + EPSILON < caseExpirationTime;
    }

    private boolean isC6Active(double currentTime) {
        return constellation >= 6
                && currentTime + EPSILON < c6ExpirationTime;
    }

    private void handleAcceptedDamage(
            CombatSimulator simulator,
            Character actor,
            AttackAction action,
            double damage,
            double time) {
        if (damage <= 0.0) {
            return;
        }
        if (constellation >= 1
                && action.getElement() == Element.DENDRO
                && simulator.isBurningActive()) {
            generateC1Scent(time);
        }
        if (actor != this || action != resolvingAction) {
            return;
        }
        if (resolvingParticleEligible) {
            triggerParticle(simulator, time);
        }
        if (resolvingC2Eligible && constellation >= 2) {
            applyC2Shred(simulator, time);
        }
        if (resolvingC6ScentEligible) {
            generateScent();
            c6ScentCount++;
            if (c6ScentCount >= (int) getTalentValue(
                    "C6 Scent Cap", 4.0)) {
                c6ExpirationTime = Double.NEGATIVE_INFINITY;
            }
        }
    }

    private void triggerParticle(
            CombatSimulator simulator,
            double hitTime) {
        if (hitTime + EPSILON < nextParticleAllowedTime) {
            return;
        }
        nextParticleAllowedTime = hitTime
                + getTalentValue("Particle Cooldown", 2.5);
        queueCommand(simulator, new PendingCommand(
                hitTime + getTalentValue(
                        "Particle Travel Frames", 100.0) * FRAME,
                CommandKind.PARTICLE,
                0L));
    }

    private void applyC2Shred(
            CombatSimulator simulator,
            double currentTime) {
        simulator.applyTeamBuffNoStack(new SimpleBuff(
                "Emilie Lakelight Top Note",
                BuffId.EMILIE_C2_DENDRO_RES_SHRED,
                getTalentValue("C2 Duration", 10.0),
                currentTime,
                stats -> stats.add(
                        StatType.DENDRO_RES_SHRED,
                        getTalentValue("C2 Dendro RES Shred", 0.3)))
                .sourcedBy(characterId));
    }

    private void resolveHit(
            CombatSimulator simulator,
            PendingHit hit) {
        switch (hit.kind) {
            case NORMAL:
                performHit(
                        simulator,
                        hit,
                        "Shadow-Hunting Spear N" + (hit.index + 1),
                        getTalentValue(
                                "N" + (hit.index + 1),
                                NORMAL_T9[hit.index]),
                        hit.c6Enhanced ? Element.DENDRO : Element.PHYSICAL,
                        StatType.NORMAL_ATTACK_DMG_BONUS,
                        ActionType.NORMAL,
                        ICDType.Standard,
                        ICDTag.NormalAttack,
                        hit.c6Enhanced ? 1.0 : 0.0,
                        false,
                        false,
                        hit.c6Enhanced);
                break;
            case CHARGED:
                performHit(
                        simulator,
                        hit,
                        "Shadow-Hunting Spear Charged Attack",
                        getTalentValue("Charged Attack", 1.67796),
                        hit.c6Enhanced ? Element.DENDRO : Element.PHYSICAL,
                        StatType.CHARGED_ATTACK_DMG_BONUS,
                        ActionType.CHARGE,
                        ICDType.Standard,
                        ICDTag.ChargedAttack,
                        hit.c6Enhanced ? 1.0 : 0.0,
                        false,
                        false,
                        hit.c6Enhanced);
                break;
            case HIGH_PLUNGE:
                performHit(
                        simulator,
                        hit,
                        "Shadow-Hunting Spear High Plunge",
                        getTalentValue("High Plunge", 2.933586),
                        Element.PHYSICAL,
                        StatType.PLUNGING_ATTACK_DMG_BONUS,
                        ActionType.PLUNGE,
                        ICDType.None,
                        ICDTag.PlungeAttack,
                        0.0,
                        false,
                        false,
                        false);
                break;
            case SKILL_SUMMON:
                performHit(
                        simulator,
                        hit,
                        "Fragrance Extraction: Lumidouce Case Summon",
                        skillValue("Lumidouce Case Summon", 0.80036, 0.9416),
                        Element.DENDRO,
                        StatType.SKILL_DMG_BONUS,
                        ActionType.SKILL,
                        ICDType.None,
                        ICDTag.ElementalSkill,
                        1.0,
                        false,
                        true,
                        false);
                break;
            case CASE_LEVEL_ONE:
                performHit(
                        simulator,
                        hit,
                        "Lumidouce Case Level 1",
                        skillValue(
                                "Lumidouce Case Level 1", 0.6732, 0.792),
                        Element.DENDRO,
                        StatType.SKILL_DMG_BONUS,
                        ActionType.SKILL,
                        ICDType.EmilieLumidouce,
                        ICDTag.Emilie_Lumidouce,
                        1.0,
                        true,
                        false,
                        false);
                break;
            case CASE_LEVEL_TWO:
                performHit(
                        simulator,
                        hit,
                        "Lumidouce Case Level 2-" + (hit.index + 1),
                        skillValue(
                                "Lumidouce Case Level 2", 1.428, 1.68),
                        Element.DENDRO,
                        StatType.SKILL_DMG_BONUS,
                        ActionType.SKILL,
                        ICDType.EmilieLumidouce,
                        ICDTag.Emilie_Lumidouce,
                        1.0,
                        true,
                        false,
                        false);
                break;
            case BURST_CASE:
                performHit(
                        simulator,
                        hit,
                        "Aromatic Explication: Lumidouce Case Level 3",
                        burstValue(),
                        Element.DENDRO,
                        StatType.BURST_DMG_BONUS,
                        ActionType.BURST,
                        ICDType.Standard,
                        ICDTag.ElementalBurst,
                        1.0,
                        true,
                        true,
                        false);
                break;
            case CLEARDEW:
                performHit(
                        simulator,
                        hit,
                        "Cleardew Cologne",
                        getTalentValue("Cleardew Cologne", 6.0),
                        Element.DENDRO,
                        null,
                        ActionType.OTHER,
                        ICDType.None,
                        ICDTag.None,
                        1.0,
                        false,
                        true,
                        false);
                break;
            default:
                throw new IllegalStateException(
                        "Unknown Emilie hit kind " + hit.kind);
        }
    }

    private void performHit(
            CombatSimulator simulator,
            PendingHit hit,
            String displayName,
            double multiplier,
            Element hitElement,
            StatType bonusStat,
            ActionType actionType,
            ICDType icdType,
            ICDTag icdTag,
            double gauge,
            boolean particleEligible,
            boolean c2Eligible,
            boolean c6ScentEligible) {
        AttackAction action = new AttackAction(
                displayName,
                multiplier,
                hitElement,
                StatType.BASE_ATK,
                bonusStat,
                0.0,
                actionType);
        action.setICD(icdType, icdTag, gauge);
        action.setCountsAsSkillDmg(actionType == ActionType.SKILL);
        action.setCountsAsBurstDmg(actionType == ActionType.BURST);
        if (hit.kind == HitKind.NORMAL) {
            action.setHitlagProfile(hit.index == 3
                    ? NORMAL_FINAL_HITLAG : NORMAL_HITLAG);
        } else if (hit.kind == HitKind.CHARGED) {
            action.setHitlagProfile(CHARGED_HITLAG);
        }
        StatsContainer snapshot = hit.snapshot == null
                ? captureLiveStats(simulator.getCurrentTime())
                : hit.snapshot.merge(null);
        if (hit.c6Enhanced) {
            snapshot.add(
                    StatType.FLAT_DMG_BONUS,
                    snapshot.getTotalAtk()
                            * getTalentValue("C6 ATK Ratio", 3.0));
        }
        action.setStatSnapshot(snapshot);
        if (constellation >= 1
                && (actionType == ActionType.SKILL
                        || hit.kind == HitKind.CLEARDEW)) {
            action.addBonusStat(
                    StatType.DMG_BONUS_ALL,
                    getTalentValue("C1 Skill DMG Bonus", 0.2));
        }
        if (simulator.isBurningActive()) {
            double a4Bonus = Math.min(
                    getTalentValue("A4 DMG Cap", 0.36),
                    snapshot.getTotalAtk() / 1000.0
                            * getTalentValue(
                                    "A4 DMG Per 1000 ATK", 0.15));
            action.addBonusStat(StatType.DMG_BONUS_ALL, a4Bonus);
        }
        resolvingAction = action;
        resolvingParticleEligible = particleEligible;
        resolvingC2Eligible = c2Eligible;
        resolvingC6ScentEligible = c6ScentEligible;
        try {
            simulator.performActionWithoutTimeAdvance(characterId, action);
        } finally {
            resolvingAction = null;
            resolvingParticleEligible = false;
            resolvingC2Eligible = false;
            resolvingC6ScentEligible = false;
        }
    }

    private double skillValue(
            String baseKey,
            double t9,
            double c3) {
        return getTalentValue(
                constellation >= 3 ? baseKey + " C3" : baseKey,
                constellation >= 3 ? c3 : t9);
    }

    private double burstValue() {
        return getTalentValue(
                constellation >= 5
                        ? "Lumidouce Case Level 3 C5"
                        : "Lumidouce Case Level 3",
                constellation >= 5 ? 4.344 : 3.6924);
    }

    private StatsContainer captureLiveStats(double currentTime) {
        StatsContainer stats = getEffectiveStats(currentTime);
        if (initializedSimulator == null) {
            return stats;
        }
        for (Buff buff : initializedSimulator.getApplicableBuffs(this)) {
            if (!buff.isExpired(currentTime)) {
                buff.apply(stats, currentTime);
            }
        }
        return stats;
    }

    private void queueHit(
            CombatSimulator simulator,
            PendingHit hit) {
        pendingHits.add(hit);
        scheduleHit(simulator, hit);
    }

    private void scheduleHit(
            CombatSimulator simulator,
            PendingHit hit) {
        schedule(simulator, hit.time, activeSimulator -> {
            if (!pendingHits.remove(hit)) {
                return;
            }
            resolveHit(activeSimulator, hit);
        });
    }

    private void queueCommand(
            CombatSimulator simulator,
            PendingCommand command) {
        pendingCommands.add(command);
        scheduleCommand(simulator, command);
    }

    private void scheduleCommand(
            CombatSimulator simulator,
            PendingCommand command) {
        schedule(simulator, command.time, activeSimulator -> {
            if (!pendingCommands.remove(command)) {
                return;
            }
            switch (command.kind) {
                case SKILL_CASE_SPAWN:
                    deploySkillCase(activeSimulator);
                    break;
                case CASE_ATTACK:
                    emitCaseAttack(activeSimulator, command.generation);
                    break;
                case SCENT_CHECK:
                    checkScents(activeSimulator, command.generation);
                    break;
                case BURST_ENERGY:
                    spendBurstEnergy(activeSimulator.getCurrentTime());
                    break;
                case BURST_CASE_SPAWN:
                    deployBurstCase(activeSimulator);
                    break;
                case BURST_CASE_ATTACK:
                    emitBurstAttack(activeSimulator, command.generation);
                    break;
                case BURST_CASE_RESTORE:
                    restorePriorCase(activeSimulator, command.generation);
                    break;
                case PARTICLE:
                    activeSimulator.getEnergyDistributor()
                            .distributeParticles(
                                    Element.DENDRO,
                                    getTalentValue("Particle Count", 1.0),
                                    ParticleType.PARTICLE);
                    break;
                default:
                    throw new IllegalStateException(
                            "Unknown Emilie command " + command.kind);
            }
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

    private static List<PendingHit> copyHits(List<PendingHit> source) {
        List<PendingHit> copy = new ArrayList<>();
        for (PendingHit hit : source) {
            copy.add(hit.copy());
        }
        return copy;
    }

    private static List<PendingCommand> copyCommands(
            List<PendingCommand> source) {
        List<PendingCommand> copy = new ArrayList<>();
        for (PendingCommand command : source) {
            copy.add(command.copy());
        }
        return copy;
    }

    private enum HitKind {
        NORMAL,
        CHARGED,
        HIGH_PLUNGE,
        SKILL_SUMMON,
        CASE_LEVEL_ONE,
        CASE_LEVEL_TWO,
        BURST_CASE,
        CLEARDEW
    }

    private enum CommandKind {
        SKILL_CASE_SPAWN,
        CASE_ATTACK,
        SCENT_CHECK,
        BURST_ENERGY,
        BURST_CASE_SPAWN,
        BURST_CASE_ATTACK,
        BURST_CASE_RESTORE,
        PARTICLE
    }

    private static final class PendingHit {
        private final double time;
        private final HitKind kind;
        private final int index;
        private final StatsContainer snapshot;
        private final boolean c6Enhanced;

        private PendingHit(
                double time,
                HitKind kind,
                int index,
                StatsContainer snapshot,
                boolean c6Enhanced) {
            this.time = time;
            this.kind = kind;
            this.index = index;
            this.snapshot = snapshot == null
                    ? null : snapshot.merge(null);
            this.c6Enhanced = c6Enhanced;
        }

        private PendingHit copy() {
            return new PendingHit(
                    time, kind, index, snapshot, c6Enhanced);
        }
    }

    private static final class PendingCommand {
        private final double time;
        private final CommandKind kind;
        private final long generation;

        private PendingCommand(
                double time,
                CommandKind kind,
                long generation) {
            this.time = time;
            this.kind = kind;
            this.generation = generation;
        }

        private PendingCommand copy() {
            return new PendingCommand(time, kind, generation);
        }
    }

    private static final class EmilieState implements State {
        private final Emilie owner;
        private final int normalAttackStep;
        private final long caseGeneration;
        private final int caseLevel;
        private final int scentCount;
        private final double caseExpirationTime;
        private final double scentResetExpirationTime;
        private final double nextScentAllowedTime;
        private final double nextC1ScentAllowedTime;
        private final double nextParticleAllowedTime;
        private final double nextBurstTargetAllowedTime;
        private final int priorCaseLevel;
        private final double c6ExpirationTime;
        private final double nextC6ActivationTime;
        private final int c6ScentCount;
        private final List<PendingHit> pendingHits;
        private final List<PendingCommand> pendingCommands;

        private EmilieState(
                Emilie owner,
                int normalAttackStep,
                long caseGeneration,
                int caseLevel,
                int scentCount,
                double caseExpirationTime,
                double scentResetExpirationTime,
                double nextScentAllowedTime,
                double nextC1ScentAllowedTime,
                double nextParticleAllowedTime,
                double nextBurstTargetAllowedTime,
                int priorCaseLevel,
                double c6ExpirationTime,
                double nextC6ActivationTime,
                int c6ScentCount,
                List<PendingHit> pendingHits,
                List<PendingCommand> pendingCommands) {
            this.owner = owner;
            this.normalAttackStep = normalAttackStep;
            this.caseGeneration = caseGeneration;
            this.caseLevel = caseLevel;
            this.scentCount = scentCount;
            this.caseExpirationTime = caseExpirationTime;
            this.scentResetExpirationTime = scentResetExpirationTime;
            this.nextScentAllowedTime = nextScentAllowedTime;
            this.nextC1ScentAllowedTime = nextC1ScentAllowedTime;
            this.nextParticleAllowedTime = nextParticleAllowedTime;
            this.nextBurstTargetAllowedTime = nextBurstTargetAllowedTime;
            this.priorCaseLevel = priorCaseLevel;
            this.c6ExpirationTime = c6ExpirationTime;
            this.nextC6ActivationTime = nextC6ActivationTime;
            this.c6ScentCount = c6ScentCount;
            this.pendingHits = copyHits(pendingHits);
            this.pendingCommands = copyCommands(pendingCommands);
        }
    }
}

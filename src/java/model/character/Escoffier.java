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
import model.entity.ArtifactSet;
import model.entity.Character;
import model.entity.SimulatorInitializedCharacterEffect;
import model.entity.SnapshotAwareCharacterEffect;
import model.entity.SwitchAwareCharacter;
import model.entity.TargetDependentTeamEffect;
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
 * Escoffier's stationary fixed-target Cooking Mek offensive/support slice.
 *
 * <p>Level-90 stats, Talent 9/12 multipliers, frames, Skill cadence, particles,
 * Burst, A4, and representable C1-C6 branches follow pinned gcsim
 * {@code ef41805d}. Skill and C6 attacks share the source's private 1.5-second
 * application group, while delayed travel hits own emission-time stats.</p>
 *
 * <p>Healing and player HP, Hold Skill collection and movement, geometry,
 * multi-target and random targeting, stamina, hitlag, low Plunge, and defensive
 * state are excluded. C4 is inactive because its Energy branch is reached only
 * through the excluded healing sequence.</p>
 */
public final class Escoffier extends Character implements
        SimulatorInitializedCharacterEffect,
        SnapshotAwareCharacterEffect,
        SwitchAwareCharacter,
        TargetDependentTeamEffect {
    private static final double FRAME = 1.0 / 60.0;
    private static final double EPSILON = 1e-9;
    private static final int[][] NORMAL_HIT_FRAMES = {
        { 7 }, { 16 }, { 27, 40 }
    };
    private static final int[] NORMAL_DURATIONS = { 17, 29, 62 };
    private static final String[][] NORMAL_KEYS = {
        { "N1" }, { "N2" }, { "N3-1", "N3-2" }
    };
    private static final double[][] NORMAL_MULTIPLIERS = {
        { 0.947099 }, { 0.874388 }, { 0.606270, 0.740996 }
    };
    private static final double[] A4_SHRED = {
        0.0, 0.05, 0.10, 0.15, 0.55
    };

    private CombatSimulator initializedSimulator;
    private int normalAttackStep;
    private long skillGeneration;
    private double skillExpirationTime = Double.NEGATIVE_INFINITY;
    private double nextSurgingBladeAllowedTime = Double.NEGATIVE_INFINITY;
    private double a4ExpirationTime = Double.NEGATIVE_INFINITY;
    private double a4Shred;
    private double c2ExpirationTime = Double.NEGATIVE_INFINITY;
    private int c2Count;
    private double nextC6AllowedTime = Double.NEGATIVE_INFINITY;
    private int c6Count;
    private List<PendingHit> pendingHits = new ArrayList<>();
    private List<PendingCommand> pendingCommands = new ArrayList<>();

    /** Constructs repository-default C6 Escoffier. */
    public Escoffier(Weapon weapon, ArtifactSet artifacts) {
        this(weapon, artifacts, TalentDataManager.getInstance(), 6);
    }

    /** Constructs Escoffier at an explicit constellation. */
    public Escoffier(
            Weapon weapon,
            ArtifactSet artifacts,
            int constellation) {
        this(weapon, artifacts, TalentDataManager.getInstance(), constellation);
    }

    /**
     * Constructs Escoffier with injectable static talent data.
     *
     * @param weapon equipped polearm, or {@code null}
     * @param artifacts equipped artifact set, or {@code null}
     * @param talentData static character-data source
     * @param constellation constellation level in {@code [0, 6]}
     */
    public Escoffier(
            Weapon weapon,
            ArtifactSet artifacts,
            TalentDataSource talentData,
            int constellation) {
        super(talentData);
        if (constellation < 0 || constellation > 6) {
            throw new IllegalArgumentException(
                    "Escoffier constellation must be between 0 and 6");
        }
        name = "Escoffier";
        characterId = CharacterId.ESCOFFIER;
        element = Element.CRYO;
        this.weapon = weapon;
        this.artifacts = new ArtifactSet[] { artifacts };
        this.constellation = constellation;
        baseStats.set(StatType.BASE_HP,
                getTalentValue("Base HP", 13348.0));
        baseStats.set(StatType.BASE_ATK,
                getTalentValue("Base ATK", 347.0));
        baseStats.set(StatType.BASE_DEF,
                getTalentValue("Base DEF", 732.0));
        baseStats.add(StatType.CRIT_RATE,
                getTalentValue("Ascension CRIT Rate", 0.192));
        setSkillCD(getTalentValue("Skill Cooldown", 15.0));
        setBurstCD(getTalentValue("Burst Cooldown", 15.0));
    }

    /** Binds hit-driven C2 and C6 behavior to one owning simulator. */
    @Override
    public void initializeForSimulator(CombatSimulator simulator) {
        if (simulator == null) {
            throw new IllegalArgumentException(
                    "Escoffier simulator is required");
        }
        if (!simulator.getPartyMembers().contains(this)) {
            throw new IllegalArgumentException(
                    "Escoffier must belong to the simulator party");
        }
        if (initializedSimulator != null
                && initializedSimulator != simulator) {
            throw new IllegalStateException(
                    "Escoffier cannot be reused across simulators");
        }
        if (initializedSimulator == simulator) {
            return;
        }
        initializedSimulator = simulator;
        simulator.addDamageListener(this::handleAcceptedDamage);
    }

    /** Captures owner state and all reconstructable delayed work. */
    @Override
    public State captureCharacterState() {
        return new EscoffierState(
                this,
                normalAttackStep,
                skillGeneration,
                skillExpirationTime,
                nextSurgingBladeAllowedTime,
                a4ExpirationTime,
                a4Shred,
                c2ExpirationTime,
                c2Count,
                nextC6AllowedTime,
                c6Count,
                pendingHits,
                pendingCommands);
    }

    /** Accepts state captured from this exact Escoffier instance only. */
    @Override
    public boolean acceptsCharacterState(State state) {
        return state instanceof EscoffierState
                && ((EscoffierState) state).owner == this;
    }

    /** Restores state and reconstructs each surviving delayed event once. */
    @Override
    public void restoreCharacterState(
            State state,
            CombatSimulator simulator) {
        if (!acceptsCharacterState(state)) {
            throw new IllegalArgumentException("Unexpected Escoffier state");
        }
        initializeForSimulator(simulator);
        EscoffierState restored = (EscoffierState) state;
        normalAttackStep = restored.normalAttackStep;
        skillGeneration = restored.skillGeneration;
        skillExpirationTime = restored.skillExpirationTime;
        nextSurgingBladeAllowedTime =
                restored.nextSurgingBladeAllowedTime;
        a4ExpirationTime = restored.a4ExpirationTime;
        a4Shred = restored.a4Shred;
        c2ExpirationTime = restored.c2ExpirationTime;
        c2Count = restored.c2Count;
        nextC6AllowedTime = restored.nextC6AllowedTime;
        c6Count = restored.c6Count;
        pendingHits = copyHits(restored.pendingHits);
        pendingCommands = copyCommands(restored.pendingCommands);
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

    /** Returns Escoffier's 60-Energy Burst cost. */
    @Override
    public double getEnergyCost() {
        return getTalentValue("Energy Cost", 60.0);
    }

    /** Escoffier's represented passives are action-conditional. */
    @Override
    public void applyPassive(StatsContainer stats) {
    }

    /** Resets the three-step Normal string on switch-out. */
    @Override
    public void onSwitchOut(CombatSimulator simulator) {
        normalAttackStep = 0;
    }

    /** Resets the three-step Normal string on switch-in. */
    @Override
    public void onSwitchIn(CombatSimulator simulator) {
        normalAttackStep = 0;
    }

    /** Returns whether the source Skill deployment window is active. */
    public boolean isCookingMekActive(double currentTime) {
        return currentTime + EPSILON < skillExpirationTime;
    }

    /** Returns the remaining C2 Cold Dish trigger count. */
    public int getC2Count(double currentTime) {
        return currentTime + EPSILON < c2ExpirationTime ? c2Count : 0;
    }

    /** Returns the remaining C6 follow-up count. */
    public int getC6Count() {
        return c6Count;
    }

    /** Returns the number of unresolved owner hits. */
    public int getPendingHitCount() {
        return pendingHits.size();
    }

    /** Reports that the source's offensive Surging Blade hit is represented. */
    public boolean isSurgingBladeRepresented() {
        return true;
    }

    /** Reports that low Plunge is outside this bounded slice. */
    public boolean isLowPlungeRepresented() {
        return false;
    }

    /** Reports that C4's healing-driven Energy branch is excluded. */
    public boolean isC4HealingEnergyRepresented() {
        return false;
    }

    /** Adds C2's live-ATK flat damage before an eligible ally Cryo hit. */
    @Override
    public void applyTargetDependentTeamStats(
            StatsContainer stats,
            Character attacker,
            model.entity.Enemy target,
            AttackAction action,
            double currentTime) {
        if (qualifiesForC2(attacker, target, action, currentTime)) {
            stats.add(
                    StatType.FLAT_DMG_BONUS,
                    captureLiveStats(currentTime).getTotalAtk()
                            * getTalentValue("C2 ATK Ratio", 2.4));
        }
        if (action != null
                && action.hasStatSnapshot()
                && currentTime + EPSILON < a4ExpirationTime) {
            stats.add(StatType.CRYO_RES_SHRED, a4Shred);
            stats.add(StatType.HYDRO_RES_SHRED, a4Shred);
        }
    }

    /** Dispatches Escoffier's represented typed action set. */
    @Override
    public void onAction(
            CharacterActionRequest request,
            CombatSimulator simulator) {
        if (request == null) {
            throw new IllegalArgumentException(
                    "Escoffier action is required");
        }
        initializeForSimulator(simulator);
        if (request.getKey() == CharacterActionKey.SKILL
                && request.getSkillMode() != SkillActionMode.PRESS) {
            throw new IllegalArgumentException(
                    "Escoffier supports Press Skill only");
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
                lowTemperatureCooking(simulator);
                break;
            case BURST:
                scoringCuts(simulator);
                break;
            default:
                throw new IllegalArgumentException(
                        "Unsupported action for Escoffier: "
                                + request.getKey());
        }
    }

    private void normalAttack(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        int step = normalAttackStep;
        for (int hit = 0; hit < NORMAL_HIT_FRAMES[step].length; hit++) {
            queueHit(simulator, new PendingHit(
                    castTime + NORMAL_HIT_FRAMES[step][hit] * FRAME,
                    HitKind.NORMAL,
                    step,
                    hit,
                    null));
        }
        normalAttackStep = (normalAttackStep + 1)
                % NORMAL_HIT_FRAMES.length;
        simulator.advanceTime(NORMAL_DURATIONS[step] * FRAME);
    }

    private void chargedAttack(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        queueHit(simulator, new PendingHit(
                castTime + 20.0 * FRAME,
                HitKind.CHARGED,
                0,
                0,
                null));
        simulator.advanceTime(65.0 * FRAME);
    }

    private void highPlunge(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        queueHit(simulator, new PendingHit(
                castTime + 43.0 * FRAME,
                HitKind.HIGH_PLUNGE,
                0,
                0,
                null));
        simulator.advanceTime(77.0 * FRAME);
    }

    private void lowTemperatureCooking(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        long generation = ++skillGeneration;
        int tickCount = (int) getTalentValue("Skill Tick Count", 21.0);
        double firstTickFrames = getTalentValue(
                "Skill First Tick Frames", 148.0);
        double intervalFrames = getTalentValue(
                "Skill Tick Interval Frames", 58.5);
        double lastTickFrames = firstTickFrames
                + Math.ceil(intervalFrames * (tickCount - 1));
        skillExpirationTime = castTime + lastTickFrames * FRAME;
        queueCommand(simulator, new PendingCommand(
                castTime + getTalentValue(
                        "Skill Cooldown Delay Frames", 22.0) * FRAME,
                CommandKind.SKILL_COOLDOWN,
                0L));
        queueHit(simulator, new PendingHit(
                castTime + 23.0 * FRAME,
                HitKind.SKILL_INITIAL,
                0,
                0,
                null));
        queueCommand(simulator, new PendingCommand(
                castTime + 83.0 * FRAME,
                CommandKind.SURGING_BLADE,
                0L));
        for (int tick = 0; tick < tickCount; tick++) {
            double emissionFrames = firstTickFrames
                    + Math.ceil(intervalFrames * tick);
            queueCommand(simulator, new PendingCommand(
                    castTime + emissionFrames * FRAME,
                    CommandKind.SKILL_TICK,
                    generation));
        }
        activateC1(simulator, castTime);
        activateC2(castTime);
        activateC6();
        simulator.advanceTime(35.0 * FRAME);
    }

    private void scoringCuts(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        markBurstCooldownUsed(
                castTime, simulator.getApplicableBuffs(this));
        queueCommand(simulator, new PendingCommand(
                castTime + 5.0 * FRAME,
                CommandKind.BURST_ENERGY,
                0L));
        queueHit(simulator, new PendingHit(
                castTime + 92.0 * FRAME,
                HitKind.BURST,
                0,
                0,
                null));
        activateC1(simulator, castTime);
        simulator.advanceTime(110.0 * FRAME);
    }

    private void activateC1(
            CombatSimulator simulator,
            double currentTime) {
        if (constellation < 1 || !isCryoHydroOnlyParty(simulator)) {
            return;
        }
        simulator.applyTeamBuffNoStack(new SimpleBuff(
                "Escoffier Dance of Seven-Tier Frost",
                BuffId.ESCOFFIER_C1_CRYO_CRIT_DMG,
                getTalentValue("C1 Duration", 15.0),
                currentTime,
                stats -> stats.add(
                        StatType.CRYO_CRIT_DMG,
                        getTalentValue("C1 Cryo CRIT DMG", 0.6)))
                .sourcedBy(characterId));
    }

    private void activateC2(double currentTime) {
        if (constellation < 2) {
            return;
        }
        c2ExpirationTime = currentTime
                + getTalentValue("C2 Duration", 15.0);
        c2Count = (int) getTalentValue("C2 Stack Count", 5.0);
    }

    private void activateC6() {
        if (constellation < 6) {
            return;
        }
        c6Count = (int) getTalentValue("C6 Trigger Count", 6.0);
    }

    private boolean isCryoHydroOnlyParty(CombatSimulator simulator) {
        for (Character member : simulator.getPartyMembers()) {
            if (member.getElement() != Element.CRYO
                    && member.getElement() != Element.HYDRO) {
                return false;
            }
        }
        return true;
    }

    private void handleAcceptedDamage(
            Character actor,
            AttackAction action,
            double damage,
            double time) {
        if (damage <= 0.0 || initializedSimulator == null) {
            return;
        }
        if (qualifiesForC2(
                actor,
                initializedSimulator.getEnemy(),
                action,
                time)) {
            c2Count = Math.max(0, c2Count - 1);
        }
        if (!qualifiesForC6(actor, action, time)) {
            return;
        }
        nextC6AllowedTime = time
                + getTalentValue("C6 Trigger Cooldown", 0.5);
        c6Count--;
        double travel = getTalentValue("Skill Travel Frames", 5.0)
                * FRAME;
        queueHit(initializedSimulator, new PendingHit(
                time + travel,
                HitKind.C6_FOLLOW_UP,
                0,
                0,
                captureLiveStats(time)));
    }

    private boolean qualifiesForC2(
            Character attacker,
            model.entity.Enemy target,
            AttackAction action,
            double currentTime) {
        if (constellation < 2
                || initializedSimulator == null
                || attacker == null
                || attacker == this
                || target == null
                || action == null
                || initializedSimulator.getActiveCharacter() != attacker
                || currentTime + EPSILON >= c2ExpirationTime
                || c2Count <= 0
                || action.getElement() != Element.CRYO
                || !action.isHitEffectTrigger()
                || action.getDamagePercent() <= 0.0) {
            return false;
        }
        ActionType type = action.getActionType();
        return type == ActionType.NORMAL
                || type == ActionType.CHARGE
                || type == ActionType.PLUNGE
                || type == ActionType.SKILL
                || type == ActionType.BURST
                || action.isCountsAsSkillDmg()
                || action.isCountsAsBurstDmg();
    }

    private boolean qualifiesForC6(
            Character attacker,
            AttackAction action,
            double currentTime) {
        if (constellation < 6
                || attacker == null
                || action == null
                || initializedSimulator.getActiveCharacter() != attacker
                || !isCookingMekActive(currentTime)
                || c6Count <= 0
                || currentTime + EPSILON < nextC6AllowedTime
                || !action.isHitEffectTrigger()) {
            return false;
        }
        ActionType type = action.getActionType();
        return type == ActionType.NORMAL
                || type == ActionType.CHARGE
                || type == ActionType.PLUNGE;
    }

    private void applyA4(
            CombatSimulator simulator,
            double currentTime) {
        int cryoHydroCount = 0;
        for (Character member : simulator.getPartyMembers()) {
            if (member.getElement() == Element.CRYO
                    || member.getElement() == Element.HYDRO) {
                cryoHydroCount++;
            }
        }
        int boundedCount = Math.min(4, cryoHydroCount);
        double shred = A4_SHRED[boundedCount];
        a4Shred = shred;
        a4ExpirationTime = currentTime
                + getTalentValue("A4 Duration", 12.0);
        simulator.applyTeamBuffNoStack(new SimpleBuff(
                "Escoffier Better to Salivate Than Medicate",
                BuffId.ESCOFFIER_A4_CRYO_HYDRO_RES_SHRED,
                getTalentValue("A4 Duration", 12.0),
                currentTime,
                stats -> {
                    stats.add(StatType.CRYO_RES_SHRED, shred);
                    stats.add(StatType.HYDRO_RES_SHRED, shred);
                }).sourcedBy(characterId));
    }

    private void resolveHit(
            CombatSimulator simulator,
            PendingHit hit) {
        switch (hit.kind) {
            case NORMAL:
                resolveNormal(simulator, hit);
                break;
            case CHARGED:
                performHit(
                        simulator,
                        hit,
                        "Kitchen Skills Charged Attack",
                        getTalentValue("Charged Attack", 2.120360),
                        Element.PHYSICAL,
                        StatType.CHARGED_ATTACK_DMG_BONUS,
                        ActionType.CHARGE,
                        ICDType.Standard,
                        ICDTag.ChargedAttack,
                        0.0,
                        false,
                        false);
                break;
            case HIGH_PLUNGE:
                performHit(
                        simulator,
                        hit,
                        "Kitchen Skills High Plunge",
                        getTalentValue("High Plunge", 2.933586),
                        Element.PHYSICAL,
                        StatType.PLUNGING_ATTACK_DMG_BONUS,
                        ActionType.PLUNGE,
                        ICDType.None,
                        ICDTag.PlungeAttack,
                        0.0,
                        false,
                        false);
                break;
            case SKILL_INITIAL:
                performHit(
                        simulator,
                        hit,
                        "Low-Temperature Cooking",
                        skillValue(
                                "Low-Temperature Cooking",
                                0.8568,
                                1.008),
                        Element.CRYO,
                        StatType.SKILL_DMG_BONUS,
                        ActionType.SKILL,
                        ICDType.ESCOFFIER_SKILL,
                        ICDTag.ESCOFFIER_SKILL,
                        1.0,
                        true,
                        true);
                break;
            case FROSTY_PARFAIT:
                performHit(
                        simulator,
                        hit,
                        "Frosty Parfait",
                        skillValue("Frosty Parfait", 2.04, 2.4),
                        Element.CRYO,
                        StatType.SKILL_DMG_BONUS,
                        ActionType.SKILL,
                        ICDType.ESCOFFIER_SKILL,
                        ICDTag.ESCOFFIER_SKILL,
                        1.0,
                        false,
                        true);
                break;
            case SURGING_BLADE:
                performHit(
                        simulator,
                        hit,
                        "Surging Blade",
                        skillValue("Surging Blade", 0.5712, 0.672),
                        Element.CRYO,
                        StatType.SKILL_DMG_BONUS,
                        ActionType.SKILL,
                        ICDType.None,
                        ICDTag.None,
                        0.0,
                        false,
                        true);
                break;
            case BURST:
                performHit(
                        simulator,
                        hit,
                        "Scoring Cuts",
                        burstValue(),
                        Element.CRYO,
                        StatType.BURST_DMG_BONUS,
                        ActionType.BURST,
                        ICDType.None,
                        ICDTag.None,
                        2.0,
                        false,
                        true);
                break;
            case C6_FOLLOW_UP:
                performHit(
                        simulator,
                        hit,
                        "Special-Grade Frozen Parfait",
                        getTalentValue("C6 ATK Ratio", 5.0),
                        Element.CRYO,
                        StatType.SKILL_DMG_BONUS,
                        ActionType.SKILL,
                        ICDType.ESCOFFIER_SKILL,
                        ICDTag.ESCOFFIER_SKILL,
                        1.0,
                        false,
                        true);
                break;
            default:
                throw new IllegalStateException(
                        "Unknown Escoffier hit kind " + hit.kind);
        }
    }

    private void resolveNormal(
            CombatSimulator simulator,
            PendingHit hit) {
        String key = NORMAL_KEYS[hit.index][hit.subIndex];
        performHit(
                simulator,
                hit,
                "Kitchen Skills " + key,
                getTalentValue(
                        key,
                        NORMAL_MULTIPLIERS[hit.index][hit.subIndex]),
                Element.PHYSICAL,
                StatType.NORMAL_ATTACK_DMG_BONUS,
                ActionType.NORMAL,
                ICDType.Standard,
                ICDTag.NormalAttack,
                0.0,
                false,
                false);
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
            boolean a4Eligible) {
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
        if (hit.snapshot != null) {
            action.setStatSnapshot(hit.snapshot);
        }
        simulator.performActionWithoutTimeAdvance(characterId, action);
        if (simulator.getEnemy() == null) {
            return;
        }
        double currentTime = simulator.getCurrentTime();
        if (particleEligible) {
            queueCommand(simulator, new PendingCommand(
                    currentTime + getTalentValue(
                            "Particle Travel Frames", 100.0) * FRAME,
                    CommandKind.PARTICLE,
                    0L));
        }
        if (a4Eligible) {
            applyA4(simulator, currentTime);
        }
    }

    private double skillValue(
            String baseKey,
            double talentNine,
            double constellationThree) {
        return getTalentValue(
                constellation >= 3 ? baseKey + " C3" : baseKey,
                constellation >= 3
                        ? constellationThree : talentNine);
    }

    private double burstValue() {
        return getTalentValue(
                constellation >= 5
                        ? "Scoring Cuts C5" : "Scoring Cuts",
                constellation >= 5 ? 11.856 : 10.0776);
    }

    private StatsContainer captureLiveStats(double currentTime) {
        StatsContainer stats = getEffectiveStats(currentTime);
        if (initializedSimulator == null) {
            return stats;
        }
        for (Buff buff : initializedSimulator.getApplicableBuffs(this)) {
            if (buff.getId()
                    != BuffId.ESCOFFIER_A4_CRYO_HYDRO_RES_SHRED
                    && !buff.isExpired(currentTime)) {
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
                case SKILL_COOLDOWN:
                    markSkillUsed(
                            activeSimulator.getCurrentTime(),
                            activeSimulator.getApplicableBuffs(this));
                    break;
                case SKILL_TICK:
                    emitSkillTick(activeSimulator, command.generation);
                    break;
                case SURGING_BLADE:
                    emitSurgingBlade(activeSimulator);
                    break;
                case PARTICLE:
                    activeSimulator.getEnergyDistributor()
                            .distributeParticles(
                                    Element.CRYO,
                                    getTalentValue("Particle Count", 4.0),
                                    ParticleType.PARTICLE);
                    break;
                case BURST_ENERGY:
                    spendBurstEnergy(activeSimulator.getCurrentTime());
                    break;
                default:
                    throw new IllegalStateException(
                            "Unknown Escoffier command " + command.kind);
            }
        });
    }

    private void emitSkillTick(
            CombatSimulator simulator,
            long generation) {
        if (generation != skillGeneration) {
            return;
        }
        double currentTime = simulator.getCurrentTime();
        queueHit(simulator, new PendingHit(
                currentTime + getTalentValue(
                        "Skill Travel Frames", 5.0) * FRAME,
                HitKind.FROSTY_PARFAIT,
                0,
                0,
                captureLiveStats(currentTime)));
    }

    private void emitSurgingBlade(CombatSimulator simulator) {
        double currentTime = simulator.getCurrentTime();
        if (currentTime + EPSILON < nextSurgingBladeAllowedTime) {
            return;
        }
        nextSurgingBladeAllowedTime = currentTime
                + getTalentValue("Surging Blade Cooldown", 10.0);
        queueHit(simulator, new PendingHit(
                currentTime,
                HitKind.SURGING_BLADE,
                0,
                0,
                captureLiveStats(currentTime)));
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
        SKILL_INITIAL,
        FROSTY_PARFAIT,
        SURGING_BLADE,
        BURST,
        C6_FOLLOW_UP
    }

    private enum CommandKind {
        SKILL_COOLDOWN,
        SKILL_TICK,
        SURGING_BLADE,
        PARTICLE,
        BURST_ENERGY
    }

    private static final class PendingHit {
        private final double time;
        private final HitKind kind;
        private final int index;
        private final int subIndex;
        private final StatsContainer snapshot;

        private PendingHit(
                double time,
                HitKind kind,
                int index,
                int subIndex,
                StatsContainer snapshot) {
            this.time = time;
            this.kind = kind;
            this.index = index;
            this.subIndex = subIndex;
            this.snapshot = snapshot == null
                    ? null : snapshot.merge(null);
        }

        private PendingHit copy() {
            return new PendingHit(
                    time, kind, index, subIndex, snapshot);
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

    private static final class EscoffierState implements State {
        private final Escoffier owner;
        private final int normalAttackStep;
        private final long skillGeneration;
        private final double skillExpirationTime;
        private final double nextSurgingBladeAllowedTime;
        private final double a4ExpirationTime;
        private final double a4Shred;
        private final double c2ExpirationTime;
        private final int c2Count;
        private final double nextC6AllowedTime;
        private final int c6Count;
        private final List<PendingHit> pendingHits;
        private final List<PendingCommand> pendingCommands;

        private EscoffierState(
                Escoffier owner,
                int normalAttackStep,
                long skillGeneration,
                double skillExpirationTime,
                double nextSurgingBladeAllowedTime,
                double a4ExpirationTime,
                double a4Shred,
                double c2ExpirationTime,
                int c2Count,
                double nextC6AllowedTime,
                int c6Count,
                List<PendingHit> pendingHits,
                List<PendingCommand> pendingCommands) {
            this.owner = owner;
            this.normalAttackStep = normalAttackStep;
            this.skillGeneration = skillGeneration;
            this.skillExpirationTime = skillExpirationTime;
            this.nextSurgingBladeAllowedTime =
                    nextSurgingBladeAllowedTime;
            this.a4ExpirationTime = a4ExpirationTime;
            this.a4Shred = a4Shred;
            this.c2ExpirationTime = c2ExpirationTime;
            this.c2Count = c2Count;
            this.nextC6AllowedTime = nextC6AllowedTime;
            this.c6Count = c6Count;
            this.pendingHits = copyHits(pendingHits);
            this.pendingCommands = copyCommands(pendingCommands);
        }
    }
}

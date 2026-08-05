package model.character;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
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
 * Skirk's deterministic fixed-target Seven-Phase Flash slice.
 *
 * <p>Level-90 sword basics, high Plunge, local Serpent's Subtlety, Press
 * Skill and its Normal/Charged/Plunge replacements, both Burst branches,
 * particles, A4, and representable offensive C1-C6 behavior follow pinned
 * gcsim revision {@code ef41805d}. A1 Void Rifts enter through
 * {@link #recordRepresentedVoidRift(CombatSimulator)} so callers explicitly
 * confirm a source-backed fixed-target trigger without inventing positions.</p>
 *
 * <p>Void Rift and crystal geometry, automatic reaction-to-rift team
 * plumbing, multi-target collection, movement, random targeting, current HP,
 * healing, hitlag, stamina, low Plunge, exploration, and defensive state fail
 * closed. Hold Skill is excluded because its travel and collection behavior
 * depends on those unsupported systems.</p>
 */
public final class Skirk extends Character implements
        SimulatorInitializedCharacterEffect,
        SnapshotAwareCharacterEffect,
        SwitchAwareCharacter {
    private static final double FRAME = 1.0 / 60.0;
    private static final double EPSILON = 1e-9;
    private static final int[] NORMAL_DURATIONS = { 27, 25, 43, 23, 72 };
    private static final int[][] NORMAL_HIT_FRAMES = {
        { 13 }, { 7 }, { 8, 22 }, { 11 }, { 35 }
    };
    private static final int[] FLASH_NORMAL_DURATIONS = {
        30, 43, 42, 60, 72
    };
    private static final int[][] FLASH_NORMAL_HIT_FRAMES = {
        { 12 }, { 11 }, { 11, 23 }, { 11, 27 }, { 25 }
    };
    private static final int[] RUIN_HIT_FRAMES = {
        109, 111, 114, 125, 135
    };
    private static final double[] NORMAL_T9 = {
        1.001720, 0.914820, 0.595660, 1.117060, 1.523120
    };
    private static final double[] A4_ATTACK_MULTIPLIERS = {
        1.0, 1.1, 1.2, 1.7
    };
    private static final double[] A4_BURST_MULTIPLIERS = {
        1.0, 1.05, 1.15, 1.60
    };

    private CombatSimulator initializedSimulator;
    private int normalAttackStep;
    private boolean previousNormalUsedFlash;
    private long skillGeneration;
    private boolean flashActive;
    private double flashActiveUntil = Double.NEGATIVE_INFINITY;
    private double serpentsSubtlety;
    private double drainPausedUntil = Double.NEGATIVE_INFINITY;
    private double nextParticleAllowedTime = Double.NEGATIVE_INFINITY;
    private double c2AttackBuffUntil = Double.NEGATIVE_INFINITY;
    private double extinctionActiveUntil = Double.NEGATIVE_INFINITY;
    private double extinctionDamageBonus;
    private int extinctionHitsRemaining;
    private double nextExtinctionAllowedTime = Double.NEGATIVE_INFINITY;
    private boolean talentPassiveActive;
    private Map<CharacterId, Double> a4Expirations =
            new EnumMap<>(CharacterId.class);
    private List<Double> voidRiftExpirations = new ArrayList<>();
    private List<Double> c6StackExpirations = new ArrayList<>();
    private double nextC6NormalAllowedTime = Double.NEGATIVE_INFINITY;
    private AttackAction resolvingAction;
    private int resolvingHitStep;
    private boolean resolvingC6Eligible;
    private boolean resolvingRiftAbsorption;
    private List<PendingHit> pendingHits = new ArrayList<>();
    private List<PendingCommand> pendingCommands = new ArrayList<>();

    /** Constructs repository-default C6 Skirk. */
    public Skirk(Weapon weapon, ArtifactSet artifacts) {
        this(weapon, artifacts, TalentDataManager.getInstance(), 6);
    }

    /** Constructs Skirk at an explicit constellation. */
    public Skirk(
            Weapon weapon,
            ArtifactSet artifacts,
            int constellation) {
        this(weapon, artifacts, TalentDataManager.getInstance(), constellation);
    }

    /**
     * Constructs Skirk with injectable static talent data.
     *
     * @param weapon equipped sword, or {@code null}
     * @param artifacts equipped artifact set, or {@code null}
     * @param talentData static character-data source
     * @param constellation constellation level in {@code [0, 6]}
     */
    public Skirk(
            Weapon weapon,
            ArtifactSet artifacts,
            TalentDataSource talentData,
            int constellation) {
        super(talentData);
        if (constellation < 0 || constellation > 6) {
            throw new IllegalArgumentException(
                    "Skirk constellation must be between 0 and 6");
        }
        name = "Skirk";
        characterId = CharacterId.SKIRK;
        element = Element.CRYO;
        this.weapon = weapon;
        this.artifacts = new ArtifactSet[] { artifacts };
        this.constellation = constellation;
        baseStats.set(StatType.BASE_HP,
                getTalentValue("Base HP", 12417.0));
        baseStats.set(StatType.BASE_ATK,
                getTalentValue("Base ATK", 359.0));
        baseStats.set(StatType.BASE_DEF,
                getTalentValue("Base DEF", 806.0));
        baseStats.add(StatType.CRIT_DMG,
                getTalentValue("Ascension CRIT DMG", 0.384));
        serpentsSubtlety = getTalentValue(
                "Initial Serpents Subtlety", 100.0);
        setSkillCD(getTalentValue("Skill Cooldown", 8.0));
        setBurstCD(getTalentValue("Burst Cooldown", 15.0));
    }

    /** Binds Skirk's accepted-hit and A4 listeners to one simulator. */
    @Override
    public void initializeForSimulator(CombatSimulator simulator) {
        if (simulator == null) {
            throw new IllegalArgumentException("Skirk simulator is required");
        }
        if (!simulator.getPartyMembers().contains(this)) {
            throw new IllegalArgumentException(
                    "Skirk must belong to the simulator party");
        }
        if (initializedSimulator != null
                && initializedSimulator != simulator) {
            throw new IllegalStateException(
                    "Skirk cannot be reused across simulators");
        }
        if (initializedSimulator == simulator) {
            return;
        }
        initializedSimulator = simulator;
        talentPassiveActive = hasExclusiveCryoHydroParty(simulator);
        simulator.addDamageListener((actor, action, damage, time) ->
                handleAcceptedDamage(
                        simulator, actor, action, damage, time));
    }

    /** Captures every local resource, gate, expiry, and delayed event. */
    @Override
    public State captureCharacterState() {
        return new SkirkState(
                this,
                normalAttackStep,
                previousNormalUsedFlash,
                skillGeneration,
                flashActive,
                flashActiveUntil,
                serpentsSubtlety,
                drainPausedUntil,
                nextParticleAllowedTime,
                c2AttackBuffUntil,
                extinctionActiveUntil,
                extinctionDamageBonus,
                extinctionHitsRemaining,
                nextExtinctionAllowedTime,
                talentPassiveActive,
                a4Expirations,
                voidRiftExpirations,
                c6StackExpirations,
                nextC6NormalAllowedTime,
                pendingHits,
                pendingCommands);
    }

    /** Accepts state captured from this exact Skirk instance only. */
    @Override
    public boolean acceptsCharacterState(State state) {
        return state instanceof SkirkState
                && ((SkirkState) state).owner == this;
    }

    /** Restores Skirk-owned state and reconstructs unresolved events once. */
    @Override
    public void restoreCharacterState(
            State state,
            CombatSimulator simulator) {
        if (!acceptsCharacterState(state)) {
            throw new IllegalArgumentException("Unexpected Skirk state");
        }
        initializeForSimulator(simulator);
        SkirkState restored = (SkirkState) state;
        normalAttackStep = restored.normalAttackStep;
        previousNormalUsedFlash = restored.previousNormalUsedFlash;
        skillGeneration = restored.skillGeneration;
        flashActive = restored.flashActive;
        flashActiveUntil = restored.flashActiveUntil;
        serpentsSubtlety = restored.serpentsSubtlety;
        drainPausedUntil = restored.drainPausedUntil;
        nextParticleAllowedTime = restored.nextParticleAllowedTime;
        c2AttackBuffUntil = restored.c2AttackBuffUntil;
        extinctionActiveUntil = restored.extinctionActiveUntil;
        extinctionDamageBonus = restored.extinctionDamageBonus;
        extinctionHitsRemaining = restored.extinctionHitsRemaining;
        nextExtinctionAllowedTime = restored.nextExtinctionAllowedTime;
        talentPassiveActive = restored.talentPassiveActive;
        a4Expirations = new EnumMap<>(restored.a4Expirations);
        voidRiftExpirations = new ArrayList<>(restored.voidRiftExpirations);
        c6StackExpirations = new ArrayList<>(restored.c6StackExpirations);
        nextC6NormalAllowedTime = restored.nextC6NormalAllowedTime;
        pendingHits = copyHits(restored.pendingHits);
        pendingCommands = copyCommands(restored.pendingCommands);
        resolvingAction = null;
        resolvingHitStep = 0;
        resolvingC6Eligible = false;
        resolvingRiftAbsorption = false;
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

    /** Skirk replaces Energy with Serpent's Subtlety. */
    @Override
    public double getEnergyCost() {
        return 0.0;
    }

    /** Skirk has no unconditional stat passive. */
    @Override
    public void applyPassive(StatsContainer stats) {
    }

    /** Exits Seven-Phase Flash and starts its cooldown on switch-out. */
    @Override
    public void onSwitchOut(CombatSimulator simulator) {
        normalAttackStep = 0;
        previousNormalUsedFlash = false;
        if (isFlashActiveAt(simulator.getCurrentTime())) {
            exitFlash(simulator, skillGeneration);
        }
    }

    /** Resets Skirk's Normal string when she returns on field. */
    @Override
    public void onSwitchIn(CombatSimulator simulator) {
        normalAttackStep = 0;
        previousNormalUsedFlash = false;
    }

    /** Returns the current local Serpent's Subtlety total in {@code [0, 100]}. */
    public double getSerpentsSubtlety() {
        return serpentsSubtlety;
    }

    /** Returns whether Seven-Phase Flash is active at the simulator clock. */
    public boolean isSevenPhaseFlashActive() {
        return initializedSimulator != null
                && isFlashActiveAt(initializedSimulator.getCurrentTime());
    }

    /** Returns active A4 contributors after expiring stale entries. */
    public int getDeathsCrossingStacks(double currentTime) {
        expireA4(currentTime);
        return Math.min(3, a4Expirations.size());
    }

    /** Returns source-confirmed, positionless Void Rifts still available. */
    public int getRepresentedVoidRiftCount(double currentTime) {
        expireVoidRifts(currentTime);
        return voidRiftExpirations.size();
    }

    /** Returns unexpired C6 Havoc: Sever stacks. */
    public int getC6StackCount(double currentTime) {
        expireC6Stacks(currentTime);
        return c6StackExpirations.size();
    }

    /**
     * Records one confirmed A1 Void Rift without representing its position.
     *
     * <p>The queue follows the source's three-rift overwrite cap and 1054-frame
     * lifetime. Collection remains explicit through Flash Charged Attack or
     * the in-Flash Burst branch.</p>
     *
     * @param simulator owning simulator at the confirmed reaction time
     */
    public void recordRepresentedVoidRift(CombatSimulator simulator) {
        initializeForSimulator(simulator);
        double currentTime = simulator.getCurrentTime();
        expireVoidRifts(currentTime);
        if (voidRiftExpirations.size() == 3) {
            voidRiftExpirations.remove(0);
        }
        voidRiftExpirations.add(currentTime
                + getTalentValue("A1 Rift Duration Frames", 1054.0)
                        * FRAME);
    }

    /** Reports that Void Rift and crystal geometry are excluded. */
    public boolean isVoidRiftGeometryRepresented() {
        return false;
    }

    /** Reports that automatic reaction-to-rift team plumbing is excluded. */
    public boolean isAutomaticRiftTeamPlumbingRepresented() {
        return false;
    }

    /** Reports that multi-target collection and random targeting are excluded. */
    public boolean isMultiTargetRandomCollectionRepresented() {
        return false;
    }

    /** Reports that player HP, healing, and defensive state are excluded. */
    public boolean isPlayerHpHealingDefenseRepresented() {
        return false;
    }

    /** Reports that movement, hitlag, and stamina are excluded. */
    public boolean isMovementHitlagStaminaRepresented() {
        return false;
    }

    /** Reports that low Plunge and exploration state are excluded. */
    public boolean isLowPlungeExplorationRepresented() {
        return false;
    }

    /** Routes Skirk's bounded typed action set. */
    @Override
    public void onAction(
            CharacterActionRequest request,
            CombatSimulator simulator) {
        validateActionRequest(request);
        initializeForSimulator(simulator);
        talentPassiveActive = hasExclusiveCryoHydroParty(simulator);
        if (request.getKey() == CharacterActionKey.SKILL
                && request.getSkillMode() != SkillActionMode.PRESS) {
            throw new IllegalArgumentException(
                    "Skirk Hold Skill movement is unsupported");
        }
        boolean flash = isFlashActiveAt(simulator.getCurrentTime());
        switch (request.getKey()) {
            case NORMAL:
                normalAttack(simulator, flash);
                break;
            case CHARGE:
                chargedAttack(simulator,
                        flash || (normalAttackStep > 0
                                && previousNormalUsedFlash));
                normalAttackStep = 0;
                previousNormalUsedFlash = false;
                break;
            case PLUNGE:
                highPlunge(simulator, flash);
                normalAttackStep = 0;
                previousNormalUsedFlash = false;
                break;
            case SKILL:
                if (flash) {
                    throw new IllegalStateException(
                            "Seven-Phase Flash is already active");
                }
                sevenPhaseFlash(simulator);
                normalAttackStep = 0;
                previousNormalUsedFlash = false;
                break;
            case BURST:
                if (flash) {
                    havocExtinction(simulator);
                } else {
                    havocRuin(simulator);
                }
                normalAttackStep = 0;
                previousNormalUsedFlash = false;
                break;
            default:
                throw new IllegalArgumentException(
                        "Unsupported action for Skirk: " + request.getKey());
        }
    }

    private void normalAttack(
            CombatSimulator simulator,
            boolean flash) {
        double castTime = simulator.getCurrentTime();
        int step = normalAttackStep;
        int[][] frames = flash
                ? FLASH_NORMAL_HIT_FRAMES : NORMAL_HIT_FRAMES;
        double a4Multiplier = flash
                ? attackA4Multiplier(castTime) : 1.0;
        for (int hit = 0; hit < frames[step].length; hit++) {
            queueHit(simulator, new PendingHit(
                    castTime + frames[step][hit] * FRAME,
                    flash ? HitKind.FLASH_NORMAL : HitKind.NORMAL,
                    step,
                    hit,
                    skillGeneration,
                    a4Multiplier,
                    null));
        }
        previousNormalUsedFlash = flash;
        normalAttackStep = (normalAttackStep + 1)
                % NORMAL_HIT_FRAMES.length;
        int duration = flash
                ? FLASH_NORMAL_DURATIONS[step]
                : NORMAL_DURATIONS[step];
        simulator.advanceTime(duration * FRAME);
    }

    private void chargedAttack(
            CombatSimulator simulator,
            boolean flash) {
        double castTime = simulator.getCurrentTime();
        int[] frames = flash
                ? new int[] { 28, 37, 46 }
                : new int[] { 27, 34 };
        double a4Multiplier = flash
                ? attackA4Multiplier(castTime) : 1.0;
        for (int hit = 0; hit < frames.length; hit++) {
            queueHit(simulator, new PendingHit(
                    castTime + frames[hit] * FRAME,
                    flash ? HitKind.FLASH_CHARGED : HitKind.CHARGED,
                    0,
                    hit,
                    skillGeneration,
                    a4Multiplier,
                    null));
        }
        simulator.advanceTime((flash ? 54.0 : 53.0) * FRAME);
    }

    private void highPlunge(
            CombatSimulator simulator,
            boolean flash) {
        double castTime = simulator.getCurrentTime();
        queueHit(simulator, new PendingHit(
                castTime + 48.0 * FRAME,
                flash ? HitKind.FLASH_HIGH_PLUNGE
                        : HitKind.HIGH_PLUNGE,
                0,
                0,
                skillGeneration,
                flash ? attackA4Multiplier(castTime) : 1.0,
                null));
        simulator.advanceTime(74.0 * FRAME);
    }

    private void sevenPhaseFlash(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        long generation = ++skillGeneration;
        queueCommand(simulator, new PendingCommand(
                castTime + getTalentValue(
                        "Skill Entry Frames", 19.0) * FRAME,
                CommandKind.SKILL_ENTRY,
                generation));
        simulator.advanceTime(43.0 * FRAME);
    }

    private void enterFlash(
            CombatSimulator simulator,
            long generation) {
        if (generation != skillGeneration) {
            return;
        }
        double currentTime = simulator.getCurrentTime();
        flashActive = true;
        flashActiveUntil = currentTime
                + getTalentValue("Skill Duration Frames", 754.0) * FRAME;
        addSerpentsSubtlety(getTalentValue(
                "Skill Serpents Subtlety", 45.0));
        if (constellation >= 2) {
            addSerpentsSubtlety(getTalentValue(
                    "C2 Skill Serpents Subtlety", 10.0));
        }
        queueCommand(simulator, new PendingCommand(
                currentTime + getTalentValue(
                        "Drain Tick Frames", 12.0) * FRAME,
                CommandKind.SKILL_DRAIN,
                generation));
        queueCommand(simulator, new PendingCommand(
                flashActiveUntil,
                CommandKind.SKILL_EXIT,
                generation));
    }

    private void drainFlash(
            CombatSimulator simulator,
            long generation) {
        double currentTime = simulator.getCurrentTime();
        if (generation != skillGeneration
                || !isFlashActiveAt(currentTime)) {
            return;
        }
        if (currentTime + EPSILON >= drainPausedUntil) {
            serpentsSubtlety = Math.max(
                    0.0,
                    serpentsSubtlety
                            - getTalentValue("Drain Per Tick", 1.4));
            if (serpentsSubtlety <= EPSILON) {
                exitFlash(simulator, generation);
                return;
            }
        }
        double nextTime = currentTime
                + getTalentValue("Drain Tick Frames", 12.0) * FRAME;
        if (nextTime < flashActiveUntil - EPSILON) {
            queueCommand(simulator, new PendingCommand(
                    nextTime,
                    CommandKind.SKILL_DRAIN,
                    generation));
        }
    }

    private void exitFlash(
            CombatSimulator simulator,
            long generation) {
        if (generation != skillGeneration || !flashActive) {
            return;
        }
        double currentTime = simulator.getCurrentTime();
        flashActive = false;
        flashActiveUntil = Double.NEGATIVE_INFINITY;
        serpentsSubtlety = 0.0;
        extinctionActiveUntil = Double.NEGATIVE_INFINITY;
        extinctionDamageBonus = 0.0;
        extinctionHitsRemaining = 0;
        c2AttackBuffUntil = Double.NEGATIVE_INFINITY;
        markSkillUsed(currentTime, simulator.getApplicableBuffs(this));
    }

    private void havocRuin(CombatSimulator simulator) {
        if (serpentsSubtlety + EPSILON < 50.0) {
            throw new IllegalStateException(
                    "Havoc: Ruin requires 50 Serpent's Subtlety");
        }
        double castTime = simulator.getCurrentTime();
        double maximumBonusPoints = constellation >= 2 ? 22.0 : 12.0;
        double bonusPoints = Math.max(
                0.0,
                Math.min(maximumBonusPoints, serpentsSubtlety - 50.0));
        double a4Multiplier = burstA4Multiplier(castTime);
        double slash = burstValue(
                "Ruin Slash", 2.086920, 2.455200);
        double finalSlash = burstValue(
                "Ruin Final", 3.478200, 4.092000);
        double pointBonus = burstValue(
                "Ruin Point Bonus", 0.328497, 0.386467);
        for (int index = 0; index < RUIN_HIT_FRAMES.length; index++) {
            queueHit(simulator, new PendingHit(
                    castTime + RUIN_HIT_FRAMES[index] * FRAME,
                    HitKind.RUIN_SLASH,
                    index,
                    0,
                    0L,
                    a4Multiplier,
                    captureScalar(slash + bonusPoints * pointBonus)));
        }
        queueHit(simulator, new PendingHit(
                castTime + 158.0 * FRAME,
                HitKind.RUIN_FINAL,
                0,
                0,
                0L,
                a4Multiplier,
                captureScalar(finalSlash + bonusPoints * pointBonus)));
        if (constellation >= 6) {
            queueC6BurstHits(simulator, castTime, a4Multiplier);
        }
        queueCommand(simulator, new PendingCommand(
                castTime + 7.0 * FRAME,
                CommandKind.CLEAR_RESOURCE,
                0L));
        markBurstCooldownUsed(
                castTime, simulator.getApplicableBuffs(this));
        simulator.advanceTime(151.0 * FRAME);
    }

    private void havocExtinction(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        int absorbed = absorbRepresentedVoidRifts(simulator);
        extinctionActiveUntil = castTime + 12.5;
        extinctionHitsRemaining = 10;
        extinctionDamageBonus = extinctionValue(absorbed);
        nextExtinctionAllowedTime = Double.NEGATIVE_INFINITY;
        if (constellation >= 2) {
            queueCommand(simulator, new PendingCommand(
                    castTime + 39.0 * FRAME,
                    CommandKind.C2_ATTACK_BUFF,
                    skillGeneration));
        }
        markBurstCooldownUsed(
                castTime, simulator.getApplicableBuffs(this));
        simulator.advanceTime(41.0 * FRAME);
    }

    private void queueC6BurstHits(
            CombatSimulator simulator,
            double castTime,
            double a4Multiplier) {
        expireC6Stacks(castTime);
        int count = c6StackExpirations.size();
        c6StackExpirations.clear();
        for (int index = 0; index < count; index++) {
            queueHit(simulator, new PendingHit(
                    castTime + 12.0 * FRAME,
                    HitKind.C6_BURST,
                    index,
                    0,
                    0L,
                    a4Multiplier,
                    null));
        }
    }

    private void resolveHit(
            CombatSimulator simulator,
            PendingHit hit) {
        switch (hit.kind) {
            case NORMAL:
                performHit(
                        simulator, hit,
                        "Havoc: Sunder N" + (hit.step + 1)
                                + hitSuffix(hit),
                        getTalentValue(
                                hit.step == 2 ? "N3 Hit"
                                        : "N" + (hit.step + 1),
                                NORMAL_T9[hit.step]),
                        Element.PHYSICAL,
                        StatType.NORMAL_ATTACK_DMG_BONUS,
                        ActionType.NORMAL,
                        ICDType.Standard,
                        ICDTag.NormalAttack,
                        0.0,
                        true);
                break;
            case CHARGED:
                performHit(
                        simulator, hit,
                        "Havoc: Sunder Charged Hit " + (hit.hit + 1),
                        getTalentValue("Charged Attack Hit", 1.227660),
                        Element.PHYSICAL,
                        StatType.CHARGED_ATTACK_DMG_BONUS,
                        ActionType.CHARGE,
                        ICDType.Standard,
                        ICDTag.ChargedAttack,
                        0.0,
                        true);
                break;
            case HIGH_PLUNGE:
                performHit(
                        simulator, hit,
                        "Havoc: Sunder High Plunge",
                        getTalentValue("High Plunge", 2.933586),
                        Element.PHYSICAL,
                        StatType.PLUNGING_ATTACK_DMG_BONUS,
                        ActionType.PLUNGE,
                        ICDType.None,
                        ICDTag.PlungeAttack,
                        0.0,
                        true);
                break;
            case FLASH_NORMAL:
                performHit(
                        simulator, hit,
                        "Seven-Phase Flash N" + (hit.step + 1)
                                + hitSuffix(hit),
                        flashNormalValue(hit.step) * hit.multiplier,
                        Element.CRYO,
                        StatType.NORMAL_ATTACK_DMG_BONUS,
                        ActionType.NORMAL,
                        ICDType.Standard,
                        ICDTag.NormalAttack,
                        1.0,
                        false);
                break;
            case FLASH_CHARGED:
                performHit(
                        simulator, hit,
                        "Seven-Phase Flash Charged Hit " + (hit.hit + 1),
                        flashSkillValue(
                                "Flash Charged Hit",
                                0.818440,
                                0.880600,
                                1.004920,
                                1.067080) * hit.multiplier,
                        Element.CRYO,
                        StatType.CHARGED_ATTACK_DMG_BONUS,
                        ActionType.CHARGE,
                        ICDType.Standard,
                        ICDTag.ChargedAttack,
                        1.0,
                        false);
                break;
            case FLASH_HIGH_PLUNGE:
                performHit(
                        simulator, hit,
                        "Seven-Phase Flash High Plunge",
                        flashSkillValue(
                                "Flash High Plunge",
                                2.933586,
                                3.156390,
                                3.601998,
                                3.824802) * hit.multiplier,
                        Element.CRYO,
                        StatType.PLUNGING_ATTACK_DMG_BONUS,
                        ActionType.PLUNGE,
                        ICDType.None,
                        ICDTag.PlungeAttack,
                        1.0,
                        false);
                break;
            case RUIN_SLASH:
                performHit(
                        simulator, hit,
                        "Havoc: Ruin Slash " + (hit.step + 1),
                        hit.scalar * hit.multiplier,
                        Element.CRYO,
                        StatType.BURST_DMG_BONUS,
                        ActionType.BURST,
                        ICDType.Standard,
                        ICDTag.ElementalBurst,
                        1.0,
                        false);
                break;
            case RUIN_FINAL:
                performHit(
                        simulator, hit,
                        "Havoc: Ruin Final Slash",
                        hit.scalar * hit.multiplier,
                        Element.CRYO,
                        StatType.BURST_DMG_BONUS,
                        ActionType.BURST,
                        ICDType.Standard,
                        ICDTag.ElementalBurst,
                        1.0,
                        false);
                break;
            case C1_FAR_TO_FALL:
                performHit(
                        simulator, hit,
                        "Far to Fall C1",
                        getTalentValue("C1 Far to Fall", 5.0),
                        Element.CRYO,
                        null,
                        ActionType.OTHER,
                        ICDType.Standard,
                        ICDTag.Skirk_Constellation,
                        1.0,
                        false);
                break;
            case C6_NORMAL:
                performHit(
                        simulator, hit,
                        "Havoc: Sever Normal " + (hit.hit + 1),
                        getTalentValue(
                                "C6 Normal Multiplier", 1.8)
                                * hit.multiplier,
                        Element.CRYO,
                        StatType.NORMAL_ATTACK_DMG_BONUS,
                        ActionType.NORMAL,
                        ICDType.Standard,
                        ICDTag.NormalAttack,
                        1.0,
                        false);
                break;
            case C6_BURST:
                performHit(
                        simulator, hit,
                        "Havoc: Sever Burst " + (hit.step + 1),
                        getTalentValue(
                                "C6 Burst Multiplier", 7.5)
                                * hit.multiplier,
                        Element.CRYO,
                        StatType.BURST_DMG_BONUS,
                        ActionType.BURST,
                        ICDType.Standard,
                        ICDTag.ElementalBurst,
                        1.0,
                        false);
                break;
            default:
                throw new IllegalStateException(
                        "Unknown Skirk hit kind " + hit.kind);
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
            boolean shatter) {
        double currentTime = simulator.getCurrentTime();
        double extinctionDamageBonus = 0.0;
        if (actionType == ActionType.NORMAL
                && isExtinctionActiveAt(currentTime)
                && extinctionHitsRemaining > 0
                && currentTime + EPSILON
                        >= nextExtinctionAllowedTime) {
            extinctionDamageBonus = this.extinctionDamageBonus;
            extinctionHitsRemaining--;
            nextExtinctionAllowedTime = currentTime + 0.1;
        }
        AttackAction action = new AttackAction(
                displayName,
                multiplier,
                hitElement,
                StatType.BASE_ATK,
                bonusStat,
                0.0,
                true,
                actionType);
        if (extinctionDamageBonus != 0.0) {
            action.addBonusStat(
                    StatType.DMG_BONUS_ALL,
                    extinctionDamageBonus);
        }
        action.setICD(icdType, icdTag, gauge);
        action.setShatterTrigger(shatter);
        if (actionType == ActionType.BURST) {
            action.setCountsAsBurstDmg(true);
        }
        StatsContainer snapshot = hit.snapshot == null
                ? captureLiveStats(currentTime)
                : hit.snapshot;
        action.setStatSnapshot(snapshot);
        resolvingAction = action;
        resolvingHitStep = hit.step;
        resolvingC6Eligible = hit.kind == HitKind.FLASH_NORMAL
                && (hit.step == 2 || hit.step == 4);
        resolvingRiftAbsorption = hit.kind == HitKind.FLASH_CHARGED;
        try {
            simulator.performActionWithoutTimeAdvance(characterId, action);
        } finally {
            resolvingAction = null;
            resolvingHitStep = 0;
            resolvingC6Eligible = false;
            resolvingRiftAbsorption = false;
        }
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
        if (actor != this) {
            recordA4Contributor(actor, action, time);
            return;
        }
        if (action.getElement() == Element.CRYO
                && time + EPSILON >= nextParticleAllowedTime) {
            nextParticleAllowedTime = time
                    + getTalentValue("Particle Cooldown", 15.0);
            queueCommand(simulator, new PendingCommand(
                    time + getTalentValue(
                            "Particle Travel Frames", 100.0) * FRAME,
                    CommandKind.PARTICLE,
                    0L));
        }
        if (action != resolvingAction) {
            return;
        }
        if (resolvingRiftAbsorption) {
            resolvingRiftAbsorption = false;
            absorbRepresentedVoidRifts(simulator);
        }
        if (resolvingC6Eligible && constellation >= 6
                && time + EPSILON >= nextC6NormalAllowedTime) {
            resolvingC6Eligible = false;
            nextC6NormalAllowedTime = time + 14.0 * FRAME;
            triggerC6Normal(simulator, time);
        }
    }

    private void recordA4Contributor(
            Character actor,
            AttackAction action,
            double time) {
        if (actor == null || action == null
                || action.getElement() != actor.getElement()) {
            return;
        }
        if (actor.getElement() != Element.CRYO
                && actor.getElement() != Element.HYDRO) {
            return;
        }
        a4Expirations.put(
                actor.getCharacterId(),
                time + getTalentValue("A4 Duration", 20.0));
    }

    private void triggerC6Normal(
            CombatSimulator simulator,
            double time) {
        expireC6Stacks(time);
        if (c6StackExpirations.isEmpty()) {
            return;
        }
        c6StackExpirations.remove(0);
        double a4Multiplier = attackA4Multiplier(time);
        for (int index = 1; index <= 3; index++) {
            queueHit(simulator, new PendingHit(
                    time + index * 3.0 * FRAME,
                    HitKind.C6_NORMAL,
                    resolvingHitStep,
                    index - 1,
                    skillGeneration,
                    a4Multiplier,
                    null));
        }
    }

    /**
     * Absorbs all unexpired positionless rifts as one fixed-target collection.
     *
     * <p>This explicit ingress represents only the local resource, A1 pause,
     * C1, and C6 consequences. It performs no range, path, or target search.</p>
     *
     * @param simulator owning simulator at collection time
     * @return number of rifts absorbed in {@code [0, 3]}
     */
    public int absorbRepresentedVoidRifts(
            CombatSimulator simulator) {
        initializeForSimulator(simulator);
        double currentTime = simulator.getCurrentTime();
        expireVoidRifts(currentTime);
        int count = voidRiftExpirations.size();
        voidRiftExpirations.clear();
        if (count == 0) {
            return 0;
        }
        addSerpentsSubtlety(count * getTalentValue(
                "A1 Serpents Subtlety Per Rift", 8.0));
        drainPausedUntil = Math.max(
                currentTime,
                drainPausedUntil)
                + count * getTalentValue(
                        "A1 Drain Pause Per Rift", 0.3);
        for (int index = 0; index < count; index++) {
            if (constellation >= 1) {
                queueHit(simulator, new PendingHit(
                        currentTime + 3.0 * FRAME,
                        HitKind.C1_FAR_TO_FALL,
                        index,
                        0,
                        0L,
                        1.0,
                        null));
            }
            if (constellation >= 6) {
                expireC6Stacks(currentTime);
                if (c6StackExpirations.size() == 3) {
                    c6StackExpirations.remove(0);
                }
                c6StackExpirations.add(currentTime
                        + getTalentValue(
                                "C6 Stack Duration", 15.0));
            }
        }
        return count;
    }

    private void addSerpentsSubtlety(double amount) {
        serpentsSubtlety = Math.min(
                getTalentValue("Maximum Serpents Subtlety", 100.0),
                Math.max(0.0, serpentsSubtlety + amount));
    }

    private double flashNormalValue(int step) {
        String key = step == 2 || step == 3
                ? "Flash N" + (step + 1) + " Hit"
                : "Flash N" + (step + 1);
        double[] t9 = {
            2.440263, 2.200972, 1.391190, 1.479670, 3.612401
        };
        double[] t10 = {
            2.625599, 2.368134, 1.496850, 1.592050, 3.886761
        };
        double[] t12 = {
            2.996272, 2.702459, 1.708170, 1.816810, 4.435480
        };
        double[] t13 = {
            3.181608, 2.869621, 1.813830, 1.929190, 4.709840
        };
        return flashSkillValue(
                key, t9[step], t10[step], t12[step], t13[step]);
    }

    private double flashSkillValue(
            String baseKey,
            double t9,
            double t10,
            double t12,
            double t13) {
        if (constellation >= 3 && talentPassiveActive) {
            return getTalentValue(baseKey + " C3 Passive", t13);
        }
        if (constellation >= 3) {
            return getTalentValue(baseKey + " C3", t12);
        }
        if (talentPassiveActive) {
            return getTalentValue(baseKey + " Passive", t10);
        }
        return getTalentValue(baseKey, t9);
    }

    private double burstValue(
            String baseKey,
            double t9,
            double c5) {
        return getTalentValue(
                constellation >= 5 ? baseKey + " C5" : baseKey,
                constellation >= 5 ? c5 : t9);
    }

    private double extinctionValue(int absorbed) {
        int count = Math.max(0, Math.min(3, absorbed));
        double[] t9 = { 0.075, 0.114, 0.152, 0.190 };
        double[] c5 = { 0.090, 0.132, 0.176, 0.220 };
        return getTalentValue(
                "Extinction Bonus " + count
                        + (constellation >= 5 ? " C5" : ""),
                constellation >= 5 ? c5[count] : t9[count]);
    }

    private double attackA4Multiplier(double currentTime) {
        return A4_ATTACK_MULTIPLIERS[
                getDeathsCrossingStacks(currentTime)];
    }

    private double burstA4Multiplier(double currentTime) {
        return A4_BURST_MULTIPLIERS[
                getDeathsCrossingStacks(currentTime)];
    }

    private boolean hasExclusiveCryoHydroParty(
            CombatSimulator simulator) {
        boolean hasCryo = false;
        boolean hasHydro = false;
        for (Character member : simulator.getPartyMembers()) {
            if (member.getElement() == Element.CRYO) {
                hasCryo = true;
            } else if (member.getElement() == Element.HYDRO) {
                hasHydro = true;
            } else {
                return false;
            }
        }
        return hasCryo && hasHydro;
    }

    private boolean isFlashActiveAt(double currentTime) {
        return flashActive
                && currentTime < flashActiveUntil - EPSILON;
    }

    private boolean isExtinctionActiveAt(double currentTime) {
        return isFlashActiveAt(currentTime)
                && currentTime < extinctionActiveUntil - EPSILON;
    }

    private void expireA4(double currentTime) {
        Iterator<Map.Entry<CharacterId, Double>> iterator =
                a4Expirations.entrySet().iterator();
        while (iterator.hasNext()) {
            if (currentTime + EPSILON >= iterator.next().getValue()) {
                iterator.remove();
            }
        }
    }

    private void expireVoidRifts(double currentTime) {
        voidRiftExpirations.removeIf(expiration ->
                currentTime > expiration + EPSILON);
    }

    private void expireC6Stacks(double currentTime) {
        c6StackExpirations.removeIf(expiration ->
                currentTime > expiration + EPSILON);
    }

    private StatsContainer captureLiveStats(double currentTime) {
        StatsContainer stats = getEffectiveStats(currentTime);
        if (initializedSimulator != null) {
            for (Buff buff : initializedSimulator.getApplicableBuffs(this)) {
                if (!buff.isExpired(currentTime)) {
                    buff.apply(stats, currentTime);
                }
            }
        }
        double attackPercent = 0.0;
        if (constellation >= 2
                && currentTime < c2AttackBuffUntil - EPSILON
                && isFlashActiveAt(currentTime)) {
            attackPercent += getTalentValue(
                    "C2 Extinction ATK", 0.7);
        }
        if (constellation >= 4) {
            int stacks = getDeathsCrossingStacks(currentTime);
            if (stacks == 1) {
                attackPercent += getTalentValue(
                        "C4 ATK Stack 1", 0.1);
            } else if (stacks == 2) {
                attackPercent += getTalentValue(
                        "C4 ATK Stack 2", 0.2);
            } else if (stacks >= 3) {
                attackPercent += getTalentValue(
                        "C4 ATK Stack 3", 0.4);
            }
        }
        if (attackPercent != 0.0) {
            stats.add(StatType.ATK_PERCENT, attackPercent);
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
                case SKILL_ENTRY:
                    enterFlash(activeSimulator, command.generation);
                    break;
                case SKILL_DRAIN:
                    drainFlash(activeSimulator, command.generation);
                    break;
                case SKILL_EXIT:
                    if (command.generation == skillGeneration
                            && activeSimulator.getCurrentTime()
                                    + EPSILON >= flashActiveUntil) {
                        exitFlash(activeSimulator, command.generation);
                    }
                    break;
                case PARTICLE:
                    activeSimulator.getEnergyDistributor()
                            .distributeParticles(
                                    Element.CRYO,
                                    getTalentValue("Particle Count", 4.0),
                                    ParticleType.PARTICLE);
                    break;
                case CLEAR_RESOURCE:
                    serpentsSubtlety = 0.0;
                    break;
                case C2_ATTACK_BUFF:
                    if (command.generation == skillGeneration
                            && isFlashActiveAt(
                                    activeSimulator.getCurrentTime())) {
                        c2AttackBuffUntil = Math.min(
                                flashActiveUntil,
                                activeSimulator.getCurrentTime() + 12.5);
                    }
                    break;
                default:
                    throw new IllegalStateException(
                            "Unknown Skirk command " + command.kind);
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

    private static String hitSuffix(PendingHit hit) {
        return hit.hit == 0 && hit.step != 2 && hit.step != 3
                ? "" : " Hit " + (hit.hit + 1);
    }

    private static Double captureScalar(double value) {
        return value;
    }

    private static List<PendingHit> copyHits(
            List<PendingHit> source) {
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
        FLASH_NORMAL,
        FLASH_CHARGED,
        FLASH_HIGH_PLUNGE,
        RUIN_SLASH,
        RUIN_FINAL,
        C1_FAR_TO_FALL,
        C6_NORMAL,
        C6_BURST
    }

    private enum CommandKind {
        SKILL_ENTRY,
        SKILL_DRAIN,
        SKILL_EXIT,
        PARTICLE,
        CLEAR_RESOURCE,
        C2_ATTACK_BUFF
    }

    /** Immutable delayed-hit payload. */
    private static final class PendingHit {
        private final double time;
        private final HitKind kind;
        private final int step;
        private final int hit;
        private final long generation;
        private final double multiplier;
        private final Double scalar;
        private final StatsContainer snapshot;

        private PendingHit(
                double time,
                HitKind kind,
                int step,
                int hit,
                long generation,
                double multiplier,
                Double scalar) {
            this(time, kind, step, hit, generation, multiplier, scalar, null);
        }

        private PendingHit(
                double time,
                HitKind kind,
                int step,
                int hit,
                long generation,
                double multiplier,
                Double scalar,
                StatsContainer snapshot) {
            this.time = time;
            this.kind = kind;
            this.step = step;
            this.hit = hit;
            this.generation = generation;
            this.multiplier = multiplier;
            this.scalar = scalar;
            this.snapshot = snapshot == null
                    ? null : snapshot.merge(null);
        }

        private PendingHit copy() {
            return new PendingHit(
                    time,
                    kind,
                    step,
                    hit,
                    generation,
                    multiplier,
                    scalar,
                    snapshot);
        }
    }

    /** Immutable delayed state-command payload. */
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

    /** Immutable owner-bound snapshot of Skirk-specific runtime state. */
    private static final class SkirkState implements State {
        private final Skirk owner;
        private final int normalAttackStep;
        private final boolean previousNormalUsedFlash;
        private final long skillGeneration;
        private final boolean flashActive;
        private final double flashActiveUntil;
        private final double serpentsSubtlety;
        private final double drainPausedUntil;
        private final double nextParticleAllowedTime;
        private final double c2AttackBuffUntil;
        private final double extinctionActiveUntil;
        private final double extinctionDamageBonus;
        private final int extinctionHitsRemaining;
        private final double nextExtinctionAllowedTime;
        private final boolean talentPassiveActive;
        private final Map<CharacterId, Double> a4Expirations;
        private final List<Double> voidRiftExpirations;
        private final List<Double> c6StackExpirations;
        private final double nextC6NormalAllowedTime;
        private final List<PendingHit> pendingHits;
        private final List<PendingCommand> pendingCommands;

        private SkirkState(
                Skirk owner,
                int normalAttackStep,
                boolean previousNormalUsedFlash,
                long skillGeneration,
                boolean flashActive,
                double flashActiveUntil,
                double serpentsSubtlety,
                double drainPausedUntil,
                double nextParticleAllowedTime,
                double c2AttackBuffUntil,
                double extinctionActiveUntil,
                double extinctionDamageBonus,
                int extinctionHitsRemaining,
                double nextExtinctionAllowedTime,
                boolean talentPassiveActive,
                Map<CharacterId, Double> a4Expirations,
                List<Double> voidRiftExpirations,
                List<Double> c6StackExpirations,
                double nextC6NormalAllowedTime,
                List<PendingHit> pendingHits,
                List<PendingCommand> pendingCommands) {
            this.owner = owner;
            this.normalAttackStep = normalAttackStep;
            this.previousNormalUsedFlash = previousNormalUsedFlash;
            this.skillGeneration = skillGeneration;
            this.flashActive = flashActive;
            this.flashActiveUntil = flashActiveUntil;
            this.serpentsSubtlety = serpentsSubtlety;
            this.drainPausedUntil = drainPausedUntil;
            this.nextParticleAllowedTime = nextParticleAllowedTime;
            this.c2AttackBuffUntil = c2AttackBuffUntil;
            this.extinctionActiveUntil = extinctionActiveUntil;
            this.extinctionDamageBonus = extinctionDamageBonus;
            this.extinctionHitsRemaining = extinctionHitsRemaining;
            this.nextExtinctionAllowedTime = nextExtinctionAllowedTime;
            this.talentPassiveActive = talentPassiveActive;
            this.a4Expirations = new EnumMap<>(a4Expirations);
            this.voidRiftExpirations =
                    new ArrayList<>(voidRiftExpirations);
            this.c6StackExpirations =
                    new ArrayList<>(c6StackExpirations);
            this.nextC6NormalAllowedTime = nextC6NormalAllowedTime;
            this.pendingHits = copyHits(pendingHits);
            this.pendingCommands = copyCommands(pendingCommands);
        }
    }
}

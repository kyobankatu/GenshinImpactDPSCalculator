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
 * Kinich's deterministic fixed-target Scalespiker offensive slice.
 *
 * <p>Claymore basics, local Nightsoul entry and point generation, two-hit Loop
 * Shots, Scalespiker Cannon, Skill particles, Ajaw's Burst attacks, and the
 * representable offensive A4/C1-C6 branches follow pinned gcsim
 * {@code ef41805d}. Loop Shots and Cannon use their independent source-backed
 * time-or-hit application groups. Ajaw's source-backed 145/150-frame interval
 * choices alternate deterministically in this single-target model.</p>
 *
 * <p>Grappling, movement, Blind Spot geometry, random or multi-target
 * selection, Nightsoul Burst team plumbing, A1 Burning/Burgeon generation,
 * stamina, low Plunge, and exploration state fail closed. A4 is exposed
 * through an explicit local trigger because the simulator has no team-wide
 * Nightsoul Burst event.</p>
 */
public final class Kinich extends Character implements
        SimulatorInitializedCharacterEffect,
        SnapshotAwareCharacterEffect,
        SwitchAwareCharacter {
    private static final double FRAME = 1.0 / 60.0;
    private static final double EPSILON = 1e-9;
    private static final int[] NORMAL_HIT_FRAMES = { 21, 22, 44 };
    private static final int[] NORMAL_DURATIONS = { 47, 48, 79 };
    private static final int[] CHARGED_HIT_FRAMES = { 71, 95, 119 };
    private static final int[][] LOOP_HIT_FRAMES = {
        { 30, 38 },
        { 31, 38 }
    };
    private static final int[] AJAW_HIT_FRAMES = {
        253, 398, 548, 693, 843, 988
    };
    private static final double[] NORMAL_T9 = {
        1.818580, 1.523120, 2.268880
    };
    /**
     * Per-hit metadata from gcsim pinned at
     * {@code 3647a07a7cc3004bc1e79d9bb5f7444de20dceaa}.
     */
    private static final HitlagProfile[] NORMAL_HITLAG = {
        new HitlagProfile(0.06, 0.01, true, false, false),
        new HitlagProfile(0.09, 0.01, true, false, false),
        new HitlagProfile(0.12, 0.01, true, false, false)
    };
    private static final HitlagProfile CHARGED_HITLAG =
            new HitlagProfile(0.01, 0.01, false, false, false);
    private static final HitlagProfile CANNON_HITLAG =
            new HitlagProfile(0.0, 0.01, true, true, false);
    private static final HitlagProfile C6_REBOUND_HITLAG =
            new HitlagProfile(0.0, 0.0, true, true, false);
    private static final HitlagProfile BURST_INITIAL_HITLAG =
            new HitlagProfile(0.0, 0.01, true, false, false);
    private static final HitlagProfile AJAW_BREATH_HITLAG =
            new HitlagProfile(0.0, 0.05, false, true, false);

    private CombatSimulator initializedSimulator;
    private int normalAttackStep;
    private int loopShotStep;
    private long nightsoulGeneration;
    private boolean nightsoulActive;
    private double nightsoulPoints;
    private double nightsoulExpirationTime = Double.NEGATIVE_INFINITY;
    private boolean particlesGenerated;
    private boolean c2FirstHitPending;
    private boolean c2CannonBonusAvailable;
    private int a4Stacks;
    private double a4ExpirationTime = Double.NEGATIVE_INFINITY;
    private double nextC4EnergyAllowedTime = Double.NEGATIVE_INFINITY;
    private long ajawGeneration;
    private AttackAction resolvingAction;
    private boolean resolvingParticleEligible;
    private boolean resolvingC2Eligible;
    private List<PendingHit> pendingHits = new ArrayList<>();
    private List<PendingCommand> pendingCommands = new ArrayList<>();

    /** Constructs repository-default C6 Kinich. */
    public Kinich(Weapon weapon, ArtifactSet artifacts) {
        this(weapon, artifacts, TalentDataManager.getInstance(), 6);
    }

    /** Constructs Kinich at an explicit constellation. */
    public Kinich(
            Weapon weapon,
            ArtifactSet artifacts,
            int constellation) {
        this(weapon, artifacts, TalentDataManager.getInstance(), constellation);
    }

    /**
     * Constructs Kinich with injectable static talent data.
     *
     * @param weapon equipped claymore, or {@code null}
     * @param artifacts equipped artifact set, or {@code null}
     * @param talentData static character-data source
     * @param constellation constellation level in {@code [0, 6]}
     */
    public Kinich(
            Weapon weapon,
            ArtifactSet artifacts,
            TalentDataSource talentData,
            int constellation) {
        super(talentData);
        if (constellation < 0 || constellation > 6) {
            throw new IllegalArgumentException(
                    "Kinich constellation must be between 0 and 6");
        }
        name = "Kinich";
        characterId = CharacterId.KINICH;
        element = Element.DENDRO;
        this.weapon = weapon;
        this.artifacts = new ArtifactSet[] { artifacts };
        this.constellation = constellation;
        baseStats.set(StatType.BASE_HP,
                getTalentValue("Base HP", 10875.0));
        baseStats.set(StatType.BASE_ATK,
                getTalentValue("Base ATK", 332.0));
        baseStats.set(StatType.BASE_DEF,
                getTalentValue("Base DEF", 692.0));
        baseStats.add(StatType.CRIT_DMG,
                getTalentValue("Ascension CRIT DMG", 0.384));
        setSkillCD(getTalentValue("Skill Cooldown", 18.0));
        setBurstCD(getTalentValue("Burst Cooldown", 18.0));
    }

    /** Binds accepted-hit callbacks to one owning simulator. */
    @Override
    public void initializeForSimulator(CombatSimulator simulator) {
        if (simulator == null) {
            throw new IllegalArgumentException("Kinich simulator is required");
        }
        if (!simulator.getPartyMembers().contains(this)) {
            throw new IllegalArgumentException(
                    "Kinich must belong to the simulator party");
        }
        if (initializedSimulator != null
                && initializedSimulator != simulator) {
            throw new IllegalStateException(
                    "Kinich cannot be reused across simulators");
        }
        if (initializedSimulator == simulator) {
            return;
        }
        initializedSimulator = simulator;
        simulator.addDamageListener((actor, action, damage, time) ->
                handleAcceptedDamage(
                        simulator, actor, action, damage, time));
    }

    /** Captures all local resources, gates, generations, and delayed work. */
    @Override
    public State captureCharacterState() {
        return new KinichState(
                this,
                normalAttackStep,
                loopShotStep,
                nightsoulGeneration,
                nightsoulActive,
                nightsoulPoints,
                nightsoulExpirationTime,
                particlesGenerated,
                c2FirstHitPending,
                c2CannonBonusAvailable,
                a4Stacks,
                a4ExpirationTime,
                nextC4EnergyAllowedTime,
                ajawGeneration,
                pendingHits,
                pendingCommands);
    }

    /** Accepts state captured from this exact Kinich instance only. */
    @Override
    public boolean acceptsCharacterState(State state) {
        return state instanceof KinichState
                && ((KinichState) state).owner == this;
    }

    /** Restores surviving Kinich-owned events exactly once. */
    @Override
    public void restoreCharacterState(
            State state,
            CombatSimulator simulator) {
        if (!acceptsCharacterState(state)) {
            throw new IllegalArgumentException("Unexpected Kinich state");
        }
        initializeForSimulator(simulator);
        KinichState restored = (KinichState) state;
        normalAttackStep = restored.normalAttackStep;
        loopShotStep = restored.loopShotStep;
        nightsoulGeneration = restored.nightsoulGeneration;
        nightsoulActive = restored.nightsoulActive;
        nightsoulPoints = restored.nightsoulPoints;
        nightsoulExpirationTime = restored.nightsoulExpirationTime;
        particlesGenerated = restored.particlesGenerated;
        c2FirstHitPending = restored.c2FirstHitPending;
        c2CannonBonusAvailable = restored.c2CannonBonusAvailable;
        a4Stacks = restored.a4Stacks;
        a4ExpirationTime = restored.a4ExpirationTime;
        nextC4EnergyAllowedTime = restored.nextC4EnergyAllowedTime;
        ajawGeneration = restored.ajawGeneration;
        pendingHits = copyHits(restored.pendingHits);
        pendingCommands = copyCommands(restored.pendingCommands);
        resolvingAction = null;
        resolvingParticleEligible = false;
        resolvingC2Eligible = false;
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

    /** Returns Kinich's 70-Energy Burst cost. */
    @Override
    public double getEnergyCost() {
        return getTalentValue("Energy Cost", 70.0);
    }

    /** Kinich has no unconditional represented passive stat. */
    @Override
    public void applyPassive(StatsContainer stats) {
    }

    /** Allows Cannon input during the active local Nightsoul state. */
    @Override
    public double getSkillCDRemaining(double currentTime) {
        if (isNightsoulActiveAt(currentTime)) {
            return 0.0;
        }
        return super.getSkillCDRemaining(currentTime);
    }

    /** Cancels local Nightsoul state and invalidates its pending generation. */
    @Override
    public void onSwitchOut(CombatSimulator simulator) {
        normalAttackStep = 0;
        cancelNightsoul();
    }

    /** Resets the fixed claymore string when Kinich returns on field. */
    @Override
    public void onSwitchIn(CombatSimulator simulator) {
        normalAttackStep = 0;
    }

    /** Returns whether the local Nightsoul state is currently active. */
    public boolean isNightsoulActive() {
        return initializedSimulator != null
                && isNightsoulActiveAt(
                        initializedSimulator.getCurrentTime());
    }

    /** Returns the current locally tracked Nightsoul points. */
    public double getNightsoulPoints() {
        return nightsoulPoints;
    }

    /** Returns A4 stacks that would be consumed by Cannon release. */
    public int getHuntersExperienceStacks(double currentTime) {
        if (currentTime + EPSILON >= a4ExpirationTime) {
            return 0;
        }
        return a4Stacks;
    }

    /** Returns the number of unresolved Kinich-owned damage events. */
    public int getPendingHitCount() {
        return pendingHits.size();
    }

    /**
     * Records one source-backed A4 trigger without adding global team plumbing.
     *
     * @param simulator owning simulator at the trigger time
     */
    public void recordRepresentedNightsoulBurst(
            CombatSimulator simulator) {
        initializeForSimulator(simulator);
        double currentTime = simulator.getCurrentTime();
        if (currentTime + EPSILON >= a4ExpirationTime) {
            a4Stacks = 0;
        }
        a4Stacks = Math.min(
                (int) getTalentValue("A4 Maximum Stacks", 2.0),
                a4Stacks + 1);
        a4ExpirationTime = currentTime
                + getTalentValue("A4 Stack Duration", 15.0);
    }

    /** Reports that A1's Burning/Burgeon point branch is unavailable. */
    public boolean isA1BurningBurgeonRepresented() {
        return false;
    }

    /** Reports that team-wide Nightsoul Burst dispatch is unavailable. */
    public boolean isNightsoulBurstTeamPlumbingRepresented() {
        return false;
    }

    /** Reports that grappling, movement, and Blind Spot geometry are excluded. */
    public boolean isGrapplingMovementBlindSpotRepresented() {
        return false;
    }

    /** Reports that random and multi-target selection are excluded. */
    public boolean isRandomMultiTargetSelectionRepresented() {
        return false;
    }

    /** Reports that complete hitlag coverage and stamina are excluded. */
    public boolean isHitlagStaminaRepresented() {
        return false;
    }

    /** Reports that low Plunge is excluded. */
    public boolean isLowPlungeRepresented() {
        return false;
    }

    /** Reports that exploration-only state is excluded. */
    public boolean isExplorationStateRepresented() {
        return false;
    }

    /** Reports that C1 movement speed is excluded with movement state. */
    public boolean isC1MovementSpeedRepresented() {
        return false;
    }

    /** Reports that C2's area increase is excluded with geometry. */
    public boolean isC2AreaIncreaseRepresented() {
        return false;
    }

    /** Dispatches Kinich's represented typed action set. */
    @Override
    public void onAction(
            CharacterActionRequest request,
            CombatSimulator simulator) {
        if (request == null) {
            throw new IllegalArgumentException("Kinich action is required");
        }
        initializeForSimulator(simulator);
        if (request.getKey() == CharacterActionKey.SKILL
                && request.getSkillMode() != SkillActionMode.PRESS) {
            throw new IllegalArgumentException(
                    "Kinich grappling and Hold Skill are unsupported");
        }
        if (request.getKey() != CharacterActionKey.NORMAL) {
            normalAttackStep = 0;
        }
        switch (request.getKey()) {
            case NORMAL:
                if (isNightsoulActiveAt(simulator.getCurrentTime())) {
                    loopShot(simulator);
                } else {
                    normalAttack(simulator);
                }
                break;
            case CHARGE:
                rejectNightsoulBasic(simulator, "Charged Attack");
                chargedAttack(simulator);
                break;
            case PLUNGE:
                rejectNightsoulBasic(simulator, "High Plunge");
                highPlunge(simulator);
                break;
            case SKILL:
                if (isNightsoulActiveAt(simulator.getCurrentTime())) {
                    scalespikerCannon(simulator);
                } else {
                    enterNightsoul(simulator);
                }
                break;
            case BURST:
                hailToTheAlmightyDragonlord(simulator);
                break;
            default:
                throw new IllegalArgumentException(
                        "Unsupported action for Kinich: "
                                + request.getKey());
        }
    }

    private void normalAttack(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        int step = normalAttackStep;
        StatsContainer snapshot = captureLiveStats(castTime);
        queueHit(simulator, new PendingHit(
                castTime + NORMAL_HIT_FRAMES[step] * FRAME,
                HitKind.NORMAL,
                step,
                0L,
                snapshot,
                0.0,
                false));
        normalAttackStep = (normalAttackStep + 1)
                % NORMAL_HIT_FRAMES.length;
        simulator.advanceTime(NORMAL_DURATIONS[step] * FRAME);
    }

    private void chargedAttack(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        StatsContainer snapshot = captureLiveStats(castTime);
        for (int index = 0; index < CHARGED_HIT_FRAMES.length;
                index++) {
            queueHit(simulator, new PendingHit(
                    castTime + CHARGED_HIT_FRAMES[index] * FRAME,
                    HitKind.CHARGED,
                    index,
                    0L,
                    snapshot,
                    0.0,
                    false));
        }
        simulator.advanceTime(87.0 * FRAME);
    }

    private void highPlunge(CombatSimulator simulator) {
        PendingHit hit = new PendingHit(
                simulator.getCurrentTime(),
                HitKind.HIGH_PLUNGE,
                0,
                0L,
                captureLiveStats(simulator.getCurrentTime()),
                0.0,
                false);
        resolveHit(simulator, hit);
        simulator.advanceTime(58.0 * FRAME);
    }

    private void enterNightsoul(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        long generation = ++nightsoulGeneration;
        queueCommand(simulator, new PendingCommand(
                castTime + getTalentValue(
                        "Nightsoul Entry Delay Frames", 9.0) * FRAME,
                CommandKind.NIGHTSOUL_ENTRY,
                generation));
        simulator.advanceTime(42.0 * FRAME);
    }

    private void activateNightsoul(
            CombatSimulator simulator,
            long generation) {
        if (generation != nightsoulGeneration) {
            return;
        }
        double currentTime = simulator.getCurrentTime();
        nightsoulActive = true;
        nightsoulPoints = 0.0;
        loopShotStep = 0;
        particlesGenerated = false;
        c2FirstHitPending = constellation >= 2;
        c2CannonBonusAvailable = constellation >= 2;
        nightsoulExpirationTime = currentTime
                + getTalentValue(
                        "Nightsoul Duration Frames", 610.0) * FRAME;
        markSkillUsed(currentTime, simulator.getApplicableBuffs(this));
        queueCommand(simulator, new PendingCommand(
                currentTime + getTalentValue(
                        "Nightsoul Periodic Point Frames", 30.0) * FRAME,
                CommandKind.NIGHTSOUL_POINT,
                generation));
        queueCommand(simulator, new PendingCommand(
                nightsoulExpirationTime,
                CommandKind.NIGHTSOUL_EXIT,
                generation));
    }

    private void loopShot(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        int step = loopShotStep;
        gainNightsoulPoints(getTalentValue("Loop Shot Points", 3.0));
        StatsContainer snapshot = captureLiveStats(castTime);
        for (int index = 0; index < LOOP_HIT_FRAMES[step].length;
                index++) {
            queueHit(simulator, new PendingHit(
                    castTime + LOOP_HIT_FRAMES[step][index] * FRAME,
                    HitKind.LOOP_SHOT,
                    index,
                    0L,
                    snapshot,
                    0.0,
                    false));
        }
        loopShotStep = (loopShotStep + 1) % LOOP_HIT_FRAMES.length;
        simulator.advanceTime(53.0 * FRAME);
    }

    private void scalespikerCannon(CombatSimulator simulator) {
        double maximum = getTalentValue(
                "Nightsoul Maximum Points", 20.0);
        if (nightsoulPoints + EPSILON < maximum) {
            throw new IllegalStateException(
                    "Scalespiker Cannon requires maximum Nightsoul points");
        }
        double castTime = simulator.getCurrentTime();
        queueCommand(simulator, new PendingCommand(
                castTime + 36.0 * FRAME,
                CommandKind.CANNON_RELEASE,
                nightsoulGeneration));
        simulator.advanceTime(135.0 * FRAME);
    }

    private void releaseCannon(
            CombatSimulator simulator,
            long generation) {
        if (generation != nightsoulGeneration
                || !isNightsoulActiveAt(simulator.getCurrentTime())) {
            return;
        }
        nightsoulPoints = 0.0;
        double currentTime = simulator.getCurrentTime();
        int consumedA4 = consumeA4Stacks(currentTime);
        double multiplierBonus = consumedA4
                * getTalentValue(
                        "A4 ATK Multiplier Per Stack", 3.2);
        StatsContainer snapshot = captureLiveStats(currentTime);
        boolean c2Bonus = c2CannonBonusAvailable;
        double impactTime = currentTime + 12.0 * FRAME;
        queueHit(simulator, new PendingHit(
                impactTime,
                HitKind.CANNON,
                0,
                0L,
                snapshot,
                multiplierBonus,
                c2Bonus));
        if (constellation >= 6) {
            queueHit(simulator, new PendingHit(
                    impactTime + getTalentValue(
                            "C6 Rebound Travel Frames", 50.0) * FRAME,
                    HitKind.C6_REBOUND,
                    0,
                    0L,
                    snapshot,
                    multiplierBonus,
                    c2Bonus));
        }
    }

    private void hailToTheAlmightyDragonlord(
            CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        long generation = ++ajawGeneration;
        queueCommand(simulator, new PendingCommand(
                castTime + 1.0 * FRAME,
                CommandKind.BURST_COOLDOWN,
                generation));
        queueCommand(simulator, new PendingCommand(
                castTime + 5.0 * FRAME,
                CommandKind.BURST_ENERGY,
                generation));
        queueHit(simulator, new PendingHit(
                castTime + 161.0 * FRAME,
                HitKind.BURST_INITIAL,
                0,
                generation,
                captureLiveStats(castTime),
                0.0,
                false));
        for (int index = 0; index < AJAW_HIT_FRAMES.length; index++) {
            queueHit(simulator, new PendingHit(
                    castTime + AJAW_HIT_FRAMES[index] * FRAME,
                    HitKind.AJAW_BREATH,
                    index,
                    generation,
                    null,
                    0.0,
                    false));
        }
        if (isNightsoulActiveAt(castTime)) {
            nightsoulExpirationTime += getTalentValue(
                    "Burst Nightsoul Extension", 1.7);
            queueCommand(simulator, new PendingCommand(
                    nightsoulExpirationTime,
                    CommandKind.NIGHTSOUL_EXIT,
                    nightsoulGeneration));
        }
        simulator.advanceTime(126.0 * FRAME);
    }

    private void resolveHit(
            CombatSimulator simulator,
            PendingHit hit) {
        if ((hit.kind == HitKind.BURST_INITIAL
                || hit.kind == HitKind.AJAW_BREATH)
                && hit.generation != ajawGeneration) {
            return;
        }
        switch (hit.kind) {
            case NORMAL:
                performHit(
                        simulator,
                        hit,
                        "Nightsun Style N" + (hit.index + 1),
                        getTalentValue(
                                "N" + (hit.index + 1),
                                NORMAL_T9[hit.index]),
                        Element.PHYSICAL,
                        StatType.NORMAL_ATTACK_DMG_BONUS,
                        ActionType.NORMAL,
                        ICDType.None,
                        ICDTag.NormalAttack,
                        0.0,
                        true);
                break;
            case CHARGED:
                performHit(
                        simulator,
                        hit,
                        "Nightsun Style Charged Attack Hit "
                                + (hit.index + 1),
                        getTalentValue("Charged Attack Hit", 0.889540),
                        Element.PHYSICAL,
                        StatType.CHARGED_ATTACK_DMG_BONUS,
                        ActionType.CHARGE,
                        ICDType.None,
                        ICDTag.ChargedAttack,
                        0.0,
                        true);
                break;
            case HIGH_PLUNGE:
                performHit(
                        simulator,
                        hit,
                        "Nightsun Style High Plunge",
                        getTalentValue("High Plunge", 3.422517),
                        Element.PHYSICAL,
                        StatType.PLUNGING_ATTACK_DMG_BONUS,
                        ActionType.PLUNGE,
                        ICDType.None,
                        ICDTag.PlungeAttack,
                        0.0,
                        true);
                break;
            case LOOP_SHOT:
                if (hit.index == 0) {
                    triggerC4Energy(simulator.getCurrentTime());
                }
                performHit(
                        simulator,
                        hit,
                        "Canopy Hunter Loop Shot " + (hit.index + 1),
                        skillValue(
                                "Loop Shot Hit",
                                0.973760,
                                1.145600),
                        Element.DENDRO,
                        StatType.SKILL_DMG_BONUS,
                        ActionType.SKILL,
                        ICDType.KinichLoopShot,
                        ICDTag.Kinich_LoopShot,
                        1.0,
                        false);
                break;
            case CANNON:
                c2CannonBonusAvailable = false;
                triggerC4Energy(simulator.getCurrentTime());
                performHit(
                        simulator,
                        hit,
                        "Scalespiker Cannon",
                        skillValue(
                                "Scalespiker Cannon",
                                11.686480,
                                13.748800) + hit.multiplierBonus,
                        Element.DENDRO,
                        StatType.SKILL_DMG_BONUS,
                        ActionType.SKILL,
                        ICDType.KinichScalespikerCannon,
                        ICDTag.Kinich_ScalespikerCannon,
                        1.0,
                        false);
                break;
            case C6_REBOUND:
                performHit(
                        simulator,
                        hit,
                        "Scalespiker Cannon C6 Rebound",
                        getTalentValue(
                                "C6 Rebound Multiplier", 7.0)
                                + hit.multiplierBonus,
                        Element.DENDRO,
                        StatType.SKILL_DMG_BONUS,
                        ActionType.SKILL,
                        ICDType.KinichScalespikerCannon,
                        ICDTag.Kinich_ScalespikerCannon,
                        1.0,
                        false);
                break;
            case BURST_INITIAL:
                performHit(
                        simulator,
                        hit,
                        "Hail to the Almighty Dragonlord Initial Ajaw Attack",
                        burstValue(
                                "Initial Ajaw Attack",
                                2.278000,
                                2.680000),
                        Element.DENDRO,
                        StatType.BURST_DMG_BONUS,
                        ActionType.BURST,
                        ICDType.Standard,
                        ICDTag.ElementalBurst,
                        1.0,
                        false);
                break;
            case AJAW_BREATH:
                performHit(
                        simulator,
                        hit,
                        "Ajaw Dragon Breath " + (hit.index + 1),
                        burstValue(
                                "Ajaw Dragon Breath",
                                2.052512,
                                2.414720),
                        Element.DENDRO,
                        StatType.BURST_DMG_BONUS,
                        ActionType.BURST,
                        ICDType.Standard,
                        ICDTag.ElementalBurst,
                        1.0,
                        false);
                break;
            default:
                throw new IllegalStateException(
                        "Unknown Kinich hit kind " + hit.kind);
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
        AttackAction action = new AttackAction(
                displayName,
                multiplier,
                hitElement,
                StatType.BASE_ATK,
                bonusStat,
                0.0,
                true,
                actionType);
        action.setICD(icdType, icdTag, gauge);
        action.setShatterTrigger(shatter);
        HitlagProfile hitlagProfile = hitlagProfile(hit);
        if (hitlagProfile != null) {
            action.setHitlagProfile(hitlagProfile);
        }
        if (actionType == ActionType.SKILL) {
            action.setCountsAsSkillDmg(true);
            if (constellation >= 1
                    && (hit.kind == HitKind.CANNON
                            || hit.kind == HitKind.C6_REBOUND)) {
                action.addBonusStat(
                        StatType.CRIT_DMG,
                        getTalentValue(
                                "C1 Cannon CRIT DMG", 1.0));
            }
            if (constellation >= 2 && hit.c2CannonBonus
                    && (hit.kind == HitKind.CANNON
                            || hit.kind == HitKind.C6_REBOUND)) {
                action.addBonusStat(
                        StatType.DMG_BONUS_ALL,
                        getTalentValue(
                                "C2 Cannon DMG Bonus", 1.0));
            }
        }
        if (actionType == ActionType.BURST) {
            action.setCountsAsBurstDmg(true);
            if (constellation >= 4) {
                action.addBonusStat(
                        StatType.DMG_BONUS_ALL,
                        getTalentValue(
                                "C4 Burst DMG Bonus", 0.7));
            }
        }
        StatsContainer snapshot = hit.snapshot == null
                ? captureLiveStats(simulator.getCurrentTime())
                : hit.snapshot;
        action.setStatSnapshot(snapshot);
        resolvingAction = action;
        resolvingParticleEligible = !particlesGenerated
                && (hit.kind == HitKind.CANNON
                        || hit.kind == HitKind.C6_REBOUND);
        resolvingC2Eligible = c2FirstHitPending
                && (hit.kind == HitKind.LOOP_SHOT
                        || hit.kind == HitKind.CANNON
                        || hit.kind == HitKind.C6_REBOUND);
        try {
            simulator.performActionWithoutTimeAdvance(characterId, action);
        } finally {
            resolvingAction = null;
            resolvingParticleEligible = false;
            resolvingC2Eligible = false;
        }
    }

    private static HitlagProfile hitlagProfile(PendingHit hit) {
        switch (hit.kind) {
            case NORMAL:
                return NORMAL_HITLAG[hit.index];
            case CHARGED:
                return CHARGED_HITLAG;
            case CANNON:
                return CANNON_HITLAG;
            case C6_REBOUND:
                return C6_REBOUND_HITLAG;
            case BURST_INITIAL:
                return BURST_INITIAL_HITLAG;
            case AJAW_BREATH:
                return AJAW_BREATH_HITLAG;
            default:
                return null;
        }
    }

    private void handleAcceptedDamage(
            CombatSimulator simulator,
            Character actor,
            AttackAction action,
            double damage,
            double time) {
        if (actor != this || action != resolvingAction || damage <= 0.0) {
            return;
        }
        if (resolvingC2Eligible) {
            c2FirstHitPending = false;
            simulator.applyTeamBuffNoStack(new SimpleBuff(
                    "Kinich Tiger Beetle's Palm",
                    BuffId.KINICH_C2_DENDRO_RES_SHRED,
                    getTalentValue("C2 Duration", 6.0),
                    time,
                    stats -> stats.add(
                            StatType.DENDRO_RES_SHRED,
                            getTalentValue(
                                    "C2 Dendro RES Shred", 0.3)))
                    .sourcedBy(characterId));
        }
        if (resolvingParticleEligible) {
            particlesGenerated = true;
            queueCommand(simulator, new PendingCommand(
                    time + getTalentValue(
                            "Particle Travel Frames", 100.0) * FRAME,
                    CommandKind.PARTICLE,
                    0L));
        }
    }

    private void triggerC4Energy(double currentTime) {
        if (constellation < 4
                || currentTime + EPSILON
                        < nextC4EnergyAllowedTime) {
            return;
        }
        nextC4EnergyAllowedTime = currentTime
                + getTalentValue("C4 Energy Cooldown", 2.8);
        receiveFlatEnergy(getTalentValue("C4 Energy", 5.0));
    }

    private void generatePeriodicPoint(
            CombatSimulator simulator,
            long generation) {
        if (generation != nightsoulGeneration
                || !isNightsoulActiveAt(simulator.getCurrentTime())) {
            return;
        }
        gainNightsoulPoints(getTalentValue(
                "Nightsoul Periodic Points", 1.0));
        double nextTime = simulator.getCurrentTime()
                + getTalentValue(
                        "Nightsoul Periodic Point Frames", 30.0) * FRAME;
        if (nextTime < nightsoulExpirationTime - EPSILON) {
            queueCommand(simulator, new PendingCommand(
                    nextTime,
                    CommandKind.NIGHTSOUL_POINT,
                    generation));
        }
    }

    private void gainNightsoulPoints(double amount) {
        nightsoulPoints = Math.min(
                getTalentValue("Nightsoul Maximum Points", 20.0),
                nightsoulPoints + Math.max(0.0, amount));
    }

    private int consumeA4Stacks(double currentTime) {
        if (currentTime + EPSILON >= a4ExpirationTime) {
            a4Stacks = 0;
        }
        int consumed = a4Stacks;
        a4Stacks = 0;
        a4ExpirationTime = Double.NEGATIVE_INFINITY;
        return consumed;
    }

    private void rejectNightsoulBasic(
            CombatSimulator simulator,
            String actionName) {
        if (isNightsoulActiveAt(simulator.getCurrentTime())) {
            throw new IllegalStateException(
                    actionName + " is unavailable during Nightsoul");
        }
    }

    private boolean isNightsoulActiveAt(double currentTime) {
        return nightsoulActive
                && currentTime < nightsoulExpirationTime - EPSILON;
    }

    private void cancelNightsoul() {
        nightsoulGeneration++;
        nightsoulActive = false;
        nightsoulPoints = 0.0;
        nightsoulExpirationTime = Double.NEGATIVE_INFINITY;
        loopShotStep = 0;
        c2FirstHitPending = false;
        c2CannonBonusAvailable = false;
        pendingCommands.removeIf(command ->
                command.kind.isNightsoulOwned());
    }

    private double skillValue(
            String baseKey,
            double t9,
            double c3) {
        return getTalentValue(
                constellation >= 3 ? baseKey + " C3" : baseKey,
                constellation >= 3 ? c3 : t9);
    }

    private double burstValue(
            String baseKey,
            double t9,
            double c5) {
        return getTalentValue(
                constellation >= 5 ? baseKey + " C5" : baseKey,
                constellation >= 5 ? c5 : t9);
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
                case NIGHTSOUL_ENTRY:
                    activateNightsoul(
                            activeSimulator, command.generation);
                    break;
                case NIGHTSOUL_POINT:
                    generatePeriodicPoint(
                            activeSimulator, command.generation);
                    break;
                case NIGHTSOUL_EXIT:
                    if (command.generation == nightsoulGeneration
                            && activeSimulator.getCurrentTime()
                                    + EPSILON
                                    >= nightsoulExpirationTime) {
                        cancelNightsoul();
                    }
                    break;
                case CANNON_RELEASE:
                    releaseCannon(
                            activeSimulator, command.generation);
                    break;
                case BURST_COOLDOWN:
                    if (command.generation == ajawGeneration) {
                        markBurstCooldownUsed(
                                activeSimulator.getCurrentTime(),
                                activeSimulator.getApplicableBuffs(this));
                    }
                    break;
                case BURST_ENERGY:
                    if (command.generation == ajawGeneration) {
                        spendBurstEnergy(
                                activeSimulator.getCurrentTime());
                    }
                    break;
                case PARTICLE:
                    activeSimulator.getEnergyDistributor()
                            .distributeParticles(
                                    Element.DENDRO,
                                    getTalentValue("Particle Count", 5.0),
                                    ParticleType.PARTICLE);
                    break;
                default:
                    throw new IllegalStateException(
                            "Unknown Kinich command " + command.kind);
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
        LOOP_SHOT,
        CANNON,
        C6_REBOUND,
        BURST_INITIAL,
        AJAW_BREATH
    }

    private enum CommandKind {
        NIGHTSOUL_ENTRY,
        NIGHTSOUL_POINT,
        NIGHTSOUL_EXIT,
        CANNON_RELEASE,
        BURST_COOLDOWN,
        BURST_ENERGY,
        PARTICLE;

        private boolean isNightsoulOwned() {
            return this == NIGHTSOUL_ENTRY
                    || this == NIGHTSOUL_POINT
                    || this == NIGHTSOUL_EXIT
                    || this == CANNON_RELEASE;
        }
    }

    /** Immutable delayed-hit payload with an action-owned stat snapshot. */
    private static final class PendingHit {
        private final double time;
        private final HitKind kind;
        private final int index;
        private final long generation;
        private final StatsContainer snapshot;
        private final double multiplierBonus;
        private final boolean c2CannonBonus;

        private PendingHit(
                double time,
                HitKind kind,
                int index,
                long generation,
                StatsContainer snapshot,
                double multiplierBonus,
                boolean c2CannonBonus) {
            this.time = time;
            this.kind = kind;
            this.index = index;
            this.generation = generation;
            this.snapshot = snapshot == null
                    ? null : snapshot.merge(null);
            this.multiplierBonus = multiplierBonus;
            this.c2CannonBonus = c2CannonBonus;
        }

        private PendingHit copy() {
            return new PendingHit(
                    time,
                    kind,
                    index,
                    generation,
                    snapshot,
                    multiplierBonus,
                    c2CannonBonus);
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

    /** Immutable owner-bound snapshot of all Kinich-specific runtime state. */
    private static final class KinichState implements State {
        private final Kinich owner;
        private final int normalAttackStep;
        private final int loopShotStep;
        private final long nightsoulGeneration;
        private final boolean nightsoulActive;
        private final double nightsoulPoints;
        private final double nightsoulExpirationTime;
        private final boolean particlesGenerated;
        private final boolean c2FirstHitPending;
        private final boolean c2CannonBonusAvailable;
        private final int a4Stacks;
        private final double a4ExpirationTime;
        private final double nextC4EnergyAllowedTime;
        private final long ajawGeneration;
        private final List<PendingHit> pendingHits;
        private final List<PendingCommand> pendingCommands;

        private KinichState(
                Kinich owner,
                int normalAttackStep,
                int loopShotStep,
                long nightsoulGeneration,
                boolean nightsoulActive,
                double nightsoulPoints,
                double nightsoulExpirationTime,
                boolean particlesGenerated,
                boolean c2FirstHitPending,
                boolean c2CannonBonusAvailable,
                int a4Stacks,
                double a4ExpirationTime,
                double nextC4EnergyAllowedTime,
                long ajawGeneration,
                List<PendingHit> pendingHits,
                List<PendingCommand> pendingCommands) {
            this.owner = owner;
            this.normalAttackStep = normalAttackStep;
            this.loopShotStep = loopShotStep;
            this.nightsoulGeneration = nightsoulGeneration;
            this.nightsoulActive = nightsoulActive;
            this.nightsoulPoints = nightsoulPoints;
            this.nightsoulExpirationTime = nightsoulExpirationTime;
            this.particlesGenerated = particlesGenerated;
            this.c2FirstHitPending = c2FirstHitPending;
            this.c2CannonBonusAvailable = c2CannonBonusAvailable;
            this.a4Stacks = a4Stacks;
            this.a4ExpirationTime = a4ExpirationTime;
            this.nextC4EnergyAllowedTime = nextC4EnergyAllowedTime;
            this.ajawGeneration = ajawGeneration;
            this.pendingHits = copyHits(pendingHits);
            this.pendingCommands = copyCommands(pendingCommands);
        }
    }
}

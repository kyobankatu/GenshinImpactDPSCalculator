package model.character;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.DoubleSupplier;

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
import simulation.action.HitlagProfile;
import simulation.action.SkillActionMode;
import simulation.event.SimpleTimerEvent;

/**
 * Varesa's deterministic fixed-target Fiery Passion offensive slice.
 *
 * <p>Catalyst basics, high Plunge, two-charge Press Skill, local Nightsoul
 * points, Fiery Passion, Apex Drive, Flying Kick, Volcano Kablam, particles,
 * and representable A1/A4/C1-C6 offense follow pinned gcsim revision
 * {@code ef41805d855a60b9e1035293584b85c085dc69e7}. Charged, Skill, and
 * Volcano Kablam use one source-backed combat-cycle ICD group.</p>
 *
 * <p>Movement, terrain and height geometry, team Nightsoul Burst plumbing,
 * random or multi-target selection, current HP and healing, hitlag, stamina,
 * low Plunge, exploration, and defensive state fail closed. Skill Hold is
 * also excluded because it is absent from the pinned source implementation.
 * A4 therefore uses an explicit typed ingress instead of fabricating a team
 * Nightsoul Burst event.</p>
 */
public final class Varesa extends Character implements
        SimulatorInitializedCharacterEffect,
        SnapshotAwareCharacterEffect,
        SwitchAwareCharacter {
    private static final double FRAME = 1.0 / 60.0;
    private static final double EPSILON = 1e-9;
    /** Unambiguous base-normal hitlag pinned to gcsim {@code 3647a07a7cc3004bc1e79d9bb5f7444de20dceaa}. */
    private static final HitlagProfile[] NORMAL_HITLAG = {
        new HitlagProfile(0.0, 0.0, true, false, false),
        new HitlagProfile(0.0, 0.0, true, false, false),
        new HitlagProfile(0.03, 0.01, true, false, false)
    };
    private static final double[] NORMAL_T9 = {
        0.795233, 0.680476, 0.957318
    };
    private static final double[] NORMAL_C5 = {
        0.935568, 0.800560, 1.126256
    };
    private static final double[] FIERY_NORMAL_T9 = {
        0.924922, 0.884490, 1.250969
    };
    private static final double[] FIERY_NORMAL_C5 = {
        1.088144, 1.040576, 1.471728
    };
    private static final int[] NORMAL_HIT_FRAMES = { 17, 7, 33 };
    private static final int[] NORMAL_DURATIONS = { 43, 30, 59 };
    private static final int[] FIERY_NORMAL_HIT_FRAMES = { 11, 29, 37 };
    private static final int[] FIERY_NORMAL_DURATIONS = { 33, 47, 63 };

    private final DoubleSupplier particleRandom;
    private CombatSimulator initializedSimulator;
    private int normalAttackStep;
    private long nightsoulGeneration;
    private boolean nightsoulActive;
    private double nightsoulPoints;
    private double nightsoulExpirationTime = Double.NEGATIVE_INFINITY;
    private boolean freeSkill;
    private double followUpExpirationTime = Double.NEGATIVE_INFINITY;
    private double apexExpirationTime = Double.NEGATIVE_INFINITY;
    private double a1ExpirationTime = Double.NEGATIVE_INFINITY;
    private double c4PlungeExpirationTime = Double.NEGATIVE_INFINITY;
    private List<Double> a4StackExpirations = new ArrayList<>();
    private List<PendingHit> pendingHits = new ArrayList<>();
    private List<PendingCommand> pendingCommands = new ArrayList<>();

    /** Constructs repository-default C6 Varesa. */
    public Varesa(Weapon weapon, ArtifactSet artifacts) {
        this(weapon, artifacts, TalentDataManager.getInstance(), 6,
                Math::random);
    }

    /** Constructs Varesa at an explicit constellation. */
    public Varesa(
            Weapon weapon,
            ArtifactSet artifacts,
            int constellation) {
        this(weapon, artifacts, TalentDataManager.getInstance(),
                constellation, Math::random);
    }

    /**
     * Constructs Varesa with injectable talent data and particle randomness.
     *
     * @param weapon equipped catalyst, or {@code null}
     * @param artifacts equipped artifact set, or {@code null}
     * @param talentData static character-data source
     * @param constellation constellation level in {@code [0, 6]}
     * @param particleRandom particle draw source in {@code [0, 1)}
     */
    public Varesa(
            Weapon weapon,
            ArtifactSet artifacts,
            TalentDataSource talentData,
            int constellation,
            DoubleSupplier particleRandom) {
        super(talentData);
        if (constellation < 0 || constellation > 6) {
            throw new IllegalArgumentException(
                    "Varesa constellation must be between 0 and 6");
        }
        if (particleRandom == null) {
            throw new IllegalArgumentException(
                    "Varesa particle random source is required");
        }
        name = "Varesa";
        characterId = CharacterId.VARESA;
        element = Element.ELECTRO;
        this.weapon = weapon;
        this.artifacts = new ArtifactSet[] { artifacts };
        this.constellation = constellation;
        this.particleRandom = particleRandom;
        baseStats.set(StatType.BASE_HP,
                getTalentValue("Base HP", 12699.0));
        baseStats.set(StatType.BASE_ATK,
                getTalentValue("Base ATK", 356.0));
        baseStats.set(StatType.BASE_DEF,
                getTalentValue("Base DEF", 782.0));
        baseStats.add(StatType.CRIT_RATE,
                getTalentValue("Ascension CRIT Rate", 0.192));
        setSkillCD(getTalentValue("Skill Cooldown", 9.0));
        setSkillMaxCharges(2);
        setBurstCD(getTalentValue("Burst Cooldown", 18.0));
    }

    /** Binds Varesa-owned delayed work to exactly one simulator. */
    @Override
    public void initializeForSimulator(CombatSimulator simulator) {
        if (simulator == null) {
            throw new IllegalArgumentException("Varesa simulator is required");
        }
        if (!simulator.getPartyMembers().contains(this)) {
            throw new IllegalArgumentException(
                    "Varesa must belong to the simulator party");
        }
        if (initializedSimulator != null
                && initializedSimulator != simulator) {
            throw new IllegalStateException(
                    "Varesa cannot be reused across simulators");
        }
        initializedSimulator = simulator;
    }

    /** Captures all local states and reconstructable future work. */
    @Override
    public State captureCharacterState() {
        return new VaresaState(
                this,
                normalAttackStep,
                nightsoulGeneration,
                nightsoulActive,
                nightsoulPoints,
                nightsoulExpirationTime,
                freeSkill,
                followUpExpirationTime,
                apexExpirationTime,
                a1ExpirationTime,
                c4PlungeExpirationTime,
                a4StackExpirations,
                pendingHits,
                pendingCommands);
    }

    /** Accepts state captured from this exact Varesa instance only. */
    @Override
    public boolean acceptsCharacterState(State state) {
        return state instanceof VaresaState
                && ((VaresaState) state).owner == this;
    }

    /** Restores local state and re-registers surviving events once. */
    @Override
    public void restoreCharacterState(
            State state,
            CombatSimulator simulator) {
        if (!acceptsCharacterState(state)) {
            throw new IllegalArgumentException("Unexpected Varesa state");
        }
        initializeForSimulator(simulator);
        VaresaState restored = (VaresaState) state;
        normalAttackStep = restored.normalAttackStep;
        nightsoulGeneration = restored.nightsoulGeneration;
        nightsoulActive = restored.nightsoulActive;
        nightsoulPoints = restored.nightsoulPoints;
        nightsoulExpirationTime = restored.nightsoulExpirationTime;
        freeSkill = restored.freeSkill;
        followUpExpirationTime = restored.followUpExpirationTime;
        apexExpirationTime = restored.apexExpirationTime;
        a1ExpirationTime = restored.a1ExpirationTime;
        c4PlungeExpirationTime = restored.c4PlungeExpirationTime;
        a4StackExpirations = new ArrayList<>(restored.a4StackExpirations);
        pendingHits = copyHits(restored.pendingHits);
        pendingCommands = copyCommands(restored.pendingCommands);
        double currentTime = simulator.getCurrentTime();
        refreshTemporalState(currentTime);
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

    /** Returns 30 Energy in Apex Drive and 70 Energy otherwise. */
    @Override
    public double getEnergyCost() {
        double currentTime = initializedSimulator == null
                ? Double.NEGATIVE_INFINITY
                : initializedSimulator.getCurrentTime();
        return isApexDriveActiveAt(currentTime) ? 30.0
                : getTalentValue("Energy Cost", 70.0);
    }

    /** Keeps Varesa's Energy bar at its source-backed 70-point maximum. */
    @Override
    public double getMaxEnergy() {
        return getTalentValue("Energy Cost", 70.0);
    }

    /** Applies the live, capped A4 ATK stacks. */
    @Override
    public void applyPassive(StatsContainer stats) {
        double currentTime = initializedSimulator == null
                ? Double.NEGATIVE_INFINITY
                : initializedSimulator.getCurrentTime();
        int stacks = activeA4StackCount(currentTime);
        if (stacks > 0) {
            stats.add(
                    StatType.ATK_PERCENT,
                    stacks * getTalentValue("A4 ATK Per Stack", 0.35));
        }
    }

    /** Ends local Nightsoul and resets the Normal string on switch-out. */
    @Override
    public void onSwitchOut(CombatSimulator simulator) {
        normalAttackStep = 0;
        endNightsoul();
    }

    /** Resets the Normal string on switch-in. */
    @Override
    public void onSwitchIn(CombatSimulator simulator) {
        normalAttackStep = 0;
    }

    /** Returns whether local Nightsoul's Blessing is active. */
    public boolean isNightsoulActive() {
        refreshFromInitializedSimulator();
        return nightsoulActive;
    }

    /** Returns the current local Nightsoul-point balance. */
    public double getNightsoulPoints() {
        refreshFromInitializedSimulator();
        return nightsoulPoints;
    }

    /** Returns whether the next Skill is the free source-backed cast. */
    public boolean isFreeSkillAvailable() {
        refreshFromInitializedSimulator();
        return freeSkill;
    }

    /** Returns whether Apex Drive is active. */
    public boolean isApexDriveActive() {
        refreshFromInitializedSimulator();
        return isApexDriveActiveAt(currentTime());
    }

    /** Returns the number of live explicit A4 stacks. */
    public int getA4StackCount() {
        return activeA4StackCount(currentTime());
    }

    /** Returns the number of unresolved Varesa-owned hits. */
    public int getPendingHitCount() {
        return pendingHits.size();
    }

    /**
     * Represents one team Nightsoul Burst for A4 without automatic plumbing.
     *
     * @param simulator owning simulator at the sourced event timestamp
     */
    public void notifyNightsoulBurst(CombatSimulator simulator) {
        initializeForSimulator(simulator);
        double currentTime = simulator.getCurrentTime();
        activeA4StackCount(currentTime);
        int maximum = (int) getTalentValue("A4 Maximum Stacks", 2.0);
        if (a4StackExpirations.size() >= maximum) {
            a4StackExpirations.remove(0);
        }
        a4StackExpirations.add(currentTime
                + getTalentValue("A4 Stack Duration", 12.0));
        Collections.sort(a4StackExpirations);
    }

    /** Reports that movement, terrain, and height geometry are excluded. */
    public boolean isMovementTerrainGeometryRepresented() {
        return false;
    }

    /** Reports that team Nightsoul Burst plumbing is excluded. */
    public boolean isNightsoulBurstTeamPlumbingRepresented() {
        return false;
    }

    /** Reports that random and multi-target selection are excluded. */
    public boolean isRandomMultiTargetSelectionRepresented() {
        return false;
    }

    /** Reports that player HP changes and healing are excluded. */
    public boolean isPlayerHpHealingRepresented() {
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

    /** Reports that exploration and defensive state are excluded. */
    public boolean isExplorationDefensiveStateRepresented() {
        return false;
    }

    /** Dispatches the bounded typed action set. */
    @Override
    public void onAction(
            CharacterActionRequest request,
            CombatSimulator simulator) {
        if (request == null) {
            throw new IllegalArgumentException("Varesa action is required");
        }
        initializeForSimulator(simulator);
        refreshTemporalState(simulator.getCurrentTime());
        if (request.getKey() == CharacterActionKey.SKILL
                && request.getSkillMode() != SkillActionMode.PRESS) {
            throw new IllegalArgumentException(
                    "Varesa supports Press Skill only");
        }
        if (request.getKey() != CharacterActionKey.NORMAL) {
            normalAttackStep = 0;
        }
        switch (request.getKey()) {
            case NORMAL:
                if (hasFollowUp(simulator.getCurrentTime())) {
                    chargedAttack(simulator, true);
                } else {
                    normalAttack(simulator);
                }
                break;
            case CHARGE:
                chargedAttack(simulator, hasFollowUp(
                        simulator.getCurrentTime()));
                break;
            case PLUNGE:
                highPlunge(simulator);
                break;
            case SKILL:
                rush(simulator);
                break;
            case BURST:
                burst(simulator);
                break;
            default:
                throw new IllegalArgumentException(
                        "Unsupported action for Varesa: "
                                + request.getKey());
        }
    }

    private void normalAttack(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        boolean fiery = nightsoulActive;
        int step = normalAttackStep;
        int hitFrame = fiery ? FIERY_NORMAL_HIT_FRAMES[step]
                : NORMAL_HIT_FRAMES[step];
        int duration = fiery ? FIERY_NORMAL_DURATIONS[step]
                : NORMAL_DURATIONS[step];
        queueHit(simulator, new PendingHit(
                castTime + hitFrame * FRAME,
                HitKind.NORMAL,
                step,
                fiery,
                false,
                false));
        normalAttackStep = (normalAttackStep + 1) % NORMAL_T9.length;
        simulator.advanceTime(duration * FRAME);
    }

    private void chargedAttack(
            CombatSimulator simulator,
            boolean followUp) {
        double castTime = simulator.getCurrentTime();
        boolean fiery = nightsoulActive;
        if (followUp) {
            followUpExpirationTime = Double.NEGATIVE_INFINITY;
        }
        int hitFrame = followUp ? 11 : 69;
        queueHit(simulator, new PendingHit(
                castTime + hitFrame * FRAME,
                HitKind.CHARGED,
                0,
                fiery,
                false,
                false));
        // The fixed-target slice follows the earliest source-backed transition
        // into High Plunge instead of simulating airborne height.
        int duration = followUp ? (fiery ? 24 : 20) : (fiery ? 81 : 77);
        simulator.advanceTime(duration * FRAME);
    }

    private void highPlunge(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        boolean fiery = nightsoulActive;
        if (fiery) {
            queueCommand(simulator, new PendingCommand(
                    castTime + 2.0 * FRAME,
                    CommandKind.CONSUME_NIGHTSOUL,
                    nightsoulGeneration,
                    0.0));
        }
        if (fiery || constellation >= 2) {
            activateApexDrive(castTime);
        }
        queueHit(simulator, new PendingHit(
                castTime + (fiery ? 41.0 : 37.0) * FRAME,
                HitKind.HIGH_PLUNGE,
                0,
                fiery,
                false,
                false,
                plungeFlatBonus(simulator, castTime, fiery, true)));
        simulator.advanceTime((fiery ? 90.0 : 72.0) * FRAME);
        if (fiery) {
            endNightsoul();
        }
    }

    private void rush(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        boolean fiery = nightsoulActive;
        boolean particleEligible = !freeSkill;
        if (freeSkill) {
            freeSkill = false;
        } else {
            markSkillUsed(castTime, simulator.getApplicableBuffs(this));
        }
        a1ExpirationTime = castTime
                + getTalentValue("A1 Duration", 5.0);
        followUpExpirationTime = castTime + 5.0;
        if (isApexDriveActiveAt(castTime)) {
            apexExpirationTime = Double.NEGATIVE_INFINITY;
        }
        queueHit(simulator, new PendingHit(
                castTime + (fiery ? 2.0 : 5.0) * FRAME,
                HitKind.SKILL,
                0,
                fiery,
                particleEligible,
                false));
        queueCommand(simulator, new PendingCommand(
                castTime + 5.0 * FRAME,
                CommandKind.GAIN_SKILL_POINTS,
                nightsoulGeneration,
                getTalentValue("Skill Nightsoul Points", 20.0)));
        simulator.advanceTime((fiery ? 52.0 : 43.0) * FRAME);
    }

    private void burst(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        if (isApexDriveActiveAt(castTime)) {
            volcanoKablam(simulator, castTime);
            return;
        }
        boolean fiery = nightsoulActive;
        boolean c4BurstBonus = constellation >= 4
                && (fiery || isApexDriveActiveAt(castTime));
        if (constellation >= 4 && !c4BurstBonus) {
            c4PlungeExpirationTime = castTime
                    + getTalentValue("C4 Duration", 15.0);
        }
        markBurstCooldownUsed(
                castTime, simulator.getApplicableBuffs(this));
        queueCommand(simulator, new PendingCommand(
                castTime + 3.0 * FRAME,
                CommandKind.ENTER_NIGHTSOUL,
                nightsoulGeneration,
                0.0));
        queueCommand(simulator, new PendingCommand(
                castTime + 9.0 * FRAME,
                CommandKind.SPEND_ENERGY,
                0L,
                70.0));
        queueHit(simulator, new PendingHit(
                castTime + 88.0 * FRAME,
                HitKind.FLYING_KICK,
                0,
                fiery,
                false,
                c4BurstBonus));
        simulator.advanceTime(122.0 * FRAME);
    }

    private void volcanoKablam(
            CombatSimulator simulator,
            double castTime) {
        if (constellation >= 1) {
            a1ExpirationTime = castTime
                    + getTalentValue("A1 Duration", 5.0);
        }
        boolean c4BurstBonus = constellation >= 4;
        double standardCooldown = getTalentValue("Burst Cooldown", 18.0);
        setBurstCD(1.0);
        markBurstCooldownUsed(
                castTime, simulator.getApplicableBuffs(this));
        setBurstCD(standardCooldown);
        spendEnergy(30.0);
        queueHit(simulator, new PendingHit(
                castTime + 42.0 * FRAME,
                HitKind.VOLCANO_KABLAM,
                0,
                nightsoulActive,
                false,
                c4BurstBonus,
                plungeFlatBonus(
                        simulator, castTime, nightsoulActive, false)));
        simulator.advanceTime(47.0 * FRAME);
        apexExpirationTime = Double.NEGATIVE_INFINITY;
    }

    private void activateApexDrive(double currentTime) {
        apexExpirationTime = currentTime
                + getTalentValue("Apex Drive Frames", 140.0) * FRAME;
        if (constellation >= 6) {
            receiveFlatEnergy(getTalentValue("C6 Apex Energy", 30.0));
        }
    }

    private void enterNightsoul(double currentTime) {
        nightsoulGeneration++;
        nightsoulActive = true;
        nightsoulPoints = getTalentValue(
                "Maximum Nightsoul Points", 40.0);
        nightsoulExpirationTime = currentTime
                + getTalentValue("Nightsoul Duration", 15.0);
        freeSkill = true;
    }

    private void gainPlungeNightsoul(double currentTime) {
        double maximum = getTalentValue("Maximum Nightsoul Points", 40.0);
        nightsoulPoints = Math.min(
                maximum,
                nightsoulPoints
                        + getTalentValue("Plunge Nightsoul Points", 25.0));
        if (!nightsoulActive && nightsoulPoints + EPSILON >= maximum) {
            enterNightsoul(currentTime);
        }
    }

    private void endNightsoul() {
        if (!nightsoulActive && nightsoulPoints <= EPSILON) {
            return;
        }
        nightsoulGeneration++;
        nightsoulActive = false;
        nightsoulPoints = 0.0;
        nightsoulExpirationTime = Double.NEGATIVE_INFINITY;
        freeSkill = false;
    }

    private void resolveHit(
            CombatSimulator simulator,
            PendingHit hit) {
        refreshTemporalState(simulator.getCurrentTime());
        double multiplier;
        String displayName;
        StatType bonusStat;
        ActionType actionType;
        ICDType icdType;
        ICDTag icdTag;
        switch (hit.kind) {
            case NORMAL:
                multiplier = normalMultiplier(hit.index, hit.fiery);
                displayName = hit.fiery
                        ? "Fiery Passion N" + (hit.index + 1)
                        : "By the Horns N" + (hit.index + 1);
                bonusStat = StatType.NORMAL_ATTACK_DMG_BONUS;
                actionType = ActionType.NORMAL;
                icdType = ICDType.Standard;
                icdTag = ICDTag.NormalAttack;
                break;
            case CHARGED:
                multiplier = chargedMultiplier(hit.fiery);
                displayName = hit.fiery
                        ? "Fiery Passion Charged Attack"
                        : "Charged Attack";
                bonusStat = StatType.CHARGED_ATTACK_DMG_BONUS;
                actionType = ActionType.CHARGE;
                icdType = ICDType.Standard;
                icdTag = ICDTag.Varesa_CombatCycle;
                break;
            case HIGH_PLUNGE:
                multiplier = plungeMultiplier(hit.fiery);
                displayName = hit.fiery
                        ? "Fiery Passion High Plunge"
                        : "High Plunge";
                bonusStat = StatType.PLUNGING_ATTACK_DMG_BONUS;
                actionType = ActionType.PLUNGE;
                icdType = ICDType.None;
                icdTag = ICDTag.None;
                break;
            case SKILL:
                multiplier = skillMultiplier(hit.fiery);
                displayName = hit.fiery ? "Fiery Passion Rush" : "Rush";
                bonusStat = StatType.SKILL_DMG_BONUS;
                actionType = ActionType.SKILL;
                icdType = ICDType.Standard;
                icdTag = ICDTag.Varesa_CombatCycle;
                break;
            case FLYING_KICK:
                multiplier = burstMultiplier(hit.fiery, false);
                displayName = hit.fiery
                        ? "Fiery Passion Flying Kick"
                        : "Flying Kick";
                bonusStat = StatType.BURST_DMG_BONUS;
                actionType = ActionType.BURST;
                icdType = ICDType.None;
                icdTag = ICDTag.None;
                break;
            case VOLCANO_KABLAM:
                multiplier = burstMultiplier(false, true);
                displayName = "Volcano Kablam";
                bonusStat = StatType.PLUNGING_ATTACK_DMG_BONUS;
                actionType = ActionType.PLUNGE;
                icdType = ICDType.Standard;
                icdTag = ICDTag.Varesa_CombatCycle;
                break;
            default:
                throw new IllegalStateException(
                        "Unknown Varesa hit kind " + hit.kind);
        }
        AttackAction action = new AttackAction(
                displayName,
                multiplier,
                Element.ELECTRO,
                StatType.BASE_ATK,
                bonusStat,
                0.0,
                actionType);
        action.setICD(icdType, icdTag, 1.0);
        if (hit.kind == HitKind.NORMAL && !hit.fiery) {
            action.setHitlagProfile(NORMAL_HITLAG[hit.index]);
        }
        action.setCountsAsSkillDmg(actionType == ActionType.SKILL);
        action.setCountsAsBurstDmg(actionType == ActionType.BURST);
        action.setHitEffectTrigger(true);
        if (actionType == ActionType.CHARGE
                || actionType == ActionType.PLUNGE) {
            action.setShatterTrigger(true);
        }
        if (hit.c4BurstBonus) {
            action.addBonusStat(
                    StatType.DMG_BONUS_ALL,
                    getTalentValue("C4 Burst DMG Bonus", 1.0));
        }
        if (hit.flatDamageBonus > 0.0) {
            action.addBonusStat(
                    StatType.FLAT_DMG_BONUS,
                    hit.flatDamageBonus);
        }
        if (constellation >= 6
                && (actionType == ActionType.PLUNGE
                        || actionType == ActionType.BURST)) {
            action.addBonusStat(
                    StatType.CRIT_RATE,
                    getTalentValue("C6 CRIT Rate", 0.10));
            action.addBonusStat(
                    StatType.CRIT_DMG,
                    getTalentValue("C6 CRIT DMG", 1.0));
        }
        simulator.performActionWithoutTimeAdvance(characterId, action);
        if (hit.kind == HitKind.HIGH_PLUNGE) {
            if (constellation >= 2) {
                receiveFlatEnergy(getTalentValue("C2 Energy", 11.5));
            }
            a1ExpirationTime = Double.NEGATIVE_INFINITY;
            c4PlungeExpirationTime = Double.NEGATIVE_INFINITY;
            if (!hit.fiery) {
                gainPlungeNightsoul(simulator.getCurrentTime());
            }
        }
        if (hit.kind == HitKind.VOLCANO_KABLAM) {
            a1ExpirationTime = Double.NEGATIVE_INFINITY;
        }
        if (hit.kind == HitKind.SKILL && hit.particleEligible) {
            double count = particleRandom.getAsDouble() < 0.5
                    ? getTalentValue("Particle Count High", 3.0)
                    : getTalentValue("Particle Count Low", 2.0);
            queueCommand(simulator, new PendingCommand(
                    simulator.getCurrentTime()
                            + getTalentValue(
                                    "Particle Travel Frames", 100.0) * FRAME,
                    CommandKind.PARTICLE,
                    0L,
                    count));
        }
    }

    private double plungeFlatBonus(
            CombatSimulator simulator,
            double currentTime,
            boolean fiery,
            boolean includeC4) {
        double attack = liveAttack(currentTime);
        double flatDamage = 0.0;
        if (currentTime + EPSILON < a1ExpirationTime) {
            double ratio = constellation >= 1 || fiery
                    ? getTalentValue("A1 Enhanced ATK Ratio", 1.8)
                    : getTalentValue("A1 ATK Ratio", 0.5);
            flatDamage += attack * ratio;
        }
        if (includeC4
                && constellation >= 4
                && currentTime + EPSILON < c4PlungeExpirationTime) {
            flatDamage += Math.min(
                    attack * getTalentValue(
                            "C4 Plunge ATK Ratio", 5.0),
                    getTalentValue(
                            "C4 Plunge Flat Cap", 20000.0));
        }
        return flatDamage;
    }

    private double liveAttack(double currentTime) {
        StatsContainer stats = getEffectiveStats(currentTime);
        if (initializedSimulator != null) {
            for (Buff buff : initializedSimulator.getApplicableBuffs(this)) {
                if (!buff.isExpired(currentTime)) {
                    buff.apply(stats, currentTime);
                }
            }
        }
        return stats.getTotalAtk();
    }

    private double normalMultiplier(int step, boolean fiery) {
        if (fiery) {
            String key = "Fiery Passion N" + (step + 1)
                    + (constellation >= 5 ? " C5" : "");
            return getTalentValue(
                    key,
                    constellation >= 5
                            ? FIERY_NORMAL_C5[step]
                            : FIERY_NORMAL_T9[step]);
        }
        String key = "N" + (step + 1)
                + (constellation >= 5 ? " C5" : "");
        return getTalentValue(
                key,
                constellation >= 5
                        ? NORMAL_C5[step] : NORMAL_T9[step]);
    }

    private double chargedMultiplier(boolean fiery) {
        String key = fiery ? "Fiery Passion Charged Attack"
                : "Charged Attack";
        if (constellation >= 5) {
            key += " C5";
        }
        if (fiery) {
            return getTalentValue(
                    key, constellation >= 5 ? 1.8528 : 1.57488);
        }
        return getTalentValue(
                key, constellation >= 5 ? 1.7856 : 1.51776);
    }

    private double plungeMultiplier(boolean fiery) {
        String key = fiery ? "Fiery Passion High Plunge" : "High Plunge";
        if (constellation >= 5) {
            key += " C5";
        }
        if (fiery) {
            return getTalentValue(
                    key, constellation >= 5 ? 6.303497 : 5.133776);
        }
        return getTalentValue(
                key, constellation >= 5 ? 4.202331 : 3.422517);
    }

    private double skillMultiplier(boolean fiery) {
        String key = fiery ? "Fiery Passion Rush" : "Rush";
        return getTalentValue(key, fiery ? 1.8088 : 1.26616);
    }

    private double burstMultiplier(boolean fiery, boolean kablam) {
        String key;
        double talentNine;
        double constellationThree;
        if (kablam) {
            key = "Volcano Kablam";
            talentNine = 6.84488;
            constellationThree = 8.0528;
        } else if (fiery) {
            key = "Fiery Passion Flying Kick";
            talentNine = 9.7784;
            constellationThree = 11.504;
        } else {
            key = "Flying Kick";
            talentNine = 5.86704;
            constellationThree = 6.9024;
        }
        if (constellation >= 3) {
            key += " C3";
        }
        return getTalentValue(
                key,
                constellation >= 3 ? constellationThree : talentNine);
    }

    private boolean hasFollowUp(double currentTime) {
        return currentTime + EPSILON < followUpExpirationTime;
    }

    private boolean isApexDriveActiveAt(double currentTime) {
        return currentTime + EPSILON < apexExpirationTime;
    }

    private int activeA4StackCount(double currentTime) {
        a4StackExpirations.removeIf(expiration ->
                currentTime + EPSILON >= expiration);
        return a4StackExpirations.size();
    }

    private double currentTime() {
        return initializedSimulator == null
                ? Double.NEGATIVE_INFINITY
                : initializedSimulator.getCurrentTime();
    }

    private void refreshFromInitializedSimulator() {
        if (initializedSimulator != null) {
            refreshTemporalState(initializedSimulator.getCurrentTime());
        }
    }

    private void refreshTemporalState(double currentTime) {
        if (nightsoulActive
                && currentTime + EPSILON >= nightsoulExpirationTime) {
            endNightsoul();
        }
        if (currentTime + EPSILON >= followUpExpirationTime) {
            followUpExpirationTime = Double.NEGATIVE_INFINITY;
        }
        if (currentTime + EPSILON >= apexExpirationTime) {
            apexExpirationTime = Double.NEGATIVE_INFINITY;
        }
        if (currentTime + EPSILON >= a1ExpirationTime) {
            a1ExpirationTime = Double.NEGATIVE_INFINITY;
        }
        if (currentTime + EPSILON >= c4PlungeExpirationTime) {
            c4PlungeExpirationTime = Double.NEGATIVE_INFINITY;
        }
        activeA4StackCount(currentTime);
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
                case GAIN_SKILL_POINTS:
                    nightsoulPoints = Math.min(
                            getTalentValue("Maximum Nightsoul Points", 40.0),
                            nightsoulPoints + command.value);
                    break;
                case ENTER_NIGHTSOUL:
                    if (nightsoulActive) {
                        nightsoulPoints = getTalentValue(
                                "Maximum Nightsoul Points", 40.0);
                    } else {
                        enterNightsoul(activeSimulator.getCurrentTime());
                    }
                    break;
                case CONSUME_NIGHTSOUL:
                    if (command.generation == nightsoulGeneration) {
                        nightsoulPoints = 0.0;
                    }
                    break;
                case PARTICLE:
                    activeSimulator.getEnergyDistributor()
                            .distributeParticles(
                                    Element.ELECTRO,
                                    command.value,
                                    ParticleType.PARTICLE);
                    break;
                case SPEND_ENERGY:
                    spendEnergy(command.value);
                    break;
                default:
                    throw new IllegalStateException(
                            "Unknown Varesa command " + command.kind);
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
        SKILL,
        FLYING_KICK,
        VOLCANO_KABLAM
    }

    private enum CommandKind {
        GAIN_SKILL_POINTS,
        ENTER_NIGHTSOUL,
        CONSUME_NIGHTSOUL,
        PARTICLE,
        SPEND_ENERGY
    }

    /** Immutable delayed hit payload. */
    private static final class PendingHit {
        private final double time;
        private final HitKind kind;
        private final int index;
        private final boolean fiery;
        private final boolean particleEligible;
        private final boolean c4BurstBonus;
        private final double flatDamageBonus;

        private PendingHit(
                double time,
                HitKind kind,
                int index,
                boolean fiery,
                boolean particleEligible,
                boolean c4BurstBonus) {
            this(
                    time,
                    kind,
                    index,
                    fiery,
                    particleEligible,
                    c4BurstBonus,
                    0.0);
        }

        private PendingHit(
                double time,
                HitKind kind,
                int index,
                boolean fiery,
                boolean particleEligible,
                boolean c4BurstBonus,
                double flatDamageBonus) {
            this.time = time;
            this.kind = kind;
            this.index = index;
            this.fiery = fiery;
            this.particleEligible = particleEligible;
            this.c4BurstBonus = c4BurstBonus;
            this.flatDamageBonus = flatDamageBonus;
        }

        private PendingHit copy() {
            return new PendingHit(
                    time,
                    kind,
                    index,
                    fiery,
                    particleEligible,
                    c4BurstBonus,
                    flatDamageBonus);
        }
    }

    /** Immutable delayed state-command payload. */
    private static final class PendingCommand {
        private final double time;
        private final CommandKind kind;
        private final long generation;
        private final double value;

        private PendingCommand(
                double time,
                CommandKind kind,
                long generation,
                double value) {
            this.time = time;
            this.kind = kind;
            this.generation = generation;
            this.value = value;
        }

        private PendingCommand copy() {
            return new PendingCommand(time, kind, generation, value);
        }
    }

    /** Immutable owner-bound snapshot of all Varesa-specific state. */
    private static final class VaresaState implements State {
        private final Varesa owner;
        private final int normalAttackStep;
        private final long nightsoulGeneration;
        private final boolean nightsoulActive;
        private final double nightsoulPoints;
        private final double nightsoulExpirationTime;
        private final boolean freeSkill;
        private final double followUpExpirationTime;
        private final double apexExpirationTime;
        private final double a1ExpirationTime;
        private final double c4PlungeExpirationTime;
        private final List<Double> a4StackExpirations;
        private final List<PendingHit> pendingHits;
        private final List<PendingCommand> pendingCommands;

        private VaresaState(
                Varesa owner,
                int normalAttackStep,
                long nightsoulGeneration,
                boolean nightsoulActive,
                double nightsoulPoints,
                double nightsoulExpirationTime,
                boolean freeSkill,
                double followUpExpirationTime,
                double apexExpirationTime,
                double a1ExpirationTime,
                double c4PlungeExpirationTime,
                List<Double> a4StackExpirations,
                List<PendingHit> pendingHits,
                List<PendingCommand> pendingCommands) {
            this.owner = owner;
            this.normalAttackStep = normalAttackStep;
            this.nightsoulGeneration = nightsoulGeneration;
            this.nightsoulActive = nightsoulActive;
            this.nightsoulPoints = nightsoulPoints;
            this.nightsoulExpirationTime = nightsoulExpirationTime;
            this.freeSkill = freeSkill;
            this.followUpExpirationTime = followUpExpirationTime;
            this.apexExpirationTime = apexExpirationTime;
            this.a1ExpirationTime = a1ExpirationTime;
            this.c4PlungeExpirationTime = c4PlungeExpirationTime;
            this.a4StackExpirations = new ArrayList<>(a4StackExpirations);
            this.pendingHits = copyHits(pendingHits);
            this.pendingCommands = copyCommands(pendingCommands);
        }
    }
}

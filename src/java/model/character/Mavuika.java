package model.character;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import mechanics.buff.ActiveCharacterBuff;
import mechanics.buff.Buff;
import mechanics.buff.BuffId;
import mechanics.data.TalentDataManager;
import mechanics.data.TalentDataSource;
import mechanics.energy.ParticleType;
import model.entity.ArtifactSet;
import model.entity.Character;
import model.entity.Enemy;
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
import simulation.action.HitlagProfile;
import simulation.action.SkillActionMode;
import simulation.event.SimpleTimerEvent;

/**
 * Mavuika's deterministic fixed-target Fighting Spirit vertical slice.
 *
 * <p>Level-90 claymore basics, fixed high Plunge, local Nightsoul drain,
 * Ring and Flamestrider offense, five particles, the source recast, Fighting
 * Spirit, Sunfell Slice, Crucible of Death and Life, A1/A4, and representable
 * offensive C1-C6 behavior follow pinned gcsim revision {@code ef41805d}.
 * Team Normal hits, team Nightsoul consumption, and Nightsoul Bursts enter
 * through explicit externally-confirmed methods rather than global hooks.</p>
 *
 * <p>Player HP, healing, damage intake, shields, defense, movement, terrain,
 * geometry, random or multi-target selection, complete hitlag coverage,
 * stamina, low Plunge, exploration, and automatic team Nightsoul plumbing
 * are excluded. The
 * Flamestrider Charged action is the source-backed fixed-target minimum cycle:
 * one startup cyclic hit followed by the earliest final hit.</p>
 */
public final class Mavuika extends Character implements
        SimulatorInitializedCharacterEffect,
        SnapshotAwareCharacterEffect,
        SwitchAwareCharacter,
        TargetDependentTeamEffect {
    private static final double FRAME = 1.0 / 60.0;
    private static final double EPSILON = 1e-9;
    private static final int[][] NORMAL_HIT_FRAMES = {
        { 21 }, { 14, 26 }, { 28, 33, 39 }, { 30 }
    };
    private static final int[] NORMAL_DURATIONS = { 31, 42, 46, 60 };
    private static final double[][] NORMAL_T9 = {
        { 1.470411 },
        { 0.670212, 0.670212 },
        { 0.610380, 0.610380, 0.610380 },
        { 2.134706 }
    };
    private static final int[] BIKE_HIT_FRAMES = { 19, 24, 31, 13, 37 };
    private static final int[] BIKE_DURATIONS = { 23, 32, 35, 27, 68 };
    private static final double[] BIKE_T9 = {
        1.052075, 1.086392, 1.285804, 1.280622, 1.671924
    };
    private static final double[] BIKE_C5 = {
        1.291788, 1.333925, 1.578772, 1.572409, 2.052869
    };

    /** Hitlag data pinned to gcsim {@code 3647a07a7cc3004bc1e79d9bb5f7444de20dceaa}. */
    private static final HitlagProfile[] NORMAL_HITLAG = {
        new HitlagProfile(0.09, 0.01, true, false, false),
        new HitlagProfile(0.05, 0.01, true, false, false),
        HitlagProfile.none(),
        new HitlagProfile(0.10, 0.01, true, false, false)
    };
    private static final HitlagProfile CHARGED_HITLAG =
            new HitlagProfile(0.15, 0.01, true, false, false);

    private CombatSimulator initializedSimulator;
    private int normalAttackStep;
    private Armament armament = Armament.RING;
    private long armamentGeneration;
    private long nightsoulGeneration;
    private boolean nightsoulBlessing;
    private double nightsoulPoints;
    private double skillRecastAllowedTime = Double.NEGATIVE_INFINITY;
    private long skillGeneration;
    private double nextParticleAllowedTime = Double.NEGATIVE_INFINITY;
    private double fightingSpirit;
    private double nextNormalSpiritAllowedTime = Double.NEGATIVE_INFINITY;
    private double a1AttackUntil = Double.NEGATIVE_INFINITY;
    private double c1AttackUntil = Double.NEGATIVE_INFINITY;
    private long burstGeneration;
    private double burstFightingSpirit;
    private double crucibleUntil = Double.NEGATIVE_INFINITY;
    private long a4Generation;
    private int a4Stacks;
    private double a4Until = Double.NEGATIVE_INFINITY;
    private double bikeChargedUntil = Double.NEGATIVE_INFINITY;
    private double nextC6RingFollowUpAllowedTime = Double.NEGATIVE_INFINITY;
    private List<PendingHit> pendingHits = new ArrayList<>();
    private List<PendingCommand> pendingCommands = new ArrayList<>();

    /** Constructs repository-default C6 Mavuika. */
    public Mavuika(Weapon weapon, ArtifactSet artifacts) {
        this(weapon, artifacts, TalentDataManager.getInstance(), 6);
    }

    /** Constructs Mavuika at an explicit constellation. */
    public Mavuika(
            Weapon weapon,
            ArtifactSet artifacts,
            int constellation) {
        this(weapon, artifacts, TalentDataManager.getInstance(),
                constellation);
    }

    /**
     * Constructs Mavuika with injectable source-backed talent data.
     *
     * @param weapon equipped claymore, or {@code null}
     * @param artifacts equipped artifact set, or {@code null}
     * @param talentData static character-data source
     * @param constellation constellation level in {@code [0, 6]}
     */
    public Mavuika(
            Weapon weapon,
            ArtifactSet artifacts,
            TalentDataSource talentData,
            int constellation) {
        super(talentData);
        if (constellation < 0 || constellation > 6) {
            throw new IllegalArgumentException(
                    "Mavuika constellation must be between 0 and 6");
        }
        name = "Mavuika";
        characterId = CharacterId.MAVUIKA;
        element = Element.PYRO;
        this.weapon = weapon;
        this.artifacts = new ArtifactSet[] { artifacts };
        this.constellation = constellation;
        baseStats.set(StatType.BASE_HP,
                getTalentValue("Base HP", 12552.0));
        baseStats.set(StatType.BASE_ATK,
                getTalentValue("Base ATK", 359.0));
        baseStats.set(StatType.BASE_DEF,
                getTalentValue("Base DEF", 792.0));
        baseStats.add(StatType.CRIT_DMG,
                getTalentValue("Ascension CRIT DMG", 0.384));
        setSkillCD(getTalentValue("Skill Cooldown", 15.0));
        setBurstCD(getTalentValue("Burst Cooldown", 18.0));
        fightingSpirit = getTalentValue(
                "Maximum Fighting Spirit", 200.0);
    }

    /** Binds Mavuika-owned state to exactly one party simulator. */
    @Override
    public void initializeForSimulator(CombatSimulator simulator) {
        if (simulator == null) {
            throw new IllegalArgumentException(
                    "Mavuika simulator is required");
        }
        if (!simulator.getPartyMembers().contains(this)) {
            throw new IllegalArgumentException(
                    "Mavuika must belong to the simulator party");
        }
        if (initializedSimulator != null
                && initializedSimulator != simulator) {
            throw new IllegalStateException(
                    "Mavuika cannot be reused across simulators");
        }
        initializedSimulator = simulator;
    }

    /** Captures resources, windows, generation gates, and delayed work. */
    @Override
    public State captureCharacterState() {
        return new MavuikaState(
                this,
                normalAttackStep,
                armament,
                armamentGeneration,
                nightsoulGeneration,
                nightsoulBlessing,
                nightsoulPoints,
                skillRecastAllowedTime,
                skillGeneration,
                nextParticleAllowedTime,
                fightingSpirit,
                nextNormalSpiritAllowedTime,
                a1AttackUntil,
                c1AttackUntil,
                burstGeneration,
                burstFightingSpirit,
                crucibleUntil,
                a4Generation,
                a4Stacks,
                a4Until,
                bikeChargedUntil,
                nextC6RingFollowUpAllowedTime,
                pendingHits,
                pendingCommands);
    }

    /** Accepts state captured by this exact Mavuika instance only. */
    @Override
    public boolean acceptsCharacterState(State state) {
        return state instanceof MavuikaState
                && ((MavuikaState) state).owner == this;
    }

    /** Restores local state and reconstructs each unresolved event once. */
    @Override
    public void restoreCharacterState(
            State state,
            CombatSimulator simulator) {
        if (!acceptsCharacterState(state)) {
            throw new IllegalArgumentException("Unexpected Mavuika state");
        }
        initializeForSimulator(simulator);
        MavuikaState restored = (MavuikaState) state;
        normalAttackStep = restored.normalAttackStep;
        armament = restored.armament;
        armamentGeneration = restored.armamentGeneration;
        nightsoulGeneration = restored.nightsoulGeneration;
        nightsoulBlessing = restored.nightsoulBlessing;
        nightsoulPoints = restored.nightsoulPoints;
        skillRecastAllowedTime = restored.skillRecastAllowedTime;
        skillGeneration = restored.skillGeneration;
        nextParticleAllowedTime = restored.nextParticleAllowedTime;
        fightingSpirit = restored.fightingSpirit;
        nextNormalSpiritAllowedTime =
                restored.nextNormalSpiritAllowedTime;
        a1AttackUntil = restored.a1AttackUntil;
        c1AttackUntil = restored.c1AttackUntil;
        burstGeneration = restored.burstGeneration;
        burstFightingSpirit = restored.burstFightingSpirit;
        crucibleUntil = restored.crucibleUntil;
        a4Generation = restored.a4Generation;
        a4Stacks = restored.a4Stacks;
        a4Until = restored.a4Until;
        bikeChargedUntil = restored.bikeChargedUntil;
        nextC6RingFollowUpAllowedTime =
                restored.nextC6RingFollowUpAllowedTime;
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

    /** Returns Mavuika's source-backed zero Energy cost. */
    @Override
    public double getEnergyCost() {
        return getTalentValue("Energy Cost", 0.0);
    }

    /** Applies live A1, C1, and C2 self stats. */
    @Override
    public void applyPassive(StatsContainer stats) {
        double currentTime = initializedSimulator == null
                ? Double.NEGATIVE_INFINITY
                : initializedSimulator.getCurrentTime();
        if (currentTime + EPSILON < a1AttackUntil) {
            stats.add(StatType.ATK_PERCENT,
                    getTalentValue("A1 ATK", 0.30));
        }
        if (constellation >= 1
                && currentTime + EPSILON < c1AttackUntil) {
            stats.add(StatType.ATK_PERCENT,
                    getTalentValue("C1 ATK", 0.40));
        }
        if (constellation >= 2 && nightsoulBlessing) {
            stats.add(StatType.BASE_ATK,
                    getTalentValue("C2 Base ATK", 200.0));
        }
    }

    /** Allows Press and Hold for Ring and Flamestrider entry. */
    @Override
    protected boolean supportsSkillActionMode(SkillActionMode mode) {
        return mode == SkillActionMode.PRESS
                || mode == SkillActionMode.HOLD;
    }

    /** Rejects Burst input below the 100 Fighting Spirit threshold. */
    @Override
    public void validateActionRequest(CharacterActionRequest request) {
        super.validateActionRequest(request);
        if (request.getKey() == CharacterActionKey.BURST
                && fightingSpirit + EPSILON
                        < getBurstFightingSpiritThreshold()) {
            throw new IllegalStateException(
                    "Mavuika requires at least 100 Fighting Spirit");
        }
    }

    /** Allows source recast input while the real Skill cooldown is active. */
    @Override
    public double getSkillCDRemaining(double currentTime) {
        if (nightsoulBlessing && !super.canSkill(currentTime)) {
            return Math.max(
                    0.0, skillRecastAllowedTime - currentTime);
        }
        return super.getSkillCDRemaining(currentTime);
    }

    /** Returns cooldown and Fighting Spirit readiness. */
    @Override
    public boolean canBurst(double currentTime) {
        return getBurstCDRemaining(currentTime) <= EPSILON
                && fightingSpirit + EPSILON
                        >= getBurstFightingSpiritThreshold();
    }

    /** Switches Flamestrider to Ring and ends Crucible on field exit. */
    @Override
    public void onSwitchOut(CombatSimulator simulator) {
        initializeForSimulator(simulator);
        normalAttackStep = 0;
        crucibleUntil = Double.NEGATIVE_INFINITY;
        if (armament == Armament.FLAMESTRIDER && nightsoulBlessing) {
            setArmament(Armament.RING,
                    simulator.getCurrentTime(), simulator);
        }
    }

    /** Resets the claymore or Flamestrider Normal string on field entry. */
    @Override
    public void onSwitchIn(CombatSimulator simulator) {
        normalAttackStep = 0;
    }

    /** Returns the local Fighting Spirit balance in {@code [0, 200]}. */
    public double getFightingSpirit() {
        return fightingSpirit;
    }

    /** Returns the source-backed 100-point Burst threshold. */
    public double getBurstFightingSpiritThreshold() {
        return getTalentValue("Burst Fighting Spirit Threshold", 100.0);
    }

    /** Returns Mavuika's local Nightsoul balance. */
    public double getNightsoulPoints() {
        return nightsoulPoints;
    }

    /** Returns whether Mavuika owns an active local Nightsoul Blessing. */
    public boolean isNightsoulBlessingActive() {
        return nightsoulBlessing;
    }

    /** Returns {@code RING} or {@code FLAMESTRIDER}. */
    public String getArmamentMode() {
        return armament.name();
    }

    /** Returns whether Crucible is active at the supplied time. */
    public boolean isCrucibleActive(double currentTime) {
        return currentTime + EPSILON < crucibleUntil;
    }

    /** Returns Fighting Spirit consumed by the current Crucible window. */
    public double getBurstFightingSpirit() {
        return burstFightingSpirit;
    }

    /** Returns current Kindled Inspiration DMG bonus for the active member. */
    public double getA4DamageBonus(double currentTime) {
        if (currentTime + EPSILON >= a4Until || a4Stacks <= 0) {
            return 0.0;
        }
        double full = burstFightingSpirit
                * getTalentValue("A4 Spirit DMG Ratio", 0.002);
        if (constellation >= 4) {
            full += getTalentValue("C4 A4 DMG Bonus", 0.10);
        }
        return full * a4Stacks / 20.0;
    }

    /** Returns the number of unresolved Mavuika damage events. */
    public int getPendingHitCount() {
        return pendingHits.size();
    }

    /**
     * Records one externally confirmed ally Normal Attack hit.
     *
     * <p>The caller owns hit classification. This method validates typed party
     * ownership and applies the source global 0.1-second Fighting Spirit gate.</p>
     *
     * @param source ally that landed the confirmed Normal hit
     * @param simulator bound simulator at the hit time
     * @return {@code true} when the hit passed the source gate
     */
    public boolean notifyExternallyConfirmedAllyNormalHit(
            CharacterId source,
            CombatSimulator simulator) {
        initializeForSimulator(simulator);
        if (!isPartySource(source, simulator)
                || source == CharacterId.MAVUIKA) {
            return false;
        }
        return acceptNormalHit(simulator.getCurrentTime());
    }

    /**
     * Records externally confirmed ally Nightsoul-point consumption.
     *
     * @param source ally whose Nightsoul points were consumed
     * @param amount actual positive points consumed after clamping
     * @param simulator bound simulator at the consumption time
     * @return {@code true} when the typed party event was accepted
     */
    public boolean notifyExternallyConfirmedNightsoulConsumption(
            CharacterId source,
            double amount,
            CombatSimulator simulator) {
        initializeForSimulator(simulator);
        if (!Double.isFinite(amount) || amount <= 0.0) {
            throw new IllegalArgumentException(
                    "Consumed Nightsoul points must be positive and finite");
        }
        if (!isPartySource(source, simulator)
                || source == CharacterId.MAVUIKA) {
            return false;
        }
        gainFightingSpirit(amount, simulator.getCurrentTime());
        return true;
    }

    /**
     * Records an externally confirmed Nightsoul Burst for A1.
     *
     * @param source typed party member associated with the confirmed event
     * @param simulator bound simulator where the event occurred
     * @return {@code true} when A1's ten-second ATK window was refreshed
     */
    public boolean notifyExternallyConfirmedNightsoulBurst(
            CharacterId source,
            CombatSimulator simulator) {
        initializeForSimulator(simulator);
        if (!isPartySource(source, simulator)) {
            return false;
        }
        a1AttackUntil = simulator.getCurrentTime()
                + getTalentValue("A1 Duration", 10.0);
        return true;
    }

    /** Reports that automatic team Normal/Nightsoul plumbing is excluded. */
    public boolean isAutomaticTeamNightsoulPlumbingRepresented() {
        return false;
    }

    /** Reports that player HP, healing, and damage intake are excluded. */
    public boolean isPlayerHpHealingDamageIntakeRepresented() {
        return false;
    }

    /** Reports that shields and defensive mechanics are excluded. */
    public boolean isShieldDefenseRepresented() {
        return false;
    }

    /** Reports that movement, terrain, and geometry are excluded. */
    public boolean isMovementTerrainGeometryRepresented() {
        return false;
    }

    /** Reports that random and multi-target behavior is excluded. */
    public boolean isRandomMultiTargetRepresented() {
        return false;
    }

    /** Reports that complete hitlag coverage and stamina are excluded. */
    public boolean isHitlagStaminaRepresented() {
        return false;
    }

    /** Reports that low Plunge and exploration state are excluded. */
    public boolean isLowPlungeExplorationRepresented() {
        return false;
    }

    /** Applies C2's live Ring-following DEF reduction at impact. */
    @Override
    public void applyTargetDependentTeamStats(
            StatsContainer stats,
            Character attacker,
            Enemy target,
            AttackAction action,
            double currentTime) {
        if (constellation < 2
                || initializedSimulator == null
                || attacker == null
                || target == null
                || action == null
                || !nightsoulBlessing
                || !initializedSimulator.getPartyMembers()
                        .contains(attacker)) {
            return;
        }
        if (armament == Armament.RING || constellation >= 6) {
            stats.add(StatType.ENEMY_DEF_REDUCTION,
                    getTalentValue("C2 Ring DEF Reduction", 0.20));
        }
    }

    /** Dispatches the bounded typed action surface. */
    @Override
    public void onAction(
            CharacterActionRequest request,
            CombatSimulator simulator) {
        validateActionRequest(request);
        initializeForSimulator(simulator);
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
                namedMoment(request.getSkillMode(), simulator);
                break;
            case BURST:
                hourOfBurningSkies(simulator);
                break;
            default:
                throw new IllegalArgumentException(
                        "Unsupported action for Mavuika: "
                                + request.getKey());
        }
    }

    private void normalAttack(CombatSimulator simulator) {
        if (armament == Armament.FLAMESTRIDER && nightsoulBlessing) {
            flamestriderNormal(simulator);
            return;
        }
        double castTime = simulator.getCurrentTime();
        int step = normalAttackStep;
        for (int hit = 0; hit < NORMAL_HIT_FRAMES[step].length; hit++) {
            queueHit(simulator, new PendingHit(
                    castTime + NORMAL_HIT_FRAMES[step][hit] * FRAME,
                    HitKind.NORMAL,
                    step,
                    hit,
                    0L));
        }
        normalAttackStep = (normalAttackStep + 1)
                % NORMAL_HIT_FRAMES.length;
        simulator.advanceTime(NORMAL_DURATIONS[step] * FRAME);
    }

    private void flamestriderNormal(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        int step = normalAttackStep % BIKE_HIT_FRAMES.length;
        queueHit(simulator, new PendingHit(
                castTime + BIKE_HIT_FRAMES[step] * FRAME,
                HitKind.FLAMESTRIDER_NORMAL,
                step,
                0,
                armamentGeneration));
        normalAttackStep = (normalAttackStep + 1)
                % BIKE_HIT_FRAMES.length;
        simulator.advanceTime(BIKE_DURATIONS[step] * FRAME);
    }

    private void chargedAttack(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        if (armament == Armament.FLAMESTRIDER && nightsoulBlessing) {
            long generation = armamentGeneration;
            bikeChargedUntil = castTime + 124.0 * FRAME;
            queueHit(simulator, new PendingHit(
                    castTime + 35.0 * FRAME,
                    HitKind.FLAMESTRIDER_CHARGED_CYCLIC,
                    0,
                    0,
                    generation));
            queueHit(simulator, new PendingHit(
                    castTime + 95.0 * FRAME,
                    HitKind.FLAMESTRIDER_CHARGED_FINAL,
                    0,
                    0,
                    generation));
            simulator.advanceTime(124.0 * FRAME);
            bikeChargedUntil = Double.NEGATIVE_INFINITY;
            return;
        }
        queueHit(simulator, new PendingHit(
                castTime + 40.0 * FRAME,
                HitKind.CHARGED,
                0,
                0,
                0L));
        simulator.advanceTime(48.0 * FRAME);
    }

    private void highPlunge(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        boolean flamestrider = armament == Armament.FLAMESTRIDER
                && nightsoulBlessing;
        queueHit(simulator, new PendingHit(
                castTime + (flamestrider ? 45.0 : 41.0) * FRAME,
                flamestrider
                        ? HitKind.FLAMESTRIDER_HIGH_PLUNGE
                        : HitKind.HIGH_PLUNGE,
                0,
                0,
                flamestrider ? armamentGeneration : 0L));
        simulator.advanceTime((flamestrider ? 80.0 : 83.0) * FRAME);
    }

    private void namedMoment(
            SkillActionMode mode,
            CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        boolean realSkillReady = super.canSkill(castTime);
        if (nightsoulBlessing && !realSkillReady) {
            if (mode != SkillActionMode.PRESS) {
                throw new IllegalArgumentException(
                        "Mavuika cannot Hold Skill while recasting");
            }
            recastArmament(simulator, castTime);
            return;
        }

        Armament selected = mode == SkillActionMode.HOLD
                ? Armament.FLAMESTRIDER : Armament.RING;
        boolean flamestriderRefresh = nightsoulBlessing
                && armament == Armament.FLAMESTRIDER;
        if (flamestriderRefresh) {
            selected = Armament.FLAMESTRIDER;
        }
        setArmament(selected, castTime, simulator);
        enterOrRegenerateNightsoul(
                maximumNightsoulPoints(), castTime, simulator);
        long generation = ++skillGeneration;
        queueHit(simulator, new PendingHit(
                castTime + getTalentValue(
                        "Skill Hit Frames", 16.0) * FRAME,
                HitKind.SKILL_INITIAL,
                0,
                0,
                generation));
        queueCommand(simulator, new PendingCommand(
                castTime + getTalentValue(
                        "Skill Cooldown Start Frames", 18.0) * FRAME,
                CommandKind.START_SKILL_COOLDOWN,
                generation));
        if (flamestriderRefresh) {
            skillRecastAllowedTime = castTime + 27.0 * FRAME;
            simulator.advanceTime(27.0 * FRAME);
        } else if (selected == Armament.FLAMESTRIDER) {
            skillRecastAllowedTime = castTime + 84.0 * FRAME;
            simulator.advanceTime(43.0 * FRAME);
        } else {
            skillRecastAllowedTime = castTime + 18.0 * FRAME;
            simulator.advanceTime(18.0 * FRAME);
        }
    }

    private void recastArmament(
            CombatSimulator simulator,
            double castTime) {
        if (!nightsoulBlessing) {
            throw new IllegalStateException(
                    "Mavuika recast requires Nightsoul Blessing");
        }
        Armament selected = armament == Armament.RING
                ? Armament.FLAMESTRIDER : Armament.RING;
        setArmament(selected, castTime, simulator);
        skillRecastAllowedTime = castTime
                + getTalentValue("Skill Recast Lock", 1.0);
        simulator.advanceTime((selected == Armament.FLAMESTRIDER
                ? 13.0 : 27.0) * FRAME);
    }

    private void hourOfBurningSkies(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        if (fightingSpirit + EPSILON
                < getBurstFightingSpiritThreshold()) {
            throw new IllegalStateException(
                    "Mavuika requires at least 100 Fighting Spirit");
        }
        burstFightingSpirit = fightingSpirit;
        fightingSpirit = 0.0;
        long generation = ++burstGeneration;
        setArmament(Armament.FLAMESTRIDER, castTime, simulator);
        markBurstCooldownUsed(
                castTime, simulator.getApplicableBuffs(this));
        queueCommand(simulator, new PendingCommand(
                castTime + getTalentValue(
                        "Burst Nightsoul Entry Frames", 87.0) * FRAME,
                CommandKind.BURST_NIGHTSOUL,
                generation));
        queueCommand(simulator, new PendingCommand(
                castTime + getTalentValue(
                        "Crucible Start Frames", 105.0) * FRAME,
                CommandKind.START_CRUCIBLE,
                generation));
        queueHit(simulator, new PendingHit(
                castTime + getTalentValue(
                        "Sunfell Slice Hit Frames", 106.0) * FRAME,
                HitKind.SUNFELL_SLICE,
                0,
                0,
                generation));
        simulator.advanceTime(116.0 * FRAME);
    }

    private void setArmament(
            Armament selected,
            double currentTime,
            CombatSimulator simulator) {
        armament = selected;
        normalAttackStep = 0;
        long generation = ++armamentGeneration;
        if (selected == Armament.RING) {
            queueCommand(simulator, new PendingCommand(
                    currentTime + getTalentValue(
                            "Ring Attack Interval", 2.0),
                    CommandKind.RING_TICK,
                    generation));
        } else if (constellation >= 6) {
            queueCommand(simulator, new PendingCommand(
                    currentTime + getTalentValue(
                            "C6 Flamestrider Ring Interval", 3.0),
                    CommandKind.C6_FLAMESTRIDER_TICK,
                    generation));
        }
    }

    private void enterOrRegenerateNightsoul(
            double points,
            double currentTime,
            CombatSimulator simulator) {
        if (nightsoulBlessing) {
            nightsoulPoints = Math.min(
                    maximumNightsoulPoints(), nightsoulPoints + points);
            return;
        }
        nightsoulBlessing = true;
        nightsoulPoints = Math.min(maximumNightsoulPoints(), points);
        long generation = ++nightsoulGeneration;
        queueCommand(simulator, new PendingCommand(
                currentTime + getTalentValue(
                        "Nightsoul Drain Interval", 0.1),
                CommandKind.NIGHTSOUL_DRAIN,
                generation));
    }

    private void drainNightsoul(
            CombatSimulator simulator,
            long generation) {
        if (!nightsoulBlessing || generation != nightsoulGeneration) {
            return;
        }
        double currentTime = simulator.getCurrentTime();
        double amount = armament == Armament.FLAMESTRIDER
                ? getTalentValue("Flamestrider Drain Per Tick", 0.9)
                : getTalentValue("Ring Drain Per Tick", 0.5);
        if (armament == Armament.FLAMESTRIDER
                && currentTime + EPSILON < bikeChargedUntil) {
            amount += getTalentValue(
                    "Flamestrider Charged Extra Drain", 0.2);
        }
        consumeLocalNightsoul(amount, currentTime, simulator);
        if (nightsoulBlessing && generation == nightsoulGeneration) {
            queueCommand(simulator, new PendingCommand(
                    currentTime + getTalentValue(
                            "Nightsoul Drain Interval", 0.1),
                    CommandKind.NIGHTSOUL_DRAIN,
                    generation));
        }
    }

    private double consumeLocalNightsoul(
            double amount,
            double currentTime,
            CombatSimulator simulator) {
        if (!nightsoulBlessing || isCrucibleActive(currentTime)) {
            return 0.0;
        }
        double consumed = Math.min(nightsoulPoints, Math.max(0.0, amount));
        nightsoulPoints -= consumed;
        if (consumed > EPSILON) {
            gainFightingSpirit(consumed, currentTime);
        }
        if (nightsoulPoints <= EPSILON) {
            exitNightsoul(simulator);
        }
        return consumed;
    }

    private void exitNightsoul(CombatSimulator simulator) {
        nightsoulBlessing = false;
        nightsoulPoints = 0.0;
        nightsoulGeneration++;
        armamentGeneration++;
        normalAttackStep = 0;
        skillRecastAllowedTime = Double.NEGATIVE_INFINITY;
        bikeChargedUntil = Double.NEGATIVE_INFINITY;
    }

    private void resolveRingTick(
            CombatSimulator simulator,
            long generation) {
        if (generation != armamentGeneration
                || armament != Armament.RING
                || !nightsoulBlessing) {
            return;
        }
        double currentTime = simulator.getCurrentTime();
        performHit(
                simulator,
                "Rings of Searing Radiance",
                skillTalentValue(
                        "Ring of Searing Radiance", 2.176, 2.56),
                Element.PYRO,
                StatType.SKILL_DMG_BONUS,
                ActionType.SKILL,
                ICDType.None,
                ICDTag.None,
                1.0,
                FlatBonusKind.NONE);
        if (constellation >= 6
                && currentTime + EPSILON
                        >= nextC6RingFollowUpAllowedTime) {
            nextC6RingFollowUpAllowedTime = currentTime
                    + getTalentValue("C6 Ring Follow-Up ICD", 0.5);
            queueHit(simulator, new PendingHit(
                    currentTime + 3.0 * FRAME,
                    HitKind.C6_RING_FOLLOW_UP,
                    0,
                    0,
                    0L));
        }
        consumeLocalNightsoul(
                getTalentValue("Ring Attack Consumption", 3.0),
                currentTime,
                simulator);
        if (nightsoulBlessing
                && generation == armamentGeneration) {
            queueCommand(simulator, new PendingCommand(
                    currentTime + getTalentValue(
                            "Ring Attack Interval", 2.0),
                    CommandKind.RING_TICK,
                    generation));
        }
    }

    private void resolveC6FlamestriderTick(
            CombatSimulator simulator,
            long generation) {
        if (constellation < 6
                || generation != armamentGeneration
                || armament != Armament.FLAMESTRIDER
                || !nightsoulBlessing) {
            return;
        }
        performHit(
                simulator,
                "Rings of Searing Radiance (C6)",
                getTalentValue("C6 Flamestrider Ring", 4.0),
                Element.PYRO,
                StatType.SKILL_DMG_BONUS,
                ActionType.SKILL,
                ICDType.None,
                ICDTag.None,
                0.0,
                FlatBonusKind.NONE);
        queueCommand(simulator, new PendingCommand(
                simulator.getCurrentTime() + getTalentValue(
                        "C6 Flamestrider Ring Interval", 3.0),
                CommandKind.C6_FLAMESTRIDER_TICK,
                generation));
    }

    private void resolveHit(
            CombatSimulator simulator,
            PendingHit hit) {
        switch (hit.kind) {
            case NORMAL:
                performHit(
                        simulator,
                        "Flames Weave Life N" + (hit.index + 1)
                                + " Hit " + (hit.subIndex + 1),
                        NORMAL_T9[hit.index][hit.subIndex],
                        Element.PHYSICAL,
                        StatType.NORMAL_ATTACK_DMG_BONUS,
                        ActionType.NORMAL,
                        ICDType.Standard,
                        ICDTag.NormalAttack,
                        0.0,
                        FlatBonusKind.NONE,
                        NORMAL_HITLAG[hit.index]);
                acceptNormalHit(simulator.getCurrentTime());
                break;
            case CHARGED:
                performHit(
                        simulator,
                        "Flames Weave Life Charged Attack",
                        getTalentValue("Charged Attack", 3.56132),
                        Element.PHYSICAL,
                        StatType.CHARGED_ATTACK_DMG_BONUS,
                        ActionType.CHARGE,
                        ICDType.Standard,
                        ICDTag.NormalAttack,
                        0.0,
                        FlatBonusKind.NONE,
                        CHARGED_HITLAG);
                break;
            case HIGH_PLUNGE:
                performHit(
                        simulator,
                        "Flames Weave Life High Plunge",
                        getTalentValue("High Plunge", 3.422517),
                        Element.PHYSICAL,
                        StatType.PLUNGING_ATTACK_DMG_BONUS,
                        ActionType.PLUNGE,
                        ICDType.None,
                        ICDTag.None,
                        0.0,
                        FlatBonusKind.NONE);
                break;
            case FLAMESTRIDER_NORMAL:
                performHit(
                        simulator,
                        "Flamestrider Normal " + (hit.index + 1),
                        flamestriderNormalValue(hit.index),
                        Element.PYRO,
                        StatType.NORMAL_ATTACK_DMG_BONUS,
                        ActionType.NORMAL,
                        ICDType.Standard,
                        ICDTag.Mavuika_Flamestrider,
                        1.0,
                        FlatBonusKind.FLAMESTRIDER_NORMAL);
                acceptNormalHit(simulator.getCurrentTime());
                consumeLocalNightsoul(
                        getTalentValue(
                                "Flamestrider Normal Consumption", 1.0),
                        simulator.getCurrentTime(),
                        simulator);
                break;
            case FLAMESTRIDER_CHARGED_CYCLIC:
                performFlamestriderCharged(
                        simulator,
                        "Flamestrider Charged Attack (Cyclic)",
                        skillTalentValue(
                                "Flamestrider Charged Cyclic",
                                1.817, 2.231));
                break;
            case FLAMESTRIDER_CHARGED_FINAL:
                performFlamestriderCharged(
                        simulator,
                        "Flamestrider Charged Attack (Final)",
                        skillTalentValue(
                                "Flamestrider Charged Final",
                                2.528, 3.104));
                break;
            case FLAMESTRIDER_HIGH_PLUNGE:
                performHit(
                        simulator,
                        "Flamestrider High Plunge",
                        skillTalentValue(
                                "Flamestrider High Plunge",
                                2.9388, 3.6084),
                        Element.PYRO,
                        StatType.PLUNGING_ATTACK_DMG_BONUS,
                        ActionType.PLUNGE,
                        ICDType.Standard,
                        ICDTag.Mavuika_Flamestrider,
                        1.0,
                        FlatBonusKind.NONE);
                break;
            case SKILL_INITIAL:
                performHit(
                        simulator,
                        "The Named Moment",
                        skillTalentValue(
                                "The Named Moment", 1.2648, 1.488),
                        Element.PYRO,
                        StatType.SKILL_DMG_BONUS,
                        ActionType.SKILL,
                        ICDType.None,
                        ICDTag.None,
                        1.0,
                        FlatBonusKind.NONE);
                queueParticles(simulator, hit.time);
                break;
            case SUNFELL_SLICE:
                if (hit.generation != burstGeneration) {
                    return;
                }
                activateA4(simulator, hit.time);
                performHit(
                        simulator,
                        "Sunfell Slice",
                        burstTalentValue(
                                "Sunfell Slice", 7.5616, 8.896),
                        Element.PYRO,
                        StatType.BURST_DMG_BONUS,
                        ActionType.BURST,
                        ICDType.None,
                        ICDTag.None,
                        1.0,
                        FlatBonusKind.SUNFELL_SLICE);
                break;
            case C6_RING_FOLLOW_UP:
                performHit(
                        simulator,
                        "Flamestrider (C6)",
                        getTalentValue("C6 Ring Follow-Up", 2.0),
                        Element.PYRO,
                        StatType.SKILL_DMG_BONUS,
                        ActionType.SKILL,
                        ICDType.None,
                        ICDTag.None,
                        0.0,
                        FlatBonusKind.NONE);
                break;
            default:
                throw new IllegalStateException(
                        "Unknown Mavuika hit " + hit.kind);
        }
    }

    private void performFlamestriderCharged(
            CombatSimulator simulator,
            String displayName,
            double multiplier) {
        performHit(
                simulator,
                displayName,
                multiplier,
                Element.PYRO,
                StatType.CHARGED_ATTACK_DMG_BONUS,
                ActionType.CHARGE,
                ICDType.Standard,
                ICDTag.Mavuika_Flamestrider,
                1.0,
                FlatBonusKind.FLAMESTRIDER_CHARGED);
    }

    private void performHit(
            CombatSimulator simulator,
            String displayName,
            double multiplier,
            Element hitElement,
            StatType bonusStat,
            ActionType actionType,
            ICDType icdType,
            ICDTag icdTag,
            double gauge,
            FlatBonusKind flatBonusKind) {
        performHit(
                simulator,
                displayName,
                multiplier,
                hitElement,
                bonusStat,
                actionType,
                icdType,
                icdTag,
                gauge,
                flatBonusKind,
                HitlagProfile.none());
    }

    private void performHit(
            CombatSimulator simulator,
            String displayName,
            double multiplier,
            Element hitElement,
            StatType bonusStat,
            ActionType actionType,
            ICDType icdType,
            ICDTag icdTag,
            double gauge,
            FlatBonusKind flatBonusKind,
            HitlagProfile hitlagProfile) {
        AttackAction action = new AttackAction(
                displayName,
                multiplier,
                hitElement,
                StatType.BASE_ATK,
                bonusStat,
                0.0,
                actionType);
        action.setICD(icdType, icdTag, gauge);
        action.setHitlagProfile(hitlagProfile);
        action.setCountsAsSkillDmg(actionType == ActionType.SKILL);
        action.setCountsAsBurstDmg(actionType == ActionType.BURST);
        double currentTime = simulator.getCurrentTime();
        double flatBonus = flatDamageBonus(
                flatBonusKind, currentTime, simulator);
        if (flatBonus > 0.0) {
            action.addBonusStat(StatType.FLAT_DMG_BONUS, flatBonus);
        }
        simulator.performActionWithoutTimeAdvance(characterId, action);
    }

    private double flatDamageBonus(
            FlatBonusKind kind,
            double currentTime,
            CombatSimulator simulator) {
        if (kind == FlatBonusKind.NONE) {
            return 0.0;
        }
        double totalAtk = captureLiveStats(currentTime, simulator)
                .getTotalAtk();
        double ratio = 0.0;
        if (kind == FlatBonusKind.FLAMESTRIDER_NORMAL) {
            if (isCrucibleActive(currentTime)) {
                ratio += burstFightingSpirit * burstTalentValue(
                        "Flamestrider Normal Spirit Ratio",
                        0.00474, 0.00582);
            }
            if (constellation >= 2) {
                ratio += getTalentValue(
                        "C2 Flamestrider Normal ATK Ratio", 0.60);
            }
        } else if (kind == FlatBonusKind.FLAMESTRIDER_CHARGED) {
            if (isCrucibleActive(currentTime)) {
                ratio += burstFightingSpirit * burstTalentValue(
                        "Flamestrider Charged Spirit Ratio",
                        0.00948, 0.01164);
            }
            if (constellation >= 2) {
                ratio += getTalentValue(
                        "C2 Flamestrider Charged ATK Ratio", 0.90);
            }
        } else if (kind == FlatBonusKind.SUNFELL_SLICE) {
            ratio += burstFightingSpirit * burstTalentValue(
                    "Sunfell Slice Spirit Ratio", 0.0272, 0.032);
            if (constellation >= 2) {
                ratio += getTalentValue(
                        "C2 Sunfell ATK Ratio", 1.20);
            }
        }
        return ratio * totalAtk;
    }

    private StatsContainer captureLiveStats(
            double currentTime,
            CombatSimulator simulator) {
        StatsContainer stats = getEffectiveStats(currentTime);
        for (Buff buff : simulator.getApplicableBuffs(this)) {
            if (!buff.isExpired(currentTime)) {
                buff.apply(stats, currentTime);
            }
        }
        return stats;
    }

    private void queueParticles(
            CombatSimulator simulator,
            double impactTime) {
        if (impactTime + EPSILON < nextParticleAllowedTime
                || simulator.getEnemy() == null) {
            return;
        }
        nextParticleAllowedTime = impactTime + 0.5;
        queueCommand(simulator, new PendingCommand(
                impactTime + getTalentValue(
                        "Particle Travel Frames", 100.0) * FRAME,
                CommandKind.PARTICLE,
                0L));
    }

    private void activateA4(
            CombatSimulator simulator,
            double currentTime) {
        a4Stacks = 20;
        a4Until = currentTime
                + getTalentValue("A4 Duration", 20.0);
        long generation = ++a4Generation;
        for (Character member : simulator.getPartyMembers()) {
            member.removeBuff(BuffId.MAVUIKA_A4_KINDLED_INSPIRATION);
            member.addBuff(new ActiveCharacterBuff(
                    "Kiongozi: Kindled Inspiration",
                    BuffId.MAVUIKA_A4_KINDLED_INSPIRATION,
                    getTalentValue("A4 Duration", 20.0),
                    currentTime,
                    simulator,
                    member,
                    stats -> stats.add(
                            StatType.DMG_BONUS_ALL,
                            getA4DamageBonus(
                                    simulator.getCurrentTime())))
                    .sourcedBy(characterId));
        }
        if (constellation < 4) {
            queueCommand(simulator, new PendingCommand(
                    currentTime + getTalentValue(
                            "A4 Decay Interval", 1.0),
                    CommandKind.A4_DECAY,
                    generation));
        }
    }

    private boolean acceptNormalHit(double currentTime) {
        if (currentTime + EPSILON < nextNormalSpiritAllowedTime) {
            return false;
        }
        nextNormalSpiritAllowedTime = currentTime
                + getTalentValue("Normal Hit Spirit Cooldown", 0.1);
        gainFightingSpirit(
                getTalentValue("Normal Hit Fighting Spirit", 1.5),
                currentTime);
        return true;
    }

    private void gainFightingSpirit(
            double amount,
            double currentTime) {
        double multiplier = constellation >= 1
                ? getTalentValue(
                        "C1 Fighting Spirit Multiplier", 1.25)
                : 1.0;
        fightingSpirit = Math.min(
                getTalentValue("Maximum Fighting Spirit", 200.0),
                fightingSpirit + amount * multiplier);
        if (constellation >= 1) {
            c1AttackUntil = currentTime
                    + getTalentValue("C1 Duration", 10.0);
        }
    }

    private boolean isPartySource(
            CharacterId source,
            CombatSimulator simulator) {
        return source != null
                && source != CharacterId.UNKNOWN
                && simulator.getCharacter(source) != null;
    }

    private double maximumNightsoulPoints() {
        return getTalentValue(
                constellation >= 1
                        ? "C1 Maximum Nightsoul Points"
                        : "Maximum Nightsoul Points",
                constellation >= 1 ? 120.0 : 80.0);
    }

    private double flamestriderNormalValue(int index) {
        String key = "Flamestrider N" + (index + 1)
                + (constellation >= 5 ? " C5" : "");
        return getTalentValue(
                key,
                constellation >= 5 ? BIKE_C5[index] : BIKE_T9[index]);
    }

    private double skillTalentValue(
            String key,
            double talentNine,
            double constellationFive) {
        return getTalentValue(
                constellation >= 5 ? key + " C5" : key,
                constellation >= 5
                        ? constellationFive : talentNine);
    }

    private double burstTalentValue(
            String key,
            double talentNine,
            double constellationThree) {
        return getTalentValue(
                constellation >= 3 ? key + " C3" : key,
                constellation >= 3
                        ? constellationThree : talentNine);
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
                case START_SKILL_COOLDOWN:
                    if (command.generation == skillGeneration) {
                        markSkillUsed(
                                activeSimulator.getCurrentTime(),
                                activeSimulator.getApplicableBuffs(this));
                    }
                    break;
                case NIGHTSOUL_DRAIN:
                    drainNightsoul(
                            activeSimulator, command.generation);
                    break;
                case RING_TICK:
                    resolveRingTick(
                            activeSimulator, command.generation);
                    break;
                case C6_FLAMESTRIDER_TICK:
                    resolveC6FlamestriderTick(
                            activeSimulator, command.generation);
                    break;
                case PARTICLE:
                    activeSimulator.getEnergyDistributor()
                            .distributeParticles(
                                    Element.PYRO,
                                    getTalentValue(
                                            "Particle Count", 5.0),
                                    ParticleType.PARTICLE);
                    break;
                case BURST_NIGHTSOUL:
                    if (command.generation == burstGeneration) {
                        enterOrRegenerateNightsoul(
                                10.0,
                                activeSimulator.getCurrentTime(),
                                activeSimulator);
                    }
                    break;
                case START_CRUCIBLE:
                    if (command.generation == burstGeneration) {
                        crucibleUntil = activeSimulator.getCurrentTime()
                                + getTalentValue(
                                        "Crucible Duration", 7.0);
                    }
                    break;
                case A4_DECAY:
                    if (command.generation == a4Generation
                            && activeSimulator.getCurrentTime() + EPSILON
                                    < a4Until
                            && a4Stacks > 0) {
                        a4Stacks--;
                        if (a4Stacks > 0) {
                            queueCommand(
                                    activeSimulator,
                                    new PendingCommand(
                                            activeSimulator.getCurrentTime()
                                                    + getTalentValue(
                                                            "A4 Decay Interval",
                                                            1.0),
                                            CommandKind.A4_DECAY,
                                            command.generation));
                        }
                    }
                    break;
                default:
                    throw new IllegalStateException(
                            "Unknown Mavuika command " + command.kind);
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

    private enum Armament {
        RING,
        FLAMESTRIDER
    }

    private enum HitKind {
        NORMAL,
        CHARGED,
        HIGH_PLUNGE,
        FLAMESTRIDER_NORMAL,
        FLAMESTRIDER_CHARGED_CYCLIC,
        FLAMESTRIDER_CHARGED_FINAL,
        FLAMESTRIDER_HIGH_PLUNGE,
        SKILL_INITIAL,
        SUNFELL_SLICE,
        C6_RING_FOLLOW_UP
    }

    private enum FlatBonusKind {
        NONE,
        FLAMESTRIDER_NORMAL,
        FLAMESTRIDER_CHARGED,
        SUNFELL_SLICE
    }

    private enum CommandKind {
        START_SKILL_COOLDOWN,
        NIGHTSOUL_DRAIN,
        RING_TICK,
        C6_FLAMESTRIDER_TICK,
        PARTICLE,
        BURST_NIGHTSOUL,
        START_CRUCIBLE,
        A4_DECAY
    }

    /** Immutable delayed Mavuika hit. */
    private static final class PendingHit {
        private final double time;
        private final HitKind kind;
        private final int index;
        private final int subIndex;
        private final long generation;

        private PendingHit(
                double time,
                HitKind kind,
                int index,
                int subIndex,
                long generation) {
            this.time = time;
            this.kind = kind;
            this.index = index;
            this.subIndex = subIndex;
            this.generation = generation;
        }

        private PendingHit copy() {
            return new PendingHit(
                    time, kind, index, subIndex, generation);
        }
    }

    /** Immutable delayed Mavuika state command. */
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

    /** Immutable owner-bound snapshot of all Mavuika-specific state. */
    private static final class MavuikaState implements State {
        private final Mavuika owner;
        private final int normalAttackStep;
        private final Armament armament;
        private final long armamentGeneration;
        private final long nightsoulGeneration;
        private final boolean nightsoulBlessing;
        private final double nightsoulPoints;
        private final double skillRecastAllowedTime;
        private final long skillGeneration;
        private final double nextParticleAllowedTime;
        private final double fightingSpirit;
        private final double nextNormalSpiritAllowedTime;
        private final double a1AttackUntil;
        private final double c1AttackUntil;
        private final long burstGeneration;
        private final double burstFightingSpirit;
        private final double crucibleUntil;
        private final long a4Generation;
        private final int a4Stacks;
        private final double a4Until;
        private final double bikeChargedUntil;
        private final double nextC6RingFollowUpAllowedTime;
        private final List<PendingHit> pendingHits;
        private final List<PendingCommand> pendingCommands;

        private MavuikaState(
                Mavuika owner,
                int normalAttackStep,
                Armament armament,
                long armamentGeneration,
                long nightsoulGeneration,
                boolean nightsoulBlessing,
                double nightsoulPoints,
                double skillRecastAllowedTime,
                long skillGeneration,
                double nextParticleAllowedTime,
                double fightingSpirit,
                double nextNormalSpiritAllowedTime,
                double a1AttackUntil,
                double c1AttackUntil,
                long burstGeneration,
                double burstFightingSpirit,
                double crucibleUntil,
                long a4Generation,
                int a4Stacks,
                double a4Until,
                double bikeChargedUntil,
                double nextC6RingFollowUpAllowedTime,
                List<PendingHit> pendingHits,
                List<PendingCommand> pendingCommands) {
            this.owner = owner;
            this.normalAttackStep = normalAttackStep;
            this.armament = armament;
            this.armamentGeneration = armamentGeneration;
            this.nightsoulGeneration = nightsoulGeneration;
            this.nightsoulBlessing = nightsoulBlessing;
            this.nightsoulPoints = nightsoulPoints;
            this.skillRecastAllowedTime = skillRecastAllowedTime;
            this.skillGeneration = skillGeneration;
            this.nextParticleAllowedTime = nextParticleAllowedTime;
            this.fightingSpirit = fightingSpirit;
            this.nextNormalSpiritAllowedTime =
                    nextNormalSpiritAllowedTime;
            this.a1AttackUntil = a1AttackUntil;
            this.c1AttackUntil = c1AttackUntil;
            this.burstGeneration = burstGeneration;
            this.burstFightingSpirit = burstFightingSpirit;
            this.crucibleUntil = crucibleUntil;
            this.a4Generation = a4Generation;
            this.a4Stacks = a4Stacks;
            this.a4Until = a4Until;
            this.bikeChargedUntil = bikeChargedUntil;
            this.nextC6RingFollowUpAllowedTime =
                    nextC6RingFollowUpAllowedTime;
            this.pendingHits = copyHits(pendingHits);
            this.pendingCommands = copyCommands(pendingCommands);
        }
    }
}

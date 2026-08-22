package model.character;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import mechanics.buff.Buff;
import mechanics.buff.BuffId;
import mechanics.buff.SimpleBuff;
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
 * Xilonen's fixed-target Source Sampler offensive and support slice.
 *
 * <p>Level-90 stats, Talent 9/12 values, frames, independent Blade Roller
 * ICD, particles, Energy timing, local Nightsoul points, Source Samplers,
 * offensive Burst branch, A1/A4, and representable C1-C6 effects follow
 * pinned gcsim {@code ef41805d}. Source Sampler resistance reduction is
 * resolved against each live hit so it never becomes part of an attacker's
 * stored snapshot.</p>
 *
 * <p>Healing and player HP, movement and climbing, geometry, multi-target and
 * random selection, Nightsoul Burst team plumbing, hitlag, stamina, low
 * Plunge, exploration, and defensive state are excluded and fail closed.
 * C2 Hydro Max-HP support and C6 healing are therefore not represented.</p>
 */
public final class Xilonen extends Character implements
        SimulatorInitializedCharacterEffect,
        SnapshotAwareCharacterEffect,
        SwitchAwareCharacter,
        TargetDependentTeamEffect {
    private static final double FRAME = 1.0 / 60.0;
    private static final double EPSILON = 1e-9;
    /** Hitlag data pinned to gcsim {@code 3647a07a7cc3004bc1e79d9bb5f7444de20dceaa}. */
    private static final HitlagProfile[][] NORMAL_HITLAG = {
        { new HitlagProfile(0.03, 0.01, true, false, false) },
        {
            new HitlagProfile(0.03, 0.01, true, false, false),
            new HitlagProfile(0.03, 0.01, true, false, false)
        },
        { new HitlagProfile(0.06, 0.01, true, false, false) }
    };
    private static final HitlagProfile[] ROLLER_HITLAG = {
        new HitlagProfile(0.03, 0.01, true, false, false),
        new HitlagProfile(0.03, 0.01, false, false, false),
        new HitlagProfile(0.03, 0.01, false, false, false),
        new HitlagProfile(0.06, 0.01, false, false, false)
    };
    private static final HitlagProfile SKILL_HITLAG =
            new HitlagProfile(0.0, 0.01, true, true, false);
    private static final int[][] NORMAL_HIT_FRAMES = {
        { 18 }, { 16, 32 }, { 22 }
    };
    private static final int[] NORMAL_DURATIONS = { 34, 57, 70 };
    private static final String[][] NORMAL_KEYS = {
        { "N1" }, { "N2-1", "N2-2" }, { "N3" }
    };
    private static final double[][] NORMAL_T9 = {
        { 0.951523 }, { 0.502914, 0.502914 }, { 1.340235 }
    };
    private static final int[] ROLLER_HIT_FRAMES = { 17, 17, 22, 32 };
    private static final int[] ROLLER_DURATIONS = { 44, 48, 50, 69 };
    private static final double[] ROLLER_T9 = {
        1.029244, 1.011342, 1.209174, 1.580506
    };

    private CombatSimulator initializedSimulator;
    private int normalAttackStep;
    private long nightsoulGeneration;
    private boolean nightsoulBlessing;
    private double nightsoulPoints;
    private double nightsoulExpirationTime = Double.NEGATIVE_INFINITY;
    private double skillRecastAllowedTime = Double.NEGATIVE_INFINITY;
    private double protectedActionUntil = Double.NEGATIVE_INFINITY;
    private double samplerExpirationTime = Double.NEGATIVE_INFINITY;
    private double c2ExpirationTime = Double.NEGATIVE_INFINITY;
    private double nextA1AllowedTime = Double.NEGATIVE_INFINITY;
    private double nextA4AllowedTime = Double.NEGATIVE_INFINITY;
    private double nextC6AllowedTime = Double.NEGATIVE_INFINITY;
    private double c6ExpirationTime = Double.NEGATIVE_INFINITY;
    private double c4ExpirationTime = Double.NEGATIVE_INFINITY;
    private final EnumMap<CharacterId, Integer> c4Stacks =
            new EnumMap<>(CharacterId.class);
    private AttackAction resolvingAction;
    private HitKind resolvingHitKind;
    private List<PendingHit> pendingHits = new ArrayList<>();
    private List<PendingCommand> pendingCommands = new ArrayList<>();

    /** Constructs repository-default C6 Xilonen. */
    public Xilonen(Weapon weapon, ArtifactSet artifacts) {
        this(weapon, artifacts, TalentDataManager.getInstance(), 6);
    }

    /** Constructs Xilonen at an explicit constellation. */
    public Xilonen(
            Weapon weapon,
            ArtifactSet artifacts,
            int constellation) {
        this(weapon, artifacts, TalentDataManager.getInstance(),
                constellation);
    }

    /**
     * Constructs Xilonen with injectable static talent data.
     *
     * @param weapon equipped sword, or {@code null}
     * @param artifacts equipped artifact set, or {@code null}
     * @param talentData static character-data source
     * @param constellation constellation level in {@code [0, 6]}
     */
    public Xilonen(
            Weapon weapon,
            ArtifactSet artifacts,
            TalentDataSource talentData,
            int constellation) {
        super(talentData);
        if (constellation < 0 || constellation > 6) {
            throw new IllegalArgumentException(
                    "Xilonen constellation must be between 0 and 6");
        }
        name = "Xilonen";
        characterId = CharacterId.XILONEN;
        element = Element.GEO;
        this.weapon = weapon;
        this.artifacts = new ArtifactSet[] { artifacts };
        this.constellation = constellation;
        baseStats.set(StatType.BASE_HP,
                getTalentValue("Base HP", 12405.0));
        baseStats.set(StatType.BASE_ATK,
                getTalentValue("Base ATK", 275.0));
        baseStats.set(StatType.BASE_DEF,
                getTalentValue("Base DEF", 930.0));
        baseStats.add(StatType.DEF_PERCENT,
                getTalentValue("Ascension DEF", 0.36));
        setSkillCD(getTalentValue("Skill Cooldown", 7.0));
        setBurstCD(getTalentValue("Burst Cooldown", 15.0));
    }

    /** Binds C2 support and post-hit C4 consumption to one simulator. */
    @Override
    public void initializeForSimulator(CombatSimulator simulator) {
        if (simulator == null) {
            throw new IllegalArgumentException("Xilonen simulator is required");
        }
        if (!simulator.getPartyMembers().contains(this)) {
            throw new IllegalArgumentException(
                    "Xilonen must belong to the simulator party");
        }
        if (initializedSimulator != null
                && initializedSimulator != simulator) {
            throw new IllegalStateException(
                    "Xilonen cannot be reused across simulators");
        }
        if (initializedSimulator == simulator) {
            return;
        }
        initializedSimulator = simulator;
        simulator.addDamageListener((actor, action, damage, time) ->
                consumeC4Stack(actor, action, damage, time));
        if (constellation >= 2) {
            simulator.applyTeamBuff(new XilonenC2Buff(
                    this,
                    "Xilonen C2 Geo Sampler",
                    BuffId.XILONEN_C2_GEO_DMG,
                    Element.GEO,
                    StatType.GEO_DMG_BONUS,
                    "C2 Geo DMG",
                    0.50).sourcedBy(characterId));
            simulator.applyTeamBuff(new XilonenC2Buff(
                    this,
                    "Xilonen C2 Pyro Sampler",
                    BuffId.XILONEN_C2_PYRO_ATK,
                    Element.PYRO,
                    StatType.ATK_PERCENT,
                    "C2 Pyro ATK",
                    0.45).sourcedBy(characterId));
            simulator.applyTeamBuff(new XilonenC2Buff(
                    this,
                    "Xilonen C2 Cryo Sampler",
                    BuffId.XILONEN_C2_CRYO_CRIT_DMG,
                    Element.CRYO,
                    StatType.CRYO_CRIT_DMG,
                    "C2 Cryo CRIT DMG",
                    0.60).sourcedBy(characterId));
        }
    }

    /** Captures Nightsoul, sampler, constellation, and delayed-work state. */
    @Override
    public State captureCharacterState() {
        return new XilonenState(
                this,
                normalAttackStep,
                nightsoulGeneration,
                nightsoulBlessing,
                nightsoulPoints,
                nightsoulExpirationTime,
                skillRecastAllowedTime,
                protectedActionUntil,
                samplerExpirationTime,
                c2ExpirationTime,
                nextA1AllowedTime,
                nextA4AllowedTime,
                nextC6AllowedTime,
                c6ExpirationTime,
                c4ExpirationTime,
                c4Stacks,
                pendingHits,
                pendingCommands);
    }

    /** Accepts state captured from this exact Xilonen instance only. */
    @Override
    public boolean acceptsCharacterState(State state) {
        return state instanceof XilonenState
                && ((XilonenState) state).owner == this;
    }

    /** Restores all surviving Xilonen-owned events exactly once. */
    @Override
    public void restoreCharacterState(
            State state,
            CombatSimulator simulator) {
        if (!acceptsCharacterState(state)) {
            throw new IllegalArgumentException("Unexpected Xilonen state");
        }
        initializeForSimulator(simulator);
        XilonenState restored = (XilonenState) state;
        normalAttackStep = restored.normalAttackStep;
        nightsoulGeneration = restored.nightsoulGeneration;
        nightsoulBlessing = restored.nightsoulBlessing;
        nightsoulPoints = restored.nightsoulPoints;
        nightsoulExpirationTime = restored.nightsoulExpirationTime;
        skillRecastAllowedTime = restored.skillRecastAllowedTime;
        protectedActionUntil = restored.protectedActionUntil;
        samplerExpirationTime = restored.samplerExpirationTime;
        c2ExpirationTime = restored.c2ExpirationTime;
        nextA1AllowedTime = restored.nextA1AllowedTime;
        nextA4AllowedTime = restored.nextA4AllowedTime;
        nextC6AllowedTime = restored.nextC6AllowedTime;
        c6ExpirationTime = restored.c6ExpirationTime;
        c4ExpirationTime = restored.c4ExpirationTime;
        c4Stacks.clear();
        c4Stacks.putAll(restored.c4Stacks);
        pendingHits = copyHits(restored.pendingHits);
        pendingCommands = copyCommands(restored.pendingCommands);
        resolvingAction = null;
        resolvingHitKind = null;
        double currentTime = simulator.getCurrentTime();
        pendingHits.removeIf(hit -> hit.time < currentTime - EPSILON);
        pendingCommands.removeIf(command ->
                command.time < currentTime - EPSILON);
        for (PendingHit hit : new ArrayList<>(pendingHits)) {
            scheduleHit(simulator, hit);
        }
        for (PendingCommand command : new ArrayList<>(pendingCommands)) {
            scheduleCommand(simulator, command);
        }
    }

    /** Returns Xilonen's 60-Energy Burst cost. */
    @Override
    public double getEnergyCost() {
        return getTalentValue("Energy Cost", 60.0);
    }

    /** Xilonen's represented passives are action-conditional. */
    @Override
    public void applyPassive(StatsContainer stats) {
    }

    /** Exits Nightsoul and resets the active Normal string on switch-out. */
    @Override
    public void onSwitchOut(CombatSimulator simulator) {
        normalAttackStep = 0;
        if (nightsoulBlessing) {
            exitNightsoul(simulator, simulator.getCurrentTime());
        }
    }

    /** Resets Xilonen's Normal string on switch-in. */
    @Override
    public void onSwitchIn(CombatSimulator simulator) {
        normalAttackStep = 0;
    }

    /** Returns the one-second recast lock while Blessing is active. */
    @Override
    public double getSkillCDRemaining(double currentTime) {
        if (nightsoulBlessing) {
            return Math.max(0.0, skillRecastAllowedTime - currentTime);
        }
        return super.getSkillCDRemaining(currentTime);
    }

    /** Returns the local Nightsoul point balance. */
    public double getNightsoulPoints() {
        return nightsoulPoints;
    }

    /** Returns whether Xilonen currently owns a local Blessing state. */
    public boolean isNightsoulBlessingActive() {
        return nightsoulBlessing;
    }

    /** Returns the source-derived converted Sampler count. */
    public int getConvertedSamplerCount() {
        return composition().convertedCount;
    }

    /** Returns whether one typed Source Sampler is live at the given time. */
    public boolean isSamplerActive(Element samplerElement, double time) {
        if (samplerElement == null) {
            return false;
        }
        Composition composition = composition();
        if (!composition.elements.contains(samplerElement)) {
            return false;
        }
        if (samplerElement == Element.GEO) {
            if (constellation >= 2 && composition.convertedCount < 3) {
                return true;
            }
            if (constellation < 2
                    && composition.convertedCount < 3
                    && nightsoulBlessing) {
                return true;
            }
        }
        return composition.convertedCount >= 2
                && time + EPSILON < samplerExpirationTime;
    }

    /** Returns one party member's remaining C4 quota. */
    public int getC4Stacks(CharacterId member, double currentTime) {
        if (member == null
                || currentTime + EPSILON >= c4ExpirationTime) {
            return 0;
        }
        Integer count = c4Stacks.get(member);
        return count == null ? 0 : count;
    }

    /** Reports that C2 Hydro Max-HP support is excluded with player HP. */
    public boolean isC2HydroHpRepresented() {
        return false;
    }

    /** Reports that Burst and C6 healing are excluded. */
    public boolean isHealingRepresented() {
        return false;
    }

    /** Reports that movement, climbing, and geometry are excluded. */
    public boolean isMovementGeometryRepresented() {
        return false;
    }

    /** Reports that multi-target and random selection are excluded. */
    public boolean isMultiTargetSelectionRepresented() {
        return false;
    }

    /** Reports that Nightsoul Burst team plumbing is excluded. */
    public boolean isNightsoulBurstTeamPlumbingRepresented() {
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

    /**
     * Applies live Sampler RES support plus C4/C6 flat damage at impact.
     *
     * <p>Sampler resistance never enters stored attack snapshots. C4 and C6
     * read Xilonen's current DEF for the exact hit being resolved.</p>
     */
    @Override
    public void applyTargetDependentTeamStats(
            StatsContainer stats,
            Character attacker,
            Enemy target,
            AttackAction action,
            double currentTime) {
        if (initializedSimulator == null
                || attacker == null
                || target == null
                || action == null
                || !initializedSimulator.getPartyMembers().contains(attacker)) {
            return;
        }
        StatType shredStat = resistanceShredStat(action.getElement());
        if (shredStat != null
                && isSamplerActive(action.getElement(), currentTime)) {
            stats.add(shredStat, samplerShred());
        }
        if (qualifiesForC4(attacker, action, currentTime)) {
            stats.add(
                    StatType.FLAT_DMG_BONUS,
                    liveDef(currentTime)
                            * getTalentValue("C4 DEF Ratio", 0.65));
        }
        if (qualifiesForC6(attacker, action, currentTime)) {
            stats.add(
                    StatType.FLAT_DMG_BONUS,
                    liveDef(currentTime)
                            * getTalentValue("C6 DEF Ratio", 3.0));
        }
    }

    /** Dispatches the bounded typed action set. */
    @Override
    public void onAction(
            CharacterActionRequest request,
            CombatSimulator simulator) {
        if (request == null) {
            throw new IllegalArgumentException("Xilonen action is required");
        }
        initializeForSimulator(simulator);
        if (request.getKey() == CharacterActionKey.SKILL
                && request.getSkillMode() != SkillActionMode.PRESS) {
            throw new IllegalArgumentException(
                    "Xilonen supports Press Skill only");
        }
        if (request.getKey() == CharacterActionKey.CHARGE
                && nightsoulBlessing) {
            throw new IllegalArgumentException(
                    "Xilonen cannot use Charged Attack during Blessing");
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
                yohualsScratch(simulator);
                break;
            case BURST:
                ocelotlicuePoint(simulator);
                break;
            default:
                throw new IllegalArgumentException(
                        "Unsupported action for Xilonen: "
                                + request.getKey());
        }
    }

    private void normalAttack(CombatSimulator simulator) {
        if (nightsoulBlessing) {
            bladeRollerAttack(simulator);
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
                    false));
        }
        normalAttackStep = (normalAttackStep + 1)
                % NORMAL_HIT_FRAMES.length;
        simulator.advanceTime(NORMAL_DURATIONS[step] * FRAME);
    }

    private void bladeRollerAttack(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        int step = normalAttackStep % ROLLER_T9.length;
        activateC6(castTime, simulator);
        protectedActionUntil = castTime + ROLLER_DURATIONS[step] * FRAME;
        queueHit(simulator, new PendingHit(
                castTime + ROLLER_HIT_FRAMES[step] * FRAME,
                HitKind.BLADE_ROLLER,
                step,
                0,
                true));
        normalAttackStep = (normalAttackStep + 1)
                % ROLLER_T9.length;
        simulator.advanceTime(ROLLER_DURATIONS[step] * FRAME);
        finishProtectedAction(simulator);
    }

    private void chargedAttack(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        queueHit(simulator, new PendingHit(
                castTime + 23.0 * FRAME,
                HitKind.CHARGED,
                0,
                0,
                false));
        simulator.advanceTime(42.0 * FRAME);
    }

    private void highPlunge(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        boolean nightsoul = nightsoulBlessing;
        if (nightsoul) {
            activateC6(castTime, simulator);
        }
        double duration = (nightsoul ? 77.0 : 75.0) * FRAME;
        protectedActionUntil = castTime + duration;
        queueHit(simulator, new PendingHit(
                castTime + 50.0 * FRAME,
                HitKind.HIGH_PLUNGE,
                0,
                0,
                nightsoul));
        simulator.advanceTime(duration);
        finishProtectedAction(simulator);
    }

    private void yohualsScratch(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        if (nightsoulBlessing) {
            exitNightsoul(simulator, castTime);
            simulator.advanceTime(FRAME);
            return;
        }
        enterNightsoul(simulator, castTime);
        queueHit(simulator, new PendingHit(
                castTime + 6.0 * FRAME,
                HitKind.SKILL,
                0,
                0,
                true));
        activateC4(simulator, castTime);
        simulator.advanceTime(20.0 * FRAME);
    }

    private void ocelotlicuePoint(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        markBurstCooldownUsed(
                castTime, simulator.getApplicableBuffs(this));
        queueCommand(simulator, new PendingCommand(
                castTime + 16.0 * FRAME,
                CommandKind.BURST_ENERGY,
                0L));
        int[] hitFrames = getConvertedSamplerCount() < 2
                ? new int[] { 96, 128, 164 }
                : new int[] { 96 };
        for (int hit = 0; hit < hitFrames.length; hit++) {
            queueHit(simulator, new PendingHit(
                    castTime + hitFrames[hit] * FRAME,
                    hit == 0 ? HitKind.BURST_INITIAL
                            : HitKind.BURST_FOLLOW_UP,
                    hit,
                    0,
                    true));
        }
        simulator.advanceTime(101.0 * FRAME);
    }

    private void enterNightsoul(
            CombatSimulator simulator,
            double currentTime) {
        nightsoulGeneration++;
        nightsoulBlessing = true;
        nightsoulPoints = getTalentValue(
                "Initial Nightsoul Points", 45.0);
        double duration = getTalentValue("Nightsoul Duration", 9.0);
        if (constellation >= 1) {
            duration *= getTalentValue(
                    "C1 Duration Multiplier", 1.45);
        }
        nightsoulExpirationTime = currentTime + duration;
        skillRecastAllowedTime = currentTime
                + getTalentValue("Skill Recast Lock", 1.0);
        protectedActionUntil = Double.NEGATIVE_INFINITY;
        long generation = nightsoulGeneration;
        queueCommand(simulator, new PendingCommand(
                currentTime + getTalentValue(
                        "Nightsoul Drain Tick", 0.1),
                CommandKind.NIGHTSOUL_DRAIN,
                generation));
        queueCommand(simulator, new PendingCommand(
                nightsoulExpirationTime,
                CommandKind.NIGHTSOUL_EXIT,
                generation));
    }

    private void exitNightsoul(
            CombatSimulator simulator,
            double currentTime) {
        if (!nightsoulBlessing) {
            return;
        }
        nightsoulBlessing = false;
        nightsoulPoints = 0.0;
        nightsoulExpirationTime = Double.NEGATIVE_INFINITY;
        skillRecastAllowedTime = Double.NEGATIVE_INFINITY;
        protectedActionUntil = Double.NEGATIVE_INFINITY;
        c6ExpirationTime = Double.NEGATIVE_INFINITY;
        normalAttackStep = 0;
        nightsoulGeneration++;
        pendingCommands.removeIf(command ->
                command.kind == CommandKind.NIGHTSOUL_DRAIN
                        || command.kind == CommandKind.NIGHTSOUL_EXIT);
        markSkillUsed(currentTime, simulator.getApplicableBuffs(this));
    }

    private void finishProtectedAction(CombatSimulator simulator) {
        protectedActionUntil = Double.NEGATIVE_INFINITY;
        if (nightsoulBlessing && nightsoulPoints <= EPSILON) {
            exitNightsoul(simulator, simulator.getCurrentTime());
        }
    }

    private void drainNightsoul(
            CombatSimulator simulator,
            long generation) {
        if (!nightsoulBlessing
                || generation != nightsoulGeneration) {
            return;
        }
        double currentTime = simulator.getCurrentTime();
        if (currentTime + EPSILON >= nightsoulExpirationTime) {
            if (currentTime + EPSILON >= protectedActionUntil) {
                exitNightsoul(simulator, currentTime);
            }
            return;
        }
        if (currentTime + EPSILON >= c6ExpirationTime) {
            double drain = getTalentValue(
                    "Nightsoul Drain Per Tick", 0.5);
            if (constellation >= 1) {
                drain *= getTalentValue(
                        "C1 Consumption Multiplier", 0.70);
            }
            nightsoulPoints = Math.max(0.0, nightsoulPoints - drain);
        }
        if (nightsoulPoints <= EPSILON) {
            if (currentTime + EPSILON >= protectedActionUntil) {
                exitNightsoul(simulator, currentTime);
            }
            return;
        }
        queueCommand(simulator, new PendingCommand(
                currentTime + getTalentValue(
                        "Nightsoul Drain Tick", 0.1),
                CommandKind.NIGHTSOUL_DRAIN,
                generation));
    }

    private void activateSamplers(
            CombatSimulator simulator,
            double currentTime) {
        nightsoulPoints = 0.0;
        double duration = getTalentValue("Sampler Duration", 15.0);
        samplerExpirationTime = currentTime + duration;
        if (constellation >= 2) {
            c2ExpirationTime = currentTime
                    + getTalentValue("C2 Duration", 15.0);
            for (Character member : simulator.getPartyMembers()) {
                if (member.getElement() == Element.ELECTRO) {
                    member.receiveFlatEnergy(getTalentValue(
                            "C2 Electro Energy", 25.0));
                    member.reduceBurstCooldown(
                            currentTime,
                            getTalentValue(
                                    "C2 Electro Burst Cooldown Reduction",
                                    6.0));
                }
            }
        }
        if (currentTime + EPSILON >= nextA4AllowedTime) {
            nextA4AllowedTime = currentTime
                    + getTalentValue("A4 Trigger Cooldown", 14.0);
            addBuff(new SimpleBuff(
                    "Xilonen Portable Armored Sheath",
                    BuffId.XILONEN_A4_DEF,
                    getTalentValue("A4 Duration", 15.0),
                    currentTime,
                    stats -> stats.add(
                            StatType.DEF_PERCENT,
                            getTalentValue("A4 DEF", 0.20))));
        }
    }

    private void activateC4(
            CombatSimulator simulator,
            double currentTime) {
        if (constellation < 4) {
            return;
        }
        c4ExpirationTime = currentTime
                + getTalentValue("C4 Duration", 15.0);
        c4Stacks.clear();
        int count = (int) getTalentValue("C4 Stack Count", 6.0);
        for (Character member : simulator.getPartyMembers()) {
            c4Stacks.put(member.getCharacterId(), count);
        }
    }

    private void activateC6(
            double currentTime,
            CombatSimulator simulator) {
        if (constellation < 6
                || !nightsoulBlessing
                || simulator.getActiveCharacter() != this
                || currentTime + EPSILON < nextC6AllowedTime) {
            return;
        }
        nextC6AllowedTime = currentTime
                + getTalentValue("C6 Trigger Cooldown", 15.0);
        double duration = getTalentValue("C6 Duration", 5.0);
        c6ExpirationTime = currentTime + duration;
        nightsoulExpirationTime += duration;
        queueCommand(simulator, new PendingCommand(
                nightsoulExpirationTime,
                CommandKind.NIGHTSOUL_EXIT,
                nightsoulGeneration));
    }

    private void resolveHit(
            CombatSimulator simulator,
            PendingHit hit) {
        switch (hit.kind) {
            case NORMAL:
                performHit(
                        simulator,
                        hit,
                        "Ehecatl's Roar "
                                + NORMAL_KEYS[hit.index][hit.subIndex],
                        getTalentValue(
                                NORMAL_KEYS[hit.index][hit.subIndex],
                                NORMAL_T9[hit.index][hit.subIndex]),
                        Element.PHYSICAL,
                        StatType.BASE_ATK,
                        StatType.NORMAL_ATTACK_DMG_BONUS,
                        ActionType.NORMAL,
                        ICDType.Standard,
                        ICDTag.NormalAttack,
                        0.0);
                break;
            case CHARGED:
                performHit(
                        simulator,
                        hit,
                        "Ehecatl's Roar Charged Attack",
                        getTalentValue("Charged Attack", 1.677960),
                        Element.PHYSICAL,
                        StatType.BASE_ATK,
                        StatType.CHARGED_ATTACK_DMG_BONUS,
                        ActionType.CHARGE,
                        ICDType.Standard,
                        ICDTag.NormalAttack,
                        0.0);
                break;
            case HIGH_PLUNGE:
                performHit(
                        simulator,
                        hit,
                        hit.nightsoul
                                ? "Ehecatl's Roar Nightsoul High Plunge"
                                : "Ehecatl's Roar High Plunge",
                        getTalentValue("High Plunge", 2.933586),
                        hit.nightsoul ? Element.GEO : Element.PHYSICAL,
                        StatType.BASE_DEF,
                        StatType.PLUNGING_ATTACK_DMG_BONUS,
                        ActionType.PLUNGE,
                        ICDType.None,
                        ICDTag.None,
                        hit.nightsoul ? 1.0 : 0.0);
                handleA1NightsoulHit(simulator, hit);
                break;
            case BLADE_ROLLER:
                performHit(
                        simulator,
                        hit,
                        "Blade Roller N" + (hit.index + 1),
                        getTalentValue(
                                "Blade Roller N" + (hit.index + 1),
                                ROLLER_T9[hit.index]),
                        Element.GEO,
                        StatType.BASE_DEF,
                        StatType.NORMAL_ATTACK_DMG_BONUS,
                        ActionType.NORMAL,
                        ICDType.Standard,
                        ICDTag.Xilonen_BladeRoller,
                        1.0);
                handleA1NightsoulHit(simulator, hit);
                break;
            case SKILL:
                performHit(
                        simulator,
                        hit,
                        "Yohual's Scratch",
                        skillValue(
                                "Yohual's Scratch",
                                3.0464,
                                3.584),
                        Element.GEO,
                        StatType.BASE_DEF,
                        StatType.SKILL_DMG_BONUS,
                        ActionType.SKILL,
                        ICDType.Standard,
                        ICDTag.ElementalSkill,
                        1.0);
                if (simulator.getEnemy() != null) {
                    queueCommand(simulator, new PendingCommand(
                            simulator.getCurrentTime()
                                    + getTalentValue(
                                            "Particle Travel Frames",
                                            100.0) * FRAME,
                            CommandKind.PARTICLE,
                            0L));
                }
                break;
            case BURST_INITIAL:
            case BURST_FOLLOW_UP:
                performHit(
                        simulator,
                        hit,
                        hit.kind == HitKind.BURST_INITIAL
                                ? "Ocelotlicue Point"
                                : "Follow-Up Beat",
                        burstValue(hit.kind == HitKind.BURST_INITIAL
                                ? "Ocelotlicue Point"
                                : "Follow-Up Beat"),
                        Element.GEO,
                        StatType.BASE_DEF,
                        StatType.BURST_DMG_BONUS,
                        ActionType.BURST,
                        ICDType.Standard,
                        ICDTag.ElementalBurst,
                        1.0);
                break;
            default:
                throw new IllegalStateException(
                        "Unknown Xilonen hit kind " + hit.kind);
        }
    }

    private void performHit(
            CombatSimulator simulator,
            PendingHit hit,
            String displayName,
            double multiplier,
            Element hitElement,
            StatType scalingStat,
            StatType bonusStat,
            ActionType actionType,
            ICDType icdType,
            ICDTag icdTag,
            double gauge) {
        AttackAction action = new AttackAction(
                displayName,
                multiplier,
                hitElement,
                scalingStat,
                bonusStat,
                0.0,
                actionType);
        action.setICD(icdType, icdTag, gauge);
        action.setHitlagProfile(hitlagProfile(hit));
        action.setCountsAsSkillDmg(actionType == ActionType.SKILL);
        action.setCountsAsBurstDmg(actionType == ActionType.BURST);
        if (hit.nightsoul
                && getConvertedSamplerCount() < 2
                && (actionType == ActionType.NORMAL
                        || actionType == ActionType.PLUNGE)) {
            action.addBonusStat(
                    StatType.DMG_BONUS_ALL,
                    getTalentValue("A1 Nightsoul DMG Bonus", 0.30));
        }
        resolvingAction = action;
        resolvingHitKind = hit.kind;
        try {
            simulator.performActionWithoutTimeAdvance(characterId, action);
        } finally {
            resolvingAction = null;
            resolvingHitKind = null;
        }
    }

    private static HitlagProfile hitlagProfile(PendingHit hit) {
        if (hit.kind == HitKind.NORMAL) {
            return NORMAL_HITLAG[hit.index][hit.subIndex];
        }
        if (hit.kind == HitKind.BLADE_ROLLER) {
            return ROLLER_HITLAG[hit.index];
        }
        if (hit.kind == HitKind.SKILL) {
            return SKILL_HITLAG;
        }
        return HitlagProfile.none();
    }

    private void handleA1NightsoulHit(
            CombatSimulator simulator,
            PendingHit hit) {
        double currentTime = simulator.getCurrentTime();
        if (!hit.nightsoul
                || !nightsoulBlessing
                || getConvertedSamplerCount() < 2
                || currentTime + EPSILON < nextA1AllowedTime) {
            return;
        }
        nextA1AllowedTime = currentTime
                + getTalentValue("A1 Point Gain Cooldown", 0.1);
        nightsoulPoints = Math.min(
                getTalentValue("Maximum Nightsoul Points", 90.0),
                nightsoulPoints
                        + getTalentValue("A1 Point Gain", 35.0));
        if (nightsoulPoints + EPSILON >= getTalentValue(
                "Maximum Nightsoul Points", 90.0)) {
            activateSamplers(simulator, currentTime);
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

    private double samplerShred() {
        return getTalentValue(
                constellation >= 3
                        ? "Source Sampler RES Shred C3"
                        : "Source Sampler RES Shred",
                constellation >= 3 ? 0.42 : 0.33);
    }

    private double burstValue(String baseKey) {
        return getTalentValue(
                constellation >= 5 ? baseKey + " C5" : baseKey,
                constellation >= 5 ? 5.6256 : 4.78176);
    }

    private boolean qualifiesForC4(
            Character attacker,
            AttackAction action,
            double currentTime) {
        if (constellation < 4
                || currentTime + EPSILON >= c4ExpirationTime
                || !action.isHitEffectTrigger()
                || action.getDamagePercent() <= 0.0
                || getC4Stacks(
                        attacker.getCharacterId(), currentTime) <= 0) {
            return false;
        }
        ActionType type = action.getActionType();
        return type == ActionType.NORMAL
                || type == ActionType.CHARGE
                || type == ActionType.PLUNGE;
    }

    private boolean qualifiesForC6(
            Character attacker,
            AttackAction action,
            double currentTime) {
        if (constellation < 6
                || attacker != this
                || action != resolvingAction
                || currentTime + EPSILON >= c6ExpirationTime) {
            return false;
        }
        return resolvingHitKind == HitKind.BLADE_ROLLER
                || (resolvingHitKind == HitKind.HIGH_PLUNGE
                        && action.getElement() == Element.GEO);
    }

    private void consumeC4Stack(
            Character attacker,
            AttackAction action,
            double damage,
            double currentTime) {
        if (damage <= 0.0
                || !qualifiesForC4(attacker, action, currentTime)) {
            return;
        }
        CharacterId id = attacker.getCharacterId();
        c4Stacks.put(id, getC4Stacks(id, currentTime) - 1);
    }

    private double liveDef(double currentTime) {
        StatsContainer stats = getEffectiveStats(currentTime);
        if (initializedSimulator != null) {
            for (Buff buff : initializedSimulator.getApplicableBuffs(this)) {
                if (!buff.isExpired(currentTime)) {
                    buff.apply(stats, currentTime);
                }
            }
        }
        return stats.getTotalDef();
    }

    private boolean isC2BuffActive(
            Element targetElement,
            double currentTime) {
        if (constellation < 2) {
            return false;
        }
        if (targetElement == Element.GEO) {
            return composition().convertedCount < 3;
        }
        return currentTime + EPSILON < c2ExpirationTime;
    }

    private Composition composition() {
        EnumSet<Element> elements = EnumSet.noneOf(Element.class);
        int convertedCount = 0;
        if (initializedSimulator == null) {
            elements.add(Element.GEO);
            return new Composition(convertedCount, elements);
        }
        for (Character member : initializedSimulator.getPartyMembers()) {
            if (member == this) {
                continue;
            }
            Element memberElement = member.getElement();
            if (memberElement == Element.PYRO
                    || memberElement == Element.HYDRO
                    || memberElement == Element.ELECTRO
                    || memberElement == Element.CRYO) {
                convertedCount++;
                elements.add(memberElement);
            } else {
                elements.add(Element.GEO);
            }
        }
        if (initializedSimulator.getPartyMembers().size() < 4) {
            elements.add(Element.GEO);
        }
        return new Composition(convertedCount, elements);
    }

    private static StatType resistanceShredStat(Element element) {
        if (element == Element.PYRO) {
            return StatType.PYRO_RES_SHRED;
        }
        if (element == Element.HYDRO) {
            return StatType.HYDRO_RES_SHRED;
        }
        if (element == Element.ELECTRO) {
            return StatType.ELECTRO_RES_SHRED;
        }
        if (element == Element.CRYO) {
            return StatType.CRYO_RES_SHRED;
        }
        if (element == Element.GEO) {
            return StatType.GEO_RES_SHRED;
        }
        return null;
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
                case NIGHTSOUL_DRAIN:
                    drainNightsoul(activeSimulator, command.generation);
                    break;
                case NIGHTSOUL_EXIT:
                    if (command.generation == nightsoulGeneration
                            && nightsoulBlessing
                            && activeSimulator.getCurrentTime() + EPSILON
                                    >= nightsoulExpirationTime
                            && activeSimulator.getCurrentTime() + EPSILON
                                    >= protectedActionUntil) {
                        exitNightsoul(
                                activeSimulator,
                                activeSimulator.getCurrentTime());
                    }
                    break;
                case PARTICLE:
                    activeSimulator.getEnergyDistributor()
                            .distributeParticles(
                                    Element.GEO,
                                    getTalentValue("Particle Count", 4.0),
                                    ParticleType.PARTICLE);
                    break;
                case BURST_ENERGY:
                    spendBurstEnergy(activeSimulator.getCurrentTime());
                    break;
                default:
                    throw new IllegalStateException(
                            "Unknown Xilonen command " + command.kind);
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
        BLADE_ROLLER,
        SKILL,
        BURST_INITIAL,
        BURST_FOLLOW_UP
    }

    private enum CommandKind {
        NIGHTSOUL_DRAIN,
        NIGHTSOUL_EXIT,
        PARTICLE,
        BURST_ENERGY
    }

    /** Immutable source-derived Sampler composition. */
    private static final class Composition {
        private final int convertedCount;
        private final Set<Element> elements;

        private Composition(
                int convertedCount,
                Set<Element> elements) {
            this.convertedCount = convertedCount;
            this.elements = EnumSet.copyOf(elements);
        }
    }

    /** Dynamic typed C2 buff restricted to one recipient element. */
    private static final class XilonenC2Buff extends Buff {
        private final Xilonen owner;
        private final Element recipientElement;
        private final StatType statType;
        private final String valueKey;
        private final double fallbackValue;

        private XilonenC2Buff(
                Xilonen owner,
                String displayName,
                BuffId id,
                Element recipientElement,
                StatType statType,
                String valueKey,
                double fallbackValue) {
            super(displayName, id);
            this.owner = owner;
            this.recipientElement = recipientElement;
            this.statType = statType;
            this.valueKey = valueKey;
            this.fallbackValue = fallbackValue;
            forElement(recipientElement);
        }

        @Override
        protected void applyStats(
                StatsContainer stats,
                double currentTime) {
            if (owner.isC2BuffActive(recipientElement, currentTime)) {
                stats.add(
                        statType,
                        owner.getTalentValue(valueKey, fallbackValue));
            }
        }
    }

    /** Immutable delayed Xilonen hit. */
    private static final class PendingHit {
        private final double time;
        private final HitKind kind;
        private final int index;
        private final int subIndex;
        private final boolean nightsoul;

        private PendingHit(
                double time,
                HitKind kind,
                int index,
                int subIndex,
                boolean nightsoul) {
            this.time = time;
            this.kind = kind;
            this.index = index;
            this.subIndex = subIndex;
            this.nightsoul = nightsoul;
        }

        private PendingHit copy() {
            return new PendingHit(
                    time, kind, index, subIndex, nightsoul);
        }
    }

    /** Immutable delayed Xilonen state command. */
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

    /** Immutable snapshot of all mutable Xilonen-owned simulator state. */
    private static final class XilonenState implements State {
        private final Xilonen owner;
        private final int normalAttackStep;
        private final long nightsoulGeneration;
        private final boolean nightsoulBlessing;
        private final double nightsoulPoints;
        private final double nightsoulExpirationTime;
        private final double skillRecastAllowedTime;
        private final double protectedActionUntil;
        private final double samplerExpirationTime;
        private final double c2ExpirationTime;
        private final double nextA1AllowedTime;
        private final double nextA4AllowedTime;
        private final double nextC6AllowedTime;
        private final double c6ExpirationTime;
        private final double c4ExpirationTime;
        private final EnumMap<CharacterId, Integer> c4Stacks;
        private final List<PendingHit> pendingHits;
        private final List<PendingCommand> pendingCommands;

        private XilonenState(
                Xilonen owner,
                int normalAttackStep,
                long nightsoulGeneration,
                boolean nightsoulBlessing,
                double nightsoulPoints,
                double nightsoulExpirationTime,
                double skillRecastAllowedTime,
                double protectedActionUntil,
                double samplerExpirationTime,
                double c2ExpirationTime,
                double nextA1AllowedTime,
                double nextA4AllowedTime,
                double nextC6AllowedTime,
                double c6ExpirationTime,
                double c4ExpirationTime,
                Map<CharacterId, Integer> c4Stacks,
                List<PendingHit> pendingHits,
                List<PendingCommand> pendingCommands) {
            this.owner = owner;
            this.normalAttackStep = normalAttackStep;
            this.nightsoulGeneration = nightsoulGeneration;
            this.nightsoulBlessing = nightsoulBlessing;
            this.nightsoulPoints = nightsoulPoints;
            this.nightsoulExpirationTime = nightsoulExpirationTime;
            this.skillRecastAllowedTime = skillRecastAllowedTime;
            this.protectedActionUntil = protectedActionUntil;
            this.samplerExpirationTime = samplerExpirationTime;
            this.c2ExpirationTime = c2ExpirationTime;
            this.nextA1AllowedTime = nextA1AllowedTime;
            this.nextA4AllowedTime = nextA4AllowedTime;
            this.nextC6AllowedTime = nextC6AllowedTime;
            this.c6ExpirationTime = c6ExpirationTime;
            this.c4ExpirationTime = c4ExpirationTime;
            this.c4Stacks = new EnumMap<>(CharacterId.class);
            this.c4Stacks.putAll(c4Stacks);
            this.pendingHits = copyHits(pendingHits);
            this.pendingCommands = copyCommands(pendingCommands);
        }
    }
}

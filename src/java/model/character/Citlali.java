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
 * Citlali's stationary fixed-target Itzpapa offensive and support slice.
 *
 * <p>Three Cryo catalyst Normals, Charged and repository-policy High Plunge,
 * Skill initial damage and particles, Itzpapa's Opal Fire cadence, Burst and
 * one fixed-target Spiritvessel Skull, A1/A4, and offensive C1-C6 behavior
 * follow pinned gcsim {@code ef41805d}. Nightsoul points exist only as the
 * character-owned resource needed to gate Itzpapa and C6.</p>
 *
 * <p>Shield durability and absorption, team Nightsoul Burst plumbing,
 * movement and geometry, multi-target and random targeting, hitlag extension, stamina,
 * Low Plunge, and defensive state are excluded rather than approximated.
 * Consequently, C2 ally Elemental Mastery uses the represented Blessing
 * condition only and does not synthesize the alternative shield condition.
 * Queued attacks capture release-stage stats; live target-dependent C1/C2/C6
 * support remains impact-time state.</p>
 */
public final class Citlali extends Character implements
        ReactionAwareCharacter,
        SimulatorInitializedCharacterEffect,
        SnapshotAwareCharacterEffect,
        SwitchAwareCharacter,
        TargetDependentTeamEffect {
    private static final double FRAME = 1.0 / 60.0;
    private static final double EPSILON = 1e-9;
    private static final int[] NORMAL_IMPACT_FRAMES = { 26, 26, 46 };
    private static final int[] NORMAL_DURATION_FRAMES = { 38, 39, 52 };
    private static final double[] NORMAL_T9 = {
        0.737922, 0.659831, 0.914110
    };

    /**
     * Catalyst hitlag semantics from gcsim pinned at
     * {@code 3647a07a7cc3004bc1e79d9bb5f7444de20dceaa}.
     */
    private static final HitlagProfile CATALYST_HITLAG =
            new HitlagProfile(0.0, 0.05, false, true, false);

    private CombatSimulator initializedSimulator;
    private int normalAttackStep;
    private long nightsoulGeneration;
    private long opalFireGeneration;
    private boolean nightsoulActive;
    private boolean opalFireActive;
    private double nightsoulExpirationTime = Double.NEGATIVE_INFINITY;
    private double nightsoulPoints;
    private int stellarBladeCount;
    private double c6PointCount;
    private double nextA1PointTime = Double.NEGATIVE_INFINITY;
    private double nextParticleTime = Double.NEGATIVE_INFINITY;
    private double nextC4Time = Double.NEGATIVE_INFINITY;
    private AttackAction c1ResolvingAction;
    private AttackAction resolvingAction;
    private HitKind resolvingHitKind;
    private List<PendingHit> pendingHits = new ArrayList<>();
    private List<PendingCommand> pendingCommands = new ArrayList<>();

    /** Constructs repository-default C6 Citlali. */
    public Citlali(Weapon weapon, ArtifactSet artifacts) {
        this(weapon, artifacts, TalentDataManager.getInstance(), 6);
    }

    /** Constructs Citlali at an explicit constellation. */
    public Citlali(
            Weapon weapon,
            ArtifactSet artifacts,
            int constellation) {
        this(weapon, artifacts, TalentDataManager.getInstance(), constellation);
    }

    /**
     * Constructs Citlali with injectable static talent data.
     *
     * @param weapon equipped catalyst, or {@code null}
     * @param artifacts equipped artifact set, or {@code null}
     * @param talentData static character-data source
     * @param constellation constellation level in {@code [0, 6]}
     */
    public Citlali(
            Weapon weapon,
            ArtifactSet artifacts,
            TalentDataSource talentData,
            int constellation) {
        super(talentData);
        if (constellation < 0 || constellation > 6) {
            throw new IllegalArgumentException(
                    "Citlali constellation must be between 0 and 6");
        }
        name = "Citlali";
        characterId = CharacterId.CITLALI;
        element = Element.CRYO;
        this.weapon = weapon;
        this.artifacts = new ArtifactSet[] { artifacts };
        this.constellation = constellation;
        baseStats.set(StatType.BASE_HP,
                getTalentValue("Base HP", 11634.0));
        baseStats.set(StatType.BASE_ATK,
                getTalentValue("Base ATK", 127.0));
        baseStats.set(StatType.BASE_DEF,
                getTalentValue("Base DEF", 763.0));
        baseStats.add(StatType.ELEMENTAL_MASTERY,
                getTalentValue("Ascension Elemental Mastery", 115.2));
        setSkillCD(getTalentValue("Skill Cooldown", 16.0));
        setBurstCD(getTalentValue("Burst Cooldown", 15.0));
    }

    /** Binds reaction and direct-damage observation to one owning simulator. */
    @Override
    public void initializeForSimulator(CombatSimulator simulator) {
        if (simulator == null) {
            throw new IllegalArgumentException("Citlali simulator is required");
        }
        if (!simulator.getPartyMembers().contains(this)) {
            throw new IllegalArgumentException(
                    "Citlali must belong to the simulator party");
        }
        if (initializedSimulator != null
                && initializedSimulator != simulator) {
            throw new IllegalStateException(
                    "Citlali cannot be reused across simulators");
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

    /** Captures local points, gates, generations, and reconstructable work. */
    @Override
    public State captureCharacterState() {
        return new CitlaliState(
                this,
                normalAttackStep,
                nightsoulGeneration,
                opalFireGeneration,
                nightsoulActive,
                opalFireActive,
                nightsoulExpirationTime,
                nightsoulPoints,
                stellarBladeCount,
                c6PointCount,
                nextA1PointTime,
                nextParticleTime,
                nextC4Time,
                pendingHits,
                pendingCommands);
    }

    /** Accepts state captured from this exact Citlali instance only. */
    @Override
    public boolean acceptsCharacterState(State state) {
        return state instanceof CitlaliState
                && ((CitlaliState) state).owner == this;
    }

    /** Restores Citlali-owned future attacks and commands exactly once. */
    @Override
    public void restoreCharacterState(
            State state,
            CombatSimulator simulator) {
        if (!acceptsCharacterState(state)) {
            throw new IllegalArgumentException("Unexpected Citlali state");
        }
        initializeForSimulator(simulator);
        CitlaliState restored = (CitlaliState) state;
        normalAttackStep = restored.normalAttackStep;
        nightsoulGeneration = restored.nightsoulGeneration;
        opalFireGeneration = restored.opalFireGeneration;
        nightsoulActive = restored.nightsoulActive;
        opalFireActive = restored.opalFireActive;
        nightsoulExpirationTime = restored.nightsoulExpirationTime;
        nightsoulPoints = restored.nightsoulPoints;
        stellarBladeCount = restored.stellarBladeCount;
        c6PointCount = restored.c6PointCount;
        nextA1PointTime = restored.nextA1PointTime;
        nextParticleTime = restored.nextParticleTime;
        nextC4Time = restored.nextC4Time;
        pendingHits = copyHits(restored.pendingHits);
        pendingCommands = copyCommands(restored.pendingCommands);
        c1ResolvingAction = null;
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

    /** Returns Citlali's 60-Energy Burst cost. */
    @Override
    public double getEnergyCost() {
        return getTalentValue("Energy Cost", 60.0);
    }

    /** Applies C2's permanent owner Elemental Mastery. */
    @Override
    public void applyPassive(StatsContainer stats) {
        if (constellation >= 2) {
            stats.add(StatType.ELEMENTAL_MASTERY,
                    getTalentValue("C2 Self Elemental Mastery", 125.0));
        }
    }

    /** Resets the catalyst Normal string on switch-out. */
    @Override
    public void onSwitchOut(CombatSimulator simulator) {
        normalAttackStep = 0;
    }

    /** Resets the catalyst Normal string on switch-in. */
    @Override
    public void onSwitchIn(CombatSimulator simulator) {
        normalAttackStep = 0;
    }

    /** Returns Citlali's locally represented Nightsoul point count. */
    public double getNightsoulPoints() {
        return nightsoulPoints;
    }

    /** Returns whether the local Nightsoul Blessing window is active. */
    public boolean isNightsoulActive(double currentTime) {
        return nightsoulActive
                && currentTime + EPSILON < nightsoulExpirationTime;
    }

    /** Returns whether Itzpapa is currently attacking in Opal Fire. */
    public boolean isOpalFireActive() {
        return opalFireActive;
    }

    /** Returns the current local Nightsoul expiry timestamp. */
    public double getNightsoulExpirationTime() {
        return nightsoulExpirationTime;
    }

    /** Returns unconsumed C1 Stellar Blade stacks. */
    public int getStellarBladeCount() {
        return stellarBladeCount;
    }

    /** Returns C6 Cifra points in {@code [0, 40]}. */
    public double getC6PointCount() {
        return c6PointCount;
    }

    /** Returns the number of unresolved Citlali-owned attacks. */
    public int getPendingHitCount() {
        return pendingHits.size();
    }

    /** Reports the explicit shield durability and absorption exclusion. */
    public boolean isShieldStateRepresented() {
        return false;
    }

    /** Reports the explicit team Nightsoul Burst plumbing exclusion. */
    public boolean isTeamNightsoulBurstRepresented() {
        return false;
    }

    /** Reports the explicit movement, geometry, and multi-target exclusion. */
    public boolean isGeometryRepresented() {
        return false;
    }

    /** Dispatches Citlali's represented typed action set. */
    @Override
    public void onAction(
            CharacterActionRequest request,
            CombatSimulator simulator) {
        if (request == null) {
            throw new IllegalArgumentException("Citlali action is required");
        }
        initializeForSimulator(simulator);
        if (request.getKey() == CharacterActionKey.SKILL
                && request.getSkillMode() != SkillActionMode.PRESS) {
            throw new IllegalArgumentException(
                    "Citlali supports Press Skill only");
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
                dawnfrostDarkstar(simulator);
                break;
            case BURST:
                edictOfEntwinedSplendor(simulator);
                break;
            default:
                throw new IllegalArgumentException(
                        "Unsupported action for Citlali: " + request.getKey());
        }
    }

    /** Applies A1 after any represented Melt or Frozen reaction during Blessing. */
    @Override
    public void onReaction(
            ReactionResult result,
            Character source,
            double time,
            CombatSimulator simulator) {
        if (simulator != initializedSimulator
                || result == null
                || !isNightsoulActive(time)
                || (result.getKind() != ReactionResult.Kind.MELT
                        && result.getKind() != ReactionResult.Kind.FROZEN)) {
            return;
        }
        if (time + EPSILON >= nextA1PointTime) {
            nextA1PointTime = time
                    + getTalentValue("A1 Nightsoul ICD", 8.0);
            generateNightsoulPoints(
                    simulator,
                    getTalentValue("A1 Nightsoul Points", 16.0));
            if (constellation >= 1) {
                stellarBladeCount += (int) getTalentValue(
                        "C1 A1 Stellar Blades", 3.0);
            }
        }
        double shred = getTalentValue(
                constellation >= 2
                        ? "A1 C2 RES Shred" : "A1 RES Shred",
                constellation >= 2 ? 0.40 : 0.20);
        simulator.applyTeamBuffNoStack(new SimpleBuff(
                "Citlali Mamaloaco's Frigid Rain",
                BuffId.CITLALI_A1_PYRO_HYDRO_RES_SHRED,
                getTalentValue("A1 Duration", 12.0),
                time,
                stats -> {
                    stats.add(StatType.PYRO_RES_SHRED, shred);
                    stats.add(StatType.HYDRO_RES_SHRED, shred);
                }).sourcedBy(characterId));
    }

    /** Adds live C1, C2, and C6 values to eligible direct fixed-target hits. */
    @Override
    public void applyTargetDependentTeamStats(
            StatsContainer stats,
            Character attacker,
            model.entity.Enemy target,
            AttackAction action,
            double currentTime) {
        if (initializedSimulator == null
                || stats == null
                || attacker == null
                || target == null
                || action == null
                || !initializedSimulator.getPartyMembers().contains(attacker)) {
            return;
        }
        boolean activeAttacker = attacker
                == initializedSimulator.getActiveCharacter();
        if (constellation >= 2
                && attacker != this
                && activeAttacker
                && isNightsoulActive(currentTime)) {
            stats.add(StatType.ELEMENTAL_MASTERY,
                    getTalentValue(
                            "C2 Active Elemental Mastery", 250.0));
        }
        if (constellation >= 6) {
            if (attacker == this) {
                stats.add(StatType.DMG_BONUS_ALL,
                        c6PointCount * getTalentValue(
                                "C6 Self DMG Per Point", 0.025));
            } else {
                double bonus = c6PointCount * getTalentValue(
                        "C6 Pyro Hydro DMG Per Point", 0.015);
                stats.add(StatType.PYRO_DMG_BONUS, bonus);
                stats.add(StatType.HYDRO_DMG_BONUS, bonus);
            }
        }
        if (constellation >= 1
                && attacker != this
                && activeAttacker
                && stellarBladeCount > 0
                && isC1Eligible(action)) {
            double citlaliEm = captureLiveStats(currentTime).get(
                    StatType.ELEMENTAL_MASTERY);
            stats.add(StatType.FLAT_DMG_BONUS,
                    citlaliEm * getTalentValue("C1 EM Ratio", 2.0));
            c1ResolvingAction = action;
        }
    }

    private void normalAttack(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        int step = normalAttackStep;
        queueHit(simulator, new PendingHit(
                castTime + NORMAL_IMPACT_FRAMES[step] * FRAME,
                HitKind.NORMAL,
                step,
                captureLiveStats(castTime),
                0.0));
        normalAttackStep = (normalAttackStep + 1) % NORMAL_T9.length;
        simulator.advanceTime(NORMAL_DURATION_FRAMES[step] * FRAME);
    }

    private void chargedAttack(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        queueHit(simulator, new PendingHit(
                castTime + 60.0 * FRAME,
                HitKind.CHARGED,
                0,
                captureLiveStats(castTime),
                0.0));
        simulator.advanceTime(56.0 * FRAME);
    }

    private void highPlunge(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        queueHit(simulator, new PendingHit(
                castTime,
                HitKind.HIGH_PLUNGE,
                0,
                captureLiveStats(castTime),
                0.0));
        simulator.advanceTime(1.0);
    }

    private void dawnfrostDarkstar(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        if (constellation >= 1) {
            stellarBladeCount = (int) getTalentValue(
                    "C1 Initial Stellar Blades", 10.0);
        }
        if (constellation >= 6) {
            c6PointCount = Math.min(
                    getTalentValue("C6 Maximum Points", 40.0),
                    nightsoulPoints);
            nightsoulPoints = 0.0;
        }
        StatsContainer snapshot = captureLiveStats(castTime);
        queueCommand(simulator, new PendingCommand(
                castTime + 18.0 * FRAME,
                CommandKind.SKILL_ACTIVATE,
                0L));
        queueHit(simulator, new PendingHit(
                castTime + 20.0 * FRAME,
                HitKind.SKILL_INITIAL,
                0,
                snapshot,
                0.0));
        simulator.advanceTime(50.0 * FRAME);
    }

    private void edictOfEntwinedSplendor(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        markBurstCooldownUsed(castTime, simulator.getApplicableBuffs(this));
        StatsContainer snapshot = captureLiveStats(castTime);
        double a4Flat = snapshot.get(StatType.ELEMENTAL_MASTERY)
                * getTalentValue("A4 Ice Storm EM Ratio", 12.0);
        queueCommand(simulator, new PendingCommand(
                castTime + 8.0 * FRAME,
                CommandKind.BURST_ENERGY,
                0L));
        queueCommand(simulator, new PendingCommand(
                castTime + 115.0 * FRAME,
                CommandKind.BURST_POINTS,
                0L));
        queueHit(simulator, new PendingHit(
                castTime + 118.0 * FRAME,
                HitKind.BURST_INITIAL,
                0,
                snapshot,
                a4Flat));
        queueHit(simulator, new PendingHit(
                castTime + 210.0 * FRAME,
                HitKind.BURST_SKULL,
                0,
                snapshot,
                0.0));
        simulator.advanceTime(113.0 * FRAME);
    }

    private void activateSkillState(CombatSimulator simulator) {
        double currentTime = simulator.getCurrentTime();
        markSkillUsed(currentTime, simulator.getApplicableBuffs(this));
        if (!isNightsoulActive(currentTime)) {
            nightsoulGeneration++;
            nightsoulActive = true;
        }
        nightsoulExpirationTime = currentTime
                + getTalentValue("Nightsoul Duration", 20.0);
        generateNightsoulPoints(
                simulator,
                getTalentValue("Nightsoul Skill Points", 24.0));
        queueCommand(simulator, new PendingCommand(
                nightsoulExpirationTime,
                CommandKind.NIGHTSOUL_EXPIRE,
                nightsoulGeneration));
    }

    private void generateNightsoulPoints(
            CombatSimulator simulator,
            double amount) {
        nightsoulPoints = Math.min(
                getTalentValue("Nightsoul Maximum", 100.0),
                nightsoulPoints + Math.max(0.0, amount));
        if (isNightsoulActive(simulator.getCurrentTime())
                && !opalFireActive
                && (constellation >= 6
                        || nightsoulPoints + EPSILON >= getTalentValue(
                                "Opal Fire Threshold", 50.0))) {
            activateOpalFire(simulator);
        }
    }

    private void activateOpalFire(CombatSimulator simulator) {
        opalFireActive = true;
        long generation = ++opalFireGeneration;
        double currentTime = simulator.getCurrentTime();
        queueCommand(simulator, new PendingCommand(
                currentTime + getTalentValue(
                        "Opal Fire Attack Frames", 59.0) * FRAME,
                CommandKind.OPAL_FIRE_HIT,
                generation));
        queueCommand(simulator, new PendingCommand(
                currentTime + getTalentValue(
                        "Opal Fire Drain Interval", 0.1),
                CommandKind.OPAL_FIRE_DRAIN,
                generation));
    }

    private void emitOpalFireHit(
            CombatSimulator simulator,
            long generation) {
        double currentTime = simulator.getCurrentTime();
        if (generation != opalFireGeneration
                || !opalFireActive
                || !isNightsoulActive(currentTime)) {
            return;
        }
        StatsContainer snapshot = captureLiveStats(currentTime);
        double a4Flat = snapshot.get(StatType.ELEMENTAL_MASTERY)
                * getTalentValue("A4 Frostfall EM Ratio", 0.9);
        resolveHit(simulator, new PendingHit(
                currentTime,
                HitKind.FROSTFALL,
                0,
                snapshot,
                a4Flat));
        queueCommand(simulator, new PendingCommand(
                currentTime + getTalentValue(
                        "Opal Fire Attack Frames", 59.0) * FRAME,
                CommandKind.OPAL_FIRE_HIT,
                generation));
    }

    private void drainOpalFire(
            CombatSimulator simulator,
            long generation) {
        double currentTime = simulator.getCurrentTime();
        if (generation != opalFireGeneration
                || !opalFireActive
                || !isNightsoulActive(currentTime)) {
            return;
        }
        double consumed = Math.min(
                nightsoulPoints,
                getTalentValue("Opal Fire Drain Amount", 0.8));
        nightsoulPoints -= consumed;
        if (constellation >= 6) {
            c6PointCount = Math.min(
                    getTalentValue("C6 Maximum Points", 40.0),
                    c6PointCount + consumed);
        } else if (nightsoulPoints < 0.001) {
            nightsoulPoints = 0.0;
            opalFireActive = false;
            opalFireGeneration++;
            return;
        }
        queueCommand(simulator, new PendingCommand(
                currentTime + getTalentValue(
                        "Opal Fire Drain Interval", 0.1),
                CommandKind.OPAL_FIRE_DRAIN,
                generation));
    }

    private void expireNightsoul(
            CombatSimulator simulator,
            long generation) {
        if (generation != nightsoulGeneration
                || simulator.getCurrentTime() + EPSILON
                        < nightsoulExpirationTime) {
            return;
        }
        nightsoulActive = false;
        opalFireActive = false;
        nightsoulPoints = 0.0;
        stellarBladeCount = 0;
        c6PointCount = 0.0;
        nightsoulGeneration++;
        opalFireGeneration++;
    }

    private void handleAcceptedDamage(
            CombatSimulator simulator,
            Character actor,
            AttackAction action,
            double damage,
            double time) {
        if (action == c1ResolvingAction) {
            if (damage > 0.0 && stellarBladeCount > 0) {
                stellarBladeCount--;
            }
            c1ResolvingAction = null;
        }
        if (actor != this || action != resolvingAction || damage <= 0.0) {
            return;
        }
        if (resolvingHitKind == HitKind.SKILL_INITIAL
                && time + EPSILON >= nextParticleTime) {
            nextParticleTime = time
                    + getTalentValue("Particle ICD", 0.3);
            queueCommand(simulator, new PendingCommand(
                    time + getTalentValue(
                            "Particle Travel Frames", 100.0) * FRAME,
                    CommandKind.PARTICLE,
                    0L));
        }
        if (resolvingHitKind == HitKind.FROSTFALL
                && constellation >= 4
                && time + EPSILON >= nextC4Time) {
            nextC4Time = time + getTalentValue("C4 Cooldown", 8.0);
            generateNightsoulPoints(
                    simulator,
                    getTalentValue("C4 Nightsoul Points", 16.0));
            receiveFlatEnergy(getTalentValue("C4 Energy", 8.0));
            StatsContainer snapshot = captureLiveStats(time);
            double flatDamage = snapshot.get(StatType.ELEMENTAL_MASTERY)
                    * getTalentValue("C4 Skull EM Ratio", 18.0);
            queueHit(simulator, new PendingHit(
                    time + getTalentValue(
                            "C4 Skull Delay Frames", 92.0) * FRAME,
                    HitKind.C4_SKULL,
                    0,
                    snapshot,
                    flatDamage));
        }
    }

    private boolean isC1Eligible(AttackAction action) {
        switch (action.getActionType()) {
            case NORMAL:
            case CHARGE:
            case PLUNGE:
            case SKILL:
            case BURST:
                return true;
            default:
                return false;
        }
    }

    private void resolveHit(
            CombatSimulator simulator,
            PendingHit hit) {
        if (hit.kind == HitKind.BURST_SKULL) {
            generateNightsoulPoints(
                    simulator,
                    getTalentValue("Burst Skull Nightsoul Points", 3.0));
        }
        switch (hit.kind) {
            case NORMAL:
                performHit(simulator, hit,
                        "Shadow-Stealing Spirit Vessel N" + (hit.index + 1),
                        getTalentValue(
                                "N" + (hit.index + 1),
                                NORMAL_T9[hit.index]),
                        StatType.NORMAL_ATTACK_DMG_BONUS,
                        ActionType.NORMAL,
                        ICDType.Standard,
                        ICDTag.NormalAttack,
                        1.0);
                break;
            case CHARGED:
                performHit(simulator, hit,
                        "Shadow-Stealing Spirit Vessel Charged Attack",
                        getTalentValue("Charged Attack", 1.6864),
                        StatType.CHARGED_ATTACK_DMG_BONUS,
                        ActionType.CHARGE,
                        ICDType.None,
                        ICDTag.ChargedAttack,
                        1.0);
                break;
            case HIGH_PLUNGE:
                performHit(simulator, hit,
                        "Shadow-Stealing Spirit Vessel High Plunge",
                        getTalentValue("High Plunge", 2.607632),
                        StatType.PLUNGING_ATTACK_DMG_BONUS,
                        ActionType.PLUNGE,
                        ICDType.None,
                        ICDTag.PlungeAttack,
                        1.0);
                break;
            case SKILL_INITIAL:
                performHit(simulator, hit,
                        "Dawnfrost Darkstar: Obsidian Tzitzimitl",
                        skillValue(
                                "Obsidian Tzitzimitl",
                                "Obsidian Tzitzimitl C3",
                                1.24032,
                                1.4592),
                        StatType.SKILL_DMG_BONUS,
                        ActionType.SKILL,
                        ICDType.None,
                        ICDTag.ElementalSkill,
                        1.0);
                break;
            case FROSTFALL:
                performHit(simulator, hit,
                        "Frostfall Storm",
                        skillValue(
                                "Frostfall Storm",
                                "Frostfall Storm C3",
                                0.289408,
                                0.34048),
                        StatType.SKILL_DMG_BONUS,
                        ActionType.SKILL,
                        ICDType.CitlaliFrostfallStorm,
                        ICDTag.Citlali_FrostfallStorm,
                        1.0);
                break;
            case BURST_INITIAL:
                performHit(simulator, hit,
                        "Edict of Entwined Splendor: Ice Storm",
                        burstValue("Ice Storm", "Ice Storm C5", 9.1392, 10.752),
                        StatType.BURST_DMG_BONUS,
                        ActionType.BURST,
                        ICDType.None,
                        ICDTag.ElementalBurst,
                        2.0);
                break;
            case BURST_SKULL:
                performHit(simulator, hit,
                        "Spiritvessel Skull",
                        burstValue(
                                "Spiritvessel Skull",
                                "Spiritvessel Skull C5",
                                2.2848,
                                2.688),
                        StatType.BURST_DMG_BONUS,
                        ActionType.BURST,
                        ICDType.Standard,
                        ICDTag.Citlali_SpiritVessel,
                        1.0);
                break;
            case C4_SKULL:
                performHit(simulator, hit,
                        "Spiritvessel Skull C4",
                        0.0,
                        null,
                        ActionType.OTHER,
                        ICDType.None,
                        ICDTag.None,
                        1.0);
                break;
            default:
                throw new IllegalStateException(
                        "Unknown Citlali hit kind " + hit.kind);
        }
    }

    private void performHit(
            CombatSimulator simulator,
            PendingHit hit,
            String displayName,
            double multiplier,
            StatType bonusStat,
            ActionType actionType,
            ICDType icdType,
            ICDTag icdTag,
            double gauge) {
        AttackAction action = new AttackAction(
                displayName,
                multiplier,
                Element.CRYO,
                StatType.BASE_ATK,
                bonusStat,
                0.0,
                actionType);
        action.setICD(icdType, icdTag, gauge);
        action.setCountsAsSkillDmg(actionType == ActionType.SKILL);
        action.setCountsAsBurstDmg(actionType == ActionType.BURST);
        if (hit.kind == HitKind.NORMAL || hit.kind == HitKind.CHARGED) {
            action.setHitlagProfile(CATALYST_HITLAG);
        }
        StatsContainer snapshot = hit.snapshot == null
                ? captureLiveStats(simulator.getCurrentTime())
                : hit.snapshot.merge(null);
        if (hit.additiveBaseDamage > 0.0) {
            snapshot.add(StatType.FLAT_DMG_BONUS,
                    hit.additiveBaseDamage);
            action.setHitEffectTrigger(true);
        }
        action.setStatSnapshot(snapshot);
        resolvingAction = action;
        resolvingHitKind = hit.kind;
        try {
            simulator.performActionWithoutTimeAdvance(characterId, action);
        } finally {
            resolvingAction = null;
            resolvingHitKind = null;
        }
    }

    private double skillValue(
            String baseKey,
            String c3Key,
            double talentNine,
            double talentTwelve) {
        return getTalentValue(
                constellation >= 3 ? c3Key : baseKey,
                constellation >= 3 ? talentTwelve : talentNine);
    }

    private double burstValue(
            String baseKey,
            String c5Key,
            double talentNine,
            double talentTwelve) {
        return getTalentValue(
                constellation >= 5 ? c5Key : baseKey,
                constellation >= 5 ? talentTwelve : talentNine);
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
                case SKILL_ACTIVATE:
                    activateSkillState(activeSimulator);
                    break;
                case NIGHTSOUL_EXPIRE:
                    expireNightsoul(activeSimulator, command.generation);
                    break;
                case OPAL_FIRE_HIT:
                    emitOpalFireHit(activeSimulator, command.generation);
                    break;
                case OPAL_FIRE_DRAIN:
                    drainOpalFire(activeSimulator, command.generation);
                    break;
                case BURST_ENERGY:
                    spendBurstEnergy(activeSimulator.getCurrentTime());
                    break;
                case BURST_POINTS:
                    generateNightsoulPoints(
                            activeSimulator,
                            getTalentValue(
                                    "Burst Initial Nightsoul Points", 24.0));
                    break;
                case PARTICLE:
                    activeSimulator.getEnergyDistributor()
                            .distributeParticles(
                                    Element.CRYO,
                                    getTalentValue("Particle Count", 5.0),
                                    ParticleType.PARTICLE);
                    break;
                default:
                    throw new IllegalStateException(
                            "Unknown Citlali command " + command.kind);
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
        SKILL_INITIAL,
        FROSTFALL,
        BURST_INITIAL,
        BURST_SKULL,
        C4_SKULL
    }

    private enum CommandKind {
        SKILL_ACTIVATE,
        NIGHTSOUL_EXPIRE,
        OPAL_FIRE_HIT,
        OPAL_FIRE_DRAIN,
        BURST_ENERGY,
        BURST_POINTS,
        PARTICLE
    }

    private static final class PendingHit {
        private final double time;
        private final HitKind kind;
        private final int index;
        private final StatsContainer snapshot;
        private final double additiveBaseDamage;

        private PendingHit(
                double time,
                HitKind kind,
                int index,
                StatsContainer snapshot,
                double additiveBaseDamage) {
            this.time = time;
            this.kind = kind;
            this.index = index;
            this.snapshot = snapshot == null
                    ? null : snapshot.merge(null);
            this.additiveBaseDamage = additiveBaseDamage;
        }

        private PendingHit copy() {
            return new PendingHit(
                    time, kind, index, snapshot, additiveBaseDamage);
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

    private static final class CitlaliState implements State {
        private final Citlali owner;
        private final int normalAttackStep;
        private final long nightsoulGeneration;
        private final long opalFireGeneration;
        private final boolean nightsoulActive;
        private final boolean opalFireActive;
        private final double nightsoulExpirationTime;
        private final double nightsoulPoints;
        private final int stellarBladeCount;
        private final double c6PointCount;
        private final double nextA1PointTime;
        private final double nextParticleTime;
        private final double nextC4Time;
        private final List<PendingHit> pendingHits;
        private final List<PendingCommand> pendingCommands;

        private CitlaliState(
                Citlali owner,
                int normalAttackStep,
                long nightsoulGeneration,
                long opalFireGeneration,
                boolean nightsoulActive,
                boolean opalFireActive,
                double nightsoulExpirationTime,
                double nightsoulPoints,
                int stellarBladeCount,
                double c6PointCount,
                double nextA1PointTime,
                double nextParticleTime,
                double nextC4Time,
                List<PendingHit> pendingHits,
                List<PendingCommand> pendingCommands) {
            this.owner = owner;
            this.normalAttackStep = normalAttackStep;
            this.nightsoulGeneration = nightsoulGeneration;
            this.opalFireGeneration = opalFireGeneration;
            this.nightsoulActive = nightsoulActive;
            this.opalFireActive = opalFireActive;
            this.nightsoulExpirationTime = nightsoulExpirationTime;
            this.nightsoulPoints = nightsoulPoints;
            this.stellarBladeCount = stellarBladeCount;
            this.c6PointCount = c6PointCount;
            this.nextA1PointTime = nextA1PointTime;
            this.nextParticleTime = nextParticleTime;
            this.nextC4Time = nextC4Time;
            this.pendingHits = copyHits(pendingHits);
            this.pendingCommands = copyCommands(pendingCommands);
        }
    }
}

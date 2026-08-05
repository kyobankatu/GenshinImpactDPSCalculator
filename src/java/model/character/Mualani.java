package model.character;

import java.util.ArrayList;
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
import simulation.action.SkillActionMode;
import simulation.event.SimpleTimerEvent;

/**
 * Mualani's fixed-target Surfshark Wavebreaker offensive slice.
 *
 * <p>Cooling Treatment basics, local Nightsoul points, explicit fixed-target
 * Wave Momentum contacts, Sharky's Bite, deterministic Surging Bite, A1
 * puffers, Boomsharka-laka, particles, and representable A4/C1-C6 branches
 * follow pinned gcsim {@code ef41805d855a60b9e1035293584b85c085dc69e7}.
 * Skill cooldown begins when Nightsoul's Blessing ends. Burst stats snapshot
 * at projectile creation while its Max-HP base damage uses impact-time HP, as
 * encoded by the pinned source.</p>
 *
 * <p>Player HP changes and healing, movement/surfing/terrain simulation,
 * enemy-mark geometry, multi-target Shark Missiles, automatic Nightsoul Burst
 * team plumbing, random targets, stamina, hitlag, low Plunge, and exploration
 * state are excluded rather than approximated.</p>
 */
public final class Mualani extends Character implements
        SimulatorInitializedCharacterEffect,
        SnapshotAwareCharacterEffect,
        SwitchAwareCharacter {
    private static final double FRAME = 1.0 / 60.0;
    private static final double EPSILON = 1e-9;
    private static final double[] NORMAL_T9 = {
        0.873732, 0.758635, 1.190585
    };
    private static final int[] NORMAL_HIT_FRAMES = { 11, 9, 31 };
    private static final int[] NORMAL_DURATIONS = { 33, 32, 67 };

    private final DoubleSupplier particleRandom;
    private CombatSimulator initializedSimulator;
    private int normalAttackStep;
    private long skillGeneration;
    private long burstGeneration;
    private boolean nightsoulActive;
    private double nightsoulPoints;
    private int waveMomentumStacks;
    private int a1PufferCount;
    private boolean c1Consumed;
    private int a4Stacks;
    private double nextBiteAllowedTime = Double.NEGATIVE_INFINITY;
    private double nextContactAllowedTime = Double.NEGATIVE_INFINITY;
    private boolean particleGenerated;
    private List<PendingHit> pendingHits = new ArrayList<>();
    private List<PendingCommand> pendingCommands = new ArrayList<>();

    /** Constructs repository-default C6 Mualani. */
    public Mualani(Weapon weapon, ArtifactSet artifacts) {
        this(weapon, artifacts, TalentDataManager.getInstance(), 6,
                Math::random);
    }

    /** Constructs Mualani at an explicit constellation. */
    public Mualani(
            Weapon weapon,
            ArtifactSet artifacts,
            int constellation) {
        this(weapon, artifacts, TalentDataManager.getInstance(),
                constellation, Math::random);
    }

    /**
     * Constructs Mualani with injectable static data and particle randomness.
     *
     * @param weapon equipped catalyst, or {@code null}
     * @param artifacts equipped artifact set, or {@code null}
     * @param talentData static character-data source
     * @param constellation constellation level in {@code [0, 6]}
     * @param particleRandom source of particle draws in {@code [0, 1)}
     */
    public Mualani(
            Weapon weapon,
            ArtifactSet artifacts,
            TalentDataSource talentData,
            int constellation,
            DoubleSupplier particleRandom) {
        super(talentData);
        if (constellation < 0 || constellation > 6) {
            throw new IllegalArgumentException(
                    "Mualani constellation must be between 0 and 6");
        }
        if (particleRandom == null) {
            throw new IllegalArgumentException(
                    "Mualani particle random source is required");
        }
        name = "Mualani";
        characterId = CharacterId.MUALANI;
        element = Element.HYDRO;
        this.weapon = weapon;
        this.artifacts = new ArtifactSet[] { artifacts };
        this.constellation = constellation;
        this.particleRandom = particleRandom;
        baseStats.set(StatType.BASE_HP,
                getTalentValue("Base HP", 15185.0));
        baseStats.set(StatType.BASE_ATK,
                getTalentValue("Base ATK", 182.0));
        baseStats.set(StatType.BASE_DEF,
                getTalentValue("Base DEF", 570.0));
        baseStats.add(StatType.CRIT_RATE,
                getTalentValue("Ascension CRIT Rate", 0.192));
        setSkillCD(getTalentValue("Skill Cooldown", 6.0));
        setBurstCD(getTalentValue("Burst Cooldown", 15.0));
    }

    /** Binds Mualani-owned delayed work to one simulator instance. */
    @Override
    public void initializeForSimulator(CombatSimulator simulator) {
        if (simulator == null) {
            throw new IllegalArgumentException(
                    "Mualani simulator is required");
        }
        if (!simulator.getPartyMembers().contains(this)) {
            throw new IllegalArgumentException(
                    "Mualani must belong to the simulator party");
        }
        if (initializedSimulator != null
                && initializedSimulator != simulator) {
            throw new IllegalStateException(
                    "Mualani cannot be reused across simulators");
        }
        initializedSimulator = simulator;
    }

    /** Captures all local resources, gates, and reconstructable future work. */
    @Override
    public State captureCharacterState() {
        return new MualaniState(
                this,
                normalAttackStep,
                skillGeneration,
                burstGeneration,
                nightsoulActive,
                nightsoulPoints,
                waveMomentumStacks,
                a1PufferCount,
                c1Consumed,
                a4Stacks,
                nextBiteAllowedTime,
                nextContactAllowedTime,
                particleGenerated,
                pendingHits,
                pendingCommands);
    }

    /** Accepts state captured from this exact Mualani instance only. */
    @Override
    public boolean acceptsCharacterState(State state) {
        return state instanceof MualaniState
                && ((MualaniState) state).owner == this;
    }

    /** Restores local state and registers each surviving event once. */
    @Override
    public void restoreCharacterState(
            State state,
            CombatSimulator simulator) {
        if (!acceptsCharacterState(state)) {
            throw new IllegalArgumentException("Unexpected Mualani state");
        }
        initializeForSimulator(simulator);
        MualaniState restored = (MualaniState) state;
        normalAttackStep = restored.normalAttackStep;
        skillGeneration = restored.skillGeneration;
        burstGeneration = restored.burstGeneration;
        nightsoulActive = restored.nightsoulActive;
        nightsoulPoints = restored.nightsoulPoints;
        waveMomentumStacks = restored.waveMomentumStacks;
        a1PufferCount = restored.a1PufferCount;
        c1Consumed = restored.c1Consumed;
        a4Stacks = restored.a4Stacks;
        nextBiteAllowedTime = restored.nextBiteAllowedTime;
        nextContactAllowedTime = restored.nextContactAllowedTime;
        particleGenerated = restored.particleGenerated;
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

    /** Returns Mualani's 60-Energy Burst cost. */
    @Override
    public double getEnergyCost() {
        return getTalentValue("Energy Cost", 60.0);
    }

    /** Mualani has no unconditional represented stat passive. */
    @Override
    public void applyPassive(StatsContainer stats) {
    }

    /** Ends Nightsoul and starts Skill cooldown when Mualani leaves the field. */
    @Override
    public void onSwitchOut(CombatSimulator simulator) {
        normalAttackStep = 0;
        if (nightsoulActive) {
            endNightsoul(simulator, simulator.getCurrentTime());
        }
    }

    /** Resets Mualani's basic three-hit string on switch-in. */
    @Override
    public void onSwitchIn(CombatSimulator simulator) {
        normalAttackStep = 0;
    }

    /** Returns whether Nightsoul's Blessing is locally active. */
    public boolean isNightsoulActive() {
        return nightsoulActive;
    }

    /** Returns the current locally tracked Nightsoul-point balance. */
    public double getNightsoulPoints() {
        return nightsoulPoints;
    }

    /** Returns the current capped Wave Momentum stack count. */
    public int getWaveMomentumStacks() {
        return waveMomentumStacks;
    }

    /** Returns A1 puffers consumed in the current Nightsoul generation. */
    public int getA1PufferCount() {
        return a1PufferCount;
    }

    /** Returns queued A4 stacks awaiting the next Burst. */
    public int getA4Stacks() {
        return a4Stacks;
    }

    /** Returns the timestamp when the next Nightsoul Bite becomes available. */
    public double getNextBiteAllowedTime() {
        return nextBiteAllowedTime;
    }

    /** Returns the number of unresolved Mualani-owned damage hits. */
    public int getPendingHitCount() {
        return pendingHits.size();
    }

    /**
     * Records an externally confirmed single-target surfing contact.
     *
     * <p>The caller owns movement and geometry validation. This method only
     * applies the source 0.7-second per-target gate and three-stack cap.</p>
     *
     * @param simulator bound simulator at contact time
     * @return {@code true} when one Wave Momentum stack was added
     */
    public boolean notifyFixedTargetSurfingContact(
            CombatSimulator simulator) {
        initializeForSimulator(simulator);
        double currentTime = simulator.getCurrentTime();
        if (!nightsoulActive
                || simulator.getActiveCharacter() != this
                || simulator.getEnemy() == null
                || currentTime + EPSILON < nextContactAllowedTime) {
            return false;
        }
        nextContactAllowedTime = currentTime
                + getTalentValue("Wave Momentum Cooldown", 0.7);
        if (waveMomentumStacks >= 3) {
            return false;
        }
        waveMomentumStacks++;
        return true;
    }

    /**
     * Records one externally confirmed Nightsoul Burst for A4.
     *
     * <p>No team listener or automatic trigger is installed by this slice;
     * callers must only invoke this after resolving the team mechanic.</p>
     *
     * @param simulator bound simulator where the event occurred
     * @return {@code true} when one A4 stack was added
     */
    public boolean notifyExternallyConfirmedNightsoulBurst(
            CombatSimulator simulator) {
        initializeForSimulator(simulator);
        int limit = frameValue("A4 Stack Limit", 3);
        if (a4Stacks >= limit) {
            return false;
        }
        a4Stacks++;
        return true;
    }

    /** Reports that player HP-change mechanics are unavailable. */
    public boolean isPlayerHpChangeRepresented() {
        return false;
    }

    /** Reports that healing mechanics are unavailable. */
    public boolean isHealingRepresented() {
        return false;
    }

    /** Reports that movement, surfing, and terrain simulation are unavailable. */
    public boolean isMovementSurfingTerrainRepresented() {
        return false;
    }

    /** Reports that enemy-mark geometry is unavailable. */
    public boolean isEnemyMarkGeometryRepresented() {
        return false;
    }

    /** Reports that multi-target Shark Missiles are unavailable. */
    public boolean isMultiTargetMissileRepresented() {
        return false;
    }

    /** Reports that automatic Nightsoul Burst team plumbing is unavailable. */
    public boolean isNightsoulBurstTeamPlumbingRepresented() {
        return false;
    }

    /** Reports that random target selection is unavailable. */
    public boolean isRandomTargetRepresented() {
        return false;
    }

    /** Reports that stamina consumption is unavailable. */
    public boolean isStaminaRepresented() {
        return false;
    }

    /** Reports that hitlag is unavailable. */
    public boolean isHitlagRepresented() {
        return false;
    }

    /** Reports that low Plunge is unavailable. */
    public boolean isLowPlungeRepresented() {
        return false;
    }

    /** Reports that unsupported exploration state is unavailable. */
    public boolean isExplorationStateRepresented() {
        return false;
    }

    /** Dispatches Mualani's represented typed action set. */
    @Override
    public void onAction(
            CharacterActionRequest request,
            CombatSimulator simulator) {
        if (request == null) {
            throw new IllegalArgumentException("Mualani action is required");
        }
        initializeForSimulator(simulator);
        if (request.getKey() == CharacterActionKey.SKILL
                && request.getSkillMode() != SkillActionMode.PRESS) {
            throw new IllegalArgumentException(
                    "Mualani supports Press Skill only");
        }
        if (request.getKey() != CharacterActionKey.NORMAL) {
            normalAttackStep = 0;
        }
        switch (request.getKey()) {
            case NORMAL:
                if (nightsoulActive) {
                    sharkBite(simulator);
                } else {
                    normalAttack(simulator);
                }
                break;
            case CHARGE:
                if (nightsoulActive) {
                    throw new IllegalStateException(
                            "Mualani cannot use Charged Attack during Nightsoul");
                }
                chargedAttack(simulator);
                break;
            case PLUNGE:
                if (nightsoulActive) {
                    throw new IllegalStateException(
                            "Mualani cannot use Plunge during Nightsoul");
                }
                highPlunge(simulator);
                break;
            case SKILL:
                if (nightsoulActive) {
                    cancelNightsoul(simulator);
                } else {
                    enterNightsoul(simulator);
                }
                break;
            case BURST:
                boomsharkaLaka(simulator);
                break;
            default:
                throw new IllegalArgumentException(
                        "Unsupported action for Mualani: "
                                + request.getKey());
        }
    }

    private void normalAttack(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        int step = normalAttackStep;
        int hitFrames = frameValue(
                "N" + (step + 1) + " Hit Frames",
                NORMAL_HIT_FRAMES[step]);
        queueHit(simulator, new PendingHit(
                castTime + hitFrames * FRAME,
                HitKind.NORMAL,
                step,
                0L,
                null));
        normalAttackStep = (normalAttackStep + 1) % NORMAL_T9.length;
        int duration = frameValue(
                "N" + (step + 1) + " Duration Frames",
                NORMAL_DURATIONS[step]);
        simulator.advanceTime(duration * FRAME);
    }

    private void chargedAttack(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        queueHit(simulator, new PendingHit(
                castTime + frameValue("Charged Hit Frames", 71) * FRAME,
                HitKind.CHARGED,
                0,
                0L,
                null));
        simulator.advanceTime(
                frameValue("Charged Duration Frames", 100) * FRAME);
    }

    private void highPlunge(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        queueHit(simulator, new PendingHit(
                castTime
                        + frameValue("High Plunge Hit Frames", 45)
                                * FRAME,
                HitKind.HIGH_PLUNGE,
                0,
                0L,
                null));
        simulator.advanceTime(
                frameValue("High Plunge Duration Frames", 68) * FRAME);
    }

    private void enterNightsoul(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        long generation = ++skillGeneration;
        queueCommand(simulator, new PendingCommand(
                castTime + frameValue("Skill Entry Frames", 2) * FRAME,
                CommandKind.SKILL_ENTRY,
                generation,
                0));
        simulator.advanceTime(
                frameValue("Skill Duration Frames", 69) * FRAME);
    }

    private void cancelNightsoul(CombatSimulator simulator) {
        endNightsoul(simulator, simulator.getCurrentTime());
        simulator.advanceTime(
                frameValue("Skill Cancel Duration Frames", 17) * FRAME);
    }

    private void beginNightsoul(
            CombatSimulator simulator,
            long generation) {
        if (generation != skillGeneration) {
            return;
        }
        nightsoulActive = true;
        nightsoulPoints = getTalentValue(
                "Nightsoul Maximum Points", 60.0);
        waveMomentumStacks = constellation >= 2
                ? frameValue("C2 Initial Momentum", 2) : 0;
        a1PufferCount = 0;
        c1Consumed = false;
        nextBiteAllowedTime = Double.NEGATIVE_INFINITY;
        nextContactAllowedTime = Double.NEGATIVE_INFINITY;
        particleGenerated = false;
        queueCommand(simulator, new PendingCommand(
                simulator.getCurrentTime()
                        + frameValue(
                                "Nightsoul Drain Interval Frames", 6)
                                * FRAME,
                CommandKind.NIGHTSOUL_DRAIN,
                generation,
                0));
    }

    private void drainNightsoul(
            CombatSimulator simulator,
            long generation) {
        if (generation != skillGeneration || !nightsoulActive) {
            return;
        }
        nightsoulPoints = Math.max(0.0, nightsoulPoints - 1.0);
        if (nightsoulPoints <= EPSILON) {
            endNightsoul(simulator, simulator.getCurrentTime());
            return;
        }
        queueCommand(simulator, new PendingCommand(
                simulator.getCurrentTime()
                        + frameValue(
                                "Nightsoul Drain Interval Frames", 6)
                                * FRAME,
                CommandKind.NIGHTSOUL_DRAIN,
                generation,
                0));
    }

    private void endNightsoul(
            CombatSimulator simulator,
            double endTime) {
        if (!nightsoulActive) {
            return;
        }
        long endedGeneration = skillGeneration;
        nightsoulActive = false;
        nightsoulPoints = 0.0;
        waveMomentumStacks = 0;
        nextBiteAllowedTime = Double.NEGATIVE_INFINITY;
        nextContactAllowedTime = Double.NEGATIVE_INFINITY;
        pendingCommands.removeIf(command ->
                command.generation == endedGeneration
                        && command.kind.isNightsoulOwned());
        skillGeneration++;
        markSkillUsed(endTime, simulator.getApplicableBuffs(this));
    }

    private void sharkBite(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        if (castTime + EPSILON < nextBiteAllowedTime) {
            throw new IllegalStateException(
                    "Mualani Sharky's Bite is on cooldown");
        }
        int stacks = waveMomentumStacks;
        int hitFrames = stacks >= 3
                ? frameValue("Surging Bite Hit Frames", 42)
                : frameValue("Bite Hit Frames", 7);
        queueHit(simulator, new PendingHit(
                castTime + hitFrames * FRAME,
                HitKind.BITE,
                stacks,
                skillGeneration,
                null));
        int duration = stacks >= 3
                ? frameValue("Surging Bite Duration Frames", 258)
                : frameValue("Bite Duration Frames", 215);
        simulator.advanceTime(duration * FRAME);
    }

    private void boomsharkaLaka(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        int consumedA4Stacks = a4Stacks;
        a4Stacks = 0;
        long generation = ++burstGeneration;
        markBurstCooldownUsed(
                castTime, simulator.getApplicableBuffs(this));
        queueCommand(simulator, new PendingCommand(
                castTime + frameValue("Burst Energy Frame", 11) * FRAME,
                CommandKind.BURST_ENERGY,
                generation,
                0));
        queueCommand(simulator, new PendingCommand(
                castTime + frameValue("Burst Snapshot Frame", 108) * FRAME,
                CommandKind.BURST_SNAPSHOT,
                generation,
                consumedA4Stacks));
        simulator.advanceTime(
                frameValue("Burst Duration Frames", 180) * FRAME);
    }

    private void resolveHit(
            CombatSimulator simulator,
            PendingHit hit) {
        switch (hit.kind) {
            case NORMAL:
                performHit(
                        simulator,
                        hit,
                        "Cooling Treatment N" + (hit.index + 1),
                        getTalentValue(
                                "N" + (hit.index + 1),
                                NORMAL_T9[hit.index]),
                        StatType.BASE_ATK,
                        StatType.NORMAL_ATTACK_DMG_BONUS,
                        ActionType.NORMAL,
                        ICDType.Standard,
                        ICDTag.NormalAttack,
                        1.0,
                        0.0,
                        false);
                break;
            case CHARGED:
                performHit(
                        simulator,
                        hit,
                        "Cooling Treatment Charged Attack",
                        getTalentValue("Charged Attack", 2.42896),
                        StatType.BASE_ATK,
                        StatType.CHARGED_ATTACK_DMG_BONUS,
                        ActionType.CHARGE,
                        ICDType.None,
                        ICDTag.None,
                        1.0,
                        0.0,
                        false);
                break;
            case HIGH_PLUNGE:
                performHit(
                        simulator,
                        hit,
                        "Cooling Treatment High Plunge",
                        getTalentValue("High Plunge", 2.607632),
                        StatType.BASE_ATK,
                        StatType.PLUNGING_ATTACK_DMG_BONUS,
                        ActionType.PLUNGE,
                        ICDType.None,
                        ICDTag.None,
                        1.0,
                        0.0,
                        false);
                break;
            case BITE:
                resolveBite(simulator, hit);
                break;
            case BURST:
                resolveBurst(simulator, hit);
                break;
            default:
                throw new IllegalStateException(
                        "Unknown Mualani hit kind " + hit.kind);
        }
    }

    private void resolveBite(
            CombatSimulator simulator,
            PendingHit hit) {
        waveMomentumStacks = 0;
        nextBiteAllowedTime = hit.time
                + getTalentValue("Bite Cooldown", 1.8);
        double multiplier = skillTalentValue(
                "Sharky's Bite", 0.14756, 0.1736);
        multiplier += hit.index * skillTalentValue(
                "Wave Momentum", 0.07378, 0.0868);
        boolean surging = hit.index >= 3;
        if (surging) {
            multiplier += skillTalentValue(
                    "Surging Bite Additional", 0.3689, 0.434);
        }
        if (constellation >= 1 && !c1Consumed) {
            multiplier += getTalentValue("C1 Max HP Bonus", 0.66);
            if (constellation < 6) {
                c1Consumed = true;
            }
        }
        performHit(
                simulator,
                hit,
                surging
                        ? "Sharky's Surging Bite" : "Sharky's Bite",
                multiplier,
                StatType.BASE_HP,
                StatType.NORMAL_ATTACK_DMG_BONUS,
                ActionType.NORMAL,
                ICDType.None,
                ICDTag.None,
                1.0,
                0.0,
                false);
        if (simulator.getEnemy() == null) {
            return;
        }
        queueParticle(simulator, hit.time);
        queueA1Puffer(simulator, hit);
    }

    private void resolveBurst(
            CombatSimulator simulator,
            PendingHit hit) {
        if (hit.generation != burstGeneration) {
            return;
        }
        double ratio = burstTalentValue(
                "Boomsharka-laka", 0.993466, 1.168784);
        ratio += hit.index
                * getTalentValue("A4 Max HP Per Stack", 0.15);
        double liveHp = captureLiveStats(hit.time).getTotalHp();
        performHit(
                simulator,
                hit,
                "Boomsharka-laka",
                0.0,
                StatType.BASE_HP,
                StatType.BURST_DMG_BONUS,
                ActionType.BURST,
                ICDType.None,
                ICDTag.None,
                1.0,
                liveHp * ratio,
                constellation >= 4);
    }

    private void queueParticle(
            CombatSimulator simulator,
            double impactTime) {
        if (particleGenerated) {
            return;
        }
        double draw = particleRandom.getAsDouble();
        if (!Double.isFinite(draw) || draw < 0.0 || draw >= 1.0) {
            throw new IllegalStateException(
                    "Mualani particle random draw must be in [0, 1)");
        }
        particleGenerated = true;
        int count = draw < 0.5
                ? frameValue("Particle Count Max", 5)
                : frameValue("Particle Count Min", 4);
        queueCommand(simulator, new PendingCommand(
                impactTime
                        + frameValue("Particle Travel Frames", 100)
                                * FRAME,
                CommandKind.PARTICLE,
                0L,
                count));
    }

    private void queueA1Puffer(
            CombatSimulator simulator,
            PendingHit hit) {
        int limit = frameValue("A1 Puffer Limit", 2);
        if (a1PufferCount >= limit) {
            return;
        }
        a1PufferCount++;
        queueCommand(simulator, new PendingCommand(
                hit.time
                        + frameValue("A1 Puffer Delay Frames", 20)
                                * FRAME,
                CommandKind.A1_PUFFER,
                hit.generation,
                a1PufferCount));
    }

    private void resolveA1Puffer(
            CombatSimulator simulator,
            PendingCommand command) {
        if (command.generation != skillGeneration || !nightsoulActive) {
            return;
        }
        addNightsoulPoints(getTalentValue(
                "A1 Puffer Nightsoul Points", 20.0));
        if (constellation >= 2) {
            waveMomentumStacks = Math.min(3, waveMomentumStacks + 1);
            if (command.value == frameValue("A1 Puffer Limit", 2)) {
                int pointCount = frameValue(
                        "C2 Delayed Nightsoul Points", 12);
                int interval = frameValue(
                        "C2 Point Interval Frames", 10);
                for (int index = 1; index <= pointCount; index++) {
                    queueCommand(simulator, new PendingCommand(
                            command.time + index * interval * FRAME,
                            CommandKind.C2_POINT,
                            command.generation,
                            1));
                }
            }
        }
        if (constellation >= 4) {
            receiveFlatEnergy(getTalentValue("C4 Puffer Energy", 8.0));
        }
    }

    private void addNightsoulPoints(double points) {
        if (!nightsoulActive) {
            return;
        }
        nightsoulPoints = Math.min(
                getTalentValue("Nightsoul Maximum Points", 60.0),
                nightsoulPoints + points);
    }

    private void performHit(
            CombatSimulator simulator,
            PendingHit hit,
            String displayName,
            double multiplier,
            StatType scalingStat,
            StatType bonusStat,
            ActionType actionType,
            ICDType icdType,
            ICDTag icdTag,
            double gaugeUnits,
            double fixedBaseDamage,
            boolean c4BurstBonus) {
        AttackAction action = fixedBaseDamage == 0.0
                ? new AttackAction(
                        displayName,
                        multiplier,
                        Element.HYDRO,
                        scalingStat,
                        bonusStat,
                        0.0,
                        actionType)
                : new FixedBaseDamageAction(
                        displayName,
                        Element.HYDRO,
                        bonusStat,
                        actionType,
                        fixedBaseDamage);
        action.setICD(icdType, icdTag, gaugeUnits);
        action.setCountsAsBurstDmg(actionType == ActionType.BURST);
        if (c4BurstBonus) {
            action.addBonusStat(
                    StatType.DMG_BONUS_ALL,
                    getTalentValue("C4 Burst DMG Bonus", 0.75));
        }
        action.setStatSnapshot(hit.snapshot == null
                ? captureLiveStats(simulator.getCurrentTime())
                : hit.snapshot);
        simulator.performActionWithoutTimeAdvance(characterId, action);
    }

    private double skillTalentValue(
            String key,
            double talentNine,
            double talentTwelve) {
        String suffix = constellation >= 3 ? " C3" : "";
        return getTalentValue(
                key + suffix,
                constellation >= 3 ? talentTwelve : talentNine);
    }

    private double burstTalentValue(
            String key,
            double talentNine,
            double talentTwelve) {
        String suffix = constellation >= 5 ? " C5" : "";
        return getTalentValue(
                key + suffix,
                constellation >= 5 ? talentTwelve : talentNine);
    }

    private int frameValue(String key, int defaultValue) {
        return (int) Math.round(getTalentValue(key, defaultValue));
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
                    beginNightsoul(activeSimulator, command.generation);
                    break;
                case NIGHTSOUL_DRAIN:
                    drainNightsoul(activeSimulator, command.generation);
                    break;
                case A1_PUFFER:
                    resolveA1Puffer(activeSimulator, command);
                    break;
                case C2_POINT:
                    if (command.generation == skillGeneration) {
                        addNightsoulPoints(command.value);
                    }
                    break;
                case PARTICLE:
                    activeSimulator.getEnergyDistributor()
                            .distributeParticles(
                                    Element.HYDRO,
                                    command.value,
                                    ParticleType.PARTICLE);
                    break;
                case BURST_ENERGY:
                    if (command.generation == burstGeneration) {
                        spendBurstEnergy(command.time);
                    }
                    break;
                case BURST_SNAPSHOT:
                    if (command.generation == burstGeneration) {
                        queueHit(activeSimulator, new PendingHit(
                                command.time
                                        + frameValue(
                                                "Burst Travel Frames", 70)
                                                * FRAME,
                                HitKind.BURST,
                                command.value,
                                command.generation,
                                captureLiveStats(command.time)));
                    }
                    break;
                default:
                    throw new IllegalStateException(
                            "Unknown Mualani command kind "
                                    + command.kind);
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
        BITE,
        BURST
    }

    private enum CommandKind {
        SKILL_ENTRY,
        NIGHTSOUL_DRAIN,
        A1_PUFFER,
        C2_POINT,
        PARTICLE,
        BURST_ENERGY,
        BURST_SNAPSHOT;

        private boolean isNightsoulOwned() {
            return this == NIGHTSOUL_DRAIN
                    || this == A1_PUFFER
                    || this == C2_POINT;
        }
    }

    /** Preserves Burst impact-time Max HP through snapshot damage resolution. */
    private static final class FixedBaseDamageAction extends AttackAction {
        private final double fixedBaseDamage;

        private FixedBaseDamageAction(
                String displayName,
                Element element,
                StatType bonusStat,
                ActionType actionType,
                double fixedBaseDamage) {
            super(
                    displayName,
                    0.0,
                    element,
                    StatType.BASE_HP,
                    bonusStat,
                    0.0,
                    actionType);
            this.fixedBaseDamage = fixedBaseDamage;
            setHitEffectTrigger(true);
        }

        @Override
        public void setAdditiveBaseDmgBonus(double value) {
        }

        @Override
        public double getAdditiveBaseDmgBonus() {
            return fixedBaseDamage;
        }
    }

    /** Immutable queued impact with source generation and optional snapshot. */
    private static final class PendingHit {
        private final double time;
        private final HitKind kind;
        private final int index;
        private final long generation;
        private final StatsContainer snapshot;

        private PendingHit(
                double time,
                HitKind kind,
                int index,
                long generation,
                StatsContainer snapshot) {
            this.time = time;
            this.kind = kind;
            this.index = index;
            this.generation = generation;
            this.snapshot = snapshot == null
                    ? null : snapshot.merge(null);
        }

        private PendingHit copy() {
            return new PendingHit(
                    time,
                    kind,
                    index,
                    generation,
                    snapshot);
        }
    }

    /** Immutable delayed state command. */
    private static final class PendingCommand {
        private final double time;
        private final CommandKind kind;
        private final long generation;
        private final int value;

        private PendingCommand(
                double time,
                CommandKind kind,
                long generation,
                int value) {
            this.time = time;
            this.kind = kind;
            this.generation = generation;
            this.value = value;
        }

        private PendingCommand copy() {
            return new PendingCommand(time, kind, generation, value);
        }
    }

    /** Immutable snapshot of all Mualani-owned mutable runtime state. */
    private static final class MualaniState implements State {
        private final Mualani owner;
        private final int normalAttackStep;
        private final long skillGeneration;
        private final long burstGeneration;
        private final boolean nightsoulActive;
        private final double nightsoulPoints;
        private final int waveMomentumStacks;
        private final int a1PufferCount;
        private final boolean c1Consumed;
        private final int a4Stacks;
        private final double nextBiteAllowedTime;
        private final double nextContactAllowedTime;
        private final boolean particleGenerated;
        private final List<PendingHit> pendingHits;
        private final List<PendingCommand> pendingCommands;

        private MualaniState(
                Mualani owner,
                int normalAttackStep,
                long skillGeneration,
                long burstGeneration,
                boolean nightsoulActive,
                double nightsoulPoints,
                int waveMomentumStacks,
                int a1PufferCount,
                boolean c1Consumed,
                int a4Stacks,
                double nextBiteAllowedTime,
                double nextContactAllowedTime,
                boolean particleGenerated,
                List<PendingHit> pendingHits,
                List<PendingCommand> pendingCommands) {
            this.owner = owner;
            this.normalAttackStep = normalAttackStep;
            this.skillGeneration = skillGeneration;
            this.burstGeneration = burstGeneration;
            this.nightsoulActive = nightsoulActive;
            this.nightsoulPoints = nightsoulPoints;
            this.waveMomentumStacks = waveMomentumStacks;
            this.a1PufferCount = a1PufferCount;
            this.c1Consumed = c1Consumed;
            this.a4Stacks = a4Stacks;
            this.nextBiteAllowedTime = nextBiteAllowedTime;
            this.nextContactAllowedTime = nextContactAllowedTime;
            this.particleGenerated = particleGenerated;
            this.pendingHits = copyHits(pendingHits);
            this.pendingCommands = copyCommands(pendingCommands);
        }
    }
}

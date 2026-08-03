package model.character;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.DoubleSupplier;

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
import simulation.action.SkillActionMode;
import simulation.event.SimpleTimerEvent;

/**
 * Klee's classic pre-Hexerei stationary single-target offensive kit through C6.
 *
 * <p>Timing and multipliers follow pinned gcsim {@code ef41805d}; the A1
 * four-second gate follows pinned KQM TCL {@code 80ba6241}. Skill and Burst
 * snapshots, two independent Skill charges, C1 pity, C2 live target DEF
 * reduction, C4 switch detonation, and C6 party effects are represented.</p>
 *
 * <p>Actual-crit A4, stamina, animation cancels, collision geometry, mine
 * scattering, multi-target behavior, defensive behavior, hitlag, and Hexerei
 * are intentionally excluded. High Plunge uses the repository's fixed
 * one-second catalyst policy.</p>
 */
public final class Klee extends Character implements
        SimulatorInitializedCharacterEffect,
        SnapshotAwareCharacterEffect,
        SwitchAwareCharacter,
        TargetDependentTeamEffect {
    private static final double FRAME = 1.0 / 60.0;
    private static final double EPSILON = 1e-9;
    private static final int[] NORMAL_RELEASE_FRAMES = { 16, 23, 37 };
    private static final int[] NORMAL_DURATION_FRAMES = { 34, 41, 77 };
    private static final double[] NORMAL_MULTIPLIERS = {
            1.22672, 1.0608, 1.52864
    };
    private static final int[] BURST_WAVE_FRAMES = {
            186, 294, 401, 503, 610, 718
    };
    private static final int[] C6_ENERGY_FRAMES = { 326, 506, 686 };

    private final DoubleSupplier a1DrawSource;
    private final DoubleSupplier c1DrawSource;
    private final DoubleSupplier burstDrawSource;
    private CombatSimulator initializedSimulator;
    private int normalAttackStep;
    private long burstGeneration;
    private List<PendingHit> pendingHits = new ArrayList<>();
    private List<PendingCommand> pendingCommands = new ArrayList<>();
    private BurstInstance burstInstance;
    private double a1SparkExpirationTime = Double.NEGATIVE_INFINITY;
    private double nextA1ProcTime = Double.NEGATIVE_INFINITY;
    private double c1Chance = 0.10;
    private double c2ExpirationTime = Double.NEGATIVE_INFINITY;
    private boolean resolvingSkillMine;

    /** Constructs repository-default C6 Klee with stochastic proc sources. */
    public Klee(Weapon weapon, ArtifactSet artifacts) {
        this(weapon, artifacts, TalentDataManager.getInstance(), 6,
                Math::random, Math::random, Math::random);
    }

    /** Constructs Klee at an explicit constellation with stochastic sources. */
    public Klee(
            Weapon weapon,
            ArtifactSet artifacts,
            int constellation) {
        this(weapon, artifacts, TalentDataManager.getInstance(), constellation,
                Math::random, Math::random, Math::random);
    }

    /**
     * Constructs Klee with deterministic probability sources.
     *
     * @param weapon equipped catalyst
     * @param artifacts equipped artifact set
     * @param constellation constellation in the inclusive range 0-6
     * @param a1DrawSource A1 draws in {@code [0, 1]}
     * @param c1DrawSource C1 draws in {@code [0, 1]}
     * @param burstDrawSource Burst extra-hit draws in {@code [0, 1]}
     */
    public Klee(
            Weapon weapon,
            ArtifactSet artifacts,
            int constellation,
            DoubleSupplier a1DrawSource,
            DoubleSupplier c1DrawSource,
            DoubleSupplier burstDrawSource) {
        this(weapon, artifacts, TalentDataManager.getInstance(), constellation,
                a1DrawSource, c1DrawSource, burstDrawSource);
    }

    /**
     * Constructs Klee with injectable talent and probability sources.
     *
     * @param weapon equipped catalyst
     * @param artifacts equipped artifact set
     * @param talentData talent and configuration source
     * @param constellation constellation in the inclusive range 0-6
     * @param a1DrawSource A1 draws in {@code [0, 1]}
     * @param c1DrawSource C1 draws in {@code [0, 1]}
     * @param burstDrawSource Burst extra-hit draws in {@code [0, 1]}
     */
    public Klee(
            Weapon weapon,
            ArtifactSet artifacts,
            TalentDataSource talentData,
            int constellation,
            DoubleSupplier a1DrawSource,
            DoubleSupplier c1DrawSource,
            DoubleSupplier burstDrawSource) {
        super(talentData);
        if (constellation < 0 || constellation > 6) {
            throw new IllegalArgumentException(
                    "Klee constellation must be between 0 and 6");
        }
        if (a1DrawSource == null) {
            throw new IllegalArgumentException("Klee A1 draw is required");
        }
        if (c1DrawSource == null) {
            throw new IllegalArgumentException("Klee C1 draw is required");
        }
        if (burstDrawSource == null) {
            throw new IllegalArgumentException(
                    "Klee Burst draw is required");
        }
        name = "Klee";
        characterId = CharacterId.KLEE;
        element = Element.PYRO;
        this.weapon = weapon;
        this.artifacts = new ArtifactSet[] { artifacts };
        this.constellation = constellation;
        this.a1DrawSource = a1DrawSource;
        this.c1DrawSource = c1DrawSource;
        this.burstDrawSource = burstDrawSource;
        baseStats.set(StatType.BASE_HP,
                getTalentValue("Base HP", 10287.0));
        baseStats.set(StatType.BASE_ATK,
                getTalentValue("Base ATK", 311.0));
        baseStats.set(StatType.BASE_DEF,
                getTalentValue("Base DEF", 615.0));
        baseStats.add(StatType.PYRO_DMG_BONUS,
                getTalentValue("Ascension Pyro DMG", 0.288));
        setSkillCD(20.0);
        setSkillMaxCharges(2);
        setBurstCD(15.0);
    }

    /** Binds Klee's post-hit passives and pending work to one simulator. */
    @Override
    public void initializeForSimulator(CombatSimulator simulator) {
        if (simulator == null) {
            throw new IllegalArgumentException("Klee simulator is required");
        }
        if (initializedSimulator != null
                && initializedSimulator != simulator) {
            throw new IllegalStateException(
                    "Klee cannot be reused across CombatSimulator instances");
        }
        if (initializedSimulator == simulator) {
            return;
        }
        initializedSimulator = simulator;
        simulator.addDamageListener((actor, action, damage, time) ->
                onResolvedDamage(actor, action, time, simulator));
    }

    /** Captures every Klee-owned state value and reconstructible future event. */
    @Override
    public State captureCharacterState() {
        return new KleeState(
                normalAttackStep,
                burstGeneration,
                pendingHits,
                pendingCommands,
                burstInstance,
                a1SparkExpirationTime,
                nextA1ProcTime,
                c1Chance,
                c2ExpirationTime);
    }

    /** Reports whether a snapshot payload belongs to Klee. */
    @Override
    public boolean acceptsCharacterState(State state) {
        return state instanceof KleeState;
    }

    /** Restores Klee-owned state and schedules each surviving event exactly once. */
    @Override
    public void restoreCharacterState(
            State state,
            CombatSimulator simulator) {
        if (!acceptsCharacterState(state)) {
            throw new IllegalArgumentException("Unexpected Klee character state");
        }
        initializeForSimulator(simulator);
        KleeState restored = (KleeState) state;
        normalAttackStep = restored.normalAttackStep;
        burstGeneration = restored.burstGeneration;
        pendingHits = copyHits(restored.pendingHits);
        pendingCommands = copyCommands(restored.pendingCommands);
        burstInstance = restored.burstInstance == null
                ? null : restored.burstInstance.copy();
        a1SparkExpirationTime = restored.a1SparkExpirationTime;
        nextA1ProcTime = restored.nextA1ProcTime;
        c1Chance = restored.c1Chance;
        c2ExpirationTime = restored.c2ExpirationTime;
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

    /** Returns Klee's 60-Energy Burst cost. */
    @Override
    public double getEnergyCost() {
        return getTalentValue("Energy Cost", 60.0);
    }

    /** Applies the live A1 Spark to the next Charged Attack snapshot. */
    @Override
    public void applyPassive(StatsContainer stats) {
        if (initializedSimulator != null
                && initializedSimulator.getCurrentTime() + EPSILON
                        < a1SparkExpirationTime) {
            stats.add(
                    StatType.CHARGED_ATTACK_DMG_BONUS,
                    getTalentValue("A1 Charged DMG Bonus", 0.50));
        }
    }

    /** Applies C2 only as live impact-time target state. */
    @Override
    public void applyTargetDependentTeamStats(
            StatsContainer stats,
            Character attacker,
            Enemy target,
            AttackAction action,
            double currentTime) {
        if (constellation >= 2
                && currentTime + EPSILON < c2ExpirationTime) {
            stats.add(
                    StatType.ENEMY_DEF_REDUCTION,
                    getTalentValue("C2 DEF Reduction", 0.233));
        }
    }

    /** Stops Sparks 'n' Splash, resets normals, and emits C4 when active. */
    @Override
    public void onSwitchOut(CombatSimulator simulator) {
        normalAttackStep = 0;
        if (!isBurstActive(simulator.getCurrentTime())) {
            return;
        }
        burstInstance.active = false;
        if (constellation < 4) {
            return;
        }
        AttackAction explosion = attack(
                "Sparkly Explosion (C4)",
                getTalentValue("C4 Explosion", 5.55),
                Element.PYRO,
                null,
                ActionType.OTHER,
                ICDType.None,
                ICDTag.None,
                2.0);
        simulator.performActionWithoutTimeAdvance(characterId, explosion);
    }

    /** Dispatches Klee's classic typed offensive actions. */
    @Override
    public void onAction(
            CharacterActionRequest request,
            CombatSimulator simulator) {
        if (request == null) {
            throw new IllegalArgumentException("Klee action is required");
        }
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
                if (request.getSkillMode() != SkillActionMode.PRESS) {
                    throw new IllegalArgumentException(
                            "Klee Hold Skill is outside this slice");
                }
                jumpyDumpty(simulator);
                break;
            case BURST:
                sparksAndSplash(simulator);
                break;
            default:
                throw new IllegalArgumentException(
                        "Unsupported action for Klee: " + request.getKey());
        }
    }

    private void normalAttack(CombatSimulator simulator) {
        boolean c1Trigger = consumeC1Decision();
        double castTime = simulator.getCurrentTime();
        int step = normalAttackStep;
        queueCommand(simulator, new PendingCommand(
                castTime + NORMAL_RELEASE_FRAMES[step] * FRAME,
                CommandKind.NORMAL_RELEASE,
                step,
                0L,
                c1Trigger));
        normalAttackStep = (normalAttackStep + 1)
                % NORMAL_MULTIPLIERS.length;
        simulator.advanceTime(NORMAL_DURATION_FRAMES[step] * FRAME);
    }

    private void chargedAttack(CombatSimulator simulator) {
        boolean c1Trigger = consumeC1Decision();
        queueCommand(simulator, new PendingCommand(
                simulator.getCurrentTime() + 61.0 * FRAME,
                CommandKind.CHARGED_SNAPSHOT,
                0,
                0L,
                c1Trigger));
        simulator.advanceTime(113.0 * FRAME);
    }

    private void highPlunge(CombatSimulator simulator) {
        AttackAction plunge = attack(
                "Kaboom High Plunge",
                getTalentValue("High Plunge", 2.607632),
                Element.PYRO,
                StatType.PLUNGING_ATTACK_DMG_BONUS,
                ActionType.PLUNGE,
                ICDType.None,
                ICDTag.PlungeAttack,
                1.0);
        plunge.setAnimationDuration(1.0);
        simulator.performAction(characterId, plunge);
    }

    private void jumpyDumpty(CombatSimulator simulator) {
        boolean c1Trigger = consumeC1Decision();
        double castTime = simulator.getCurrentTime();
        StatsContainer snapshot = captureActionSnapshot(simulator);
        queueHit(simulator, new PendingHit(
                castTime + 71.0 * FRAME,
                HitKind.SKILL_BOUNCE,
                0L,
                snapshot));
        queueHit(simulator, new PendingHit(
                castTime + 240.0 * FRAME,
                HitKind.SKILL_MINE,
                0L,
                snapshot));
        queueHit(simulator, new PendingHit(
                castTime + 240.0 * FRAME,
                HitKind.SKILL_MINE,
                0L,
                snapshot));
        if (c1Trigger) {
            queueHit(simulator, new PendingHit(
                    castTime + 71.0 * FRAME,
                    HitKind.C1,
                    0L,
                    null));
        }
        queueCommand(simulator, new PendingCommand(
                castTime + 33.0 * FRAME,
                CommandKind.SKILL_COOLDOWN,
                0,
                0L,
                false));
        queueCommand(simulator, new PendingCommand(
                castTime + 171.0 * FRAME,
                CommandKind.SKILL_PARTICLES,
                0,
                0L,
                false));
        simulator.advanceTime(75.0 * FRAME);
    }

    private void sparksAndSplash(CombatSimulator simulator) {
        double c1Draw = constellation >= 1
                ? consumeDraw(c1DrawSource, "Klee C1") : Double.NaN;
        boolean[][] extraHits = consumeBurstOutcomes();
        boolean c1Trigger = applyC1Decision(c1Draw);
        double castTime = simulator.getCurrentTime();
        long generation = ++burstGeneration;
        burstInstance = new BurstInstance(
                generation,
                castTime + 746.0 * FRAME,
                extraHits,
                c1Trigger);
        if (constellation >= 6) {
            applyC6Buff(simulator, castTime);
        }
        queueCommand(simulator, new PendingCommand(
                castTime + 9.0 * FRAME,
                CommandKind.BURST_COOLDOWN,
                0,
                generation,
                false));
        queueCommand(simulator, new PendingCommand(
                castTime + 12.0 * FRAME,
                CommandKind.BURST_ENERGY,
                0,
                generation,
                false));
        queueCommand(simulator, new PendingCommand(
                castTime + 100.0 * FRAME,
                CommandKind.BURST_SNAPSHOT,
                0,
                generation,
                false));
        if (constellation >= 6) {
            for (int frame : C6_ENERGY_FRAMES) {
                queueCommand(simulator, new PendingCommand(
                        castTime + frame * FRAME,
                        CommandKind.C6_ENERGY,
                        0,
                        generation,
                        false));
            }
        }
        simulator.advanceTime(139.0 * FRAME);
    }

    private void applyC6Buff(
            CombatSimulator simulator,
            double castTime) {
        simulator.applyTeamBuffNoStack(new SimpleBuff(
                "Klee C6 Blazing Delight",
                BuffId.KLEE_C6_PYRO_DMG_BONUS,
                getTalentValue("C6 Buff Duration", 25.0),
                castTime,
                stats -> stats.add(
                        StatType.PYRO_DMG_BONUS,
                        getTalentValue("C6 Pyro DMG Bonus", 0.10)))
                .sourcedBy(characterId));
    }

    private boolean[][] consumeBurstOutcomes() {
        boolean[][] extraHits = new boolean[BURST_WAVE_FRAMES.length][2];
        for (int wave = 0; wave < BURST_WAVE_FRAMES.length; wave++) {
            double secondDraw = consumeDraw(
                    burstDrawSource, "Klee Burst 30% extra hit");
            double thirdDraw = consumeDraw(
                    burstDrawSource, "Klee Burst 50% extra hit");
            extraHits[wave][0] = secondDraw < 0.30;
            extraHits[wave][1] = thirdDraw < 0.50;
        }
        return extraHits;
    }

    private void captureBurstAndQueueHits(
            CombatSimulator simulator,
            long generation) {
        if (!isBurstGenerationActive(generation, simulator.getCurrentTime())) {
            return;
        }
        StatsContainer snapshot = captureActionSnapshot(simulator);
        burstInstance.snapshot = snapshot.merge(null);
        double castTime = burstInstance.expirationTime - 746.0 * FRAME;
        for (int wave = 0; wave < BURST_WAVE_FRAMES.length; wave++) {
            double waveTime = castTime + BURST_WAVE_FRAMES[wave] * FRAME;
            queueHit(simulator, new PendingHit(
                    waveTime,
                    HitKind.BURST,
                    generation,
                    snapshot));
            queueHit(simulator, new PendingHit(
                    waveTime + 12.0 * FRAME,
                    HitKind.BURST,
                    generation,
                    snapshot));
            if (burstInstance.extraHits[wave][0]) {
                queueHit(simulator, new PendingHit(
                        waveTime + 12.0 * FRAME,
                        HitKind.BURST,
                        generation,
                        snapshot));
            }
            queueHit(simulator, new PendingHit(
                    waveTime + 24.0 * FRAME,
                    HitKind.BURST,
                    generation,
                    snapshot));
            if (burstInstance.extraHits[wave][1]) {
                queueHit(simulator, new PendingHit(
                        waveTime + 24.0 * FRAME,
                        HitKind.BURST,
                        generation,
                        snapshot));
            }
        }
        if (burstInstance.c1Trigger) {
            queueHit(simulator, new PendingHit(
                    castTime + BURST_WAVE_FRAMES[0] * FRAME,
                    HitKind.C1,
                    generation,
                    null));
        }
    }

    private void onResolvedDamage(
            Character actor,
            AttackAction action,
            double time,
            CombatSimulator simulator) {
        if (simulator != initializedSimulator
                || actor != this
                || action == null) {
            return;
        }
        if (resolvingSkillMine && constellation >= 2) {
            c2ExpirationTime = time
                    + getTalentValue("C2 Duration", 10.0);
        }
        if (action.getActionType() != ActionType.NORMAL
                && action.getActionType() != ActionType.SKILL) {
            return;
        }
        if (time + EPSILON < nextA1ProcTime) {
            return;
        }
        double draw = consumeDraw(a1DrawSource, "Klee A1");
        if (draw >= 0.50) {
            return;
        }
        nextA1ProcTime = time + getTalentValue("A1 Gate", 4.0);
        if (time + EPSILON >= a1SparkExpirationTime) {
            a1SparkExpirationTime = time
                    + getTalentValue("A1 Spark Duration", 30.0);
        }
    }

    private boolean consumeC1Decision() {
        if (constellation < 1) {
            return false;
        }
        return applyC1Decision(consumeDraw(c1DrawSource, "Klee C1"));
    }

    private boolean applyC1Decision(double draw) {
        if (constellation < 1) {
            return false;
        }
        boolean triggered = draw < c1Chance;
        c1Chance = triggered ? 0.10 : Math.min(1.0, c1Chance + 0.08);
        return triggered;
    }

    private static double consumeDraw(
            DoubleSupplier source,
            String label) {
        double draw = source.getAsDouble();
        if (!Double.isFinite(draw) || draw < 0.0 || draw > 1.0) {
            throw new IllegalArgumentException(
                    label + " draw must be finite and in [0, 1]");
        }
        return draw;
    }

    private void queueHit(CombatSimulator simulator, PendingHit hit) {
        pendingHits.add(hit);
        scheduleHit(simulator, hit);
    }

    private void scheduleHit(CombatSimulator simulator, PendingHit hit) {
        schedule(simulator, hit.time, activeSim -> {
            if (!pendingHits.remove(hit)) {
                return;
            }
            resolveHit(activeSim, hit);
        });
    }

    private void resolveHit(CombatSimulator simulator, PendingHit hit) {
        AttackAction action;
        switch (hit.kind) {
            case NORMAL:
                action = attack(
                        "Kaboom N" + (hit.index + 1),
                        getTalentValue(
                                "N" + (hit.index + 1),
                                NORMAL_MULTIPLIERS[hit.index]),
                        Element.PYRO,
                        StatType.NORMAL_ATTACK_DMG_BONUS,
                        ActionType.NORMAL,
                        ICDType.Standard,
                        ICDTag.NormalAttack,
                        1.0);
                break;
            case CHARGED:
                action = attack(
                        "Kaboom Charged Attack",
                        getTalentValue("Charged Attack", 2.67512),
                        Element.PYRO,
                        StatType.CHARGED_ATTACK_DMG_BONUS,
                        ActionType.CHARGE,
                        ICDType.None,
                        ICDTag.ChargedAttack,
                        1.0);
                break;
            case SKILL_BOUNCE:
                action = attack(
                        "Jumpy Dumpty (Bounce)",
                        getTalentValue(
                                constellation >= 3
                                        ? "Jumpy Dumpty C3"
                                        : "Jumpy Dumpty",
                                constellation >= 3 ? 1.904 : 1.6184),
                        Element.PYRO,
                        StatType.SKILL_DMG_BONUS,
                        ActionType.SKILL,
                        ICDType.Standard,
                        ICDTag.NormalAttack,
                        1.0);
                break;
            case SKILL_MINE:
                action = attack(
                        "Jumpy Dumpty (Mine)",
                        getTalentValue(
                                constellation >= 3
                                        ? "Jumpy Dumpty Mine C3"
                                        : "Jumpy Dumpty Mine",
                                constellation >= 3 ? 0.656 : 0.5576),
                        Element.PYRO,
                        StatType.SKILL_DMG_BONUS,
                        ActionType.SKILL,
                        ICDType.Standard,
                        ICDTag.NormalAttack,
                        1.0);
                break;
            case BURST:
                if (!isBurstGenerationActive(
                        hit.generation, simulator.getCurrentTime())) {
                    return;
                }
                action = burstAttack("Sparks 'n' Splash");
                break;
            case C1:
                if (hit.generation != 0L
                        && !isBurstGenerationActive(
                                hit.generation, simulator.getCurrentTime())) {
                    return;
                }
                action = attack(
                        "Sparks 'n' Splash (C1)",
                        getTalentValue("C1 Burst Factor", 1.20)
                                * burstMultiplier(),
                        Element.PYRO,
                        StatType.BURST_DMG_BONUS,
                        ActionType.BURST,
                        ICDType.Standard,
                        ICDTag.ElementalBurst,
                        1.0);
                break;
            default:
                throw new IllegalStateException("Unknown Klee hit kind");
        }
        if (hit.snapshot != null) {
            action.setStatSnapshot(hit.snapshot);
        }
        resolvingSkillMine = hit.kind == HitKind.SKILL_MINE;
        try {
            simulator.performActionWithoutTimeAdvance(characterId, action);
        } finally {
            resolvingSkillMine = false;
        }
    }

    private AttackAction burstAttack(String displayName) {
        return attack(
                displayName,
                burstMultiplier(),
                Element.PYRO,
                StatType.BURST_DMG_BONUS,
                ActionType.BURST,
                ICDType.Standard,
                ICDTag.ElementalBurst,
                1.0);
    }

    private double burstMultiplier() {
        return getTalentValue(
                constellation >= 5
                        ? "Sparks n Splash C5"
                        : "Sparks n Splash",
                constellation >= 5 ? 0.8528 : 0.72488);
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
        schedule(simulator, command.time, activeSim -> {
            if (!pendingCommands.remove(command)) {
                return;
            }
            switch (command.kind) {
                case NORMAL_RELEASE:
                    queueHit(activeSim, new PendingHit(
                            activeSim.getCurrentTime() + 10.0 * FRAME,
                            HitKind.NORMAL,
                            command.index,
                            0L,
                            captureActionSnapshot(activeSim)));
                    if (command.c1Trigger) {
                        queueHit(activeSim, new PendingHit(
                                activeSim.getCurrentTime() + 10.0 * FRAME,
                                HitKind.C1,
                                0L,
                                null));
                    }
                    break;
                case CHARGED_SNAPSHOT:
                    StatsContainer chargedSnapshot =
                            captureActionSnapshot(activeSim);
                    a1SparkExpirationTime = Double.NEGATIVE_INFINITY;
                    queueHit(activeSim, new PendingHit(
                            activeSim.getCurrentTime() + 25.0 * FRAME,
                            HitKind.CHARGED,
                            0L,
                            chargedSnapshot));
                    if (command.c1Trigger) {
                        queueHit(activeSim, new PendingHit(
                                activeSim.getCurrentTime() + 25.0 * FRAME,
                                HitKind.C1,
                                0L,
                                null));
                    }
                    break;
                case SKILL_COOLDOWN:
                    markSkillUsed(
                            activeSim.getCurrentTime(),
                            activeSim.getApplicableBuffs(this));
                    break;
                case SKILL_PARTICLES:
                    activeSim.getEnergyDistributor().distributeParticles(
                            Element.PYRO, 4.0, ParticleType.PARTICLE);
                    break;
                case BURST_COOLDOWN:
                    if (isCurrentBurstGeneration(command.generation)) {
                        markBurstCooldownUsed(
                                activeSim.getCurrentTime(),
                                activeSim.getApplicableBuffs(this));
                    }
                    break;
                case BURST_ENERGY:
                    if (isCurrentBurstGeneration(command.generation)) {
                        spendBurstEnergy(activeSim.getCurrentTime());
                    }
                    break;
                case BURST_SNAPSHOT:
                    captureBurstAndQueueHits(activeSim, command.generation);
                    break;
                case C6_ENERGY:
                    if (isBurstGenerationActive(
                            command.generation,
                            activeSim.getCurrentTime())) {
                        distributeC6Energy(activeSim);
                    }
                    break;
                default:
                    throw new IllegalStateException(
                            "Unknown Klee command kind");
            }
        });
    }

    private void distributeC6Energy(CombatSimulator simulator) {
        double amount = getTalentValue("C6 Flat Energy", 3.0);
        for (Character member : simulator.getPartyMembers()) {
            if (member != this) {
                member.receiveFlatEnergy(amount);
            }
        }
    }

    private boolean isBurstActive(double currentTime) {
        return burstInstance != null
                && burstInstance.active
                && currentTime < burstInstance.expirationTime - EPSILON;
    }

    private boolean isCurrentBurstGeneration(long generation) {
        return burstInstance != null
                && burstInstance.generation == generation;
    }

    private boolean isBurstGenerationActive(
            long generation,
            double currentTime) {
        return isCurrentBurstGeneration(generation)
                && isBurstActive(currentTime);
    }

    private StatsContainer captureActionSnapshot(CombatSimulator simulator) {
        double currentTime = simulator.getCurrentTime();
        StatsContainer stats = getEffectiveStats(currentTime);
        for (Buff buff : simulator.getApplicableBuffs(this)) {
            if (!buff.isExpired(currentTime)) {
                buff.apply(stats, currentTime);
            }
        }
        return stats;
    }

    private static AttackAction attack(
            String name,
            double multiplier,
            Element element,
            StatType bonusStat,
            ActionType actionType,
            ICDType icdType,
            ICDTag icdTag,
            double gaugeUnits) {
        AttackAction action = new AttackAction(
                name,
                multiplier,
                element,
                StatType.BASE_ATK,
                bonusStat,
                0.0,
                actionType);
        action.setICD(icdType, icdTag, gaugeUnits);
        return action;
    }

    private static void schedule(
            CombatSimulator simulator,
            double time,
            Consumer<CombatSimulator> effect) {
        simulator.registerEvent(new SimpleTimerEvent(time, 1.0) {
            @Override
            public void onTick(CombatSimulator activeSim) {
                finish();
                effect.accept(activeSim);
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
        SKILL_BOUNCE,
        SKILL_MINE,
        BURST,
        C1
    }

    private enum CommandKind {
        NORMAL_RELEASE,
        CHARGED_SNAPSHOT,
        SKILL_COOLDOWN,
        SKILL_PARTICLES,
        BURST_COOLDOWN,
        BURST_ENERGY,
        BURST_SNAPSHOT,
        C6_ENERGY
    }

    /** Immutable delayed hit description. */
    private static final class PendingHit {
        private final double time;
        private final HitKind kind;
        private final int index;
        private final long generation;
        private final StatsContainer snapshot;

        private PendingHit(
                double time,
                HitKind kind,
                long generation,
                StatsContainer snapshot) {
            this(time, kind, 0, generation, snapshot);
        }

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
            this.snapshot = snapshot == null ? null : snapshot.merge(null);
        }

        private PendingHit copy() {
            return new PendingHit(time, kind, index, generation, snapshot);
        }
    }

    /** Immutable delayed non-damage event description. */
    private static final class PendingCommand {
        private final double time;
        private final CommandKind kind;
        private final int index;
        private final long generation;
        private final boolean c1Trigger;

        private PendingCommand(
                double time,
                CommandKind kind,
                int index,
                long generation,
                boolean c1Trigger) {
            this.time = time;
            this.kind = kind;
            this.index = index;
            this.generation = generation;
            this.c1Trigger = c1Trigger;
        }

        private PendingCommand copy() {
            return new PendingCommand(
                    time, kind, index, generation, c1Trigger);
        }
    }

    /** Mutable state for one non-overlapping Burst instance. */
    private static final class BurstInstance {
        private final long generation;
        private final double expirationTime;
        private final boolean[][] extraHits;
        private final boolean c1Trigger;
        private boolean active = true;
        private StatsContainer snapshot;

        private BurstInstance(
                long generation,
                double expirationTime,
                boolean[][] extraHits,
                boolean c1Trigger) {
            this.generation = generation;
            this.expirationTime = expirationTime;
            this.extraHits = copyOutcomes(extraHits);
            this.c1Trigger = c1Trigger;
        }

        private BurstInstance copy() {
            BurstInstance copy = new BurstInstance(
                    generation, expirationTime, extraHits, c1Trigger);
            copy.active = active;
            copy.snapshot = snapshot == null ? null : snapshot.merge(null);
            return copy;
        }

        private static boolean[][] copyOutcomes(boolean[][] source) {
            boolean[][] copy = new boolean[source.length][2];
            for (int index = 0; index < source.length; index++) {
                copy[index][0] = source[index][0];
                copy[index][1] = source[index][1];
            }
            return copy;
        }
    }

    /** Immutable Klee-owned simulator state. */
    private static final class KleeState implements State {
        private final int normalAttackStep;
        private final long burstGeneration;
        private final List<PendingHit> pendingHits;
        private final List<PendingCommand> pendingCommands;
        private final BurstInstance burstInstance;
        private final double a1SparkExpirationTime;
        private final double nextA1ProcTime;
        private final double c1Chance;
        private final double c2ExpirationTime;

        private KleeState(
                int normalAttackStep,
                long burstGeneration,
                List<PendingHit> pendingHits,
                List<PendingCommand> pendingCommands,
                BurstInstance burstInstance,
                double a1SparkExpirationTime,
                double nextA1ProcTime,
                double c1Chance,
                double c2ExpirationTime) {
            this.normalAttackStep = normalAttackStep;
            this.burstGeneration = burstGeneration;
            this.pendingHits = copyHits(pendingHits);
            this.pendingCommands = copyCommands(pendingCommands);
            this.burstInstance = burstInstance == null
                    ? null : burstInstance.copy();
            this.a1SparkExpirationTime = a1SparkExpirationTime;
            this.nextA1ProcTime = nextA1ProcTime;
            this.c1Chance = c1Chance;
            this.c2ExpirationTime = c2ExpirationTime;
        }
    }
}

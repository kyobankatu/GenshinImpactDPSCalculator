package model.character;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
 * Jahoda's sourced single-target offensive model.
 *
 * <p>The model covers bow attacks, Shadow Pursuit absorption, Flask discharge,
 * Ascendant-Gleam Meowballs, Burst robots, offensive A1/C1/C2/C3/C4/C5/C6
 * effects, particles, and reconstructable delayed work. Movement/contact
 * geometry, Smoke Bomb fallback, healing/current-HP checks, gadget targeting,
 * hitlag, and multi-target selection deliberately fail closed.</p>
 */
public final class Jahoda extends Character implements
        SimulatorInitializedCharacterEffect,
        SnapshotAwareCharacterEffect {
    private static final double FRAME = 1.0 / 60.0;
    private static final double EPSILON = 1e-9;
    private static final int[] NORMAL_DURATIONS = { 35, 52, 99 };
    private static final int[][] NORMAL_HIT_FRAMES = {
            { 24 }, { 25, 39 }, { 50 }
    };
    private static final double[][] NORMAL_T9 = {
            { 0.765636 }, { 0.353320, 0.353320 }, { 0.940606 }
    };
    private static final Element[] ABSORB_PRIORITY = {
            Element.PYRO,
            Element.HYDRO,
            Element.ELECTRO,
            Element.CRYO
    };

    private final DoubleSupplier c1Random;
    private CombatSimulator initializedSimulator;
    private int normalAttackStep;
    private boolean shadowPursuit;
    private long skillGeneration;
    private double shadowStartTime = Double.NEGATIVE_INFINITY;
    private Element flaskElement;
    private int flaskGauge;
    private boolean flaskParticleIssued;
    private double nextMeowballEnergyTime = Double.NEGATIVE_INFINITY;
    private long burstGeneration;
    private double burstEndTime = Double.NEGATIVE_INFINITY;
    private double robotIntervalFrames = 140.0;
    private double robotDamageMultiplier = 1.0;
    private int robotCount = 2;
    private final double[] robotLastApplication = new double[4];
    private final int[] robotSuppressedHits = new int[4];
    private double c1LastApplication = Double.NEGATIVE_INFINITY;
    private int c1SuppressedHits;
    private long eventGeneration;
    private AttackAction resolvingAction;
    private boolean resolvingFlask;
    private boolean resolvingMeowball;
    private List<PendingHit> pendingHits = new ArrayList<>();
    private List<PendingCommand> pendingCommands = new ArrayList<>();

    /** Constructs repository-default C6 Jahoda with runtime C1 draws. */
    public Jahoda(Weapon weapon, ArtifactSet artifacts) {
        this(weapon, artifacts, TalentDataManager.getInstance(), 6, Math::random);
    }

    /** Constructs Jahoda at an explicit constellation. */
    public Jahoda(
            Weapon weapon,
            ArtifactSet artifacts,
            int constellation) {
        this(
                weapon,
                artifacts,
                TalentDataManager.getInstance(),
                constellation,
                Math::random);
    }

    /**
     * Constructs Jahoda with injectable data and C1 randomness.
     *
     * @param weapon equipped bow, or {@code null}
     * @param artifacts equipped artifact set, or {@code null}
     * @param talentData character-data source
     * @param constellation constellation in {@code [0, 6]}
     * @param c1Random C1 bounce draw source in {@code [0, 1)}
     */
    public Jahoda(
            Weapon weapon,
            ArtifactSet artifacts,
            TalentDataSource talentData,
            int constellation,
            DoubleSupplier c1Random) {
        super(talentData);
        if (constellation < 0 || constellation > 6) {
            throw new IllegalArgumentException(
                    "Jahoda constellation must be between 0 and 6");
        }
        this.c1Random = Objects.requireNonNull(
                c1Random, "Jahoda C1 random source is required");
        name = "Jahoda";
        characterId = CharacterId.JAHODA;
        element = Element.ANEMO;
        this.weapon = weapon;
        this.artifacts = new ArtifactSet[] { artifacts };
        this.constellation = constellation;
        baseStats.set(StatType.BASE_HP,
                getTalentValue("Base HP", 9646.0));
        baseStats.set(StatType.BASE_ATK,
                getTalentValue("Base ATK", 223.0));
        baseStats.set(StatType.BASE_DEF,
                getTalentValue("Base DEF", 580.0));
        baseStats.add(StatType.HEALING_BONUS,
                getTalentValue("Ascension Healing Bonus", 0.1846));
        setSkillCD(getTalentValue("Skill Cooldown", 15.0));
        setBurstCD(getTalentValue("Burst Cooldown", 18.0));
        resetRobotIcd();
    }

    /** Binds accepted-hit effects and delayed work to one simulator. */
    @Override
    public void initializeForSimulator(CombatSimulator simulator) {
        if (simulator == null) {
            throw new IllegalArgumentException("Jahoda simulator is required");
        }
        if (!simulator.getPartyMembers().contains(this)) {
            throw new IllegalArgumentException(
                    "Jahoda must belong to the simulator party");
        }
        if (initializedSimulator != null
                && initializedSimulator != simulator) {
            throw new IllegalStateException(
                    "Jahoda cannot be reused across simulators");
        }
        if (initializedSimulator == simulator) {
            return;
        }
        initializedSimulator = simulator;
        simulator.addDamageListener((actor, action, damage, time) -> {
            if (actor != this || action != resolvingAction || damage <= 0.0) {
                return;
            }
            if (resolvingFlask && !flaskParticleIssued) {
                flaskParticleIssued = true;
                queueCommand(simulator, new PendingCommand(
                        time + getTalentValue(
                                "Particle Travel Frames", 100.0) * FRAME,
                        CommandKind.PARTICLE,
                        skillGeneration,
                        0,
                        getTalentValue("Particle Count", 4.0),
                        null));
            }
            if (resolvingMeowball) {
                onAcceptedMeowball(simulator, time);
            }
        });
    }

    /** Captures Jahoda-owned gates and all reconstructable delayed work. */
    @Override
    public State captureCharacterState() {
        return new JahodaState(
                this,
                normalAttackStep,
                shadowPursuit,
                skillGeneration,
                shadowStartTime,
                flaskElement,
                flaskGauge,
                flaskParticleIssued,
                nextMeowballEnergyTime,
                burstGeneration,
                burstEndTime,
                robotIntervalFrames,
                robotDamageMultiplier,
                robotCount,
                robotLastApplication,
                robotSuppressedHits,
                c1LastApplication,
                c1SuppressedHits,
                eventGeneration,
                pendingHits,
                pendingCommands);
    }

    /** Accepts only state captured from this exact Jahoda instance. */
    @Override
    public boolean acceptsCharacterState(State state) {
        return state instanceof JahodaState
                && ((JahodaState) state).owner == this;
    }

    /** Restores owner state and invalidates callbacks from the old timeline. */
    @Override
    public void restoreCharacterState(
            State state,
            CombatSimulator simulator) {
        if (!acceptsCharacterState(state)) {
            throw new IllegalArgumentException("Unexpected Jahoda state");
        }
        initializeForSimulator(simulator);
        JahodaState restored = (JahodaState) state;
        normalAttackStep = restored.normalAttackStep;
        shadowPursuit = restored.shadowPursuit;
        skillGeneration = restored.skillGeneration;
        shadowStartTime = restored.shadowStartTime;
        flaskElement = restored.flaskElement;
        flaskGauge = restored.flaskGauge;
        flaskParticleIssued = restored.flaskParticleIssued;
        nextMeowballEnergyTime = restored.nextMeowballEnergyTime;
        burstGeneration = restored.burstGeneration;
        burstEndTime = restored.burstEndTime;
        robotIntervalFrames = restored.robotIntervalFrames;
        robotDamageMultiplier = restored.robotDamageMultiplier;
        robotCount = restored.robotCount;
        System.arraycopy(restored.robotLastApplication, 0,
                robotLastApplication, 0, robotLastApplication.length);
        System.arraycopy(restored.robotSuppressedHits, 0,
                robotSuppressedHits, 0, robotSuppressedHits.length);
        c1LastApplication = restored.c1LastApplication;
        c1SuppressedHits = restored.c1SuppressedHits;
        eventGeneration = Math.max(eventGeneration, restored.eventGeneration) + 1L;
        pendingHits = copyHits(restored.pendingHits);
        pendingCommands = copyCommands(restored.pendingCommands);
        resolvingAction = null;
        resolvingFlask = false;
        resolvingMeowball = false;
        for (PendingHit hit : new ArrayList<>(pendingHits)) {
            scheduleHit(simulator, hit);
        }
        for (PendingCommand command : new ArrayList<>(pendingCommands)) {
            scheduleCommand(simulator, command);
        }
    }

    /** Jahoda contributes one party Moonsign level. */
    @Override
    public boolean isLunarCharacter() {
        return true;
    }

    /** Returns Jahoda's 70-Energy Burst cost. */
    @Override
    public double getEnergyCost() {
        return getTalentValue("Energy Cost", 70.0);
    }

    /** Applies no unconditional offensive passive stats. */
    @Override
    public void applyPassive(StatsContainer stats) {
        // Healing and HP-gated A4 behavior are outside this bounded slice.
    }

    /** Allows the sourced Skill recast while Shadow Pursuit is active. */
    @Override
    public double getSkillCDRemaining(double currentTime) {
        if (shadowPursuit) {
            return 0.0;
        }
        return super.getSkillCDRemaining(currentTime);
    }

    /** Returns whether Jahoda is in the owner-only Shadow Pursuit state. */
    public boolean isShadowPursuitActive() {
        return shadowPursuit;
    }

    /** Returns the current fixed-target Flask gauge in {@code [0, 100]}. */
    public int getFlaskGauge() {
        return flaskGauge;
    }

    /** Returns the absorbed Flask element, or {@code null}. */
    public Element getFlaskElement() {
        return flaskElement;
    }

    /** Returns unresolved Jahoda-owned hits. */
    public int getPendingHitCount() {
        return pendingHits.size();
    }

    /** Returns unresolved Jahoda-owned state commands. */
    public int getPendingCommandCount() {
        return pendingCommands.size();
    }

    /** Reports excluded healing/current-HP behavior. */
    public boolean isHealingRepresented() {
        return false;
    }

    /** Reports excluded movement, contact, and hitlag behavior. */
    public boolean isMovementAndHitlagRepresented() {
        return false;
    }

    /** Reports excluded gadget and multi-target geometry behavior. */
    public boolean isGadgetAndMultiTargetRepresented() {
        return false;
    }

    /** Dispatches Jahoda's bounded fixed-target action set. */
    @Override
    public void onAction(
            CharacterActionRequest request,
            CombatSimulator simulator) {
        if (request == null) {
            throw new IllegalArgumentException("Jahoda action is required");
        }
        initializeForSimulator(simulator);
        if (request.getKey() == CharacterActionKey.SKILL
                && request.getSkillMode() != SkillActionMode.PRESS) {
            throw new IllegalArgumentException(
                    "Jahoda has no supported Hold Skill mode");
        }
        if (shadowPursuit && request.getKey() != CharacterActionKey.SKILL) {
            throw new IllegalStateException(
                    "Shadow Pursuit can only be ended by a Skill recast");
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
            case SKILL:
                skill(simulator);
                break;
            case BURST:
                burst(simulator);
                break;
            default:
                throw new IllegalArgumentException(
                        "Unsupported fixed-target action for Jahoda: "
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
                    Element.PHYSICAL,
                    0L,
                    null));
        }
        normalAttackStep = (normalAttackStep + 1)
                % NORMAL_DURATIONS.length;
        simulator.advanceTime(NORMAL_DURATIONS[step] * FRAME);
    }

    private void chargedAttack(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        queueHit(simulator, new PendingHit(
                castTime + 95.0 * FRAME,
                HitKind.CHARGED,
                0,
                0,
                Element.ANEMO,
                0L,
                null));
        simulator.advanceTime(85.0 * FRAME);
    }

    private void skill(CombatSimulator simulator) {
        if (shadowPursuit) {
            drainFlask(simulator, skillGeneration, false);
            simulator.advanceTime(43.0 * FRAME);
            return;
        }
        double castTime = simulator.getCurrentTime();
        skillGeneration++;
        long generation = skillGeneration;
        flaskElement = null;
        flaskGauge = 0;
        flaskParticleIssued = false;
        queueCommand(simulator, new PendingCommand(
                castTime + getTalentValue(
                        "Skill Windup Frames", 30.0) * FRAME,
                CommandKind.ENTER_PURSUIT,
                generation,
                0,
                0.0,
                null));
        simulator.advanceTime(42.0 * FRAME);
    }

    private void enterPursuit(
            CombatSimulator simulator,
            long generation,
            double time) {
        if (generation != skillGeneration) {
            return;
        }
        shadowPursuit = true;
        shadowStartTime = time;
        queueCommand(simulator, new PendingCommand(
                time + getTalentValue(
                        "First Flask Fill Frames", 7.0) * FRAME,
                CommandKind.FLASK_FILL,
                generation,
                0,
                0.0,
                null));
        queueCommand(simulator, new PendingCommand(
                time + getTalentValue(
                        "Shadow Pursuit Duration Frames", 334.0) * FRAME,
                CommandKind.NATURAL_DRAIN,
                generation,
                0,
                0.0,
                null));
    }

    private void fillFlask(
            CombatSimulator simulator,
            long generation,
            double time) {
        if (!shadowPursuit || generation != skillGeneration) {
            return;
        }
        Element detected = detectAbsorbableAura(simulator);
        if (detected != null) {
            int strong = (int) Math.round(getTalentValue(
                    "Flask Fill Strong", 20.0));
            int weak = (int) Math.round(getTalentValue(
                    "Flask Fill Weak", 10.0));
            if (flaskElement == null) {
                flaskElement = detected;
                flaskGauge += strong;
            } else if (flaskElement == detected) {
                flaskGauge += strong;
            } else {
                flaskGauge += weak;
            }
            int maximum = (int) Math.round(getTalentValue(
                    "Flask Gauge Maximum", 100.0));
            if (flaskGauge >= maximum) {
                flaskGauge = maximum;
                queueCommand(simulator, new PendingCommand(
                        time + 4.0 * FRAME,
                        CommandKind.FULL_DRAIN,
                        generation,
                        0,
                        0.0,
                        null));
                return;
            }
        }
        queueCommand(simulator, new PendingCommand(
                time + getTalentValue(
                        "Flask Fill Interval Frames", 30.0) * FRAME,
                CommandKind.FLASK_FILL,
                generation,
                0,
                0.0,
                null));
    }

    private void drainFlask(
            CombatSimulator simulator,
            long generation,
            boolean full) {
        if (!shadowPursuit || generation != skillGeneration) {
            return;
        }
        double drainTime = simulator.getCurrentTime();
        shadowPursuit = false;
        shadowStartTime = Double.NEGATIVE_INFINITY;
        pendingCommands.removeIf(command ->
                command.generation == generation
                        && isPursuitCommand(command.kind));
        markSkillUsed(drainTime, simulator.getApplicableBuffs(this));
        queueHit(simulator, new PendingHit(
                drainTime + (full ? 2.0 : 4.0) * FRAME,
                full ? HitKind.FILLED_FLASK : HitKind.UNFILLED_FLASK,
                0,
                0,
                Element.ANEMO,
                generation,
                captureLiveStats(drainTime)));
        if (full
                && simulator.getMoonsign()
                        == CombatSimulator.Moonsign.ASCENDANT_GLEAM) {
            applyC6(simulator, drainTime);
            int ticks = 10;
            double first = getTalentValue(
                    "Meowball First Frames", 129.0);
            double interval = getTalentValue(
                    "Meowball Interval Frames", 116.0);
            double travel = getTalentValue(
                    "Meowball Travel Frames", 13.0);
            for (int index = 0; index < ticks; index++) {
                queueHit(simulator, new PendingHit(
                        drainTime + (first + index * interval + travel) * FRAME,
                        HitKind.MEOWBALL,
                        index,
                        0,
                        flaskElement,
                        generation,
                        null));
            }
        }
    }

    private void burst(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        burstGeneration++;
        long generation = burstGeneration;
        burstEndTime = castTime + getTalentValue(
                "Burst Duration Frames", 790.0) * FRAME;
        configureRobotEffects(simulator);
        resetRobotIcd();
        StatsContainer snapshot = captureLiveStats(castTime);
        queueCommand(simulator, new PendingCommand(
                castTime + FRAME,
                CommandKind.BURST_COOLDOWN,
                generation,
                0,
                0.0,
                null));
        queueCommand(simulator, new PendingCommand(
                castTime + 13.0 * FRAME,
                CommandKind.BURST_ENERGY,
                generation,
                0,
                0.0,
                null));
        queueHit(simulator, new PendingHit(
                castTime + 43.0 * FRAME,
                HitKind.BURST_CAST,
                0,
                0,
                Element.ANEMO,
                generation,
                snapshot));
        if (simulator.getMoonsign()
                == CombatSimulator.Moonsign.ASCENDANT_GLEAM) {
            for (int robot = 0; robot < robotCount; robot++) {
                queueCommand(simulator, new PendingCommand(
                        castTime + (getTalentValue(
                                "Robot First Absorb Frames", 6.0)
                                + robot * getTalentValue(
                                        "Robot Absorb Interval Frames", 41.0))
                                * FRAME,
                        CommandKind.ROBOT_ABSORB,
                        generation,
                        robot,
                        0.0,
                        null));
            }
        }
        simulator.advanceTime(48.0 * FRAME);
    }

    private void configureRobotEffects(CombatSimulator simulator) {
        robotCount = 2;
        robotIntervalFrames = getTalentValue(
                "Robot Attack Interval Frames", 140.0);
        robotDamageMultiplier = 1.0;
        List<Element> effects = selectA1Elements(simulator);
        for (Element selected : effects) {
            if (selected == Element.PYRO) {
                robotDamageMultiplier *= getTalentValue(
                        "A1 Pyro Multiplier", 1.3);
            } else if (selected == Element.ELECTRO) {
                robotCount++;
            } else if (selected == Element.CRYO) {
                robotIntervalFrames *= getTalentValue(
                        "A1 Cryo Interval Multiplier", 0.9);
            }
        }
    }

    private List<Element> selectA1Elements(CombatSimulator simulator) {
        Map<Element, Integer> counts = new EnumMap<>(Element.class);
        for (Element candidate : ABSORB_PRIORITY) {
            counts.put(candidate, 0);
        }
        for (Character member : simulator.getPartyMembers()) {
            if (counts.containsKey(member.getElement())) {
                counts.put(member.getElement(),
                        counts.get(member.getElement()) + 1);
            }
        }
        List<Element> selected = new ArrayList<>();
        int limit = constellation >= 2
                && simulator.getMoonsign()
                        == CombatSimulator.Moonsign.ASCENDANT_GLEAM
                ? 2 : 1;
        for (int selection = 0; selection < limit; selection++) {
            Element best = null;
            int bestCount = 0;
            for (Element candidate : ABSORB_PRIORITY) {
                int count = counts.get(candidate);
                if (!selected.contains(candidate) && count > bestCount) {
                    best = candidate;
                    bestCount = count;
                }
            }
            if (best == null) {
                break;
            }
            selected.add(best);
        }
        return selected;
    }

    private void absorbRobot(
            CombatSimulator simulator,
            PendingCommand command) {
        if (command.generation != burstGeneration
                || command.time + EPSILON >= burstEndTime) {
            return;
        }
        Element absorbed = detectAbsorbableAura(simulator);
        if (absorbed == null) {
            queueCommand(simulator, new PendingCommand(
                    command.time
                            + Math.floor(robotIntervalFrames / 3.0) * FRAME,
                    CommandKind.ROBOT_ABSORB,
                    command.generation,
                    command.index,
                    0.0,
                    null));
            return;
        }
        if (constellation >= 4) {
            receiveFlatEnergy(getTalentValue("C4 Flat Energy", 4.0));
        }
        queueHit(simulator, new PendingHit(
                command.time + getTalentValue(
                        "Robot First Hit Frames", 41.0) * FRAME,
                HitKind.ROBOT,
                command.index,
                0,
                absorbed,
                command.generation,
                captureLiveStats(command.time)));
    }

    private void onAcceptedMeowball(
            CombatSimulator simulator,
            double hitTime) {
        if (hitTime + EPSILON >= nextMeowballEnergyTime) {
            receiveFlatEnergy(getTalentValue(
                    "Meowball Flat Energy", 2.0));
            nextMeowballEnergyTime = hitTime + getTalentValue(
                    "Meowball Energy Cooldown", 3.5);
        }
        if (constellation < 1) {
            return;
        }
        double draw = c1Random.getAsDouble();
        if (!Double.isFinite(draw) || draw < 0.0 || draw >= 1.0) {
            throw new IllegalStateException(
                    "Jahoda C1 random draw must be in [0, 1)");
        }
        if (draw < getTalentValue("C1 Bounce Chance", 0.5)) {
            queueHit(simulator, new PendingHit(
                    hitTime + (getTalentValue(
                            "C1 Bounce Frames", 32.0)
                            + getTalentValue(
                                    "Meowball Travel Frames", 13.0)) * FRAME,
                    HitKind.C1_BOUNCE,
                    0,
                    0,
                    flaskElement,
                    skillGeneration,
                    null));
        }
    }

    private void applyC6(CombatSimulator simulator, double currentTime) {
        if (constellation < 6) {
            return;
        }
        double duration = getTalentValue("C6 Duration", 20.0);
        double critRate = getTalentValue("C6 Crit Rate", 0.05);
        double critDamage = getTalentValue("C6 Crit Damage", 0.40);
        for (Character member : simulator.getPartyMembers()) {
            if (!member.isLunarCharacter()) {
                continue;
            }
            member.getActiveBuffs().removeIf(buff ->
                    buff instanceof JahodaC6Buff
                            && ((JahodaC6Buff) buff).owner == this);
            member.addBuff(new JahodaC6Buff(
                    this,
                    duration,
                    currentTime,
                    critRate,
                    critDamage));
        }
    }

    private Element detectAbsorbableAura(CombatSimulator simulator) {
        if (simulator.getEnemy() == null) {
            return null;
        }
        for (Element candidate : ABSORB_PRIORITY) {
            if (simulator.getEnemy().getAuraUnits(
                    candidate, simulator.getCurrentTime()) > 0.0) {
                return candidate;
            }
        }
        return null;
    }

    private void resolveHit(CombatSimulator simulator, PendingHit hit) {
        switch (hit.kind) {
            case NORMAL:
                performHit(
                        simulator,
                        hit,
                        "Strike While the Arrow's Hot N"
                                + (hit.index + 1)
                                + (hit.subIndex > 0
                                        ? "-" + (hit.subIndex + 1) : ""),
                        NORMAL_T9[hit.index][hit.subIndex],
                        StatType.NORMAL_ATTACK_DMG_BONUS,
                        ActionType.NORMAL,
                        ICDType.None,
                        ICDTag.None,
                        0.0,
                        false,
                        false);
                break;
            case CHARGED:
                performHit(
                        simulator,
                        hit,
                        "Fully-Charged Aimed Shot",
                        getTalentValue("Fully-Charged Aimed Shot", 2.108),
                        StatType.CHARGED_ATTACK_DMG_BONUS,
                        ActionType.CHARGE,
                        ICDType.None,
                        ICDTag.None,
                        1.0,
                        false,
                        false);
                break;
            case UNFILLED_FLASK:
                performFlask(simulator, hit, false);
                break;
            case FILLED_FLASK:
                performFlask(simulator, hit, true);
                break;
            case MEOWBALL:
                if (hit.generation == skillGeneration) {
                    performHit(
                            simulator,
                            hit,
                            "Meowball",
                            skillValue("Meowball", 2.176, 2.560),
                            StatType.SKILL_DMG_BONUS,
                            ActionType.SKILL,
                            ICDType.None,
                            ICDTag.None,
                            1.0,
                            false,
                            true);
                }
                break;
            case C1_BOUNCE:
                performHit(
                        simulator,
                        hit,
                        "Meowball C1 Bounce",
                        skillValue("Meowball", 2.176, 2.560),
                        StatType.SKILL_DMG_BONUS,
                        ActionType.SKILL,
                        ICDType.None,
                        ICDTag.None,
                        nextC1Gauge(hit.time),
                        false,
                        false);
                break;
            case BURST_CAST:
                performHit(
                        simulator,
                        hit,
                        "Hidden Aces: Seven Tools of the Hunter",
                        burstValue("Hidden Aces", 3.5224, 4.144),
                        StatType.BURST_DMG_BONUS,
                        ActionType.BURST,
                        ICDType.None,
                        ICDTag.None,
                        1.0,
                        false,
                        false);
                break;
            case ROBOT:
                if (hit.generation == burstGeneration
                        && hit.time + EPSILON < burstEndTime) {
                    performHit(
                            simulator,
                            hit,
                            "Purrsonal Coordinated Assistance Robot",
                            burstValue("Robot", 0.293529, 0.345328)
                                    * robotDamageMultiplier,
                            StatType.BURST_DMG_BONUS,
                            ActionType.BURST,
                            ICDType.None,
                            ICDTag.ElementalBurst,
                            nextRobotGauge(hit.element, hit.time),
                            false,
                            false);
                    queueHit(simulator, new PendingHit(
                            hit.time + robotIntervalFrames * FRAME,
                            HitKind.ROBOT,
                            hit.index,
                            0,
                            hit.element,
                            hit.generation,
                            hit.snapshot));
                }
                break;
            default:
                throw new IllegalStateException(
                        "Unknown Jahoda hit kind " + hit.kind);
        }
    }

    private void performFlask(
            CombatSimulator simulator,
            PendingHit hit,
            boolean full) {
        performHit(
                simulator,
                hit,
                full ? "Filled Treasure Flask" : "Unfilled Treasure Flask",
                skillValue(
                        full ? "Filled Treasure Flask"
                                : "Unfilled Treasure Flask",
                        full ? 3.604 : 3.2436,
                        full ? 4.24 : 3.816),
                StatType.SKILL_DMG_BONUS,
                ActionType.SKILL,
                ICDType.Standard,
                ICDTag.ElementalSkill,
                1.0,
                true,
                false);
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
            double gauge,
            boolean flask,
            boolean meowball) {
        if (simulator.getEnemy() == null || hit.element == null) {
            return;
        }
        AttackAction action = new AttackAction(
                displayName,
                multiplier,
                hit.element,
                StatType.BASE_ATK,
                bonusStat,
                0.0,
                actionType);
        action.setICD(icdType, icdTag, gauge);
        action.setCountsAsSkillDmg(actionType == ActionType.SKILL);
        action.setCountsAsBurstDmg(actionType == ActionType.BURST);
        StatsContainer snapshot = hit.snapshot == null
                ? captureLiveStats(simulator.getCurrentTime())
                : hit.snapshot.merge(null);
        action.setStatSnapshot(snapshot);
        resolvingAction = action;
        resolvingFlask = flask;
        resolvingMeowball = meowball;
        try {
            simulator.performActionWithoutTimeAdvance(characterId, action);
        } finally {
            resolvingAction = null;
            resolvingFlask = false;
            resolvingMeowball = false;
        }
    }

    private double skillValue(
            String key,
            double talentNine,
            double talentTwelve) {
        boolean c5 = constellation >= 5;
        return getTalentValue(
                c5 ? key + " C5" : key,
                c5 ? talentTwelve : talentNine);
    }

    private double burstValue(
            String key,
            double talentNine,
            double talentTwelve) {
        boolean c3 = constellation >= 3;
        return getTalentValue(
                c3 ? key + " C3" : key,
                c3 ? talentTwelve : talentNine);
    }

    private double nextRobotGauge(Element element, double currentTime) {
        int index = absorbIndex(element);
        if (robotLastApplication[index] == Double.NEGATIVE_INFINITY
                || currentTime - robotLastApplication[index] + EPSILON
                        >= 15.0) {
            robotLastApplication[index] = currentTime;
            robotSuppressedHits[index] = 0;
            return 1.0;
        }
        robotSuppressedHits[index]++;
        if (robotSuppressedHits[index] >= 3) {
            robotLastApplication[index] = currentTime;
            robotSuppressedHits[index] = 0;
            return 1.0;
        }
        return 0.0;
    }

    private double nextC1Gauge(double currentTime) {
        if (c1LastApplication == Double.NEGATIVE_INFINITY
                || currentTime - c1LastApplication + EPSILON >= 2.5) {
            c1LastApplication = currentTime;
            c1SuppressedHits = 0;
            return 1.0;
        }
        c1SuppressedHits++;
        if (c1SuppressedHits >= 2) {
            c1LastApplication = currentTime;
            c1SuppressedHits = 0;
            return 1.0;
        }
        return 0.0;
    }

    private void resetRobotIcd() {
        for (int index = 0; index < robotLastApplication.length; index++) {
            robotLastApplication[index] = Double.NEGATIVE_INFINITY;
            robotSuppressedHits[index] = 0;
        }
    }

    private static int absorbIndex(Element element) {
        for (int index = 0; index < ABSORB_PRIORITY.length; index++) {
            if (ABSORB_PRIORITY[index] == element) {
                return index;
            }
        }
        throw new IllegalArgumentException(
                "Unsupported Jahoda absorbed element " + element);
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

    private void queueHit(CombatSimulator simulator, PendingHit hit) {
        pendingHits.add(hit);
        scheduleHit(simulator, hit);
    }

    private void scheduleHit(CombatSimulator simulator, PendingHit hit) {
        long scheduledGeneration = eventGeneration;
        schedule(simulator, hit.time, activeSimulator -> {
            if (scheduledGeneration != eventGeneration
                    || !pendingHits.remove(hit)) {
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
        long scheduledGeneration = eventGeneration;
        schedule(simulator, command.time, activeSimulator -> {
            if (scheduledGeneration != eventGeneration
                    || !pendingCommands.remove(command)) {
                return;
            }
            switch (command.kind) {
                case ENTER_PURSUIT:
                    enterPursuit(
                            activeSimulator,
                            command.generation,
                            command.time);
                    break;
                case FLASK_FILL:
                    fillFlask(
                            activeSimulator,
                            command.generation,
                            command.time);
                    break;
                case NATURAL_DRAIN:
                    drainFlask(activeSimulator, command.generation, false);
                    break;
                case FULL_DRAIN:
                    drainFlask(activeSimulator, command.generation, true);
                    break;
                case BURST_COOLDOWN:
                    if (command.generation == burstGeneration) {
                        markBurstCooldownUsed(
                                command.time,
                                activeSimulator.getApplicableBuffs(this));
                    }
                    break;
                case BURST_ENERGY:
                    if (command.generation == burstGeneration) {
                        spendBurstEnergy(command.time);
                    }
                    break;
                case ROBOT_ABSORB:
                    absorbRobot(activeSimulator, command);
                    break;
                case PARTICLE:
                    if (command.generation == skillGeneration) {
                        activeSimulator.getEnergyDistributor()
                                .distributeParticles(
                                        Element.ANEMO,
                                        command.value,
                                        ParticleType.PARTICLE);
                    }
                    break;
                default:
                    throw new IllegalStateException(
                            "Unknown Jahoda command " + command.kind);
            }
        });
    }

    private static boolean isPursuitCommand(CommandKind kind) {
        return kind == CommandKind.FLASK_FILL
                || kind == CommandKind.NATURAL_DRAIN
                || kind == CommandKind.FULL_DRAIN;
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
        UNFILLED_FLASK,
        FILLED_FLASK,
        MEOWBALL,
        C1_BOUNCE,
        BURST_CAST,
        ROBOT
    }

    private enum CommandKind {
        ENTER_PURSUIT,
        FLASK_FILL,
        NATURAL_DRAIN,
        FULL_DRAIN,
        BURST_COOLDOWN,
        BURST_ENERGY,
        ROBOT_ABSORB,
        PARTICLE
    }

    private static final class PendingHit {
        private final double time;
        private final HitKind kind;
        private final int index;
        private final int subIndex;
        private final Element element;
        private final long generation;
        private final StatsContainer snapshot;

        private PendingHit(
                double time,
                HitKind kind,
                int index,
                int subIndex,
                Element element,
                long generation,
                StatsContainer snapshot) {
            this.time = time;
            this.kind = kind;
            this.index = index;
            this.subIndex = subIndex;
            this.element = element;
            this.generation = generation;
            this.snapshot = snapshot == null
                    ? null : snapshot.merge(null);
        }

        private PendingHit copy() {
            return new PendingHit(
                    time,
                    kind,
                    index,
                    subIndex,
                    element,
                    generation,
                    snapshot);
        }
    }

    private static final class PendingCommand {
        private final double time;
        private final CommandKind kind;
        private final long generation;
        private final int index;
        private final double value;
        private final Element element;

        private PendingCommand(
                double time,
                CommandKind kind,
                long generation,
                int index,
                double value,
                Element element) {
            this.time = time;
            this.kind = kind;
            this.generation = generation;
            this.index = index;
            this.value = value;
            this.element = element;
        }

        private PendingCommand copy() {
            return new PendingCommand(
                    time, kind, generation, index, value, element);
        }
    }

    private static final class JahodaC6Buff extends Buff {
        private final Jahoda owner;
        private final double critRate;
        private final double critDamage;

        private JahodaC6Buff(
                Jahoda owner,
                double duration,
                double currentTime,
                double critRate,
                double critDamage) {
            super("Jahoda The Littlest Luck", duration, currentTime);
            this.owner = owner;
            this.critRate = critRate;
            this.critDamage = critDamage;
            sourcedBy(owner.characterId);
        }

        @Override
        protected void applyStats(
                StatsContainer stats,
                double currentTime) {
            stats.add(StatType.CRIT_RATE, critRate);
            stats.add(StatType.CRIT_DMG, critDamage);
        }
    }

    private static final class JahodaState implements State {
        private final Jahoda owner;
        private final int normalAttackStep;
        private final boolean shadowPursuit;
        private final long skillGeneration;
        private final double shadowStartTime;
        private final Element flaskElement;
        private final int flaskGauge;
        private final boolean flaskParticleIssued;
        private final double nextMeowballEnergyTime;
        private final long burstGeneration;
        private final double burstEndTime;
        private final double robotIntervalFrames;
        private final double robotDamageMultiplier;
        private final int robotCount;
        private final double[] robotLastApplication;
        private final int[] robotSuppressedHits;
        private final double c1LastApplication;
        private final int c1SuppressedHits;
        private final long eventGeneration;
        private final List<PendingHit> pendingHits;
        private final List<PendingCommand> pendingCommands;

        private JahodaState(
                Jahoda owner,
                int normalAttackStep,
                boolean shadowPursuit,
                long skillGeneration,
                double shadowStartTime,
                Element flaskElement,
                int flaskGauge,
                boolean flaskParticleIssued,
                double nextMeowballEnergyTime,
                long burstGeneration,
                double burstEndTime,
                double robotIntervalFrames,
                double robotDamageMultiplier,
                int robotCount,
                double[] robotLastApplication,
                int[] robotSuppressedHits,
                double c1LastApplication,
                int c1SuppressedHits,
                long eventGeneration,
                List<PendingHit> pendingHits,
                List<PendingCommand> pendingCommands) {
            this.owner = owner;
            this.normalAttackStep = normalAttackStep;
            this.shadowPursuit = shadowPursuit;
            this.skillGeneration = skillGeneration;
            this.shadowStartTime = shadowStartTime;
            this.flaskElement = flaskElement;
            this.flaskGauge = flaskGauge;
            this.flaskParticleIssued = flaskParticleIssued;
            this.nextMeowballEnergyTime = nextMeowballEnergyTime;
            this.burstGeneration = burstGeneration;
            this.burstEndTime = burstEndTime;
            this.robotIntervalFrames = robotIntervalFrames;
            this.robotDamageMultiplier = robotDamageMultiplier;
            this.robotCount = robotCount;
            this.robotLastApplication = robotLastApplication.clone();
            this.robotSuppressedHits = robotSuppressedHits.clone();
            this.c1LastApplication = c1LastApplication;
            this.c1SuppressedHits = c1SuppressedHits;
            this.eventGeneration = eventGeneration;
            this.pendingHits = copyHits(pendingHits);
            this.pendingCommands = copyCommands(pendingCommands);
        }
    }
}

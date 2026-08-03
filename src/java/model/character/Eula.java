package model.character;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.DoubleSupplier;

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
import simulation.action.SkillActionMode;
import simulation.event.SimpleTimerEvent;

/**
 * Eula's bounded physical Normal, Skill, and Lightfall Sword kit through C6.
 *
 * <p>Multipliers, hitmarks, cooldown and Energy timing, gauge, ICD, and
 * Lightfall behavior follow pinned gcsim {@code ef41805d} and KQM TCL
 * {@code 80ba6241}. Lightfall closes its stack window 35 frames before impact
 * and reads live impact-time stats. All delayed work is value-copied for
 * simulator snapshot restoration.</p>
 *
 * <p>Charged Attack, defensive Grimheart effects, C4, geometry, shields,
 * hitlag, cancels, and multi-target behavior are intentionally excluded.</p>
 */
public final class Eula extends Character implements
        SimulatorInitializedCharacterEffect,
        SnapshotAwareCharacterEffect,
        SwitchAwareCharacter {
    private static final double FRAME = 1.0 / 60.0;
    private static final double EPSILON = 1e-9;
    private static final double PARTICLE_TRAVEL = 100.0 * FRAME;
    private static final int MAX_GRIMHEART_STACKS = 2;
    private static final int MAX_LIGHTFALL_STACKS = 30;
    private static final int[] NORMAL_DURATIONS = { 34, 36, 56, 44, 105 };
    private static final int[][] NORMAL_HITMARKS = {
            { 30 }, { 19 }, { 25, 42 }, { 17 }, { 29, 56 }
    };
    private static final double[][] NORMAL_MULTIPLIERS = {
            { 1.648572 },
            { 1.718724 },
            { 1.043511, 1.043511 },
            { 2.069484 },
            { 1.319735, 1.319735 }
    };

    private final DoubleSupplier particleDrawSource;
    private final DoubleSupplier c6DrawSource;
    private CombatSimulator initializedSimulator;
    private int normalAttackStep;
    private long skillGeneration;
    private long burstGeneration;
    private int grimheartStacks;
    private double grimheartExpirationTime = Double.NEGATIVE_INFINITY;
    private double nextGrimheartGainTime = Double.NEGATIVE_INFINITY;
    private double nextLightfallStackTime = Double.NEGATIVE_INFINITY;
    private BurstInstance burstInstance;
    private List<PendingHit> pendingHits = new ArrayList<>();
    private List<PendingCommand> pendingCommands = new ArrayList<>();
    private PendingHit resolvingHit;

    /** Constructs repository-default C6 Eula with stochastic draws. */
    public Eula(Weapon weapon, ArtifactSet artifacts) {
        this(weapon, artifacts, TalentDataManager.getInstance(), 6,
                Math::random, Math::random);
    }

    /** Constructs Eula at an explicit constellation with stochastic draws. */
    public Eula(Weapon weapon, ArtifactSet artifacts, int constellation) {
        this(weapon, artifacts, TalentDataManager.getInstance(), constellation,
                Math::random, Math::random);
    }

    /**
     * Constructs Eula with deterministic probability sources.
     *
     * @param weapon equipped claymore
     * @param artifacts equipped artifact set
     * @param constellation constellation in the inclusive range 0-6
     * @param particleDrawSource particle draws in {@code [0, 1]}
     * @param c6DrawSource C6 extra-stack draws in {@code [0, 1]}
     */
    public Eula(
            Weapon weapon,
            ArtifactSet artifacts,
            int constellation,
            DoubleSupplier particleDrawSource,
            DoubleSupplier c6DrawSource) {
        this(weapon, artifacts, TalentDataManager.getInstance(), constellation,
                particleDrawSource, c6DrawSource);
    }

    /**
     * Constructs Eula with injectable talent and probability sources.
     *
     * @param weapon equipped claymore
     * @param artifacts equipped artifact set
     * @param talentData talent and configuration source
     * @param constellation constellation in the inclusive range 0-6
     * @param particleDrawSource particle draws in {@code [0, 1]}
     * @param c6DrawSource C6 extra-stack draws in {@code [0, 1]}
     */
    public Eula(
            Weapon weapon,
            ArtifactSet artifacts,
            TalentDataSource talentData,
            int constellation,
            DoubleSupplier particleDrawSource,
            DoubleSupplier c6DrawSource) {
        super(talentData);
        if (constellation < 0 || constellation > 6) {
            throw new IllegalArgumentException(
                    "Eula constellation must be between 0 and 6");
        }
        if (particleDrawSource == null) {
            throw new IllegalArgumentException(
                    "Eula particle draw is required");
        }
        if (c6DrawSource == null) {
            throw new IllegalArgumentException("Eula C6 draw is required");
        }
        name = "Eula";
        characterId = CharacterId.EULA;
        element = Element.CRYO;
        this.weapon = weapon;
        this.artifacts = new ArtifactSet[] { artifacts };
        this.constellation = constellation;
        this.particleDrawSource = particleDrawSource;
        this.c6DrawSource = c6DrawSource;
        baseStats.set(StatType.BASE_HP,
                getTalentValue("Base HP", 13226.0));
        baseStats.set(StatType.BASE_ATK,
                getTalentValue("Base ATK", 342.0));
        baseStats.set(StatType.BASE_DEF,
                getTalentValue("Base DEF", 751.0));
        baseStats.add(StatType.CRIT_DMG,
                getTalentValue("Ascension CRIT DMG", 0.384));
        setSkillCD(4.0);
        setBurstCD(20.0);
    }

    /** Binds Eula's damage listener and owned event state to one simulator. */
    @Override
    public void initializeForSimulator(CombatSimulator simulator) {
        if (simulator == null) {
            throw new IllegalArgumentException("Eula simulator is required");
        }
        if (initializedSimulator != null
                && initializedSimulator != simulator) {
            throw new IllegalStateException(
                    "Eula cannot be reused across CombatSimulator instances");
        }
        if (initializedSimulator == simulator) {
            return;
        }
        initializedSimulator = simulator;
        simulator.addDamageListener((actor, action, damage, time) ->
                onResolvedDamage(actor, action, damage, time, simulator));
    }

    /** Captures Eula-owned resources and reconstructible future work. */
    @Override
    public State captureCharacterState() {
        return new EulaState(
                this,
                normalAttackStep,
                skillGeneration,
                burstGeneration,
                grimheartStacks,
                grimheartExpirationTime,
                nextGrimheartGainTime,
                nextLightfallStackTime,
                burstInstance,
                pendingHits,
                pendingCommands);
    }

    /** Accepts only payloads captured from this exact Eula instance. */
    @Override
    public boolean acceptsCharacterState(State state) {
        return state instanceof EulaState
                && ((EulaState) state).owner == this;
    }

    /** Restores each surviving Eula-owned event exactly once. */
    @Override
    public void restoreCharacterState(
            State state,
            CombatSimulator simulator) {
        if (!acceptsCharacterState(state)) {
            throw new IllegalArgumentException("Unexpected Eula character state");
        }
        initializeForSimulator(simulator);
        EulaState restored = (EulaState) state;
        normalAttackStep = restored.normalAttackStep;
        skillGeneration = restored.skillGeneration;
        burstGeneration = restored.burstGeneration;
        grimheartStacks = restored.grimheartStacks;
        grimheartExpirationTime = restored.grimheartExpirationTime;
        nextGrimheartGainTime = restored.nextGrimheartGainTime;
        nextLightfallStackTime = restored.nextLightfallStackTime;
        burstInstance = restored.burstInstance == null
                ? null : restored.burstInstance.copy();
        pendingHits = copyHits(restored.pendingHits);
        pendingCommands = copyCommands(restored.pendingCommands);
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
        expireGrimheart(currentTime);
    }

    /** Returns Eula's 80-Energy Burst cost. */
    @Override
    public double getEnergyCost() {
        return getTalentValue("Energy Cost", 80.0);
    }

    /** Eula has no unconditional offensive passive in this bounded slice. */
    @Override
    public void applyPassive(StatsContainer stats) {
        // Modeled passives require Skill or Burst state.
    }

    /** Returns active Grimheart stacks, expiring them at the exact boundary. */
    public int getGrimheartStacks(double currentTime) {
        expireGrimheart(currentTime);
        return grimheartStacks;
    }

    /** Returns the current capped Lightfall stack count. */
    public int getLightfallStacks() {
        return burstInstance == null ? 0 : burstInstance.stackCount;
    }

    /** Returns whether the current Lightfall generation accepts stacks. */
    public boolean isLightfallActive(double currentTime) {
        return burstInstance != null
                && burstInstance.generation == burstGeneration
                && burstInstance.active
                && currentTime >= burstInstance.activationTime
                && currentTime < burstInstance.stackWindowEnd;
    }

    /** Closes Lightfall on switch and schedules its live explosion 35 frames later. */
    @Override
    public void onSwitchOut(CombatSimulator simulator) {
        normalAttackStep = 0;
        if (isLightfallActive(simulator.getCurrentTime())) {
            lockAndQueueLightfall(simulator, burstGeneration);
        }
    }

    /** Dispatches Eula's supported typed offensive actions. */
    @Override
    public void onAction(
            CharacterActionRequest request,
            CombatSimulator simulator) {
        if (request == null) {
            throw new IllegalArgumentException("Eula action is required");
        }
        initializeForSimulator(simulator);
        if (request.getKey() != CharacterActionKey.NORMAL) {
            normalAttackStep = 0;
        }
        switch (request.getKey()) {
            case NORMAL:
                normalAttack(simulator);
                break;
            case PLUNGE:
                highPlunge(simulator);
                break;
            case SKILL:
                if (request.getSkillMode() == SkillActionMode.HOLD) {
                    holdSkill(simulator);
                } else {
                    pressSkill(simulator);
                }
                break;
            case BURST:
                glacialIllumination(simulator);
                break;
            default:
                throw new IllegalArgumentException(
                        "Unsupported action for Eula: " + request.getKey());
        }
    }

    private void normalAttack(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        int step = normalAttackStep;
        for (int hit = 0; hit < NORMAL_MULTIPLIERS[step].length; hit++) {
            queueHit(simulator, new PendingHit(
                    castTime + NORMAL_HITMARKS[step][hit] * FRAME,
                    HitKind.NORMAL,
                    step,
                    hit,
                    0L,
                    0));
        }
        normalAttackStep = (normalAttackStep + 1)
                % NORMAL_MULTIPLIERS.length;
        simulator.advanceTime(NORMAL_DURATIONS[step] * FRAME);
    }

    private void highPlunge(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        queueHit(simulator, new PendingHit(
                castTime + 41.0 * FRAME,
                HitKind.HIGH_PLUNGE,
                0,
                0,
                0L,
                0));
        simulator.advanceTime(84.0 * FRAME);
    }

    private void pressSkill(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        long generation = ++skillGeneration;
        setSkillCD(4.0);
        queueHit(simulator, new PendingHit(
                castTime + 20.0 * FRAME,
                HitKind.SKILL_PRESS,
                0,
                0,
                generation,
                0));
        queueCommand(simulator, new PendingCommand(
                castTime + 16.0 * FRAME,
                CommandKind.SKILL_COOLDOWN,
                generation,
                0.0));
        simulator.advanceTime(48.0 * FRAME);
    }

    private void holdSkill(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        long generation = ++skillGeneration;
        int consumed = consumeGrimheart(castTime);
        setSkillCD(constellation >= 2 ? 4.0 : 10.0);
        queueHit(simulator, new PendingHit(
                castTime + 49.0 * FRAME,
                HitKind.SKILL_HOLD,
                0,
                0,
                generation,
                consumed));
        for (int index = 0; index < consumed; index++) {
            int hitmark = index == 0 ? 79 : 92;
            queueHit(simulator, new PendingHit(
                    castTime + hitmark * FRAME,
                    HitKind.ICEWHIRL,
                    index,
                    0,
                    generation,
                    consumed));
        }
        if (consumed == MAX_GRIMHEART_STACKS) {
            queueHit(simulator, new PendingHit(
                    castTime + 108.0 * FRAME,
                    HitKind.A1_LIGHTFALL,
                    0,
                    0,
                    generation,
                    consumed));
        }
        if (constellation >= 1 && consumed > 0) {
            replaceC1Buff(castTime, consumed);
        }
        queueCommand(simulator, new PendingCommand(
                castTime + 46.0 * FRAME,
                CommandKind.SKILL_COOLDOWN,
                generation,
                0.0));
        simulator.advanceTime(100.0 * FRAME);
    }

    private void glacialIllumination(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        long generation = ++burstGeneration;
        int initialStacks = constellation >= 6 ? 5 : 0;
        burstInstance = new BurstInstance(
                generation,
                castTime + 117.0 * FRAME,
                castTime + 565.0 * FRAME,
                initialStacks);
        nextLightfallStackTime = Double.NEGATIVE_INFINITY;
        applyA4(castTime);
        queueCommand(simulator, new PendingCommand(
                castTime + 97.0 * FRAME,
                CommandKind.BURST_COOLDOWN,
                generation,
                0.0));
        queueHit(simulator, new PendingHit(
                castTime + 100.0 * FRAME,
                HitKind.BURST_INITIAL,
                0,
                0,
                generation,
                0));
        queueCommand(simulator, new PendingCommand(
                castTime + 107.0 * FRAME,
                CommandKind.BURST_ENERGY,
                generation,
                0.0));
        queueCommand(simulator, new PendingCommand(
                castTime + 117.0 * FRAME,
                CommandKind.BURST_ACTIVATE,
                generation,
                0.0));
        queueCommand(simulator, new PendingCommand(
                castTime + 565.0 * FRAME,
                CommandKind.LIGHTFALL_LOCK,
                generation,
                0.0));
        simulator.advanceTime(123.0 * FRAME);
    }

    private void applyA4(double currentTime) {
        resetSkillCooldown(currentTime);
        addGrimheart(currentTime);
    }

    private void addGrimheart(double currentTime) {
        expireGrimheart(currentTime);
        grimheartStacks = Math.min(
                MAX_GRIMHEART_STACKS, grimheartStacks + 1);
        grimheartExpirationTime = currentTime + 18.0;
    }

    private int consumeGrimheart(double currentTime) {
        expireGrimheart(currentTime);
        int consumed = grimheartStacks;
        grimheartStacks = 0;
        grimheartExpirationTime = Double.NEGATIVE_INFINITY;
        return consumed;
    }

    private void expireGrimheart(double currentTime) {
        if (currentTime >= grimheartExpirationTime) {
            grimheartStacks = 0;
            grimheartExpirationTime = Double.NEGATIVE_INFINITY;
        }
    }

    private void replaceC1Buff(double currentTime, int consumed) {
        removeBuff(BuffId.EULA_C1_PHYSICAL_DMG_BONUS);
        addBuff(new SimpleBuff(
                "Eula Tidal Illusion",
                BuffId.EULA_C1_PHYSICAL_DMG_BONUS,
                6.0 + 6.0 * consumed,
                currentTime,
                stats -> stats.add(
                        StatType.PHYSICAL_DMG_BONUS,
                        getTalentValue("C1 Physical DMG Bonus", 0.30))));
    }

    private void refreshIcewhirlShred(
            CombatSimulator simulator,
            double currentTime,
            int consumed) {
        double amount = getTalentValue(
                constellation >= 5 ? "RES Shred C5" : "RES Shred",
                constellation >= 5 ? 0.25 : 0.24);
        simulator.applyTeamBuffNoStack(new SimpleBuff(
                "Eula Icewhirl Resistance Shred",
                BuffId.EULA_ICEWHIRL_RES_SHRED,
                7.0 * consumed,
                currentTime,
                stats -> {
                    stats.add(StatType.CRYO_RES_SHRED, amount);
                    stats.add(StatType.PHYS_RES_SHRED, amount);
                }).sourcedBy(characterId));
    }

    private void onResolvedDamage(
            Character actor,
            AttackAction action,
            double damage,
            double time,
            CombatSimulator simulator) {
        if (simulator != initializedSimulator
                || actor != this
                || action == null
                || damage <= 0.0) {
            return;
        }
        PendingHit hit = resolvingHit;
        if (hit != null
                && hit.kind == HitKind.SKILL_PRESS
                && time + EPSILON >= nextGrimheartGainTime) {
            addGrimheart(time);
            nextGrimheartGainTime = time + 18.0 * FRAME;
        }
        if (hit != null && hit.kind == HitKind.ICEWHIRL) {
            refreshIcewhirlShred(simulator, time, hit.consumedStacks);
        }
        if (!isLightfallActive(time)
                || (hit != null && hit.kind == HitKind.BURST_INITIAL)
                || (action.getActionType() != ActionType.NORMAL
                        && action.getActionType() != ActionType.SKILL
                        && action.getActionType() != ActionType.BURST)
                || time + EPSILON < nextLightfallStackTime) {
            return;
        }
        addLightfallStack();
        nextLightfallStackTime = time + 6.0 * FRAME;
        if (constellation >= 6) {
            double draw = consumeDraw(c6DrawSource, "Eula C6");
            if (draw < 0.50) {
                addLightfallStack();
            }
        }
    }

    private void addLightfallStack() {
        if (burstInstance != null) {
            burstInstance.stackCount = Math.min(
                    MAX_LIGHTFALL_STACKS,
                    burstInstance.stackCount + 1);
        }
    }

    private void lockAndQueueLightfall(
            CombatSimulator simulator,
            long generation) {
        if (burstInstance == null
                || burstInstance.generation != generation
                || !burstInstance.active
                || burstInstance.explosionQueued) {
            return;
        }
        burstInstance.active = false;
        burstInstance.explosionQueued = true;
        queueHit(simulator, new PendingHit(
                simulator.getCurrentTime() + 35.0 * FRAME,
                HitKind.LIGHTFALL,
                0,
                0,
                generation,
                burstInstance.stackCount));
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
        if (isStale(hit)) {
            return;
        }
        AttackAction action;
        switch (hit.kind) {
            case NORMAL:
                String normalKey = normalTalentKey(hit.index, hit.subIndex);
                action = attack(
                        "Favonius Bladework - Edel N" + (hit.index + 1)
                                + " Hit " + (hit.subIndex + 1),
                        getTalentValue(
                                normalKey,
                                NORMAL_MULTIPLIERS[hit.index][hit.subIndex]),
                        Element.PHYSICAL,
                        StatType.NORMAL_ATTACK_DMG_BONUS,
                        ActionType.NORMAL,
                        ICDType.Standard,
                        ICDTag.NormalAttack,
                        0.0,
                        true);
                break;
            case HIGH_PLUNGE:
                action = attack(
                        "Favonius Bladework - Edel High Plunge",
                        getTalentValue("High Plunge", 3.422517),
                        Element.PHYSICAL,
                        StatType.PLUNGING_ATTACK_DMG_BONUS,
                        ActionType.PLUNGE,
                        ICDType.None,
                        ICDTag.PlungeAttack,
                        0.0,
                        true);
                break;
            case SKILL_PRESS:
                action = skillAttack(
                        "Icetide Vortex (Press)",
                        "Press Skill",
                        "Press Skill C5",
                        constellation >= 5 ? 2.928 : 2.4888,
                        ICDType.None,
                        true);
                break;
            case SKILL_HOLD:
                action = skillAttack(
                        "Icetide Vortex (Hold)",
                        "Hold Skill",
                        "Hold Skill C5",
                        constellation >= 5 ? 4.912 : 4.1752,
                        ICDType.None,
                        true);
                break;
            case ICEWHIRL:
                action = skillAttack(
                        "Icetide Vortex (Icewhirl)",
                        "Icewhirl",
                        "Icewhirl C5",
                        constellation >= 5 ? 1.92 : 1.632,
                        ICDType.Standard,
                        false);
                break;
            case A1_LIGHTFALL:
                action = burstAttack(
                        "Roiling Rime Shattered Lightfall Sword",
                        lightfallBaseMultiplier()
                                * getTalentValue("A1 Lightfall Factor", 0.50),
                        Element.PHYSICAL,
                        0.0,
                        true);
                break;
            case BURST_INITIAL:
                action = burstAttack(
                        "Glacial Illumination (Initial)",
                        getTalentValue(
                                constellation >= 3
                                        ? "Burst Initial C3"
                                        : "Burst Initial",
                                constellation >= 3 ? 4.912 : 4.1752),
                        Element.CRYO,
                        2.0,
                        true);
                break;
            case LIGHTFALL:
                action = burstAttack(
                        "Glacial Illumination (Lightfall Sword)",
                        lightfallBaseMultiplier()
                                + lightfallStackMultiplier()
                                        * hit.consumedStacks,
                        Element.PHYSICAL,
                        0.0,
                        true);
                break;
            default:
                throw new IllegalStateException("Unknown Eula hit kind");
        }
        if (hit.kind == HitKind.SKILL_PRESS
                || hit.kind == HitKind.SKILL_HOLD) {
            double draw = consumeDraw(
                    particleDrawSource, "Eula particle");
            double count = hit.kind == HitKind.SKILL_PRESS
                    ? (draw < 0.50 ? 2.0 : 1.0)
                    : (draw < 0.50 ? 3.0 : 2.0);
            queueCommand(simulator, new PendingCommand(
                    simulator.getCurrentTime() + PARTICLE_TRAVEL,
                    CommandKind.PARTICLES,
                    hit.generation,
                    count));
        }
        resolvingHit = hit;
        try {
            simulator.performActionWithoutTimeAdvance(characterId, action);
        } finally {
            resolvingHit = null;
        }
        if (hit.kind == HitKind.LIGHTFALL
                && burstInstance != null
                && burstInstance.generation == hit.generation) {
            burstInstance = null;
            nextLightfallStackTime = Double.NEGATIVE_INFINITY;
        }
    }

    private boolean isStale(PendingHit hit) {
        switch (hit.kind) {
            case SKILL_PRESS:
            case SKILL_HOLD:
            case ICEWHIRL:
            case A1_LIGHTFALL:
                return hit.generation != skillGeneration;
            case BURST_INITIAL:
            case LIGHTFALL:
                return hit.generation != burstGeneration
                        || burstInstance == null
                        || burstInstance.generation != hit.generation;
            default:
                return false;
        }
    }

    private static String normalTalentKey(int step, int hit) {
        if (step == 2 || step == 4) {
            return "N" + (step + 1) + " Hit " + (hit + 1);
        }
        return "N" + (step + 1);
    }

    private AttackAction skillAttack(
            String name,
            String baseKey,
            String c5Key,
            double defaultValue,
            ICDType icdType,
            boolean blunt) {
        String key = constellation >= 5 ? c5Key : baseKey;
        return attack(
                name,
                getTalentValue(key, defaultValue),
                Element.CRYO,
                StatType.SKILL_DMG_BONUS,
                ActionType.SKILL,
                icdType,
                ICDTag.ElementalSkill,
                1.0,
                blunt);
    }

    private AttackAction burstAttack(
            String name,
            double multiplier,
            Element element,
            double gaugeUnits,
            boolean blunt) {
        return attack(
                name,
                multiplier,
                element,
                StatType.BURST_DMG_BONUS,
                ActionType.BURST,
                ICDType.None,
                ICDTag.ElementalBurst,
                gaugeUnits,
                blunt);
    }

    private double lightfallBaseMultiplier() {
        return getTalentValue(
                constellation >= 3
                        ? "Lightfall Base C3" : "Lightfall Base",
                constellation >= 3 ? 8.532586 : 6.74344);
    }

    private double lightfallStackMultiplier() {
        return getTalentValue(
                constellation >= 3
                        ? "Lightfall Per Stack C3"
                        : "Lightfall Per Stack",
                constellation >= 3 ? 1.743302 : 1.37776);
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
                case SKILL_COOLDOWN:
                    if (command.generation == skillGeneration) {
                        markSkillUsed(
                                activeSim.getCurrentTime(),
                                activeSim.getApplicableBuffs(this));
                    }
                    break;
                case PARTICLES:
                    if (command.generation == skillGeneration) {
                        activeSim.getEnergyDistributor().distributeParticles(
                                Element.CRYO,
                                command.value,
                                ParticleType.PARTICLE);
                    }
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
                case BURST_ACTIVATE:
                    if (isCurrentBurstGeneration(command.generation)) {
                        burstInstance.active = true;
                    }
                    break;
                case LIGHTFALL_LOCK:
                    lockAndQueueLightfall(activeSim, command.generation);
                    break;
                default:
                    throw new IllegalStateException(
                            "Unknown Eula command kind");
            }
        });
    }

    private boolean isCurrentBurstGeneration(long generation) {
        return burstInstance != null
                && burstInstance.generation == generation
                && burstGeneration == generation;
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

    private static AttackAction attack(
            String name,
            double multiplier,
            Element element,
            StatType bonusStat,
            ActionType actionType,
            ICDType icdType,
            ICDTag icdTag,
            double gaugeUnits,
            boolean blunt) {
        AttackAction action = new AttackAction(
                name,
                multiplier,
                element,
                StatType.BASE_ATK,
                bonusStat,
                0.0,
                actionType);
        action.setICD(icdType, icdTag, gaugeUnits);
        action.setShatterTrigger(blunt);
        action.setCountsAsSkillDmg(actionType == ActionType.SKILL);
        action.setCountsAsBurstDmg(actionType == ActionType.BURST);
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
        HIGH_PLUNGE,
        SKILL_PRESS,
        SKILL_HOLD,
        ICEWHIRL,
        A1_LIGHTFALL,
        BURST_INITIAL,
        LIGHTFALL
    }

    private enum CommandKind {
        SKILL_COOLDOWN,
        PARTICLES,
        BURST_COOLDOWN,
        BURST_ENERGY,
        BURST_ACTIVATE,
        LIGHTFALL_LOCK
    }

    /** Immutable delayed Eula hit description. */
    private static final class PendingHit {
        private final double time;
        private final HitKind kind;
        private final int index;
        private final int subIndex;
        private final long generation;
        private final int consumedStacks;

        private PendingHit(
                double time,
                HitKind kind,
                int index,
                int subIndex,
                long generation,
                int consumedStacks) {
            this.time = time;
            this.kind = kind;
            this.index = index;
            this.subIndex = subIndex;
            this.generation = generation;
            this.consumedStacks = consumedStacks;
        }

        private PendingHit copy() {
            return new PendingHit(
                    time,
                    kind,
                    index,
                    subIndex,
                    generation,
                    consumedStacks);
        }
    }

    /** Immutable delayed Eula non-damage command. */
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

    /** Mutable state for one Lightfall Sword generation. */
    private static final class BurstInstance {
        private final long generation;
        private final double activationTime;
        private final double stackWindowEnd;
        private int stackCount;
        private boolean active;
        private boolean explosionQueued;

        private BurstInstance(
                long generation,
                double activationTime,
                double stackWindowEnd,
                int stackCount) {
            this.generation = generation;
            this.activationTime = activationTime;
            this.stackWindowEnd = stackWindowEnd;
            this.stackCount = Math.min(MAX_LIGHTFALL_STACKS, stackCount);
        }

        private BurstInstance copy() {
            BurstInstance copy = new BurstInstance(
                    generation, activationTime, stackWindowEnd, stackCount);
            copy.active = active;
            copy.explosionQueued = explosionQueued;
            return copy;
        }
    }

    /** Immutable Eula-owned simulator snapshot payload. */
    private static final class EulaState implements State {
        private final Eula owner;
        private final int normalAttackStep;
        private final long skillGeneration;
        private final long burstGeneration;
        private final int grimheartStacks;
        private final double grimheartExpirationTime;
        private final double nextGrimheartGainTime;
        private final double nextLightfallStackTime;
        private final BurstInstance burstInstance;
        private final List<PendingHit> pendingHits;
        private final List<PendingCommand> pendingCommands;

        private EulaState(
                Eula owner,
                int normalAttackStep,
                long skillGeneration,
                long burstGeneration,
                int grimheartStacks,
                double grimheartExpirationTime,
                double nextGrimheartGainTime,
                double nextLightfallStackTime,
                BurstInstance burstInstance,
                List<PendingHit> pendingHits,
                List<PendingCommand> pendingCommands) {
            this.owner = owner;
            this.normalAttackStep = normalAttackStep;
            this.skillGeneration = skillGeneration;
            this.burstGeneration = burstGeneration;
            this.grimheartStacks = grimheartStacks;
            this.grimheartExpirationTime = grimheartExpirationTime;
            this.nextGrimheartGainTime = nextGrimheartGainTime;
            this.nextLightfallStackTime = nextLightfallStackTime;
            this.burstInstance = burstInstance == null
                    ? null : burstInstance.copy();
            this.pendingHits = copyHits(pendingHits);
            this.pendingCommands = copyCommands(pendingCommands);
        }
    }
}

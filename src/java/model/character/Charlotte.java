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
import simulation.action.SkillActionMode;
import simulation.event.SimpleTimerEvent;

/**
 * Charlotte's fixed-target Kamera offensive kit through represented C5.
 *
 * <p>Basics, Charged Arkhe, Press/minimum-Hold marks, particles, Burst damage,
 * C2, C3, and C5 follow pinned gcsim {@code ef41805d}. Healing, A1/A4,
 * C1/C4/C6, multi-target selection, and variable Hold remain excluded.</p>
 */
public final class Charlotte extends Character implements
        SimulatorInitializedCharacterEffect,
        SnapshotAwareCharacterEffect,
        SwitchAwareCharacter {
    private static final double FRAME = 1.0 / 60.0;
    private static final double EPSILON = 1e-9;
    private static final double PARTICLE_TRAVEL = 100.0 * FRAME;
    private static final int[] NORMAL_HIT_FRAMES = { 13, 25, 31 };
    private static final int[] NORMAL_DURATIONS = { 32, 45, 74 };
    private static final double[] NORMAL_T9 = {
        0.847375, 0.737378, 1.098214
    };
    private static final int[] BURST_TICK_FRAMES = {
        95, 119, 143, 166, 179, 203, 226, 249
    };

    private CombatSimulator initializedSimulator;
    private int normalAttackStep;
    private long markGeneration;
    private double nextArkheTime = Double.NEGATIVE_INFINITY;
    private List<PendingHit> pendingHits = new ArrayList<>();
    private List<PendingCommand> pendingCommands = new ArrayList<>();

    /** Constructs repository-default C6 Charlotte. */
    public Charlotte(Weapon weapon, ArtifactSet artifacts) {
        this(weapon, artifacts, TalentDataManager.getInstance(), 6);
    }

    /** Constructs Charlotte at an explicit constellation. */
    public Charlotte(Weapon weapon, ArtifactSet artifacts, int constellation) {
        this(weapon, artifacts, TalentDataManager.getInstance(), constellation);
    }

    /** Constructs Charlotte with injectable talent data. */
    public Charlotte(
            Weapon weapon,
            ArtifactSet artifacts,
            TalentDataSource talentData,
            int constellation) {
        super(talentData);
        if (constellation < 0 || constellation > 6) {
            throw new IllegalArgumentException(
                    "Charlotte constellation must be between 0 and 6");
        }
        name = "Charlotte";
        characterId = CharacterId.CHARLOTTE;
        element = Element.CRYO;
        this.weapon = weapon;
        this.artifacts = new ArtifactSet[] { artifacts };
        this.constellation = constellation;
        baseStats.set(StatType.BASE_HP,
                getTalentValue("Base HP", 10766.0));
        baseStats.set(StatType.BASE_ATK,
                getTalentValue("Base ATK", 173.0));
        baseStats.set(StatType.BASE_DEF,
                getTalentValue("Base DEF", 546.0));
        baseStats.add(StatType.ATK_PERCENT,
                getTalentValue("Ascension ATK Percent", 0.24));
        setSkillCD(getTalentValue("Press Skill Cooldown", 12.0));
        setBurstCD(getTalentValue("Burst Cooldown", 20.0));
    }

    /** Binds delayed work to one simulator. */
    @Override
    public void initializeForSimulator(CombatSimulator simulator) {
        if (simulator == null) {
            throw new IllegalArgumentException(
                    "Charlotte simulator is required");
        }
        if (!simulator.getPartyMembers().contains(this)) {
            throw new IllegalArgumentException(
                    "Charlotte must belong to the simulator party");
        }
        if (initializedSimulator != null
                && initializedSimulator != simulator) {
            throw new IllegalStateException(
                    "Charlotte cannot be reused across simulators");
        }
        initializedSimulator = simulator;
    }

    /** Captures strings, gates, generations, and delayed work. */
    @Override
    public State captureCharacterState() {
        return new CharlotteState(this, normalAttackStep, markGeneration,
                nextArkheTime, pendingHits, pendingCommands);
    }

    /** Accepts state from this exact Charlotte instance only. */
    @Override
    public boolean acceptsCharacterState(State state) {
        return state instanceof CharlotteState
                && ((CharlotteState) state).owner == this;
    }

    /** Restores surviving delayed work once. */
    @Override
    public void restoreCharacterState(
            State state,
            CombatSimulator simulator) {
        if (!acceptsCharacterState(state)) {
            throw new IllegalArgumentException("Unexpected Charlotte state");
        }
        initializeForSimulator(simulator);
        CharlotteState restored = (CharlotteState) state;
        normalAttackStep = restored.normalAttackStep;
        markGeneration = restored.markGeneration;
        nextArkheTime = restored.nextArkheTime;
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
    }

    /** Returns Charlotte's 80-Energy Burst cost. */
    @Override
    public double getEnergyCost() {
        return getTalentValue("Energy Cost", 80.0);
    }

    /** Charlotte has no unconditional represented passive stat. */
    @Override
    public void applyPassive(StatsContainer stats) {
    }

    /** Supports Press and deterministic minimum-Hold Skill. */
    @Override
    protected boolean supportsSkillActionMode(SkillActionMode mode) {
        return mode == SkillActionMode.PRESS || mode == SkillActionMode.HOLD;
    }

    /** Resets Charlotte's Normal string on switch-out. */
    @Override
    public void onSwitchOut(CombatSimulator simulator) {
        normalAttackStep = 0;
    }

    /** Dispatches Charlotte's represented action set. */
    @Override
    public void onAction(
            CharacterActionRequest request,
            CombatSimulator simulator) {
        if (request == null) {
            throw new IllegalArgumentException("Charlotte action is required");
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
            case SKILL:
                freezingPoint(simulator,
                        request.getSkillMode() == SkillActionMode.HOLD);
                break;
            case BURST:
                comprehensiveConfirmation(simulator);
                break;
            default:
                throw new IllegalArgumentException(
                        "Unsupported action for Charlotte: "
                                + request.getKey());
        }
    }

    private void normalAttack(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        int step = normalAttackStep;
        queueHit(simulator, new PendingHit(
                castTime + NORMAL_HIT_FRAMES[step] * FRAME,
                HitKind.NORMAL,
                step,
                0,
                0,
                null));
        normalAttackStep = (normalAttackStep + 1) % NORMAL_HIT_FRAMES.length;
        simulator.advanceTime(NORMAL_DURATIONS[step] * FRAME);
    }

    private void chargedAttack(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        queueHit(simulator, new PendingHit(
                castTime + 67.0 * FRAME,
                HitKind.CHARGED,
                0,
                0,
                0,
                null));
        queueHit(simulator, new PendingHit(
                castTime + 97.0 * FRAME,
                HitKind.ARKHE,
                0,
                0,
                0,
                null));
        simulator.advanceTime(84.0 * FRAME);
    }

    private void freezingPoint(
            CombatSimulator simulator,
            boolean hold) {
        double castTime = simulator.getCurrentTime();
        markGeneration++;
        int impactFrame = hold ? 112 : 31;
        int cooldownFrame = hold ? 110 : 29;
        queueCommand(simulator, new PendingCommand(
                castTime + cooldownFrame * FRAME,
                CommandKind.SKILL_COOLDOWN,
                hold ? 18 : 12));
        queueHit(simulator, new PendingHit(
                castTime + impactFrame * FRAME,
                HitKind.SKILL,
                hold ? 1 : 0,
                0,
                markGeneration,
                null));
        int tickCount = hold ? 8 : 4;
        for (int tick = 1; tick <= tickCount; tick++) {
            queueHit(simulator, new PendingHit(
                    castTime + (impactFrame + 90 * tick) * FRAME,
                    HitKind.MARK,
                    hold ? 1 : 0,
                    tick,
                    markGeneration,
                    null));
        }
        simulator.advanceTime((hold ? 138.0 : 49.0) * FRAME);
    }

    private void comprehensiveConfirmation(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        StatsContainer snapshot = captureLiveStats(castTime);
        markBurstCooldownUsed(castTime,
                simulator.getApplicableBuffs(this));
        queueCommand(simulator, new PendingCommand(
                castTime + 7.0 * FRAME,
                CommandKind.BURST_ENERGY,
                0));
        queueHit(simulator, new PendingHit(
                castTime + 53.0 * FRAME,
                HitKind.BURST_INITIAL,
                0,
                0,
                0,
                snapshot));
        for (int tick = 0; tick < BURST_TICK_FRAMES.length; tick++) {
            queueHit(simulator, new PendingHit(
                    castTime + BURST_TICK_FRAMES[tick] * FRAME,
                    HitKind.BURST_TICK,
                    tick,
                    0,
                    0,
                    snapshot));
        }
        simulator.advanceTime(70.0 * FRAME);
    }

    private void resolveHit(CombatSimulator simulator, PendingHit hit) {
        switch (hit.kind) {
            case NORMAL:
                performHit(simulator, hit,
                        "Cool-Color Capture N" + (hit.index + 1),
                        getTalentValue("N" + (hit.index + 1),
                                NORMAL_T9[hit.index]),
                        StatType.NORMAL_ATTACK_DMG_BONUS,
                        ActionType.NORMAL,
                        ICDType.Standard,
                        ICDTag.NormalAttack,
                        1.0);
                break;
            case CHARGED:
                performHit(simulator, hit, "Cool-Color Capture Charged",
                        getTalentValue("Charged Attack", 1.708704),
                        StatType.CHARGED_ATTACK_DMG_BONUS,
                        ActionType.CHARGE,
                        ICDType.None,
                        ICDTag.ChargedAttack,
                        1.0);
                break;
            case ARKHE:
                if (hit.time + EPSILON < nextArkheTime) {
                    break;
                }
                nextArkheTime = hit.time
                        + getTalentValue("Arkhe Cooldown", 6.0);
                performHit(simulator, hit,
                        "Spiritbreath Thorn (Charlotte)",
                        getTalentValue("Spiritbreath Thorn", 0.189856),
                        StatType.CHARGED_ATTACK_DMG_BONUS,
                        ActionType.CHARGE,
                        ICDType.None,
                        ICDTag.ChargedAttack,
                        0.0);
                break;
            case SKILL:
                resolveSkill(simulator, hit);
                break;
            case MARK:
                if (hit.generation != markGeneration) {
                    break;
                }
                performHit(simulator, hit,
                        hit.index == 1
                                ? "Focused Impression Mark"
                                : "Snappy Silhouette Mark",
                        skillValue(
                                hit.index == 1
                                        ? "Focused Mark" : "Snappy Mark",
                                hit.index == 1 ? 0.690200 : 0.666400,
                                hit.index == 1 ? 0.812000 : 0.784000),
                        StatType.SKILL_DMG_BONUS,
                        ActionType.SKILL,
                        ICDType.Standard,
                        ICDTag.Charlotte_Mark,
                        1.0);
                break;
            case BURST_INITIAL:
                performHit(simulator, hit,
                        "Still Photo: Comprehensive Confirmation",
                        burstValue("Still Photo Initial", 1.319472, 1.552320),
                        StatType.BURST_DMG_BONUS,
                        ActionType.BURST,
                        ICDType.None,
                        ICDTag.ElementalBurst,
                        2.0);
                break;
            case BURST_TICK:
                performHit(simulator, hit,
                        "Still Photo: Kamera Tick " + (hit.index + 1),
                        burstValue("Kamera Tick", 0.109956, 0.129360),
                        StatType.BURST_DMG_BONUS,
                        ActionType.BURST,
                        ICDType.Standard,
                        ICDTag.Charlotte_Kamera,
                        1.0);
                break;
            default:
                throw new IllegalStateException("Unknown Charlotte hit kind");
        }
    }

    private void resolveSkill(CombatSimulator simulator, PendingHit hit) {
        boolean hold = hit.index == 1;
        performHit(simulator, hit,
                hold
                        ? "Framing: Freezing Point Composition (Hold)"
                        : "Framing: Freezing Point Composition",
                skillValue(hold ? "Hold Photo" : "Press Photo",
                        hold ? 2.366400 : 1.142400,
                        hold ? 2.784000 : 1.344000),
                StatType.SKILL_DMG_BONUS,
                ActionType.SKILL,
                ICDType.Standard,
                ICDTag.ElementalSkill,
                1.0);
        if (constellation >= 2) {
            removeBuff(BuffId.CHARLOTTE_C2_ATK);
            addBuff(new SimpleBuff(
                    "Charlotte C2 ATK",
                    BuffId.CHARLOTTE_C2_ATK,
                    12.0,
                    simulator.getCurrentTime(),
                    stats -> stats.add(StatType.ATK_PERCENT, 0.10))
                    .sourcedBy(characterId));
        }
        queueCommand(simulator, new PendingCommand(
                simulator.getCurrentTime() + PARTICLE_TRAVEL,
                CommandKind.PARTICLE,
                hold ? 5 : 3));
    }

    private double skillValue(
            String key,
            double talentNine,
            double talentTwelve) {
        String suffix = constellation >= 5 ? " C5" : "";
        return getTalentValue(key + suffix,
                constellation >= 5 ? talentTwelve : talentNine);
    }

    private double burstValue(
            String key,
            double talentNine,
            double talentTwelve) {
        String suffix = constellation >= 3 ? " C3" : "";
        return getTalentValue(key + suffix,
                constellation >= 3 ? talentTwelve : talentNine);
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
        action.setStatSnapshot(hit.snapshot == null
                ? captureLiveStats(simulator.getCurrentTime())
                : hit.snapshot);
        simulator.performActionWithoutTimeAdvance(characterId, action);
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

    private void queueHit(CombatSimulator simulator, PendingHit hit) {
        pendingHits.add(hit);
        scheduleHit(simulator, hit);
    }

    private void scheduleHit(CombatSimulator simulator, PendingHit hit) {
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
                case SKILL_COOLDOWN:
                    setSkillCD(command.value);
                    markSkillUsed(activeSimulator.getCurrentTime(),
                            activeSimulator.getApplicableBuffs(this));
                    break;
                case BURST_ENERGY:
                    spendBurstEnergy(activeSimulator.getCurrentTime());
                    break;
                case PARTICLE:
                    activeSimulator.getEnergyDistributor().distributeParticles(
                            Element.CRYO,
                            command.value,
                            ParticleType.PARTICLE);
                    break;
                default:
                    throw new IllegalStateException(
                            "Unknown Charlotte command kind");
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
        ARKHE,
        SKILL,
        MARK,
        BURST_INITIAL,
        BURST_TICK
    }

    private enum CommandKind {
        SKILL_COOLDOWN,
        BURST_ENERGY,
        PARTICLE
    }

    private static final class PendingHit {
        private final double time;
        private final HitKind kind;
        private final int index;
        private final int variant;
        private final long generation;
        private final StatsContainer snapshot;

        private PendingHit(
                double time,
                HitKind kind,
                int index,
                int variant,
                long generation,
                StatsContainer snapshot) {
            this.time = time;
            this.kind = kind;
            this.index = index;
            this.variant = variant;
            this.generation = generation;
            this.snapshot = snapshot == null ? null : snapshot.merge(null);
        }

        private PendingHit copy() {
            return new PendingHit(time, kind, index, variant,
                    generation, snapshot);
        }
    }

    private static final class PendingCommand {
        private final double time;
        private final CommandKind kind;
        private final int value;

        private PendingCommand(
                double time,
                CommandKind kind,
                int value) {
            this.time = time;
            this.kind = kind;
            this.value = value;
        }

        private PendingCommand copy() {
            return new PendingCommand(time, kind, value);
        }
    }

    private static final class CharlotteState implements State {
        private final Charlotte owner;
        private final int normalAttackStep;
        private final long markGeneration;
        private final double nextArkheTime;
        private final List<PendingHit> pendingHits;
        private final List<PendingCommand> pendingCommands;

        private CharlotteState(
                Charlotte owner,
                int normalAttackStep,
                long markGeneration,
                double nextArkheTime,
                List<PendingHit> pendingHits,
                List<PendingCommand> pendingCommands) {
            this.owner = owner;
            this.normalAttackStep = normalAttackStep;
            this.markGeneration = markGeneration;
            this.nextArkheTime = nextArkheTime;
            this.pendingHits = copyHits(pendingHits);
            this.pendingCommands = copyCommands(pendingCommands);
        }
    }
}

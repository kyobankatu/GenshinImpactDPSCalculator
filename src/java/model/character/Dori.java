package model.character;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

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
import simulation.event.SimpleTimerEvent;

/**
 * Dori's fixed-target offensive and Energy-support slice through C6.
 *
 * <p>Three physical Normals, Troubleshooter and After-Sales rounds, particles,
 * A4 self Energy, the snapshotted Jinni connector, active-character Energy,
 * C1/C3/C5, and C6 Electro infusion follow pinned gcsim {@code ef41805d}.
 * A1 connected-character reaction cooldown reduction, healing-triggered C2,
 * C4's HP/healing branches, C6 healing, Charged/Plunge attacks, geometry, and
 * hitlag extension remains excluded.</p>
 */
public final class Dori extends Character implements
        SimulatorInitializedCharacterEffect,
        SnapshotAwareCharacterEffect,
        SwitchAwareCharacter {
    private static final double FRAME = 1.0 / 60.0;
    private static final double EPSILON = 1e-9;
    private static final double PARTICLE_TRAVEL = 100.0 * FRAME;
    private static final int[][] NORMAL_HIT_FRAMES = {
        { 27 }, { 19, 33 }, { 60 }
    };
    private static final int[] NORMAL_DURATIONS = { 44, 46, 108 };
    private static final double[][] NORMAL_T9 = {
        { 1.657420 }, { 0.754608, 0.792212 }, { 2.358940 }
    };
    private static final int[] AFTER_SALES_FRAMES = { 72, 85, 85 };
    private static final int BURST_TICK_COUNT = 32;

    /**
     * Normal-attack hitlag from gcsim config YAML pinned at
     * {@code 3647a07a7cc3004bc1e79d9bb5f7444de20dceaa}.
     */
    private static final HitlagProfile NORMAL_FIRST_HITLAG =
            new HitlagProfile(0.10, 0.01, true, false, false);
    private static final HitlagProfile NORMAL_FINAL_HITLAG =
            new HitlagProfile(0.08, 0.01, true, false, false);

    private CombatSimulator initializedSimulator;
    private int normalAttackStep;
    private double c6InfusionUntil = Double.NEGATIVE_INFINITY;
    private List<PendingHit> pendingHits = new ArrayList<>();
    private List<PendingCommand> pendingCommands = new ArrayList<>();

    /** Constructs repository-default C6 Dori. */
    public Dori(Weapon weapon, ArtifactSet artifacts) {
        this(weapon, artifacts, TalentDataManager.getInstance(), 6);
    }

    /** Constructs Dori at an explicit constellation. */
    public Dori(Weapon weapon, ArtifactSet artifacts, int constellation) {
        this(weapon, artifacts, TalentDataManager.getInstance(), constellation);
    }

    /** Constructs Dori with injectable talent data. */
    public Dori(
            Weapon weapon,
            ArtifactSet artifacts,
            TalentDataSource talentData,
            int constellation) {
        super(talentData);
        if (constellation < 0 || constellation > 6) {
            throw new IllegalArgumentException(
                    "Dori constellation must be between 0 and 6");
        }
        name = "Dori";
        characterId = CharacterId.DORI;
        element = Element.ELECTRO;
        this.weapon = weapon;
        this.artifacts = new ArtifactSet[] { artifacts };
        this.constellation = constellation;
        baseStats.set(StatType.BASE_HP,
                getTalentValue("Base HP", 12397.0));
        baseStats.set(StatType.BASE_ATK,
                getTalentValue("Base ATK", 223.0));
        baseStats.set(StatType.BASE_DEF,
                getTalentValue("Base DEF", 723.0));
        baseStats.add(StatType.HP_PERCENT,
                getTalentValue("Ascension HP Percent", 0.24));
        setSkillCD(getTalentValue("Skill Cooldown", 9.0));
        setBurstCD(getTalentValue("Burst Cooldown", 20.0));
    }

    /** Binds delayed work to one simulator. */
    @Override
    public void initializeForSimulator(CombatSimulator simulator) {
        if (simulator == null) {
            throw new IllegalArgumentException("Dori simulator is required");
        }
        if (!simulator.getPartyMembers().contains(this)) {
            throw new IllegalArgumentException(
                    "Dori must belong to the simulator party");
        }
        if (initializedSimulator != null
                && initializedSimulator != simulator) {
            throw new IllegalStateException(
                    "Dori cannot be reused across simulators");
        }
        initializedSimulator = simulator;
    }

    /** Captures combo, infusion, and delayed-work state. */
    @Override
    public State captureCharacterState() {
        return new DoriState(this, normalAttackStep, c6InfusionUntil,
                pendingHits, pendingCommands);
    }

    /** Accepts state from this exact Dori instance only. */
    @Override
    public boolean acceptsCharacterState(State state) {
        return state instanceof DoriState
                && ((DoriState) state).owner == this;
    }

    /** Restores surviving delayed work once. */
    @Override
    public void restoreCharacterState(
            State state,
            CombatSimulator simulator) {
        if (!acceptsCharacterState(state)) {
            throw new IllegalArgumentException("Unexpected Dori state");
        }
        initializeForSimulator(simulator);
        DoriState restored = (DoriState) state;
        normalAttackStep = restored.normalAttackStep;
        c6InfusionUntil = restored.c6InfusionUntil;
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

    /** Returns Dori's 80-Energy Burst cost. */
    @Override
    public double getEnergyCost() {
        return getTalentValue("Energy Cost", 80.0);
    }

    /** Dori has no unconditional represented passive stat. */
    @Override
    public void applyPassive(StatsContainer stats) {
    }

    /** Resets Dori's Normal string on switch-out. */
    @Override
    public void onSwitchOut(CombatSimulator simulator) {
        normalAttackStep = 0;
    }

    /** Returns whether C6 Electro infusion is active. */
    public boolean isC6InfusionActive(double currentTime) {
        return constellation >= 6 && currentTime < c6InfusionUntil;
    }

    /** Returns the number of unresolved Dori-owned hits. */
    public int getPendingHitCount() {
        return pendingHits.size();
    }

    /** Dispatches Dori's represented action set. */
    @Override
    public void onAction(
            CharacterActionRequest request,
            CombatSimulator simulator) {
        if (request == null) {
            throw new IllegalArgumentException("Dori action is required");
        }
        initializeForSimulator(simulator);
        if (request.getKey() != CharacterActionKey.NORMAL) {
            normalAttackStep = 0;
        }
        switch (request.getKey()) {
            case NORMAL:
                normalAttack(simulator);
                break;
            case SKILL:
                spiritWardingLamp(simulator);
                break;
            case BURST:
                alcazarzaraysExactitude(simulator);
                break;
            default:
                throw new IllegalArgumentException(
                        "Unsupported action for Dori: " + request.getKey());
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
                    null));
        }
        normalAttackStep = (normalAttackStep + 1)
                % NORMAL_HIT_FRAMES.length;
        simulator.advanceTime(NORMAL_DURATIONS[step] * FRAME);
    }

    private void spiritWardingLamp(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        StatsContainer snapshot = captureLiveStats(castTime);
        queueCommand(simulator, new PendingCommand(
                castTime + 16.0 * FRAME,
                CommandKind.SKILL_COOLDOWN,
                getTalentValue("Skill Cooldown", 9.0)));
        if (constellation >= 6) {
            c6InfusionUntil = castTime + getTalentValue(
                    "C6 Infusion Duration", 3.8);
        }
        queueHit(simulator, new PendingHit(
                castTime + 26.0 * FRAME,
                HitKind.SKILL,
                0,
                0,
                snapshot));
        int roundCount = constellation >= 1 ? 3 : 2;
        for (int round = 0; round < roundCount; round++) {
            queueHit(simulator, new PendingHit(
                    castTime + AFTER_SALES_FRAMES[round] * FRAME,
                    HitKind.AFTER_SALES,
                    round,
                    0,
                    snapshot));
        }
        double impactTime = castTime + 26.0 * FRAME;
        queueCommand(simulator, new PendingCommand(
                impactTime,
                CommandKind.SELF_ENERGY,
                Math.min(15.0,
                        snapshot.getTotalEnergyRecharge() * 5.0)));
        queueCommand(simulator, new PendingCommand(
                impactTime + PARTICLE_TRAVEL,
                CommandKind.PARTICLE,
                getTalentValue("Particle Count", 2.0)));
        simulator.advanceTime(44.0 * FRAME);
    }

    private void alcazarzaraysExactitude(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        StatsContainer snapshot = captureLiveStats(castTime);
        markBurstCooldownUsed(castTime,
                simulator.getApplicableBuffs(this));
        queueCommand(simulator, new PendingCommand(
                castTime + 4.0 * FRAME,
                CommandKind.BURST_ENERGY,
                0.0));
        for (int tick = 0; tick < BURST_TICK_COUNT; tick++) {
            queueHit(simulator, new PendingHit(
                    castTime + (28.0 + 24.0 * tick) * FRAME,
                    HitKind.BURST_CONNECTOR,
                    tick,
                    0,
                    snapshot));
        }
        double energy = burstValue(
                "Energy Regeneration", 2.4, 2.5);
        for (int pulse = 0; pulse < 6; pulse++) {
            queueCommand(simulator, new PendingCommand(
                    castTime + (11.0 + 120.0 * pulse) * FRAME,
                    CommandKind.ACTIVE_ENERGY,
                    energy));
        }
        simulator.advanceTime(58.0 * FRAME);
    }

    private void resolveHit(CombatSimulator simulator, PendingHit hit) {
        switch (hit.kind) {
            case NORMAL:
                Element normalElement = isC6InfusionActive(hit.time)
                        ? Element.ELECTRO : Element.PHYSICAL;
                performHit(simulator, hit,
                        "Marvelous Sword-Dance N" + (hit.index + 1)
                                + (NORMAL_HIT_FRAMES[hit.index].length > 1
                                        ? " Hit " + (hit.variant + 1) : ""),
                        NORMAL_T9[hit.index][hit.variant],
                        normalElement,
                        StatType.NORMAL_ATTACK_DMG_BONUS,
                        ActionType.NORMAL,
                        ICDType.Standard,
                        ICDTag.NormalAttack,
                        normalElement == Element.ELECTRO ? 1.0 : 0.0);
                break;
            case SKILL:
                performHit(simulator, hit,
                        "Troubleshooter Shot",
                        skillValue("Troubleshooter Shot",
                                2.503760, 2.945600),
                        Element.ELECTRO,
                        StatType.SKILL_DMG_BONUS,
                        ActionType.SKILL,
                        ICDType.Standard,
                        ICDTag.ElementalSkill,
                        1.0);
                break;
            case AFTER_SALES:
                performHit(simulator, hit,
                        "After-Sales Service Round " + (hit.index + 1),
                        skillValue("After-Sales Service Round",
                                0.536520, 0.631200),
                        Element.ELECTRO,
                        StatType.SKILL_DMG_BONUS,
                        ActionType.SKILL,
                        ICDType.Standard,
                        ICDTag.ElementalSkill,
                        1.0);
                break;
            case BURST_CONNECTOR:
                performHit(simulator, hit,
                        "Alcazarzaray's Exactitude Connector "
                                + (hit.index + 1),
                        burstValue("Connector DMG", 0.270001, 0.317648),
                        Element.ELECTRO,
                        StatType.BURST_DMG_BONUS,
                        ActionType.BURST,
                        ICDType.Standard,
                        ICDTag.ElementalBurst,
                        1.0);
                break;
            default:
                throw new IllegalStateException("Unknown Dori hit kind");
        }
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
            Element hitElement,
            StatType bonusStat,
            ActionType actionType,
            ICDType icdType,
            ICDTag icdTag,
            double gauge) {
        AttackAction action = new AttackAction(
                displayName,
                multiplier,
                hitElement,
                StatType.BASE_ATK,
                bonusStat,
                0.0,
                actionType);
        action.setICD(icdType, icdTag, gauge);
        action.setCountsAsSkillDmg(actionType == ActionType.SKILL);
        action.setCountsAsBurstDmg(actionType == ActionType.BURST);
        if (hit.kind == HitKind.NORMAL) {
            if (hit.index == 0) {
                action.setHitlagProfile(NORMAL_FIRST_HITLAG);
            } else if (hit.index == 2) {
                action.setHitlagProfile(NORMAL_FINAL_HITLAG);
            }
        }
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
                            Element.ELECTRO,
                            command.value,
                            ParticleType.PARTICLE);
                    break;
                case SELF_ENERGY:
                    receiveFlatEnergy(command.value);
                    break;
                case ACTIVE_ENERGY:
                    Character active = activeSimulator.getActiveCharacter();
                    if (active != null) {
                        active.receiveFlatEnergy(command.value);
                    }
                    break;
                default:
                    throw new IllegalStateException(
                            "Unknown Dori command kind");
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
        SKILL,
        AFTER_SALES,
        BURST_CONNECTOR
    }

    private enum CommandKind {
        SKILL_COOLDOWN,
        BURST_ENERGY,
        PARTICLE,
        SELF_ENERGY,
        ACTIVE_ENERGY
    }

    private static final class PendingHit {
        private final double time;
        private final HitKind kind;
        private final int index;
        private final int variant;
        private final StatsContainer snapshot;

        private PendingHit(
                double time,
                HitKind kind,
                int index,
                int variant,
                StatsContainer snapshot) {
            this.time = time;
            this.kind = kind;
            this.index = index;
            this.variant = variant;
            this.snapshot = snapshot == null ? null : snapshot.merge(null);
        }

        private PendingHit copy() {
            return new PendingHit(time, kind, index, variant, snapshot);
        }
    }

    private static final class PendingCommand {
        private final double time;
        private final CommandKind kind;
        private final double value;

        private PendingCommand(
                double time,
                CommandKind kind,
                double value) {
            this.time = time;
            this.kind = kind;
            this.value = value;
        }

        private PendingCommand copy() {
            return new PendingCommand(time, kind, value);
        }
    }

    private static final class DoriState implements State {
        private final Dori owner;
        private final int normalAttackStep;
        private final double c6InfusionUntil;
        private final List<PendingHit> pendingHits;
        private final List<PendingCommand> pendingCommands;

        private DoriState(
                Dori owner,
                int normalAttackStep,
                double c6InfusionUntil,
                List<PendingHit> pendingHits,
                List<PendingCommand> pendingCommands) {
            this.owner = owner;
            this.normalAttackStep = normalAttackStep;
            this.c6InfusionUntil = c6InfusionUntil;
            this.pendingHits = copyHits(pendingHits);
            this.pendingCommands = copyCommands(pendingCommands);
        }
    }
}

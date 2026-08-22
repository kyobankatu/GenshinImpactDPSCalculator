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
import simulation.action.HitlagProfile;
import simulation.event.SimpleTimerEvent;

/**
 * Xinyan's fixed-target direct Skill and Riff Revolution slice through C5.
 *
 * <p>Dance on Fire, Sweeping Fervor's initial hit and particles, Riff
 * Revolution's Physical strike and seven Pyro pulses, C2, C3-C5, and C4's
 * resistance window follow pinned gcsim {@code ef41805d}. Delayed hits and
 * windows remain owner-bound and rollback-safe.</p>
 *
 * <p>Shield creation and absorption, shield DoT and C2 shield pulses, A1/A4,
 * C1 CRIT-driven attack speed, C6 Charged scaling, Charged Attack movement,
 * low Plunge, geometry, stamina, and complete hitlag coverage are intentionally excluded.</p>
 */
public final class Xinyan extends Character implements
        SimulatorInitializedCharacterEffect,
        SnapshotAwareCharacterEffect,
        SwitchAwareCharacter {
    private static final double FRAME = 1.0 / 60.0;
    private static final double EPSILON = 1e-9;
    /** Hitlag data pinned to gcsim {@code 3647a07a7cc3004bc1e79d9bb5f7444de20dceaa}. */
    private static final HitlagProfile[] NORMAL_HITLAG = {
        new HitlagProfile(0.10, 0.01, true, false, false),
        new HitlagProfile(0.10, 0.01, true, false, false),
        new HitlagProfile(0.12, 0.01, true, false, false),
        new HitlagProfile(0.12, 0.01, true, false, false)
    };
    private static final HitlagProfile SKILL_HITLAG =
            new HitlagProfile(0.09, 0.01, true, false, false);
    private static final HitlagProfile DEFENSE_ONLY_HITLAG =
            new HitlagProfile(0.0, 0.0, true, false, false);
    private static final double PARTICLE_TRAVEL = 100.0 * FRAME;
    private static final int[] NORMAL_HIT_FRAMES = { 25, 27, 47, 35 };
    private static final int[] NORMAL_DURATIONS = { 33, 39, 64, 94 };
    private static final int[] BURST_DOT_FRAMES = {
        57, 74, 91, 108, 125, 142, 159
    };
    private static final double[] NORMAL_T9 = {
        1.406200, 1.358800, 1.753800, 2.128260
    };

    private CombatSimulator initializedSimulator;
    private int normalAttackStep;
    private double nextParticleAllowedTime = Double.NEGATIVE_INFINITY;
    private List<PendingHit> pendingHits = new ArrayList<>();
    private List<PendingCommand> pendingCommands = new ArrayList<>();

    /** Constructs repository-default C6 Xinyan. */
    public Xinyan(Weapon weapon, ArtifactSet artifacts) {
        this(weapon, artifacts, TalentDataManager.getInstance(), 6);
    }

    /** Constructs Xinyan at an explicit constellation. */
    public Xinyan(Weapon weapon, ArtifactSet artifacts, int constellation) {
        this(weapon, artifacts, TalentDataManager.getInstance(), constellation);
    }

    /** Constructs Xinyan with an injectable talent-data source. */
    public Xinyan(
            Weapon weapon,
            ArtifactSet artifacts,
            TalentDataSource talentData,
            int constellation) {
        super(talentData);
        if (constellation < 0 || constellation > 6) {
            throw new IllegalArgumentException(
                    "Xinyan constellation must be between 0 and 6");
        }
        name = "Xinyan";
        characterId = CharacterId.XINYAN;
        element = Element.PYRO;
        this.weapon = weapon;
        this.artifacts = new ArtifactSet[] { artifacts };
        this.constellation = constellation;
        baseStats.set(StatType.BASE_HP,
                getTalentValue("Base HP", 11201.0));
        baseStats.set(StatType.BASE_ATK,
                getTalentValue("Base ATK", 249.0));
        baseStats.set(StatType.BASE_DEF,
                getTalentValue("Base DEF", 799.0));
        baseStats.add(StatType.ATK_PERCENT,
                getTalentValue("Ascension ATK Percent", 0.24));
        setSkillCD(getTalentValue("Skill Cooldown", 18.0));
        setBurstCD(getTalentValue("Burst Cooldown", 15.0));
    }

    /** Binds delayed work to one party and simulator. */
    @Override
    public void initializeForSimulator(CombatSimulator simulator) {
        if (simulator == null) {
            throw new IllegalArgumentException("Xinyan simulator is required");
        }
        if (!simulator.getPartyMembers().contains(this)) {
            throw new IllegalArgumentException(
                    "Xinyan must belong to the simulator party");
        }
        if (initializedSimulator != null) {
            if (initializedSimulator != simulator) {
                throw new IllegalStateException(
                        "Xinyan cannot be reused across simulators");
            }
            return;
        }
        initializedSimulator = simulator;
    }

    /** Captures combo, particle gate, and all reconstructable future work. */
    @Override
    public State captureCharacterState() {
        return new XinyanState(
                this,
                normalAttackStep,
                nextParticleAllowedTime,
                pendingHits,
                pendingCommands);
    }

    /** Accepts state captured from this exact Xinyan instance only. */
    @Override
    public boolean acceptsCharacterState(State state) {
        return state instanceof XinyanState
                && ((XinyanState) state).owner == this;
    }

    /** Restores state and re-registers each surviving future event once. */
    @Override
    public void restoreCharacterState(
            State state,
            CombatSimulator simulator) {
        if (!acceptsCharacterState(state)) {
            throw new IllegalArgumentException("Unexpected Xinyan state");
        }
        initializeForSimulator(simulator);
        XinyanState restored = (XinyanState) state;
        normalAttackStep = restored.normalAttackStep;
        nextParticleAllowedTime = restored.nextParticleAllowedTime;
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

    /** Returns Xinyan's 60-Energy Burst cost. */
    @Override
    public double getEnergyCost() {
        return getTalentValue("Energy Cost", 60.0);
    }

    /** Xinyan has no unconditional represented stat passive. */
    @Override
    public void applyPassive(StatsContainer stats) {
    }

    /** Resets Dance on Fire progression when Xinyan leaves the field. */
    @Override
    public void onSwitchOut(CombatSimulator simulator) {
        normalAttackStep = 0;
    }

    /** Returns the number of unresolved Xinyan-owned damage hits. */
    public int getPendingHitCount() {
        return pendingHits.size();
    }

    /** Dispatches Xinyan's represented typed action set. */
    @Override
    public void onAction(
            CharacterActionRequest request,
            CombatSimulator simulator) {
        if (request == null) {
            throw new IllegalArgumentException("Xinyan action is required");
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
                sweepingFervor(simulator);
                break;
            case BURST:
                riffRevolution(simulator);
                break;
            case CHARGE:
                throw new IllegalArgumentException(
                        "Xinyan Charged Attack is outside this slice");
            default:
                throw new IllegalArgumentException(
                        "Unsupported action for Xinyan: " + request.getKey());
        }
    }

    private void normalAttack(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        int step = normalAttackStep;
        queueHit(simulator, new PendingHit(
                castTime + NORMAL_HIT_FRAMES[step] * FRAME,
                HitKind.NORMAL,
                step,
                null));
        normalAttackStep = (normalAttackStep + 1)
                % NORMAL_HIT_FRAMES.length;
        simulator.advanceTime(NORMAL_DURATIONS[step] * FRAME);
    }

    private void highPlunge(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        queueHit(simulator, new PendingHit(
                castTime + 40.0 * FRAME,
                HitKind.HIGH_PLUNGE,
                0,
                null));
        simulator.advanceTime(66.0 * FRAME);
    }

    private void sweepingFervor(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        queueCommand(simulator, new PendingCommand(
                castTime + 13.0 * FRAME,
                CommandKind.SKILL_COOLDOWN,
                0.0));
        queueHit(simulator, new PendingHit(
                castTime + 15.0 * FRAME,
                HitKind.SKILL,
                0,
                captureLiveStats(castTime)));
        simulator.advanceTime(53.0 * FRAME);
    }

    private void riffRevolution(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        StatsContainer initialSnapshot = captureLiveStats(castTime);
        if (constellation >= 2) {
            initialSnapshot.add(
                    StatType.CRIT_RATE,
                    getTalentValue(
                            "C2 Physical Burst Critical Rate", 1.0));
        }
        queueCommand(simulator, new PendingCommand(
                castTime + FRAME,
                CommandKind.BURST_COOLDOWN,
                0.0));
        queueCommand(simulator, new PendingCommand(
                castTime + 5.0 * FRAME,
                CommandKind.BURST_ENERGY,
                0.0));
        queueHit(simulator, new PendingHit(
                castTime + 22.0 * FRAME,
                HitKind.BURST_INITIAL,
                0,
                initialSnapshot));
        for (int index = 0; index < BURST_DOT_FRAMES.length; index++) {
            queueHit(simulator, new PendingHit(
                    castTime + BURST_DOT_FRAMES[index] * FRAME,
                    HitKind.BURST_DOT,
                    index,
                    null));
        }
        simulator.advanceTime(86.0 * FRAME);
    }

    private void resolveHit(CombatSimulator simulator, PendingHit hit) {
        switch (hit.kind) {
            case NORMAL:
                performHit(
                        simulator,
                        hit,
                        "Dance on Fire N" + (hit.index + 1),
                        getTalentValue(
                                "N" + (hit.index + 1),
                                NORMAL_T9[hit.index]),
                        Element.PHYSICAL,
                        StatType.NORMAL_ATTACK_DMG_BONUS,
                        ActionType.NORMAL,
                        ICDType.Standard,
                        ICDTag.NormalAttack,
                        0.0);
                break;
            case HIGH_PLUNGE:
                performHit(
                        simulator,
                        hit,
                        "Dance on Fire High Plunge",
                        getTalentValue("High Plunge", 3.422517),
                        Element.PHYSICAL,
                        StatType.PLUNGING_ATTACK_DMG_BONUS,
                        ActionType.PLUNGE,
                        ICDType.None,
                        ICDTag.None,
                        0.0);
                break;
            case SKILL:
                resolveSkillHit(simulator, hit);
                break;
            case BURST_INITIAL:
                performHit(
                        simulator,
                        hit,
                        "Riff Revolution Initial",
                        burstValue(
                                "Riff Revolution Initial",
                                5.793600,
                                6.816000),
                        Element.PHYSICAL,
                        StatType.BURST_DMG_BONUS,
                        ActionType.BURST,
                        ICDType.None,
                        ICDTag.None,
                        0.0);
                break;
            case BURST_DOT:
                performHit(
                        simulator,
                        hit,
                        "Riff Revolution Pyro DoT " + (hit.index + 1),
                        burstValue(
                                "Riff Revolution DoT",
                                0.680000,
                                0.800000),
                        Element.PYRO,
                        StatType.BURST_DMG_BONUS,
                        ActionType.BURST,
                        ICDType.Standard,
                        ICDTag.ElementalBurst,
                        1.0);
                break;
            default:
                throw new IllegalStateException(
                        "Unknown Xinyan hit kind " + hit.kind);
        }
    }

    private void resolveSkillHit(
            CombatSimulator simulator,
            PendingHit hit) {
        performHit(
                simulator,
                hit,
                "Sweeping Fervor Initial",
                getTalentValue(
                        constellation >= 3
                                ? "Sweeping Fervor C3"
                                : "Sweeping Fervor",
                        constellation >= 3 ? 3.392000 : 2.883200),
                Element.PYRO,
                StatType.SKILL_DMG_BONUS,
                ActionType.SKILL,
                ICDType.None,
                ICDTag.None,
                1.0);
        if (simulator.getEnemy() != null
                && hit.time + EPSILON >= nextParticleAllowedTime) {
            nextParticleAllowedTime = hit.time
                    + getTalentValue("Particle Cooldown", 0.2);
            queueCommand(simulator, new PendingCommand(
                    hit.time + PARTICLE_TRAVEL,
                    CommandKind.PARTICLE,
                    getTalentValue("Particle Count", 4.0)));
        }
        if (constellation >= 4) {
            double shred = getTalentValue(
                    "C4 Physical Resistance Shred", 0.15);
            double duration = getTalentValue("C4 Duration", 12.0);
            simulator.applyTeamBuffNoStack(new SimpleBuff(
                    "Xinyan C4 Wildfire Rhythm",
                    BuffId.XINYAN_C4_PHYSICAL_RES_SHRED,
                    duration,
                    hit.time,
                    stats -> stats.add(StatType.PHYS_RES_SHRED, shred))
                    .sourcedBy(characterId));
        }
    }

    private double burstValue(
            String key,
            double talentNine,
            double talentTwelve) {
        String suffix = constellation >= 5 ? " C5" : "";
        return getTalentValue(key + suffix,
                constellation >= 5 ? talentTwelve : talentNine);
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
            double gaugeUnits) {
        AttackAction action = new AttackAction(
                displayName,
                multiplier,
                hitElement,
                StatType.BASE_ATK,
                bonusStat,
                0.0,
                actionType);
        action.setICD(icdType, icdTag, gaugeUnits);
        action.setHitlagProfile(hitlagProfile(hit));
        action.setCountsAsSkillDmg(actionType == ActionType.SKILL);
        action.setCountsAsBurstDmg(actionType == ActionType.BURST);
        action.setStatSnapshot(hit.snapshot == null
                ? captureLiveStats(simulator.getCurrentTime())
                : hit.snapshot);
        simulator.performActionWithoutTimeAdvance(characterId, action);
    }

    private static HitlagProfile hitlagProfile(PendingHit hit) {
        if (hit.kind == HitKind.NORMAL) {
            return NORMAL_HITLAG[hit.index];
        }
        if (hit.kind == HitKind.SKILL) {
            return SKILL_HITLAG;
        }
        if (hit.kind == HitKind.BURST_INITIAL
                || (hit.kind == HitKind.BURST_DOT && hit.index == 0)) {
            return DEFENSE_ONLY_HITLAG;
        }
        return HitlagProfile.none();
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
                    markSkillUsed(
                            activeSimulator.getCurrentTime(),
                            activeSimulator.getApplicableBuffs(this));
                    break;
                case BURST_COOLDOWN:
                    markBurstCooldownUsed(
                            activeSimulator.getCurrentTime(),
                            activeSimulator.getApplicableBuffs(this));
                    break;
                case BURST_ENERGY:
                    spendBurstEnergy(activeSimulator.getCurrentTime());
                    break;
                case PARTICLE:
                    activeSimulator.getEnergyDistributor()
                            .distributeParticles(
                                    Element.PYRO,
                                    command.value,
                                    ParticleType.PARTICLE);
                    break;
                default:
                    throw new IllegalStateException(
                            "Unknown Xinyan command kind " + command.kind);
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
        HIGH_PLUNGE,
        SKILL,
        BURST_INITIAL,
        BURST_DOT
    }

    private enum CommandKind {
        SKILL_COOLDOWN,
        BURST_COOLDOWN,
        BURST_ENERGY,
        PARTICLE
    }

    /** Immutable future hit with queue-time stats where required. */
    private static final class PendingHit {
        private final double time;
        private final HitKind kind;
        private final int index;
        private final StatsContainer snapshot;

        private PendingHit(
                double time,
                HitKind kind,
                int index,
                StatsContainer snapshot) {
            this.time = time;
            this.kind = kind;
            this.index = index;
            this.snapshot = snapshot == null
                    ? null : snapshot.merge(null);
        }

        private PendingHit copy() {
            return new PendingHit(time, kind, index, snapshot);
        }
    }

    /** Immutable delayed state command. */
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

    /** Immutable snapshot of all mutable Xinyan-owned runtime state. */
    private static final class XinyanState implements State {
        private final Xinyan owner;
        private final int normalAttackStep;
        private final double nextParticleAllowedTime;
        private final List<PendingHit> pendingHits;
        private final List<PendingCommand> pendingCommands;

        private XinyanState(
                Xinyan owner,
                int normalAttackStep,
                double nextParticleAllowedTime,
                List<PendingHit> pendingHits,
                List<PendingCommand> pendingCommands) {
            this.owner = owner;
            this.normalAttackStep = normalAttackStep;
            this.nextParticleAllowedTime = nextParticleAllowedTime;
            this.pendingHits = copyHits(pendingHits);
            this.pendingCommands = copyCommands(pendingCommands);
        }
    }
}

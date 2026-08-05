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
import simulation.event.SimpleTimerEvent;

/**
 * Gaming's fixed-target Charmed Cloudstrider and Burst slice through C6.
 *
 * <p>Stellar Rend, Bestial Ascent, Suanni's Gilded Dance, the initial Man Chai
 * return, particles, C3-C6 offensive values, and timing follow pinned gcsim
 * {@code ef41805d}. Delayed work remains owner-bound and snapshot-safe.</p>
 *
 * <p>Player HP loss and healing, overheal-triggered C2, current-HP A4, the
 * HP-gated repeated Man Chai loop, Charged Attack movement, low Plunge,
 * collision, geometry, stamina, and hitlag are intentionally excluded. The
 * initial Burst Man Chai return is independent of those unavailable systems
 * and therefore remains represented.</p>
 */
public final class Gaming extends Character implements
        SimulatorInitializedCharacterEffect,
        SnapshotAwareCharacterEffect,
        SwitchAwareCharacter {
    private static final double FRAME = 1.0 / 60.0;
    private static final double EPSILON = 1e-9;
    private static final double PARTICLE_TRAVEL = 100.0 * FRAME;
    private static final int[] NORMAL_HIT_FRAMES = { 25, 18, 34, 48 };
    private static final int[] NORMAL_DURATIONS = { 30, 32, 79, 87 };
    private static final double[] NORMAL_T9 = {
        1.540611, 1.452210, 1.959311, 2.350692
    };

    private CombatSimulator initializedSimulator;
    private int normalAttackStep;
    private boolean specialPlungeReady;
    private long burstGeneration;
    private double burstStartTime = Double.POSITIVE_INFINITY;
    private double burstExpirationTime = Double.NEGATIVE_INFINITY;
    private double nextParticleAllowedTime = Double.NEGATIVE_INFINITY;
    private List<PendingHit> pendingHits = new ArrayList<>();
    private List<PendingCommand> pendingCommands = new ArrayList<>();

    /** Constructs repository-default C6 Gaming. */
    public Gaming(Weapon weapon, ArtifactSet artifacts) {
        this(weapon, artifacts, TalentDataManager.getInstance(), 6);
    }

    /** Constructs Gaming at an explicit constellation. */
    public Gaming(Weapon weapon, ArtifactSet artifacts, int constellation) {
        this(weapon, artifacts, TalentDataManager.getInstance(), constellation);
    }

    /** Constructs Gaming with an injectable talent-data source. */
    public Gaming(
            Weapon weapon,
            ArtifactSet artifacts,
            TalentDataSource talentData,
            int constellation) {
        super(talentData);
        if (constellation < 0 || constellation > 6) {
            throw new IllegalArgumentException(
                    "Gaming constellation must be between 0 and 6");
        }
        name = "Gaming";
        characterId = CharacterId.GAMING;
        element = Element.PYRO;
        this.weapon = weapon;
        this.artifacts = new ArtifactSet[] { artifacts };
        this.constellation = constellation;
        baseStats.set(StatType.BASE_HP,
                getTalentValue("Base HP", 11419.0));
        baseStats.set(StatType.BASE_ATK,
                getTalentValue("Base ATK", 302.0));
        baseStats.set(StatType.BASE_DEF,
                getTalentValue("Base DEF", 703.0));
        baseStats.add(StatType.ATK_PERCENT,
                getTalentValue("Ascension ATK Percent", 0.24));
        setSkillCD(getTalentValue("Skill Cooldown", 6.0));
        setBurstCD(getTalentValue("Burst Cooldown", 15.0));
    }

    /** Binds mutable Gaming state to exactly one party and simulator. */
    @Override
    public void initializeForSimulator(CombatSimulator simulator) {
        if (simulator == null) {
            throw new IllegalArgumentException("Gaming simulator is required");
        }
        if (!simulator.getPartyMembers().contains(this)) {
            throw new IllegalArgumentException(
                    "Gaming must belong to the simulator party");
        }
        if (initializedSimulator != null) {
            if (initializedSimulator != simulator) {
                throw new IllegalStateException(
                        "Gaming cannot be reused across simulators");
            }
            return;
        }
        initializedSimulator = simulator;
    }

    /** Captures combo, forms, trigger gates, and reconstructable future work. */
    @Override
    public State captureCharacterState() {
        return new GamingState(
                this,
                normalAttackStep,
                specialPlungeReady,
                burstGeneration,
                burstStartTime,
                burstExpirationTime,
                nextParticleAllowedTime,
                pendingHits,
                pendingCommands);
    }

    /** Accepts state captured from this exact Gaming instance only. */
    @Override
    public boolean acceptsCharacterState(State state) {
        return state instanceof GamingState
                && ((GamingState) state).owner == this;
    }

    /** Restores mutable state and re-registers each future event once. */
    @Override
    public void restoreCharacterState(
            State state,
            CombatSimulator simulator) {
        if (!acceptsCharacterState(state)) {
            throw new IllegalArgumentException("Unexpected Gaming state");
        }
        initializeForSimulator(simulator);
        GamingState restored = (GamingState) state;
        normalAttackStep = restored.normalAttackStep;
        specialPlungeReady = restored.specialPlungeReady;
        burstGeneration = restored.burstGeneration;
        burstStartTime = restored.burstStartTime;
        burstExpirationTime = restored.burstExpirationTime;
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

    /** Returns Gaming's 60-Energy Burst cost. */
    @Override
    public double getEnergyCost() {
        return getTalentValue("Energy Cost", 60.0);
    }

    /** Gaming has no unconditional represented stat passive. */
    @Override
    public void applyPassive(StatsContainer stats) {
    }

    /** Cancels the Burst form and resets the Normal string on switch-out. */
    @Override
    public void onSwitchOut(CombatSimulator simulator) {
        normalAttackStep = 0;
        burstStartTime = Double.POSITIVE_INFINITY;
        burstExpirationTime = Double.NEGATIVE_INFINITY;
    }

    /** Returns whether Suanni's Gilded Dance is active at this timestamp. */
    public boolean isBurstActive(double currentTime) {
        return currentTime + EPSILON >= burstStartTime
                && currentTime + EPSILON < burstExpirationTime;
    }

    /** Returns whether the next represented Plunge is Charmed Cloudstrider. */
    public boolean isSpecialPlungeReady() {
        return specialPlungeReady;
    }

    /** Returns the next timestamp at which a special Plunge may make particles. */
    public double getNextParticleAllowedTime() {
        return nextParticleAllowedTime;
    }

    /** Returns the number of unresolved Gaming-owned damage hits. */
    public int getPendingHitCount() {
        return pendingHits.size();
    }

    /** Dispatches Gaming's represented typed action set. */
    @Override
    public void onAction(
            CharacterActionRequest request,
            CombatSimulator simulator) {
        if (request == null) {
            throw new IllegalArgumentException("Gaming action is required");
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
                if (specialPlungeReady) {
                    charmedCloudstrider(simulator);
                } else {
                    highPlunge(simulator);
                }
                break;
            case SKILL:
                bestialAscent(simulator);
                break;
            case BURST:
                suanniGildedDance(simulator);
                break;
            case CHARGE:
                throw new IllegalArgumentException(
                        "Gaming Charged Attack movement is outside this slice");
            default:
                throw new IllegalArgumentException(
                        "Unsupported action for Gaming: " + request.getKey());
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
                castTime + 46.0 * FRAME,
                HitKind.HIGH_PLUNGE,
                0,
                null));
        simulator.advanceTime(69.0 * FRAME);
    }

    private void bestialAscent(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        specialPlungeReady = true;
        queueCommand(simulator, new PendingCommand(
                castTime + 4.0 * FRAME,
                CommandKind.SKILL_COOLDOWN,
                0.0,
                0L));
        simulator.advanceTime(23.0 * FRAME);
    }

    private void charmedCloudstrider(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        specialPlungeReady = false;
        StatsContainer snapshot = captureLiveStats(castTime);
        if (constellation >= 6) {
            snapshot.add(StatType.CRIT_RATE,
                    getTalentValue("C6 Critical Rate", 0.20));
            snapshot.add(StatType.CRIT_DMG,
                    getTalentValue("C6 Critical Damage", 0.40));
        }
        queueHit(simulator, new PendingHit(
                castTime + 32.0 * FRAME,
                HitKind.SPECIAL_PLUNGE,
                0,
                snapshot));
        simulator.advanceTime(52.0 * FRAME);
    }

    private void suanniGildedDance(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        long generation = ++burstGeneration;
        markBurstCooldownUsed(
                castTime, simulator.getApplicableBuffs(this));
        queueCommand(simulator, new PendingCommand(
                castTime + 7.0 * FRAME,
                CommandKind.BURST_ENERGY,
                0.0,
                generation));
        queueCommand(simulator, new PendingCommand(
                castTime + 36.0 * FRAME,
                CommandKind.BURST_START,
                0.0,
                generation));
        queueHit(simulator, new PendingHit(
                castTime + 60.0 * FRAME,
                HitKind.BURST,
                0,
                captureLiveStats(castTime)));
        queueCommand(simulator, new PendingCommand(
                castTime + 161.0 * FRAME,
                CommandKind.MAN_CHAI_RETURN,
                0.0,
                generation));
        simulator.advanceTime(63.0 * FRAME);
    }

    private void resolveHit(CombatSimulator simulator, PendingHit hit) {
        switch (hit.kind) {
            case NORMAL:
                performHit(
                        simulator,
                        hit,
                        "Stellar Rend N" + (hit.index + 1),
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
                        "Stellar Rend High Plunge",
                        getTalentValue("High Plunge", 2.943366),
                        Element.PHYSICAL,
                        StatType.PLUNGING_ATTACK_DMG_BONUS,
                        ActionType.PLUNGE,
                        ICDType.None,
                        ICDTag.None,
                        0.0);
                break;
            case SPECIAL_PLUNGE:
                resolveSpecialPlunge(simulator, hit);
                break;
            case BURST:
                performHit(
                        simulator,
                        hit,
                        "Suanni's Gilded Dance",
                        getTalentValue(
                                constellation >= 5
                                        ? "Suanni Gilded Dance C5"
                                        : "Suanni Gilded Dance",
                                constellation >= 5 ? 7.408000 : 6.296800),
                        Element.PYRO,
                        StatType.BURST_DMG_BONUS,
                        ActionType.BURST,
                        ICDType.Standard,
                        ICDTag.ElementalBurst,
                        1.0);
                break;
            default:
                throw new IllegalStateException(
                        "Unknown Gaming hit kind " + hit.kind);
        }
    }

    private void resolveSpecialPlunge(
            CombatSimulator simulator,
            PendingHit hit) {
        performHit(
                simulator,
                hit,
                "Charmed Cloudstrider",
                getTalentValue(
                        constellation >= 3
                                ? "Charmed Cloudstrider C3"
                                : "Charmed Cloudstrider",
                        constellation >= 3 ? 4.608000 : 3.916800),
                Element.PYRO,
                StatType.PLUNGING_ATTACK_DMG_BONUS,
                ActionType.PLUNGE,
                ICDType.None,
                ICDTag.None,
                1.0);
        if (simulator.getEnemy() != null
                && hit.time + EPSILON >= nextParticleAllowedTime) {
            nextParticleAllowedTime = hit.time
                    + getTalentValue("Particle Cooldown", 3.0);
            queueCommand(simulator, new PendingCommand(
                    hit.time + PARTICLE_TRAVEL,
                    CommandKind.PARTICLE,
                    getTalentValue("Particle Count", 2.0),
                    0L));
        }
        if (constellation >= 4) {
            receiveFlatEnergy(getTalentValue("C4 Energy", 2.0));
        }
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
        action.setCountsAsSkillDmg(false);
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
                    markSkillUsed(
                            activeSimulator.getCurrentTime(),
                            activeSimulator.getApplicableBuffs(this));
                    break;
                case BURST_START:
                    if (command.generation == burstGeneration) {
                        burstStartTime = activeSimulator.getCurrentTime();
                        burstExpirationTime = burstStartTime
                                + getTalentValue("Burst Duration", 12.0);
                    }
                    break;
                case BURST_ENERGY:
                    spendBurstEnergy(activeSimulator.getCurrentTime());
                    break;
                case MAN_CHAI_RETURN:
                    if (command.generation == burstGeneration
                            && activeSimulator.getActiveCharacter() == this) {
                        resetSkillCooldown(activeSimulator.getCurrentTime());
                    }
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
                            "Unknown Gaming command kind " + command.kind);
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
        SPECIAL_PLUNGE,
        BURST
    }

    private enum CommandKind {
        SKILL_COOLDOWN,
        BURST_START,
        BURST_ENERGY,
        MAN_CHAI_RETURN,
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
        private final long generation;

        private PendingCommand(
                double time,
                CommandKind kind,
                double value,
                long generation) {
            this.time = time;
            this.kind = kind;
            this.value = value;
            this.generation = generation;
        }

        private PendingCommand copy() {
            return new PendingCommand(time, kind, value, generation);
        }
    }

    /** Immutable snapshot of all mutable Gaming-owned runtime state. */
    private static final class GamingState implements State {
        private final Gaming owner;
        private final int normalAttackStep;
        private final boolean specialPlungeReady;
        private final long burstGeneration;
        private final double burstStartTime;
        private final double burstExpirationTime;
        private final double nextParticleAllowedTime;
        private final List<PendingHit> pendingHits;
        private final List<PendingCommand> pendingCommands;

        private GamingState(
                Gaming owner,
                int normalAttackStep,
                boolean specialPlungeReady,
                long burstGeneration,
                double burstStartTime,
                double burstExpirationTime,
                double nextParticleAllowedTime,
                List<PendingHit> pendingHits,
                List<PendingCommand> pendingCommands) {
            this.owner = owner;
            this.normalAttackStep = normalAttackStep;
            this.specialPlungeReady = specialPlungeReady;
            this.burstGeneration = burstGeneration;
            this.burstStartTime = burstStartTime;
            this.burstExpirationTime = burstExpirationTime;
            this.nextParticleAllowedTime = nextParticleAllowedTime;
            this.pendingHits = copyHits(pendingHits);
            this.pendingCommands = copyCommands(pendingCommands);
        }
    }
}

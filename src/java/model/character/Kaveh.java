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
import simulation.event.SimpleTimerEvent;

/**
 * Kaveh's fixed-target offensive and Bloom-support slice through C6.
 *
 * <p>Normals, Skill, particles, Painted Dome damage and infusion, the party
 * Bloom bonus, A4 EM stacks, and represented constellations follow pinned
 * gcsim {@code ef41805d}. Mutable form and delayed-hit state is owner-bound and
 * rollback-safe.</p>
 *
 * <p>Dendro Core forced rupture, A1 Bloom self-healing, C1 Dendro resistance,
 * C6 Core rupture, Charged/Plunge attacks, geometry, hitlag, and interruption
 * resistance are excluded because their required runtime contracts are not
 * available in this bounded content slice.</p>
 */
public final class Kaveh extends Character implements
        SimulatorInitializedCharacterEffect,
        SnapshotAwareCharacterEffect,
        SwitchAwareCharacter {
    private static final double FRAME = 1.0 / 60.0;
    private static final double EPSILON = 1e-9;
    private static final double PARTICLE_TRAVEL = 100.0 * FRAME;
    private static final int[] NORMAL_HIT_FRAMES = { 27, 22, 33, 40 };
    private static final int[] NORMAL_DURATIONS = { 44, 45, 56, 81 };
    private static final double[] NORMAL_T9 = {
        1.399690, 1.279405, 1.548052, 1.886599
    };

    private CombatSimulator initializedSimulator;
    private int normalAttackStep;
    private long burstGeneration;
    private double burstStartTime = Double.POSITIVE_INFINITY;
    private double burstExpirationTime = Double.NEGATIVE_INFINITY;
    private double c1ExpirationTime = Double.NEGATIVE_INFINITY;
    private int a4Stacks;
    private double nextA4TriggerTime = Double.NEGATIVE_INFINITY;
    private double nextC6TriggerTime = Double.NEGATIVE_INFINITY;
    private List<PendingHit> pendingHits = new ArrayList<>();
    private List<PendingCommand> pendingCommands = new ArrayList<>();

    /** Constructs repository-default C6 Kaveh. */
    public Kaveh(Weapon weapon, ArtifactSet artifacts) {
        this(weapon, artifacts, TalentDataManager.getInstance(), 6);
    }

    /** Constructs Kaveh at an explicit constellation. */
    public Kaveh(Weapon weapon, ArtifactSet artifacts, int constellation) {
        this(weapon, artifacts, TalentDataManager.getInstance(), constellation);
    }

    /** Constructs Kaveh with injectable talent data. */
    public Kaveh(
            Weapon weapon,
            ArtifactSet artifacts,
            TalentDataSource talentData,
            int constellation) {
        super(talentData);
        if (constellation < 0 || constellation > 6) {
            throw new IllegalArgumentException(
                    "Kaveh constellation must be between 0 and 6");
        }
        name = "Kaveh";
        characterId = CharacterId.KAVEH;
        element = Element.DENDRO;
        this.weapon = weapon;
        this.artifacts = new ArtifactSet[] { artifacts };
        this.constellation = constellation;
        baseStats.set(StatType.BASE_HP,
                getTalentValue("Base HP", 11962.0));
        baseStats.set(StatType.BASE_ATK,
                getTalentValue("Base ATK", 234.0));
        baseStats.set(StatType.BASE_DEF,
                getTalentValue("Base DEF", 751.0));
        baseStats.add(StatType.ELEMENTAL_MASTERY,
                getTalentValue("Ascension Elemental Mastery", 96.0));
        setSkillCD(getTalentValue("Skill Cooldown", 6.0));
        setBurstCD(getTalentValue("Burst Cooldown", 20.0));
    }

    /** Binds form listeners and delayed work to exactly one simulator. */
    @Override
    public void initializeForSimulator(CombatSimulator simulator) {
        if (simulator == null) {
            throw new IllegalArgumentException("Kaveh simulator is required");
        }
        if (initializedSimulator != null) {
            if (initializedSimulator != simulator) {
                throw new IllegalStateException(
                        "Kaveh cannot be reused across simulators");
            }
            return;
        }
        if (!simulator.getPartyMembers().contains(this)) {
            throw new IllegalArgumentException(
                    "Kaveh must belong to the simulator party");
        }
        initializedSimulator = simulator;
        simulator.addDamageListener((actor, action, damage, time) ->
                handleResolvedDamage(simulator, actor, action, time));
    }

    /** Captures form, stack, trigger-gate, combo, and delayed-work state. */
    @Override
    public State captureCharacterState() {
        return new KavehState(
                this,
                normalAttackStep,
                burstGeneration,
                burstStartTime,
                burstExpirationTime,
                c1ExpirationTime,
                a4Stacks,
                nextA4TriggerTime,
                nextC6TriggerTime,
                pendingHits,
                pendingCommands);
    }

    /** Accepts only state captured from this exact Kaveh instance. */
    @Override
    public boolean acceptsCharacterState(State state) {
        return state instanceof KavehState
                && ((KavehState) state).owner == this;
    }

    /** Restores surviving Kaveh-owned delayed work once. */
    @Override
    public void restoreCharacterState(
            State state,
            CombatSimulator simulator) {
        if (!acceptsCharacterState(state)) {
            throw new IllegalArgumentException("Unexpected Kaveh state");
        }
        initializeForSimulator(simulator);
        KavehState restored = (KavehState) state;
        normalAttackStep = restored.normalAttackStep;
        burstGeneration = restored.burstGeneration;
        burstStartTime = restored.burstStartTime;
        burstExpirationTime = restored.burstExpirationTime;
        c1ExpirationTime = restored.c1ExpirationTime;
        a4Stacks = restored.a4Stacks;
        nextA4TriggerTime = restored.nextA4TriggerTime;
        nextC6TriggerTime = restored.nextC6TriggerTime;
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

    /** Returns Kaveh's 80-Energy Burst cost. */
    @Override
    public double getEnergyCost() {
        return getTalentValue("Energy Cost", 80.0);
    }

    /** Applies Kaveh-owned C1, C2, C4, and A4 stats. */
    @Override
    public void applyPassive(StatsContainer stats) {
        double currentTime = initializedSimulator == null
                ? Double.NEGATIVE_INFINITY
                : initializedSimulator.getCurrentTime();
        if (constellation >= 1 && currentTime < c1ExpirationTime) {
            stats.add(StatType.HEALING_BONUS,
                    getTalentValue("C1 Healing Bonus", 0.25));
        }
        if (isBurstActive(currentTime)) {
            stats.add(StatType.ELEMENTAL_MASTERY,
                    a4Stacks * getTalentValue(
                            "A4 Elemental Mastery Per Stack", 25.0));
            if (constellation >= 2) {
                stats.add(StatType.NORMAL_ATTACK_SPD,
                        getTalentValue("C2 Normal Attack Speed", 0.15));
            }
        }
        if (constellation >= 4) {
            stats.add(StatType.BLOOM_DMG_BONUS,
                    getTalentValue("C4 Bloom DMG Bonus", 0.60));
        }
    }

    /** Ends Painted Dome and resets Kaveh's Normal string on switch-out. */
    @Override
    public void onSwitchOut(CombatSimulator simulator) {
        normalAttackStep = 0;
        endBurst(simulator, simulator.getCurrentTime());
    }

    /** Resets Kaveh's Normal string on entry. */
    @Override
    public void onSwitchIn(CombatSimulator simulator) {
        normalAttackStep = 0;
    }

    /** Returns whether Painted Dome is active at a supplied timestamp. */
    public boolean isBurstActive(double currentTime) {
        return currentTime >= burstStartTime
                && currentTime < burstExpirationTime;
    }

    /** Returns current A4 EM stacks, failing closed outside Painted Dome. */
    public int getA4Stacks(double currentTime) {
        return isBurstActive(currentTime) ? a4Stacks : 0;
    }

    /** Returns the number of unresolved Kaveh-owned hits. */
    public int getPendingHitCount() {
        return pendingHits.size();
    }

    /** Dispatches Kaveh's represented action set. */
    @Override
    public void onAction(
            CharacterActionRequest request,
            CombatSimulator simulator) {
        if (request == null) {
            throw new IllegalArgumentException("Kaveh action is required");
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
                artisticIngenuity(simulator);
                break;
            case BURST:
                paintedDome(simulator);
                break;
            default:
                throw new IllegalArgumentException(
                        "Unsupported action for Kaveh: " + request.getKey());
        }
    }

    private void normalAttack(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        int step = normalAttackStep;
        double hitDelay = resolveNormalDuration(
                simulator, NORMAL_HIT_FRAMES[step] * FRAME);
        queueHit(simulator, new PendingHit(
                castTime + hitDelay,
                HitKind.NORMAL,
                step,
                burstGeneration,
                null));
        normalAttackStep = (normalAttackStep + 1)
                % NORMAL_HIT_FRAMES.length;
        simulator.advanceTime(resolveNormalDuration(
                simulator, NORMAL_DURATIONS[step] * FRAME));
    }

    private void artisticIngenuity(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        StatsContainer snapshot = captureLiveStats(castTime);
        queueCommand(simulator, new PendingCommand(
                castTime + 33.0 * FRAME,
                CommandKind.SKILL_COOLDOWN,
                getTalentValue("Skill Cooldown", 6.0),
                burstGeneration));
        if (constellation >= 1) {
            queueCommand(simulator, new PendingCommand(
                    castTime + 33.0 * FRAME,
                    CommandKind.C1,
                    getTalentValue("C1 Duration", 3.0),
                    burstGeneration));
        }
        double impactTime = castTime + 35.0 * FRAME;
        queueHit(simulator, new PendingHit(
                impactTime,
                HitKind.SKILL,
                0,
                burstGeneration,
                snapshot));
        queueCommand(simulator, new PendingCommand(
                impactTime + PARTICLE_TRAVEL,
                CommandKind.PARTICLE,
                getTalentValue("Particle Count", 2.0),
                burstGeneration));
        simulator.advanceTime(52.0 * FRAME);
    }

    private void paintedDome(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        StatsContainer snapshot = captureLiveStats(castTime);
        markBurstCooldownUsed(castTime,
                simulator.getApplicableBuffs(this));
        queueCommand(simulator, new PendingCommand(
                castTime + 3.0 * FRAME,
                CommandKind.BURST_ENERGY,
                0.0,
                burstGeneration));
        queueHit(simulator, new PendingHit(
                castTime + 36.0 * FRAME,
                HitKind.BURST,
                0,
                burstGeneration,
                snapshot));
        simulator.advanceTime(49.0 * FRAME);
    }

    private void handleResolvedDamage(
            CombatSimulator simulator,
            Character actor,
            AttackAction action,
            double time) {
        if (actor != this
                || action == null
                || action.getActionType() != ActionType.NORMAL
                || !isBurstActive(time)) {
            return;
        }
        if (time + EPSILON >= nextA4TriggerTime
                && a4Stacks < (int) getTalentValue(
                        "A4 Max Stacks", 4.0)) {
            a4Stacks++;
            nextA4TriggerTime = time + getTalentValue(
                    "A4 Trigger Cooldown", 0.1);
        }
        if (constellation >= 6
                && time + EPSILON >= nextC6TriggerTime) {
            nextC6TriggerTime = time + getTalentValue(
                    "C6 Trigger Cooldown", 3.0);
            queueHit(simulator, new PendingHit(
                    time + getTalentValue("C6 Delay", 0.3),
                    HitKind.C6,
                    0,
                    burstGeneration,
                    captureLiveStats(time)));
        }
    }

    private void resolveHit(CombatSimulator simulator, PendingHit hit) {
        if (hit.kind == HitKind.C6
                && (hit.generation != burstGeneration
                        || !isBurstActive(hit.time))) {
            return;
        }
        switch (hit.kind) {
            case NORMAL:
                Element normalElement = isBurstActive(hit.time)
                        ? Element.DENDRO : Element.PHYSICAL;
                performHit(simulator, hit,
                        "Schematic Setup N" + (hit.index + 1),
                        NORMAL_T9[hit.index],
                        normalElement,
                        StatType.NORMAL_ATTACK_DMG_BONUS,
                        ActionType.NORMAL,
                        ICDType.Standard,
                        ICDTag.NormalAttack,
                        normalElement == Element.DENDRO ? 1.0 : 0.0);
                break;
            case SKILL:
                performHit(simulator, hit,
                        "Artistic Ingenuity",
                        skillValue(),
                        Element.DENDRO,
                        StatType.SKILL_DMG_BONUS,
                        ActionType.SKILL,
                        ICDType.None,
                        ICDTag.ElementalSkill,
                        1.0);
                break;
            case BURST:
                performHit(simulator, hit,
                        "Painted Dome",
                        burstValue("Painted Dome", 2.72, 3.20),
                        Element.DENDRO,
                        StatType.BURST_DMG_BONUS,
                        ActionType.BURST,
                        ICDType.None,
                        ICDTag.ElementalBurst,
                        2.0);
                activateBurst(simulator, hit.time);
                break;
            case C6:
                performHit(simulator, hit,
                        "Pairidaeza's Dreams",
                        getTalentValue("C6 Multiplier", 0.618),
                        Element.DENDRO,
                        null,
                        ActionType.OTHER,
                        ICDType.None,
                        ICDTag.None,
                        1.0);
                break;
            default:
                throw new IllegalStateException("Unknown Kaveh hit kind");
        }
    }

    private void activateBurst(
            CombatSimulator simulator,
            double currentTime) {
        burstGeneration++;
        burstStartTime = currentTime;
        burstExpirationTime = currentTime
                + getTalentValue("Burst Duration", 12.0);
        a4Stacks = 0;
        nextA4TriggerTime = currentTime;
        nextC6TriggerTime = currentTime;
        double bloomBonus = burstValue(
                "Bloom DMG Bonus", 0.467296, 0.549760);
        simulator.applyTeamBuffNoStack(new SimpleBuff(
                "Kaveh Painted Dome Bloom DMG",
                BuffId.KAVEH_PAINTED_DOME_BLOOM_DMG,
                getTalentValue("Burst Duration", 12.0),
                currentTime,
                stats -> stats.add(
                        StatType.BLOOM_DMG_BONUS, bloomBonus))
                .sourcedBy(characterId));
    }

    private void endBurst(
            CombatSimulator simulator,
            double currentTime) {
        if (isBurstActive(currentTime)) {
            burstExpirationTime = currentTime;
        }
        burstGeneration++;
        a4Stacks = 0;
        simulator.removeTeamBuffsById(
                BuffId.KAVEH_PAINTED_DOME_BLOOM_DMG);
    }

    private double skillValue() {
        String suffix = constellation >= 5 ? " C5" : "";
        return getTalentValue("Artistic Ingenuity" + suffix,
                constellation >= 5 ? 4.08 : 3.468);
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
        action.setStatSnapshot(hit.snapshot == null
                ? captureLiveStats(simulator.getCurrentTime())
                : hit.snapshot);
        simulator.performActionWithoutTimeAdvance(characterId, action);
    }

    private double resolveNormalDuration(
            CombatSimulator simulator,
            double duration) {
        StatsContainer stats = captureLiveStats(simulator.getCurrentTime());
        double speed = Math.min(
                0.60,
                stats.get(StatType.ATK_SPD)
                        + stats.get(StatType.NORMAL_ATTACK_SPD));
        return speed <= 0.0 ? duration : duration / (1.0 + speed);
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
                            Element.DENDRO,
                            command.value,
                            ParticleType.PARTICLE);
                    break;
                case C1:
                    c1ExpirationTime = activeSimulator.getCurrentTime()
                            + command.value;
                    break;
                default:
                    throw new IllegalStateException(
                            "Unknown Kaveh command kind");
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
        BURST,
        C6
    }

    private enum CommandKind {
        SKILL_COOLDOWN,
        BURST_ENERGY,
        PARTICLE,
        C1
    }

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
            this.snapshot = snapshot == null ? null : snapshot.merge(null);
        }

        private PendingHit copy() {
            return new PendingHit(
                    time, kind, index, generation, snapshot);
        }
    }

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

    private static final class KavehState implements State {
        private final Kaveh owner;
        private final int normalAttackStep;
        private final long burstGeneration;
        private final double burstStartTime;
        private final double burstExpirationTime;
        private final double c1ExpirationTime;
        private final int a4Stacks;
        private final double nextA4TriggerTime;
        private final double nextC6TriggerTime;
        private final List<PendingHit> pendingHits;
        private final List<PendingCommand> pendingCommands;

        private KavehState(
                Kaveh owner,
                int normalAttackStep,
                long burstGeneration,
                double burstStartTime,
                double burstExpirationTime,
                double c1ExpirationTime,
                int a4Stacks,
                double nextA4TriggerTime,
                double nextC6TriggerTime,
                List<PendingHit> pendingHits,
                List<PendingCommand> pendingCommands) {
            this.owner = owner;
            this.normalAttackStep = normalAttackStep;
            this.burstGeneration = burstGeneration;
            this.burstStartTime = burstStartTime;
            this.burstExpirationTime = burstExpirationTime;
            this.c1ExpirationTime = c1ExpirationTime;
            this.a4Stacks = a4Stacks;
            this.nextA4TriggerTime = nextA4TriggerTime;
            this.nextC6TriggerTime = nextC6TriggerTime;
            this.pendingHits = copyHits(pendingHits);
            this.pendingCommands = copyCommands(pendingCommands);
        }
    }
}

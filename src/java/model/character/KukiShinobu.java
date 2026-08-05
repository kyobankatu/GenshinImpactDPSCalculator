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
import simulation.event.SimpleTimerEvent;

/**
 * Kuki Shinobu's fixed-full-HP offensive ring kit.
 *
 * <p>This bounded implementation follows pinned gcsim {@code ef41805d} for
 * Normal, Charged, Skill, ring, Burst, A4, and C2-C5 timing. The simulator has
 * no player current-HP or healing state, so Skill HP consumption, healing,
 * low-HP Burst, A1, and C6 remain outside this slice. C1's radius increase has
 * no effect in the stationary single-target abstraction.</p>
 *
 * <p>Ring and C4 damage use live hit-time stats. Burst captures one cast-time
 * snapshot. Particle rolls are consumed in hit order from an append-only tape;
 * snapshots restore its cursor so future branches replay the same outcomes.</p>
 */
public final class KukiShinobu extends Character implements
        SimulatorInitializedCharacterEffect,
        SnapshotAwareCharacterEffect,
        SwitchAwareCharacter {
    private static final double FRAME = 1.0 / 60.0;
    private static final double EPSILON = 1e-9;
    private static final double PARTICLE_TRAVEL = 100.0 * FRAME;
    private static final int[] NORMAL_HIT_FRAMES = { 12, 13, 13, 23 };
    private static final int[] NORMAL_DURATIONS = { 19, 17, 42, 57 };
    private static final double[] NORMAL_MULTIPLIERS = {
        0.89586, 0.81844, 1.0902, 1.3983
    };
    private static final int[] CHARGED_HIT_FRAMES = { 14, 25 };
    private static final double[] CHARGED_MULTIPLIERS = {
        1.022102, 1.226617
    };
    private static final int[] BURST_HIT_FRAMES = {
        50, 67, 84, 101, 118, 135, 152
    };

    private final DoubleSupplier particleDrawSource;
    private CombatSimulator initializedSimulator;
    private int normalAttackStep;
    private long skillGeneration;
    private long ringGeneration;
    private long burstGeneration;
    private double ringExpirationTime = Double.NEGATIVE_INFINITY;
    private double nextParticleTime = Double.NEGATIVE_INFINITY;
    private double nextC4Time = Double.NEGATIVE_INFINITY;
    private final List<Double> particleDrawTape = new ArrayList<>();
    private int particleDrawCursor;
    private List<PendingHit> pendingHits = new ArrayList<>();
    private List<PendingCommand> pendingCommands = new ArrayList<>();

    /** Constructs repository-default C6 Kuki with the shared data source. */
    public KukiShinobu(Weapon weapon, ArtifactSet artifacts) {
        this(weapon, artifacts, TalentDataManager.getInstance(), 6,
                Math::random);
    }

    /** Constructs Kuki at an explicit constellation. */
    public KukiShinobu(
            Weapon weapon,
            ArtifactSet artifacts,
            int constellation) {
        this(weapon, artifacts, TalentDataManager.getInstance(), constellation,
                Math::random);
    }

    /** Constructs Kuki with injectable talent data and particle draws. */
    public KukiShinobu(
            Weapon weapon,
            ArtifactSet artifacts,
            TalentDataSource talentData,
            int constellation,
            DoubleSupplier particleDrawSource) {
        super(talentData);
        if (constellation < 0 || constellation > 6) {
            throw new IllegalArgumentException(
                    "Kuki Shinobu constellation must be between 0 and 6");
        }
        if (particleDrawSource == null) {
            throw new IllegalArgumentException(
                    "Kuki Shinobu particle draw source is required");
        }
        name = "Kuki Shinobu";
        characterId = CharacterId.KUKI_SHINOBU;
        element = Element.ELECTRO;
        this.weapon = weapon;
        this.artifacts = new ArtifactSet[] { artifacts };
        this.constellation = constellation;
        this.particleDrawSource = particleDrawSource;
        baseStats.set(StatType.BASE_HP,
                getTalentValue("Base HP", 12289.0));
        baseStats.set(StatType.BASE_ATK,
                getTalentValue("Base ATK", 212.0));
        baseStats.set(StatType.BASE_DEF,
                getTalentValue("Base DEF", 751.0));
        baseStats.add(StatType.HP_PERCENT,
                getTalentValue("Ascension HP Percent", 0.24));
        setSkillCD(15.0);
        setBurstCD(15.0);
    }

    /** Binds Kuki's C4 listener and delayed state to one simulator. */
    @Override
    public void initializeForSimulator(CombatSimulator simulator) {
        if (simulator == null) {
            throw new IllegalArgumentException(
                    "Kuki Shinobu simulator is required");
        }
        if (initializedSimulator != null
                && initializedSimulator != simulator) {
            throw new IllegalStateException(
                    "Kuki Shinobu cannot be reused across simulators");
        }
        if (initializedSimulator == simulator) {
            return;
        }
        if (!simulator.getPartyMembers().contains(this)) {
            throw new IllegalArgumentException(
                    "Kuki Shinobu must belong to the target simulator party");
        }
        initializedSimulator = simulator;
        simulator.addDamageListener((actor, action, damage, time) ->
                onResolvedDamage(actor, action, damage, time, simulator));
    }

    /** Captures Kuki-owned gates, generations, and future work. */
    @Override
    public State captureCharacterState() {
        return new KukiState(
                this,
                normalAttackStep,
                skillGeneration,
                ringGeneration,
                burstGeneration,
                ringExpirationTime,
                nextParticleTime,
                nextC4Time,
                particleDrawCursor,
                pendingHits,
                pendingCommands);
    }

    /** Accepts only state captured from this exact Kuki instance. */
    @Override
    public boolean acceptsCharacterState(State state) {
        return state instanceof KukiState
                && ((KukiState) state).owner == this;
    }

    /** Restores Kuki state and reconstructs each pending event once. */
    @Override
    public void restoreCharacterState(
            State state,
            CombatSimulator simulator) {
        if (!acceptsCharacterState(state)) {
            throw new IllegalArgumentException(
                    "Unexpected Kuki Shinobu state");
        }
        initializeForSimulator(simulator);
        KukiState restored = (KukiState) state;
        normalAttackStep = restored.normalAttackStep;
        skillGeneration = restored.skillGeneration;
        ringGeneration = restored.ringGeneration;
        burstGeneration = restored.burstGeneration;
        ringExpirationTime = restored.ringExpirationTime;
        nextParticleTime = restored.nextParticleTime;
        nextC4Time = restored.nextC4Time;
        particleDrawCursor = restored.particleDrawCursor;
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

    /** Returns Kuki's 60-Energy Burst cost. */
    @Override
    public double getEnergyCost() {
        return getTalentValue("Energy Cost", 60.0);
    }

    /** Kuki's represented passives are dynamic damage effects. */
    @Override
    public void applyPassive(StatsContainer stats) {
        // A4 is applied to Skill damage from its hit-time EM.
    }

    /** Resets the Normal string when Kuki leaves the field. */
    @Override
    public void onSwitchOut(CombatSimulator simulator) {
        normalAttackStep = 0;
    }

    /** Resets the Normal string when Kuki returns. */
    @Override
    public void onSwitchIn(CombatSimulator simulator) {
        normalAttackStep = 0;
    }

    /** Returns whether Grass Ring is active at the supplied time. */
    public boolean isRingActive(double currentTime) {
        return currentTime < ringExpirationTime;
    }

    /** Returns the half-open ring expiration timestamp. */
    public double getRingExpirationTime() {
        return ringExpirationTime;
    }

    /** Dispatches Kuki's represented action set. */
    @Override
    public void onAction(
            CharacterActionRequest request,
            CombatSimulator simulator) {
        if (request == null) {
            throw new IllegalArgumentException(
                    "Kuki Shinobu action is required");
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
                sanctifyingRing(simulator);
                break;
            case BURST:
                gyoeiNarukamiKariyamaRite(simulator);
                break;
            default:
                throw new IllegalArgumentException(
                        "Unsupported action for Kuki Shinobu: "
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
                0L,
                null,
                0.0));
        normalAttackStep = (normalAttackStep + 1) % 4;
        simulator.advanceTime(NORMAL_DURATIONS[step] * FRAME);
    }

    private void chargedAttack(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        for (int index = 0; index < CHARGED_HIT_FRAMES.length; index++) {
            queueHit(simulator, new PendingHit(
                    castTime + CHARGED_HIT_FRAMES[index] * FRAME,
                    HitKind.CHARGED,
                    index,
                    0L,
                    null,
                    0.0));
        }
        simulator.advanceTime(35.0 * FRAME);
    }

    private void sanctifyingRing(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        long generation = ++skillGeneration;
        queueCommand(simulator, new PendingCommand(
                castTime + 7.0 * FRAME,
                CommandKind.SKILL_COOLDOWN,
                generation,
                0));
        queueHit(simulator, new PendingHit(
                castTime + 11.0 * FRAME,
                HitKind.SKILL_INITIAL,
                0,
                generation,
                null,
                captureLiveStats(castTime).get(
                        StatType.ELEMENTAL_MASTERY)
                        * getTalentValue(
                                "A4 Damage Per Elemental Mastery", 0.25)));
        queueCommand(simulator, new PendingCommand(
                castTime + 23.0 * FRAME,
                CommandKind.RING_ACTIVATE,
                generation,
                0));
        simulator.advanceTime(52.0 * FRAME);
    }

    private void activateRing(
            CombatSimulator simulator,
            long skillCastGeneration) {
        if (skillCastGeneration != skillGeneration) {
            return;
        }
        double activationTime = simulator.getCurrentTime();
        long generation = ++ringGeneration;
        double duration = constellation >= 2
                ? getTalentValue("C2 Grass Ring Duration", 15.0)
                : getTalentValue("Grass Ring Duration", 12.0);
        ringExpirationTime = activationTime + duration;
        int tickCount = getRingTickCount();
        for (int index = 1; index <= tickCount; index++) {
            double tickTime = activationTime
                    + index * getTalentValue(
                            "Grass Ring Tick Interval Frames", 90.0) * FRAME;
            queueCommand(simulator, new PendingCommand(
                    tickTime,
                    CommandKind.RING_TICK,
                    generation,
                    index - 1));
        }
    }

    private int getRingTickCount() {
        return constellation >= 2
                ? (int) getTalentValue("Grass Ring C2 Tick Count", 10.0)
                : (int) getTalentValue("Grass Ring C0 Tick Count", 8.0);
    }

    private void queueRingHit(
            CombatSimulator simulator,
            long generation,
            int index) {
        if (generation != ringGeneration) {
            return;
        }
        double currentTime = simulator.getCurrentTime();
        double additiveBaseDamage = captureLiveStats(currentTime).get(
                StatType.ELEMENTAL_MASTERY)
                * getTalentValue(
                        "A4 Damage Per Elemental Mastery", 0.25);
        queueHit(simulator, new PendingHit(
                currentTime + 2.0 * FRAME,
                HitKind.RING,
                index,
                generation,
                null,
                additiveBaseDamage));
    }

    private void gyoeiNarukamiKariyamaRite(
            CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        long generation = ++burstGeneration;
        markBurstCooldownUsed(castTime, simulator.getApplicableBuffs(this));
        StatsContainer snapshot = captureLiveStats(castTime);
        queueCommand(simulator, new PendingCommand(
                castTime + 4.0 * FRAME,
                CommandKind.BURST_ENERGY,
                generation,
                0));
        for (int index = 0; index < BURST_HIT_FRAMES.length; index++) {
            queueHit(simulator, new PendingHit(
                    castTime + BURST_HIT_FRAMES[index] * FRAME,
                    HitKind.BURST,
                    index,
                    generation,
                    snapshot,
                    0.0));
        }
        simulator.advanceTime(63.0 * FRAME);
    }

    private void onResolvedDamage(
            Character actor,
            AttackAction action,
            double damage,
            double time,
            CombatSimulator simulator) {
        if (constellation < 4
                || damage <= 0.0
                || actor == null
                || actor != simulator.getActiveCharacter()
                || !isRingActive(time)
                || time < nextC4Time - EPSILON
                || !isNormalChargedOrPlunge(action)) {
            return;
        }
        nextC4Time = time + getTalentValue("C4 Cooldown", 5.0);
        double fixedHpDamage = captureLiveStats(time).getTotalHp()
                * getTalentValue(
                        "C4 Thundergrass Mark Max HP", 0.097);
        queueHit(simulator, new PendingHit(
                time + getTalentValue("C4 Hit Delay Frames", 5.0) * FRAME,
                HitKind.C4,
                0,
                ringGeneration,
                null,
                fixedHpDamage));
    }

    private static boolean isNormalChargedOrPlunge(AttackAction action) {
        if (action == null) {
            return false;
        }
        ActionType type = action.getActionType();
        return type == ActionType.NORMAL
                || type == ActionType.CHARGE
                || type == ActionType.PLUNGE;
    }

    private void resolveHit(CombatSimulator simulator, PendingHit hit) {
        if (hit.kind == HitKind.SKILL_INITIAL
                && hit.generation != skillGeneration) {
            return;
        }
        if ((hit.kind == HitKind.RING || hit.kind == HitKind.C4)
                && hit.generation != ringGeneration) {
            return;
        }
        if (hit.kind == HitKind.BURST
                && hit.generation != burstGeneration) {
            return;
        }
        switch (hit.kind) {
            case NORMAL:
                resolveNormal(simulator, hit);
                break;
            case CHARGED:
                resolveCharged(simulator, hit);
                break;
            case SKILL_INITIAL:
                resolveSkillDamage(simulator, hit, false);
                break;
            case RING:
                resolveSkillDamage(simulator, hit, true);
                break;
            case BURST:
                resolveBurst(simulator, hit);
                break;
            case C4:
                resolveC4(simulator, hit);
                break;
            default:
                throw new IllegalStateException("Unknown Kuki hit kind");
        }
    }

    private void resolveNormal(CombatSimulator simulator, PendingHit hit) {
        AttackAction action = attack(
                "Shinobu's Shadowsword N" + (hit.index + 1),
                getTalentValue(
                        "N" + (hit.index + 1),
                        NORMAL_MULTIPLIERS[hit.index]),
                Element.PHYSICAL,
                StatType.BASE_ATK,
                StatType.NORMAL_ATTACK_DMG_BONUS,
                ActionType.NORMAL,
                ICDType.None,
                ICDTag.None,
                0.0);
        action.setStatSnapshot(captureLiveStats(simulator.getCurrentTime()));
        simulator.performActionWithoutTimeAdvance(characterId, action);
    }

    private void resolveCharged(CombatSimulator simulator, PendingHit hit) {
        AttackAction action = attack(
                "Shinobu's Shadowsword Charged " + (hit.index + 1),
                getTalentValue(
                        "Charged-" + (hit.index + 1),
                        CHARGED_MULTIPLIERS[hit.index]),
                Element.PHYSICAL,
                StatType.BASE_ATK,
                StatType.CHARGED_ATTACK_DMG_BONUS,
                ActionType.CHARGE,
                ICDType.None,
                ICDTag.None,
                0.0);
        action.setStatSnapshot(captureLiveStats(simulator.getCurrentTime()));
        simulator.performActionWithoutTimeAdvance(characterId, action);
    }

    private void resolveSkillDamage(
            CombatSimulator simulator,
            PendingHit hit,
            boolean periodic) {
        StatsContainer snapshot = captureLiveStats(simulator.getCurrentTime());
        String key;
        double fallback;
        if (periodic) {
            key = constellation >= 3 ? "Grass Ring C3" : "Grass Ring";
            fallback = constellation >= 3 ? 0.5048 : 0.42908;
        } else {
            key = constellation >= 3 ? "Skill Initial C3" : "Skill Initial";
            fallback = constellation >= 3 ? 1.51424 : 1.287104;
        }
        AttackAction action = attack(
                periodic
                        ? "Grass Ring of Sanctification"
                        : "Sanctifying Ring",
                getTalentValue(key, fallback),
                Element.ELECTRO,
                StatType.BASE_ATK,
                StatType.SKILL_DMG_BONUS,
                ActionType.SKILL,
                ICDType.Standard,
                ICDTag.ElementalSkill,
                1.0);
        snapshot.add(StatType.FLAT_DMG_BONUS, hit.additiveBaseDamage);
        action.setStatSnapshot(snapshot);
        if (!periodic) {
            action.setShatterTrigger(true);
        }
        simulator.performActionWithoutTimeAdvance(characterId, action);
        if (periodic) {
            scheduleParticleAfterHit(simulator);
        }
    }

    private void resolveBurst(CombatSimulator simulator, PendingHit hit) {
        boolean c5 = constellation >= 5;
        AttackAction action = attack(
                "Gyoei Narukami Kariyama Rite " + (hit.index + 1),
                getTalentValue(
                        c5 ? "Single Instance Max HP C5"
                                : "Single Instance Max HP",
                        c5 ? 0.072096 : 0.061282),
                Element.ELECTRO,
                StatType.BASE_HP,
                StatType.BURST_DMG_BONUS,
                ActionType.BURST,
                ICDType.Standard,
                ICDTag.ElementalBurst,
                1.0);
        action.setStatSnapshot(hit.snapshot);
        simulator.performActionWithoutTimeAdvance(characterId, action);
    }

    private void resolveC4(CombatSimulator simulator, PendingHit hit) {
        StatsContainer snapshot = captureLiveStats(simulator.getCurrentTime());
        AttackAction action = attack(
                "Thundergrass Mark",
                0.0,
                Element.ELECTRO,
                StatType.BASE_ATK,
                StatType.SKILL_DMG_BONUS,
                ActionType.SKILL,
                ICDType.None,
                ICDTag.None,
                1.0);
        snapshot.add(StatType.FLAT_DMG_BONUS, hit.additiveBaseDamage);
        action.setStatSnapshot(snapshot);
        simulator.performActionWithoutTimeAdvance(characterId, action);
        scheduleParticleAfterHit(simulator);
    }

    private void scheduleParticleAfterHit(CombatSimulator simulator) {
        double currentTime = simulator.getCurrentTime();
        if (simulator.getEnemy() == null
                || currentTime < nextParticleTime - EPSILON) {
            return;
        }
        nextParticleTime = currentTime
                + getTalentValue("Particle Cooldown", 0.2);
        if (nextParticleDraw()
                >= getTalentValue("Particle Chance", 0.45)) {
            return;
        }
        queueCommand(simulator, new PendingCommand(
                currentTime + PARTICLE_TRAVEL,
                CommandKind.PARTICLE,
                0L,
                1));
    }

    private double nextParticleDraw() {
        if (particleDrawCursor < particleDrawTape.size()) {
            return particleDrawTape.get(particleDrawCursor++);
        }
        double draw = validatedDraw();
        particleDrawTape.add(draw);
        particleDrawCursor++;
        return draw;
    }

    private double validatedDraw() {
        double draw = particleDrawSource.getAsDouble();
        if (!Double.isFinite(draw) || draw < 0.0 || draw >= 1.0) {
            throw new IllegalStateException(
                    "Kuki Shinobu particle draw must be in [0, 1)");
        }
        return draw;
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
                    if (command.generation == skillGeneration) {
                        markSkillUsed(
                                activeSimulator.getCurrentTime(),
                                activeSimulator.getApplicableBuffs(this));
                    }
                    break;
                case RING_ACTIVATE:
                    activateRing(activeSimulator, command.generation);
                    break;
                case RING_TICK:
                    queueRingHit(
                            activeSimulator,
                            command.generation,
                            command.index);
                    break;
                case BURST_ENERGY:
                    if (command.generation == burstGeneration) {
                        spendBurstEnergy(activeSimulator.getCurrentTime());
                    }
                    break;
                case PARTICLE:
                    activeSimulator.getEnergyDistributor().distributeParticles(
                            Element.ELECTRO,
                            command.index,
                            ParticleType.PARTICLE);
                    break;
                default:
                    throw new IllegalStateException(
                            "Unknown Kuki command kind");
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

    private static AttackAction attack(
            String displayName,
            double multiplier,
            Element element,
            StatType scalingStat,
            StatType bonusStat,
            ActionType actionType,
            ICDType icdType,
            ICDTag icdTag,
            double gauge) {
        AttackAction action = new AttackAction(
                displayName,
                multiplier,
                element,
                scalingStat,
                bonusStat,
                0.0,
                false,
                actionType);
        action.setICD(icdType, icdTag, gauge);
        action.setCountsAsSkillDmg(actionType == ActionType.SKILL);
        action.setCountsAsBurstDmg(actionType == ActionType.BURST);
        return action;
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
        SKILL_INITIAL,
        RING,
        BURST,
        C4
    }

    private enum CommandKind {
        SKILL_COOLDOWN,
        RING_ACTIVATE,
        RING_TICK,
        BURST_ENERGY,
        PARTICLE
    }

    /** Immutable delayed Kuki hit description. */
    private static final class PendingHit {
        private final double time;
        private final HitKind kind;
        private final int index;
        private final long generation;
        private final StatsContainer snapshot;
        private final double additiveBaseDamage;

        private PendingHit(
                double time,
                HitKind kind,
                int index,
                long generation,
                StatsContainer snapshot,
                double additiveBaseDamage) {
            this.time = time;
            this.kind = kind;
            this.index = index;
            this.generation = generation;
            this.snapshot = snapshot == null
                    ? null : snapshot.merge(null);
            this.additiveBaseDamage = additiveBaseDamage;
        }

        private PendingHit copy() {
            return new PendingHit(
                    time, kind, index, generation, snapshot,
                    additiveBaseDamage);
        }
    }

    /** Immutable delayed Kuki command description. */
    private static final class PendingCommand {
        private final double time;
        private final CommandKind kind;
        private final long generation;
        private final int index;

        private PendingCommand(
                double time,
                CommandKind kind,
                long generation,
                int index) {
            this.time = time;
            this.kind = kind;
            this.generation = generation;
            this.index = index;
        }

        private PendingCommand copy() {
            return new PendingCommand(time, kind, generation, index);
        }
    }

    /** Immutable Kuki-owned simulator snapshot payload. */
    private static final class KukiState implements State {
        private final KukiShinobu owner;
        private final int normalAttackStep;
        private final long skillGeneration;
        private final long ringGeneration;
        private final long burstGeneration;
        private final double ringExpirationTime;
        private final double nextParticleTime;
        private final double nextC4Time;
        private final int particleDrawCursor;
        private final List<PendingHit> pendingHits;
        private final List<PendingCommand> pendingCommands;

        private KukiState(
                KukiShinobu owner,
                int normalAttackStep,
                long skillGeneration,
                long ringGeneration,
                long burstGeneration,
                double ringExpirationTime,
                double nextParticleTime,
                double nextC4Time,
                int particleDrawCursor,
                List<PendingHit> pendingHits,
                List<PendingCommand> pendingCommands) {
            this.owner = owner;
            this.normalAttackStep = normalAttackStep;
            this.skillGeneration = skillGeneration;
            this.ringGeneration = ringGeneration;
            this.burstGeneration = burstGeneration;
            this.ringExpirationTime = ringExpirationTime;
            this.nextParticleTime = nextParticleTime;
            this.nextC4Time = nextC4Time;
            this.particleDrawCursor = particleDrawCursor;
            this.pendingHits = copyHits(pendingHits);
            this.pendingCommands = copyCommands(pendingCommands);
        }
    }
}

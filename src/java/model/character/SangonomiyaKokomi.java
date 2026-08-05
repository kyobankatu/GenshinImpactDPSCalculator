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
 * Sangonomiya Kokomi's fixed-target Bake-Kurage offensive slice through C5.
 *
 * <p>The Shape of Water, Bake-Kurage's seven snapshotted ripples, particles,
 * Nereid's Ascension and its Max-HP additions, Flawless Strategy, A1/A4, and
 * C1/C3/C4/C5 follow pinned gcsim {@code ef41805d}. Burst additions use final
 * Max HP at each source-defined snapshot and preserve the A4 Healing Bonus
 * conversion.</p>
 *
 * <p>Healing, current-HP C2/C6, water movement, interruption resistance,
 * projectile and area geometry, multi-target behavior, and hitlag are
 * excluded without approximation.</p>
 */
public final class SangonomiyaKokomi extends Character implements
        SimulatorInitializedCharacterEffect,
        SnapshotAwareCharacterEffect,
        SwitchAwareCharacter {
    private static final double FRAME = 1.0 / 60.0;
    private static final double EPSILON = 1e-9;
    private static final int[] NORMAL_HIT_FRAMES = { 14, 22, 38 };
    private static final int[] NORMAL_DURATIONS = { 30, 34, 65 };
    private static final double[] NORMAL_T9 = {
        1.162392, 1.046153, 1.603195
    };

    private final DoubleSupplier particleRandom;
    private CombatSimulator initializedSimulator;
    private int normalAttackStep;
    private long skillGeneration;
    private long burstGeneration;
    private double skillExpirationTime = Double.NEGATIVE_INFINITY;
    private double burstExpirationTime = Double.NEGATIVE_INFINITY;
    private double nextParticleAllowedTime = Double.NEGATIVE_INFINITY;
    private double nextC4EnergyAllowedTime = Double.NEGATIVE_INFINITY;
    private List<PendingHit> pendingHits = new ArrayList<>();
    private List<PendingCommand> pendingCommands = new ArrayList<>();

    /** Constructs repository-default C6 Sangonomiya Kokomi. */
    public SangonomiyaKokomi(Weapon weapon, ArtifactSet artifacts) {
        this(weapon, artifacts, TalentDataManager.getInstance(), 6,
                Math::random);
    }

    /** Constructs Sangonomiya Kokomi at an explicit constellation. */
    public SangonomiyaKokomi(
            Weapon weapon,
            ArtifactSet artifacts,
            int constellation) {
        this(weapon, artifacts, TalentDataManager.getInstance(),
                constellation, Math::random);
    }

    /**
     * Constructs Kokomi with injectable talent data and particle randomness.
     *
     * @param weapon equipped catalyst, or {@code null}
     * @param artifacts equipped artifact set, or {@code null}
     * @param talentData static character-data source
     * @param constellation constellation level in {@code [0, 6]}
     * @param particleRandom source of draws in {@code [0, 1)}
     */
    public SangonomiyaKokomi(
            Weapon weapon,
            ArtifactSet artifacts,
            TalentDataSource talentData,
            int constellation,
            DoubleSupplier particleRandom) {
        super(talentData);
        if (constellation < 0 || constellation > 6) {
            throw new IllegalArgumentException(
                    "Sangonomiya Kokomi constellation must be between 0 and 6");
        }
        if (particleRandom == null) {
            throw new IllegalArgumentException(
                    "Sangonomiya Kokomi particle random source is required");
        }
        name = "Sangonomiya Kokomi";
        characterId = CharacterId.SANGONOMIYA_KOKOMI;
        element = Element.HYDRO;
        this.weapon = weapon;
        this.artifacts = new ArtifactSet[] { artifacts };
        this.constellation = constellation;
        this.particleRandom = particleRandom;
        baseStats.set(StatType.BASE_HP,
                getTalentValue("Base HP", 13471.0));
        baseStats.set(StatType.BASE_ATK,
                getTalentValue("Base ATK", 234.0));
        baseStats.set(StatType.BASE_DEF,
                getTalentValue("Base DEF", 657.0));
        baseStats.add(StatType.HYDRO_DMG_BONUS,
                getTalentValue("Ascension Hydro DMG Bonus", 0.288));
        setSkillCD(getTalentValue("Skill Cooldown", 20.0));
        setBurstCD(getTalentValue("Burst Cooldown", 18.0));
    }

    /** Binds jellyfish and Ceremonial Garment work to one simulator. */
    @Override
    public void initializeForSimulator(CombatSimulator simulator) {
        if (simulator == null) {
            throw new IllegalArgumentException(
                    "Sangonomiya Kokomi simulator is required");
        }
        if (!simulator.getPartyMembers().contains(this)) {
            throw new IllegalArgumentException(
                    "Sangonomiya Kokomi must belong to the simulator party");
        }
        if (initializedSimulator != null
                && initializedSimulator != simulator) {
            throw new IllegalStateException(
                    "Sangonomiya Kokomi cannot be reused across simulators");
        }
        initializedSimulator = simulator;
    }

    /** Captures action, deployable, Burst, ICD, and future event state. */
    @Override
    public State captureCharacterState() {
        return new KokomiState(
                this,
                normalAttackStep,
                skillGeneration,
                burstGeneration,
                skillExpirationTime,
                burstExpirationTime,
                nextParticleAllowedTime,
                nextC4EnergyAllowedTime,
                pendingHits,
                pendingCommands);
    }

    /** Accepts state captured from this exact Kokomi instance only. */
    @Override
    public boolean acceptsCharacterState(State state) {
        return state instanceof KokomiState
                && ((KokomiState) state).owner == this;
    }

    /** Restores owner state and registers each surviving event once. */
    @Override
    public void restoreCharacterState(
            State state,
            CombatSimulator simulator) {
        if (!acceptsCharacterState(state)) {
            throw new IllegalArgumentException(
                    "Unexpected Sangonomiya Kokomi state");
        }
        initializeForSimulator(simulator);
        KokomiState restored = (KokomiState) state;
        normalAttackStep = restored.normalAttackStep;
        skillGeneration = restored.skillGeneration;
        burstGeneration = restored.burstGeneration;
        skillExpirationTime = restored.skillExpirationTime;
        burstExpirationTime = restored.burstExpirationTime;
        nextParticleAllowedTime = restored.nextParticleAllowedTime;
        nextC4EnergyAllowedTime = restored.nextC4EnergyAllowedTime;
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

    /** Returns Kokomi's 70-Energy Burst cost. */
    @Override
    public double getEnergyCost() {
        return getTalentValue("Energy Cost", 70.0);
    }

    /** Applies Flawless Strategy and C4's active Burst attack speed. */
    @Override
    public void applyPassive(StatsContainer stats) {
        stats.add(StatType.HEALING_BONUS,
                getTalentValue(
                        "Flawless Strategy Healing Bonus", 0.25));
        stats.add(StatType.CRIT_RATE,
                getTalentValue("Flawless Strategy CRIT Rate", -1.0));
        if (constellation >= 4
                && initializedSimulator != null
                && isBurstActive(initializedSimulator.getCurrentTime())) {
            stats.add(StatType.NORMAL_ATTACK_SPD,
                    getTalentValue("C4 Attack Speed", 0.10));
        }
    }

    /** Ends Ceremonial Garment while preserving Bake-Kurage. */
    @Override
    public void onSwitchOut(CombatSimulator simulator) {
        normalAttackStep = 0;
        burstGeneration++;
        burstExpirationTime = Double.NEGATIVE_INFINITY;
    }

    /** Returns whether Bake-Kurage exists at a half-open boundary. */
    public boolean isBakeKurageActive(double currentTime) {
        return skillGeneration > 0
                && currentTime + EPSILON < skillExpirationTime;
    }

    /** Returns whether Ceremonial Garment is active at a half-open boundary. */
    public boolean isBurstActive(double currentTime) {
        return burstGeneration > 0
                && currentTime + EPSILON < burstExpirationTime;
    }

    /** Returns Bake-Kurage's current expiration timestamp. */
    public double getBakeKurageExpirationTime() {
        return skillExpirationTime;
    }

    /** Returns the next particle eligibility timestamp. */
    public double getNextParticleAllowedTime() {
        return nextParticleAllowedTime;
    }

    /** Returns the number of unresolved Kokomi-owned damage hits. */
    public int getPendingHitCount() {
        return pendingHits.size();
    }

    /** Dispatches Kokomi's represented typed action set. */
    @Override
    public void onAction(
            CharacterActionRequest request,
            CombatSimulator simulator) {
        if (request == null) {
            throw new IllegalArgumentException(
                    "Sangonomiya Kokomi action is required");
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
                kuragesOath(simulator);
                break;
            case BURST:
                nereidsAscension(simulator);
                break;
            case PLUNGE:
                throw new IllegalArgumentException(
                        "Kokomi Plunge data is outside the pinned source slice");
            default:
                throw new IllegalArgumentException(
                        "Unsupported action for Sangonomiya Kokomi: "
                                + request.getKey());
        }
    }

    private void normalAttack(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        int step = normalAttackStep;
        StatsContainer snapshot = captureLiveStats(castTime);
        boolean burstActive = isBurstActive(castTime);
        double speedScale = 1.0 + Math.min(
                0.60,
                Math.max(
                        0.0,
                        snapshot.get(StatType.ATK_SPD)
                                + snapshot.get(
                                        StatType.NORMAL_ATTACK_SPD)));
        queueHit(simulator, new PendingHit(
                castTime + NORMAL_HIT_FRAMES[step] * FRAME / speedScale,
                HitKind.NORMAL,
                step,
                snapshot,
                burstFlatDamage(snapshot, HitKind.NORMAL, burstActive),
                burstGeneration));
        if (step == 2 && constellation >= 1 && burstActive) {
            queueHit(simulator, new PendingHit(
                    castTime
                            + NORMAL_HIT_FRAMES[step] * FRAME / speedScale,
                    HitKind.C1,
                    0,
                    snapshot,
                    snapshot.getTotalHp()
                            * getTalentValue(
                                    "C1 Max HP Flat DMG", 0.30),
                    burstGeneration));
        }
        normalAttackStep = (normalAttackStep + 1)
                % NORMAL_HIT_FRAMES.length;
        simulator.advanceTime(
                NORMAL_DURATIONS[step] * FRAME / speedScale);
    }

    private void chargedAttack(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        StatsContainer snapshot = captureLiveStats(castTime);
        boolean burstActive = isBurstActive(castTime);
        queueHit(simulator, new PendingHit(
                castTime + 48.0 * FRAME,
                HitKind.CHARGED,
                0,
                snapshot,
                burstFlatDamage(snapshot, HitKind.CHARGED, burstActive),
                burstGeneration));
        simulator.advanceTime(76.0 * FRAME);
    }

    private void kuragesOath(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        long generation = ++skillGeneration;
        skillExpirationTime = castTime
                + getTalentValue("Bake-Kurage Duration Frames", 751.0)
                        * FRAME;
        StatsContainer snapshot = captureLiveStats(castTime);
        queueHit(simulator, new PendingHit(
                castTime + 24.0 * FRAME,
                HitKind.SKILL,
                0,
                snapshot,
                0.0,
                generation));
        queueHit(simulator, new PendingHit(
                castTime
                        + getTalentValue(
                                "Bake-Kurage Second Tick Frames", 150.0)
                                * FRAME,
                HitKind.SKILL,
                1,
                snapshot,
                0.0,
                generation));
        queueCommand(simulator, new PendingCommand(
                castTime + 20.0 * FRAME,
                CommandKind.SKILL_COOLDOWN,
                0L,
                0.0));
        simulator.advanceTime(61.0 * FRAME);
    }

    private void nereidsAscension(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        long generation = ++burstGeneration;
        burstExpirationTime = castTime
                + getTalentValue("Burst Duration", 10.0);
        StatsContainer snapshot = captureLiveStats(castTime);
        queueHit(simulator, new PendingHit(
                castTime + 49.0 * FRAME,
                HitKind.BURST,
                0,
                snapshot,
                0.0,
                generation));
        queueCommand(simulator, new PendingCommand(
                castTime + 46.0 * FRAME,
                CommandKind.BURST_COOLDOWN_AND_REFRESH,
                generation,
                0.0));
        queueCommand(simulator, new PendingCommand(
                castTime + 57.0 * FRAME,
                CommandKind.BURST_ENERGY,
                generation,
                0.0));
        simulator.advanceTime(78.0 * FRAME);
    }

    private void resolveHit(CombatSimulator simulator, PendingHit hit) {
        switch (hit.kind) {
            case NORMAL:
                performHit(
                        simulator,
                        hit,
                        "The Shape of Water N" + (hit.index + 1),
                        getTalentValue(
                                "N" + (hit.index + 1),
                                NORMAL_T9[hit.index]),
                        StatType.BASE_ATK,
                        StatType.NORMAL_ATTACK_DMG_BONUS,
                        ActionType.NORMAL,
                        ICDType.Standard,
                        ICDTag.NormalAttack,
                        1.0);
                triggerC4Energy(simulator, hit);
                break;
            case CHARGED:
                performHit(
                        simulator,
                        hit,
                        "The Shape of Water Charged",
                        getTalentValue("Charged Attack", 2.521440),
                        StatType.BASE_ATK,
                        StatType.CHARGED_ATTACK_DMG_BONUS,
                        ActionType.CHARGE,
                        ICDType.None,
                        ICDTag.None,
                        1.0);
                triggerC4Energy(simulator, hit);
                break;
            case C1:
                if (hit.generation == burstGeneration
                        && isBurstActive(hit.time)) {
                    performHit(
                            simulator,
                            hit,
                            "At Water's Edge C1",
                            0.0,
                            StatType.BASE_ATK,
                            null,
                            ActionType.OTHER,
                            ICDType.None,
                            ICDTag.None,
                            1.0);
                }
                break;
            case SKILL:
                resolveSkillTick(simulator, hit);
                break;
            case BURST:
                performHit(
                        simulator,
                        hit,
                        "Nereid's Ascension",
                        burstValue("Nereid Ascension", 0.177072, 0.208320),
                        StatType.BASE_HP,
                        StatType.BURST_DMG_BONUS,
                        ActionType.BURST,
                        ICDType.Standard,
                        ICDTag.ElementalBurst,
                        2.0);
                break;
            default:
                throw new IllegalStateException(
                        "Unknown Kokomi hit kind " + hit.kind);
        }
    }

    private void resolveSkillTick(
            CombatSimulator simulator,
            PendingHit hit) {
        if (hit.generation != skillGeneration
                || hit.time + EPSILON >= skillExpirationTime) {
            return;
        }
        StatsContainer live = captureLiveStats(simulator.getCurrentTime());
        double flatDamage = burstFlatDamage(
                live,
                HitKind.SKILL,
                isBurstActive(simulator.getCurrentTime()));
        PendingHit resolved = new PendingHit(
                hit.time,
                HitKind.SKILL,
                hit.index,
                hit.snapshot,
                flatDamage,
                hit.generation);
        performHit(
                simulator,
                resolved,
                "Bake-Kurage",
                skillValue(),
                StatType.BASE_ATK,
                StatType.SKILL_DMG_BONUS,
                ActionType.SKILL,
                ICDType.None,
                ICDTag.None,
                1.0);
        triggerParticle(simulator, hit.time);
        if (hit.index > 0) {
            double nextTime = hit.time
                    + getTalentValue(
                            "Bake-Kurage Interval Frames", 120.0)
                            * FRAME;
            if (nextTime + EPSILON < skillExpirationTime) {
                queueHit(simulator, new PendingHit(
                        nextTime,
                        HitKind.SKILL,
                        hit.index + 1,
                        hit.snapshot,
                        0.0,
                        hit.generation));
            }
        }
    }

    private void triggerParticle(
            CombatSimulator simulator,
            double hitTime) {
        if (simulator.getEnemy() == null
                || hitTime + EPSILON < nextParticleAllowedTime) {
            return;
        }
        double draw = particleRandom.getAsDouble();
        if (!Double.isFinite(draw) || draw < 0.0 || draw >= 1.0) {
            throw new IllegalStateException(
                    "Kokomi particle random draw must be in [0, 1)");
        }
        nextParticleAllowedTime = hitTime
                + getTalentValue("Particle Cooldown", 1.0);
        if (draw >= getTalentValue("Particle Chance", 0.67)) {
            return;
        }
        queueCommand(simulator, new PendingCommand(
                hitTime
                        + getTalentValue("Particle Travel Frames", 100.0)
                                * FRAME,
                CommandKind.PARTICLE,
                0L,
                getTalentValue("Particle Count", 1.0)));
    }

    private void triggerC4Energy(
            CombatSimulator simulator,
            PendingHit hit) {
        if (constellation < 4
                || hit.generation != burstGeneration
                || !isBurstActive(hit.time)
                || simulator.getEnemy() == null
                || hit.time + EPSILON < nextC4EnergyAllowedTime) {
            return;
        }
        nextC4EnergyAllowedTime = hit.time
                + getTalentValue("C4 Energy Cooldown", 0.2);
        receiveFlatEnergy(getTalentValue("C4 Energy", 0.8));
    }

    private double burstFlatDamage(
            StatsContainer snapshot,
            HitKind kind,
            boolean active) {
        if (!active) {
            return 0.0;
        }
        String key;
        double talentNine;
        double talentTwelve;
        switch (kind) {
            case NORMAL:
                key = "Normal Max HP Bonus";
                talentNine = 0.082280;
                talentTwelve = 0.096800;
                break;
            case CHARGED:
                key = "Charged Max HP Bonus";
                talentNine = 0.115192;
                talentTwelve = 0.135520;
                break;
            case SKILL:
                key = "Bake-Kurage Max HP Bonus";
                talentNine = 0.120637;
                talentTwelve = 0.141926;
                break;
            default:
                return 0.0;
        }
        double ratio = burstValue(key, talentNine, talentTwelve);
        if (kind == HitKind.NORMAL || kind == HitKind.CHARGED) {
            ratio += snapshot.get(StatType.HEALING_BONUS)
                    * getTalentValue(
                            "A4 Healing Bonus Conversion", 0.15);
        }
        return snapshot.getTotalHp() * ratio;
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
            double gaugeUnits) {
        AttackAction action = hit.flatDamage == 0.0
                ? new AttackAction(
                        displayName,
                        multiplier,
                        Element.HYDRO,
                        scalingStat,
                        bonusStat,
                        0.0,
                        actionType)
                : new KokomiAttackAction(
                        displayName,
                        multiplier,
                        scalingStat,
                        bonusStat,
                        actionType,
                        hit.flatDamage);
        action.setICD(icdType, icdTag, gaugeUnits);
        action.setCountsAsSkillDmg(actionType == ActionType.SKILL);
        action.setCountsAsBurstDmg(actionType == ActionType.BURST);
        action.setStatSnapshot(hit.snapshot == null
                ? captureLiveStats(simulator.getCurrentTime())
                : hit.snapshot);
        simulator.performActionWithoutTimeAdvance(characterId, action);
    }

    private double skillValue() {
        String suffix = constellation >= 5 ? " C5" : "";
        return getTalentValue("Bake-Kurage" + suffix,
                constellation >= 5 ? 2.183808 : 1.856237);
    }

    private double burstValue(
            String key,
            double talentNine,
            double talentTwelve) {
        String suffix = constellation >= 3 ? " C3" : "";
        return getTalentValue(key + suffix,
                constellation >= 3 ? talentTwelve : talentNine);
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
                case BURST_COOLDOWN_AND_REFRESH:
                    if (command.generation == burstGeneration) {
                        markBurstCooldownUsed(
                                activeSimulator.getCurrentTime(),
                                activeSimulator.getApplicableBuffs(this));
                        if (isBakeKurageActive(
                                activeSimulator.getCurrentTime())) {
                            skillExpirationTime =
                                    activeSimulator.getCurrentTime()
                                    + getTalentValue(
                                            "Bake-Kurage Refresh Frames",
                                            721.0) * FRAME;
                        }
                    }
                    break;
                case BURST_ENERGY:
                    if (command.generation == burstGeneration) {
                        spendBurstEnergy(activeSimulator.getCurrentTime());
                    }
                    break;
                case PARTICLE:
                    activeSimulator.getEnergyDistributor()
                            .distributeParticles(
                                    Element.HYDRO,
                                    command.value,
                                    ParticleType.PARTICLE);
                    break;
                default:
                    throw new IllegalStateException(
                            "Unknown Kokomi command kind " + command.kind);
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
        C1,
        SKILL,
        BURST
    }

    private enum CommandKind {
        SKILL_COOLDOWN,
        BURST_COOLDOWN_AND_REFRESH,
        BURST_ENERGY,
        PARTICLE
    }

    /** Preserves Kokomi's fixed Max-HP additions through reaction resolution. */
    private static final class KokomiAttackAction extends AttackAction {
        private final double fixedAdditiveBaseDamage;

        private KokomiAttackAction(
                String displayName,
                double multiplier,
                StatType scalingStat,
                StatType bonusStat,
                ActionType actionType,
                double fixedAdditiveBaseDamage) {
            super(
                    displayName,
                    multiplier,
                    Element.HYDRO,
                    scalingStat,
                    bonusStat,
                    0.0,
                    actionType);
            this.fixedAdditiveBaseDamage = fixedAdditiveBaseDamage;
        }

        @Override
        public void setAdditiveBaseDmgBonus(double value) {
        }

        @Override
        public double getAdditiveBaseDmgBonus() {
            return fixedAdditiveBaseDamage;
        }
    }

    /** Immutable future hit with source snapshot, flat addition, and generation. */
    private static final class PendingHit {
        private final double time;
        private final HitKind kind;
        private final int index;
        private final StatsContainer snapshot;
        private final double flatDamage;
        private final long generation;

        private PendingHit(
                double time,
                HitKind kind,
                int index,
                StatsContainer snapshot,
                double flatDamage,
                long generation) {
            this.time = time;
            this.kind = kind;
            this.index = index;
            this.snapshot = snapshot == null
                    ? null : snapshot.merge(null);
            this.flatDamage = flatDamage;
            this.generation = generation;
        }

        private PendingHit copy() {
            return new PendingHit(
                    time,
                    kind,
                    index,
                    snapshot,
                    flatDamage,
                    generation);
        }
    }

    /** Immutable delayed state command. */
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

    /** Immutable snapshot of all Kokomi-owned mutable runtime state. */
    private static final class KokomiState implements State {
        private final SangonomiyaKokomi owner;
        private final int normalAttackStep;
        private final long skillGeneration;
        private final long burstGeneration;
        private final double skillExpirationTime;
        private final double burstExpirationTime;
        private final double nextParticleAllowedTime;
        private final double nextC4EnergyAllowedTime;
        private final List<PendingHit> pendingHits;
        private final List<PendingCommand> pendingCommands;

        private KokomiState(
                SangonomiyaKokomi owner,
                int normalAttackStep,
                long skillGeneration,
                long burstGeneration,
                double skillExpirationTime,
                double burstExpirationTime,
                double nextParticleAllowedTime,
                double nextC4EnergyAllowedTime,
                List<PendingHit> pendingHits,
                List<PendingCommand> pendingCommands) {
            this.owner = owner;
            this.normalAttackStep = normalAttackStep;
            this.skillGeneration = skillGeneration;
            this.burstGeneration = burstGeneration;
            this.skillExpirationTime = skillExpirationTime;
            this.burstExpirationTime = burstExpirationTime;
            this.nextParticleAllowedTime = nextParticleAllowedTime;
            this.nextC4EnergyAllowedTime = nextC4EnergyAllowedTime;
            this.pendingHits = copyHits(pendingHits);
            this.pendingCommands = copyCommands(pendingCommands);
        }
    }
}

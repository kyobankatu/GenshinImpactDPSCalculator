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
import simulation.action.HitlagProfile;
import simulation.action.SkillActionMode;
import simulation.event.SimpleTimerEvent;

/**
 * Zhongli's fixed-target Stone Stele offensive slice through C5.
 *
 * <p>Rain of Stone, Press/Hold Dominus Lapidis damage and timing, one
 * replaceable Stone Stele with snapshotted two-second pulses, particle gate,
 * Planet Befall, A4 Max-HP additions, and C3/C5 follow pinned gcsim
 * {@code ef41805d}. Delayed construct work survives simulator rollback.</p>
 *
 * <p>Jade Shield, shield resistance reduction, A1, C1's second geometric
 * construct, C2 shield creation, C4 area/petrification, C6 healing, construct
 * resonance geometry, collision, and complete hitlag coverage are excluded without
 * approximation.</p>
 */
public final class Zhongli extends Character implements
        SimulatorInitializedCharacterEffect,
        SnapshotAwareCharacterEffect,
        SwitchAwareCharacter {
    private static final double FRAME = 1.0 / 60.0;
    private static final double EPSILON = 1e-9;
    /** Hitlag data pinned to gcsim {@code 3647a07a7cc3004bc1e79d9bb5f7444de20dceaa}. */
    private static final HitlagProfile NORMAL_HITLAG =
            new HitlagProfile(0.02, 0.01, true, false, false);
    private static final HitlagProfile CHARGED_HITLAG =
            new HitlagProfile(0.06, 0.01, true, true, false);
    private static final int[][] NORMAL_HIT_FRAMES = {
        { 11 }, { 9 }, { 8 }, { 16 }, { 11, 18, 23, 29 }, { 29 }
    };
    private static final int[] NORMAL_DURATIONS = {
        30, 30, 28, 34, 31, 54
    };
    private static final double[] NORMAL_T9 = {
        0.565292, 0.572323, 0.708725, 0.788878, 0.197500, 1.001214
    };

    private final DoubleSupplier particleRandom;
    private CombatSimulator initializedSimulator;
    private int normalAttackStep;
    private long steleGeneration;
    private double steleExpirationTime = Double.NEGATIVE_INFINITY;
    private double nextParticleAllowedTime = Double.NEGATIVE_INFINITY;
    private List<PendingHit> pendingHits = new ArrayList<>();
    private List<PendingCommand> pendingCommands = new ArrayList<>();

    /** Constructs repository-default C6 Zhongli. */
    public Zhongli(Weapon weapon, ArtifactSet artifacts) {
        this(weapon, artifacts, TalentDataManager.getInstance(), 6,
                Math::random);
    }

    /** Constructs Zhongli at an explicit constellation. */
    public Zhongli(
            Weapon weapon,
            ArtifactSet artifacts,
            int constellation) {
        this(weapon, artifacts, TalentDataManager.getInstance(),
                constellation, Math::random);
    }

    /**
     * Constructs Zhongli with injectable talent data and particle randomness.
     *
     * @param weapon equipped polearm, or {@code null}
     * @param artifacts equipped artifact set, or {@code null}
     * @param talentData static character-data source
     * @param constellation constellation level in {@code [0, 6]}
     * @param particleRandom source of draws in {@code [0, 1)}
     */
    public Zhongli(
            Weapon weapon,
            ArtifactSet artifacts,
            TalentDataSource talentData,
            int constellation,
            DoubleSupplier particleRandom) {
        super(talentData);
        if (constellation < 0 || constellation > 6) {
            throw new IllegalArgumentException(
                    "Zhongli constellation must be between 0 and 6");
        }
        if (particleRandom == null) {
            throw new IllegalArgumentException(
                    "Zhongli particle random source is required");
        }
        name = "Zhongli";
        characterId = CharacterId.ZHONGLI;
        element = Element.GEO;
        this.weapon = weapon;
        this.artifacts = new ArtifactSet[] { artifacts };
        this.constellation = constellation;
        this.particleRandom = particleRandom;
        baseStats.set(StatType.BASE_HP,
                getTalentValue("Base HP", 14695.0));
        baseStats.set(StatType.BASE_ATK,
                getTalentValue("Base ATK", 251.0));
        baseStats.set(StatType.BASE_DEF,
                getTalentValue("Base DEF", 738.0));
        baseStats.add(StatType.GEO_DMG_BONUS,
                getTalentValue("Ascension Geo DMG Bonus", 0.288));
        setSkillCD(getTalentValue("Press Skill Cooldown", 4.0));
        setBurstCD(getTalentValue("Burst Cooldown", 12.0));
    }

    /** Binds Stone Stele delayed work to exactly one simulator. */
    @Override
    public void initializeForSimulator(CombatSimulator simulator) {
        if (simulator == null) {
            throw new IllegalArgumentException("Zhongli simulator is required");
        }
        if (!simulator.getPartyMembers().contains(this)) {
            throw new IllegalArgumentException(
                    "Zhongli must belong to the simulator party");
        }
        if (initializedSimulator != null
                && initializedSimulator != simulator) {
            throw new IllegalStateException(
                    "Zhongli cannot be reused across simulators");
        }
        initializedSimulator = simulator;
    }

    /** Captures combo, construct, particle, and reconstructable future state. */
    @Override
    public State captureCharacterState() {
        return new ZhongliState(
                this,
                normalAttackStep,
                steleGeneration,
                steleExpirationTime,
                nextParticleAllowedTime,
                pendingHits,
                pendingCommands);
    }

    /** Accepts state captured from this exact Zhongli instance only. */
    @Override
    public boolean acceptsCharacterState(State state) {
        return state instanceof ZhongliState
                && ((ZhongliState) state).owner == this;
    }

    /** Restores owner state and registers each surviving event once. */
    @Override
    public void restoreCharacterState(
            State state,
            CombatSimulator simulator) {
        if (!acceptsCharacterState(state)) {
            throw new IllegalArgumentException("Unexpected Zhongli state");
        }
        initializeForSimulator(simulator);
        ZhongliState restored = (ZhongliState) state;
        normalAttackStep = restored.normalAttackStep;
        steleGeneration = restored.steleGeneration;
        steleExpirationTime = restored.steleExpirationTime;
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

    /** Returns Zhongli's 40-Energy Burst cost. */
    @Override
    public double getEnergyCost() {
        return getTalentValue("Energy Cost", 40.0);
    }

    /** Zhongli has no unconditional stat passive in this offensive slice. */
    @Override
    public void applyPassive(StatsContainer stats) {
    }

    /** Supports the source-defined Press and Hold Skill modes. */
    @Override
    protected boolean supportsSkillActionMode(SkillActionMode mode) {
        return mode == SkillActionMode.PRESS
                || mode == SkillActionMode.HOLD;
    }

    /** Resets only the on-field Normal string; Stone Stele persists. */
    @Override
    public void onSwitchOut(CombatSimulator simulator) {
        normalAttackStep = 0;
    }

    /** Returns whether the represented Stone Stele exists at this time. */
    public boolean isStoneSteleActive(double currentTime) {
        return steleGeneration > 0
                && currentTime + EPSILON < steleExpirationTime;
    }

    /** Returns the current Stone Stele expiration timestamp. */
    public double getStoneSteleExpirationTime() {
        return steleExpirationTime;
    }

    /** Returns the next timestamp at which Stele damage may make a particle. */
    public double getNextParticleAllowedTime() {
        return nextParticleAllowedTime;
    }

    /** Returns the number of unresolved Zhongli-owned damage hits. */
    public int getPendingHitCount() {
        return pendingHits.size();
    }

    /** Dispatches Zhongli's represented typed action set. */
    @Override
    public void onAction(
            CharacterActionRequest request,
            CombatSimulator simulator) {
        if (request == null) {
            throw new IllegalArgumentException("Zhongli action is required");
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
                dominusLapidis(
                        simulator,
                        request.getSkillMode() == SkillActionMode.HOLD);
                break;
            case BURST:
                planetBefall(simulator);
                break;
            default:
                throw new IllegalArgumentException(
                        "Unsupported action for Zhongli: "
                                + request.getKey());
        }
    }

    private void normalAttack(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        int step = normalAttackStep;
        StatsContainer snapshot = captureLiveStats(castTime);
        double flatDamage = attackFlatDamage(snapshot);
        for (int variant = 0;
                variant < NORMAL_HIT_FRAMES[step].length;
                variant++) {
            queueHit(simulator, new PendingHit(
                    castTime + NORMAL_HIT_FRAMES[step][variant] * FRAME,
                    HitKind.NORMAL,
                    step,
                    snapshot,
                    flatDamage,
                    0L));
        }
        normalAttackStep = (normalAttackStep + 1)
                % NORMAL_HIT_FRAMES.length;
        simulator.advanceTime(NORMAL_DURATIONS[step] * FRAME);
    }

    private void chargedAttack(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        StatsContainer snapshot = captureLiveStats(castTime);
        queueHit(simulator, new PendingHit(
                castTime + 4.0 * FRAME,
                HitKind.CHARGED,
                0,
                snapshot,
                attackFlatDamage(snapshot),
                0L));
        simulator.advanceTime(47.0 * FRAME);
    }

    private void highPlunge(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        StatsContainer snapshot = captureLiveStats(castTime);
        queueHit(simulator, new PendingHit(
                castTime + 45.0 * FRAME,
                HitKind.HIGH_PLUNGE,
                0,
                snapshot,
                attackFlatDamage(snapshot),
                0L));
        simulator.advanceTime(76.0 * FRAME);
    }

    private void dominusLapidis(
            CombatSimulator simulator,
            boolean hold) {
        double castTime = simulator.getCurrentTime();
        if (hold) {
            StatsContainer snapshot = captureLiveStats(castTime);
            queueHit(simulator, new PendingHit(
                    castTime + 48.0 * FRAME,
                    HitKind.HOLD,
                    0,
                    snapshot,
                    skillFlatDamage(snapshot),
                    0L));
            queueCommand(simulator, new PendingCommand(
                    castTime + 48.0 * FRAME,
                    CommandKind.CREATE_STELE_IF_ABSENT,
                    0.0));
            queueSkillCooldown(simulator, castTime, 47.0, 12.0);
            simulator.advanceTime(96.0 * FRAME);
            return;
        }
        queueCommand(simulator, new PendingCommand(
                castTime + 24.0 * FRAME,
                CommandKind.CREATE_STELE,
                0.0));
        queueSkillCooldown(simulator, castTime, 22.0, 4.0);
        simulator.advanceTime(38.0 * FRAME);
    }

    private void queueSkillCooldown(
            CombatSimulator simulator,
            double castTime,
            double delayFrames,
            double cooldown) {
        queueCommand(simulator, new PendingCommand(
                castTime + delayFrames * FRAME,
                CommandKind.SKILL_COOLDOWN,
                cooldown));
    }

    private void planetBefall(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        StatsContainer snapshot = captureLiveStats(castTime);
        markBurstCooldownUsed(
                castTime, simulator.getApplicableBuffs(this));
        queueCommand(simulator, new PendingCommand(
                castTime + 7.0 * FRAME,
                CommandKind.BURST_ENERGY,
                0.0));
        queueHit(simulator, new PendingHit(
                castTime + 101.0 * FRAME,
                HitKind.BURST,
                0,
                snapshot,
                burstFlatDamage(snapshot),
                0L));
        simulator.advanceTime(139.0 * FRAME);
    }

    private void createStele(CombatSimulator simulator) {
        double currentTime = simulator.getCurrentTime();
        long generation = ++steleGeneration;
        steleExpirationTime = currentTime
                + getTalentValue("Stone Stele Duration", 31.0);
        StatsContainer snapshot = captureLiveStats(currentTime);
        PendingHit initial = new PendingHit(
                currentTime,
                HitKind.STELE_INITIAL,
                0,
                snapshot,
                skillFlatDamage(snapshot),
                generation);
        resolveHit(simulator, initial);
        queueHit(simulator, new PendingHit(
                currentTime
                        + getTalentValue("Stone Stele Interval", 2.0),
                HitKind.STELE_PULSE,
                0,
                snapshot,
                skillFlatDamage(snapshot),
                generation));
    }

    private void resolveHit(CombatSimulator simulator, PendingHit hit) {
        switch (hit.kind) {
            case NORMAL:
                performHit(
                        simulator,
                        hit,
                        "Rain of Stone N" + (hit.index + 1),
                        normalValue(hit.index),
                        Element.PHYSICAL,
                        StatType.NORMAL_ATTACK_DMG_BONUS,
                        ActionType.NORMAL,
                        ICDType.Standard,
                        ICDTag.NormalAttack,
                        0.0);
                break;
            case CHARGED:
                performHit(
                        simulator,
                        hit,
                        "Rain of Stone Charged",
                        getTalentValue("Charged Attack", 2.039780),
                        Element.PHYSICAL,
                        StatType.CHARGED_ATTACK_DMG_BONUS,
                        ActionType.CHARGE,
                        ICDType.Standard,
                        ICDTag.ChargedAttack,
                        0.0);
                break;
            case HIGH_PLUNGE:
                performHit(
                        simulator,
                        hit,
                        "Rain of Stone High Plunge",
                        getTalentValue("High Plunge", 2.747916),
                        Element.PHYSICAL,
                        StatType.PLUNGING_ATTACK_DMG_BONUS,
                        ActionType.PLUNGE,
                        ICDType.None,
                        ICDTag.None,
                        0.0);
                break;
            case HOLD:
                performHit(
                        simulator,
                        hit,
                        "Dominus Lapidis Hold",
                        skillValue("Hold Damage", 1.360000, 1.600000),
                        Element.GEO,
                        StatType.SKILL_DMG_BONUS,
                        ActionType.SKILL,
                        ICDType.Standard,
                        ICDTag.ElementalSkill,
                        1.0);
                break;
            case STELE_INITIAL:
                performHit(
                        simulator,
                        hit,
                        "Stone Stele Initial",
                        skillValue(
                                "Stone Stele Initial", 0.272000, 0.320000),
                        Element.GEO,
                        StatType.SKILL_DMG_BONUS,
                        ActionType.SKILL,
                        ICDType.Standard,
                        ICDTag.ElementalSkill,
                        2.0);
                triggerParticle(simulator, hit.time);
                break;
            case STELE_PULSE:
                resolveStelePulse(simulator, hit);
                break;
            case BURST:
                performHit(
                        simulator,
                        hit,
                        "Planet Befall",
                        burstValue(),
                        Element.GEO,
                        StatType.BURST_DMG_BONUS,
                        ActionType.BURST,
                        ICDType.None,
                        ICDTag.ElementalBurst,
                        4.0);
                break;
            default:
                throw new IllegalStateException(
                        "Unknown Zhongli hit kind " + hit.kind);
        }
    }

    private void resolveStelePulse(
            CombatSimulator simulator,
            PendingHit hit) {
        if (hit.generation != steleGeneration
                || hit.time + EPSILON >= steleExpirationTime) {
            return;
        }
        performHit(
                simulator,
                hit,
                "Stone Stele Pulse",
                skillValue(
                        "Stone Stele Pulse", 0.544000, 0.640000),
                Element.GEO,
                StatType.SKILL_DMG_BONUS,
                ActionType.SKILL,
                ICDType.Standard,
                ICDTag.ElementalSkill,
                1.0);
        triggerParticle(simulator, hit.time);
        double nextTime = hit.time
                + getTalentValue("Stone Stele Interval", 2.0);
        if (nextTime + EPSILON < steleExpirationTime) {
            queueHit(simulator, new PendingHit(
                    nextTime,
                    HitKind.STELE_PULSE,
                    0,
                    hit.snapshot,
                    hit.flatDamage,
                    hit.generation));
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
                    "Zhongli particle random draw must be in [0, 1)");
        }
        nextParticleAllowedTime = hitTime
                + getTalentValue("Particle Cooldown", 1.5);
        if (draw >= getTalentValue("Particle Chance", 0.5)) {
            return;
        }
        queueCommand(simulator, new PendingCommand(
                hitTime
                        + getTalentValue("Particle Travel Frames", 100.0)
                                * FRAME,
                CommandKind.PARTICLE,
                getTalentValue("Particle Count", 1.0)));
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
        AttackAction action = hit.flatDamage == 0.0
                ? new AttackAction(
                        displayName,
                        multiplier,
                        hitElement,
                        StatType.BASE_ATK,
                        bonusStat,
                        0.0,
                        actionType)
                : new ZhongliAttackAction(
                        displayName,
                        multiplier,
                        hitElement,
                        bonusStat,
                        actionType,
                        hit.flatDamage);
        action.setICD(icdType, icdTag, gaugeUnits);
        if (hit.kind == HitKind.CHARGED) {
            action.setHitlagProfile(CHARGED_HITLAG);
        } else if (hit.kind == HitKind.NORMAL && hit.index != 4) {
            action.setHitlagProfile(NORMAL_HITLAG);
        }
        action.setCountsAsSkillDmg(actionType == ActionType.SKILL);
        action.setCountsAsBurstDmg(actionType == ActionType.BURST);
        action.setStatSnapshot(hit.snapshot == null
                ? captureLiveStats(simulator.getCurrentTime())
                : hit.snapshot);
        simulator.performActionWithoutTimeAdvance(characterId, action);
    }

    private double normalValue(int step) {
        return getTalentValue("N" + (step + 1), NORMAL_T9[step]);
    }

    private double skillValue(
            String key,
            double talentNine,
            double talentTwelve) {
        String suffix = constellation >= 3 ? " C3" : "";
        return getTalentValue(key + suffix,
                constellation >= 3 ? talentTwelve : talentNine);
    }

    private double burstValue() {
        String suffix = constellation >= 5 ? " C5" : "";
        return getTalentValue("Planet Befall" + suffix,
                constellation >= 5 ? 10.298000 : 8.346800);
    }

    private double attackFlatDamage(StatsContainer snapshot) {
        return snapshot.getTotalHp()
                * getTalentValue("A4 Attack Max HP Ratio", 0.0139);
    }

    private double skillFlatDamage(StatsContainer snapshot) {
        return snapshot.getTotalHp()
                * getTalentValue("A4 Skill Max HP Ratio", 0.019);
    }

    private double burstFlatDamage(StatsContainer snapshot) {
        return snapshot.getTotalHp()
                * getTalentValue("A4 Burst Max HP Ratio", 0.33);
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
                case CREATE_STELE:
                    createStele(activeSimulator);
                    break;
                case CREATE_STELE_IF_ABSENT:
                    if (!isStoneSteleActive(
                            activeSimulator.getCurrentTime())) {
                        createStele(activeSimulator);
                    }
                    break;
                case SKILL_COOLDOWN:
                    setSkillCD(command.value);
                    markSkillUsed(
                            activeSimulator.getCurrentTime(),
                            activeSimulator.getApplicableBuffs(this));
                    setSkillCD(getTalentValue(
                            "Press Skill Cooldown", 4.0));
                    break;
                case BURST_ENERGY:
                    spendBurstEnergy(activeSimulator.getCurrentTime());
                    break;
                case PARTICLE:
                    activeSimulator.getEnergyDistributor()
                            .distributeParticles(
                                    Element.GEO,
                                    command.value,
                                    ParticleType.PARTICLE);
                    break;
                default:
                    throw new IllegalStateException(
                            "Unknown Zhongli command kind " + command.kind);
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
        HIGH_PLUNGE,
        HOLD,
        STELE_INITIAL,
        STELE_PULSE,
        BURST
    }

    private enum CommandKind {
        CREATE_STELE,
        CREATE_STELE_IF_ABSENT,
        SKILL_COOLDOWN,
        BURST_ENERGY,
        PARTICLE
    }

    /** Preserves A4's fixed Max-HP addition through reaction resolution. */
    private static final class ZhongliAttackAction extends AttackAction {
        private final double fixedAdditiveBaseDamage;

        private ZhongliAttackAction(
                String displayName,
                double multiplier,
                Element element,
                StatType bonusStat,
                ActionType actionType,
                double fixedAdditiveBaseDamage) {
            super(
                    displayName,
                    multiplier,
                    element,
                    StatType.BASE_ATK,
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

    /** Immutable future hit with snapshot, flat addition, and generation. */
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

    /** Immutable snapshot of all Zhongli-owned mutable runtime state. */
    private static final class ZhongliState implements State {
        private final Zhongli owner;
        private final int normalAttackStep;
        private final long steleGeneration;
        private final double steleExpirationTime;
        private final double nextParticleAllowedTime;
        private final List<PendingHit> pendingHits;
        private final List<PendingCommand> pendingCommands;

        private ZhongliState(
                Zhongli owner,
                int normalAttackStep,
                long steleGeneration,
                double steleExpirationTime,
                double nextParticleAllowedTime,
                List<PendingHit> pendingHits,
                List<PendingCommand> pendingCommands) {
            this.owner = owner;
            this.normalAttackStep = normalAttackStep;
            this.steleGeneration = steleGeneration;
            this.steleExpirationTime = steleExpirationTime;
            this.nextParticleAllowedTime = nextParticleAllowedTime;
            this.pendingHits = copyHits(pendingHits);
            this.pendingCommands = copyCommands(pendingCommands);
        }
    }
}

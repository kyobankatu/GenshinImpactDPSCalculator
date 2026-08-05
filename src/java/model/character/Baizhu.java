package model.character;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.DoubleSupplier;

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
 * Baizhu's fixed-target Gossamer Sprite and Spiritvein offensive slice.
 *
 * <p>The Classics of Acupuncture, Universal Diagnosis's three-hit chain and
 * particles, Holistic Revivification's six natural Spiritveins, A4, C1-C4,
 * and C6 follow pinned gcsim {@code ef41805d}. Defensive shield objects are
 * not synthesized; only natural refresh and expiry work with deterministic
 * offensive consequences is scheduled.</p>
 *
 * <p>Healing, current-HP A1, shield absorption and early break, overlapping
 * shield replacement, movement, target-chain geometry, multi-target behavior,
 * stamina, hitlag, and Low/High Plunge data are excluded rather than
 * approximated.</p>
 */
public final class Baizhu extends Character implements
        SimulatorInitializedCharacterEffect,
        SnapshotAwareCharacterEffect,
        SwitchAwareCharacter {
    private static final double FRAME = 1.0 / 60.0;
    private static final double EPSILON = 1e-9;
    private static final int[] NORMAL_DURATIONS = { 33, 39, 46, 67 };
    private static final int[][] NORMAL_HIT_FRAMES = {
        { 17 }, { 25 }, { 23, 35 }, { 30 }
    };
    private static final double[][] NORMAL_T9 = {
        { 0.635297 },
        { 0.619222 },
        { 0.383207, 0.383207 },
        { 0.920339 }
    };
    private static final int[] BURST_REFRESH_FRAMES = {
        223, 369, 515, 661, 807, 959
    };

    private final DoubleSupplier particleRandom;
    private CombatSimulator initializedSimulator;
    private int normalAttackStep;
    private long burstGeneration;
    private double nextC2AllowedTime = Double.NEGATIVE_INFINITY;
    private List<PendingEvent> pendingEvents = new ArrayList<>();

    /** Constructs repository-default C6 Baizhu. */
    public Baizhu(Weapon weapon, ArtifactSet artifacts) {
        this(weapon, artifacts, TalentDataManager.getInstance(), 6,
                Math::random);
    }

    /** Constructs Baizhu at an explicit constellation. */
    public Baizhu(
            Weapon weapon,
            ArtifactSet artifacts,
            int constellation) {
        this(weapon, artifacts, TalentDataManager.getInstance(),
                constellation, Math::random);
    }

    /**
     * Constructs Baizhu with injectable talent data and particle randomness.
     *
     * @param weapon equipped catalyst, or {@code null}
     * @param artifacts equipped artifact set, or {@code null}
     * @param talentData static character-data source
     * @param constellation constellation level in {@code [0, 6]}
     * @param particleRandom source of particle draws in {@code [0, 1)}
     */
    public Baizhu(
            Weapon weapon,
            ArtifactSet artifacts,
            TalentDataSource talentData,
            int constellation,
            DoubleSupplier particleRandom) {
        super(talentData);
        if (constellation < 0 || constellation > 6) {
            throw new IllegalArgumentException(
                    "Baizhu constellation must be between 0 and 6");
        }
        if (particleRandom == null) {
            throw new IllegalArgumentException(
                    "Baizhu particle random source is required");
        }
        name = "Baizhu";
        characterId = CharacterId.BAIZHU;
        element = Element.DENDRO;
        this.weapon = weapon;
        this.artifacts = new ArtifactSet[] { artifacts };
        this.constellation = constellation;
        this.particleRandom = particleRandom;
        baseStats.set(StatType.BASE_HP,
                getTalentValue("Base HP", 13348.0));
        baseStats.set(StatType.BASE_ATK,
                getTalentValue("Base ATK", 193.0));
        baseStats.set(StatType.BASE_DEF,
                getTalentValue("Base DEF", 500.0));
        baseStats.add(StatType.HP_PERCENT,
                getTalentValue("Ascension HP Percent", 0.288));
        setSkillCD(getTalentValue("Skill Cooldown", 10.0));
        setBurstCD(getTalentValue("Burst Cooldown", 20.0));
        if (constellation >= 1) {
            setSkillMaxCharges(2);
        }
    }

    /** Binds C2 observation and delayed work to exactly one simulator. */
    @Override
    public void initializeForSimulator(CombatSimulator simulator) {
        if (simulator == null) {
            throw new IllegalArgumentException("Baizhu simulator is required");
        }
        if (!simulator.getPartyMembers().contains(this)) {
            throw new IllegalArgumentException(
                    "Baizhu must belong to the simulator party");
        }
        if (initializedSimulator != null
                && initializedSimulator != simulator) {
            throw new IllegalStateException(
                    "Baizhu cannot be reused across simulators");
        }
        if (initializedSimulator == simulator) {
            return;
        }
        initializedSimulator = simulator;
        if (constellation >= 2) {
            simulator.addDamageListener((actor, action, damage, time) ->
                    observeC2Damage(
                            simulator, actor, action, damage, time));
        }
    }

    /** Captures combo, C2 gate, Burst generation, and all future work. */
    @Override
    public State captureCharacterState() {
        return new BaizhuState(
                this,
                normalAttackStep,
                burstGeneration,
                nextC2AllowedTime,
                pendingEvents);
    }

    /** Accepts only state captured from this exact Baizhu instance. */
    @Override
    public boolean acceptsCharacterState(State state) {
        return state instanceof BaizhuState
                && ((BaizhuState) state).owner == this;
    }

    /** Restores Baizhu-owned state and schedules surviving events once. */
    @Override
    public void restoreCharacterState(
            State state,
            CombatSimulator simulator) {
        if (!acceptsCharacterState(state)) {
            throw new IllegalArgumentException("Unexpected Baizhu state");
        }
        initializeForSimulator(simulator);
        BaizhuState restored = (BaizhuState) state;
        normalAttackStep = restored.normalAttackStep;
        burstGeneration = restored.burstGeneration;
        nextC2AllowedTime = restored.nextC2AllowedTime;
        pendingEvents = copyEvents(restored.pendingEvents);
        double currentTime = simulator.getCurrentTime();
        pendingEvents.removeIf(event ->
                event.time < currentTime - EPSILON);
        for (PendingEvent event : new ArrayList<>(pendingEvents)) {
            scheduleEvent(simulator, event);
        }
    }

    /** Returns Baizhu's 80-Energy Burst cost. */
    @Override
    public double getEnergyCost() {
        return getTalentValue("Energy Cost", 80.0);
    }

    /** Current-HP A1 is intentionally inactive without player HP state. */
    @Override
    public void applyPassive(StatsContainer stats) {
    }

    /** Resets only Baizhu's on-field Normal progression. */
    @Override
    public void onSwitchOut(CombatSimulator simulator) {
        normalAttackStep = 0;
    }

    /** Returns the next timestamp at which C2 may trigger. */
    public double getNextC2AllowedTime() {
        return nextC2AllowedTime;
    }

    /** Returns the count of unresolved Baizhu-owned events. */
    public int getPendingEventCount() {
        return pendingEvents.size();
    }

    /** Dispatches Baizhu's represented typed action set. */
    @Override
    public void onAction(
            CharacterActionRequest request,
            CombatSimulator simulator) {
        if (request == null) {
            throw new IllegalArgumentException("Baizhu action is required");
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
                universalDiagnosis(simulator);
                break;
            case BURST:
                holisticRevivification(simulator);
                break;
            case PLUNGE:
                throw new IllegalArgumentException(
                        "Baizhu Plunge data is outside the pinned source slice");
            default:
                throw new IllegalArgumentException(
                        "Unsupported action for Baizhu: " + request.getKey());
        }
    }

    private void normalAttack(CombatSimulator simulator) {
        int step = normalAttackStep;
        double castTime = simulator.getCurrentTime();
        StatsContainer snapshot = captureLiveStats(castTime);
        for (int hit = 0; hit < NORMAL_HIT_FRAMES[step].length; hit++) {
            queueEvent(simulator, new PendingEvent(
                    castTime + NORMAL_HIT_FRAMES[step][hit] * FRAME,
                    EventKind.NORMAL_HIT,
                    step,
                    hit,
                    0L,
                    snapshot,
                    0.0));
        }
        normalAttackStep = (normalAttackStep + 1)
                % NORMAL_HIT_FRAMES.length;
        simulator.advanceTime(NORMAL_DURATIONS[step] * FRAME);
    }

    private void chargedAttack(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        queueEvent(simulator, new PendingEvent(
                castTime + 59.0 * FRAME,
                EventKind.CHARGED_HIT,
                0,
                0,
                0L,
                captureLiveStats(castTime),
                0.0));
        simulator.advanceTime(75.0 * FRAME);
    }

    private void universalDiagnosis(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        StatsContainer snapshot = captureLiveStats(castTime);
        double particleCount = simulator.getEnemy() == null
                ? 0.0 : particleCount();
        for (int hit = 0; hit < 3; hit++) {
            queueEvent(simulator, new PendingEvent(
                    castTime + (13.0 + hit * 48.0) * FRAME,
                    EventKind.SKILL_HIT,
                    hit,
                    0,
                    0L,
                    snapshot,
                    hit == 0 ? particleCount : 0.0));
        }
        queueEvent(simulator, new PendingEvent(
                castTime + 23.0 * FRAME,
                EventKind.SKILL_COOLDOWN,
                0,
                0,
                0L,
                null,
                0.0));
        simulator.advanceTime(49.0 * FRAME);
    }

    private void holisticRevivification(
            CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        long generation = ++burstGeneration;
        markBurstCooldownUsed(
                castTime, simulator.getApplicableBuffs(this));
        queueEvent(simulator, new PendingEvent(
                castTime + 5.0 * FRAME,
                EventKind.BURST_ENERGY,
                0,
                0,
                generation,
                null,
                0.0));
        for (int refreshFrame : BURST_REFRESH_FRAMES) {
            queueEvent(simulator, new PendingEvent(
                    castTime + refreshFrame * FRAME,
                    EventKind.NATURAL_REFRESH,
                    0,
                    0,
                    generation,
                    null,
                    0.0));
        }
        if (constellation >= 4) {
            applyC4(simulator, castTime);
        }
        simulator.advanceTime(105.0 * FRAME);
    }

    private void observeC2Damage(
            CombatSimulator simulator,
            Character actor,
            AttackAction action,
            double damage,
            double time) {
        if (simulator != initializedSimulator
                || actor != simulator.getActiveCharacter()
                || !simulator.getPartyMembers().contains(actor)
                || damage <= 0.0
                || time + EPSILON < nextC2AllowedTime) {
            return;
        }
        nextC2AllowedTime = time
                + getTalentValue("C2 Cooldown", 5.0);
        queueEvent(simulator, new PendingEvent(
                time + getTalentValue("C2 Travel Frames", 13.0) * FRAME,
                EventKind.C2_HIT,
                0,
                0,
                0L,
                captureLiveStats(time),
                0.0));
    }

    private void resolveEvent(
            CombatSimulator simulator,
            PendingEvent event) {
        switch (event.kind) {
            case NORMAL_HIT:
                performHit(
                        simulator,
                        event,
                        "The Classics of Acupuncture N"
                                + (event.index + 1)
                                + (NORMAL_T9[event.index].length > 1
                                        ? " Hit " + (event.subIndex + 1)
                                        : ""),
                        NORMAL_T9[event.index][event.subIndex],
                        StatType.BASE_ATK,
                        StatType.NORMAL_ATTACK_DMG_BONUS,
                        ActionType.NORMAL,
                        ICDType.Standard,
                        ICDTag.NormalAttack,
                        1.0,
                        0.0);
                break;
            case CHARGED_HIT:
                performHit(
                        simulator,
                        event,
                        "The Classics of Acupuncture Charged Attack",
                        getTalentValue("Charged Attack", 2.057680),
                        StatType.BASE_ATK,
                        StatType.CHARGED_ATTACK_DMG_BONUS,
                        ActionType.CHARGE,
                        ICDType.None,
                        ICDTag.None,
                        1.0,
                        0.0);
                break;
            case SKILL_HIT:
                resolveSkillHit(simulator, event);
                break;
            case SKILL_COOLDOWN:
                markSkillUsed(
                        simulator.getCurrentTime(),
                        simulator.getApplicableBuffs(this));
                break;
            case C2_HIT:
                resolveC2Hit(simulator, event);
                break;
            case BURST_ENERGY:
                if (event.generation == burstGeneration) {
                    spendBurstEnergy(simulator.getCurrentTime());
                }
                break;
            case NATURAL_REFRESH:
                if (event.generation == 0L
                        || event.generation == burstGeneration) {
                    applyA4(simulator);
                    queueSpiritvein(simulator, event.generation);
                }
                break;
            case SPIRITVEIN_HIT:
                if (event.generation == 0L
                        || event.generation == burstGeneration) {
                    performSpiritvein(simulator, event);
                }
                break;
            case PARTICLE:
                simulator.getEnergyDistributor().distributeParticles(
                        Element.DENDRO,
                        event.value,
                        ParticleType.PARTICLE);
                break;
            default:
                throw new IllegalStateException(
                        "Unknown Baizhu event " + event.kind);
        }
    }

    private void resolveSkillHit(
            CombatSimulator simulator,
            PendingEvent event) {
        performHit(
                simulator,
                event,
                "Universal Diagnosis Hit " + (event.index + 1),
                skillValue(),
                StatType.BASE_ATK,
                StatType.SKILL_DMG_BONUS,
                ActionType.SKILL,
                ICDType.Standard,
                ICDTag.ElementalSkill,
                1.0,
                0.0);
        if (event.index == 0 && event.value > 0.0) {
            queueEvent(simulator, new PendingEvent(
                    event.time
                            + getTalentValue(
                                    "Particle Travel Frames", 100.0) * FRAME,
                    EventKind.PARTICLE,
                    0,
                    0,
                    0L,
                    null,
                    event.value));
        }
        if (event.index == 0 && constellation >= 6) {
            queueNaturalC6Refresh(simulator, event.time);
        }
    }

    private void resolveC2Hit(
            CombatSimulator simulator,
            PendingEvent event) {
        performHit(
                simulator,
                event,
                "Gossamer Sprite: Splice (C2)",
                getTalentValue("C2 Multiplier", 2.50),
                StatType.BASE_ATK,
                StatType.SKILL_DMG_BONUS,
                ActionType.SKILL,
                ICDType.Standard,
                ICDTag.Baizhu_C2,
                1.0,
                0.0);
        if (constellation >= 6) {
            queueNaturalC6Refresh(simulator, event.time);
        }
    }

    private void queueNaturalC6Refresh(
            CombatSimulator simulator,
            double creationTime) {
        queueEvent(simulator, new PendingEvent(
                creationTime
                        + getTalentValue(
                                "Natural Shield Lifetime Frames", 152.0)
                                * FRAME,
                EventKind.NATURAL_REFRESH,
                0,
                0,
                0L,
                null,
                0.0));
    }

    private void queueSpiritvein(
            CombatSimulator simulator,
            long generation) {
        double currentTime = simulator.getCurrentTime();
        StatsContainer snapshot = captureLiveStats(currentTime);
        double flatDamage = constellation >= 6
                ? snapshot.getTotalHp()
                        * getTalentValue(
                                "C6 Max HP Flat DMG Ratio", 0.08)
                : 0.0;
        queueEvent(simulator, new PendingEvent(
                currentTime
                        + getTalentValue(
                                "Spiritvein Release Travel Frames", 29.0)
                                * FRAME,
                EventKind.SPIRITVEIN_HIT,
                0,
                0,
                generation,
                snapshot,
                flatDamage));
    }

    private void performSpiritvein(
            CombatSimulator simulator,
            PendingEvent event) {
        performHit(
                simulator,
                event,
                "Holistic Revivification Spiritvein",
                burstValue(),
                StatType.BASE_ATK,
                StatType.BURST_DMG_BONUS,
                ActionType.BURST,
                ICDType.None,
                ICDTag.None,
                1.0,
                event.value);
    }

    private void applyA4(CombatSimulator simulator) {
        Character recipient = simulator.getActiveCharacter();
        if (recipient == null) {
            return;
        }
        double hpThousands = Math.min(
                getTalentValue("A4 HP Cap Thousands", 50.0),
                captureLiveStats(simulator.getCurrentTime()).getTotalHp()
                        / 1000.0);
        double bloomBonus = hpThousands * getTalentValue(
                "A4 Burning Bloom Bonus Per 1000 HP", 0.02);
        double catalyzeBonus = hpThousands * getTalentValue(
                "A4 Aggravate Spread Bonus Per 1000 HP", 0.008);
        double lunarBloomBonus = hpThousands * getTalentValue(
                "A4 Lunar Bloom Bonus Per 1000 HP", 0.007);
        recipient.removeBuff(BuffId.BAIZHU_A4_VERDANT_FAVOR);
        recipient.addBuff(new SimpleBuff(
                "Baizhu Year of Verdant Favor",
                BuffId.BAIZHU_A4_VERDANT_FAVOR,
                getTalentValue("A4 Duration", 6.0),
                simulator.getCurrentTime(),
                stats -> {
                    stats.add(StatType.BURNING_DMG_BONUS, bloomBonus);
                    stats.add(StatType.BLOOM_DMG_BONUS, bloomBonus);
                    stats.add(StatType.HYPERBLOOM_DMG_BONUS, bloomBonus);
                    stats.add(StatType.BURGEON_DMG_BONUS, bloomBonus);
                    stats.add(StatType.AGGRAVATE_DMG_BONUS, catalyzeBonus);
                    stats.add(StatType.SPREAD_DMG_BONUS, catalyzeBonus);
                    stats.add(
                            StatType.LUNAR_BLOOM_DMG_BONUS,
                            lunarBloomBonus);
                }).sourcedBy(characterId));
    }

    private void applyC4(
            CombatSimulator simulator,
            double castTime) {
        for (Character member : simulator.getPartyMembers()) {
            member.removeBuff(BuffId.BAIZHU_C4_PARTY_ELEMENTAL_MASTERY);
            member.addBuff(new SimpleBuff(
                    "Baizhu Ancient Art of Perception",
                    BuffId.BAIZHU_C4_PARTY_ELEMENTAL_MASTERY,
                    getTalentValue("C4 Duration", 15.0),
                    castTime,
                    stats -> stats.add(
                            StatType.ELEMENTAL_MASTERY,
                            getTalentValue(
                                    "C4 Elemental Mastery", 80.0)))
                    .sourcedBy(characterId));
        }
    }

    private void performHit(
            CombatSimulator simulator,
            PendingEvent event,
            String displayName,
            double multiplier,
            StatType scalingStat,
            StatType bonusStat,
            ActionType actionType,
            ICDType icdType,
            ICDTag icdTag,
            double gaugeUnits,
            double flatDamage) {
        AttackAction action = flatDamage == 0.0
                ? new AttackAction(
                        displayName,
                        multiplier,
                        Element.DENDRO,
                        scalingStat,
                        bonusStat,
                        0.0,
                        actionType)
                : new BaizhuAttackAction(
                        displayName,
                        multiplier,
                        scalingStat,
                        bonusStat,
                        actionType,
                        flatDamage);
        action.setICD(icdType, icdTag, gaugeUnits);
        action.setCountsAsSkillDmg(actionType == ActionType.SKILL);
        action.setCountsAsBurstDmg(actionType == ActionType.BURST);
        if (event.snapshot != null) {
            action.setStatSnapshot(event.snapshot);
        }
        simulator.performActionWithoutTimeAdvance(characterId, action);
    }

    private double skillValue() {
        return getTalentValue(
                constellation >= 5
                        ? "Universal Diagnosis C5"
                        : "Universal Diagnosis",
                constellation >= 5 ? 1.584000 : 1.346400);
    }

    private double burstValue() {
        return getTalentValue(
                constellation >= 3 ? "Spiritvein C3" : "Spiritvein",
                constellation >= 3 ? 1.941280 : 1.650088);
    }

    private double particleCount() {
        double draw = particleRandom.getAsDouble();
        if (!Double.isFinite(draw) || draw < 0.0 || draw >= 1.0) {
            throw new IllegalStateException(
                    "Baizhu particle random draw must be in [0, 1)");
        }
        return draw < getTalentValue("Particle Chance Four", 0.50)
                ? 4.0 : 3.0;
    }

    private StatsContainer captureLiveStats(double currentTime) {
        StatsContainer stats = getEffectiveStats(currentTime);
        if (initializedSimulator != null) {
            for (Buff buff
                    : initializedSimulator.getApplicableBuffs(this)) {
                if (!buff.isExpired(currentTime)) {
                    buff.apply(stats, currentTime);
                }
            }
        }
        return stats;
    }

    private void queueEvent(
            CombatSimulator simulator,
            PendingEvent event) {
        pendingEvents.add(event);
        scheduleEvent(simulator, event);
    }

    private void scheduleEvent(
            CombatSimulator simulator,
            PendingEvent event) {
        schedule(simulator, event.time, activeSimulator -> {
            if (!pendingEvents.remove(event)) {
                return;
            }
            resolveEvent(activeSimulator, event);
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

    private static List<PendingEvent> copyEvents(
            List<PendingEvent> source) {
        List<PendingEvent> copy = new ArrayList<>();
        for (PendingEvent event : source) {
            copy.add(event.copy());
        }
        return copy;
    }

    private enum EventKind {
        NORMAL_HIT,
        CHARGED_HIT,
        SKILL_HIT,
        SKILL_COOLDOWN,
        C2_HIT,
        BURST_ENERGY,
        NATURAL_REFRESH,
        SPIRITVEIN_HIT,
        PARTICLE
    }

    /** Preserves C6's refresh-time Max-HP addition through resolution. */
    private static final class BaizhuAttackAction extends AttackAction {
        private final double fixedBaseDamage;

        private BaizhuAttackAction(
                String displayName,
                double multiplier,
                StatType scalingStat,
                StatType bonusStat,
                ActionType actionType,
                double fixedBaseDamage) {
            super(
                    displayName,
                    multiplier,
                    Element.DENDRO,
                    scalingStat,
                    bonusStat,
                    0.0,
                    actionType);
            this.fixedBaseDamage = fixedBaseDamage;
        }

        @Override
        public void setAdditiveBaseDmgBonus(double value) {
        }

        @Override
        public double getAdditiveBaseDmgBonus() {
            return fixedBaseDamage;
        }
    }

    /** Immutable reconstructable Baizhu-owned event. */
    private static final class PendingEvent {
        private final double time;
        private final EventKind kind;
        private final int index;
        private final int subIndex;
        private final long generation;
        private final StatsContainer snapshot;
        private final double value;

        private PendingEvent(
                double time,
                EventKind kind,
                int index,
                int subIndex,
                long generation,
                StatsContainer snapshot,
                double value) {
            this.time = time;
            this.kind = kind;
            this.index = index;
            this.subIndex = subIndex;
            this.generation = generation;
            this.snapshot = snapshot == null
                    ? null : snapshot.merge(null);
            this.value = value;
        }

        private PendingEvent copy() {
            return new PendingEvent(
                    time,
                    kind,
                    index,
                    subIndex,
                    generation,
                    snapshot,
                    value);
        }
    }

    /** Immutable snapshot of all mutable Baizhu-owned runtime state. */
    private static final class BaizhuState implements State {
        private final Baizhu owner;
        private final int normalAttackStep;
        private final long burstGeneration;
        private final double nextC2AllowedTime;
        private final List<PendingEvent> pendingEvents;

        private BaizhuState(
                Baizhu owner,
                int normalAttackStep,
                long burstGeneration,
                double nextC2AllowedTime,
                List<PendingEvent> pendingEvents) {
            this.owner = owner;
            this.normalAttackStep = normalAttackStep;
            this.burstGeneration = burstGeneration;
            this.nextC2AllowedTime = nextC2AllowedTime;
            this.pendingEvents = copyEvents(pendingEvents);
        }
    }
}

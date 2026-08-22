package model.character;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import mechanics.buff.Buff;
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
import simulation.action.SkillActionMode;
import simulation.event.SimpleTimerEvent;

/**
 * Kirara's stationary single-target Cardamom offensive slice.
 *
 * <p>Boxcutter basics, Press Meow-teor Kick, Secret Art: Surprise
 * Dispatch, Cat Grass Cardamoms, A4, and offensive C1/C3-C6 behavior follow
 * pinned gcsim {@code ef41805d}. Cardamoms snapshot at frame 34, and all
 * owner-delayed work is reconstructable after simulator rollback.</p>
 *
 * <p>The C4 gate tracks only the source-defined twelve-second nominal shield
 * window. Shield absorption and strength, Hold movement and collision, A1,
 * co-op-only C2, player HP and healing, plunge data absent from the pinned
 * source, geometry, multi-target behavior, and stamina are excluded
 * rather than approximated.</p>
 */
public final class Kirara extends Character implements
        SimulatorInitializedCharacterEffect,
        SnapshotAwareCharacterEffect,
        SwitchAwareCharacter {
    private static final double FRAME = 1.0 / 60.0;
    private static final double EPSILON = 1e-9;
    private static final int[][] NORMAL_HIT_FRAMES = {
        { 13 }, { 18 }, { 17, 39 }, { 39 }
    };
    private static final int[] NORMAL_DURATIONS = { 33, 35, 61, 63 };
    private static final double[][] NORMAL_T9 = {
        { 0.880060 },
        { 0.851620 },
        { 0.467048, 0.700572 },
        { 1.346160 }
    };
    private static final int[] CHARGED_HIT_FRAMES = { 20, 27, 37 };
    private static final double[] CHARGED_T9 = {
        0.411116, 0.822232, 0.822232
    };
    /**
     * Per-hit metadata from gcsim pinned at
     * {@code 3647a07a7cc3004bc1e79d9bb5f7444de20dceaa}.
     */
    private static final HitlagProfile NORMAL_HITLAG_SHORT =
            new HitlagProfile(0.03, 0.01, true, false, false);
    private static final HitlagProfile NORMAL_HITLAG_N3_FIRST =
            new HitlagProfile(0.01, 0.01, true, false, false);
    private static final HitlagProfile NORMAL_HITLAG_N3_SECOND =
            new HitlagProfile(0.05, 0.01, true, false, false);
    private static final HitlagProfile NORMAL_HITLAG_N4 =
            new HitlagProfile(0.06, 0.01, true, false, false);
    private static final HitlagProfile CHARGED_FINAL_HITLAG =
            new HitlagProfile(0.10, 0.01, true, false, false);
    private static final HitlagProfile SKILL_HITLAG =
            new HitlagProfile(0.0, 0.01, true, false, false);
    private static final HitlagProfile DEPLOYABLE_DEFENSE_HITLAG =
            new HitlagProfile(0.0, 0.0, true, true, false);
    private static final HitlagProfile C4_HITLAG =
            new HitlagProfile(0.0, 0.0, true, false, false);

    private CombatSimulator initializedSimulator;
    private int normalAttackStep;
    private long shieldGeneration;
    private long burstGeneration;
    private int activeCardamoms;
    private double shieldExpirationTime = Double.NEGATIVE_INFINITY;
    private double nextC4AllowedTime = Double.NEGATIVE_INFINITY;
    private StatsContainer cardamomSnapshot;
    private Buff c6Buff;
    private List<PendingEvent> pendingEvents = new ArrayList<>();

    /** Constructs repository-default C6 Kirara. */
    public Kirara(Weapon weapon, ArtifactSet artifacts) {
        this(weapon, artifacts, TalentDataManager.getInstance(), 6);
    }

    /** Constructs Kirara at an explicit constellation. */
    public Kirara(
            Weapon weapon,
            ArtifactSet artifacts,
            int constellation) {
        this(weapon, artifacts, TalentDataManager.getInstance(), constellation);
    }

    /**
     * Constructs Kirara with explicit static data and constellation state.
     *
     * @param weapon equipped sword, or {@code null}
     * @param artifacts equipped artifact set, or {@code null}
     * @param talentData static character-data source
     * @param constellation constellation level in {@code [0, 6]}
     */
    public Kirara(
            Weapon weapon,
            ArtifactSet artifacts,
            TalentDataSource talentData,
            int constellation) {
        super(talentData);
        if (constellation < 0 || constellation > 6) {
            throw new IllegalArgumentException(
                    "Kirara constellation must be between 0 and 6");
        }
        name = "Kirara";
        characterId = CharacterId.KIRARA;
        element = Element.DENDRO;
        this.weapon = weapon;
        this.artifacts = new ArtifactSet[] { artifacts };
        this.constellation = constellation;
        baseStats.set(StatType.BASE_HP,
                getTalentValue("Base HP", 12180.0));
        baseStats.set(StatType.BASE_ATK,
                getTalentValue("Base ATK", 223.0));
        baseStats.set(StatType.BASE_DEF,
                getTalentValue("Base DEF", 546.0));
        baseStats.add(StatType.HP_PERCENT,
                getTalentValue("Ascension HP Percent", 0.24));
        setSkillCD(getTalentValue("Skill Cooldown", 8.0));
        setBurstCD(getTalentValue("Burst Cooldown", 15.0));
    }

    /** Binds Kirara's listener and delayed work to exactly one simulator. */
    @Override
    public void initializeForSimulator(CombatSimulator simulator) {
        if (simulator == null) {
            throw new IllegalArgumentException("Kirara simulator is required");
        }
        if (!simulator.getPartyMembers().contains(this)) {
            throw new IllegalArgumentException(
                    "Kirara must belong to the simulator party");
        }
        if (initializedSimulator != null
                && initializedSimulator != simulator) {
            throw new IllegalStateException(
                    "Kirara cannot be reused across simulators");
        }
        if (initializedSimulator == simulator) {
            return;
        }
        initializedSimulator = simulator;
        simulator.addDamageListener((actor, action, damage, time) ->
                observeC4Trigger(actor, action, time, simulator));
    }

    /** Captures Kirara's gates, Cardamom snapshot, and future event queue. */
    @Override
    public State captureCharacterState() {
        return new KiraraState(
                this,
                normalAttackStep,
                shieldGeneration,
                burstGeneration,
                activeCardamoms,
                shieldExpirationTime,
                nextC4AllowedTime,
                cardamomSnapshot,
                c6Buff,
                pendingEvents);
    }

    /** Accepts state captured from this exact Kirara instance only. */
    @Override
    public boolean acceptsCharacterState(State state) {
        return state instanceof KiraraState
                && ((KiraraState) state).owner == this;
    }

    /** Restores Kirara-owned state and re-registers surviving events once. */
    @Override
    public void restoreCharacterState(
            State state,
            CombatSimulator simulator) {
        if (!acceptsCharacterState(state)) {
            throw new IllegalArgumentException("Unexpected Kirara state");
        }
        initializeForSimulator(simulator);
        KiraraState restored = (KiraraState) state;
        normalAttackStep = restored.normalAttackStep;
        shieldGeneration = restored.shieldGeneration;
        burstGeneration = restored.burstGeneration;
        activeCardamoms = restored.activeCardamoms;
        shieldExpirationTime = restored.shieldExpirationTime;
        nextC4AllowedTime = restored.nextC4AllowedTime;
        cardamomSnapshot = copyStats(restored.cardamomSnapshot);
        c6Buff = restored.c6Buff;
        pendingEvents = copyEvents(restored.pendingEvents);
        double currentTime = simulator.getCurrentTime();
        pendingEvents.removeIf(event ->
                event.time < currentTime - EPSILON);
        for (PendingEvent event : new ArrayList<>(pendingEvents)) {
            scheduleEvent(simulator, event);
        }
    }

    /** Returns Kirara's 60-Energy Burst cost. */
    @Override
    public double getEnergyCost() {
        return getTalentValue("Energy Cost", 60.0);
    }

    /** Kirara's represented A4 passive is scoped to individual actions. */
    @Override
    public void applyPassive(StatsContainer stats) {
    }

    /** Only Press Meow-teor Kick is represented. */
    @Override
    protected boolean supportsSkillActionMode(SkillActionMode mode) {
        return mode == SkillActionMode.PRESS;
    }

    /** Resets the on-field Normal string. */
    @Override
    public void onSwitchOut(CombatSimulator simulator) {
        normalAttackStep = 0;
    }

    /** Resets the on-field Normal string. */
    @Override
    public void onSwitchIn(CombatSimulator simulator) {
        normalAttackStep = 0;
    }

    /** Returns whether the nominal C4-enabling shield window remains active. */
    public boolean isNominalShieldActive(double currentTime) {
        return shieldGeneration > 0
                && currentTime + EPSILON < shieldExpirationTime;
    }

    /** Returns the number of unresolved Cardamoms from the latest Burst. */
    public int getActiveCardamomCount() {
        return activeCardamoms;
    }

    /** Returns the count of unresolved Kirara-owned events. */
    public int getPendingEventCount() {
        return pendingEvents.size();
    }

    /** Dispatches Kirara's represented typed action set. */
    @Override
    public void onAction(
            CharacterActionRequest request,
            CombatSimulator simulator) {
        if (request == null) {
            throw new IllegalArgumentException("Kirara action is required");
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
                if (request.getSkillMode() != SkillActionMode.PRESS) {
                    throw new IllegalArgumentException(
                            "Kirara Hold Skill is outside this slice");
                }
                meowTeorKick(simulator);
                break;
            case BURST:
                surpriseDispatch(simulator);
                break;
            case PLUNGE:
                throw new IllegalArgumentException(
                        "Kirara Plunge is absent from the pinned source slice");
            default:
                throw new IllegalArgumentException(
                        "Unsupported action for Kirara: " + request.getKey());
        }
    }

    private void normalAttack(CombatSimulator simulator) {
        int step = normalAttackStep;
        double castTime = simulator.getCurrentTime();
        for (int hit = 0; hit < NORMAL_HIT_FRAMES[step].length; hit++) {
            queueEvent(simulator, new PendingEvent(
                    castTime + NORMAL_HIT_FRAMES[step][hit] * FRAME,
                    EventKind.NORMAL_HIT,
                    step,
                    hit,
                    0L,
                    null));
        }
        normalAttackStep = (normalAttackStep + 1)
                % NORMAL_HIT_FRAMES.length;
        simulator.advanceTime(NORMAL_DURATIONS[step] * FRAME);
    }

    private void chargedAttack(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        for (int hit = 0; hit < CHARGED_HIT_FRAMES.length; hit++) {
            queueEvent(simulator, new PendingEvent(
                    castTime + CHARGED_HIT_FRAMES[hit] * FRAME,
                    EventKind.CHARGED_HIT,
                    hit,
                    0,
                    0L,
                    null));
        }
        simulator.advanceTime(52.0 * FRAME);
    }

    private void meowTeorKick(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        StatsContainer skillSnapshot = captureLiveStats(castTime);
        queueEvent(simulator, new PendingEvent(
                castTime + 12.0 * FRAME,
                EventKind.SKILL_COOLDOWN,
                0,
                0,
                0L,
                null));
        queueEvent(simulator, new PendingEvent(
                castTime + 14.0 * FRAME,
                EventKind.SKILL_HIT,
                0,
                0,
                0L,
                skillSnapshot));
        applyC6(simulator, castTime);
        simulator.advanceTime(38.0 * FRAME);
    }

    private void surpriseDispatch(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        markBurstCooldownUsed(
                castTime, simulator.getApplicableBuffs(this));
        applyC6(simulator, castTime);
        long generation = ++burstGeneration;
        cardamomSnapshot = null;
        activeCardamoms = cardamomCount(captureLiveStats(castTime));
        queueEvent(simulator, new PendingEvent(
                castTime + 7.0 * FRAME,
                EventKind.BURST_ENERGY,
                0,
                0,
                generation,
                null));
        queueEvent(simulator, new PendingEvent(
                castTime + getTalentValue(
                        "Cardamom Snapshot Frames", 34.0) * FRAME,
                EventKind.CARDAMOM_SNAPSHOT,
                0,
                0,
                generation,
                null));
        queueEvent(simulator, new PendingEvent(
                castTime + 38.0 * FRAME,
                EventKind.BURST_HIT,
                0,
                0,
                generation,
                null));
        int earlyHits = Math.min(activeCardamoms, (int) getTalentValue(
                "Cardamom Default Early Hits", 2.0));
        double earlyTime = castTime + getTalentValue(
                "Cardamom Early Hit Frames", 180.0) * FRAME;
        for (int hit = 0; hit < earlyHits; hit++) {
            queueEvent(simulator, new PendingEvent(
                    earlyTime,
                    EventKind.CARDAMOM_HIT,
                    hit,
                    0,
                    generation,
                    null));
        }
        double expirationTime = castTime + getTalentValue(
                "Cardamom Expiration Frames", 800.0) * FRAME;
        double spacing = getTalentValue(
                "Cardamom Expiration Spacing Frames", 18.0) * FRAME;
        for (int hit = earlyHits; hit < activeCardamoms; hit++) {
            queueEvent(simulator, new PendingEvent(
                    expirationTime + (hit - earlyHits) * spacing,
                    EventKind.CARDAMOM_HIT,
                    hit,
                    0,
                    generation,
                    null));
        }
        simulator.advanceTime(58.0 * FRAME);
    }

    private int cardamomCount(StatsContainer castStats) {
        int base = (int) getTalentValue("C1 Base Cardamoms", 6.0);
        if (constellation < 1) {
            return base;
        }
        int bonus = (int) Math.floor(castStats.getTotalHp()
                / getTalentValue("C1 Max HP Per Cardamom", 8000.0));
        int cap = (int) getTalentValue("C1 Bonus Cardamom Cap", 4.0);
        return base + Math.min(cap, Math.max(0, bonus));
    }

    private void observeC4Trigger(
            Character actor,
            AttackAction action,
            double time,
            CombatSimulator simulator) {
        if (constellation < 4
                || !isNominalShieldActive(time)
                || time + EPSILON < nextC4AllowedTime
                || action == null) {
            return;
        }
        ActionType type = action.getActionType();
        if (type != ActionType.NORMAL
                && type != ActionType.CHARGE
                && type != ActionType.PLUNGE) {
            return;
        }
        nextC4AllowedTime = time + getTalentValue("C4 Cooldown", 3.8);
        performHit(
                simulator,
                "Small Cat Grass Cardamom",
                getTalentValue("C4 Coordinated Multiplier", 2.0),
                Element.DENDRO,
                StatType.BASE_ATK,
                StatType.BURST_DMG_BONUS,
                ActionType.BURST,
                ICDType.Standard,
                ICDTag.ElementalBurst,
                1.0,
                null,
                C4_HITLAG);
    }

    private void applyC6(
            CombatSimulator simulator,
            double currentTime) {
        if (constellation < 6) {
            return;
        }
        double bonus = getTalentValue("C6 Team DMG Bonus", 0.12);
        if (c6Buff != null) {
            simulator.getTeamBuffList().remove(c6Buff);
        }
        c6Buff = new SimpleBuff(
                "Kirara Countless Sights to See",
                getTalentValue("C6 Duration", 15.0),
                currentTime,
                stats -> {
                    stats.add(StatType.PYRO_DMG_BONUS, bonus);
                    stats.add(StatType.HYDRO_DMG_BONUS, bonus);
                    stats.add(StatType.ELECTRO_DMG_BONUS, bonus);
                    stats.add(StatType.CRYO_DMG_BONUS, bonus);
                    stats.add(StatType.ANEMO_DMG_BONUS, bonus);
                    stats.add(StatType.GEO_DMG_BONUS, bonus);
                    stats.add(StatType.DENDRO_DMG_BONUS, bonus);
                    stats.add(StatType.PHYSICAL_DMG_BONUS, bonus);
                }).sourcedBy(characterId);
        simulator.applyTeamBuff(c6Buff);
    }

    private void resolveEvent(
            CombatSimulator simulator,
            PendingEvent event) {
        switch (event.kind) {
            case NORMAL_HIT:
                performHit(
                        simulator,
                        "Boxcutter N" + (event.index + 1)
                                + (NORMAL_T9[event.index].length > 1
                                        ? "-" + (event.subIndex + 1) : ""),
                        getTalentValue(
                                "N" + (event.index + 1)
                                        + (NORMAL_T9[event.index].length > 1
                                                ? "-" + (event.subIndex + 1)
                                                : ""),
                                NORMAL_T9[event.index][event.subIndex]),
                        Element.PHYSICAL,
                        StatType.BASE_ATK,
                        StatType.NORMAL_ATTACK_DMG_BONUS,
                        ActionType.NORMAL,
                        ICDType.Standard,
                        ICDTag.NormalAttack,
                        0.0,
                        event.snapshot,
                        normalHitlag(event.index, event.subIndex));
                break;
            case CHARGED_HIT:
                performHit(
                        simulator,
                        "Boxcutter Charged " + (event.index + 1),
                        getTalentValue(
                                "Charged-" + (event.index + 1),
                                CHARGED_T9[event.index]),
                        Element.PHYSICAL,
                        StatType.BASE_ATK,
                        StatType.CHARGED_ATTACK_DMG_BONUS,
                        ActionType.CHARGE,
                        ICDType.Standard,
                        ICDTag.NormalAttack,
                        0.0,
                        event.snapshot,
                        event.index == 2 ? CHARGED_FINAL_HITLAG : null);
                break;
            case SKILL_COOLDOWN:
                markSkillUsed(
                        simulator.getCurrentTime(),
                        simulator.getApplicableBuffs(this));
                break;
            case SKILL_HIT:
                resolveSkillHit(simulator, event.snapshot);
                break;
            case SHIELD_EXPIRE:
                if (event.generation == shieldGeneration) {
                    shieldExpirationTime = Double.NEGATIVE_INFINITY;
                }
                break;
            case BURST_ENERGY:
                if (event.generation == burstGeneration) {
                    spendBurstEnergy(simulator.getCurrentTime());
                }
                break;
            case CARDAMOM_SNAPSHOT:
                if (event.generation == burstGeneration) {
                    cardamomSnapshot = captureLiveStats(
                            simulator.getCurrentTime());
                }
                break;
            case BURST_HIT:
                if (event.generation == burstGeneration) {
                    resolveBurstHit(simulator, null, false);
                }
                break;
            case CARDAMOM_HIT:
                if (event.generation == burstGeneration
                        && activeCardamoms > 0) {
                    resolveBurstHit(simulator, cardamomSnapshot, true);
                    activeCardamoms--;
                }
                break;
            default:
                throw new IllegalStateException(
                        "Unknown Kirara event " + event.kind);
        }
    }

    private void resolveSkillHit(
            CombatSimulator simulator,
            StatsContainer snapshot) {
        boolean c3 = constellation >= 3;
        performHit(
                simulator,
                "Tail-Flicking Flying Kick",
                getTalentValue(
                        c3 ? "Tail-Flicking Flying Kick C3"
                                : "Tail-Flicking Flying Kick",
                        c3 ? 2.080000 : 1.768000),
                Element.DENDRO,
                StatType.BASE_ATK,
                StatType.SKILL_DMG_BONUS,
                ActionType.SKILL,
                ICDType.None,
                ICDTag.None,
                1.0,
                snapshot,
                SKILL_HITLAG);
        simulator.getEnergyDistributor().distributeParticles(
                Element.DENDRO,
                getTalentValue("Skill Particle Count", 3.0),
                ParticleType.PARTICLE);
        long generation = ++shieldGeneration;
        shieldExpirationTime = simulator.getCurrentTime()
                + getTalentValue("Nominal Shield Duration", 12.0);
        queueEvent(simulator, new PendingEvent(
                shieldExpirationTime,
                EventKind.SHIELD_EXPIRE,
                0,
                0,
                generation,
                null));
    }

    private void resolveBurstHit(
            CombatSimulator simulator,
            StatsContainer snapshot,
            boolean cardamom) {
        boolean c5 = constellation >= 5;
        performHit(
                simulator,
                cardamom
                        ? "Cat Grass Cardamom Explosion"
                        : "Secret Art: Surprise Dispatch",
                getTalentValue(
                        (cardamom ? "Cat Grass Cardamom"
                                : "Secret Art Surprise Dispatch")
                                + (c5 ? " C5" : ""),
                        cardamom
                                ? (c5 ? 0.712800 : 0.605880)
                                : (c5 ? 11.404800 : 9.694080)),
                Element.DENDRO,
                StatType.BASE_ATK,
                StatType.BURST_DMG_BONUS,
                ActionType.BURST,
                cardamom ? ICDType.Standard : ICDType.None,
                cardamom ? ICDTag.ElementalBurst : ICDTag.None,
                cardamom ? 1.0 : 2.0,
                snapshot,
                cardamom ? DEPLOYABLE_DEFENSE_HITLAG : null);
    }

    private void performHit(
            CombatSimulator simulator,
            String displayName,
            double multiplier,
            Element hitElement,
            StatType scalingStat,
            StatType bonusStat,
            ActionType actionType,
            ICDType icdType,
            ICDTag icdTag,
            double gaugeUnits,
            StatsContainer requestedSnapshot,
            HitlagProfile hitlagProfile) {
        StatsContainer snapshot = requestedSnapshot == null
                ? captureLiveStats(simulator.getCurrentTime())
                : copyStats(requestedSnapshot);
        AttackAction action = new AttackAction(
                displayName,
                multiplier,
                hitElement,
                scalingStat,
                bonusStat,
                0.0,
                actionType);
        action.setICD(icdType, icdTag, gaugeUnits);
        action.setCountsAsSkillDmg(actionType == ActionType.SKILL);
        action.setCountsAsBurstDmg(actionType == ActionType.BURST);
        if (hitlagProfile != null) {
            action.setHitlagProfile(hitlagProfile);
        }
        if (actionType == ActionType.SKILL) {
            action.addBonusStat(
                    StatType.DMG_BONUS_ALL,
                    snapshot.getTotalHp() * getTalentValue(
                            "A4 Skill DMG Per Max HP", 0.000004));
        } else if (actionType == ActionType.BURST) {
            action.addBonusStat(
                    StatType.DMG_BONUS_ALL,
                    snapshot.getTotalHp() * getTalentValue(
                            "A4 Burst DMG Per Max HP", 0.000003));
        }
        action.setStatSnapshot(snapshot);
        simulator.performActionWithoutTimeAdvance(characterId, action);
    }

    private static HitlagProfile normalHitlag(int step, int hit) {
        if (step <= 1) {
            return NORMAL_HITLAG_SHORT;
        }
        if (step == 2) {
            return hit == 0
                    ? NORMAL_HITLAG_N3_FIRST
                    : NORMAL_HITLAG_N3_SECOND;
        }
        return NORMAL_HITLAG_N4;
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

    private static StatsContainer copyStats(StatsContainer source) {
        return source == null ? null : source.merge(null);
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
        SKILL_COOLDOWN,
        SKILL_HIT,
        SHIELD_EXPIRE,
        BURST_ENERGY,
        CARDAMOM_SNAPSHOT,
        BURST_HIT,
        CARDAMOM_HIT
    }

    /** Immutable reconstructable Kirara-owned event. */
    private static final class PendingEvent {
        private final double time;
        private final EventKind kind;
        private final int index;
        private final int subIndex;
        private final long generation;
        private final StatsContainer snapshot;

        private PendingEvent(
                double time,
                EventKind kind,
                int index,
                int subIndex,
                long generation,
                StatsContainer snapshot) {
            this.time = time;
            this.kind = kind;
            this.index = index;
            this.subIndex = subIndex;
            this.generation = generation;
            this.snapshot = copyStats(snapshot);
        }

        private PendingEvent copy() {
            return new PendingEvent(
                    time, kind, index, subIndex, generation, snapshot);
        }
    }

    /** Immutable snapshot of all Kirara-owned mutable runtime state. */
    private static final class KiraraState implements State {
        private final Kirara owner;
        private final int normalAttackStep;
        private final long shieldGeneration;
        private final long burstGeneration;
        private final int activeCardamoms;
        private final double shieldExpirationTime;
        private final double nextC4AllowedTime;
        private final StatsContainer cardamomSnapshot;
        private final Buff c6Buff;
        private final List<PendingEvent> pendingEvents;

        private KiraraState(
                Kirara owner,
                int normalAttackStep,
                long shieldGeneration,
                long burstGeneration,
                int activeCardamoms,
                double shieldExpirationTime,
                double nextC4AllowedTime,
                StatsContainer cardamomSnapshot,
                Buff c6Buff,
                List<PendingEvent> pendingEvents) {
            this.owner = owner;
            this.normalAttackStep = normalAttackStep;
            this.shieldGeneration = shieldGeneration;
            this.burstGeneration = burstGeneration;
            this.activeCardamoms = activeCardamoms;
            this.shieldExpirationTime = shieldExpirationTime;
            this.nextC4AllowedTime = nextC4AllowedTime;
            this.cardamomSnapshot = copyStats(cardamomSnapshot);
            this.c6Buff = c6Buff;
            this.pendingEvents = copyEvents(pendingEvents);
        }
    }
}

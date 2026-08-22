package model.character;

import java.util.ArrayList;
import java.util.List;
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
import simulation.event.SimpleTimerEvent;

/**
 * Chiori's fixed-target dual-scaling Tamoto offensive slice through C6.
 *
 * <p>Normal, Charged, high Plunge, upward sweep, Tamoto cadence, Burst,
 * Tailoring, and representable C1-C6 behavior follow pinned gcsim
 * {@code ef41805d}. ATK and DEF portions are evaluated together at each
 * sourced hit. Tamoto replacement and all delayed work survive rollback.</p>
 *
 * <p>Hold movement, the second Skill press and forced swap, Geo-construct
 * discovery, target geometry, hitlag extension, stamina, and low Plunge are excluded.
 * C1's second Tamoto therefore requires another Geo party member, while the
 * construct alternative and A4 construct trigger fail closed.</p>
 */
public final class Chiori extends Character implements
        SimulatorInitializedCharacterEffect,
        SnapshotAwareCharacterEffect,
        SwitchAwareCharacter {
    private static final double FRAME = 1.0 / 60.0;
    private static final double EPSILON = 1e-9;
    private static final int[] NORMAL_DURATIONS = { 22, 33, 42, 59 };
    private static final int[][] NORMAL_HIT_FRAMES = {
        { 17 }, { 16 }, { 24, 32 }, { 37 }
    };
    private static final String[][] NORMAL_KEYS = {
        { "N1" }, { "N2" },
        { "N3 Hit 1", "N3 Hit 2" }, { "N4" }
    };
    private static final double[][] NORMAL_T9 = {
        { 0.907773 }, { 0.860436 },
        { 0.558814, 0.558814 }, { 1.380162 }
    };
    private static final double TAMOTO_SPAWN = 19.0 * FRAME;
    private static final double TAMOTO_FIRST_DELAY = 0.6;
    private static final double TAMOTO_SECOND_DELAY = 1.2;
    private static final double KINU_DELAY = 41.0 * FRAME;

    /**
     * Per-hit hitlag from gcsim pinned at
     * {@code 3647a07a7cc3004bc1e79d9bb5f7444de20dceaa}.
     */
    private static final HitlagProfile NORMAL_SHORT_HITLAG =
            new HitlagProfile(0.03, 0.01, true, false, false);
    private static final HitlagProfile NORMAL_N3_FIRST_HITLAG =
            new HitlagProfile(0.06, 0.01, true, false, false);
    private static final HitlagProfile NORMAL_N4_HITLAG =
            new HitlagProfile(0.0, 0.05, true, false, false);
    private static final HitlagProfile CHARGED_SECOND_HITLAG =
            new HitlagProfile(0.06, 0.01, true, false, false);

    private final DoubleSupplier random;
    private CombatSimulator initializedSimulator;
    private int normalAttackStep;
    private long tamotoGeneration;
    private long a1Generation;
    private double tamotoExpirationTime = Double.NEGATIVE_INFINITY;
    private double nextParticleAllowedTime = Double.NEGATIVE_INFINITY;
    private double a1WindowStartTime = Double.NEGATIVE_INFINITY;
    private double a1WindowEndTime = Double.NEGATIVE_INFINITY;
    private double geoInfusionExpirationTime = Double.NEGATIVE_INFINITY;
    private double c4ExpirationTime = Double.NEGATIVE_INFINITY;
    private double nextC4AllowedTime = Double.NEGATIVE_INFINITY;
    private int c4TriggerCount;
    private List<PendingEvent> pendingEvents = new ArrayList<>();

    /** Constructs repository-default C6 Chiori. */
    public Chiori(Weapon weapon, ArtifactSet artifacts) {
        this(weapon, artifacts, TalentDataManager.getInstance(), 6,
                Math::random);
    }

    /** Constructs Chiori at an explicit constellation. */
    public Chiori(
            Weapon weapon,
            ArtifactSet artifacts,
            int constellation) {
        this(weapon, artifacts, TalentDataManager.getInstance(),
                constellation, Math::random);
    }

    /**
     * Constructs Chiori with injectable data and particle randomness.
     *
     * @param weapon equipped sword, or {@code null}
     * @param artifacts equipped artifact set, or {@code null}
     * @param talentData static character data
     * @param constellation constellation in {@code [0, 6]}
     * @param random particle draw source in {@code [0, 1)}
     */
    public Chiori(
            Weapon weapon,
            ArtifactSet artifacts,
            TalentDataSource talentData,
            int constellation,
            DoubleSupplier random) {
        super(talentData);
        if (constellation < 0 || constellation > 6) {
            throw new IllegalArgumentException(
                    "Chiori constellation must be between 0 and 6");
        }
        if (random == null) {
            throw new IllegalArgumentException(
                    "Chiori random source is required");
        }
        name = "Chiori";
        characterId = CharacterId.CHIORI;
        element = Element.GEO;
        this.weapon = weapon;
        this.artifacts = new ArtifactSet[] { artifacts };
        this.constellation = constellation;
        this.random = random;
        baseStats.set(StatType.BASE_HP,
                getTalentValue("Base HP", 11438.0));
        baseStats.set(StatType.BASE_ATK,
                getTalentValue("Base ATK", 323.0));
        baseStats.set(StatType.BASE_DEF,
                getTalentValue("Base DEF", 953.0));
        baseStats.add(StatType.CRIT_RATE,
                getTalentValue("Ascension CRIT Rate", 0.192));
        setSkillCD(getTalentValue("Skill Cooldown", 16.0));
        setBurstCD(getTalentValue("Burst Cooldown", 13.5));
    }

    /** Binds Chiori's listeners and delayed work to one simulator. */
    @Override
    public void initializeForSimulator(CombatSimulator simulator) {
        if (simulator == null) {
            throw new IllegalArgumentException("Chiori simulator is required");
        }
        if (!simulator.getPartyMembers().contains(this)) {
            throw new IllegalArgumentException(
                    "Chiori must belong to the simulator party");
        }
        if (initializedSimulator != null
                && initializedSimulator != simulator) {
            throw new IllegalStateException(
                    "Chiori cannot be reused across simulators");
        }
        if (initializedSimulator == simulator) {
            return;
        }
        initializedSimulator = simulator;
        simulator.addDamageListener((actor, action, damage, time) ->
                handleC4Trigger(actor, action, damage, time, simulator));
    }

    /** Captures Chiori's windows, generations, and reconstructable events. */
    @Override
    public State captureCharacterState() {
        return new ChioriState(
                this,
                normalAttackStep,
                tamotoGeneration,
                a1Generation,
                tamotoExpirationTime,
                nextParticleAllowedTime,
                a1WindowStartTime,
                a1WindowEndTime,
                geoInfusionExpirationTime,
                c4ExpirationTime,
                nextC4AllowedTime,
                c4TriggerCount,
                pendingEvents);
    }

    /** Accepts state captured by this exact Chiori instance only. */
    @Override
    public boolean acceptsCharacterState(State state) {
        return state instanceof ChioriState
                && ((ChioriState) state).owner == this;
    }

    /** Restores Chiori state and reconstructs each surviving event once. */
    @Override
    public void restoreCharacterState(
            State state,
            CombatSimulator simulator) {
        if (!acceptsCharacterState(state)) {
            throw new IllegalArgumentException("Unexpected Chiori state");
        }
        initializeForSimulator(simulator);
        ChioriState restored = (ChioriState) state;
        normalAttackStep = restored.normalAttackStep;
        tamotoGeneration = restored.tamotoGeneration;
        a1Generation = restored.a1Generation;
        tamotoExpirationTime = restored.tamotoExpirationTime;
        nextParticleAllowedTime = restored.nextParticleAllowedTime;
        a1WindowStartTime = restored.a1WindowStartTime;
        a1WindowEndTime = restored.a1WindowEndTime;
        geoInfusionExpirationTime = restored.geoInfusionExpirationTime;
        c4ExpirationTime = restored.c4ExpirationTime;
        nextC4AllowedTime = restored.nextC4AllowedTime;
        c4TriggerCount = restored.c4TriggerCount;
        pendingEvents = copyEvents(restored.pendingEvents);
        double currentTime = simulator.getCurrentTime();
        pendingEvents.removeIf(event ->
                event.time < currentTime - EPSILON);
        for (PendingEvent event : new ArrayList<>(pendingEvents)) {
            registerEvent(simulator, event);
        }
    }

    /** Returns Chiori's 50-Energy Burst cost. */
    @Override
    public double getEnergyCost() {
        return getTalentValue("Energy Cost", 50.0);
    }

    /** Chiori has no unconditional static offensive passive. */
    @Override
    public void applyPassive(StatsContainer stats) {
    }

    /** Resets only Chiori's Normal string when she leaves the field. */
    @Override
    public void onSwitchOut(CombatSimulator simulator) {
        normalAttackStep = 0;
    }

    /** Returns whether the current Tamoto generation remains active. */
    public boolean isTamotoActive(double currentTime) {
        return currentTime + EPSILON < tamotoExpirationTime;
    }

    /** Returns whether Tailoring's Geo infusion remains active. */
    public boolean isGeoInfusionActive(double currentTime) {
        return currentTime + EPSILON < geoInfusionExpirationTime;
    }

    /** Returns the unresolved Chiori-owned delayed-event count. */
    public int getPendingEventCount() {
        return pendingEvents.size();
    }

    /** Dispatches Chiori's represented typed action set. */
    @Override
    public void onAction(
            CharacterActionRequest request,
            CombatSimulator simulator) {
        validateActionRequest(request);
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
                flutteringHasode(simulator);
                break;
            case BURST:
                hiyokuTwinBlades(simulator);
                break;
            default:
                throw new IllegalArgumentException(
                        "Unsupported action for Chiori: " + request.getKey());
        }
    }

    private void normalAttack(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        activateTailoringIfPending(castTime);
        int step = normalAttackStep;
        Element attackElement = isGeoInfusionActive(castTime)
                ? Element.GEO : Element.PHYSICAL;
        for (int hit = 0; hit < NORMAL_KEYS[step].length; hit++) {
            PendingEvent event = new PendingEvent(
                    castTime + NORMAL_HIT_FRAMES[step][hit] * FRAME,
                    EventType.NORMAL_HIT,
                    step,
                    hit,
                    attackElement,
                    0L);
            scheduleEvent(simulator, event);
        }
        normalAttackStep = (normalAttackStep + 1)
                % NORMAL_DURATIONS.length;
        simulator.advanceTime(NORMAL_DURATIONS[step] * FRAME);
    }

    private void chargedAttack(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        for (int hit = 0; hit < 2; hit++) {
            scheduleEvent(simulator, new PendingEvent(
                    castTime + (25 + hit) * FRAME,
                    EventType.CHARGED_HIT,
                    0,
                    hit,
                    Element.PHYSICAL,
                    0L));
        }
        simulator.advanceTime(44.0 * FRAME);
    }

    private void highPlunge(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        Element attackElement = isGeoInfusionActive(castTime)
                ? Element.GEO : Element.PHYSICAL;
        scheduleEvent(simulator, new PendingEvent(
                castTime + 41.0 * FRAME,
                EventType.PLUNGE_HIT,
                0,
                0,
                attackElement,
                0L));
        simulator.advanceTime(69.0 * FRAME);
    }

    private void flutteringHasode(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        markSkillUsed(castTime, simulator.getApplicableBuffs(this));
        tamotoGeneration++;
        long generation = tamotoGeneration;
        tamotoExpirationTime = castTime + TAMOTO_SPAWN
                + getTalentValue("Tamoto Duration", 17.5);
        scheduleEvent(simulator, new PendingEvent(
                castTime + 21.0 * FRAME,
                EventType.SKILL_HIT,
                0,
                0,
                Element.GEO,
                generation));
        scheduleTamotoSequence(
                simulator,
                castTime + TAMOTO_SPAWN + TAMOTO_FIRST_DELAY,
                generation,
                0);
        if (constellation >= 1 && hasOtherGeoPartyMember(simulator)) {
            scheduleTamotoSequence(
                    simulator,
                    castTime + TAMOTO_SPAWN + TAMOTO_SECOND_DELAY,
                    generation,
                    1);
        }
        if (a1Generation != Long.MAX_VALUE) {
            a1Generation++;
        }
        long windowGeneration = a1Generation;
        a1WindowStartTime = castTime + 26.0 * FRAME;
        a1WindowEndTime = castTime + 104.0 * FRAME;
        scheduleEvent(simulator, new PendingEvent(
                a1WindowEndTime,
                EventType.A1_DEFAULT,
                0,
                0,
                Element.GEO,
                windowGeneration));
        simulator.advanceTime(51.0 * FRAME);
    }

    private void scheduleTamotoSequence(
            CombatSimulator simulator,
            double firstHitTime,
            long generation,
            int dollIndex) {
        double interval = getTalentValue("Tamoto Interval", 3.6);
        for (double time = firstHitTime;
                time < tamotoExpirationTime - EPSILON;
                time += interval) {
            scheduleEvent(simulator, new PendingEvent(
                    time,
                    EventType.TAMOTO_HIT,
                    dollIndex,
                    0,
                    Element.GEO,
                    generation));
        }
    }

    private void hiyokuTwinBlades(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        markBurstUsed(castTime, simulator.getApplicableBuffs(this));
        scheduleEvent(simulator, new PendingEvent(
                castTime + 92.0 * FRAME,
                EventType.BURST_HIT,
                0,
                0,
                Element.GEO,
                0L));
        if (constellation >= 2) {
            double firstKinu = castTime + 91.0 * FRAME + 3.0
                    + KINU_DELAY;
            for (int index = 0; index < 3; index++) {
                scheduleEvent(simulator, new PendingEvent(
                        firstKinu + index * 3.0,
                        EventType.KINU_HIT,
                        2,
                        index,
                        Element.GEO,
                        0L));
            }
        }
        simulator.advanceTime(101.0 * FRAME);
    }

    private void activateTailoringIfPending(double currentTime) {
        if (currentTime + EPSILON < a1WindowStartTime
                || currentTime >= a1WindowEndTime - EPSILON) {
            return;
        }
        activateTailoring(currentTime, a1Generation);
    }

    private void activateTailoring(
            double currentTime,
            long generation) {
        if (generation != a1Generation) {
            return;
        }
        a1WindowStartTime = Double.NEGATIVE_INFINITY;
        a1WindowEndTime = Double.NEGATIVE_INFINITY;
        geoInfusionExpirationTime = currentTime
                + getTalentValue("A1 Infusion Duration", 5.0);
        if (constellation >= 4) {
            c4ExpirationTime = currentTime
                    + getTalentValue("C4 Duration", 8.0);
            nextC4AllowedTime = currentTime;
            c4TriggerCount = 0;
        }
        if (constellation >= 6) {
            reduceSkillCooldown(
                    currentTime,
                    getTalentValue("C6 Cooldown Reduction", 12.0));
        }
    }

    private void handleC4Trigger(
            Character actor,
            AttackAction action,
            double damage,
            double time,
            CombatSimulator simulator) {
        if (constellation < 4
                || actor != simulator.getActiveCharacter()
                || damage <= 0.0
                || time + EPSILON < nextC4AllowedTime
                || time >= c4ExpirationTime - EPSILON
                || c4TriggerCount >= 3) {
            return;
        }
        ActionType type = action.getActionType();
        if (type != ActionType.NORMAL
                && type != ActionType.CHARGE
                && type != ActionType.PLUNGE) {
            return;
        }
        nextC4AllowedTime = time + 1.0;
        c4TriggerCount++;
        scheduleEvent(simulator, new PendingEvent(
                time + KINU_DELAY,
                EventType.KINU_HIT,
                4,
                c4TriggerCount,
                Element.GEO,
                0L));
    }

    private void resolveEvent(
            CombatSimulator simulator,
            PendingEvent event) {
        switch (event.type) {
            case NORMAL_HIT:
                resolveNormalHit(simulator, event);
                break;
            case CHARGED_HIT:
                resolveChargedHit(simulator, event);
                break;
            case PLUNGE_HIT:
                resolvePlungeHit(simulator, event);
                break;
            case SKILL_HIT:
                if (event.generation == tamotoGeneration) {
                    performDualScaling(
                            simulator,
                            "Fluttering Hasode (Upward Sweep)",
                            skillValue("Upward Sweep ATK", 2.537760),
                            skillValue("Upward Sweep DEF", 3.172200),
                            StatType.SKILL_DMG_BONUS,
                            ActionType.SKILL,
                            false);
                }
                break;
            case TAMOTO_HIT:
                if (event.generation == tamotoGeneration
                        && simulator.getCurrentTime()
                                < tamotoExpirationTime - EPSILON) {
                    performDualScaling(
                            simulator,
                            event.index == 0
                                    ? "Fluttering Hasode (Tamoto)"
                                    : "Fluttering Hasode (Tamoto C1)",
                            skillValue("Tamoto ATK", 1.395360),
                            skillValue("Tamoto DEF", 1.744200),
                            StatType.SKILL_DMG_BONUS,
                            ActionType.SKILL,
                            true);
                }
                break;
            case BURST_HIT:
                performDualScaling(
                        simulator,
                        "Hiyoku: Twin Blades",
                        burstValue("Hiyoku ATK", 4.357440),
                        burstValue("Hiyoku DEF", 5.446800),
                        StatType.BURST_DMG_BONUS,
                        ActionType.BURST,
                        false);
                break;
            case KINU_HIT:
                double ratio = getTalentValue("C2 Kinu Ratio", 1.7);
                performDualScaling(
                        simulator,
                        "Fluttering Hasode (Kinu C" + event.index + ")",
                        skillValue("Tamoto ATK", 1.395360) * ratio,
                        skillValue("Tamoto DEF", 1.744200) * ratio,
                        StatType.SKILL_DMG_BONUS,
                        ActionType.SKILL,
                        false);
                break;
            case A1_DEFAULT:
                if (event.generation == a1Generation
                        && simulator.getCurrentTime()
                                >= a1WindowEndTime - EPSILON) {
                    activateTailoring(
                            simulator.getCurrentTime(), event.generation);
                }
                break;
            default:
                throw new IllegalStateException(
                        "Unknown Chiori event type");
        }
    }

    private void resolveNormalHit(
            CombatSimulator simulator,
            PendingEvent event) {
        String key = NORMAL_KEYS[event.index][event.subIndex];
        double fixedBaseDamage = constellation >= 6
                ? getFinalDef(simulator)
                        * getTalentValue("C6 DEF Flat Ratio", 2.35)
                : 0.0;
        AttackAction action = new ChioriAttackAction(
                "Weaving Blade " + key,
                getTalentValue(key,
                        NORMAL_T9[event.index][event.subIndex]),
                event.element,
                StatType.NORMAL_ATTACK_DMG_BONUS,
                ActionType.NORMAL,
                fixedBaseDamage);
        action.setICD(
                ICDType.Standard,
                ICDTag.NormalAttack,
                event.element == Element.GEO ? 1.0 : 0.0);
        if (event.index < 2) {
            action.setHitlagProfile(NORMAL_SHORT_HITLAG);
        } else if (event.index == 2 && event.subIndex == 0) {
            action.setHitlagProfile(NORMAL_N3_FIRST_HITLAG);
        } else if (event.index == 3) {
            action.setHitlagProfile(NORMAL_N4_HITLAG);
        }
        simulator.performActionWithoutTimeAdvance(characterId, action);
    }

    private void resolveChargedHit(
            CombatSimulator simulator,
            PendingEvent event) {
        AttackAction action = new AttackAction(
                "Weaving Blade Charged " + (event.subIndex + 1),
                getTalentValue(
                        "Charged Hit " + (event.subIndex + 1),
                        0.997770),
                Element.PHYSICAL,
                StatType.BASE_ATK,
                StatType.CHARGED_ATTACK_DMG_BONUS,
                0.0,
                ActionType.CHARGE);
        action.setICD(
                ICDType.Standard, ICDTag.NormalAttack, 0.0);
        if (event.subIndex == 1) {
            action.setHitlagProfile(CHARGED_SECOND_HITLAG);
        }
        simulator.performActionWithoutTimeAdvance(characterId, action);
    }

    private void resolvePlungeHit(
            CombatSimulator simulator,
            PendingEvent event) {
        AttackAction action = new AttackAction(
                "Weaving Blade High Plunge",
                getTalentValue("High Plunge", 2.933586),
                event.element,
                StatType.BASE_ATK,
                StatType.PLUNGING_ATTACK_DMG_BONUS,
                0.0,
                ActionType.PLUNGE);
        action.setICD(
                ICDType.None,
                ICDTag.PlungeAttack,
                event.element == Element.GEO ? 1.0 : 0.0);
        action.setShatterTrigger(true);
        simulator.performActionWithoutTimeAdvance(characterId, action);
    }

    private void performDualScaling(
            CombatSimulator simulator,
            String actionName,
            double atkRatio,
            double defRatio,
            StatType bonusStat,
            ActionType actionType,
            boolean particles) {
        AttackAction action = new ChioriAttackAction(
                actionName,
                atkRatio,
                Element.GEO,
                bonusStat,
                actionType,
                getFinalDef(simulator) * defRatio);
        action.setICD(
                ICDType.Standard, ICDTag.ElementalSkill, 1.0);
        action.setShatterTrigger(true);
        simulator.performActionWithoutTimeAdvance(characterId, action);
        if (particles) {
            generateParticles(simulator);
        }
    }

    private void generateParticles(CombatSimulator simulator) {
        double currentTime = simulator.getCurrentTime();
        if (currentTime + EPSILON < nextParticleAllowedTime) {
            return;
        }
        double draw = random.getAsDouble();
        if (!Double.isFinite(draw) || draw < 0.0 || draw >= 1.0) {
            throw new IllegalStateException(
                    "Chiori random draw must be in [0, 1)");
        }
        nextParticleAllowedTime = currentTime
                + getTalentValue("Particle Gate", 3.0);
        simulator.getEnergyDistributor().distributeParticles(
                Element.GEO,
                draw < 0.2 ? 2.0 : 1.0,
                ParticleType.PARTICLE);
    }

    private double skillValue(String key, double fallback) {
        if (constellation >= 3) {
            return getTalentValue(key + " C3", c3Fallback(key));
        }
        return getTalentValue(key, fallback);
    }

    private double c3Fallback(String key) {
        switch (key) {
            case "Tamoto ATK":
                return 1.641600;
            case "Tamoto DEF":
                return 2.052000;
            case "Upward Sweep ATK":
                return 2.985600;
            case "Upward Sweep DEF":
                return 3.732000;
            default:
                throw new IllegalArgumentException(
                        "Unknown Chiori Skill multiplier " + key);
        }
    }

    private double burstValue(String key, double fallback) {
        if (constellation >= 5) {
            return getTalentValue(
                    key + " C5",
                    "Hiyoku ATK".equals(key) ? 5.126400 : 6.408000);
        }
        return getTalentValue(key, fallback);
    }

    private double getFinalDef(CombatSimulator simulator) {
        double currentTime = simulator.getCurrentTime();
        StatsContainer stats = getEffectiveStats(currentTime);
        List<Buff> buffs = simulator.getApplicableBuffs(this);
        for (Buff buff : buffs) {
            if (!buff.isExpired(currentTime)) {
                buff.apply(stats, currentTime);
            }
        }
        return stats.getTotalDef();
    }

    private boolean hasOtherGeoPartyMember(CombatSimulator simulator) {
        for (Character member : simulator.getPartyMembers()) {
            if (member != this && member.getElement() == Element.GEO) {
                return true;
            }
        }
        return false;
    }

    private void scheduleEvent(
            CombatSimulator simulator,
            PendingEvent event) {
        pendingEvents.add(event);
        registerEvent(simulator, event);
    }

    private void registerEvent(
            CombatSimulator simulator,
            PendingEvent event) {
        simulator.registerEvent(new SimpleTimerEvent(event.time, 1.0) {
            @Override
            public void onTick(CombatSimulator activeSimulator) {
                finish();
                if (!pendingEvents.remove(event)) {
                    return;
                }
                resolveEvent(activeSimulator, event);
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

    /** Preserves Chiori's DEF contribution through reaction normalization. */
    private static final class ChioriAttackAction extends AttackAction {
        private final double fixedBaseDamage;

        private ChioriAttackAction(
                String displayName,
                double multiplier,
                Element element,
                StatType bonusStat,
                ActionType actionType,
                double fixedBaseDamage) {
            super(
                    displayName,
                    multiplier,
                    element,
                    StatType.BASE_ATK,
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

    /** Immutable delayed Chiori action description. */
    private static final class PendingEvent {
        private final double time;
        private final EventType type;
        private final int index;
        private final int subIndex;
        private final Element element;
        private final long generation;

        private PendingEvent(
                double time,
                EventType type,
                int index,
                int subIndex,
                Element element,
                long generation) {
            this.time = time;
            this.type = type;
            this.index = index;
            this.subIndex = subIndex;
            this.element = element;
            this.generation = generation;
        }

        private PendingEvent copy() {
            return new PendingEvent(
                    time, type, index, subIndex, element, generation);
        }
    }

    private enum EventType {
        NORMAL_HIT,
        CHARGED_HIT,
        PLUNGE_HIT,
        SKILL_HIT,
        TAMOTO_HIT,
        BURST_HIT,
        KINU_HIT,
        A1_DEFAULT
    }

    /** Immutable owner-bound rollback payload. */
    private static final class ChioriState implements State {
        private final Chiori owner;
        private final int normalAttackStep;
        private final long tamotoGeneration;
        private final long a1Generation;
        private final double tamotoExpirationTime;
        private final double nextParticleAllowedTime;
        private final double a1WindowStartTime;
        private final double a1WindowEndTime;
        private final double geoInfusionExpirationTime;
        private final double c4ExpirationTime;
        private final double nextC4AllowedTime;
        private final int c4TriggerCount;
        private final List<PendingEvent> pendingEvents;

        private ChioriState(
                Chiori owner,
                int normalAttackStep,
                long tamotoGeneration,
                long a1Generation,
                double tamotoExpirationTime,
                double nextParticleAllowedTime,
                double a1WindowStartTime,
                double a1WindowEndTime,
                double geoInfusionExpirationTime,
                double c4ExpirationTime,
                double nextC4AllowedTime,
                int c4TriggerCount,
                List<PendingEvent> pendingEvents) {
            this.owner = owner;
            this.normalAttackStep = normalAttackStep;
            this.tamotoGeneration = tamotoGeneration;
            this.a1Generation = a1Generation;
            this.tamotoExpirationTime = tamotoExpirationTime;
            this.nextParticleAllowedTime = nextParticleAllowedTime;
            this.a1WindowStartTime = a1WindowStartTime;
            this.a1WindowEndTime = a1WindowEndTime;
            this.geoInfusionExpirationTime = geoInfusionExpirationTime;
            this.c4ExpirationTime = c4ExpirationTime;
            this.nextC4AllowedTime = nextC4AllowedTime;
            this.c4TriggerCount = c4TriggerCount;
            this.pendingEvents = copyEvents(pendingEvents);
        }
    }
}

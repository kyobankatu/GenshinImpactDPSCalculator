package model.character;

import java.util.ArrayList;
import java.util.List;

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
import simulation.event.SimpleTimerEvent;

/**
 * Lyney's fixed-target Prop Arrow and Grin-Malkin offensive slice through C6.
 *
 * <p>Bow normals, Prop Arrow, Hat creation/replacement/expiry, Skill
 * detonation, Miracle Parade, Pyro-aura A4, and representable constellations
 * follow pinned gcsim {@code ef41805d}. Hat strikes retain their creation-time
 * snapshots and all future work is reconstructable after rollback.</p>
 *
 * <p>Player HP is unavailable, so ordinary Prop Arrows do not create the
 * HP-consumption stack or A1 bonus. C1 and Burst stacks remain active. Aimed
 * levels, weak points, Arkhe, gadget durability, movement, geometry,
 * multi-target selection, stamina, hitlag, and Plunge are excluded.</p>
 */
public final class Lyney extends Character implements
        SimulatorInitializedCharacterEffect,
        SnapshotAwareCharacterEffect,
        SwitchAwareCharacter {
    private static final double FRAME = 1.0 / 60.0;
    private static final double EPSILON = 1e-9;
    private static final int[] NORMAL_DURATIONS = { 32, 34, 86, 66 };
    private static final int[][] NORMAL_HIT_FRAMES = {
        { 27 }, { 22 }, { 30, 39 }, { 39 }
    };
    private static final String[][] NORMAL_KEYS = {
        { "N1" }, { "N2" },
        { "N3 Hit 1", "N3 Hit 2" }, { "N4" }
    };
    private static final double[][] NORMAL_T9 = {
        { 0.712580 }, { 0.698360 },
        { 0.500860, 0.500860 }, { 1.045960 }
    };
    private static final double[] NORMAL_C3 = {
        0.874940, 0.857480, 0.614980, 0.614980, 1.284280
    };

    private CombatSimulator initializedSimulator;
    private int normalAttackStep;
    private int propSurplusStacks;
    private long nextHatId;
    private long burstGeneration;
    private long c2Generation;
    private double nextC1AllowedTime = Double.NEGATIVE_INFINITY;
    private double nextSpiritbreathAllowedTime = Double.NEGATIVE_INFINITY;
    private double burstExpirationTime = Double.NEGATIVE_INFINITY;
    private int c2Stacks;
    private List<HatState> activeHats = new ArrayList<>();
    private List<PendingEvent> pendingEvents = new ArrayList<>();

    /** Constructs repository-default C6 Lyney. */
    public Lyney(Weapon weapon, ArtifactSet artifacts) {
        this(weapon, artifacts, TalentDataManager.getInstance(), 6);
    }

    /** Constructs Lyney at an explicit constellation. */
    public Lyney(
            Weapon weapon,
            ArtifactSet artifacts,
            int constellation) {
        this(weapon, artifacts, TalentDataManager.getInstance(), constellation);
    }

    /**
     * Constructs Lyney with injectable character data.
     *
     * @param weapon equipped bow, or {@code null}
     * @param artifacts equipped artifact set, or {@code null}
     * @param talentData static character data
     * @param constellation constellation in {@code [0, 6]}
     */
    public Lyney(
            Weapon weapon,
            ArtifactSet artifacts,
            TalentDataSource talentData,
            int constellation) {
        super(talentData);
        if (constellation < 0 || constellation > 6) {
            throw new IllegalArgumentException(
                    "Lyney constellation must be between 0 and 6");
        }
        name = "Lyney";
        characterId = CharacterId.LYNEY;
        element = Element.PYRO;
        this.weapon = weapon;
        this.artifacts = new ArtifactSet[] { artifacts };
        this.constellation = constellation;
        baseStats.set(StatType.BASE_HP,
                getTalentValue("Base HP", 11021.0));
        baseStats.set(StatType.BASE_ATK,
                getTalentValue("Base ATK", 318.0));
        baseStats.set(StatType.BASE_DEF,
                getTalentValue("Base DEF", 538.0));
        baseStats.add(StatType.CRIT_RATE,
                getTalentValue("Ascension CRIT Rate", 0.192));
        setSkillCD(getTalentValue("Skill Cooldown", 15.0));
        setBurstCD(getTalentValue("Burst Cooldown", 15.0));
    }

    /** Binds Lyney's on-field and delayed state to one simulator. */
    @Override
    public void initializeForSimulator(CombatSimulator simulator) {
        if (simulator == null) {
            throw new IllegalArgumentException("Lyney simulator is required");
        }
        if (!simulator.getPartyMembers().contains(this)) {
            throw new IllegalArgumentException(
                    "Lyney must belong to the simulator party");
        }
        if (initializedSimulator != null
                && initializedSimulator != simulator) {
            throw new IllegalStateException(
                    "Lyney cannot be reused across simulators");
        }
        if (initializedSimulator == simulator) {
            return;
        }
        initializedSimulator = simulator;
        if (simulator.getActiveCharacter() == this) {
            startC2(simulator);
        }
    }

    /** Captures all Lyney-owned windows, Hats, and delayed work. */
    @Override
    public State captureCharacterState() {
        return new LyneyState(
                this,
                normalAttackStep,
                propSurplusStacks,
                nextHatId,
                burstGeneration,
                c2Generation,
                nextC1AllowedTime,
                nextSpiritbreathAllowedTime,
                burstExpirationTime,
                c2Stacks,
                activeHats,
                pendingEvents);
    }

    /** Accepts only state captured from this exact Lyney instance. */
    @Override
    public boolean acceptsCharacterState(State state) {
        return state instanceof LyneyState
                && ((LyneyState) state).owner == this;
    }

    /** Restores Lyney and reconstructs each surviving delayed event once. */
    @Override
    public void restoreCharacterState(
            State state,
            CombatSimulator simulator) {
        if (!acceptsCharacterState(state)) {
            throw new IllegalArgumentException("Unexpected Lyney state");
        }
        initializeForSimulator(simulator);
        LyneyState restored = (LyneyState) state;
        normalAttackStep = restored.normalAttackStep;
        propSurplusStacks = restored.propSurplusStacks;
        nextHatId = restored.nextHatId;
        burstGeneration = restored.burstGeneration;
        c2Generation = restored.c2Generation;
        nextC1AllowedTime = restored.nextC1AllowedTime;
        nextSpiritbreathAllowedTime = restored.nextSpiritbreathAllowedTime;
        burstExpirationTime = restored.burstExpirationTime;
        c2Stacks = restored.c2Stacks;
        activeHats = copyHats(restored.activeHats);
        pendingEvents = copyEvents(restored.pendingEvents);
        double currentTime = simulator.getCurrentTime();
        pendingEvents.removeIf(event ->
                event.time < currentTime - EPSILON);
        for (PendingEvent event : new ArrayList<>(pendingEvents)) {
            registerEvent(simulator, event);
        }
    }

    /** Returns Lyney's 60-Energy Burst cost. */
    @Override
    public double getEnergyCost() {
        return getTalentValue("Energy Cost", 60.0);
    }

    /** Lyney has no unconditional static passive stat. */
    @Override
    public void applyPassive(StatsContainer stats) {
    }

    /** Clears C2 and ends Miracle Parade when Lyney leaves the field. */
    @Override
    public void onSwitchOut(CombatSimulator simulator) {
        normalAttackStep = 0;
        c2Generation++;
        c2Stacks = 0;
        if (isBurstActive(simulator.getCurrentTime())) {
            finishBurst(simulator, burstGeneration);
        }
    }

    /** Starts Lyney's on-field C2 cadence. */
    @Override
    public void onSwitchIn(CombatSimulator simulator) {
        startC2(simulator);
    }

    /** Returns the current Prop Surplus count. */
    public int getPropSurplusStacks() {
        return propSurplusStacks;
    }

    /** Returns the number of active Grin-Malkin Hats. */
    public int getActiveHatCount() {
        return activeHats.size();
    }

    /** Returns current C2 Crisp Focus stacks. */
    public int getCrispFocusStacks() {
        return c2Stacks;
    }

    /** Returns whether Miracle Parade remains active. */
    public boolean isBurstActive(double currentTime) {
        return currentTime + EPSILON < burstExpirationTime;
    }

    /** Returns unresolved Lyney-owned event count. */
    public int getPendingEventCount() {
        return pendingEvents.size();
    }

    /** Dispatches Lyney's represented typed action set. */
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
                propArrow(simulator);
                break;
            case SKILL:
                if (isBurstActive(simulator.getCurrentTime())) {
                    finishBurst(simulator, burstGeneration);
                    simulator.advanceTime(7.0 * FRAME);
                } else {
                    bewilderingLights(simulator);
                }
                break;
            case BURST:
                wondrousTrick(simulator);
                break;
            default:
                throw new IllegalArgumentException(
                        "Unsupported action for Lyney: " + request.getKey());
        }
    }

    private void normalAttack(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        int step = normalAttackStep;
        for (int hit = 0; hit < NORMAL_KEYS[step].length; hit++) {
            scheduleEvent(simulator, new PendingEvent(
                    castTime + NORMAL_HIT_FRAMES[step][hit] * FRAME,
                    EventType.NORMAL_HIT,
                    step,
                    hit,
                    0L,
                    null));
        }
        normalAttackStep = (normalAttackStep + 1)
                % NORMAL_DURATIONS.length;
        simulator.advanceTime(NORMAL_DURATIONS[step] * FRAME);
    }

    private void propArrow(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        scheduleEvent(simulator, new PendingEvent(
                castTime + 113.0 * FRAME,
                EventType.PROP_ARROW,
                0,
                0,
                0L,
                null));
        simulator.advanceTime(111.0 * FRAME);
    }

    private void bewilderingLights(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        markSkillUsed(castTime, simulator.getApplicableBuffs(this));
        int consumedStacks = propSurplusStacks;
        propSurplusStacks = 0;
        scheduleEvent(simulator, new PendingEvent(
                castTime + 18.0 * FRAME,
                EventType.SKILL_HIT,
                consumedStacks,
                0,
                0L,
                null));
        simulator.advanceTime(43.0 * FRAME);
    }

    private void wondrousTrick(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        markBurstUsed(castTime, simulator.getApplicableBuffs(this));
        burstGeneration++;
        long generation = burstGeneration;
        scheduleEvent(simulator, new PendingEvent(
                castTime + 100.0 * FRAME,
                EventType.BURST_START,
                0,
                0,
                generation,
                null));
        simulator.advanceTime(101.0 * FRAME);
    }

    private void resolveEvent(
            CombatSimulator simulator,
            PendingEvent event) {
        switch (event.type) {
            case NORMAL_HIT:
                resolveNormal(simulator, event.index, event.subIndex);
                break;
            case PROP_ARROW:
                resolvePropArrow(simulator);
                break;
            case SPIRITBREATH:
                resolveSimpleAttack(
                        simulator,
                        "Spiritbreath Thorn",
                        normalValue("Spiritbreath Thorn", 0.468384),
                        StatType.CHARGED_ATTACK_DMG_BONUS,
                        ActionType.CHARGE,
                        ICDTag.ChargedAttack,
                        0.0,
                        null);
                break;
            case HAT_EXPIRE:
                HatState expiring = findHat(event.generation);
                if (expiring != null) {
                    activeHats.remove(expiring);
                    schedulePyrotechnic(simulator, expiring, 36.0 * FRAME);
                }
                break;
            case PYROTECHNIC:
                resolveSimpleAttack(
                        simulator,
                        event.index == 1
                                ? "Pyrotechnic Strike (Skill)"
                                : "Pyrotechnic Strike",
                        normalValue("Pyrotechnic Strike", 3.604000),
                        StatType.CHARGED_ATTACK_DMG_BONUS,
                        ActionType.CHARGE,
                        ICDTag.ChargedAttack,
                        1.0,
                        event.snapshot);
                break;
            case SKILL_HIT:
                resolveSkill(simulator, event.index);
                break;
            case BURST_START:
                if (event.generation == burstGeneration) {
                    burstExpirationTime = simulator.getCurrentTime()
                            + 182.0 * FRAME;
                    scheduleEvent(simulator, new PendingEvent(
                            simulator.getCurrentTime() + 12.0 * FRAME,
                            EventType.BURST_COLLISION,
                            0,
                            0,
                            event.generation,
                            null));
                    scheduleEvent(simulator, new PendingEvent(
                            burstExpirationTime,
                            EventType.BURST_END,
                            0,
                            0,
                            event.generation,
                            null));
                }
                break;
            case BURST_COLLISION:
                if (event.generation == burstGeneration
                        && isBurstActive(simulator.getCurrentTime())) {
                    resolveSimpleAttack(
                            simulator,
                            "Wondrous Trick: Miracle Parade",
                            burstValue("Miracle Parade", 2.618000),
                            StatType.BURST_DMG_BONUS,
                            ActionType.BURST,
                            ICDTag.ElementalBurst,
                            1.0,
                            null);
                }
                break;
            case BURST_END:
                finishBurst(simulator, event.generation);
                break;
            case FIREWORK:
                resolveSimpleAttack(
                        simulator,
                        "Wondrous Trick: Explosive Firework",
                        burstValue("Explosive Firework", 7.038000),
                        StatType.BURST_DMG_BONUS,
                        ActionType.BURST,
                        ICDTag.ElementalBurst,
                        1.0,
                        null);
                break;
            case C2_TICK:
                if (event.generation == c2Generation
                        && simulator.getActiveCharacter() == this
                        && c2Stacks < 3) {
                    c2Stacks++;
                    scheduleEvent(simulator, new PendingEvent(
                            simulator.getCurrentTime()
                                    + getTalentValue("C2 Interval", 2.0),
                            EventType.C2_TICK,
                            0,
                            0,
                            event.generation,
                            null));
                }
                break;
            default:
                throw new IllegalStateException("Unknown Lyney event type");
        }
    }

    private void resolveNormal(
            CombatSimulator simulator,
            int step,
            int hit) {
        String key = NORMAL_KEYS[step][hit];
        double fallback = NORMAL_T9[step][hit];
        if (constellation >= 3) {
            int flatIndex = step == 0 ? 0
                    : step == 1 ? 1
                    : step == 2 ? 2 + hit : 4;
            fallback = NORMAL_C3[flatIndex];
            key += " C3";
        }
        resolveSimpleAttack(
                simulator,
                "Card Force Translocation " + key,
                getTalentValue(key, fallback),
                StatType.NORMAL_ATTACK_DMG_BONUS,
                ActionType.NORMAL,
                ICDTag.NormalAttack,
                0.0,
                null);
    }

    private void resolvePropArrow(CombatSimulator simulator) {
        resolveSimpleAttack(
                simulator,
                "Card Force Translocation Prop Arrow",
                normalValue("Prop Arrow", 2.937600),
                StatType.CHARGED_ATTACK_DMG_BONUS,
                ActionType.CHARGE,
                ICDTag.ChargedAttack,
                1.0,
                null);
        applyC4(simulator);
        if (constellation >= 6) {
            resolveSimpleAttack(
                    simulator,
                    "Pyrotechnic Strike: Reprised (C6)",
                    normalValue("Pyrotechnic Strike", 3.604000) * 0.8,
                    StatType.CHARGED_ATTACK_DMG_BONUS,
                    ActionType.CHARGE,
                    ICDTag.ChargedAttack,
                    1.0,
                    null);
        }
        boolean c1Ready = constellation >= 1
                && simulator.getCurrentTime() + EPSILON
                        >= nextC1AllowedTime;
        if (c1Ready) {
            nextC1AllowedTime = simulator.getCurrentTime()
                    + getTalentValue("C1 Gate", 15.0);
            gainPropSurplus(1);
        }
        int hatsToCreate = c1Ready ? 2 : 1;
        for (int index = 0; index < hatsToCreate; index++) {
            createHat(
                    simulator,
                    getTalentValue("Hat Duration Frames", 238.0) * FRAME);
        }
        if (simulator.getCurrentTime() + EPSILON
                >= nextSpiritbreathAllowedTime) {
            nextSpiritbreathAllowedTime = simulator.getCurrentTime() + 6.0;
            scheduleEvent(simulator, new PendingEvent(
                    simulator.getCurrentTime() + 42.0 * FRAME,
                    EventType.SPIRITBREATH,
                    0,
                    0,
                    0L,
                    null));
        }
    }

    private void resolveSkill(
            CombatSimulator simulator,
            int consumedStacks) {
        double multiplier = getTalentValue(
                "Bewildering Lights", 2.842400)
                + consumedStacks
                        * getTalentValue("Stack Bonus", 0.904400);
        resolveSimpleAttack(
                simulator,
                "Bewildering Lights",
                multiplier,
                StatType.SKILL_DMG_BONUS,
                ActionType.SKILL,
                ICDTag.ElementalSkill,
                1.0,
                null);
        simulator.getEnergyDistributor().distributeParticles(
                Element.PYRO, 5.0, ParticleType.PARTICLE);
        for (HatState hat : new ArrayList<>(activeHats)) {
            activeHats.remove(hat);
            schedulePyrotechnic(simulator, hat, 13.0 * FRAME, 1);
        }
    }

    private void finishBurst(
            CombatSimulator simulator,
            long generation) {
        if (generation != burstGeneration
                || burstExpirationTime == Double.NEGATIVE_INFINITY) {
            return;
        }
        burstExpirationTime = Double.NEGATIVE_INFINITY;
        burstGeneration++;
        scheduleEvent(simulator, new PendingEvent(
                simulator.getCurrentTime() + 6.0 * FRAME,
                EventType.FIREWORK,
                0,
                0,
                0L,
                null));
        createHat(
                simulator,
                getTalentValue("Burst Hat Duration Frames", 245.0) * FRAME);
        gainPropSurplus(1);
    }

    private void createHat(
            CombatSimulator simulator,
            double duration) {
        int cap = constellation >= 1 ? 2 : 1;
        if (activeHats.size() >= cap) {
            HatState replaced = activeHats.remove(0);
            schedulePyrotechnic(simulator, replaced, 36.0 * FRAME);
        }
        long id = ++nextHatId;
        double expirationTime = simulator.getCurrentTime() + duration;
        HatState hat = new HatState(
                id,
                expirationTime,
                captureStats(simulator));
        activeHats.add(hat);
        scheduleEvent(simulator, new PendingEvent(
                expirationTime,
                EventType.HAT_EXPIRE,
                0,
                0,
                id,
                null));
    }

    private void schedulePyrotechnic(
            CombatSimulator simulator,
            HatState hat,
            double delay) {
        schedulePyrotechnic(simulator, hat, delay, 0);
    }

    private void schedulePyrotechnic(
            CombatSimulator simulator,
            HatState hat,
            double delay,
            int enhanced) {
        scheduleEvent(simulator, new PendingEvent(
                simulator.getCurrentTime() + delay,
                EventType.PYROTECHNIC,
                enhanced,
                0,
                hat.id,
                hat.snapshot));
    }

    private HatState findHat(long id) {
        for (HatState hat : activeHats) {
            if (hat.id == id) {
                return hat;
            }
        }
        return null;
    }

    private void gainPropSurplus(int amount) {
        int cap = (int) getTalentValue("Prop Surplus Cap", 5.0);
        propSurplusStacks = Math.min(cap, propSurplusStacks + amount);
    }

    private void startC2(CombatSimulator simulator) {
        if (constellation < 2) {
            return;
        }
        c2Generation++;
        long generation = c2Generation;
        scheduleEvent(simulator, new PendingEvent(
                simulator.getCurrentTime()
                        + getTalentValue("C2 Interval", 2.0),
                EventType.C2_TICK,
                0,
                0,
                generation,
                null));
    }

    private void applyC4(CombatSimulator simulator) {
        if (constellation < 4) {
            return;
        }
        double currentTime = simulator.getCurrentTime();
        getActiveBuffs().removeIf(buff ->
                "Lyney C4 Pyro RES Shred".equals(buff.getName()));
        addBuff(new SimpleBuff(
                "Lyney C4 Pyro RES Shred",
                getTalentValue("C4 Duration", 6.0),
                currentTime,
                stats -> stats.add(
                        StatType.PYRO_RES_SHRED,
                        getTalentValue("C4 Pyro RES Shred", 0.2))));
    }

    private void resolveSimpleAttack(
            CombatSimulator simulator,
            String actionName,
            double multiplier,
            StatType bonusStat,
            ActionType actionType,
            ICDTag icdTag,
            double gauge,
            StatsContainer snapshot) {
        AttackAction action = new AttackAction(
                actionName,
                multiplier,
                actionType == ActionType.NORMAL
                        ? Element.PHYSICAL : Element.PYRO,
                StatType.BASE_ATK,
                bonusStat,
                0.0,
                actionType);
        action.setICD(
                gauge == 0.0 ? ICDType.None : ICDType.Standard,
                icdTag,
                gauge);
        if (snapshot != null) {
            action.setStatSnapshot(snapshot);
        }
        action.addBonusStat(StatType.CRIT_DMG, c2Stacks * 0.2);
        applyA4(action, simulator);
        simulator.performActionWithoutTimeAdvance(characterId, action);
    }

    private void applyA4(
            AttackAction action,
            CombatSimulator simulator) {
        if (simulator.getEnemy().getAuraUnits(
                Element.PYRO, simulator.getCurrentTime()) <= 0.0) {
            return;
        }
        int otherPyro = 0;
        for (Character member : simulator.getPartyMembers()) {
            if (member != this && member.getElement() == Element.PYRO) {
                otherPyro++;
            }
        }
        action.addBonusStat(
                StatType.DMG_BONUS_ALL,
                Math.min(1.0, 0.6 + otherPyro * 0.2));
    }

    private double normalValue(String key, double fallback) {
        if (constellation >= 3) {
            return getTalentValue(key + " C3", c3Value(key));
        }
        return getTalentValue(key, fallback);
    }

    private double c3Value(String key) {
        switch (key) {
            case "Prop Arrow":
                return 3.456000;
            case "Pyrotechnic Strike":
                return 4.240000;
            case "Spiritbreath Thorn":
                return 0.551040;
            default:
                throw new IllegalArgumentException(
                        "Unknown Lyney Normal value " + key);
        }
    }

    private double burstValue(String key, double fallback) {
        if (constellation >= 5) {
            return getTalentValue(
                    key + " C5",
                    "Miracle Parade".equals(key) ? 3.080000 : 8.280000);
        }
        return getTalentValue(key, fallback);
    }

    private StatsContainer captureStats(CombatSimulator simulator) {
        double currentTime = simulator.getCurrentTime();
        StatsContainer stats = getEffectiveStats(currentTime);
        for (Buff buff : simulator.getApplicableBuffs(this)) {
            if (!buff.isExpired(currentTime)) {
                buff.apply(stats, currentTime);
            }
        }
        return stats;
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

    private static List<HatState> copyHats(List<HatState> source) {
        List<HatState> copy = new ArrayList<>();
        for (HatState hat : source) {
            copy.add(hat.copy());
        }
        return copy;
    }

    private enum EventType {
        NORMAL_HIT,
        PROP_ARROW,
        SPIRITBREATH,
        HAT_EXPIRE,
        PYROTECHNIC,
        SKILL_HIT,
        BURST_START,
        BURST_COLLISION,
        BURST_END,
        FIREWORK,
        C2_TICK
    }

    /** Immutable delayed Lyney action. */
    private static final class PendingEvent {
        private final double time;
        private final EventType type;
        private final int index;
        private final int subIndex;
        private final long generation;
        private final StatsContainer snapshot;

        private PendingEvent(
                double time,
                EventType type,
                int index,
                int subIndex,
                long generation,
                StatsContainer snapshot) {
            this.time = time;
            this.type = type;
            this.index = index;
            this.subIndex = subIndex;
            this.generation = generation;
            this.snapshot = snapshot == null
                    ? null : snapshot.merge(null);
        }

        private PendingEvent copy() {
            return new PendingEvent(
                    time, type, index, subIndex, generation, snapshot);
        }
    }

    /** Immutable active Hat state. */
    private static final class HatState {
        private final long id;
        private final double expirationTime;
        private final StatsContainer snapshot;

        private HatState(
                long id,
                double expirationTime,
                StatsContainer snapshot) {
            this.id = id;
            this.expirationTime = expirationTime;
            this.snapshot = snapshot.merge(null);
        }

        private HatState copy() {
            return new HatState(id, expirationTime, snapshot);
        }
    }

    /** Immutable owner-bound Lyney rollback payload. */
    private static final class LyneyState implements State {
        private final Lyney owner;
        private final int normalAttackStep;
        private final int propSurplusStacks;
        private final long nextHatId;
        private final long burstGeneration;
        private final long c2Generation;
        private final double nextC1AllowedTime;
        private final double nextSpiritbreathAllowedTime;
        private final double burstExpirationTime;
        private final int c2Stacks;
        private final List<HatState> activeHats;
        private final List<PendingEvent> pendingEvents;

        private LyneyState(
                Lyney owner,
                int normalAttackStep,
                int propSurplusStacks,
                long nextHatId,
                long burstGeneration,
                long c2Generation,
                double nextC1AllowedTime,
                double nextSpiritbreathAllowedTime,
                double burstExpirationTime,
                int c2Stacks,
                List<HatState> activeHats,
                List<PendingEvent> pendingEvents) {
            this.owner = owner;
            this.normalAttackStep = normalAttackStep;
            this.propSurplusStacks = propSurplusStacks;
            this.nextHatId = nextHatId;
            this.burstGeneration = burstGeneration;
            this.c2Generation = c2Generation;
            this.nextC1AllowedTime = nextC1AllowedTime;
            this.nextSpiritbreathAllowedTime = nextSpiritbreathAllowedTime;
            this.burstExpirationTime = burstExpirationTime;
            this.c2Stacks = c2Stacks;
            this.activeHats = copyHats(activeHats);
            this.pendingEvents = copyEvents(pendingEvents);
        }
    }
}

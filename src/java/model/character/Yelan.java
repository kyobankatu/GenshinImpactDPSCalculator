package model.character;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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
import simulation.action.SkillActionMode;
import simulation.event.SimpleTimerEvent;

/**
 * Yelan's stationary single-target Normal, Lifeline, and Exquisite Throw kit.
 *
 * <p>Lv. 90 data, Talent 9/12 multipliers, frames, gauge, ICD, particles,
 * cooldowns, Energy timing, and constellation values follow pinned gcsim
 * {@code ef41805d} and KQM TCL {@code 80ba6241}. Each Throw projectile takes
 * its own generation-time stat snapshot, while A4 remains a live field buff
 * that follows the active character.</p>
 *
 * <p>Movement, path and aiming geometry, multi-target selection, actual enemy
 * HP, hitlag, ordinary aimed shots, Breakthrough acquisition, healing, and
 * defensive behavior are intentionally excluded.</p>
 */
public final class Yelan extends Character implements
        SimulatorInitializedCharacterEffect,
        SnapshotAwareCharacterEffect,
        SwitchAwareCharacter {
    private static final double FRAME = 1.0 / 60.0;
    private static final double EPSILON = 1e-9;
    private static final double PARTICLE_TRAVEL = 100.0 * FRAME;
    private static final int[] NORMAL_DURATIONS = { 15, 21, 38, 67 };
    private static final int[][] NORMAL_HITMARKS = {
            { 23 }, { 23 }, { 28 }, { 25, 39 }
    };
    private static final double[][] NORMAL_MULTIPLIERS = {
            { 0.74734 }, { 0.71732 }, { 0.948 }, { 0.59724, 0.59724 }
    };
    private static final int MAX_C4_STACKS = 4;
    private static final int MAX_C6_ARROWS = 5;

    private CombatSimulator initializedSimulator;
    private int normalAttackStep;
    private long skillGeneration;
    private long burstGeneration;
    private long waveGeneration;
    private final Set<Long> activeSkillGenerations = new HashSet<>();
    private double burstStartTime = Double.NEGATIVE_INFINITY;
    private double burstExpirationTime = Double.NEGATIVE_INFINITY;
    private double nextNormalWaveTime = Double.NEGATIVE_INFINITY;
    private double nextC2Time = Double.NEGATIVE_INFINITY;
    private int c4Stacks;
    private double c4ExpirationTime = Double.NEGATIVE_INFINITY;
    private int c6ArrowsRemaining;
    private double c6ExpirationTime = Double.NEGATIVE_INFINITY;
    private List<PendingEvent> pendingEvents = new ArrayList<>();

    /** Constructs repository-default C6 Yelan. */
    public Yelan(Weapon weapon, ArtifactSet artifacts) {
        this(weapon, artifacts, TalentDataManager.getInstance(), 6);
    }

    /** Constructs Yelan at an explicit constellation. */
    public Yelan(Weapon weapon, ArtifactSet artifacts, int constellation) {
        this(weapon, artifacts, TalentDataManager.getInstance(), constellation);
    }

    /**
     * Constructs Yelan with injectable talent data.
     *
     * @param weapon equipped bow
     * @param artifacts equipped artifact set
     * @param talentData static character data source
     * @param constellation constellation in the inclusive range 0-6
     */
    public Yelan(
            Weapon weapon,
            ArtifactSet artifacts,
            TalentDataSource talentData,
            int constellation) {
        super(talentData);
        if (constellation < 0 || constellation > 6) {
            throw new IllegalArgumentException(
                    "Yelan constellation must be between 0 and 6");
        }
        name = "Yelan";
        characterId = CharacterId.YELAN;
        element = Element.HYDRO;
        this.weapon = weapon;
        this.artifacts = new ArtifactSet[] { artifacts };
        this.constellation = constellation;
        baseStats.set(StatType.BASE_HP,
                getTalentValue("Base HP", 14450.0));
        baseStats.set(StatType.BASE_ATK,
                getTalentValue("Base ATK", 244.0));
        baseStats.set(StatType.BASE_DEF,
                getTalentValue("Base DEF", 548.0));
        baseStats.add(StatType.CRIT_RATE,
                getTalentValue("Ascension CRIT Rate", 0.192));
        setSkillCD(10.0);
        setBurstCD(18.0);
        if (constellation >= 1) {
            setSkillMaxCharges(2);
        }
    }

    /** Binds Yelan's coordinated-attack listener to one simulator. */
    @Override
    public void initializeForSimulator(CombatSimulator simulator) {
        if (simulator == null) {
            throw new IllegalArgumentException("Yelan simulator is required");
        }
        if (initializedSimulator != null
                && initializedSimulator != simulator) {
            throw new IllegalStateException(
                    "Yelan cannot be reused across CombatSimulator instances");
        }
        if (initializedSimulator == simulator) {
            return;
        }
        initializedSimulator = simulator;
        simulator.addActionRequestListener((actor, request, time) ->
                onActionRequest(simulator, actor, request, time));
    }

    /** Captures all Yelan-owned progression and future work. */
    @Override
    public State captureCharacterState() {
        return new YelanState(
                this,
                normalAttackStep,
                skillGeneration,
                burstGeneration,
                waveGeneration,
                activeSkillGenerations,
                burstStartTime,
                burstExpirationTime,
                nextNormalWaveTime,
                nextC2Time,
                c4Stacks,
                c4ExpirationTime,
                c6ArrowsRemaining,
                c6ExpirationTime,
                pendingEvents);
    }

    /** Accepts only payloads captured from this exact Yelan instance. */
    @Override
    public boolean acceptsCharacterState(State state) {
        return state instanceof YelanState
                && ((YelanState) state).owner == this;
    }

    /** Restores surviving Yelan-owned events exactly once. */
    @Override
    public void restoreCharacterState(
            State state,
            CombatSimulator simulator) {
        if (!acceptsCharacterState(state)) {
            throw new IllegalArgumentException("Unexpected Yelan character state");
        }
        initializeForSimulator(simulator);
        YelanState restored = (YelanState) state;
        normalAttackStep = restored.normalAttackStep;
        skillGeneration = restored.skillGeneration;
        burstGeneration = restored.burstGeneration;
        waveGeneration = restored.waveGeneration;
        activeSkillGenerations.clear();
        activeSkillGenerations.addAll(restored.activeSkillGenerations);
        burstStartTime = restored.burstStartTime;
        burstExpirationTime = restored.burstExpirationTime;
        nextNormalWaveTime = restored.nextNormalWaveTime;
        nextC2Time = restored.nextC2Time;
        c4Stacks = restored.c4Stacks;
        c4ExpirationTime = restored.c4ExpirationTime;
        c6ArrowsRemaining = restored.c6ArrowsRemaining;
        c6ExpirationTime = restored.c6ExpirationTime;
        pendingEvents = copyEvents(restored.pendingEvents);
        double currentTime = simulator.getCurrentTime();
        pendingEvents.removeIf(event -> event.time < currentTime - EPSILON);
        for (PendingEvent event : new ArrayList<>(pendingEvents)) {
            scheduleEvent(simulator, event);
        }
        expireConstellationState(currentTime);
    }

    /** Returns Yelan's 70-Energy Burst cost. */
    @Override
    public double getEnergyCost() {
        return getTalentValue("Energy Cost", 70.0);
    }

    /** Applies A1 from the live party's distinct elemental-type count. */
    @Override
    public void applyPassive(StatsContainer stats) {
        if (initializedSimulator == null) {
            stats.add(StatType.HP_PERCENT,
                    getTalentValue("A1 One Element", 0.06));
            return;
        }
        Set<Element> elements = new HashSet<>();
        for (Character member : initializedSimulator.getPartyMembers()) {
            elements.add(member.getElement());
        }
        int count = Math.max(1, Math.min(4, elements.size()));
        String key;
        double value;
        switch (count) {
            case 1:
                key = "A1 One Element";
                value = 0.06;
                break;
            case 2:
                key = "A1 Two Elements";
                value = 0.12;
                break;
            case 3:
                key = "A1 Three Elements";
                value = 0.18;
                break;
            default:
                key = "A1 Four Elements";
                value = 0.30;
                break;
        }
        stats.add(StatType.HP_PERCENT, getTalentValue(key, value));
    }

    /** Returns whether the current Depth-Clarion Dice window is active. */
    public boolean isExquisiteThrowActive(double currentTime) {
        return burstGeneration > 0
                && currentTime + EPSILON >= burstStartTime
                && currentTime < burstExpirationTime;
    }

    /** Returns C4's active single-target stack count. */
    public int getC4Stacks(double currentTime) {
        expireConstellationState(currentTime);
        return c4Stacks;
    }

    /** Returns the number of C6 Mastermind arrows still available. */
    public int getC6ArrowsRemaining(double currentTime) {
        expireConstellationState(currentTime);
        return c6ArrowsRemaining;
    }

    /** Returns the earliest timestamp accepted by the Normal wave gate. */
    public double getNextNormalWaveTime() {
        return nextNormalWaveTime;
    }

    /** Resets Normal sequence progression when Yelan leaves the field. */
    @Override
    public void onSwitchOut(CombatSimulator simulator) {
        normalAttackStep = 0;
    }

    /** Dispatches Yelan's supported typed actions. */
    @Override
    public void onAction(
            CharacterActionRequest request,
            CombatSimulator simulator) {
        if (request == null) {
            throw new IllegalArgumentException("Yelan action is required");
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
                if (request.getSkillMode() != SkillActionMode.PRESS) {
                    throw new IllegalArgumentException(
                            "Yelan Hold Skill is outside this slice");
                }
                lingeringLifeline(simulator);
                break;
            case BURST:
                depthClarionDice(simulator);
                break;
            default:
                throw new IllegalArgumentException(
                        "Unsupported action for Yelan: " + request.getKey());
        }
    }

    private void normalAttack(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        expireConstellationState(castTime);
        int step = normalAttackStep;
        for (int hit = 0; hit < NORMAL_MULTIPLIERS[step].length; hit++) {
            boolean c6 = constellation >= 6 && c6ArrowsRemaining > 0;
            if (c6) {
                c6ArrowsRemaining--;
            }
            queueEvent(simulator, new PendingEvent(
                    castTime + NORMAL_HITMARKS[step][hit] * FRAME,
                    c6 ? EventKind.C6_HIT : EventKind.NORMAL_HIT,
                    0L,
                    0L,
                    step,
                    hit,
                    null));
        }
        normalAttackStep = (normalAttackStep + 1)
                % NORMAL_MULTIPLIERS.length;
        simulator.advanceTime(NORMAL_DURATIONS[step] * FRAME);
    }

    private void highPlunge(CombatSimulator simulator) {
        queueEvent(simulator, new PendingEvent(
                simulator.getCurrentTime() + 41.0 * FRAME,
                EventKind.HIGH_PLUNGE,
                0L,
                0L,
                0,
                0,
                null));
        simulator.advanceTime(84.0 * FRAME);
    }

    private void lingeringLifeline(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        long generation = ++skillGeneration;
        activeSkillGenerations.add(generation);
        queueEvent(simulator, new PendingEvent(
                castTime + 33.0 * FRAME,
                EventKind.SKILL_COOLDOWN,
                generation,
                0L,
                0,
                0,
                null));
        queueEvent(simulator, new PendingEvent(
                castTime + 36.0 * FRAME,
                EventKind.SKILL_HIT,
                generation,
                0L,
                0,
                0,
                null));
        simulator.advanceTime(42.0 * FRAME);
    }

    private void depthClarionDice(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        long generation = ++burstGeneration;
        burstStartTime = castTime + 73.0 * FRAME;
        burstExpirationTime = burstStartTime + 15.0;
        nextNormalWaveTime = Double.NEGATIVE_INFINITY;
        nextC2Time = Double.NEGATIVE_INFINITY;
        markBurstCooldownUsed(castTime, simulator.getApplicableBuffs(this));
        queueEvent(simulator, new PendingEvent(
                castTime + 6.0 * FRAME,
                EventKind.BURST_ENERGY,
                generation,
                0L,
                0,
                0,
                null));
        queueEvent(simulator, new PendingEvent(
                burstStartTime,
                EventKind.BURST_ACTIVATE,
                generation,
                0L,
                0,
                0,
                null));
        queueEvent(simulator, new PendingEvent(
                castTime + 76.0 * FRAME,
                EventKind.BURST_INITIAL,
                generation,
                0L,
                0,
                0,
                null));
        if (constellation >= 6) {
            c6ArrowsRemaining = MAX_C6_ARROWS;
            c6ExpirationTime = castTime + 20.0;
        }
        simulator.advanceTime(93.0 * FRAME);
    }

    private void onActionRequest(
            CombatSimulator simulator,
            Character actor,
            CharacterActionRequest request,
            double time) {
        if (simulator != initializedSimulator
                || simulator.getActiveCharacter() != actor
                || request == null
                || request.getKey() != CharacterActionKey.NORMAL
                || !isExquisiteThrowActive(time)
                || time + EPSILON < nextNormalWaveTime) {
            return;
        }
        nextNormalWaveTime = time + 1.0;
        summonExquisiteThrow(simulator, time, burstGeneration);
    }

    private void summonExquisiteThrow(
            CombatSimulator simulator,
            double triggerTime,
            long generation) {
        if (!isCurrentBurstGeneration(generation)
                || !isExquisiteThrowActive(triggerTime)) {
            return;
        }
        long wave = ++waveGeneration;
        for (int projectile = 0; projectile < 3; projectile++) {
            queueEvent(simulator, new PendingEvent(
                    triggerTime + projectile * 6.0 * FRAME,
                    EventKind.WAVE_SNAPSHOT,
                    generation,
                    wave,
                    projectile,
                    0,
                    null));
        }
        if (constellation >= 2
                && triggerTime + EPSILON >= nextC2Time) {
            nextC2Time = triggerTime + 1.8;
            queueEvent(simulator, new PendingEvent(
                    triggerTime + 17.0 * FRAME,
                    EventKind.C2_HIT,
                    generation,
                    wave,
                    0,
                    0,
                    null));
        }
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
        schedule(simulator, event.time, activeSim -> {
            if (!pendingEvents.remove(event)) {
                return;
            }
            resolveEvent(activeSim, event);
        });
    }

    private void resolveEvent(
            CombatSimulator simulator,
            PendingEvent event) {
        if (isStale(event)) {
            return;
        }
        switch (event.kind) {
            case NORMAL_HIT:
                resolveNormalHit(simulator, event, false);
                break;
            case C6_HIT:
                resolveNormalHit(simulator, event, true);
                break;
            case HIGH_PLUNGE:
                resolveHighPlunge(simulator);
                break;
            case SKILL_COOLDOWN:
                markSkillUsed(
                        simulator.getCurrentTime(),
                        simulator.getApplicableBuffs(this));
                break;
            case SKILL_HIT:
                resolveSkillHit(simulator, event.generation);
                break;
            case PARTICLES:
                simulator.getEnergyDistributor().distributeParticles(
                        Element.HYDRO, 4.0, ParticleType.PARTICLE);
                activeSkillGenerations.remove(event.generation);
                break;
            case BURST_ENERGY:
                spendBurstEnergy(simulator.getCurrentTime());
                break;
            case BURST_ACTIVATE:
                replaceA4Buff(simulator, event.generation);
                break;
            case BURST_INITIAL:
                resolveBurstInitial(simulator);
                break;
            case WAVE_SNAPSHOT:
                snapshotProjectile(simulator, event);
                break;
            case WAVE_HIT:
                resolveWaveHit(simulator, event);
                break;
            case C2_HIT:
                resolveC2Hit(simulator);
                break;
            default:
                throw new IllegalStateException(
                        "Unhandled Yelan event " + event.kind);
        }
    }

    private void resolveNormalHit(
            CombatSimulator simulator,
            PendingEvent event,
            boolean c6) {
        AttackAction action;
        if (c6) {
            double barb = getTalentValue("Breakthrough Barb", 0.196792);
            double factor = getTalentValue("C6 Barb Factor", 1.56);
            action = attack(
                    "Winner Takes All Breakthrough Barb",
                    barb * factor,
                    Element.HYDRO,
                    StatType.BASE_HP,
                    StatType.CHARGED_ATTACK_DMG_BONUS,
                    ActionType.EXTRA,
                    ICDType.YelanBreakthrough,
                    ICDTag.Yelan_Breakthrough,
                    1.0,
                    false);
        } else {
            action = attack(
                    "Stealthy Bowshot N" + (event.index + 1)
                            + " Hit " + (event.subIndex + 1),
                    getTalentValue(
                            normalTalentKey(event.index, event.subIndex),
                            NORMAL_MULTIPLIERS[event.index][event.subIndex]),
                    Element.PHYSICAL,
                    StatType.BASE_ATK,
                    StatType.NORMAL_ATTACK_DMG_BONUS,
                    ActionType.NORMAL,
                    ICDType.Standard,
                    ICDTag.NormalAttack,
                    0.0,
                    false);
        }
        simulator.performActionWithoutTimeAdvance(characterId, action);
    }

    private void resolveHighPlunge(CombatSimulator simulator) {
        AttackAction plunge = attack(
                "Stealthy Bowshot High Plunge",
                getTalentValue("High Plunge", 2.6076),
                Element.PHYSICAL,
                StatType.BASE_ATK,
                StatType.PLUNGING_ATTACK_DMG_BONUS,
                ActionType.PLUNGE,
                ICDType.None,
                ICDTag.None,
                0.0,
                true);
        simulator.performActionWithoutTimeAdvance(characterId, plunge);
    }

    private void resolveSkillHit(
            CombatSimulator simulator,
            long generation) {
        AttackAction skill = attack(
                "Lingering Lifeline",
                getTalentValue(
                        constellation >= 5
                                ? "Lifeline C5" : "Lifeline",
                        constellation >= 5 ? 0.452272 : 0.384431),
                Element.HYDRO,
                StatType.BASE_HP,
                StatType.SKILL_DMG_BONUS,
                ActionType.SKILL,
                ICDType.Standard,
                ICDTag.ElementalSkill,
                1.0,
                false);
        simulator.performActionWithoutTimeAdvance(characterId, skill);
        queueEvent(simulator, new PendingEvent(
                simulator.getCurrentTime() + PARTICLE_TRAVEL,
                EventKind.PARTICLES,
                generation,
                0L,
                0,
                0,
                null));
        if (isExquisiteThrowActive(simulator.getCurrentTime())) {
            summonExquisiteThrow(
                    simulator,
                    simulator.getCurrentTime(),
                    burstGeneration);
        }
        if (constellation >= 4) {
            refreshC4(simulator);
        }
    }

    private void resolveBurstInitial(CombatSimulator simulator) {
        AttackAction initial = attack(
                "Depth-Clarion Dice (Initial)",
                getTalentValue(
                        constellation >= 3
                                ? "Burst Initial C3" : "Burst Initial",
                        constellation >= 3 ? 0.14616 : 0.124236),
                Element.HYDRO,
                StatType.BASE_HP,
                StatType.BURST_DMG_BONUS,
                ActionType.BURST,
                ICDType.None,
                ICDTag.None,
                2.0,
                false);
        simulator.performActionWithoutTimeAdvance(characterId, initial);
    }

    private void snapshotProjectile(
            CombatSimulator simulator,
            PendingEvent event) {
        StatsContainer snapshot = captureActionSnapshot(simulator);
        queueEvent(simulator, new PendingEvent(
                simulator.getCurrentTime() + 20.0 * FRAME,
                EventKind.WAVE_HIT,
                event.generation,
                event.wave,
                event.index,
                0,
                snapshot));
    }

    private void resolveWaveHit(
            CombatSimulator simulator,
            PendingEvent event) {
        AttackAction projectile = attack(
                "Exquisite Throw Projectile " + (event.index + 1),
                getTalentValue(
                        constellation >= 3
                                ? "Exquisite Throw C3" : "Exquisite Throw",
                        constellation >= 3 ? 0.09744 : 0.082824),
                Element.HYDRO,
                StatType.BASE_HP,
                StatType.BURST_DMG_BONUS,
                ActionType.BURST,
                ICDType.YelanBurst,
                ICDTag.Yelan_ExquisiteThrow,
                1.0,
                false);
        projectile.setStatSnapshot(event.snapshot);
        simulator.performActionWithoutTimeAdvance(characterId, projectile);
    }

    private void resolveC2Hit(CombatSimulator simulator) {
        AttackAction c2 = attack(
                "Exquisite Throw (C2)",
                getTalentValue("C2 Arrow", 0.14),
                Element.HYDRO,
                StatType.BASE_HP,
                StatType.BURST_DMG_BONUS,
                ActionType.BURST,
                ICDType.None,
                ICDTag.None,
                1.0,
                false);
        simulator.performActionWithoutTimeAdvance(characterId, c2);
    }

    private void refreshC4(CombatSimulator simulator) {
        double currentTime = simulator.getCurrentTime();
        expireConstellationState(currentTime);
        c4Stacks = Math.min(MAX_C4_STACKS, c4Stacks + 1);
        c4ExpirationTime = currentTime + 25.0;
        double amount = c4Stacks
                * getTalentValue("C4 Max HP Per Stack", 0.10);
        SimpleBuff buff = new SimpleBuff(
                "Yelan C4 Bait-and-Switch",
                BuffId.YELAN_C4_MAX_HP,
                25.0,
                currentTime,
                stats -> stats.add(StatType.HP_PERCENT, amount));
        buff.sourcedBy(characterId);
        simulator.applyTeamBuffNoStack(buff);
    }

    private void replaceA4Buff(
            CombatSimulator simulator,
            long generation) {
        if (!isCurrentBurstGeneration(generation)) {
            return;
        }
        simulator.getFieldBuffList().removeIf(
                buff -> buff.getId() == BuffId.YELAN_ADAPT_WITH_EASE);
        simulator.applyFieldBuff(new AdaptWithEaseBuff(
                burstStartTime,
                burstExpirationTime,
                getTalentValue("A4 Initial DMG Bonus", 0.01),
                getTalentValue("A4 Per Second DMG Bonus", 0.035),
                getTalentValue("A4 Maximum DMG Bonus", 0.50))
                .sourcedBy(characterId));
    }

    private void expireConstellationState(double currentTime) {
        if (currentTime + EPSILON >= c4ExpirationTime) {
            c4Stacks = 0;
        }
        if (currentTime + EPSILON >= c6ExpirationTime) {
            c6ArrowsRemaining = 0;
        }
    }

    private boolean isStale(PendingEvent event) {
        switch (event.kind) {
            case SKILL_COOLDOWN:
            case SKILL_HIT:
            case PARTICLES:
                return !activeSkillGenerations.contains(event.generation);
            case BURST_ENERGY:
            case BURST_ACTIVATE:
            case BURST_INITIAL:
            case WAVE_HIT:
            case C2_HIT:
                return !isCurrentBurstGeneration(event.generation);
            case WAVE_SNAPSHOT:
                return !isCurrentBurstGeneration(event.generation)
                        || !isExquisiteThrowActive(event.time);
            default:
                return false;
        }
    }

    private boolean isCurrentBurstGeneration(long generation) {
        return generation > 0 && generation == burstGeneration;
    }

    private StatsContainer captureActionSnapshot(
            CombatSimulator simulator) {
        double currentTime = simulator.getCurrentTime();
        StatsContainer stats = getEffectiveStats(currentTime);
        for (Buff buff : simulator.getApplicableBuffs(this)) {
            if (!buff.isExpired(currentTime)) {
                buff.apply(stats, currentTime);
            }
        }
        return stats;
    }

    private static String normalTalentKey(int step, int hit) {
        if (step == 3) {
            return "N4 Hit " + (hit + 1);
        }
        return "N" + (step + 1);
    }

    private static AttackAction attack(
            String name,
            double multiplier,
            Element element,
            StatType scalingStat,
            StatType bonusStat,
            ActionType actionType,
            ICDType icdType,
            ICDTag icdTag,
            double gaugeUnits,
            boolean blunt) {
        AttackAction action = new AttackAction(
                name,
                multiplier,
                element,
                scalingStat,
                bonusStat,
                0.0,
                actionType);
        action.setICD(icdType, icdTag, gaugeUnits);
        action.setShatterTrigger(blunt);
        action.setCountsAsSkillDmg(actionType == ActionType.SKILL);
        action.setCountsAsBurstDmg(actionType == ActionType.BURST);
        return action;
    }

    private static void schedule(
            CombatSimulator simulator,
            double time,
            Consumer<CombatSimulator> effect) {
        simulator.registerEvent(new SimpleTimerEvent(time, 1.0) {
            @Override
            public void onTick(CombatSimulator activeSim) {
                finish();
                effect.accept(activeSim);
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
        C6_HIT,
        HIGH_PLUNGE,
        SKILL_COOLDOWN,
        SKILL_HIT,
        PARTICLES,
        BURST_ENERGY,
        BURST_ACTIVATE,
        BURST_INITIAL,
        WAVE_SNAPSHOT,
        WAVE_HIT,
        C2_HIT
    }

    /** Immutable delayed Yelan event description. */
    private static final class PendingEvent {
        private final double time;
        private final EventKind kind;
        private final long generation;
        private final long wave;
        private final int index;
        private final int subIndex;
        private final StatsContainer snapshot;

        private PendingEvent(
                double time,
                EventKind kind,
                long generation,
                long wave,
                int index,
                int subIndex,
                StatsContainer snapshot) {
            this.time = time;
            this.kind = kind;
            this.generation = generation;
            this.wave = wave;
            this.index = index;
            this.subIndex = subIndex;
            this.snapshot = snapshot == null ? null : snapshot.merge(null);
        }

        private PendingEvent copy() {
            return new PendingEvent(
                    time,
                    kind,
                    generation,
                    wave,
                    index,
                    subIndex,
                    snapshot);
        }
    }

    /** Dynamic active-character-only A4 field buff. */
    private static final class AdaptWithEaseBuff extends Buff {
        private final double initial;
        private final double perSecond;
        private final double maximum;

        private AdaptWithEaseBuff(
                double startTime,
                double expirationTime,
                double initial,
                double perSecond,
                double maximum) {
            super(
                    "Yelan A4 Adapt With Ease",
                    BuffId.YELAN_ADAPT_WITH_EASE,
                    expirationTime - startTime,
                    startTime);
            this.initial = initial;
            this.perSecond = perSecond;
            this.maximum = maximum;
        }

        @Override
        protected void applyStats(
                StatsContainer stats,
                double currentTime) {
            long seconds = Math.max(
                    0L,
                    (long) Math.floor(
                            (currentTime - startTime + EPSILON)));
            stats.add(
                    StatType.DMG_BONUS_ALL,
                    Math.min(maximum, initial + seconds * perSecond));
        }
    }

    /** Immutable Yelan-owned simulator snapshot payload. */
    private static final class YelanState implements State {
        private final Yelan owner;
        private final int normalAttackStep;
        private final long skillGeneration;
        private final long burstGeneration;
        private final long waveGeneration;
        private final Set<Long> activeSkillGenerations;
        private final double burstStartTime;
        private final double burstExpirationTime;
        private final double nextNormalWaveTime;
        private final double nextC2Time;
        private final int c4Stacks;
        private final double c4ExpirationTime;
        private final int c6ArrowsRemaining;
        private final double c6ExpirationTime;
        private final List<PendingEvent> pendingEvents;

        private YelanState(
                Yelan owner,
                int normalAttackStep,
                long skillGeneration,
                long burstGeneration,
                long waveGeneration,
                Set<Long> activeSkillGenerations,
                double burstStartTime,
                double burstExpirationTime,
                double nextNormalWaveTime,
                double nextC2Time,
                int c4Stacks,
                double c4ExpirationTime,
                int c6ArrowsRemaining,
                double c6ExpirationTime,
                List<PendingEvent> pendingEvents) {
            this.owner = owner;
            this.normalAttackStep = normalAttackStep;
            this.skillGeneration = skillGeneration;
            this.burstGeneration = burstGeneration;
            this.waveGeneration = waveGeneration;
            this.activeSkillGenerations = new HashSet<>(
                    activeSkillGenerations);
            this.burstStartTime = burstStartTime;
            this.burstExpirationTime = burstExpirationTime;
            this.nextNormalWaveTime = nextNormalWaveTime;
            this.nextC2Time = nextC2Time;
            this.c4Stacks = c4Stacks;
            this.c4ExpirationTime = c4ExpirationTime;
            this.c6ArrowsRemaining = c6ArrowsRemaining;
            this.c6ExpirationTime = c6ExpirationTime;
            this.pendingEvents = copyEvents(pendingEvents);
        }
    }
}

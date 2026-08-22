package model.character;

import java.util.ArrayList;
import java.util.List;

import mechanics.buff.Buff;
import mechanics.buff.BuffId;
import mechanics.buff.SimpleBuff;
import mechanics.data.TalentDataManager;
import mechanics.data.TalentDataSource;
import mechanics.energy.ParticleType;
import mechanics.reaction.ReactionResult;
import model.entity.ArtifactSet;
import model.entity.Character;
import model.entity.ReactionAwareCharacter;
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
 * Dahlia's stationary fixed-target Favonian Favor support slice through C6.
 *
 * <p>Favonius Bladework basics, Press Immersive Ordinance, Radiant Psalter,
 * four-hit Benison generation, A1 Frozen generation, A4 Max-HP-derived Normal
 * Attack speed, C1, C3-C5, and C6 speed follow pinned gcsim
 * {@code ef41805d}. The high-Plunge multiplier comes from Dahlia's pinned
 * data; its 39/66-frame timeline uses the pinned same-body sword archetype
 * because Dahlia's source package does not implement Plunge. Favor refreshes
 * its speed value every 0.5 seconds, and all delayed owner work is
 * reconstructable after simulator rollback.</p>
 *
 * <p>Shield absorption, shield regeneration, C2, player HP, C6 revival, Hold
 * Skill movement, geometry, multi-target behavior, hitlag extension, stamina, and low
 * Plunge are excluded. Benison is therefore retained as a capped support
 * resource and is never consumed by an approximated shield.</p>
 */
public final class Dahlia extends Character implements
        SimulatorInitializedCharacterEffect,
        SnapshotAwareCharacterEffect,
        SwitchAwareCharacter,
        ReactionAwareCharacter {
    private static final double FRAME = 1.0 / 60.0;
    private static final double EPSILON = 1e-9;
    private static final double PARTICLE_TRAVEL = 100.0 * FRAME;
    private static final double SPEED_REFRESH_INTERVAL = 0.5;
    private static final int[][] NORMAL_HIT_FRAMES = {
        { 15 }, { 13 }, { 14, 15 }, { 21 }
    };
    private static final int[] NORMAL_DURATIONS = { 32, 34, 47, 62 };
    private static final String[][] NORMAL_KEYS = {
        { "N1" }, { "N2" },
        { "N3 Hit 1", "N3 Hit 2" }, { "N4" }
    };
    private static final double[][] NORMAL_T9 = {
        { 0.800049 }, { 0.736722 },
        { 0.436238, 0.533092 }, { 1.206267 }
    };
    private static final int[] CHARGED_HIT_FRAMES = { 10, 10 };
    private static final double[] CHARGED_T9 = {
        0.732614, 1.011706
    };

    /**
     * Normal-attack hitlag from gcsim config YAML pinned at
     * {@code 3647a07a7cc3004bc1e79d9bb5f7444de20dceaa}.
     */
    private static final HitlagProfile[] NORMAL_HITLAG = {
        new HitlagProfile(0.03, 0.01, true, false, false),
        new HitlagProfile(0.03, 0.01, true, false, false),
        new HitlagProfile(0.06, 0.01, true, false, false),
        new HitlagProfile(0.09, 0.01, true, false, false)
    };

    private CombatSimulator initializedSimulator;
    private int normalAttackStep;
    private long skillGeneration;
    private long burstGeneration;
    private double favonianFavorUntil = Double.NEGATIVE_INFINITY;
    private double nextNormalBenisonAllowedTime = Double.NEGATIVE_INFINITY;
    private double nextFrozenBenisonAllowedTime = Double.NEGATIVE_INFINITY;
    private int normalHitCount;
    private int generatedBenisonStacks;
    private int benisonStacks;
    private List<PendingEvent> pendingEvents = new ArrayList<>();

    /** Constructs repository-default C6 Dahlia. */
    public Dahlia(Weapon weapon, ArtifactSet artifacts) {
        this(weapon, artifacts, TalentDataManager.getInstance(), 6);
    }

    /** Constructs Dahlia at an explicit constellation. */
    public Dahlia(
            Weapon weapon,
            ArtifactSet artifacts,
            int constellation) {
        this(weapon, artifacts, TalentDataManager.getInstance(), constellation);
    }

    /**
     * Constructs Dahlia with injectable static character data.
     *
     * @param weapon equipped sword, or {@code null}
     * @param artifacts equipped artifact set, or {@code null}
     * @param talentData static data source
     * @param constellation constellation in {@code [0, 6]}
     */
    public Dahlia(
            Weapon weapon,
            ArtifactSet artifacts,
            TalentDataSource talentData,
            int constellation) {
        super(talentData);
        if (constellation < 0 || constellation > 6) {
            throw new IllegalArgumentException(
                    "Dahlia constellation must be between 0 and 6");
        }
        name = "Dahlia";
        characterId = CharacterId.DAHLIA;
        element = Element.HYDRO;
        this.weapon = weapon;
        this.artifacts = new ArtifactSet[] { artifacts };
        this.constellation = constellation;
        baseStats.set(StatType.BASE_HP,
                getTalentValue("Base HP", 12506.0));
        baseStats.set(StatType.BASE_ATK,
                getTalentValue("Base ATK", 189.0));
        baseStats.set(StatType.BASE_DEF,
                getTalentValue("Base DEF", 560.0));
        baseStats.add(StatType.HP_PERCENT,
                getTalentValue("Ascension HP Percent", 0.24));
        setSkillCD(getTalentValue("Skill Cooldown", 9.0));
        setBurstCD(getTalentValue("Burst Cooldown", 15.0));
    }

    /** Binds Dahlia's listeners and delayed work to exactly one simulator. */
    @Override
    public void initializeForSimulator(CombatSimulator simulator) {
        if (simulator == null) {
            throw new IllegalArgumentException("Dahlia simulator is required");
        }
        if (!simulator.getPartyMembers().contains(this)) {
            throw new IllegalArgumentException(
                    "Dahlia must belong to the simulator party");
        }
        if (initializedSimulator != null
                && initializedSimulator != simulator) {
            throw new IllegalStateException(
                    "Dahlia cannot be reused across simulators");
        }
        if (initializedSimulator == simulator) {
            return;
        }
        initializedSimulator = simulator;
        simulator.addDamageListener((actor, action, damage, time) ->
                observeNormalHit(
                        simulator, actor, action, damage, time));
        simulator.addReactionListener(this);
    }

    /** Captures Favor, Benison, generation, and delayed-event state. */
    @Override
    public State captureCharacterState() {
        return new DahliaState(
                this,
                normalAttackStep,
                skillGeneration,
                burstGeneration,
                favonianFavorUntil,
                nextNormalBenisonAllowedTime,
                nextFrozenBenisonAllowedTime,
                normalHitCount,
                generatedBenisonStacks,
                benisonStacks,
                pendingEvents);
    }

    /** Accepts state captured from this exact Dahlia instance only. */
    @Override
    public boolean acceptsCharacterState(State state) {
        return state instanceof DahliaState
                && ((DahliaState) state).owner == this;
    }

    /** Restores Dahlia state and reconstructs each surviving event once. */
    @Override
    public void restoreCharacterState(
            State state,
            CombatSimulator simulator) {
        if (!acceptsCharacterState(state)) {
            throw new IllegalArgumentException("Unexpected Dahlia state");
        }
        initializeForSimulator(simulator);
        DahliaState restored = (DahliaState) state;
        normalAttackStep = restored.normalAttackStep;
        skillGeneration = restored.skillGeneration;
        burstGeneration = restored.burstGeneration;
        favonianFavorUntil = restored.favonianFavorUntil;
        nextNormalBenisonAllowedTime =
                restored.nextNormalBenisonAllowedTime;
        nextFrozenBenisonAllowedTime =
                restored.nextFrozenBenisonAllowedTime;
        normalHitCount = restored.normalHitCount;
        generatedBenisonStacks = restored.generatedBenisonStacks;
        benisonStacks = restored.benisonStacks;
        pendingEvents = copyEvents(restored.pendingEvents);
        double currentTime = simulator.getCurrentTime();
        pendingEvents.removeIf(event ->
                event.time < currentTime - EPSILON);
        for (PendingEvent event : new ArrayList<>(pendingEvents)) {
            registerEvent(simulator, event);
        }
    }

    /** Returns Dahlia's 60-Energy Burst cost. */
    @Override
    public double getEnergyCost() {
        return getTalentValue("Energy Cost", 60.0);
    }

    /** Dahlia's ascension HP is loaded structurally in the constructor. */
    @Override
    public void applyPassive(StatsContainer stats) {
    }

    /** Supports only the stationary Press Skill represented by this slice. */
    @Override
    protected boolean supportsSkillActionMode(SkillActionMode mode) {
        return mode == SkillActionMode.PRESS;
    }

    /** Resets Dahlia's Normal string on switch-out. */
    @Override
    public void onSwitchOut(CombatSimulator simulator) {
        normalAttackStep = 0;
    }

    /** Returns whether Favonian Favor is active in its half-open window. */
    public boolean isFavonianFavorActive(double currentTime) {
        return currentTime < favonianFavorUntil;
    }

    /** Returns Benison retained by the offensive-only slice. */
    public int getBenisonStacks() {
        return benisonStacks;
    }

    /** Returns total Benison generated in the current Burst window. */
    public int getGeneratedBenisonStacks() {
        return generatedBenisonStacks;
    }

    /** Returns the unresolved Dahlia-owned event count. */
    public int getPendingEventCount() {
        return pendingEvents.size();
    }

    /** Dispatches Dahlia's represented typed actions. */
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
                immersiveOrdinance(simulator);
                break;
            case BURST:
                radiantPsalter(simulator);
                break;
            default:
                throw new IllegalArgumentException(
                        "Unsupported action for Dahlia: "
                                + request.getKey());
        }
    }

    /** Adds A1 Benison only for typed Frozen events during Favor. */
    @Override
    public void onReaction(
            ReactionResult result,
            Character source,
            double time,
            CombatSimulator simulator) {
        if (simulator != initializedSimulator
                || result == null
                || result.getKind() != ReactionResult.Kind.FROZEN
                || source == null
                || !simulator.getPartyMembers().contains(source)
                || !isFavonianFavorActive(time)
                || time + EPSILON < nextFrozenBenisonAllowedTime) {
            return;
        }
        nextFrozenBenisonAllowedTime = time
                + getTalentValue("A1 Cooldown", 8.0);
        addBenisonStacks((int) getTalentValue(
                "A1 Benison Stacks", 2.0));
    }

    private void normalAttack(CombatSimulator simulator) {
        int step = normalAttackStep;
        double castTime = simulator.getCurrentTime();
        double speedScale = 1.0 + normalAttackSpeed(castTime);
        for (int hit = 0; hit < NORMAL_HIT_FRAMES[step].length; hit++) {
            queueEvent(simulator, new PendingEvent(
                    castTime + NORMAL_HIT_FRAMES[step][hit]
                            * FRAME / speedScale,
                    EventKind.NORMAL_HIT,
                    step,
                    hit,
                    0L));
        }
        normalAttackStep = (normalAttackStep + 1)
                % NORMAL_HIT_FRAMES.length;
        simulator.advanceTime(
                NORMAL_DURATIONS[step] * FRAME / speedScale);
    }

    private void chargedAttack(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        for (int hit = 0; hit < CHARGED_HIT_FRAMES.length; hit++) {
            queueEvent(simulator, new PendingEvent(
                    castTime + CHARGED_HIT_FRAMES[hit] * FRAME,
                    EventKind.CHARGED_HIT,
                    hit,
                    0,
                    0L));
        }
        simulator.advanceTime(67.0 * FRAME);
    }

    private void highPlunge(CombatSimulator simulator) {
        queueEvent(simulator, new PendingEvent(
                simulator.getCurrentTime() + 39.0 * FRAME,
                EventKind.HIGH_PLUNGE,
                0,
                0,
                0L));
        simulator.advanceTime(66.0 * FRAME);
    }

    private void immersiveOrdinance(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        long generation = ++skillGeneration;
        queueEvent(simulator, new PendingEvent(
                castTime + 23.0 * FRAME,
                EventKind.SKILL_COOLDOWN,
                0,
                0,
                generation));
        queueEvent(simulator, new PendingEvent(
                castTime + 27.0 * FRAME,
                EventKind.SKILL_HIT,
                0,
                0,
                generation));
        simulator.advanceTime(53.0 * FRAME);
    }

    private void radiantPsalter(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        long generation = ++burstGeneration;
        resetBenisonWindow();
        markBurstCooldownUsed(
                castTime, simulator.getApplicableBuffs(this));
        queueEvent(simulator, new PendingEvent(
                castTime + 8.0 * FRAME,
                EventKind.BURST_ENERGY,
                0,
                0,
                generation));
        queueEvent(simulator, new PendingEvent(
                castTime + 30.0 * FRAME,
                EventKind.BURST_HIT,
                0,
                0,
                generation));
        queueEvent(simulator, new PendingEvent(
                castTime + 31.0 * FRAME,
                EventKind.FAVOR_ACTIVATE,
                0,
                0,
                generation));
        simulator.advanceTime(55.0 * FRAME);
    }

    private void resolveEvent(
            CombatSimulator simulator,
            PendingEvent event) {
        switch (event.kind) {
            case NORMAL_HIT:
                resolveNormalHit(simulator, event);
                break;
            case CHARGED_HIT:
                resolveChargedHit(simulator, event.index);
                break;
            case HIGH_PLUNGE:
                resolveHighPlunge(simulator);
                break;
            case SKILL_COOLDOWN:
                if (event.generation == skillGeneration) {
                    markSkillUsed(event.time,
                            simulator.getApplicableBuffs(this));
                }
                break;
            case SKILL_HIT:
                if (event.generation == skillGeneration) {
                    resolveSkillHit(simulator, event.generation);
                }
                break;
            case SKILL_PARTICLES:
                if (event.generation == skillGeneration) {
                    simulator.getEnergyDistributor().distributeParticles(
                            Element.HYDRO,
                            getTalentValue("Particle Count", 3.0),
                            ParticleType.PARTICLE);
                }
                break;
            case BURST_ENERGY:
                if (event.generation == burstGeneration) {
                    spendBurstEnergy(event.time);
                }
                break;
            case BURST_HIT:
                if (event.generation == burstGeneration) {
                    resolveBurstHit(simulator);
                }
                break;
            case FAVOR_ACTIVATE:
                if (event.generation == burstGeneration) {
                    activateFavonianFavor(simulator, event.generation);
                }
                break;
            case SPEED_REFRESH:
                if (event.generation == burstGeneration
                        && isFavonianFavorActive(event.time)) {
                    refreshFavorSpeed(simulator, event.generation);
                }
                break;
            default:
                throw new IllegalStateException(
                        "Unknown Dahlia event kind");
        }
    }

    private void resolveNormalHit(
            CombatSimulator simulator,
            PendingEvent event) {
        String key = NORMAL_KEYS[event.index][event.subIndex];
        performHit(
                simulator,
                "Favonius Bladework: Ritual " + key,
                getTalentValue(
                        key, NORMAL_T9[event.index][event.subIndex]),
                Element.PHYSICAL,
                StatType.NORMAL_ATTACK_DMG_BONUS,
                ActionType.NORMAL,
                ICDType.Standard,
                ICDTag.NormalAttack,
                0.0,
                false,
                NORMAL_HITLAG[event.index]);
    }

    private void resolveChargedHit(
            CombatSimulator simulator,
            int hit) {
        String key = "Charged Hit " + (hit + 1);
        performHit(
                simulator,
                "Favonius Bladework: Ritual " + key,
                getTalentValue(key, CHARGED_T9[hit]),
                Element.PHYSICAL,
                StatType.CHARGED_ATTACK_DMG_BONUS,
                ActionType.CHARGE,
                ICDType.Standard,
                ICDTag.NormalAttack,
                0.0,
                false,
                HitlagProfile.none());
    }

    private void resolveHighPlunge(CombatSimulator simulator) {
        performHit(
                simulator,
                "Favonius Bladework: Ritual High Plunge",
                getTalentValue("High Plunge", 2.933586),
                Element.PHYSICAL,
                StatType.PLUNGING_ATTACK_DMG_BONUS,
                ActionType.PLUNGE,
                ICDType.None,
                ICDTag.PlungeAttack,
                0.0,
                true,
                HitlagProfile.none());
    }

    private void resolveSkillHit(
            CombatSimulator simulator,
            long generation) {
        performHit(
                simulator,
                "Immersive Ordinance",
                skillValue(),
                Element.HYDRO,
                StatType.SKILL_DMG_BONUS,
                ActionType.SKILL,
                ICDType.None,
                ICDTag.None,
                1.0,
                false,
                HitlagProfile.none());
        queueEvent(simulator, new PendingEvent(
                simulator.getCurrentTime() + PARTICLE_TRAVEL,
                EventKind.SKILL_PARTICLES,
                0,
                0,
                generation));
    }

    private void resolveBurstHit(CombatSimulator simulator) {
        performHit(
                simulator,
                "Radiant Psalter",
                burstValue(),
                Element.HYDRO,
                StatType.BURST_DMG_BONUS,
                ActionType.BURST,
                ICDType.None,
                ICDTag.None,
                2.0,
                false,
                HitlagProfile.none());
    }

    private void performHit(
            CombatSimulator simulator,
            String displayName,
            double multiplier,
            Element hitElement,
            StatType bonusStat,
            ActionType actionType,
            ICDType icdType,
            ICDTag icdTag,
            double gauge,
            boolean shatter,
            HitlagProfile hitlagProfile) {
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
        action.setShatterTrigger(shatter);
        action.setHitlagProfile(hitlagProfile);
        action.setStatSnapshot(captureLiveStats(
                simulator.getCurrentTime()));
        simulator.performActionWithoutTimeAdvance(characterId, action);
    }

    private void activateFavonianFavor(
            CombatSimulator simulator,
            long generation) {
        double duration = constellation >= 4
                ? getTalentValue("C4 Favonian Favor Duration", 15.0)
                : getTalentValue("Favonian Favor Duration", 12.0);
        favonianFavorUntil = simulator.getCurrentTime() + duration;
        nextFrozenBenisonAllowedTime = Double.NEGATIVE_INFINITY;
        refreshFavorSpeed(simulator, generation);
    }

    private void refreshFavorSpeed(
            CombatSimulator simulator,
            long generation) {
        double currentTime = simulator.getCurrentTime();
        if (!isFavonianFavorActive(currentTime)) {
            return;
        }
        double maxHp = captureLiveStats(currentTime).getTotalHp();
        double hpAttackSpeed = Math.min(
                getTalentValue("A4 Speed Cap", 0.20),
                maxHp * getTalentValue("A4 Speed Per HP", 0.000005));
        final double attackSpeed = hpAttackSpeed
                + (constellation >= 6
                        ? getTalentValue("C6 Attack Speed", 0.10)
                        : 0.0);
        SimpleBuff favor = new SimpleBuff(
                "Dahlia Favonian Favor Attack Speed",
                BuffId.DAHLIA_FAVONIAN_FAVOR_ATTACK_SPEED,
                favonianFavorUntil - currentTime,
                currentTime,
                stats -> stats.add(
                        StatType.NORMAL_ATTACK_SPD, attackSpeed));
        favor.sourcedBy(characterId);
        simulator.applyTeamBuffNoStack(favor);

        double nextRefresh = currentTime + SPEED_REFRESH_INTERVAL;
        if (nextRefresh < favonianFavorUntil - EPSILON) {
            queueEvent(simulator, new PendingEvent(
                    nextRefresh,
                    EventKind.SPEED_REFRESH,
                    0,
                    0,
                    generation));
        }
    }

    private void observeNormalHit(
            CombatSimulator simulator,
            Character actor,
            AttackAction action,
            double ignoredDamage,
            double time) {
        if (!isFavonianFavorActive(time)
                || actor == null
                || actor != simulator.getActiveCharacter()
                || action == null
                || action.getActionType() != ActionType.NORMAL
                || time + EPSILON < nextNormalBenisonAllowedTime
                || generatedBenisonStacks >= benisonGenerationCap()) {
            return;
        }
        nextNormalBenisonAllowedTime = time
                + getTalentValue("Normal Hit Gate", 0.05);
        normalHitCount++;
        if (normalHitCount >= (int) getTalentValue(
                "Normal Hits Per Benison", 4.0)) {
            normalHitCount = 0;
            addBenisonStacks(1);
        }
    }

    private void addBenisonStacks(int requestedStacks) {
        if (requestedStacks <= 0) {
            return;
        }
        int available = benisonGenerationCap()
                - generatedBenisonStacks;
        int added = Math.min(requestedStacks, available);
        if (added <= 0) {
            return;
        }
        generatedBenisonStacks += added;
        benisonStacks += added;
        if (constellation >= 1) {
            receiveFlatEnergy(added * getTalentValue(
                    "C1 Energy Per Stack", 2.5));
        }
    }

    private int benisonGenerationCap() {
        return (int) getTalentValue("Benison Generation Cap", 4.0);
    }

    private void resetBenisonWindow() {
        normalHitCount = 0;
        generatedBenisonStacks = 0;
        benisonStacks = 0;
        nextNormalBenisonAllowedTime = Double.NEGATIVE_INFINITY;
        nextFrozenBenisonAllowedTime = Double.NEGATIVE_INFINITY;
    }

    private double skillValue() {
        if (constellation >= 5) {
            return getTalentValue(
                    "Immersive Ordinance C5", 4.656000);
        }
        return getTalentValue("Immersive Ordinance", 3.957600);
    }

    private double burstValue() {
        if (constellation >= 3) {
            return getTalentValue("Radiant Psalter C3", 8.128000);
        }
        return getTalentValue("Radiant Psalter", 6.908800);
    }

    private double normalAttackSpeed(double currentTime) {
        StatsContainer stats = captureLiveStats(currentTime);
        double speed = stats.get(StatType.ATK_SPD)
                + stats.get(StatType.NORMAL_ATTACK_SPD);
        return Math.min(0.60, Math.max(0.0, speed));
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

    private void queueEvent(
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

    /** Immutable delayed Dahlia event description. */
    private static final class PendingEvent {
        private final double time;
        private final EventKind kind;
        private final int index;
        private final int subIndex;
        private final long generation;

        private PendingEvent(
                double time,
                EventKind kind,
                int index,
                int subIndex,
                long generation) {
            this.time = time;
            this.kind = kind;
            this.index = index;
            this.subIndex = subIndex;
            this.generation = generation;
        }

        private PendingEvent copy() {
            return new PendingEvent(
                    time, kind, index, subIndex, generation);
        }
    }

    private enum EventKind {
        NORMAL_HIT,
        CHARGED_HIT,
        HIGH_PLUNGE,
        SKILL_COOLDOWN,
        SKILL_HIT,
        SKILL_PARTICLES,
        BURST_ENERGY,
        BURST_HIT,
        FAVOR_ACTIVATE,
        SPEED_REFRESH
    }

    /** Immutable owner-bound rollback payload. */
    private static final class DahliaState implements State {
        private final Dahlia owner;
        private final int normalAttackStep;
        private final long skillGeneration;
        private final long burstGeneration;
        private final double favonianFavorUntil;
        private final double nextNormalBenisonAllowedTime;
        private final double nextFrozenBenisonAllowedTime;
        private final int normalHitCount;
        private final int generatedBenisonStacks;
        private final int benisonStacks;
        private final List<PendingEvent> pendingEvents;

        private DahliaState(
                Dahlia owner,
                int normalAttackStep,
                long skillGeneration,
                long burstGeneration,
                double favonianFavorUntil,
                double nextNormalBenisonAllowedTime,
                double nextFrozenBenisonAllowedTime,
                int normalHitCount,
                int generatedBenisonStacks,
                int benisonStacks,
                List<PendingEvent> pendingEvents) {
            this.owner = owner;
            this.normalAttackStep = normalAttackStep;
            this.skillGeneration = skillGeneration;
            this.burstGeneration = burstGeneration;
            this.favonianFavorUntil = favonianFavorUntil;
            this.nextNormalBenisonAllowedTime =
                    nextNormalBenisonAllowedTime;
            this.nextFrozenBenisonAllowedTime =
                    nextFrozenBenisonAllowedTime;
            this.normalHitCount = normalHitCount;
            this.generatedBenisonStacks = generatedBenisonStacks;
            this.benisonStacks = benisonStacks;
            this.pendingEvents = copyEvents(pendingEvents);
        }
    }
}

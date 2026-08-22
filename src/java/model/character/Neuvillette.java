package model.character;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import mechanics.buff.Buff;
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
 * Neuvillette's stationary single-target Equitable Judgment offensive slice.
 *
 * <p>Level-90 data, three catalyst Normal Attacks, a deterministic full
 * Charged Attack: Equitable Judgment, Skill damage and particles, Burst
 * initial and Waterfall hits, A1 reaction stacks, fixed-full-HP A4, and the
 * representable offensive C1-C6 branches follow pinned gcsim
 * {@code ef41805d}. Delayed attacks use the source's hit-time snapshot points.
 * Mutable reaction, gate, generation, and pending-event state is owner-bound
 * and rollback-safe.</p>
 *
 * <p>Player HP change, drain, and healing; Sourcewater Droplet creation,
 * pickup, and geometry; hover and movement; random or multi-target selection;
 * stamina, low Plunge; and dynamic HP-ratio branches are excluded.
 * A4 therefore represents only the deterministic full-HP 30% Hydro bonus,
 * while C4 and droplet-driven C6 duration extension remain inactive.</p>
 */
public final class Neuvillette extends Character implements
        ReactionAwareCharacter,
        SimulatorInitializedCharacterEffect,
        SnapshotAwareCharacterEffect,
        SwitchAwareCharacter {
    private static final double FRAME = 1.0 / 60.0;
    private static final double EPSILON = 1e-9;
    private static final int[] NORMAL_HIT_FRAMES = { 19, 16, 32 };
    private static final int[] NORMAL_DURATIONS = { 36, 33, 62 };
    private static final double[] NORMAL_T9 = {
        0.927806, 0.786175, 1.229739
    };
    private static final double[] NORMAL_C3 = {
        1.091536, 0.924912, 1.446752
    };
    private static final int[] JUDGMENT_HIT_FRAMES = {
        232, 254, 279, 304, 329, 354, 379, 399
    };
    private static final double[] A1_MULTIPLIERS = {
        1.0, 1.1, 1.25, 1.6
    };
    /** Hitlag data pinned to gcsim {@code 3647a07a7cc3004bc1e79d9bb5f7444de20dceaa}. */
    private static final HitlagProfile THORN_HITLAG =
            new HitlagProfile(0.0, 0.01, true, false, false);

    private CombatSimulator initializedSimulator;
    private int normalAttackStep;
    private long judgmentGeneration;
    private long skillGeneration;
    private long burstGeneration;
    private double nextParticleAllowedTime = Double.NEGATIVE_INFINITY;
    private double nextThornAllowedTime = Double.NEGATIVE_INFINITY;
    private double nextC6AllowedTime = Double.NEGATIVE_INFINITY;
    private boolean resolvingSkillParticleEligible;
    private boolean resolvingJudgmentC6Eligible;
    private long resolvingGeneration;
    private EnumMap<A1Reaction, Double> a1Expirations =
            new EnumMap<>(A1Reaction.class);
    private List<PendingEvent> pendingEvents = new ArrayList<>();

    /** Constructs repository-default C6 Neuvillette. */
    public Neuvillette(Weapon weapon, ArtifactSet artifacts) {
        this(weapon, artifacts, TalentDataManager.getInstance(), 6);
    }

    /** Constructs Neuvillette at an explicit constellation. */
    public Neuvillette(
            Weapon weapon,
            ArtifactSet artifacts,
            int constellation) {
        this(
                weapon,
                artifacts,
                TalentDataManager.getInstance(),
                constellation);
    }

    /**
     * Constructs Neuvillette with injectable static talent data.
     *
     * @param weapon equipped catalyst, or {@code null}
     * @param artifacts equipped artifact set, or {@code null}
     * @param talentData static character-data source
     * @param constellation constellation level in {@code [0, 6]}
     */
    public Neuvillette(
            Weapon weapon,
            ArtifactSet artifacts,
            TalentDataSource talentData,
            int constellation) {
        super(talentData);
        if (constellation < 0 || constellation > 6) {
            throw new IllegalArgumentException(
                    "Neuvillette constellation must be between 0 and 6");
        }
        name = "Neuvillette";
        characterId = CharacterId.NEUVILLETTE;
        element = Element.HYDRO;
        this.weapon = weapon;
        this.artifacts = new ArtifactSet[] { artifacts };
        this.constellation = constellation;
        baseStats.set(StatType.BASE_HP,
                getTalentValue("Base HP", 14695.0));
        baseStats.set(StatType.BASE_ATK,
                getTalentValue("Base ATK", 208.0));
        baseStats.set(StatType.BASE_DEF,
                getTalentValue("Base DEF", 576.0));
        baseStats.add(StatType.CRIT_DMG,
                getTalentValue("Ascension CRIT DMG", 0.384));
        setSkillCD(getTalentValue("Skill Cooldown", 12.0));
        setBurstCD(getTalentValue("Burst Cooldown", 18.0));
    }

    /** Binds typed reaction and accepted-damage callbacks to one simulator. */
    @Override
    public void initializeForSimulator(CombatSimulator simulator) {
        if (simulator == null) {
            throw new IllegalArgumentException(
                    "Neuvillette simulator is required");
        }
        if (!simulator.getPartyMembers().contains(this)) {
            throw new IllegalArgumentException(
                    "Neuvillette must belong to the simulator party");
        }
        if (initializedSimulator != null
                && initializedSimulator != simulator) {
            throw new IllegalStateException(
                    "Neuvillette cannot be reused across simulators");
        }
        if (initializedSimulator == simulator) {
            return;
        }
        initializedSimulator = simulator;
        simulator.addReactionListener(this);
        simulator.addDamageListener(this::handleAcceptedDamage);
    }

    /** Captures combo, passive, gate, generation, and future-event state. */
    @Override
    public State captureCharacterState() {
        return new NeuvilletteState(
                this,
                normalAttackStep,
                judgmentGeneration,
                skillGeneration,
                burstGeneration,
                nextParticleAllowedTime,
                nextThornAllowedTime,
                nextC6AllowedTime,
                a1Expirations,
                pendingEvents);
    }

    /** Accepts state captured from this exact Neuvillette instance only. */
    @Override
    public boolean acceptsCharacterState(State state) {
        return state instanceof NeuvilletteState
                && ((NeuvilletteState) state).owner == this;
    }

    /** Restores owner state and reconstructs each unresolved event once. */
    @Override
    public void restoreCharacterState(
            State state,
            CombatSimulator simulator) {
        if (!acceptsCharacterState(state)) {
            throw new IllegalArgumentException(
                    "Unexpected Neuvillette state");
        }
        initializeForSimulator(simulator);
        NeuvilletteState restored = (NeuvilletteState) state;
        normalAttackStep = restored.normalAttackStep;
        judgmentGeneration = restored.judgmentGeneration;
        skillGeneration = restored.skillGeneration;
        burstGeneration = restored.burstGeneration;
        nextParticleAllowedTime = restored.nextParticleAllowedTime;
        nextThornAllowedTime = restored.nextThornAllowedTime;
        nextC6AllowedTime = restored.nextC6AllowedTime;
        a1Expirations = copyExpirations(restored.a1Expirations);
        pendingEvents = copyEvents(restored.pendingEvents);
        resolvingSkillParticleEligible = false;
        resolvingJudgmentC6Eligible = false;
        resolvingGeneration = 0L;
        double currentTime = simulator.getCurrentTime();
        pendingEvents.removeIf(event ->
                event.time < currentTime - EPSILON);
        for (PendingEvent event : new ArrayList<>(pendingEvents)) {
            scheduleEvent(simulator, event);
        }
    }

    /** Returns Neuvillette's 70-Energy Burst cost. */
    @Override
    public double getEnergyCost() {
        return getTalentValue("Energy Cost", 70.0);
    }

    /** Applies only A4's deterministic full-HP Hydro bonus branch. */
    @Override
    public void applyPassive(StatsContainer stats) {
        stats.add(
                StatType.HYDRO_DMG_BONUS,
                getTalentValue(
                        "A4 Full HP Hydro DMG Bonus", 0.30));
    }

    /** Resets the catalyst Normal string when Neuvillette leaves the field. */
    @Override
    public void onSwitchOut(CombatSimulator simulator) {
        normalAttackStep = 0;
    }

    /** Resets the catalyst Normal string when Neuvillette enters the field. */
    @Override
    public void onSwitchIn(CombatSimulator simulator) {
        normalAttackStep = 0;
    }

    /**
     * Records one typed Past Draconic Glories reaction family for 30 seconds.
     *
     * <p>Any party member may create the reaction. Repeated reactions refresh
     * their own family and do not create a second stack.</p>
     */
    @Override
    public void onReaction(
            ReactionResult result,
            Character source,
            double time,
            CombatSimulator simulator) {
        if (simulator != initializedSimulator
                || result == null
                || source == null
                || !simulator.getPartyMembers().contains(source)) {
            return;
        }
        A1Reaction reaction = a1Reaction(result);
        if (reaction == null) {
            return;
        }
        a1Expirations.put(
                reaction,
                time + getTalentValue("A1 Duration", 30.0));
    }

    /** Returns the active A1 stack count, including C1's base stack. */
    public int getPastDraconicGloriesStackCount(double currentTime) {
        int count = constellation >= 1 ? 1 : 0;
        for (double expirationTime : a1Expirations.values()) {
            if (currentTime < expirationTime) {
                count++;
            }
            if (count >= 3) {
                return 3;
            }
        }
        return count;
    }

    /** Returns the next Skill hit timestamp eligible to create particles. */
    public double getNextParticleAllowedTime() {
        return nextParticleAllowedTime;
    }

    /** Returns the next timestamp eligible to call a C6 current pair. */
    public double getNextC6AllowedTime() {
        return nextC6AllowedTime;
    }

    /** Returns the number of unresolved Neuvillette-owned events. */
    public int getPendingEventCount() {
        return pendingEvents.size();
    }

    /** Reports that dynamic player HP and HP-event branches are excluded. */
    public boolean isPlayerHpStateRepresented() {
        return false;
    }

    /** Reports that Sourcewater Droplets and their geometry are excluded. */
    public boolean isSourcewaterDropletRepresented() {
        return false;
    }

    /** Reports that hover, movement, and target geometry are excluded. */
    public boolean isMovementGeometryRepresented() {
        return false;
    }

    /** Reports that multi-target and random targeting are excluded. */
    public boolean isMultiTargetRandomnessRepresented() {
        return false;
    }

    /** Reports that stamina remains excluded; the legacy method name is retained. */
    public boolean isStaminaHitlagRepresented() {
        return false;
    }

    /** Reports that low Plunge is excluded. */
    public boolean isLowPlungeRepresented() {
        return false;
    }

    /** Reports that A4 is fixed to full HP rather than dynamic HP ratio. */
    public boolean isDynamicA4Represented() {
        return false;
    }

    /** Reports that droplet-driven C6 beam extension is excluded. */
    public boolean isC6DurationExtensionRepresented() {
        return false;
    }

    /** Dispatches Neuvillette's represented typed action set. */
    @Override
    public void onAction(
            CharacterActionRequest request,
            CombatSimulator simulator) {
        if (request == null) {
            throw new IllegalArgumentException(
                    "Neuvillette action is required");
        }
        initializeForSimulator(simulator);
        if (request.getKey() == CharacterActionKey.SKILL
                && request.getSkillMode() != SkillActionMode.PRESS) {
            throw new IllegalArgumentException(
                    "Neuvillette only supports Press Skill in this slice");
        }
        if (request.getKey() != CharacterActionKey.NORMAL) {
            normalAttackStep = 0;
        }
        switch (request.getKey()) {
            case NORMAL:
                normalAttack(simulator);
                break;
            case CHARGE:
                equitableJudgment(simulator);
                break;
            case SKILL:
                oTearsIShallRepay(simulator);
                break;
            case BURST:
                oTidesIHaveReturned(simulator);
                break;
            default:
                throw new IllegalArgumentException(
                        "Unsupported action for Neuvillette: "
                                + request.getKey());
        }
    }

    private void normalAttack(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        int step = normalAttackStep;
        queueEvent(simulator, new PendingEvent(
                castTime + NORMAL_HIT_FRAMES[step] * FRAME,
                EventKind.NORMAL,
                0L,
                step));
        normalAttackStep = (normalAttackStep + 1)
                % NORMAL_HIT_FRAMES.length;
        simulator.advanceTime(NORMAL_DURATIONS[step] * FRAME);
    }

    /** Queues the source-defined no-droplet full beam and fixed recovery. */
    private void equitableJudgment(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        long generation = ++judgmentGeneration;
        for (int index = 0;
                index < JUDGMENT_HIT_FRAMES.length;
                index++) {
            queueEvent(simulator, new PendingEvent(
                    castTime + JUDGMENT_HIT_FRAMES[index] * FRAME,
                    EventKind.JUDGMENT,
                    generation,
                    index));
        }
        simulator.advanceTime(450.0 * FRAME);
    }

    /** Queues Skill cooldown, hit-time snapshots, Arkhe, and particles. */
    private void oTearsIShallRepay(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        long generation = ++skillGeneration;
        queueEvent(simulator, new PendingEvent(
                castTime + 20.0 * FRAME,
                EventKind.SKILL_COOLDOWN,
                generation,
                0));
        queueEvent(simulator, new PendingEvent(
                castTime + 23.0 * FRAME,
                EventKind.SKILL,
                generation,
                0));
        queueEvent(simulator, new PendingEvent(
                castTime + 23.0 * FRAME,
                EventKind.THORN_CHECK,
                generation,
                0));
        simulator.advanceTime(42.0 * FRAME);
    }

    /** Queues Burst Energy consumption and all three hit-time snapshots. */
    private void oTidesIHaveReturned(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        long generation = ++burstGeneration;
        markBurstCooldownUsed(
                castTime,
                simulator.getApplicableBuffs(this));
        queueEvent(simulator, new PendingEvent(
                castTime + 4.0 * FRAME,
                EventKind.BURST_ENERGY,
                generation,
                0));
        queueEvent(simulator, new PendingEvent(
                castTime + 95.0 * FRAME,
                EventKind.BURST_INITIAL,
                generation,
                0));
        queueEvent(simulator, new PendingEvent(
                castTime + 135.0 * FRAME,
                EventKind.BURST_WATERFALL,
                generation,
                0));
        queueEvent(simulator, new PendingEvent(
                castTime + 154.0 * FRAME,
                EventKind.BURST_WATERFALL,
                generation,
                1));
        simulator.advanceTime(135.0 * FRAME);
    }

    /** Resolves one owner event after checking its generation. */
    private void resolveEvent(
            CombatSimulator simulator,
            PendingEvent event) {
        switch (event.kind) {
            case NORMAL:
                resolveNormal(simulator, event);
                break;
            case JUDGMENT:
                if (event.generation == judgmentGeneration) {
                    resolveJudgment(simulator, event);
                }
                break;
            case SKILL_COOLDOWN:
                if (event.generation == skillGeneration) {
                    markSkillUsed(
                            event.time,
                            simulator.getApplicableBuffs(this));
                }
                break;
            case SKILL:
                if (event.generation == skillGeneration) {
                    resolveSkill(simulator, event);
                }
                break;
            case THORN_CHECK:
                if (event.generation == skillGeneration) {
                    resolveThornCheck(simulator, event);
                }
                break;
            case THORN:
                if (event.generation == skillGeneration) {
                    resolveThorn(simulator, event);
                }
                break;
            case BURST_ENERGY:
                if (event.generation == burstGeneration) {
                    spendBurstEnergy(event.time);
                }
                break;
            case BURST_INITIAL:
            case BURST_WATERFALL:
                if (event.generation == burstGeneration) {
                    resolveBurst(simulator, event);
                }
                break;
            case PARTICLE:
                if (event.generation == skillGeneration) {
                    simulator.getEnergyDistributor().distributeParticles(
                            Element.HYDRO,
                            event.index,
                            ParticleType.PARTICLE);
                }
                break;
            case C6:
                if (event.generation == judgmentGeneration) {
                    resolveC6(simulator, event);
                }
                break;
            default:
                throw new IllegalStateException(
                        "Unknown Neuvillette event kind");
        }
    }

    private void resolveNormal(
            CombatSimulator simulator,
            PendingEvent event) {
        double[] multipliers = constellation >= 3
                ? NORMAL_C3 : NORMAL_T9;
        AttackAction action = createAction(
                "Normal " + (event.index + 1),
                getTalentValue(
                        "N" + (event.index + 1)
                                + (constellation >= 3 ? " C3" : ""),
                        multipliers[event.index]),
                StatType.BASE_ATK,
                StatType.NORMAL_ATTACK_DMG_BONUS,
                ActionType.NORMAL,
                ICDType.Standard,
                ICDTag.NormalAttack,
                1.0,
                simulator.getCurrentTime(),
                false);
        simulator.performActionWithoutTimeAdvance(characterId, action);
    }

    private void resolveJudgment(
            CombatSimulator simulator,
            PendingEvent event) {
        int stacks = getPastDraconicGloriesStackCount(event.time);
        double baseMultiplier = getTalentValue(
                constellation >= 3
                        ? "Equitable Judgment C3"
                        : "Equitable Judgment",
                constellation >= 3 ? 0.165094 : 0.134458);
        AttackAction action = createAction(
                "Charged Attack: Equitable Judgment",
                baseMultiplier * a1Multiplier(stacks),
                StatType.BASE_HP,
                StatType.CHARGED_ATTACK_DMG_BONUS,
                ActionType.CHARGE,
                ICDType.Standard,
                ICDTag.Neuvillette_Judgment,
                1.0,
                event.time,
                true);
        resolvingJudgmentC6Eligible = constellation >= 6;
        resolvingGeneration = event.generation;
        try {
            simulator.performActionWithoutTimeAdvance(characterId, action);
        } finally {
            resolvingJudgmentC6Eligible = false;
            resolvingGeneration = 0L;
        }
    }

    private void resolveSkill(
            CombatSimulator simulator,
            PendingEvent event) {
        AttackAction action = createAction(
                "O Tears, I Shall Repay",
                getTalentValue("O Tears I Shall Repay", 0.218688),
                StatType.BASE_HP,
                StatType.SKILL_DMG_BONUS,
                ActionType.SKILL,
                ICDType.None,
                ICDTag.ElementalSkill,
                1.0,
                event.time,
                false);
        action.setCountsAsSkillDmg(true);
        resolvingSkillParticleEligible = true;
        resolvingGeneration = event.generation;
        try {
            simulator.performActionWithoutTimeAdvance(characterId, action);
        } finally {
            resolvingSkillParticleEligible = false;
            resolvingGeneration = 0L;
        }
    }

    private void resolveThornCheck(
            CombatSimulator simulator,
            PendingEvent event) {
        if (event.time + EPSILON < nextThornAllowedTime) {
            return;
        }
        nextThornAllowedTime = event.time
                + getTalentValue("Spiritbreath Thorn Gate", 10.0);
        queueEvent(simulator, new PendingEvent(
                event.time + 37.0 * FRAME,
                EventKind.THORN,
                event.generation,
                0));
    }

    private void resolveThorn(
            CombatSimulator simulator,
            PendingEvent event) {
        AttackAction action = createAction(
                "Spiritbreath Thorn (Neuvillette)",
                getTalentValue("Spiritbreath Thorn", 0.3536),
                StatType.BASE_ATK,
                StatType.SKILL_DMG_BONUS,
                ActionType.SKILL,
                ICDType.None,
                ICDTag.ElementalSkill,
                0.0,
                event.time,
                false);
        action.setHitlagProfile(THORN_HITLAG);
        action.setCountsAsSkillDmg(true);
        simulator.performActionWithoutTimeAdvance(characterId, action);
    }

    private void resolveBurst(
            CombatSimulator simulator,
            PendingEvent event) {
        boolean waterfall = event.kind == EventKind.BURST_WATERFALL;
        String suffix = constellation >= 5 ? " C5" : "";
        String key = waterfall ? "Waterfall" : "O Tides I Have Returned";
        double fallback;
        if (waterfall) {
            fallback = constellation >= 5 ? 0.182109 : 0.154793;
        } else {
            fallback = constellation >= 5 ? 0.445157 : 0.378383;
        }
        AttackAction action = createAction(
                waterfall
                        ? "O Tides, I Have Returned: Waterfall DMG"
                        : "O Tides, I Have Returned: Skill DMG",
                getTalentValue(key + suffix, fallback),
                StatType.BASE_HP,
                StatType.BURST_DMG_BONUS,
                ActionType.BURST,
                ICDType.Standard,
                ICDTag.ElementalBurst,
                1.0,
                event.time,
                false);
        action.setCountsAsBurstDmg(true);
        simulator.performActionWithoutTimeAdvance(characterId, action);
    }

    private void resolveC6(
            CombatSimulator simulator,
            PendingEvent event) {
        int stacks = getPastDraconicGloriesStackCount(event.time);
        AttackAction action = createAction(
                "Charged Attack: Equitable Judgment (C6)",
                getTalentValue("C6 Max HP Ratio", 0.10)
                        * a1Multiplier(stacks),
                StatType.BASE_HP,
                StatType.CHARGED_ATTACK_DMG_BONUS,
                ActionType.CHARGE,
                ICDType.Standard,
                ICDTag.Neuvillette_C6,
                1.0,
                event.time,
                true);
        simulator.performActionWithoutTimeAdvance(characterId, action);
    }

    /** Handles accepted Skill and Judgment damage callbacks without recursion. */
    private void handleAcceptedDamage(
            Character actor,
            AttackAction action,
            double damage,
            double time) {
        if (initializedSimulator == null
                || actor != this
                || damage <= 0.0) {
            return;
        }
        if (resolvingSkillParticleEligible
                && time + EPSILON >= nextParticleAllowedTime) {
            nextParticleAllowedTime = time
                    + getTalentValue("Particle Gate", 0.3);
            queueEvent(initializedSimulator, new PendingEvent(
                    time + getTalentValue(
                            "Particle Travel Frames", 100.0) * FRAME,
                    EventKind.PARTICLE,
                    resolvingGeneration,
                    (int) getTalentValue("Skill Particle Count", 4.0)));
        }
        if (resolvingJudgmentC6Eligible
                && time + EPSILON >= nextC6AllowedTime) {
            nextC6AllowedTime = time
                    + getTalentValue("C6 Trigger Gate", 2.0);
            double impactTime = time + getTalentValue(
                    "C6 Impact Delay Frames", 29.0) * FRAME;
            for (int index = 0; index < 2; index++) {
                queueEvent(initializedSimulator, new PendingEvent(
                        impactTime,
                        EventKind.C6,
                        resolvingGeneration,
                        index));
            }
        }
    }

    /** Creates one hit with a source-defined hit-time stat snapshot. */
    private AttackAction createAction(
            String displayName,
            double multiplier,
            StatType scalingStat,
            StatType bonusStat,
            ActionType actionType,
            ICDType icdType,
            ICDTag icdTag,
            double gauge,
            double snapshotTime,
            boolean receivesC2) {
        AttackAction action = new AttackAction(
                displayName,
                multiplier,
                Element.HYDRO,
                scalingStat,
                bonusStat,
                0.0,
                actionType);
        action.setICD(icdType, icdTag, gauge);
        StatsContainer snapshot = captureLiveStats(snapshotTime);
        if (receivesC2 && constellation >= 2) {
            int stacks = getPastDraconicGloriesStackCount(snapshotTime);
            snapshot.add(
                    StatType.CRIT_DMG,
                    stacks * getTalentValue(
                            "C2 CRIT DMG Per Stack", 0.14));
        }
        action.setStatSnapshot(snapshot);
        return action;
    }

    private double a1Multiplier(int stackCount) {
        if (stackCount <= 0) {
            return 1.0;
        }
        if (stackCount >= 3) {
            return getTalentValue(
                    "A1 Three Stack Multiplier",
                    A1_MULTIPLIERS[3]);
        }
        if (stackCount == 2) {
            return getTalentValue(
                    "A1 Two Stack Multiplier",
                    A1_MULTIPLIERS[2]);
        }
        return getTalentValue(
                "A1 One Stack Multiplier",
                A1_MULTIPLIERS[1]);
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

    private static A1Reaction a1Reaction(ReactionResult result) {
        switch (result.getKind()) {
            case BLOOM:
                return A1Reaction.BLOOM;
            case LUNAR_BLOOM:
                return A1Reaction.LUNAR_BLOOM;
            case CRYSTALLIZE:
                return result.getRelatedElement() == Element.HYDRO
                        ? A1Reaction.HYDRO_CRYSTALLIZE : null;
            case ELECTRO_CHARGED:
                return A1Reaction.ELECTRO_CHARGED;
            case LUNAR_CHARGED:
                return A1Reaction.LUNAR_CHARGED;
            case LUNAR_CRYSTALLIZE:
                return A1Reaction.LUNAR_CRYSTALLIZE;
            case FROZEN:
                return A1Reaction.FROZEN;
            case SWIRL:
                return result.getSwirlElement() == Element.HYDRO
                        ? A1Reaction.HYDRO_SWIRL : null;
            case VAPORIZE:
                return A1Reaction.VAPORIZE;
            default:
                return null;
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

    private static EnumMap<A1Reaction, Double> copyExpirations(
            Map<A1Reaction, Double> source) {
        EnumMap<A1Reaction, Double> copy =
                new EnumMap<>(A1Reaction.class);
        copy.putAll(source);
        return copy;
    }

    private static List<PendingEvent> copyEvents(
            List<PendingEvent> source) {
        List<PendingEvent> copy = new ArrayList<>();
        for (PendingEvent event : source) {
            copy.add(event.copy());
        }
        return copy;
    }

    private enum A1Reaction {
        BLOOM,
        LUNAR_BLOOM,
        HYDRO_CRYSTALLIZE,
        ELECTRO_CHARGED,
        LUNAR_CHARGED,
        LUNAR_CRYSTALLIZE,
        FROZEN,
        HYDRO_SWIRL,
        VAPORIZE
    }

    private enum EventKind {
        NORMAL,
        JUDGMENT,
        SKILL_COOLDOWN,
        SKILL,
        THORN_CHECK,
        THORN,
        BURST_ENERGY,
        BURST_INITIAL,
        BURST_WATERFALL,
        PARTICLE,
        C6
    }

    private static final class PendingEvent {
        private final double time;
        private final EventKind kind;
        private final long generation;
        private final int index;

        private PendingEvent(
                double time,
                EventKind kind,
                long generation,
                int index) {
            this.time = time;
            this.kind = kind;
            this.generation = generation;
            this.index = index;
        }

        private PendingEvent copy() {
            return new PendingEvent(time, kind, generation, index);
        }
    }

    private static final class NeuvilletteState implements State {
        private final Neuvillette owner;
        private final int normalAttackStep;
        private final long judgmentGeneration;
        private final long skillGeneration;
        private final long burstGeneration;
        private final double nextParticleAllowedTime;
        private final double nextThornAllowedTime;
        private final double nextC6AllowedTime;
        private final EnumMap<A1Reaction, Double> a1Expirations;
        private final List<PendingEvent> pendingEvents;

        private NeuvilletteState(
                Neuvillette owner,
                int normalAttackStep,
                long judgmentGeneration,
                long skillGeneration,
                long burstGeneration,
                double nextParticleAllowedTime,
                double nextThornAllowedTime,
                double nextC6AllowedTime,
                Map<A1Reaction, Double> a1Expirations,
                List<PendingEvent> pendingEvents) {
            this.owner = owner;
            this.normalAttackStep = normalAttackStep;
            this.judgmentGeneration = judgmentGeneration;
            this.skillGeneration = skillGeneration;
            this.burstGeneration = burstGeneration;
            this.nextParticleAllowedTime = nextParticleAllowedTime;
            this.nextThornAllowedTime = nextThornAllowedTime;
            this.nextC6AllowedTime = nextC6AllowedTime;
            this.a1Expirations = copyExpirations(a1Expirations);
            this.pendingEvents = copyEvents(pendingEvents);
        }
    }
}

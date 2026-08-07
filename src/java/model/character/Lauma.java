package model.character;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import mechanics.buff.Buff;
import mechanics.buff.BuffId;
import mechanics.data.TalentDataManager;
import mechanics.data.TalentDataSource;
import mechanics.energy.ParticleType;
import mechanics.formula.DamageCalculator;
import mechanics.reaction.ReactionResult;
import model.entity.ArtifactSet;
import model.entity.Character;
import model.entity.CharacterTeamBuffProvider;
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
 * Lauma's source-backed fixed-target Lunar-Bloom support slice.
 *
 * <p>The implementation follows pinned gcsim {@code ef41805d}: three Dendro
 * catalyst Normals, the offensive Charged hit, repository-policy High Plunge,
 * Press/Hold Skill, eight Frostgrove Sanctuary ticks, Pale Hymn, Moonsong,
 * offensive ascension passives, and representable C1-C6 branches. Hold
 * atomically consumes up to three simulator-owned Verdant Dew stacks at frame
 * 29 and uses the returned count for both its direct Lunar-Bloom packet and
 * Moonsong.</p>
 *
 * <p>Player HP, healing, damage intake, shields, defense, stamina, movement,
 * deer terrain/geometry, random and multi-target behavior, hitlag, Low Plunge,
 * and exploration are intentionally excluded. Standard Bloom-family Pale Hymn
 * support uses typed reaction-bonus stats. Direct Lunar-Bloom callers outside
 * this class must pass their typed action through
 * {@link #prepareLunarBloomAction(Character, AttackAction, double)} because the
 * shared Lunar formula has no pre-resolution character-support callback.</p>
 */
public final class Lauma extends Character implements
        CharacterTeamBuffProvider,
        CombatSimulator.ReactionListener,
        SimulatorInitializedCharacterEffect,
        SnapshotAwareCharacterEffect,
        SwitchAwareCharacter {
    private static final double FRAME = 1.0 / 60.0;
    private static final double EPSILON = 1e-9;
    private static final double LEVEL_90_REACTION_BASE = 1446.85;
    private static final int[] NORMAL_HIT_FRAMES = { 14, 11, 16 };
    private static final int[] NORMAL_DURATION_FRAMES = { 29, 33, 38 };
    private static final double[] NORMAL_T9 = {
        0.572941, 0.540682, 0.756446
    };

    private CombatSimulator initializedSimulator;
    private int normalAttackStep;
    private long eventGeneration;
    private long skillGeneration;
    private long burstGeneration;
    private double a1ActiveUntil = Double.NEGATIVE_INFINITY;
    private double skillShredUntil = Double.NEGATIVE_INFINITY;
    private double nextParticleAllowedTime = Double.NEGATIVE_INFINITY;
    private double nextC4AllowedTime = Double.NEGATIVE_INFINITY;
    private double burstActiveUntil = Double.NEGATIVE_INFINITY;
    private int storedMoonsong;
    private double storedMoonsongUntil = Double.NEGATIVE_INFINITY;
    private boolean moonsongConvertedForBurst;
    private int burstPaleHymn;
    private int moonsongPaleHymn;
    private int c6PaleHymn;
    private double burstPaleHymnUntil = Double.NEGATIVE_INFINITY;
    private double moonsongPaleHymnUntil = Double.NEGATIVE_INFINITY;
    private double c6PaleHymnUntil = Double.NEGATIVE_INFINITY;
    private int c6SanctuaryCount;
    private HitKind resolvingHitKind;
    private List<PendingHit> pendingHits = new ArrayList<>();
    private List<PendingCommand> pendingCommands = new ArrayList<>();

    /** Constructs repository-default C6 Lauma. */
    public Lauma(Weapon weapon, ArtifactSet artifacts) {
        this(weapon, artifacts, TalentDataManager.getInstance(), 6);
    }

    /** Constructs Lauma at an explicit constellation. */
    public Lauma(
            Weapon weapon,
            ArtifactSet artifacts,
            int constellation) {
        this(weapon, artifacts, TalentDataManager.getInstance(), constellation);
    }

    /**
     * Constructs Lauma with injectable static talent data.
     *
     * @param weapon equipped catalyst, or {@code null}
     * @param artifacts equipped artifact set, or {@code null}
     * @param talentData static character-data source
     * @param constellation constellation level in {@code [0, 6]}
     */
    public Lauma(
            Weapon weapon,
            ArtifactSet artifacts,
            TalentDataSource talentData,
            int constellation) {
        super(talentData);
        if (constellation < 0 || constellation > 6) {
            throw new IllegalArgumentException(
                    "Lauma constellation must be between 0 and 6");
        }
        name = "Lauma";
        characterId = CharacterId.LAUMA;
        element = Element.DENDRO;
        this.weapon = weapon;
        this.artifacts = new ArtifactSet[] { artifacts };
        this.constellation = constellation;
        baseStats.set(StatType.BASE_HP,
                getTalentValue("Base HP", 10654.0));
        baseStats.set(StatType.BASE_ATK,
                getTalentValue("Base ATK", 255.0));
        baseStats.set(StatType.BASE_DEF,
                getTalentValue("Base DEF", 669.0));
        baseStats.add(StatType.ELEMENTAL_MASTERY,
                getTalentValue("Base Elemental Mastery", 200.0));
        baseStats.add(StatType.ELEMENTAL_MASTERY,
                getTalentValue("Ascension Elemental Mastery", 115.2));
        setSkillCD(getTalentValue("Skill Cooldown", 12.0));
        setBurstCD(getTalentValue("Burst Cooldown", 15.0));
    }

    /** Binds listeners and delayed work to one simulator instance. */
    @Override
    public void initializeForSimulator(CombatSimulator simulator) {
        if (simulator == null) {
            throw new IllegalArgumentException("Lauma simulator is required");
        }
        if (!simulator.getPartyMembers().contains(this)) {
            throw new IllegalArgumentException(
                    "Lauma must belong to the simulator party");
        }
        if (initializedSimulator != null
                && initializedSimulator != simulator) {
            throw new IllegalStateException(
                    "Lauma cannot be reused across simulators");
        }
        if (initializedSimulator == simulator) {
            return;
        }
        initializedSimulator = simulator;
        simulator.addReactionListener(this);
        simulator.addDamageListener((actor, action, damage, time) ->
                handleAcceptedDamage(
                        simulator, actor, action, damage, time));
    }

    /** Captures every Lauma-owned gate, stack pool, and reconstructable event. */
    @Override
    public State captureCharacterState() {
        return new LaumaState(
                this,
                normalAttackStep,
                eventGeneration,
                skillGeneration,
                burstGeneration,
                a1ActiveUntil,
                skillShredUntil,
                nextParticleAllowedTime,
                nextC4AllowedTime,
                burstActiveUntil,
                storedMoonsong,
                storedMoonsongUntil,
                moonsongConvertedForBurst,
                burstPaleHymn,
                moonsongPaleHymn,
                c6PaleHymn,
                burstPaleHymnUntil,
                moonsongPaleHymnUntil,
                c6PaleHymnUntil,
                c6SanctuaryCount,
                pendingHits,
                pendingCommands);
    }

    /** Accepts state captured from this exact Lauma instance only. */
    @Override
    public boolean acceptsCharacterState(State state) {
        return state instanceof LaumaState
                && ((LaumaState) state).owner == this;
    }

    /** Restores Lauma-owned state and reconstructs surviving delayed work. */
    @Override
    public void restoreCharacterState(
            State state,
            CombatSimulator simulator) {
        if (!acceptsCharacterState(state)) {
            throw new IllegalArgumentException("Unexpected Lauma state");
        }
        initializeForSimulator(simulator);
        LaumaState restored = (LaumaState) state;
        normalAttackStep = restored.normalAttackStep;
        eventGeneration = Math.max(
                eventGeneration, restored.eventGeneration) + 1L;
        skillGeneration = restored.skillGeneration;
        burstGeneration = restored.burstGeneration;
        a1ActiveUntil = restored.a1ActiveUntil;
        skillShredUntil = restored.skillShredUntil;
        nextParticleAllowedTime = restored.nextParticleAllowedTime;
        nextC4AllowedTime = restored.nextC4AllowedTime;
        burstActiveUntil = restored.burstActiveUntil;
        storedMoonsong = restored.storedMoonsong;
        storedMoonsongUntil = restored.storedMoonsongUntil;
        moonsongConvertedForBurst =
                restored.moonsongConvertedForBurst;
        burstPaleHymn = restored.burstPaleHymn;
        moonsongPaleHymn = restored.moonsongPaleHymn;
        c6PaleHymn = restored.c6PaleHymn;
        burstPaleHymnUntil = restored.burstPaleHymnUntil;
        moonsongPaleHymnUntil = restored.moonsongPaleHymnUntil;
        c6PaleHymnUntil = restored.c6PaleHymnUntil;
        c6SanctuaryCount = restored.c6SanctuaryCount;
        resolvingHitKind = null;
        pendingHits = copyHits(restored.pendingHits);
        pendingCommands = copyCommands(restored.pendingCommands);
        double currentTime = simulator.getCurrentTime();
        pendingHits.removeIf(hit -> hit.time < currentTime - EPSILON);
        pendingCommands.removeIf(command ->
                command.time < currentTime - EPSILON);
        for (PendingHit hit : new ArrayList<>(pendingHits)) {
            scheduleHit(simulator, hit);
        }
        for (PendingCommand command
                : new ArrayList<>(pendingCommands)) {
            scheduleCommand(simulator, command);
        }
    }

    /** Returns Lauma's sourced 60-Energy Burst cost. */
    @Override
    public double getEnergyCost() {
        return getTalentValue("Energy Cost", 60.0);
    }

    /** Lauma's permanent offensive state is provided through typed team buffs. */
    @Override
    public void applyPassive(StatsContainer stats) {
    }

    /** Reports Lauma's typed Moonsign contribution. */
    @Override
    public boolean isLunarCharacter() {
        return true;
    }

    /** Supports both sourced Press and Hold Skill modes. */
    @Override
    protected boolean supportsSkillActionMode(SkillActionMode mode) {
        return mode == SkillActionMode.PRESS
                || mode == SkillActionMode.HOLD;
    }

    /** Resets the catalyst Normal sequence on switch-out. */
    @Override
    public void onSwitchOut(CombatSimulator simulator) {
        normalAttackStep = 0;
    }

    /** Resets the catalyst Normal sequence on switch-in. */
    @Override
    public void onSwitchIn(CombatSimulator simulator) {
        normalAttackStep = 0;
    }

    /** Returns the currently unexpired total Pale Hymn stack count. */
    public int getPaleHymnStackCount(double currentTime) {
        pruneTimedState(currentTime);
        return burstPaleHymn + moonsongPaleHymn + c6PaleHymn;
    }

    /** Returns the currently stored, unconverted Moonsong Dew count. */
    public int getStoredMoonsong(double currentTime) {
        pruneTimedState(currentTime);
        return storedMoonsong;
    }

    /** Returns the exact Dendro/Hydro shred expiration timestamp. */
    public double getSkillShredUntil() {
        return skillShredUntil;
    }

    /** Returns the number of unresolved Lauma-owned damage packets. */
    public int getPendingHitCount() {
        return pendingHits.size();
    }

    /** Returns the number of unresolved Lauma-owned state commands. */
    public int getPendingCommandCount() {
        return pendingCommands.size();
    }

    /** Reports that healing and player-HP state are excluded. */
    public boolean isHealingRepresented() {
        return false;
    }

    /** Reports that deer movement, stamina, and terrain are excluded. */
    public boolean isDeerMovementRepresented() {
        return false;
    }

    /** Reports that random and multi-target behavior are excluded. */
    public boolean isRandomMultiTargetRepresented() {
        return false;
    }

    /** Reports that Low Plunge is excluded from the fixed High-Plunge action. */
    public boolean isLowPlungeRepresented() {
        return false;
    }

    /**
     * Applies Lauma's action-scoped Lunar-Bloom support before resolution.
     *
     * <p>This typed ingress is required for external direct Lunar-Bloom actions.
     * It captures the source's resolved stats, applies Ascendant A1 critical
     * support, C6 elevation when available, and converts one active Pale Hymn
     * flat addition into the equivalent current Lunar formula bonus. Stack
     * consumption remains reaction-listener driven when the action resolves.</p>
     *
     * @param source character owning the direct Lunar-Bloom action
     * @param action typed Lunar-Bloom action to prepare
     * @param currentTime action resolution time in seconds
     */
    public void prepareLunarBloomAction(
            Character source,
            AttackAction action,
            double currentTime) {
        prepareLunarBloomAction(
                source, action, currentTime, true);
    }

    private void prepareLunarBloomAction(
            Character source,
            AttackAction action,
            double currentTime,
            boolean applyPaleHymn) {
        if (initializedSimulator == null) {
            throw new IllegalStateException(
                    "Lauma must be initialized before Lunar-Bloom ingress");
        }
        if (source == null
                || !initializedSimulator.getPartyMembers().contains(source)) {
            throw new IllegalArgumentException(
                    "Lunar-Bloom source must belong to Lauma's simulator");
        }
        if (action == null
                || !action.isLunarConsidered()
                || action.getLunarReactionType()
                        != AttackAction.LunarReactionType.BLOOM) {
            throw new IllegalArgumentException(
                    "Lauma ingress requires a typed Lunar-Bloom action");
        }
        if (action.hasStatSnapshot()) {
            throw new IllegalArgumentException(
                    "Lauma Lunar-Bloom action is already prepared");
        }
        StatsContainer stats = DamageCalculator.resolveStats(
                source,
                action,
                initializedSimulator.getApplicableBuffs(source),
                currentTime);
        double laumaEm = currentLaumaEm(currentTime);
        stats.add(StatType.LUNAR_BASE_BONUS,
                Math.min(
                        getTalentValue(
                                "Lunar-Bloom Base Bonus Cap", 0.14),
                        laumaEm * getTalentValue(
                                "Lunar-Bloom Base Bonus Per EM", 0.000175)));
        if (isAscendantGleam()) {
            stats.add(StatType.CRIT_RATE,
                    getTalentValue("A1 Ascendant Crit Rate", 0.10));
            stats.add(StatType.LUNAR_REACTION_CRIT_DMG,
                    getTalentValue("A1 Ascendant Crit DMG", 0.20));
        }
        if (constellation >= 6 && isAscendantGleam()) {
            stats.add(StatType.LUNAR_MULTIPLIER,
                    getTalentValue("C6 Ascendant Elevation", 0.25));
        }
        if (getPaleHymnStackCount(currentTime) > 0) {
            double paleHymnFlat = laumaEm * lunarPaleHymnRatio();
            stats.add(StatType.LUNAR_BLOOM_DMG_BONUS,
                    -paleHymnFlat
                            / (LEVEL_90_REACTION_BASE * 2.0));
            if (!applyPaleHymn) {
                action.setStatSnapshot(stats);
                return;
            }
            double sourceEm = stats.get(StatType.ELEMENTAL_MASTERY);
            double baseSection = 3.0
                    * sourceEm
                    * action.getDamagePercent()
                    * (1.0 + stats.get(StatType.LUNAR_BASE_BONUS))
                    * (1.0 + stats.get(StatType.LUNAR_UNIQUE_BONUS));
            if (baseSection > EPSILON) {
                stats.add(StatType.LUNAR_BLOOM_DMG_BONUS,
                        paleHymnFlat / baseSection);
            }
        }
        action.setStatSnapshot(stats);
    }

    /** Returns dynamic Pale Hymn and Lauma Lunar support buffs. */
    @Override
    public List<Buff> getTeamBuffs() {
        List<Buff> buffs = new ArrayList<>();
        buffs.add(new Buff(
                "Lauma Pale Hymn",
                BuffId.LAUMA_PALE_HYMN,
                Double.MAX_VALUE,
                0.0) {
            @Override
            protected void applyStats(
                    StatsContainer stats,
                    double currentTime) {
                applyPaleHymnStats(stats, currentTime);
            }
        }.sourcedBy(characterId));
        buffs.add(new Buff(
                "Lauma Lunar Support",
                BuffId.LAUMA_LUNAR_SUPPORT,
                Double.MAX_VALUE,
                0.0) {
            @Override
            protected void applyStats(
                    StatsContainer stats,
                    double currentTime) {
                applyLunarSupportStats(stats, currentTime);
            }
        }.sourcedBy(characterId));
        return buffs;
    }

    /** Dispatches Lauma's represented typed action set. */
    @Override
    public void onAction(
            CharacterActionRequest request,
            CombatSimulator simulator) {
        if (request == null) {
            throw new IllegalArgumentException("Lauma action is required");
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
                hymnOfHunting(simulator, request.getSkillMode());
                break;
            case BURST:
                allHeartsBecomeTheBeatingMoon(simulator);
                break;
            default:
                throw new IllegalArgumentException(
                        "Unsupported action for Lauma: " + request.getKey());
        }
    }

    /** Consumes Pale Hymn only for the four source-backed reaction families. */
    @Override
    public void onReaction(
            ReactionResult result,
            Character source,
            double time,
            CombatSimulator simulator) {
        if (simulator != initializedSimulator
                || result == null
                || resolvingHitKind == HitKind.C6_SANCTUARY_LUNAR
                || getPaleHymnStackCount(time) <= 0) {
            return;
        }
        switch (result.getKind()) {
            case BLOOM:
            case HYPERBLOOM:
            case BURGEON:
            case LUNAR_BLOOM:
                consumePaleHymn(time);
                break;
            default:
                break;
        }
    }

    private void normalAttack(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        int step = normalAttackStep;
        boolean c6Lunar = constellation >= 6
                && getPaleHymnStackCount(castTime) > 0;
        if (c6Lunar) {
            consumePaleHymn(castTime);
        }
        queueHit(simulator, new PendingHit(
                castTime + NORMAL_HIT_FRAMES[step] * FRAME,
                c6Lunar ? HitKind.C6_NORMAL_LUNAR : HitKind.NORMAL,
                step,
                0,
                0L));
        normalAttackStep = (normalAttackStep + 1) % NORMAL_T9.length;
        simulator.advanceTime(NORMAL_DURATION_FRAMES[step] * FRAME);
    }

    private void chargedAttack(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        queueHit(simulator, new PendingHit(
                castTime + getTalentValue(
                        "Charged Hit Frames", 73.0) * FRAME,
                HitKind.CHARGED,
                0,
                0,
                0L));
        simulator.advanceTime(getTalentValue(
                "Charged Duration Frames", 68.0) * FRAME);
    }

    private void highPlunge(CombatSimulator simulator) {
        queueHit(simulator, new PendingHit(
                simulator.getCurrentTime(),
                HitKind.HIGH_PLUNGE,
                0,
                0,
                0L));
        simulator.advanceTime(getTalentValue(
                "High Plunge Duration Frames", 60.0) * FRAME);
    }

    private void hymnOfHunting(
            CombatSimulator simulator,
            SkillActionMode mode) {
        if (mode != SkillActionMode.PRESS
                && mode != SkillActionMode.HOLD) {
            throw new IllegalArgumentException(
                    "Lauma supports Press and Hold Skill only");
        }
        if (mode == SkillActionMode.HOLD
                && simulator.getVerdantDewCount() <= 0) {
            throw new IllegalStateException(
                    "Lauma Hold Skill requires Verdant Dew");
        }
        double castTime = simulator.getCurrentTime();
        long generation = ++skillGeneration;
        a1ActiveUntil = castTime
                + getTalentValue("A1 Duration", 20.0);
        if (constellation >= 6) {
            c6PaleHymn = 0;
            c6PaleHymnUntil = Double.NEGATIVE_INFINITY;
            c6SanctuaryCount = 0;
        }
        queueCommand(simulator, new PendingCommand(
                castTime + getTalentValue(
                        "Skill Cooldown Start Frames", 13.0) * FRAME,
                CommandKind.SKILL_COOLDOWN,
                generation,
                castTime));
        int tickCount = (int) getTalentValue(
                "Sanctuary Tick Count", 8.0);
        double firstTick = getTalentValue(
                "Sanctuary First Tick Frames", 62.0) * FRAME;
        double interval = getTalentValue(
                "Sanctuary Interval Frames", 117.0) * FRAME;
        for (int index = 0; index < tickCount; index++) {
            queueHit(simulator, new PendingHit(
                    castTime + firstTick + index * interval,
                    HitKind.SANCTUARY,
                    index,
                    0,
                    generation));
        }
        if (mode == SkillActionMode.PRESS) {
            queueHit(simulator, new PendingHit(
                    castTime + getTalentValue(
                            "Press Hit Frames", 16.0) * FRAME,
                    HitKind.SKILL_PRESS,
                    0,
                    0,
                    generation));
            simulator.advanceTime(getTalentValue(
                    "Press Duration Frames", 36.0) * FRAME);
            return;
        }
        queueHit(simulator, new PendingHit(
                castTime + getTalentValue(
                        "Hold Hit Frames", 45.0) * FRAME,
                HitKind.SKILL_HOLD,
                0,
                0,
                generation));
        queueCommand(simulator, new PendingCommand(
                castTime + getTalentValue(
                        "Hold Consume Dew Frames", 29.0) * FRAME,
                CommandKind.HOLD_CONSUME,
                generation,
                castTime));
        simulator.advanceTime(getTalentValue(
                "Hold Duration Frames", 56.0) * FRAME);
    }

    private void allHeartsBecomeTheBeatingMoon(
            CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        long generation = ++burstGeneration;
        markBurstCooldownUsed(
                castTime, simulator.getApplicableBuffs(this));
        burstActiveUntil = Double.NEGATIVE_INFINITY;
        moonsongConvertedForBurst = false;
        queueCommand(simulator, new PendingCommand(
                castTime + getTalentValue(
                        "Burst Energy Spend Frames", 8.0) * FRAME,
                CommandKind.BURST_ENERGY,
                generation,
                castTime));
        queueCommand(simulator, new PendingCommand(
                castTime + getTalentValue(
                        "Pale Hymn Gain Frames", 96.0) * FRAME,
                CommandKind.BURST_ACTIVATE,
                generation,
                castTime));
        simulator.advanceTime(getTalentValue(
                "Burst Duration Frames", 109.0) * FRAME);
    }

    private void resolveHit(
            CombatSimulator simulator,
            PendingHit hit) {
        if (simulator.getEnemy() == null) {
            return;
        }
        if (requiresActiveSkillGeneration(hit.kind)
                && hit.generation != skillGeneration) {
            return;
        }
        AttackAction action;
        StatsContainer snapshot = captureLiveStats(
                simulator.getCurrentTime());
        switch (hit.kind) {
            case NORMAL:
                action = standardAttack(
                        "Peregrination of Linnunrata N" + (hit.index + 1),
                        getTalentValue(
                                "N" + (hit.index + 1),
                                NORMAL_T9[hit.index]),
                        StatType.NORMAL_ATTACK_DMG_BONUS,
                        ActionType.NORMAL,
                        ICDType.Standard,
                        ICDTag.NormalAttack,
                        1.0);
                break;
            case C6_NORMAL_LUNAR:
                action = lunarBloomAttack(
                        "Peregrination C6 Pale Hymn",
                        getTalentValue(
                                "C6 Normal Lunar-Bloom Ratio", 1.50),
                        ActionType.NORMAL);
                prepareLunarBloomAction(
                        this, action, simulator.getCurrentTime());
                snapshot = action.getStatSnapshot();
                break;
            case CHARGED:
                action = standardAttack(
                        "Peregrination Spiritcall Prayer",
                        getTalentValue("Spiritcall Prayer", 2.193680),
                        StatType.CHARGED_ATTACK_DMG_BONUS,
                        ActionType.CHARGE,
                        ICDType.None,
                        ICDTag.ChargedAttack,
                        1.0);
                break;
            case HIGH_PLUNGE:
                action = standardAttack(
                        "Peregrination High Plunge",
                        getTalentValue("High Plunge", 2.607632),
                        StatType.PLUNGING_ATTACK_DMG_BONUS,
                        ActionType.PLUNGE,
                        ICDType.None,
                        ICDTag.PlungeAttack,
                        1.0);
                break;
            case SKILL_PRESS:
                applyA4SkillBonus(snapshot);
                action = skillAttack(
                        "Hymn of Hunting Press",
                        skillValue("Press DMG", "Press DMG C5",
                                2.0672, 2.4320),
                        ICDType.None,
                        1.0);
                break;
            case SKILL_HOLD:
                applyA4SkillBonus(snapshot);
                action = skillAttack(
                        "Hymn of Eternal Rest Hold",
                        skillValue("Hold DMG", "Hold DMG C5",
                                2.68736, 3.1616),
                        ICDType.None,
                        1.0);
                break;
            case HOLD_LUNAR:
                action = lunarBloomAttack(
                        "Hymn of Eternal Rest Lunar-Bloom",
                        skillValue(
                                "Hold Lunar-Bloom Per Dew",
                                "Hold Lunar-Bloom Per Dew C5",
                                2.584, 3.04) * hit.value,
                        ActionType.SKILL);
                prepareLunarBloomAction(
                        this, action, simulator.getCurrentTime());
                snapshot = action.getStatSnapshot();
                break;
            case SANCTUARY:
                applyA4SkillBonus(snapshot);
                double em = snapshot.get(StatType.ELEMENTAL_MASTERY);
                snapshot.add(StatType.FLAT_DMG_BONUS,
                        em * skillValue(
                                "Frostgrove Sanctuary EM",
                                "Frostgrove Sanctuary EM C5",
                                3.264, 3.84));
                action = skillAttack(
                        "Frostgrove Sanctuary",
                        skillValue(
                                "Frostgrove Sanctuary ATK",
                                "Frostgrove Sanctuary ATK C5",
                                1.632, 1.92),
                        ICDType.None,
                        1.0);
                break;
            case C6_SANCTUARY_LUNAR:
                action = lunarBloomAttack(
                        "Frostgrove Sanctuary C6",
                        getTalentValue(
                                "C6 Frostgrove Lunar-Bloom Ratio", 1.85),
                        ActionType.SKILL);
                prepareLunarBloomAction(
                        this, action, simulator.getCurrentTime(), false);
                snapshot = action.getStatSnapshot();
                break;
            default:
                throw new IllegalStateException(
                        "Unknown Lauma hit kind " + hit.kind);
        }
        if (!action.hasStatSnapshot()) {
            action.setStatSnapshot(snapshot);
        }
        resolvingHitKind = hit.kind;
        try {
            simulator.performActionWithoutTimeAdvance(characterId, action);
        } finally {
            resolvingHitKind = null;
        }
    }

    private void handleAcceptedDamage(
            CombatSimulator simulator,
            Character actor,
            AttackAction action,
            double damage,
            double time) {
        if (simulator != initializedSimulator
                || actor != this
                || resolvingHitKind == null
                || damage <= 0.0) {
            return;
        }
        switch (resolvingHitKind) {
            case SKILL_PRESS:
            case SKILL_HOLD:
            case HOLD_LUNAR:
                refreshSkillShred(time);
                break;
            case SANCTUARY:
                refreshSkillShred(time);
                triggerSanctuaryHitEffects(simulator, time);
                break;
            case C6_SANCTUARY_LUNAR:
                addC6PaleHymn(time);
                break;
            default:
                break;
        }
    }

    private void triggerSanctuaryHitEffects(
            CombatSimulator simulator,
            double time) {
        if (time + EPSILON >= nextParticleAllowedTime) {
            nextParticleAllowedTime = time
                    + getTalentValue("Particle Cooldown", 3.3);
            queueCommand(simulator, new PendingCommand(
                    time + getTalentValue(
                            "Particle Travel Frames", 100.0) * FRAME,
                    CommandKind.PARTICLE,
                    skillGeneration,
                    time));
        }
        if (constellation >= 4
                && time + EPSILON >= nextC4AllowedTime) {
            nextC4AllowedTime = time
                    + getTalentValue("C4 Cooldown", 5.0);
            receiveFlatEnergy(getTalentValue("C4 Flat Energy", 5.0));
        }
        if (constellation >= 6
                && c6SanctuaryCount < (int) getTalentValue(
                        "C6 Maximum Frostgrove Hits", 8.0)) {
            c6SanctuaryCount++;
            queueHit(simulator, new PendingHit(
                    time + getTalentValue(
                            "C6 Frostgrove Delay Frames", 16.0) * FRAME,
                    HitKind.C6_SANCTUARY_LUNAR,
                    c6SanctuaryCount - 1,
                    0,
                    skillGeneration));
        }
    }

    private void refreshSkillShred(double time) {
        skillShredUntil = time
                + getTalentValue("Skill Shred Duration", 10.0);
    }

    private void consumeHoldDew(
            CombatSimulator simulator,
            PendingCommand command) {
        if (command.generation != skillGeneration) {
            return;
        }
        int consumed = simulator.consumeVerdantDewCount(3);
        if (consumed <= 0) {
            throw new IllegalStateException(
                    "Lauma Hold Skill consumed no Verdant Dew");
        }
        addMoonsong(consumed, command.time);
        double holdHitTime = command.castTime
                + getTalentValue("Hold Hit Frames", 45.0) * FRAME;
        queueHit(simulator, new PendingHit(
                holdHitTime,
                HitKind.HOLD_LUNAR,
                0,
                consumed,
                command.generation));
    }

    private void activateBurst(double time) {
        burstPaleHymn = (int) getTalentValue(
                "Pale Hymn Initial Stacks", 18.0);
        double duration = getTalentValue("Pale Hymn Duration", 15.0);
        burstPaleHymnUntil = time + duration;
        burstActiveUntil = time + duration;
        pruneTimedState(time);
        if (storedMoonsong > 0 && !moonsongConvertedForBurst) {
            convertMoonsong(storedMoonsong, time);
            storedMoonsong = 0;
            storedMoonsongUntil = Double.NEGATIVE_INFINITY;
        }
    }

    private void addMoonsong(int dewCount, double time) {
        pruneTimedState(time);
        if (time + EPSILON < burstActiveUntil
                && !moonsongConvertedForBurst) {
            convertMoonsong(dewCount, time);
            return;
        }
        storedMoonsong = dewCount;
        storedMoonsongUntil = time
                + getTalentValue("Moonsong Duration", 15.0);
    }

    private void convertMoonsong(int dewCount, double time) {
        moonsongPaleHymn = dewCount * (int) getTalentValue(
                "Moonsong Pale Hymn Per Dew", 6.0);
        moonsongPaleHymnUntil = time
                + getTalentValue("Pale Hymn Duration", 15.0);
        moonsongConvertedForBurst = true;
    }

    private void addC6PaleHymn(double time) {
        pruneTimedState(time);
        c6PaleHymn += (int) getTalentValue(
                "C6 Pale Hymn Stacks", 2.0);
        c6PaleHymnUntil = time
                + getTalentValue("Pale Hymn Duration", 15.0);
    }

    private void consumePaleHymn(double time) {
        pruneTimedState(time);
        if (c6PaleHymn > 0) {
            c6PaleHymn--;
            return;
        }
        if (moonsongPaleHymn > 0) {
            moonsongPaleHymn--;
            return;
        }
        if (burstPaleHymn > 0) {
            burstPaleHymn--;
        }
    }

    private void pruneTimedState(double currentTime) {
        if (currentTime + EPSILON >= burstPaleHymnUntil) {
            burstPaleHymn = 0;
        }
        if (currentTime + EPSILON >= moonsongPaleHymnUntil) {
            moonsongPaleHymn = 0;
        }
        if (currentTime + EPSILON >= c6PaleHymnUntil) {
            c6PaleHymn = 0;
        }
        if (currentTime + EPSILON >= storedMoonsongUntil) {
            storedMoonsong = 0;
        }
    }

    private void applyPaleHymnStats(
            StatsContainer stats,
            double currentTime) {
        if (isA1Active(currentTime) && !isAscendantGleam()) {
            double expectedCrit = getTalentValue(
                    "A1 Nascent Expected Crit Bonus", 0.15);
            stats.add(StatType.BLOOM_DMG_BONUS, expectedCrit);
            stats.add(StatType.HYPERBLOOM_DMG_BONUS, expectedCrit);
            stats.add(StatType.BURGEON_DMG_BONUS, expectedCrit);
        }
        if (getPaleHymnStackCount(currentTime) <= 0) {
            return;
        }
        double flatSupport = currentLaumaEm(currentTime)
                * bloomPaleHymnRatio();
        stats.add(StatType.BLOOM_DMG_BONUS,
                flatSupport / (LEVEL_90_REACTION_BASE * 2.0));
        stats.add(StatType.HYPERBLOOM_DMG_BONUS,
                flatSupport / (LEVEL_90_REACTION_BASE * 3.0));
        stats.add(StatType.BURGEON_DMG_BONUS,
                flatSupport / (LEVEL_90_REACTION_BASE * 3.0));
        double lunarFlatSupport = currentLaumaEm(currentTime)
                * lunarPaleHymnRatio();
        stats.add(StatType.LUNAR_BLOOM_DMG_BONUS,
                lunarFlatSupport / (LEVEL_90_REACTION_BASE * 2.0));
    }

    private void applyLunarSupportStats(
            StatsContainer stats,
            double currentTime) {
        if (currentTime + EPSILON < skillShredUntil) {
            double shred = skillValue(
                    "RES Shred", "RES Shred C5", 0.225, 0.31);
            stats.add(StatType.DENDRO_RES_SHRED, shred);
            stats.add(StatType.HYDRO_RES_SHRED, shred);
        }
        if (constellation >= 2 && isAscendantGleam()) {
            stats.add(StatType.LUNAR_BLOOM_DMG_BONUS,
                    getTalentValue(
                            "C2 Ascendant Lunar-Bloom Bonus", 0.40));
        }
    }

    private boolean isA1Active(double currentTime) {
        return currentTime + EPSILON < a1ActiveUntil;
    }

    private boolean isAscendantGleam() {
        return initializedSimulator != null
                && initializedSimulator.getMoonsign()
                        == CombatSimulator.Moonsign.ASCENDANT_GLEAM;
    }

    private double currentLaumaEm(double currentTime) {
        StatsContainer stats = getEffectiveStats(currentTime);
        if (initializedSimulator != null) {
            for (Buff buff : initializedSimulator.getApplicableBuffs(this)) {
                if (!buff.isExpired(currentTime)
                        && buff.getId() != BuffId.LAUMA_PALE_HYMN) {
                    buff.apply(stats, currentTime);
                }
            }
        }
        return stats.get(StatType.ELEMENTAL_MASTERY);
    }

    private double bloomPaleHymnRatio() {
        double ratio = getTalentValue(
                constellation >= 3
                        ? "Bloom Family EM Ratio C3"
                        : "Bloom Family EM Ratio",
                constellation >= 3 ? 5.5552 : 4.72192);
        if (constellation >= 2) {
            ratio += getTalentValue(
                    "C2 Bloom Family EM Ratio", 5.0);
        }
        return ratio;
    }

    private double lunarPaleHymnRatio() {
        double ratio = getTalentValue(
                constellation >= 3
                        ? "Lunar-Bloom EM Ratio C3"
                        : "Lunar-Bloom EM Ratio",
                constellation >= 3 ? 4.4448 : 3.77808);
        if (constellation >= 2) {
            ratio += getTalentValue(
                    "C2 Lunar-Bloom EM Ratio", 4.0);
        }
        return ratio;
    }

    private double skillValue(
            String t9Key,
            String c5Key,
            double t9Default,
            double c5Default) {
        boolean c5 = constellation >= 5;
        return getTalentValue(
                c5 ? c5Key : t9Key,
                c5 ? c5Default : t9Default);
    }

    private void applyA4SkillBonus(StatsContainer stats) {
        double bonus = Math.min(
                getTalentValue("A4 Skill DMG Cap", 0.32),
                stats.get(StatType.ELEMENTAL_MASTERY)
                        * getTalentValue(
                                "A4 Skill DMG Per EM", 0.004));
        stats.add(StatType.SKILL_DMG_BONUS, bonus);
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

    private static AttackAction standardAttack(
            String displayName,
            double multiplier,
            StatType bonusStat,
            ActionType actionType,
            ICDType icdType,
            ICDTag icdTag,
            double gauge) {
        AttackAction action = new AttackAction(
                displayName,
                multiplier,
                Element.DENDRO,
                StatType.BASE_ATK,
                bonusStat,
                0.0,
                actionType);
        action.setICD(icdType, icdTag, gauge);
        action.setCountsAsSkillDmg(actionType == ActionType.SKILL);
        return action;
    }

    private static AttackAction skillAttack(
            String displayName,
            double multiplier,
            ICDType icdType,
            double gauge) {
        return standardAttack(
                displayName,
                multiplier,
                StatType.SKILL_DMG_BONUS,
                ActionType.SKILL,
                icdType,
                ICDTag.ElementalSkill,
                gauge);
    }

    private static AttackAction lunarBloomAttack(
            String displayName,
            double multiplier,
            ActionType actionType) {
        AttackAction action = new AttackAction(
                displayName,
                multiplier,
                Element.DENDRO,
                StatType.ELEMENTAL_MASTERY,
                null,
                0.0,
                actionType);
        action.setICD(ICDType.None, ICDTag.None, 0.0);
        action.setLunarReactionType(
                AttackAction.LunarReactionType.BLOOM);
        action.setCountsAsSkillDmg(actionType == ActionType.SKILL);
        return action;
    }

    private static boolean requiresActiveSkillGeneration(HitKind kind) {
        return kind == HitKind.SKILL_PRESS
                || kind == HitKind.SKILL_HOLD
                || kind == HitKind.HOLD_LUNAR
                || kind == HitKind.SANCTUARY
                || kind == HitKind.C6_SANCTUARY_LUNAR;
    }

    private void queueHit(
            CombatSimulator simulator,
            PendingHit hit) {
        pendingHits.add(hit);
        scheduleHit(simulator, hit);
    }

    private void scheduleHit(
            CombatSimulator simulator,
            PendingHit hit) {
        long scheduledGeneration = eventGeneration;
        schedule(simulator, hit.time, activeSimulator -> {
            if (scheduledGeneration != eventGeneration
                    || !pendingHits.remove(hit)) {
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
        long scheduledGeneration = eventGeneration;
        schedule(simulator, command.time, activeSimulator -> {
            if (scheduledGeneration != eventGeneration
                    || !pendingCommands.remove(command)) {
                return;
            }
            switch (command.kind) {
                case SKILL_COOLDOWN:
                    if (command.generation == skillGeneration) {
                        markSkillUsed(
                                command.time,
                                activeSimulator.getApplicableBuffs(this));
                    }
                    break;
                case HOLD_CONSUME:
                    consumeHoldDew(activeSimulator, command);
                    break;
                case PARTICLE:
                    activeSimulator.getEnergyDistributor()
                            .distributeParticles(
                                    Element.DENDRO,
                                    getTalentValue(
                                            "Particle Count Expected", 1.3),
                                    ParticleType.PARTICLE);
                    break;
                case BURST_ENERGY:
                    if (command.generation == burstGeneration) {
                        spendBurstEnergy(command.time);
                    }
                    break;
                case BURST_ACTIVATE:
                    if (command.generation == burstGeneration) {
                        activateBurst(command.time);
                    }
                    break;
                default:
                    throw new IllegalStateException(
                            "Unknown Lauma command " + command.kind);
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
        C6_NORMAL_LUNAR,
        CHARGED,
        HIGH_PLUNGE,
        SKILL_PRESS,
        SKILL_HOLD,
        HOLD_LUNAR,
        SANCTUARY,
        C6_SANCTUARY_LUNAR
    }

    private enum CommandKind {
        SKILL_COOLDOWN,
        HOLD_CONSUME,
        PARTICLE,
        BURST_ENERGY,
        BURST_ACTIVATE
    }

    /** Immutable delayed Lauma damage packet. */
    private static final class PendingHit {
        private final double time;
        private final HitKind kind;
        private final int index;
        private final int value;
        private final long generation;

        private PendingHit(
                double time,
                HitKind kind,
                int index,
                int value,
                long generation) {
            this.time = time;
            this.kind = kind;
            this.index = index;
            this.value = value;
            this.generation = generation;
        }

        private PendingHit copy() {
            return new PendingHit(
                    time, kind, index, value, generation);
        }
    }

    /** Immutable delayed Lauma state command. */
    private static final class PendingCommand {
        private final double time;
        private final CommandKind kind;
        private final long generation;
        private final double castTime;

        private PendingCommand(
                double time,
                CommandKind kind,
                long generation,
                double castTime) {
            this.time = time;
            this.kind = kind;
            this.generation = generation;
            this.castTime = castTime;
        }

        private PendingCommand copy() {
            return new PendingCommand(
                    time, kind, generation, castTime);
        }
    }

    /** Immutable owner-bound snapshot of all mutable Lauma runtime state. */
    private static final class LaumaState implements State {
        private final Lauma owner;
        private final int normalAttackStep;
        private final long eventGeneration;
        private final long skillGeneration;
        private final long burstGeneration;
        private final double a1ActiveUntil;
        private final double skillShredUntil;
        private final double nextParticleAllowedTime;
        private final double nextC4AllowedTime;
        private final double burstActiveUntil;
        private final int storedMoonsong;
        private final double storedMoonsongUntil;
        private final boolean moonsongConvertedForBurst;
        private final int burstPaleHymn;
        private final int moonsongPaleHymn;
        private final int c6PaleHymn;
        private final double burstPaleHymnUntil;
        private final double moonsongPaleHymnUntil;
        private final double c6PaleHymnUntil;
        private final int c6SanctuaryCount;
        private final List<PendingHit> pendingHits;
        private final List<PendingCommand> pendingCommands;

        private LaumaState(
                Lauma owner,
                int normalAttackStep,
                long eventGeneration,
                long skillGeneration,
                long burstGeneration,
                double a1ActiveUntil,
                double skillShredUntil,
                double nextParticleAllowedTime,
                double nextC4AllowedTime,
                double burstActiveUntil,
                int storedMoonsong,
                double storedMoonsongUntil,
                boolean moonsongConvertedForBurst,
                int burstPaleHymn,
                int moonsongPaleHymn,
                int c6PaleHymn,
                double burstPaleHymnUntil,
                double moonsongPaleHymnUntil,
                double c6PaleHymnUntil,
                int c6SanctuaryCount,
                List<PendingHit> pendingHits,
                List<PendingCommand> pendingCommands) {
            this.owner = owner;
            this.normalAttackStep = normalAttackStep;
            this.eventGeneration = eventGeneration;
            this.skillGeneration = skillGeneration;
            this.burstGeneration = burstGeneration;
            this.a1ActiveUntil = a1ActiveUntil;
            this.skillShredUntil = skillShredUntil;
            this.nextParticleAllowedTime = nextParticleAllowedTime;
            this.nextC4AllowedTime = nextC4AllowedTime;
            this.burstActiveUntil = burstActiveUntil;
            this.storedMoonsong = storedMoonsong;
            this.storedMoonsongUntil = storedMoonsongUntil;
            this.moonsongConvertedForBurst =
                    moonsongConvertedForBurst;
            this.burstPaleHymn = burstPaleHymn;
            this.moonsongPaleHymn = moonsongPaleHymn;
            this.c6PaleHymn = c6PaleHymn;
            this.burstPaleHymnUntil = burstPaleHymnUntil;
            this.moonsongPaleHymnUntil = moonsongPaleHymnUntil;
            this.c6PaleHymnUntil = c6PaleHymnUntil;
            this.c6SanctuaryCount = c6SanctuaryCount;
            this.pendingHits = copyHits(pendingHits);
            this.pendingCommands = copyCommands(pendingCommands);
        }
    }
}

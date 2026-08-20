package model.character;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import mechanics.buff.Buff;
import mechanics.buff.BuffId;
import mechanics.buff.SimpleBuff;
import mechanics.data.TalentDataManager;
import mechanics.data.TalentDataSource;
import mechanics.energy.ParticleType;
import mechanics.reaction.ReactionResult;
import model.entity.ArtifactSet;
import model.entity.Character;
import model.entity.Enemy;
import model.entity.ReactionAwareCharacter;
import model.entity.SimulatorInitializedCharacterEffect;
import model.entity.SnapshotAwareCharacterEffect;
import model.entity.SwitchAwareCharacter;
import model.entity.TargetDependentTeamEffect;
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
 * Yumemizuki Mizuki's fixed-target Dreamdrifter offensive slice.
 *
 * <p>Lv. 90 catalyst basics, a repository-standard fixed high Plunge,
 * Dreamdrifter entry and cancellation, snapshot continuous attacks, four
 * one-particle generations, Burst activation damage, A1/A4, and representable
 * C1-C3/C5-C6 behavior follow pinned gcsim {@code ef41805d}. Dreamdrifter
 * continuous attacks launch first at frame 18, repeat every 45 frames, travel
 * for 30 frames, and share a typed 1.2-second time-only ICD.</p>
 *
 * <p>Dreamdrifter's ordinary and Stellar-Swirl bonuses, C1's separate flat
 * additions, and C6's reaction-specific CRIT values follow Genshin Optimizer
 * {@code d791814a}. Flat additions are converted into equivalent typed reaction
 * bonuses at level 90 without crossing the ordinary and Stellar channels. C6's
 * ordinary 30% expected Swirl bonus remains deterministic, while Stellar-Swirl
 * uses its dedicated CRIT Rate and CRIT DMG channels. Healing, current HP,
 * Snack pickup or selection, C4's Snack effects, multi-target geometry,
 * movement, random targeting, hitlag, stamina, low Plunge, and exploration
 * state are excluded instead of being approximated.</p>
 */
public final class YumemizukiMizuki extends Character implements
        SimulatorInitializedCharacterEffect,
        SnapshotAwareCharacterEffect,
        SwitchAwareCharacter,
        TargetDependentTeamEffect,
        ReactionAwareCharacter {
    private static final double FRAME = 1.0 / 60.0;
    private static final double EPSILON = 1e-9;
    private static final double LEVEL_90_SWIRL_BASE = 1446.85 * 0.6;
    private static final double LEVEL_90_STELLAR_SWIRL_BASE = 1446.85 * 0.75;
    private static final int[] NORMAL_IMPACT_FRAMES = { 17, 29, 47 };
    private static final int[] NORMAL_DURATION_FRAMES = { 34, 38, 98 };
    private static final double[] NORMAL_T9 = {
        0.888706, 0.797545, 1.213270
    };

    private CombatSimulator initializedSimulator;
    private int normalAttackStep;
    private long dreamGeneration;
    private boolean dreamDrifterActive;
    private double dreamDrifterExpirationTime = Double.NEGATIVE_INFINITY;
    private int dreamDrifterExtensionsRemaining;
    private double nextA1AllowedTime = Double.NEGATIVE_INFINITY;
    private double nextA4AllowedTime = Double.NEGATIVE_INFINITY;
    private int particleGenerationsRemaining;
    private double nextParticleAllowedTime = Double.NEGATIVE_INFINITY;
    private double c1AwaitingExpirationTime = Double.NEGATIVE_INFINITY;
    private double c1ElementalMastery;
    private double c2ElementalDamageBonus;
    private AttackAction resolvingAction;
    private HitKind resolvingHitKind;
    private List<PendingHit> pendingHits = new ArrayList<>();
    private List<PendingCommand> pendingCommands = new ArrayList<>();

    /** Constructs repository-default C6 Yumemizuki Mizuki. */
    public YumemizukiMizuki(Weapon weapon, ArtifactSet artifacts) {
        this(weapon, artifacts, TalentDataManager.getInstance(), 6);
    }

    /** Constructs Yumemizuki Mizuki at an explicit constellation. */
    public YumemizukiMizuki(
            Weapon weapon,
            ArtifactSet artifacts,
            int constellation) {
        this(weapon, artifacts, TalentDataManager.getInstance(),
                constellation);
    }

    /**
     * Constructs Yumemizuki Mizuki with injectable static talent data.
     *
     * @param weapon equipped catalyst, or {@code null}
     * @param artifacts equipped artifact set, or {@code null}
     * @param talentData static character-data source
     * @param constellation constellation level in {@code [0, 6]}
     */
    public YumemizukiMizuki(
            Weapon weapon,
            ArtifactSet artifacts,
            TalentDataSource talentData,
            int constellation) {
        super(talentData);
        if (constellation < 0 || constellation > 6) {
            throw new IllegalArgumentException(
                    "Yumemizuki Mizuki constellation must be between 0 and 6");
        }
        name = "Yumemizuki Mizuki";
        characterId = CharacterId.YUMEMIZUKI_MIZUKI;
        element = Element.ANEMO;
        this.weapon = weapon;
        this.artifacts = new ArtifactSet[] { artifacts };
        this.constellation = constellation;
        baseStats.set(StatType.BASE_HP,
                getTalentValue("Base HP", 12736.0));
        baseStats.set(StatType.BASE_ATK,
                getTalentValue("Base ATK", 215.0));
        baseStats.set(StatType.BASE_DEF,
                getTalentValue("Base DEF", 757.0));
        baseStats.add(StatType.ELEMENTAL_MASTERY,
                getTalentValue("Ascension Elemental Mastery", 115.2));
        setSkillCD(getTalentValue("Skill Cooldown", 15.0));
        setBurstCD(getTalentValue("Burst Cooldown", 15.0));
    }

    /** Binds damage and reaction callbacks to exactly one owning simulator. */
    @Override
    public void initializeForSimulator(CombatSimulator simulator) {
        if (simulator == null) {
            throw new IllegalArgumentException(
                    "Yumemizuki Mizuki simulator is required");
        }
        if (!simulator.getPartyMembers().contains(this)) {
            throw new IllegalArgumentException(
                    "Yumemizuki Mizuki must belong to the simulator party");
        }
        if (initializedSimulator != null
                && initializedSimulator != simulator) {
            throw new IllegalStateException(
                    "Yumemizuki Mizuki cannot be reused across simulators");
        }
        if (initializedSimulator == simulator) {
            return;
        }
        initializedSimulator = simulator;
        simulator.addDamageListener((actor, action, damage, time) ->
                handleAcceptedDamage(
                        simulator, actor, action, damage, time));
        simulator.addReactionListener(this);
    }

    /** Captures all mutable Dreamdrifter state and reconstructable delayed work. */
    @Override
    public State captureCharacterState() {
        return new MizukiState(
                this,
                normalAttackStep,
                dreamGeneration,
                dreamDrifterActive,
                dreamDrifterExpirationTime,
                dreamDrifterExtensionsRemaining,
                nextA1AllowedTime,
                nextA4AllowedTime,
                particleGenerationsRemaining,
                nextParticleAllowedTime,
                c1AwaitingExpirationTime,
                c1ElementalMastery,
                c2ElementalDamageBonus,
                pendingHits,
                pendingCommands);
    }

    /** Accepts state captured from this exact Mizuki instance only. */
    @Override
    public boolean acceptsCharacterState(State state) {
        return state instanceof MizukiState
                && ((MizukiState) state).owner == this;
    }

    /** Restores Mizuki-owned state and reconstructs surviving events once. */
    @Override
    public void restoreCharacterState(
            State state,
            CombatSimulator simulator) {
        if (!acceptsCharacterState(state)) {
            throw new IllegalArgumentException(
                    "Unexpected Yumemizuki Mizuki state");
        }
        initializeForSimulator(simulator);
        MizukiState restored = (MizukiState) state;
        normalAttackStep = restored.normalAttackStep;
        dreamGeneration = restored.dreamGeneration;
        dreamDrifterActive = restored.dreamDrifterActive;
        dreamDrifterExpirationTime = restored.dreamDrifterExpirationTime;
        dreamDrifterExtensionsRemaining =
                restored.dreamDrifterExtensionsRemaining;
        nextA1AllowedTime = restored.nextA1AllowedTime;
        nextA4AllowedTime = restored.nextA4AllowedTime;
        particleGenerationsRemaining =
                restored.particleGenerationsRemaining;
        nextParticleAllowedTime = restored.nextParticleAllowedTime;
        c1AwaitingExpirationTime = restored.c1AwaitingExpirationTime;
        c1ElementalMastery = restored.c1ElementalMastery;
        c2ElementalDamageBonus = restored.c2ElementalDamageBonus;
        pendingHits = copyHits(restored.pendingHits);
        pendingCommands = copyCommands(restored.pendingCommands);
        resolvingAction = null;
        resolvingHitKind = null;
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

    /** Returns Mizuki's 60-Energy Burst cost. */
    @Override
    public double getEnergyCost() {
        return getTalentValue("Energy Cost", 60.0);
    }

    /** Mizuki has no unconditional passive beyond ascension EM. */
    @Override
    public void applyPassive(StatsContainer stats) {
    }

    /** Allows a second Skill input to cancel an active Dreamdrifter state. */
    @Override
    public double getSkillCDRemaining(double currentTime) {
        if (isDreamDrifterActive(currentTime)) {
            return 0.0;
        }
        return super.getSkillCDRemaining(currentTime);
    }

    /** Returns whether Skill can start or cancel at the supplied time. */
    @Override
    public boolean canSkill(double currentTime) {
        return isDreamDrifterActive(currentTime)
                || super.canSkill(currentTime);
    }

    /** Ends Dreamdrifter and resets the Normal string on field exit. */
    @Override
    public void onSwitchOut(CombatSimulator simulator) {
        normalAttackStep = 0;
        endDreamDrifter();
    }

    /** Resets the catalyst Normal string on field entry. */
    @Override
    public void onSwitchIn(CombatSimulator simulator) {
        normalAttackStep = 0;
    }

    /** Returns whether the half-open Dreamdrifter window is active. */
    public boolean isDreamDrifterActive(double currentTime) {
        return dreamDrifterActive
                && currentTime + EPSILON < dreamDrifterExpirationTime;
    }

    /** Returns the current Dreamdrifter expiration time in seconds. */
    public double getDreamDrifterExpirationTime() {
        return dreamDrifterExpirationTime;
    }

    /** Returns unused A1 extensions in the current Dreamdrifter state. */
    public int getDreamDrifterExtensionsRemaining() {
        return dreamDrifterExtensionsRemaining;
    }

    /** Returns remaining Skill particle generations. */
    public int getParticleGenerationsRemaining() {
        return particleGenerationsRemaining;
    }

    /** Returns whether the fixed target currently carries C1's awaiting mark. */
    public boolean isC1AwaitingActive(double currentTime) {
        return currentTime + EPSILON < c1AwaitingExpirationTime;
    }

    /** Returns the cast-time C2 elemental damage bonus. */
    public double getC2ElementalDamageBonus() {
        return c2ElementalDamageBonus;
    }

    /** Returns the number of unresolved Mizuki-owned hits. */
    public int getPendingHitCount() {
        return pendingHits.size();
    }

    /** Reports that healing and current-player-HP logic are unavailable. */
    public boolean isHealingAndCurrentHpRepresented() {
        return false;
    }

    /** Reports that Snack pickup, selection, shockwaves, and C4 are unavailable. */
    public boolean isSnackSystemRepresented() {
        return false;
    }

    /** Reports that multi-target placement and geometry are unavailable. */
    public boolean isMultiTargetGeometryRepresented() {
        return false;
    }

    /** Reports that movement and exploration state are unavailable. */
    public boolean isMovementAndExplorationRepresented() {
        return false;
    }

    /** Reports that random target selection is unavailable. */
    public boolean isRandomTargetingRepresented() {
        return false;
    }

    /** Reports that hitlag is unavailable. */
    public boolean isHitlagRepresented() {
        return false;
    }

    /** Reports that stamina and low-Plunge state are unavailable. */
    public boolean isStaminaAndLowPlungeRepresented() {
        return false;
    }

    /** Dispatches Mizuki's represented typed action set. */
    @Override
    public void onAction(
            CharacterActionRequest request,
            CombatSimulator simulator) {
        if (request == null) {
            throw new IllegalArgumentException(
                    "Yumemizuki Mizuki action is required");
        }
        initializeForSimulator(simulator);
        if (request.getKey() == CharacterActionKey.SKILL
                && request.getSkillMode() != SkillActionMode.PRESS) {
            throw new IllegalArgumentException(
                    "Yumemizuki Mizuki supports Press Skill only");
        }
        boolean dreamActive = isDreamDrifterActive(
                simulator.getCurrentTime());
        if (dreamActive
                && request.getKey() != CharacterActionKey.SKILL
                && request.getKey() != CharacterActionKey.BURST) {
            throw new IllegalStateException(
                    "Dreamdrifter permits only Burst, Skill cancel, or switching");
        }
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
                if (dreamActive) {
                    cancelDreamDrifter(simulator);
                } else {
                    aisaUtamakuraPilgrimage(simulator);
                }
                break;
            case BURST:
                anrakuSecretSpringTherapy(simulator);
                break;
            default:
                throw new IllegalArgumentException(
                        "Unsupported action for Yumemizuki Mizuki: "
                                + request.getKey());
        }
    }

    /** Extends A1 and consumes C1 for ordinary or typed Stellar-Swirl only. */
    @Override
    public void onReaction(
            ReactionResult result,
            Character source,
            double time,
            CombatSimulator simulator) {
        if (simulator != initializedSimulator
                || result == null
                || !isSupportedSwirl(result.getKind())) {
            return;
        }
        boolean partySource = source != null
                && initializedSimulator.getPartyMembers().contains(source);
        if (partySource
                && isC1AwaitingActive(time)
                && result.getTransformDamage() > 0.0) {
            c1AwaitingExpirationTime = Double.NEGATIVE_INFINITY;
        }
        if (source != this
                || !isDreamDrifterActive(time)
                || dreamDrifterExtensionsRemaining <= 0
                || time + EPSILON < nextA1AllowedTime) {
            return;
        }
        nextA1AllowedTime = time
                + getTalentValue("A1 Extension ICD", 0.3);
        dreamDrifterExtensionsRemaining--;
        dreamDrifterExpirationTime += getTalentValue(
                "A1 Duration Extension", 2.5);
        queueCommand(simulator, new PendingCommand(
                dreamDrifterExpirationTime,
                CommandKind.DREAM_EXPIRE,
                dreamGeneration));
    }

    /**
     * Applies live Dreamdrifter Swirl support and represented constellations.
     *
     * <p>C1 remains on the fixed target for its own three-second lifetime even
     * after Dreamdrifter ends. All other contributions require the active
     * Dreamdrifter window.</p>
     */
    @Override
    public void applyTargetDependentTeamStats(
            StatsContainer stats,
            Character attacker,
            Enemy target,
            AttackAction action,
            double currentTime) {
        if (initializedSimulator == null
                || stats == null
                || attacker == null
                || target == null
                || action == null
                || !initializedSimulator.getPartyMembers().contains(attacker)) {
            return;
        }
        if (isDreamDrifterActive(currentTime)) {
            double mizukiEm = captureLiveStats(currentTime).get(
                    StatType.ELEMENTAL_MASTERY);
            stats.add(StatType.SWIRL_DMG_BONUS,
                    skillValue(
                            "Dreamdrifter Swirl Bonus",
                            "Dreamdrifter Swirl Bonus C3",
                            0.0042,
                            0.0051) * mizukiEm);
            stats.add(StatType.STELLAR_SWIRL_DMG_BONUS,
                    skillValue(
                            "Dreamdrifter Stellar-Swirl Bonus",
                            "Dreamdrifter Stellar-Swirl Bonus C3",
                            0.00042,
                            0.00051) * mizukiEm);
            if (constellation >= 2 && attacker != this) {
                stats.add(StatType.PYRO_DMG_BONUS,
                        c2ElementalDamageBonus);
                stats.add(StatType.HYDRO_DMG_BONUS,
                        c2ElementalDamageBonus);
                stats.add(StatType.ELECTRO_DMG_BONUS,
                        c2ElementalDamageBonus);
                stats.add(StatType.CRYO_DMG_BONUS,
                        c2ElementalDamageBonus);
            }
            if (constellation >= 6) {
                stats.add(StatType.SWIRL_DMG_BONUS,
                        getTalentValue("C6 Expected Swirl Bonus", 0.30));
                stats.add(StatType.STELLAR_SWIRL_CRIT_RATE,
                        getTalentValue(
                                "C6 Stellar-Swirl CRIT Rate", 0.10));
                stats.add(StatType.STELLAR_SWIRL_CRIT_DMG,
                        getTalentValue(
                                "C6 Stellar-Swirl CRIT DMG", 0.20));
            }
        }
        if (constellation >= 1 && isC1AwaitingActive(currentTime)) {
            stats.add(StatType.SWIRL_DMG_BONUS,
                    getTalentValue("C1 EM Flat Multiplier", 11.0)
                            * c1ElementalMastery
                            / LEVEL_90_SWIRL_BASE);
            double stellarBaseSection = Math.max(
                    EPSILON,
                    1.0 + stats.get(
                            StatType.STELLAR_SWIRL_BASE_DMG_BONUS));
            stats.add(StatType.STELLAR_SWIRL_DMG_BONUS,
                    getTalentValue(
                            "C1 Stellar-Swirl EM Flat Multiplier", 5.5)
                            * c1ElementalMastery
                            / (LEVEL_90_STELLAR_SWIRL_BASE
                                    * stellarBaseSection));
        }
    }

    private static boolean isSupportedSwirl(ReactionResult.Kind kind) {
        return kind == ReactionResult.Kind.SWIRL
                || kind == ReactionResult.Kind.STELLAR_SWIRL;
    }

    private void normalAttack(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        int step = normalAttackStep;
        queueHit(simulator, new PendingHit(
                castTime + NORMAL_IMPACT_FRAMES[step] * FRAME,
                HitKind.NORMAL,
                step,
                null));
        normalAttackStep = (normalAttackStep + 1) % NORMAL_T9.length;
        simulator.advanceTime(NORMAL_DURATION_FRAMES[step] * FRAME);
    }

    private void chargedAttack(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        queueHit(simulator, new PendingHit(
                castTime + getTalentValue(
                        "Charged Hit Frames", 39.0) * FRAME,
                HitKind.CHARGED,
                0,
                null));
        simulator.advanceTime(getTalentValue(
                "Charged Duration Frames", 81.0) * FRAME);
    }

    private void highPlunge(CombatSimulator simulator) {
        queueHit(simulator, new PendingHit(
                simulator.getCurrentTime(),
                HitKind.HIGH_PLUNGE,
                0,
                null));
        simulator.advanceTime(getTalentValue(
                "High Plunge Duration Frames", 60.0) * FRAME);
    }

    private void aisaUtamakuraPilgrimage(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        long generation = ++dreamGeneration;
        dreamDrifterActive = true;
        dreamDrifterExpirationTime = castTime
                + getTalentValue("Dreamdrifter Duration", 5.0);
        dreamDrifterExtensionsRemaining = 2;
        nextA1AllowedTime = Double.NEGATIVE_INFINITY;
        particleGenerationsRemaining = (int) getTalentValue(
                "Particle Generation Limit", 4.0);
        nextParticleAllowedTime = Double.NEGATIVE_INFINITY;
        StatsContainer cloudSnapshot = captureLiveStats(castTime);
        c2ElementalDamageBonus = constellation >= 2
                ? cloudSnapshot.get(StatType.ELEMENTAL_MASTERY)
                        * getTalentValue("C2 EM Ratio", 0.0004)
                : 0.0;
        queueHit(simulator, new PendingHit(
                castTime + getTalentValue(
                        "Skill Activation Hit Frames", 2.0) * FRAME,
                HitKind.SKILL_INITIAL,
                0,
                null));
        queueCommand(simulator, new PendingCommand(
                castTime + getTalentValue(
                        "Skill Cooldown Delay Frames", 23.0) * FRAME,
                CommandKind.SKILL_COOLDOWN,
                0L));
        queueCommand(simulator, new PendingCommand(
                castTime + getTalentValue(
                        "Cloud First Launch Frames", 18.0) * FRAME,
                CommandKind.CLOUD_LAUNCH,
                generation,
                cloudSnapshot));
        queueCommand(simulator, new PendingCommand(
                dreamDrifterExpirationTime,
                CommandKind.DREAM_EXPIRE,
                generation));
        if (constellation >= 1) {
            queueCommand(simulator, new PendingCommand(
                    castTime + getTalentValue(
                            "C1 Initial Delay Frames", 4.0) * FRAME,
                    CommandKind.C1_APPLY,
                    generation));
        }
        simulator.advanceTime(getTalentValue(
                "Skill Duration Frames", 50.0) * FRAME);
    }

    private void cancelDreamDrifter(CombatSimulator simulator) {
        endDreamDrifter();
        simulator.advanceTime(getTalentValue(
                "Skill Cancel Duration Frames", 50.0) * FRAME);
    }

    private void anrakuSecretSpringTherapy(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        queueCommand(simulator, new PendingCommand(
                castTime + getTalentValue(
                        "Burst Cooldown Delay Frames", 1.0) * FRAME,
                CommandKind.BURST_COOLDOWN,
                0L));
        queueCommand(simulator, new PendingCommand(
                castTime + getTalentValue(
                        "Burst Energy Delay Frames", 4.0) * FRAME,
                CommandKind.BURST_ENERGY,
                0L));
        queueHit(simulator, new PendingHit(
                castTime + getTalentValue(
                        "Burst Hit Frames", 93.0) * FRAME,
                HitKind.BURST_INITIAL,
                0,
                null));
        simulator.advanceTime(getTalentValue(
                "Burst Duration Frames", 94.0) * FRAME);
    }

    private void launchCloud(
            CombatSimulator simulator,
            PendingCommand command) {
        double currentTime = simulator.getCurrentTime();
        if (command.generation != dreamGeneration
                || !isDreamDrifterActive(currentTime)) {
            return;
        }
        queueHit(simulator, new PendingHit(
                currentTime + getTalentValue(
                        "Cloud Travel Frames", 30.0) * FRAME,
                HitKind.CLOUD,
                0,
                command.snapshot));
        queueCommand(simulator, new PendingCommand(
                currentTime + getTalentValue(
                        "Cloud Launch Interval Frames", 45.0) * FRAME,
                CommandKind.CLOUD_LAUNCH,
                command.generation,
                command.snapshot));
    }

    private void applyC1Awaiting(
            CombatSimulator simulator,
            long generation) {
        double currentTime = simulator.getCurrentTime();
        if (constellation < 1
                || generation != dreamGeneration
                || !isDreamDrifterActive(currentTime)) {
            return;
        }
        if (simulator.getEnemy() != null) {
            c1ElementalMastery = captureLiveStats(currentTime).get(
                    StatType.ELEMENTAL_MASTERY);
            c1AwaitingExpirationTime = currentTime
                    + getTalentValue("C1 Awaiting Duration", 3.0);
        }
        queueCommand(simulator, new PendingCommand(
                currentTime + getTalentValue(
                        "C1 Application Interval", 3.5),
                CommandKind.C1_APPLY,
                generation));
    }

    private void endDreamDrifter() {
        if (!dreamDrifterActive) {
            return;
        }
        dreamDrifterActive = false;
        dreamDrifterExpirationTime = Double.NEGATIVE_INFINITY;
        dreamDrifterExtensionsRemaining = 0;
        c2ElementalDamageBonus = 0.0;
        dreamGeneration++;
    }

    private void handleAcceptedDamage(
            CombatSimulator simulator,
            Character actor,
            AttackAction action,
            double damage,
            double time) {
        if (actor != this
                && damage > 0.0
                && isDreamDrifterActive(time)
                && time + EPSILON >= nextA4AllowedTime
                && isA4Element(action.getElement())) {
            nextA4AllowedTime = time
                    + getTalentValue("A4 Trigger ICD", 0.3);
            removeBuff(BuffId.YUMEMIZUKI_MIZUKI_A4_ELEMENTAL_MASTERY);
            addBuff(new SimpleBuff(
                    "Yumemizuki Mizuki Thoughts by Day Bring Dreams by Night",
                    BuffId.YUMEMIZUKI_MIZUKI_A4_ELEMENTAL_MASTERY,
                    getTalentValue("A4 Duration", 4.0),
                    time,
                    stats -> stats.add(
                            StatType.ELEMENTAL_MASTERY,
                            getTalentValue("A4 Elemental Mastery", 100.0))));
        }
        if (actor != this
                || action != resolvingAction
                || damage <= 0.0
                || (resolvingHitKind != HitKind.SKILL_INITIAL
                        && resolvingHitKind != HitKind.CLOUD)
                || particleGenerationsRemaining <= 0
                || time + EPSILON < nextParticleAllowedTime) {
            return;
        }
        particleGenerationsRemaining--;
        nextParticleAllowedTime = time
                + getTalentValue("Particle Generation ICD", 0.5);
        queueCommand(simulator, new PendingCommand(
                time + getTalentValue(
                        "Particle Travel Frames", 100.0) * FRAME,
                CommandKind.PARTICLE,
                0L));
    }

    private boolean isA4Element(Element attackElement) {
        return attackElement == Element.PYRO
                || attackElement == Element.HYDRO
                || attackElement == Element.ELECTRO
                || attackElement == Element.CRYO;
    }

    private void resolveHit(
            CombatSimulator simulator,
            PendingHit hit) {
        switch (hit.kind) {
            case NORMAL:
                performHit(
                        simulator,
                        hit,
                        "Pure Heart, Pure Dreams N" + (hit.index + 1),
                        getTalentValue(
                                "N" + (hit.index + 1),
                                NORMAL_T9[hit.index]),
                        StatType.NORMAL_ATTACK_DMG_BONUS,
                        ActionType.NORMAL,
                        ICDType.Standard,
                        ICDTag.NormalAttack);
                break;
            case CHARGED:
                performHit(
                        simulator,
                        hit,
                        "Pure Heart, Pure Dreams Charged Attack",
                        getTalentValue("Charged Attack", 2.21),
                        StatType.CHARGED_ATTACK_DMG_BONUS,
                        ActionType.CHARGE,
                        ICDType.None,
                        ICDTag.None);
                break;
            case HIGH_PLUNGE:
                performHit(
                        simulator,
                        hit,
                        "Pure Heart, Pure Dreams High Plunge",
                        getTalentValue("High Plunge", 2.607632),
                        StatType.PLUNGING_ATTACK_DMG_BONUS,
                        ActionType.PLUNGE,
                        ICDType.None,
                        ICDTag.None);
                break;
            case SKILL_INITIAL:
                performHit(
                        simulator,
                        hit,
                        "Aisa Utamakura Pilgrimage",
                        skillValue(
                                "Skill Activation Damage",
                                "Skill Activation Damage C3",
                                0.981648,
                                1.154880),
                        StatType.SKILL_DMG_BONUS,
                        ActionType.SKILL,
                        ICDType.None,
                        ICDTag.None);
                break;
            case CLOUD:
                performHit(
                        simulator,
                        hit,
                        "Dreamdrifter Continuous Attack",
                        skillValue(
                                "Dreamdrifter Continuous Damage",
                                "Dreamdrifter Continuous Damage C3",
                                0.763504,
                                0.898240),
                        StatType.SKILL_DMG_BONUS,
                        ActionType.SKILL,
                        ICDType.YumemizukiMizukiDreamdrifter,
                        ICDTag.YumemizukiMizuki_Dreamdrifter);
                break;
            case BURST_INITIAL:
                performHit(
                        simulator,
                        hit,
                        "Anraku Secret Spring Therapy",
                        burstValue(
                                "Burst Activation Damage",
                                "Burst Activation Damage C5",
                                1.599360,
                                1.881600),
                        StatType.BURST_DMG_BONUS,
                        ActionType.BURST,
                        ICDType.None,
                        ICDTag.None);
                break;
            default:
                throw new IllegalStateException(
                        "Unknown Yumemizuki Mizuki hit kind " + hit.kind);
        }
    }

    private void performHit(
            CombatSimulator simulator,
            PendingHit hit,
            String displayName,
            double multiplier,
            StatType bonusStat,
            ActionType actionType,
            ICDType icdType,
            ICDTag icdTag) {
        if (simulator.getEnemy() == null) {
            return;
        }
        AttackAction action = new AttackAction(
                displayName,
                multiplier,
                Element.ANEMO,
                StatType.BASE_ATK,
                bonusStat,
                0.0,
                actionType);
        action.setICD(icdType, icdTag, 1.0);
        action.setCountsAsSkillDmg(actionType == ActionType.SKILL);
        action.setCountsAsBurstDmg(actionType == ActionType.BURST);
        StatsContainer snapshot = hit.snapshot == null
                ? captureLiveStats(simulator.getCurrentTime())
                : hit.snapshot.merge(null);
        action.setStatSnapshot(snapshot);
        resolvingAction = action;
        resolvingHitKind = hit.kind;
        try {
            simulator.performActionWithoutTimeAdvance(characterId, action);
        } finally {
            resolvingAction = null;
            resolvingHitKind = null;
        }
    }

    private double skillValue(
            String baseKey,
            String c3Key,
            double talentNine,
            double talentTwelve) {
        return getTalentValue(
                constellation >= 3 ? c3Key : baseKey,
                constellation >= 3 ? talentTwelve : talentNine);
    }

    private double burstValue(
            String baseKey,
            String c5Key,
            double talentNine,
            double talentTwelve) {
        return getTalentValue(
                constellation >= 5 ? c5Key : baseKey,
                constellation >= 5 ? talentTwelve : talentNine);
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

    private void queueHit(
            CombatSimulator simulator,
            PendingHit hit) {
        pendingHits.add(hit);
        scheduleHit(simulator, hit);
    }

    private void scheduleHit(
            CombatSimulator simulator,
            PendingHit hit) {
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
                case DREAM_EXPIRE:
                    if (command.generation == dreamGeneration
                            && activeSimulator.getCurrentTime() + EPSILON
                                    >= dreamDrifterExpirationTime) {
                        endDreamDrifter();
                    }
                    break;
                case CLOUD_LAUNCH:
                    launchCloud(activeSimulator, command);
                    break;
                case C1_APPLY:
                    applyC1Awaiting(
                            activeSimulator, command.generation);
                    break;
                case PARTICLE:
                    activeSimulator.getEnergyDistributor()
                            .distributeParticles(
                                    Element.ANEMO,
                                    1.0,
                                    ParticleType.PARTICLE);
                    break;
                case BURST_COOLDOWN:
                    markBurstCooldownUsed(
                            activeSimulator.getCurrentTime(),
                            activeSimulator.getApplicableBuffs(this));
                    break;
                case BURST_ENERGY:
                    spendBurstEnergy(activeSimulator.getCurrentTime());
                    break;
                default:
                    throw new IllegalStateException(
                            "Unknown Yumemizuki Mizuki command "
                                    + command.kind);
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
                effect.accept(activeSimulator);
                finish();
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
        SKILL_INITIAL,
        CLOUD,
        BURST_INITIAL
    }

    private enum CommandKind {
        SKILL_COOLDOWN,
        DREAM_EXPIRE,
        CLOUD_LAUNCH,
        C1_APPLY,
        PARTICLE,
        BURST_COOLDOWN,
        BURST_ENERGY
    }

    private static final class PendingHit {
        private final double time;
        private final HitKind kind;
        private final int index;
        private final StatsContainer snapshot;

        private PendingHit(
                double time,
                HitKind kind,
                int index,
                StatsContainer snapshot) {
            this.time = time;
            this.kind = kind;
            this.index = index;
            this.snapshot = snapshot == null
                    ? null : snapshot.merge(null);
        }

        private PendingHit copy() {
            return new PendingHit(time, kind, index, snapshot);
        }
    }

    private static final class PendingCommand {
        private final double time;
        private final CommandKind kind;
        private final long generation;
        private final StatsContainer snapshot;

        private PendingCommand(
                double time,
                CommandKind kind,
                long generation) {
            this(time, kind, generation, null);
        }

        private PendingCommand(
                double time,
                CommandKind kind,
                long generation,
                StatsContainer snapshot) {
            this.time = time;
            this.kind = kind;
            this.generation = generation;
            this.snapshot = snapshot == null
                    ? null : snapshot.merge(null);
        }

        private PendingCommand copy() {
            return new PendingCommand(time, kind, generation, snapshot);
        }
    }

    private static final class MizukiState implements State {
        private final YumemizukiMizuki owner;
        private final int normalAttackStep;
        private final long dreamGeneration;
        private final boolean dreamDrifterActive;
        private final double dreamDrifterExpirationTime;
        private final int dreamDrifterExtensionsRemaining;
        private final double nextA1AllowedTime;
        private final double nextA4AllowedTime;
        private final int particleGenerationsRemaining;
        private final double nextParticleAllowedTime;
        private final double c1AwaitingExpirationTime;
        private final double c1ElementalMastery;
        private final double c2ElementalDamageBonus;
        private final List<PendingHit> pendingHits;
        private final List<PendingCommand> pendingCommands;

        private MizukiState(
                YumemizukiMizuki owner,
                int normalAttackStep,
                long dreamGeneration,
                boolean dreamDrifterActive,
                double dreamDrifterExpirationTime,
                int dreamDrifterExtensionsRemaining,
                double nextA1AllowedTime,
                double nextA4AllowedTime,
                int particleGenerationsRemaining,
                double nextParticleAllowedTime,
                double c1AwaitingExpirationTime,
                double c1ElementalMastery,
                double c2ElementalDamageBonus,
                List<PendingHit> pendingHits,
                List<PendingCommand> pendingCommands) {
            this.owner = owner;
            this.normalAttackStep = normalAttackStep;
            this.dreamGeneration = dreamGeneration;
            this.dreamDrifterActive = dreamDrifterActive;
            this.dreamDrifterExpirationTime =
                    dreamDrifterExpirationTime;
            this.dreamDrifterExtensionsRemaining =
                    dreamDrifterExtensionsRemaining;
            this.nextA1AllowedTime = nextA1AllowedTime;
            this.nextA4AllowedTime = nextA4AllowedTime;
            this.particleGenerationsRemaining =
                    particleGenerationsRemaining;
            this.nextParticleAllowedTime = nextParticleAllowedTime;
            this.c1AwaitingExpirationTime = c1AwaitingExpirationTime;
            this.c1ElementalMastery = c1ElementalMastery;
            this.c2ElementalDamageBonus = c2ElementalDamageBonus;
            this.pendingHits = copyHits(pendingHits);
            this.pendingCommands = copyCommands(pendingCommands);
        }
    }
}

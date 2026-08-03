package model.character;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import mechanics.buff.Buff;
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
import simulation.event.SimpleTimerEvent;

/**
 * Collei's stationary single-target offensive and reaction kit through C6.
 *
 * <p>This bounded slice follows pinned gcsim {@code ef41805d} timing and KQM
 * TCL {@code 80ba6241}. Floral Brush, Sprout, and Cuilein-Anbar retain their
 * sourced snapshots. Burst and Sprout schedule their maximum possible extended
 * cadence up front, so a reaction caused by the final currently active tick can
 * still expose the next extension tick.</p>
 *
 * <p>Projectile geometry, weak points, movement, collision, target grouping,
 * multi-target behavior, and defensive mechanics are intentionally excluded.</p>
 */
public final class Collei extends Character implements
        ReactionAwareCharacter,
        SimulatorInitializedCharacterEffect,
        SnapshotAwareCharacterEffect,
        SwitchAwareCharacter {
    private static final double FRAME = 1.0 / 60.0;
    private static final double EPSILON = 1e-9;
    private static final double PARTICLE_TRAVEL = 100.0 * FRAME;
    private static final int[] NORMAL_HITMARKS = { 26, 24, 32, 42 };
    private static final int[] NORMAL_DURATIONS = { 24, 25, 43, 65 };
    private static final double[] NORMAL_MULTIPLIERS = {
            0.80106, 0.78368, 0.99382, 1.24978
    };
    private static final int[] SKILL_HITMARKS = { 34, 138 };
    private static final int BURST_MAX_LEAPS = 18;
    private static final int SPROUT_MAX_TICKS = 4;

    private CombatSimulator initializedSimulator;
    private int normalAttackStep;
    private long skillGeneration;
    private long burstGeneration;
    private List<PendingHit> pendingHits = new ArrayList<>();
    private List<PendingCommand> pendingCommands = new ArrayList<>();
    private List<SkillInstance> skillInstances = new ArrayList<>();
    private long activeSproutGeneration;
    private double burstExpirationTime = Double.NEGATIVE_INFINITY;
    private int burstExtensionCount;
    private boolean burstExtensionActive;

    /** Constructs repository-default C6 Collei. */
    public Collei(Weapon weapon, ArtifactSet artifacts) {
        this(weapon, artifacts, TalentDataManager.getInstance(), 6);
    }

    /** Constructs Collei at an explicit constellation. */
    public Collei(Weapon weapon, ArtifactSet artifacts, int constellation) {
        this(weapon, artifacts, TalentDataManager.getInstance(), constellation);
    }

    /** Constructs Collei with injectable talent data and constellation state. */
    public Collei(
            Weapon weapon,
            ArtifactSet artifacts,
            TalentDataSource talentData,
            int constellation) {
        super(talentData);
        if (constellation < 0 || constellation > 6) {
            throw new IllegalArgumentException(
                    "Collei constellation must be between 0 and 6");
        }
        name = "Collei";
        characterId = CharacterId.COLLEI;
        element = Element.DENDRO;
        this.weapon = weapon;
        this.artifacts = new ArtifactSet[] { artifacts };
        this.constellation = constellation;
        baseStats.set(StatType.BASE_HP,
                getTalentValue("Base HP", 9787.0));
        baseStats.set(StatType.BASE_ATK,
                getTalentValue("Base ATK", 200.0));
        baseStats.set(StatType.BASE_DEF,
                getTalentValue("Base DEF", 601.0));
        baseStats.add(StatType.ATK_PERCENT,
                getTalentValue("Ascension ATK", 0.24));
        setSkillCD(12.0);
        setBurstCD(15.0);
    }

    /** Binds Collei's reaction listener and event ownership to one simulator. */
    @Override
    public void initializeForSimulator(CombatSimulator simulator) {
        if (simulator == null) {
            throw new IllegalArgumentException("Collei simulator is required");
        }
        if (initializedSimulator != null
                && initializedSimulator != simulator) {
            throw new IllegalStateException(
                    "Collei cannot be reused across CombatSimulator instances");
        }
        if (initializedSimulator == simulator) {
            return;
        }
        initializedSimulator = simulator;
        simulator.addReactionListener(this);
    }

    /** Captures all Collei-owned windows and reconstructible pending work. */
    @Override
    public State captureCharacterState() {
        return new ColleiState(
                normalAttackStep,
                skillGeneration,
                burstGeneration,
                pendingHits,
                pendingCommands,
                skillInstances,
                activeSproutGeneration,
                burstExpirationTime,
                burstExtensionCount,
                burstExtensionActive);
    }

    /** Reports whether a payload belongs to Collei. */
    @Override
    public boolean acceptsCharacterState(State state) {
        return state instanceof ColleiState;
    }

    /** Restores future events once, retaining exact-deadline work. */
    @Override
    public void restoreCharacterState(State state, CombatSimulator simulator) {
        if (!acceptsCharacterState(state)) {
            throw new IllegalArgumentException(
                    "Unexpected Collei character state");
        }
        initializeForSimulator(simulator);
        ColleiState restored = (ColleiState) state;
        normalAttackStep = restored.normalAttackStep;
        skillGeneration = restored.skillGeneration;
        burstGeneration = restored.burstGeneration;
        pendingHits = copyHits(restored.pendingHits);
        pendingCommands = copyCommands(restored.pendingCommands);
        skillInstances = copySkillInstances(restored.skillInstances);
        activeSproutGeneration = restored.activeSproutGeneration;
        burstExpirationTime = restored.burstExpirationTime;
        burstExtensionCount = restored.burstExtensionCount;
        burstExtensionActive = restored.burstExtensionActive;
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

    /** Returns Collei's 60-Energy Burst cost. */
    @Override
    public double getEnergyCost() {
        return getTalentValue("Energy Cost", 60.0);
    }

    /** Applies ATK ascension structurally and C1 ER only while off-field. */
    @Override
    public void applyPassive(StatsContainer stats) {
        if (constellation >= 1
                && initializedSimulator != null
                && initializedSimulator.getActiveCharacter() != this) {
            stats.add(StatType.ENERGY_RECHARGE,
                    getTalentValue("C1 Energy Recharge", 0.20));
        }
    }

    /** Resets the four-shot string when Collei leaves the field. */
    @Override
    public void onSwitchOut(CombatSimulator simulator) {
        normalAttackStep = 0;
    }

    /** Handles A1/C2 Sprout and A4 field extension reactions. */
    @Override
    public void onReaction(
            ReactionResult result,
            Character source,
            double time,
            CombatSimulator simulator) {
        if (simulator != initializedSimulator
                || result == null
                || !isDendroReaction(result.getKind())
                || source == null
                || !simulator.getPartyMembers().contains(source)) {
            return;
        }
        for (SkillInstance skill : skillInstances) {
            boolean inReactionWindow =
                    time <= skill.reactionWindowEnd + EPSILON;
            boolean inActiveSprout =
                    skill.generation == activeSproutGeneration
                    && time <= skill.sproutExpirationTime + EPSILON;
            if (inReactionWindow) {
                skill.reactionObserved = true;
            }
            if (constellation >= 2
                    && !skill.reactionExtensionObserved
                    && (inReactionWindow || inActiveSprout)) {
                skill.reactionExtensionObserved = true;
                if (Double.isFinite(skill.sproutExpirationTime)) {
                    skill.sproutExpirationTime += 180.0 * FRAME;
                }
            }
        }
        if (!burstExtensionActive
                || time + EPSILON >= burstExpirationTime
                || burstExtensionCount >= 3) {
            return;
        }
        burstExtensionCount++;
        burstExpirationTime += 60.0 * FRAME;
    }

    /** Dispatches Collei's supported typed offensive actions. */
    @Override
    public void onAction(
            CharacterActionRequest request,
            CombatSimulator simulator) {
        if (request == null) {
            throw new IllegalArgumentException("Collei action is required");
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
                fullyChargedShot(simulator);
                break;
            case PLUNGE:
                highPlunge(simulator);
                break;
            case SKILL:
                floralBrush(simulator);
                break;
            case BURST:
                trumpCardKitty(simulator);
                break;
            default:
                throw new IllegalArgumentException(
                        "Unsupported action for Collei: " + request.getKey());
        }
    }

    private void normalAttack(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        int step = normalAttackStep;
        queueCommand(simulator, new PendingCommand(
                castTime + (NORMAL_HITMARKS[step] - 10.0) * FRAME,
                CommandKind.NORMAL_RELEASE,
                step));
        normalAttackStep = (normalAttackStep + 1)
                % NORMAL_MULTIPLIERS.length;
        simulator.advanceTime(NORMAL_DURATIONS[step] * FRAME);
    }

    private void fullyChargedShot(CombatSimulator simulator) {
        queueCommand(simulator, new PendingCommand(
                simulator.getCurrentTime() + 86.0 * FRAME,
                CommandKind.CHARGED_SNAPSHOT,
                0L));
        simulator.advanceTime(96.0 * FRAME);
    }

    private void highPlunge(CombatSimulator simulator) {
        AttackAction plunge = attack(
                "Supplicant's Bowmanship High Plunge",
                getTalentValue("High Plunge", 2.607632),
                Element.PHYSICAL,
                StatType.PLUNGING_ATTACK_DMG_BONUS,
                ActionType.PLUNGE,
                ICDType.None,
                ICDTag.PlungeAttack,
                0.0);
        // No pinned Collei plunge frames exist; use the repository's 1 s bow policy.
        plunge.setAnimationDuration(1.0);
        simulator.performAction(characterId, plunge);
    }

    private void floralBrush(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        pruneSkillInstances(castTime);
        long generation = ++skillGeneration;
        skillInstances.add(new SkillInstance(generation));
        queueCommand(simulator, new PendingCommand(
                castTime + 20.0 * FRAME,
                CommandKind.SKILL_RELEASE,
                generation));
        simulator.advanceTime(68.0 * FRAME);
    }

    private void trumpCardKitty(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        long generation = ++burstGeneration;
        burstExpirationTime = Double.NEGATIVE_INFINITY;
        burstExtensionCount = 0;
        burstExtensionActive = false;
        if (constellation >= 4) {
            simulator.applyTeamBuff(new SimpleBuff(
                    "Collei C4 Gift of the Woods",
                    12.0,
                    castTime,
                    stats -> stats.add(
                            StatType.ELEMENTAL_MASTERY,
                            getTalentValue(
                                    "C4 Elemental Mastery", 60.0)))
                    .exclude(characterId)
                    .sourcedBy(characterId));
        }
        markBurstCooldownUsed(
                castTime, simulator.getApplicableBuffs(this));
        queueCommand(simulator, new PendingCommand(
                castTime + 7.0 * FRAME,
                CommandKind.BURST_ENERGY,
                generation));
        queueCommand(simulator, new PendingCommand(
                castTime + 25.0 * FRAME,
                CommandKind.BURST_FIELD,
                generation));
        simulator.advanceTime(67.0 * FRAME);
    }

    private void createBurstField(
            CombatSimulator simulator,
            long generation) {
        if (generation != burstGeneration) {
            return;
        }
        StatsContainer snapshot = captureActionSnapshot(simulator);
        double castTime = simulator.getCurrentTime() - 25.0 * FRAME;
        burstExpirationTime = simulator.getCurrentTime() + 378.0 * FRAME;
        for (int leap = 0; leap < BURST_MAX_LEAPS; leap++) {
            queueHit(simulator, new PendingHit(
                    castTime + (68.0 + 30.0 * leap) * FRAME,
                    HitKind.BURST_LEAP,
                    leap,
                    generation,
                    snapshot));
        }
        queueCommand(simulator, new PendingCommand(
                simulator.getCurrentTime(),
                CommandKind.BURST_ACTIVATE,
                generation));
        resolveHit(simulator, new PendingHit(
                simulator.getCurrentTime(),
                HitKind.BURST_EXPLOSION,
                0,
                generation,
                snapshot));
    }

    private void createSprout(
            CombatSimulator simulator,
            long generation) {
        SkillInstance skill = findSkillInstance(generation);
        if (skill == null || !skill.reactionObserved) {
            return;
        }
        SkillInstance previous = findSkillInstance(activeSproutGeneration);
        if (previous != null) {
            previous.sproutExpirationTime = Double.NEGATIVE_INFINITY;
        }
        activeSproutGeneration = generation;
        double startTime = simulator.getCurrentTime();
        skill.sproutExpirationTime = startTime
                + (skill.reactionExtensionObserved ? 360.0 : 180.0) * FRAME;
        StatsContainer snapshot = captureActionSnapshot(simulator);
        for (int tick = 0; tick < SPROUT_MAX_TICKS; tick++) {
            queueHit(simulator, new PendingHit(
                    startTime + (86.0 + 89.0 * tick) * FRAME,
                    HitKind.SPROUT,
                    tick,
                    generation,
                    snapshot));
        }
    }

    private void queueHit(CombatSimulator simulator, PendingHit hit) {
        pendingHits.add(hit);
        scheduleHit(simulator, hit);
    }

    private void scheduleHit(CombatSimulator simulator, PendingHit hit) {
        schedule(simulator, hit.time, activeSim -> {
            if (!pendingHits.remove(hit)) {
                return;
            }
            resolveHit(activeSim, hit);
            pruneSkillInstances(activeSim.getCurrentTime());
        });
    }

    private void resolveHit(CombatSimulator simulator, PendingHit hit) {
        AttackAction action;
        switch (hit.kind) {
            case NORMAL:
                action = attack(
                        "Supplicant's Bowmanship N" + (hit.index + 1),
                        getTalentValue("N" + (hit.index + 1),
                                NORMAL_MULTIPLIERS[hit.index]),
                        Element.PHYSICAL,
                        StatType.NORMAL_ATTACK_DMG_BONUS,
                        ActionType.NORMAL,
                        ICDType.None,
                        ICDTag.NormalAttack,
                        0.0);
                break;
            case CHARGED:
                action = attack(
                        "Supplicant's Bowmanship Fully Charged",
                        getTalentValue("Fully Charged Aimed Shot", 2.108),
                        Element.DENDRO,
                        StatType.CHARGED_ATTACK_DMG_BONUS,
                        ActionType.CHARGE,
                        ICDType.None,
                        ICDTag.ChargedAttack,
                        1.0);
                break;
            case SKILL:
                if (findSkillInstance(hit.generation) == null) {
                    return;
                }
                if (hit.index == 0 && constellation >= 6) {
                    queueCommand(simulator, new PendingCommand(
                            simulator.getCurrentTime(),
                            CommandKind.C6_CAPTURE,
                            hit.generation));
                }
                action = attack(
                        "Floral Brush",
                        getTalentValue(
                                constellation >= 3
                                        ? "Floral Brush C3"
                                        : "Floral Brush",
                                constellation >= 3 ? 3.024 : 2.5704),
                        Element.DENDRO,
                        StatType.SKILL_DMG_BONUS,
                        ActionType.SKILL,
                        ICDType.None,
                        ICDTag.ElementalSkill,
                        1.0);
                break;
            case C6:
                if (findSkillInstance(hit.generation) == null) {
                    return;
                }
                action = attack(
                        "Forest of Falling Arrows",
                        getTalentValue("C6 Follow-Up", 2.0),
                        Element.DENDRO,
                        null,
                        ActionType.OTHER,
                        ICDType.None,
                        ICDTag.None,
                        1.0);
                break;
            case BURST_EXPLOSION:
                action = burstAttack("Trump-Card Kitty Explosion", hit, true);
                break;
            case BURST_LEAP:
                if (hit.generation != burstGeneration
                        || hit.time > burstExpirationTime + EPSILON) {
                    return;
                }
                action = burstAttack("Trump-Card Kitty Leap", hit, false);
                break;
            case SPROUT:
                SkillInstance skill = findSkillInstance(hit.generation);
                if (skill == null
                        || hit.generation != activeSproutGeneration
                        || hit.time > skill.sproutExpirationTime + EPSILON) {
                    return;
                }
                action = attack(
                        "Floral Sidewinder Sprout",
                        getTalentValue("Sprout", 0.40),
                        Element.DENDRO,
                        StatType.SKILL_DMG_BONUS,
                        ActionType.SKILL,
                        ICDType.Standard,
                        ICDTag.Collei_Sprout,
                        1.0);
                break;
            default:
                throw new IllegalStateException("Unknown Collei hit kind");
        }
        if (hit.snapshot != null) {
            action.setStatSnapshot(hit.snapshot);
        }
        simulator.performActionWithoutTimeAdvance(characterId, action);
    }

    private AttackAction burstAttack(
            String displayName,
            PendingHit hit,
            boolean explosion) {
        String key;
        double defaultValue;
        if (explosion) {
            key = constellation >= 5 ? "Explosion C5" : "Explosion";
            defaultValue = constellation >= 5 ? 4.03648 : 3.431008;
        } else {
            key = constellation >= 5 ? "Leap C5" : "Leap";
            defaultValue = constellation >= 5 ? 0.86496 : 0.735216;
        }
        boolean appliesDendro = explosion
                || hit.index == 5
                || hit.index == 11
                || hit.index == 17;
        return attack(
                displayName,
                getTalentValue(key, defaultValue),
                Element.DENDRO,
                StatType.BURST_DMG_BONUS,
                ActionType.BURST,
                ICDType.None,
                ICDTag.ElementalBurst,
                appliesDendro ? 1.0 : 0.0);
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
        schedule(simulator, command.time, activeSim -> {
            if (!pendingCommands.remove(command)) {
                return;
            }
            switch (command.kind) {
                case NORMAL_RELEASE:
                    queueHit(activeSim, new PendingHit(
                            activeSim.getCurrentTime() + 10.0 * FRAME,
                            HitKind.NORMAL,
                            (int) command.generation,
                            0L,
                            captureActionSnapshot(activeSim)));
                    break;
                case CHARGED_SNAPSHOT:
                    queueHit(activeSim, new PendingHit(
                            activeSim.getCurrentTime() + 10.0 * FRAME,
                            HitKind.CHARGED,
                            0,
                            0L,
                            captureActionSnapshot(activeSim)));
                    break;
                case SKILL_RELEASE:
                    releaseFloralBrush(activeSim, command.generation);
                    break;
                case PARTICLE:
                    if (findSkillInstance(command.generation) != null) {
                        activeSim.getEnergyDistributor().distributeParticles(
                                Element.DENDRO, 3.0, ParticleType.PARTICLE);
                    }
                    break;
                case SKILL_RETURN:
                    createSprout(activeSim, command.generation);
                    break;
                case C6_CAPTURE:
                    if (findSkillInstance(command.generation) != null) {
                        queueHit(activeSim, new PendingHit(
                                activeSim.getCurrentTime() + 22.0 * FRAME,
                                HitKind.C6,
                                0,
                                command.generation,
                                captureActionSnapshot(activeSim)));
                    }
                    break;
                case BURST_ENERGY:
                    if (command.generation == burstGeneration) {
                        spendBurstEnergy(activeSim.getCurrentTime());
                    }
                    break;
                case BURST_FIELD:
                    createBurstField(activeSim, command.generation);
                    break;
                case BURST_ACTIVATE:
                    if (command.generation == burstGeneration) {
                        burstExtensionActive = true;
                    }
                    break;
                default:
                    throw new IllegalStateException(
                            "Unknown Collei command kind");
            }
            pruneSkillInstances(activeSim.getCurrentTime());
        });
    }

    private void releaseFloralBrush(
            CombatSimulator simulator,
            long generation) {
        SkillInstance skill = findSkillInstance(generation);
        if (skill == null) {
            return;
        }
        double releaseTime = simulator.getCurrentTime();
        skill.reactionWindowEnd = releaseTime + 137.0 * FRAME;
        skill.reactionObserved = constellation >= 2;
        StatsContainer snapshot = captureActionSnapshot(simulator);
        markSkillUsed(
                releaseTime, simulator.getApplicableBuffs(this));
        for (int index = 0; index < SKILL_HITMARKS.length; index++) {
            int hitmark = SKILL_HITMARKS[index];
            queueHit(simulator, new PendingHit(
                    releaseTime + (hitmark - 20.0) * FRAME,
                    HitKind.SKILL,
                    index,
                    generation,
                    snapshot));
        }
        queueCommand(simulator, new PendingCommand(
                releaseTime + (SKILL_HITMARKS[0] - 20.0) * FRAME
                        + PARTICLE_TRAVEL,
                CommandKind.PARTICLE,
                generation));
        queueCommand(simulator, new PendingCommand(
                skill.reactionWindowEnd,
                CommandKind.SKILL_RETURN,
                generation));
    }

    private SkillInstance findSkillInstance(long generation) {
        for (SkillInstance skill : skillInstances) {
            if (skill.generation == generation) {
                return skill;
            }
        }
        return null;
    }

    private void pruneSkillInstances(double currentTime) {
        skillInstances.removeIf(skill ->
                currentTime > skill.reactionWindowEnd + EPSILON
                && currentTime > skill.sproutExpirationTime + EPSILON
                && !hasPendingSkillWork(skill.generation));
        if (findSkillInstance(activeSproutGeneration) == null) {
            activeSproutGeneration = 0L;
        }
    }

    private boolean hasPendingSkillWork(long generation) {
        for (PendingHit hit : pendingHits) {
            if (hit.generation == generation
                    && (hit.kind == HitKind.SKILL
                            || hit.kind == HitKind.C6
                            || hit.kind == HitKind.SPROUT)) {
                return true;
            }
        }
        for (PendingCommand command : pendingCommands) {
            if (command.generation == generation
                    && (command.kind == CommandKind.SKILL_RELEASE
                            || command.kind == CommandKind.PARTICLE
                            || command.kind == CommandKind.SKILL_RETURN
                            || command.kind == CommandKind.C6_CAPTURE)) {
                return true;
            }
        }
        return false;
    }

    private StatsContainer captureActionSnapshot(CombatSimulator simulator) {
        double currentTime = simulator.getCurrentTime();
        StatsContainer stats = getEffectiveStats(currentTime);
        for (Buff buff : simulator.getApplicableBuffs(this)) {
            if (!buff.isExpired(currentTime)) {
                buff.apply(stats, currentTime);
            }
        }
        return stats;
    }

    private static boolean isDendroReaction(ReactionResult.Kind kind) {
        switch (kind) {
            case BURNING:
            case QUICKEN:
            case AGGRAVATE:
            case SPREAD:
            case BLOOM:
            case HYPERBLOOM:
            case BURGEON:
            case LUNAR_BLOOM:
                return true;
            default:
                return false;
        }
    }

    private static AttackAction attack(
            String name,
            double multiplier,
            Element element,
            StatType bonusStat,
            ActionType actionType,
            ICDType icdType,
            ICDTag icdTag,
            double gaugeUnits) {
        AttackAction action = new AttackAction(
                name,
                multiplier,
                element,
                StatType.BASE_ATK,
                bonusStat,
                0.0,
                actionType);
        action.setICD(icdType, icdTag, gaugeUnits);
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

    private static List<SkillInstance> copySkillInstances(
            List<SkillInstance> source) {
        List<SkillInstance> copy = new ArrayList<>();
        for (SkillInstance skill : source) {
            copy.add(skill.copy());
        }
        return copy;
    }

    private enum HitKind {
        NORMAL,
        CHARGED,
        SKILL,
        C6,
        BURST_EXPLOSION,
        BURST_LEAP,
        SPROUT
    }

    private enum CommandKind {
        NORMAL_RELEASE,
        CHARGED_SNAPSHOT,
        SKILL_RELEASE,
        PARTICLE,
        SKILL_RETURN,
        C6_CAPTURE,
        BURST_ENERGY,
        BURST_FIELD,
        BURST_ACTIVATE
    }

    /** Mutable state owned by one independently resolving Floral Brush cast. */
    private static final class SkillInstance {
        private final long generation;
        private double reactionWindowEnd = Double.NEGATIVE_INFINITY;
        private boolean reactionObserved;
        private boolean reactionExtensionObserved;
        private double sproutExpirationTime = Double.NEGATIVE_INFINITY;

        private SkillInstance(long generation) {
            this.generation = generation;
        }

        private SkillInstance copy() {
            SkillInstance copy = new SkillInstance(generation);
            copy.reactionWindowEnd = reactionWindowEnd;
            copy.reactionObserved = reactionObserved;
            copy.reactionExtensionObserved = reactionExtensionObserved;
            copy.sproutExpirationTime = sproutExpirationTime;
            return copy;
        }
    }

    /** Immutable delayed hit description. */
    private static final class PendingHit {
        private final double time;
        private final HitKind kind;
        private final int index;
        private final long generation;
        private final StatsContainer snapshot;

        private PendingHit(
                double time,
                HitKind kind,
                int index,
                long generation,
                StatsContainer snapshot) {
            this.time = time;
            this.kind = kind;
            this.index = index;
            this.generation = generation;
            this.snapshot = snapshot == null ? null : snapshot.merge(null);
        }

        private PendingHit copy() {
            return new PendingHit(time, kind, index, generation, snapshot);
        }
    }

    /** Immutable delayed non-damage event description. */
    private static final class PendingCommand {
        private final double time;
        private final CommandKind kind;
        private final long generation;

        private PendingCommand(
                double time,
                CommandKind kind,
                long generation) {
            this.time = time;
            this.kind = kind;
            this.generation = generation;
        }

        private PendingCommand copy() {
            return new PendingCommand(time, kind, generation);
        }
    }

    /** Immutable Collei-owned simulator state. */
    private static final class ColleiState implements State {
        private final int normalAttackStep;
        private final long skillGeneration;
        private final long burstGeneration;
        private final List<PendingHit> pendingHits;
        private final List<PendingCommand> pendingCommands;
        private final List<SkillInstance> skillInstances;
        private final long activeSproutGeneration;
        private final double burstExpirationTime;
        private final int burstExtensionCount;
        private final boolean burstExtensionActive;

        private ColleiState(
                int normalAttackStep,
                long skillGeneration,
                long burstGeneration,
                List<PendingHit> pendingHits,
                List<PendingCommand> pendingCommands,
                List<SkillInstance> skillInstances,
                long activeSproutGeneration,
                double burstExpirationTime,
                int burstExtensionCount,
                boolean burstExtensionActive) {
            this.normalAttackStep = normalAttackStep;
            this.skillGeneration = skillGeneration;
            this.burstGeneration = burstGeneration;
            this.pendingHits = copyHits(pendingHits);
            this.pendingCommands = copyCommands(pendingCommands);
            this.skillInstances = copySkillInstances(skillInstances);
            this.activeSproutGeneration = activeSproutGeneration;
            this.burstExpirationTime = burstExpirationTime;
            this.burstExtensionCount = burstExtensionCount;
            this.burstExtensionActive = burstExtensionActive;
        }
    }
}

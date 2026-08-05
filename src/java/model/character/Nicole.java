package model.character;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
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
 * Nicole's source-backed fixed-target Arcane Projection support slice.
 *
 * <p>Three Pyro catalyst Normals, Charged Attack, high Plunge, Revelation:
 * Uncreated Light, its snapshotted team ATK support, Revelation: Ladder of
 * Divine Ascent, and deterministic single-target Arcane Projections follow
 * pinned gcsim revision {@code ef41805d}. A1, A4, and the representable
 * offensive C1-C6 branches retain their frame gates, half-open durations,
 * captured projection actor, and owner-local rollback state.</p>
 *
 * <p>Shield durability and absorption, Hexerei roster detection and bonus
 * effects, projection geometry and multi-target placement, player HP,
 * movement, random targets, stamina, hitlag, low Plunge, and unsupported
 * defensive state fail closed instead of being approximated.</p>
 */
public final class Nicole extends Character implements
        SimulatorInitializedCharacterEffect,
        SnapshotAwareCharacterEffect,
        SwitchAwareCharacter,
        TargetDependentTeamEffect {
    private static final double FRAME = 1.0 / 60.0;
    private static final double EPSILON = 1e-9;
    private static final int[] NORMAL_HIT_FRAMES = { 17, 8, 38 };
    private static final int[] NORMAL_RECOVERY_FRAMES = { 25, 22, 52 };
    private static final double[] NORMAL_T9 = {
        0.598046, 0.503771, 0.785196
    };

    private CombatSimulator initializedSimulator;
    private int normalAttackStep;
    private long skillGeneration;
    private long burstGeneration;
    private long switchGeneration;
    private double graceExpirationTime = Double.NEGATIVE_INFINITY;
    private double graceAttackBonus;
    private double burstActivationTime = Double.NEGATIVE_INFINITY;
    private double burstExpirationTime = Double.NEGATIVE_INFINITY;
    private double nextProjectionAllowedTime = Double.NEGATIVE_INFINITY;
    private int projectionCount;
    private double nextC1AllowedTime = Double.NEGATIVE_INFINITY;
    private final Map<CharacterId, Double> a1Expirations =
            new EnumMap<>(CharacterId.class);
    private final Map<CharacterId, Long> c2Generations =
            new EnumMap<>(CharacterId.class);
    private final Map<CharacterId, Double> c2ShredExpirations =
            new EnumMap<>(CharacterId.class);
    private final Map<CharacterId, Double> c4NextAllowedTimes =
            new EnumMap<>(CharacterId.class);
    private final Map<CharacterId, Double> c4Expirations =
            new EnumMap<>(CharacterId.class);
    private final Map<CharacterId, Integer> c4Stacks =
            new EnumMap<>(CharacterId.class);
    private List<PendingHit> pendingHits = new ArrayList<>();
    private List<PendingCommand> pendingCommands = new ArrayList<>();

    /** Constructs repository-default C6 Nicole. */
    public Nicole(Weapon weapon, ArtifactSet artifacts) {
        this(weapon, artifacts, TalentDataManager.getInstance(), 6);
    }

    /** Constructs Nicole at an explicit constellation. */
    public Nicole(
            Weapon weapon,
            ArtifactSet artifacts,
            int constellation) {
        this(weapon, artifacts, TalentDataManager.getInstance(),
                constellation);
    }

    /**
     * Constructs Nicole with injectable static talent data.
     *
     * @param weapon equipped catalyst, or {@code null}
     * @param artifacts equipped artifact set, or {@code null}
     * @param talentData static character-data source
     * @param constellation constellation level in {@code [0, 6]}
     */
    public Nicole(
            Weapon weapon,
            ArtifactSet artifacts,
            TalentDataSource talentData,
            int constellation) {
        super(talentData);
        if (constellation < 0 || constellation > 6) {
            throw new IllegalArgumentException(
                    "Nicole constellation must be between 0 and 6");
        }
        name = "Nicole";
        characterId = CharacterId.NICOLE;
        element = Element.PYRO;
        this.weapon = weapon;
        this.artifacts = new ArtifactSet[] { artifacts };
        this.constellation = constellation;
        baseStats.set(StatType.BASE_HP,
                getTalentValue("Base HP", 10409.0));
        baseStats.set(StatType.BASE_ATK,
                getTalentValue("Base ATK", 342.0));
        baseStats.set(StatType.BASE_DEF,
                getTalentValue("Base DEF", 563.0));
        baseStats.add(StatType.ATK_PERCENT,
                getTalentValue("Ascension ATK", 0.288));
        setSkillCD(getTalentValue("Skill Cooldown", 16.0));
        setBurstCD(getTalentValue("Burst Cooldown", 15.0));
    }

    /** Binds Nicole's direct-hit observer to exactly one simulator. */
    @Override
    public void initializeForSimulator(CombatSimulator simulator) {
        if (simulator == null) {
            throw new IllegalArgumentException("Nicole simulator is required");
        }
        if (!simulator.getPartyMembers().contains(this)) {
            throw new IllegalArgumentException(
                    "Nicole must belong to the simulator party");
        }
        if (initializedSimulator != null
                && initializedSimulator != simulator) {
            throw new IllegalStateException(
                    "Nicole cannot be reused across simulators");
        }
        if (initializedSimulator == simulator) {
            return;
        }
        initializedSimulator = simulator;
        simulator.addDamageListener((actor, action, damage, time) ->
                handleAcceptedDamage(
                        simulator, actor, action, damage, time));
    }

    /** Captures every Nicole-owned gate, per-character status, and future event. */
    @Override
    public State captureCharacterState() {
        return new NicoleState(
                this,
                normalAttackStep,
                skillGeneration,
                burstGeneration,
                switchGeneration,
                graceExpirationTime,
                graceAttackBonus,
                burstActivationTime,
                burstExpirationTime,
                nextProjectionAllowedTime,
                projectionCount,
                nextC1AllowedTime,
                a1Expirations,
                c2Generations,
                c2ShredExpirations,
                c4NextAllowedTimes,
                c4Expirations,
                c4Stacks,
                pendingHits,
                pendingCommands);
    }

    /** Accepts state captured from this exact Nicole instance only. */
    @Override
    public boolean acceptsCharacterState(State state) {
        return state instanceof NicoleState
                && ((NicoleState) state).owner == this;
    }

    /** Restores Nicole-owned state and reconstructs each surviving event once. */
    @Override
    public void restoreCharacterState(
            State state,
            CombatSimulator simulator) {
        if (!acceptsCharacterState(state)) {
            throw new IllegalArgumentException("Unexpected Nicole state");
        }
        initializeForSimulator(simulator);
        NicoleState restored = (NicoleState) state;
        normalAttackStep = restored.normalAttackStep;
        skillGeneration = restored.skillGeneration;
        burstGeneration = restored.burstGeneration;
        switchGeneration = restored.switchGeneration;
        graceExpirationTime = restored.graceExpirationTime;
        graceAttackBonus = restored.graceAttackBonus;
        burstActivationTime = restored.burstActivationTime;
        burstExpirationTime = restored.burstExpirationTime;
        nextProjectionAllowedTime = restored.nextProjectionAllowedTime;
        projectionCount = restored.projectionCount;
        nextC1AllowedTime = restored.nextC1AllowedTime;
        replaceMap(a1Expirations, restored.a1Expirations);
        replaceMap(c2Generations, restored.c2Generations);
        replaceMap(c2ShredExpirations, restored.c2ShredExpirations);
        replaceMap(c4NextAllowedTimes, restored.c4NextAllowedTimes);
        replaceMap(c4Expirations, restored.c4Expirations);
        replaceMap(c4Stacks, restored.c4Stacks);
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

    /** Returns Nicole's source-backed 60-Energy Burst cost. */
    @Override
    public double getEnergyCost() {
        return getTalentValue("Energy Cost", 60.0);
    }

    /** Nicole has no unconditional represented passive stat. */
    @Override
    public void applyPassive(StatsContainer stats) {
    }

    /** Resets the Normal string and starts A1's non-Hexerei switch delay. */
    @Override
    public void onSwitchOut(CombatSimulator simulator) {
        normalAttackStep = 0;
        beginSwitchUpgrade(simulator, CharacterId.UNKNOWN);
    }

    /** Resets the Normal string and starts A1's non-Hexerei switch delay. */
    @Override
    public void onSwitchIn(CombatSimulator simulator) {
        normalAttackStep = 0;
        beginSwitchUpgrade(simulator, characterId);
    }

    /**
     * Applies A4 before-hit upgrades plus represented C2/C4/C6 hit modifiers.
     *
     * <p>This hook mirrors gcsim's ordered enemy-hit callbacks: an active
     * elemental hit can acquire Guidance first, then receive C2 resistance
     * reduction, C4 flat damage, and C6 DEF ignore on that same hit.</p>
     */
    @Override
    public void applyTargetDependentTeamStats(
            StatsContainer stats,
            Character attacker,
            model.entity.Enemy target,
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
        if (attacker == initializedSimulator.getActiveCharacter()
                && isElemental(action.getElement())) {
            double requestedDuration = constellation >= 6
                    ? Double.POSITIVE_INFINITY
                    : getTalentValue("A4 Self Duration", 8.0);
            upgradeGuidance(this, currentTime, requestedDuration);
        }
        if (isC2ShredActive(attacker.getCharacterId(), currentTime)) {
            StatType shredStat = resistanceShredStat(attacker.getElement());
            if (shredStat != null) {
                stats.add(shredStat,
                        getTalentValue("C2 Resistance Reduction", 0.25));
            }
        }
        if (isC4Eligible(action)
                && getC4StackCount(attacker.getCharacterId(), currentTime) > 0) {
            double nicoleAttack = captureLiveStats(currentTime).getTotalAtk();
            stats.add(StatType.FLAT_DMG_BONUS,
                    nicoleAttack
                            * getTalentValue("C4 Flat ATK Ratio", 0.7));
        }
        if (constellation >= 6
                && isGuidanceActive(attacker.getCharacterId(), currentTime)) {
            stats.add(StatType.DEF_IGNORE,
                    getTalentValue("C6 DEF Ignore", 0.4));
        }
    }

    /** Dispatches Nicole's represented fixed-target action set. */
    @Override
    public void onAction(
            CharacterActionRequest request,
            CombatSimulator simulator) {
        if (request == null) {
            throw new IllegalArgumentException("Nicole action is required");
        }
        initializeForSimulator(simulator);
        if (request.getKey() == CharacterActionKey.SKILL
                && request.getSkillMode() != SkillActionMode.PRESS) {
            throw new IllegalArgumentException(
                    "Nicole supports Press Skill only");
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
                revelationUncreatedLight(simulator);
                break;
            case BURST:
                revelationLadderOfDivineAscent(simulator);
                break;
            default:
                throw new IllegalArgumentException(
                        "Unsupported action for Nicole: "
                                + request.getKey());
        }
    }

    /** Returns the currently snapshotted Grace of Kenosis flat ATK bonus. */
    public double getGraceAttackBonus() {
        return graceAttackBonus;
    }

    /** Returns Grace of Kenosis's exact half-open expiration timestamp. */
    public double getGraceExpirationTime() {
        return graceExpirationTime;
    }

    /** Returns whether Silent Contemplation is active at {@code currentTime}. */
    public boolean isBurstActive(double currentTime) {
        return currentTime + EPSILON >= burstActivationTime
                && currentTime + EPSILON < burstExpirationTime;
    }

    /** Returns Arcane Projections consumed by the current Burst generation. */
    public int getProjectionCount() {
        return projectionCount;
    }

    /** Returns whether Guidance of Theosis is active for one character. */
    public boolean isGuidanceActive(
            CharacterId targetId,
            double currentTime) {
        return currentTime + EPSILON < a1Expirations.getOrDefault(
                targetId, Double.NEGATIVE_INFINITY);
    }

    /** Returns the represented C4 hit count remaining for one character. */
    public int getC4StackCount(
            CharacterId targetId,
            double currentTime) {
        if (currentTime + EPSILON >= c4Expirations.getOrDefault(
                targetId, Double.NEGATIVE_INFINITY)) {
            return 0;
        }
        return c4Stacks.getOrDefault(targetId, 0);
    }

    /** Returns the number of unresolved Nicole-owned impacts. */
    public int getPendingHitCount() {
        return pendingHits.size();
    }

    /** Reports that shield durability and absorption are excluded. */
    public boolean isShieldStateRepresented() {
        return false;
    }

    /** Reports that Hexerei roster detection and bonuses are excluded. */
    public boolean isHexereiRepresented() {
        return false;
    }

    /** Reports that projection placement and multi-target geometry are excluded. */
    public boolean isProjectionGeometryRepresented() {
        return false;
    }

    /** Reports that player current HP is excluded. */
    public boolean isPlayerHpRepresented() {
        return false;
    }

    /** Reports that movement is excluded. */
    public boolean isMovementRepresented() {
        return false;
    }

    /** Reports that random target selection is excluded. */
    public boolean isRandomTargetingRepresented() {
        return false;
    }

    /** Reports that stamina is excluded. */
    public boolean isStaminaRepresented() {
        return false;
    }

    /** Reports that hitlag is excluded. */
    public boolean isHitlagRepresented() {
        return false;
    }

    /** Reports that low Plunge is excluded. */
    public boolean isLowPlungeRepresented() {
        return false;
    }

    /** Reports that unsupported defensive state is excluded. */
    public boolean isUnsupportedDefensiveStateRepresented() {
        return false;
    }

    private void normalAttack(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        int step = normalAttackStep;
        queueHit(simulator, new PendingHit(
                castTime + NORMAL_HIT_FRAMES[step] * FRAME,
                HitKind.NORMAL,
                step,
                characterId,
                0L));
        normalAttackStep = (normalAttackStep + 1) % NORMAL_T9.length;
        simulator.advanceTime(NORMAL_RECOVERY_FRAMES[step] * FRAME);
    }

    private void chargedAttack(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        queueHit(simulator, new PendingHit(
                castTime + getTalentValue(
                        "Charged Hit Frame", 66.0) * FRAME,
                HitKind.CHARGED,
                0,
                characterId,
                0L));
        simulator.advanceTime(getTalentValue(
                "Charged Recovery Frames", 64.0) * FRAME);
    }

    private void highPlunge(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        queueHit(simulator, new PendingHit(
                castTime + getTalentValue(
                        "High Plunge Hit Frame", 46.0) * FRAME,
                HitKind.HIGH_PLUNGE,
                0,
                characterId,
                0L));
        simulator.advanceTime(getTalentValue(
                "High Plunge Recovery Frames", 67.0) * FRAME);
    }

    private void revelationUncreatedLight(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        long generation = ++skillGeneration;
        markSkillUsed(castTime, simulator.getApplicableBuffs(this));
        removeGraceAndGuidance(simulator);
        queueCommand(simulator, new PendingCommand(
                castTime + getTalentValue(
                        "Skill Buff Frame", 8.0) * FRAME,
                CommandKind.SKILL_BUFF,
                characterId,
                generation));
        queueHit(simulator, new PendingHit(
                castTime + getTalentValue(
                        "Skill Hit Frame", 9.0) * FRAME,
                HitKind.SKILL,
                0,
                characterId,
                generation));
        simulator.advanceTime(getTalentValue(
                "Skill Recovery Frames", 31.0) * FRAME);
    }

    private void revelationLadderOfDivineAscent(
            CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        long generation = ++burstGeneration;
        burstActivationTime = Double.NEGATIVE_INFINITY;
        burstExpirationTime = Double.NEGATIVE_INFINITY;
        projectionCount = 0;
        markBurstCooldownUsed(
                castTime, simulator.getApplicableBuffs(this));
        queueCommand(simulator, new PendingCommand(
                castTime + getTalentValue(
                        "Burst Energy Frame", 12.0) * FRAME,
                CommandKind.BURST_ENERGY,
                characterId,
                generation));
        queueHit(simulator, new PendingHit(
                castTime + getTalentValue(
                        "Burst Hit Frame", 108.0) * FRAME,
                HitKind.BURST_INITIAL,
                0,
                characterId,
                generation));
        queueCommand(simulator, new PendingCommand(
                castTime + getTalentValue(
                        "Burst Activation Frame", 109.0) * FRAME,
                CommandKind.BURST_ACTIVATE,
                characterId,
                generation));
        simulator.advanceTime(getTalentValue(
                "Burst Recovery Frames", 113.0) * FRAME);
    }

    private void removeGraceAndGuidance(CombatSimulator simulator) {
        simulator.removeTeamBuffsById(BuffId.NICOLE_GRACE_OF_KENOSIS);
        simulator.removeTeamBuffsById(BuffId.NICOLE_C2_GRACE_ATK);
        graceExpirationTime = Double.NEGATIVE_INFINITY;
        graceAttackBonus = 0.0;
        for (Character member : simulator.getPartyMembers()) {
            member.removeBuff(BuffId.NICOLE_GUIDANCE_OF_THEOSIS);
            invalidateC2Ticker(member.getCharacterId());
        }
        a1Expirations.clear();
    }

    private void applyGrace(CombatSimulator simulator) {
        double currentTime = simulator.getCurrentTime();
        double attack = captureLiveStats(currentTime).getTotalAtk();
        double ratio = getTalentValue(
                constellation >= 3
                        ? "Grace ATK Ratio C3" : "Grace ATK Ratio",
                constellation >= 3 ? 0.168 : 0.1425);
        double cap = getTalentValue(
                constellation >= 3
                        ? "Grace ATK Cap C3" : "Grace ATK Cap",
                constellation >= 3 ? 672.0 : 570.0);
        graceAttackBonus = Math.min(attack * ratio, cap);
        double duration = getTalentValue("Grace Duration", 20.0);
        graceExpirationTime = currentTime + duration;
        double snapshottedBonus = graceAttackBonus;
        simulator.applyTeamBuffNoStack(new SimpleBuff(
                "Grace of Kenosis",
                BuffId.NICOLE_GRACE_OF_KENOSIS,
                duration,
                currentTime,
                stats -> stats.add(
                        StatType.ATK_FLAT, snapshottedBonus))
                .sourcedBy(characterId));
        if (constellation >= 2) {
            double c2Attack = getTalentValue(
                    "C2 Team ATK Flat", 300.0);
            simulator.applyTeamBuffNoStack(new SimpleBuff(
                    "Grace of Kenosis C2",
                    BuffId.NICOLE_C2_GRACE_ATK,
                    duration,
                    currentTime,
                    stats -> stats.add(StatType.ATK_FLAT, c2Attack))
                    .sourcedBy(characterId));
        }
    }

    private void beginSwitchUpgrade(
            CombatSimulator simulator,
            CharacterId targetId) {
        long generation = ++switchGeneration;
        if (constellation < 6) {
            for (Character member : simulator.getPartyMembers()) {
                member.removeBuff(BuffId.NICOLE_GUIDANCE_OF_THEOSIS);
                a1Expirations.remove(member.getCharacterId());
                invalidateC2Ticker(member.getCharacterId());
            }
        }
        queueCommand(simulator, new PendingCommand(
                simulator.getCurrentTime()
                        + getTalentValue("A1 Switch Delay", 3.0),
                CommandKind.A1_SWITCH_UPGRADE,
                targetId,
                generation));
    }

    private void upgradeGuidance(
            Character target,
            double currentTime,
            double requestedDuration) {
        if (target == null
                || currentTime + EPSILON >= graceExpirationTime) {
            return;
        }
        double remainingGrace = graceExpirationTime - currentTime;
        double duration = Double.isInfinite(requestedDuration)
                ? remainingGrace
                : Math.min(requestedDuration, remainingGrace);
        if (duration <= EPSILON) {
            return;
        }
        double amount = getTalentValue("A1 ATK Flat", 300.0);
        target.removeBuff(BuffId.NICOLE_GUIDANCE_OF_THEOSIS);
        target.addBuff(new SimpleBuff(
                "Guidance of Theosis",
                BuffId.NICOLE_GUIDANCE_OF_THEOSIS,
                duration,
                currentTime,
                stats -> stats.add(StatType.ATK_FLAT, amount))
                .sourcedBy(characterId));
        a1Expirations.put(
                target.getCharacterId(), currentTime + duration);
        activateC2Shred(target, currentTime);
        activateC4(target, currentTime);
        if (constellation >= 6 && target == this) {
            for (Character member : initializedSimulator.getPartyMembers()) {
                if (member != this) {
                    upgradeGuidance(
                            member, currentTime, Double.POSITIVE_INFINITY);
                }
            }
        }
    }

    private void activateC2Shred(
            Character target,
            double currentTime) {
        if (constellation < 2) {
            return;
        }
        CharacterId targetId = target.getCharacterId();
        long generation = c2Generations.getOrDefault(targetId, 0L) + 1L;
        c2Generations.put(targetId, generation);
        refreshC2Shred(targetId, currentTime);
        queueCommand(initializedSimulator, new PendingCommand(
                currentTime + getTalentValue(
                        "C2 Refresh Interval", 0.3),
                CommandKind.C2_REFRESH,
                targetId,
                generation));
    }

    private void refreshC2Shred(
            CharacterId targetId,
            double currentTime) {
        c2ShredExpirations.put(
                targetId,
                currentTime + getTalentValue(
                        "C2 Resistance Duration", 1.0));
    }

    private boolean isC2ShredActive(
            CharacterId targetId,
            double currentTime) {
        return constellation >= 2
                && currentTime + EPSILON < c2ShredExpirations.getOrDefault(
                        targetId, Double.NEGATIVE_INFINITY);
    }

    private void invalidateC2Ticker(CharacterId targetId) {
        c2Generations.put(
                targetId,
                c2Generations.getOrDefault(targetId, 0L) + 1L);
    }

    private void activateC4(
            Character target,
            double currentTime) {
        if (constellation < 4) {
            return;
        }
        CharacterId targetId = target.getCharacterId();
        if (currentTime + EPSILON < c4NextAllowedTimes.getOrDefault(
                targetId, Double.NEGATIVE_INFINITY)) {
            return;
        }
        c4NextAllowedTimes.put(
                targetId,
                currentTime + getTalentValue(
                        "C4 Trigger Cooldown", 16.0));
        c4Expirations.put(
                targetId,
                currentTime + getTalentValue("C4 Duration", 20.0));
        c4Stacks.put(targetId,
                (int) getTalentValue("C4 Stack Count", 8.0));
    }

    private void handleAcceptedDamage(
            CombatSimulator simulator,
            Character actor,
            AttackAction action,
            double damage,
            double time) {
        if (simulator != initializedSimulator
                || actor == null
                || action == null
                || damage <= 0.0) {
            return;
        }
        if (actor == simulator.getActiveCharacter()) {
            triggerBurstProjection(simulator, actor, time);
            triggerC1Projection(simulator, actor, time);
        }
        if (isC4Eligible(action)
                && getC4StackCount(actor.getCharacterId(), time) > 0) {
            c4Stacks.put(
                    actor.getCharacterId(),
                    getC4StackCount(actor.getCharacterId(), time) - 1);
        }
    }

    private void triggerBurstProjection(
            CombatSimulator simulator,
            Character actor,
            double triggerTime) {
        if (!isBurstActive(triggerTime)
                || projectionCount >= (int) getTalentValue(
                        "Projection Cap", 4.0)
                || triggerTime + EPSILON < nextProjectionAllowedTime) {
            return;
        }
        nextProjectionAllowedTime = triggerTime + getTalentValue(
                "Projection Trigger Cooldown", 3.0);
        projectionCount++;
        queueHit(simulator, new PendingHit(
                triggerTime + getTalentValue(
                        "Projection Delay Frames", 30.0) * FRAME,
                HitKind.BURST_PROJECTION,
                projectionCount,
                actor.getCharacterId(),
                burstGeneration));
    }

    private void triggerC1Projection(
            CombatSimulator simulator,
            Character actor,
            double triggerTime) {
        if (constellation < 1
                || triggerTime + EPSILON < nextC1AllowedTime) {
            return;
        }
        nextC1AllowedTime = triggerTime + getTalentValue(
                "C1 Trigger Cooldown", 6.0);
        queueHit(simulator, new PendingHit(
                triggerTime + getTalentValue(
                        "Projection Delay Frames", 30.0) * FRAME,
                HitKind.C1_PROJECTION,
                0,
                actor.getCharacterId(),
                0L));
    }

    private void resolveHit(
            CombatSimulator simulator,
            PendingHit hit) {
        if (hit.kind == HitKind.SKILL
                && hit.generation != skillGeneration) {
            return;
        }
        if (hit.kind == HitKind.BURST_INITIAL
                && hit.generation != burstGeneration) {
            return;
        }
        Character actor = simulator.getCharacter(hit.actorId);
        if (actor == null || simulator.getEnemy() == null) {
            return;
        }
        switch (hit.kind) {
            case NORMAL:
                performHit(
                        simulator,
                        actor,
                        "Allegoria N" + (hit.index + 1),
                        getTalentValue(
                                "N" + (hit.index + 1),
                                NORMAL_T9[hit.index]),
                        Element.PYRO,
                        StatType.NORMAL_ATTACK_DMG_BONUS,
                        ActionType.NORMAL,
                        ICDType.Standard,
                        ICDTag.NormalAttack,
                        1.0);
                break;
            case CHARGED:
                performHit(
                        simulator,
                        actor,
                        "Allegoria Charged Attack",
                        getTalentValue("Charged Attack", 1.90944),
                        Element.PYRO,
                        StatType.CHARGED_ATTACK_DMG_BONUS,
                        ActionType.CHARGE,
                        ICDType.None,
                        ICDTag.ChargedAttack,
                        1.0);
                break;
            case HIGH_PLUNGE:
                performHit(
                        simulator,
                        actor,
                        "Allegoria High Plunge",
                        getTalentValue("High Plunge", 2.607632),
                        Element.PYRO,
                        StatType.PLUNGING_ATTACK_DMG_BONUS,
                        ActionType.PLUNGE,
                        ICDType.None,
                        ICDTag.PlungeAttack,
                        1.0);
                break;
            case SKILL:
                performHit(
                        simulator,
                        actor,
                        "Revelation: Uncreated Light",
                        skillMultiplier(),
                        Element.PYRO,
                        StatType.SKILL_DMG_BONUS,
                        ActionType.SKILL,
                        ICDType.None,
                        ICDTag.ElementalSkill,
                        1.0);
                queueCommand(simulator, new PendingCommand(
                        hit.time + getTalentValue(
                                "Particle Travel Frames", 100.0) * FRAME,
                        CommandKind.PARTICLE,
                        characterId,
                        hit.generation));
                break;
            case BURST_INITIAL:
                performHit(
                        simulator,
                        actor,
                        "Revelation: Ladder of Divine Ascent",
                        burstInitialMultiplier(),
                        Element.PYRO,
                        StatType.BURST_DMG_BONUS,
                        ActionType.BURST,
                        ICDType.None,
                        ICDTag.ElementalBurst,
                        1.0);
                break;
            case BURST_PROJECTION:
                performHit(
                        simulator,
                        actor,
                        "Arcane Projection " + hit.index,
                        burstProjectionMultiplier(),
                        actor.getElement(),
                        null,
                        ActionType.OTHER,
                        ICDType.None,
                        ICDTag.None,
                        0.0);
                break;
            case C1_PROJECTION:
                performHit(
                        simulator,
                        actor,
                        "Arcane Projection: Unity",
                        getTalentValue(
                                "C1 Projection Multiplier", 6.0),
                        actor.getElement(),
                        null,
                        ActionType.OTHER,
                        ICDType.None,
                        ICDTag.None,
                        0.0);
                break;
            default:
                throw new IllegalStateException("Unknown Nicole hit kind");
        }
    }

    private double skillMultiplier() {
        return getTalentValue(
                constellation >= 3
                        ? "Uncreated Light C3" : "Uncreated Light",
                constellation >= 3 ? 2.768 : 2.3528);
    }

    private double burstInitialMultiplier() {
        return getTalentValue(
                constellation >= 5
                        ? "Ladder Initial C5" : "Ladder Initial",
                constellation >= 5 ? 6.336 : 5.3856);
    }

    private double burstProjectionMultiplier() {
        return getTalentValue(
                constellation >= 5
                        ? "Arcane Projection C5" : "Arcane Projection",
                constellation >= 5 ? 2.016 : 1.71);
    }

    private static void performHit(
            CombatSimulator simulator,
            Character actor,
            String displayName,
            double multiplier,
            Element hitElement,
            StatType bonusStat,
            ActionType actionType,
            ICDType icdType,
            ICDTag icdTag,
            double gauge) {
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
        action.setCountsAsBurstDmg(actionType == ActionType.BURST);
        simulator.performActionWithoutTimeAdvance(
                actor.getCharacterId(), action);
    }

    private StatsContainer captureLiveStats(double currentTime) {
        StatsContainer stats = getEffectiveStats(currentTime);
        if (initializedSimulator != null) {
            for (Buff buff : initializedSimulator.getApplicableBuffs(this)) {
                if (!buff.isExpired(currentTime)) {
                    buff.apply(stats, currentTime);
                }
            }
        }
        return stats;
    }

    private static boolean isElemental(Element element) {
        return element == Element.PYRO
                || element == Element.HYDRO
                || element == Element.ELECTRO
                || element == Element.CRYO
                || element == Element.ANEMO
                || element == Element.GEO
                || element == Element.DENDRO;
    }

    private static StatType resistanceShredStat(Element element) {
        switch (element) {
            case PYRO:
                return StatType.PYRO_RES_SHRED;
            case HYDRO:
                return StatType.HYDRO_RES_SHRED;
            case ELECTRO:
                return StatType.ELECTRO_RES_SHRED;
            case CRYO:
                return StatType.CRYO_RES_SHRED;
            case ANEMO:
                return StatType.ANEMO_RES_SHRED;
            case GEO:
                return StatType.GEO_RES_SHRED;
            case DENDRO:
                return StatType.DENDRO_RES_SHRED;
            default:
                return null;
        }
    }

    private static boolean isC4Eligible(AttackAction action) {
        return action.getActionType() == ActionType.NORMAL
                || action.getActionType() == ActionType.CHARGE
                || action.getActionType() == ActionType.PLUNGE
                || action.getActionType() == ActionType.SKILL
                || action.getActionType() == ActionType.BURST;
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
                case SKILL_BUFF:
                    if (command.generation == skillGeneration) {
                        applyGrace(activeSimulator);
                    }
                    break;
                case PARTICLE:
                    if (command.generation == skillGeneration) {
                        activeSimulator.getEnergyDistributor()
                                .distributeParticles(
                                        Element.PYRO,
                                        getTalentValue(
                                                "Particle Count", 5.0),
                                        ParticleType.PARTICLE);
                    }
                    break;
                case BURST_ENERGY:
                    if (command.generation == burstGeneration) {
                        spendBurstEnergy(activeSimulator.getCurrentTime());
                    }
                    break;
                case BURST_ACTIVATE:
                    if (command.generation == burstGeneration) {
                        burstActivationTime = activeSimulator.getCurrentTime();
                        burstExpirationTime = burstActivationTime
                                + getTalentValue("Burst Duration", 20.0);
                        projectionCount = 0;
                    }
                    break;
                case A1_SWITCH_UPGRADE:
                    if (command.generation == switchGeneration) {
                        Character target = command.targetId == CharacterId.UNKNOWN
                                ? activeSimulator.getActiveCharacter()
                                : activeSimulator.getCharacter(command.targetId);
                        upgradeGuidance(
                                target,
                                activeSimulator.getCurrentTime(),
                                Double.POSITIVE_INFINITY);
                    }
                    break;
                case C2_REFRESH:
                    if (command.generation == c2Generations.getOrDefault(
                            command.targetId, Long.MIN_VALUE)
                            && isGuidanceActive(
                                    command.targetId,
                                    activeSimulator.getCurrentTime())) {
                        refreshC2Shred(
                                command.targetId,
                                activeSimulator.getCurrentTime());
                        queueCommand(activeSimulator, new PendingCommand(
                                activeSimulator.getCurrentTime()
                                        + getTalentValue(
                                                "C2 Refresh Interval", 0.3),
                                CommandKind.C2_REFRESH,
                                command.targetId,
                                command.generation));
                    }
                    break;
                default:
                    throw new IllegalStateException(
                            "Unknown Nicole command kind");
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

    private static <T> void replaceMap(
            Map<CharacterId, T> destination,
            Map<CharacterId, T> source) {
        destination.clear();
        destination.putAll(source);
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
        SKILL,
        BURST_INITIAL,
        BURST_PROJECTION,
        C1_PROJECTION
    }

    private enum CommandKind {
        SKILL_BUFF,
        PARTICLE,
        BURST_ENERGY,
        BURST_ACTIVATE,
        A1_SWITCH_UPGRADE,
        C2_REFRESH
    }

    /** Immutable delayed Nicole hit with captured actor and generation. */
    private static final class PendingHit {
        private final double time;
        private final HitKind kind;
        private final int index;
        private final CharacterId actorId;
        private final long generation;

        private PendingHit(
                double time,
                HitKind kind,
                int index,
                CharacterId actorId,
                long generation) {
            this.time = time;
            this.kind = kind;
            this.index = index;
            this.actorId = actorId;
            this.generation = generation;
        }

        private PendingHit copy() {
            return new PendingHit(
                    time, kind, index, actorId, generation);
        }
    }

    /** Immutable delayed Nicole state command. */
    private static final class PendingCommand {
        private final double time;
        private final CommandKind kind;
        private final CharacterId targetId;
        private final long generation;

        private PendingCommand(
                double time,
                CommandKind kind,
                CharacterId targetId,
                long generation) {
            this.time = time;
            this.kind = kind;
            this.targetId = targetId;
            this.generation = generation;
        }

        private PendingCommand copy() {
            return new PendingCommand(
                    time, kind, targetId, generation);
        }
    }

    /** Immutable snapshot of all mutable Nicole-owned simulator state. */
    private static final class NicoleState implements State {
        private final Nicole owner;
        private final int normalAttackStep;
        private final long skillGeneration;
        private final long burstGeneration;
        private final long switchGeneration;
        private final double graceExpirationTime;
        private final double graceAttackBonus;
        private final double burstActivationTime;
        private final double burstExpirationTime;
        private final double nextProjectionAllowedTime;
        private final int projectionCount;
        private final double nextC1AllowedTime;
        private final Map<CharacterId, Double> a1Expirations;
        private final Map<CharacterId, Long> c2Generations;
        private final Map<CharacterId, Double> c2ShredExpirations;
        private final Map<CharacterId, Double> c4NextAllowedTimes;
        private final Map<CharacterId, Double> c4Expirations;
        private final Map<CharacterId, Integer> c4Stacks;
        private final List<PendingHit> pendingHits;
        private final List<PendingCommand> pendingCommands;

        private NicoleState(
                Nicole owner,
                int normalAttackStep,
                long skillGeneration,
                long burstGeneration,
                long switchGeneration,
                double graceExpirationTime,
                double graceAttackBonus,
                double burstActivationTime,
                double burstExpirationTime,
                double nextProjectionAllowedTime,
                int projectionCount,
                double nextC1AllowedTime,
                Map<CharacterId, Double> a1Expirations,
                Map<CharacterId, Long> c2Generations,
                Map<CharacterId, Double> c2ShredExpirations,
                Map<CharacterId, Double> c4NextAllowedTimes,
                Map<CharacterId, Double> c4Expirations,
                Map<CharacterId, Integer> c4Stacks,
                List<PendingHit> pendingHits,
                List<PendingCommand> pendingCommands) {
            this.owner = owner;
            this.normalAttackStep = normalAttackStep;
            this.skillGeneration = skillGeneration;
            this.burstGeneration = burstGeneration;
            this.switchGeneration = switchGeneration;
            this.graceExpirationTime = graceExpirationTime;
            this.graceAttackBonus = graceAttackBonus;
            this.burstActivationTime = burstActivationTime;
            this.burstExpirationTime = burstExpirationTime;
            this.nextProjectionAllowedTime = nextProjectionAllowedTime;
            this.projectionCount = projectionCount;
            this.nextC1AllowedTime = nextC1AllowedTime;
            this.a1Expirations = new EnumMap<>(a1Expirations);
            this.c2Generations = new EnumMap<>(c2Generations);
            this.c2ShredExpirations = new EnumMap<>(c2ShredExpirations);
            this.c4NextAllowedTimes = new EnumMap<>(c4NextAllowedTimes);
            this.c4Expirations = new EnumMap<>(c4Expirations);
            this.c4Stacks = new EnumMap<>(c4Stacks);
            this.pendingHits = copyHits(pendingHits);
            this.pendingCommands = copyCommands(pendingCommands);
        }
    }
}

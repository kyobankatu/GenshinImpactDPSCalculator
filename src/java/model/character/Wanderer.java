package model.character;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

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
import simulation.action.SkillActionMode;
import simulation.event.SimpleTimerEvent;

/**
 * Wanderer's stationary fixed-target Windfavored offensive slice.
 *
 * <p>Normal, Charged, and High Plunge attacks, Skill entry, point depletion,
 * Windfavored multipliers, particles, five-hit Burst, and representable
 * C1/C2/C3/C5/C6 behavior follow pinned gcsim {@code ef41805d}. Release-stage
 * snapshots and delayed owner work are reconstructed by simulator rollback.</p>
 *
 * <p>A1 absorption is excluded because source-element selection is not exposed
 * by the fixed-target API. A4 is excluded because dash consumption and its
 * probability progression are not represented. Movement-specific point costs,
 * geometry, multi-target behavior, stamina, hitlag, low plunge, and defensive
 * behavior are also excluded rather than replaced with deterministic proxies.
 * High Plunge uses the repository's fixed one-second catalyst policy.</p>
 */
public final class Wanderer extends Character implements
        SimulatorInitializedCharacterEffect,
        SnapshotAwareCharacterEffect,
        SwitchAwareCharacter {
    private static final double FRAME = 1.0 / 60.0;
    private static final double EPSILON = 1e-9;
    private static final int[][] NORMAL_RELEASE_FRAMES = {
        { 11 }, { 6 }, { 32, 41 }
    };
    private static final int[][] WINDFAVORED_RELEASE_FRAMES = {
        { 15 }, { 3 }, { 32, 40 }
    };
    private static final int[] NORMAL_DURATION_FRAMES = { 35, 39, 76 };
    private static final int[] WINDFAVORED_DURATION_FRAMES = { 43, 34, 70 };
    private static final double[][] NORMAL_T9 = {
        { 1.262420 }, { 1.194480 }, { 0.875320, 0.875320 }
    };

    private CombatSimulator initializedSimulator;
    private int normalAttackStep;
    private long windfavoredGeneration;
    private boolean windfavoredActive;
    private double windfavoredStartTime = Double.NEGATIVE_INFINITY;
    private int skydwellerPoints;
    private int c6RestoreCount;
    private double nextParticleTime = Double.NEGATIVE_INFINITY;
    private double nextC6RestoreTime = Double.NEGATIVE_INFINITY;
    private AttackAction resolvingAction;
    private HitKind resolvingHitKind;
    private double resolvingMultiplier;
    private List<PendingSnapshot> pendingSnapshots = new ArrayList<>();
    private List<PendingHit> pendingHits = new ArrayList<>();
    private List<PendingCommand> pendingCommands = new ArrayList<>();

    /** Constructs the repository-default C6 Wanderer. */
    public Wanderer(Weapon weapon, ArtifactSet artifacts) {
        this(weapon, artifacts, TalentDataManager.getInstance(), 6);
    }

    /** Constructs Wanderer at an explicit constellation. */
    public Wanderer(
            Weapon weapon,
            ArtifactSet artifacts,
            int constellation) {
        this(weapon, artifacts, TalentDataManager.getInstance(),
                constellation);
    }

    /**
     * Constructs Wanderer with injectable static talent data.
     *
     * @param weapon equipped catalyst, or {@code null}
     * @param artifacts equipped artifact set, or {@code null}
     * @param talentData static character-data source
     * @param constellation constellation level in {@code [0, 6]}
     */
    public Wanderer(
            Weapon weapon,
            ArtifactSet artifacts,
            TalentDataSource talentData,
            int constellation) {
        super(talentData);
        if (constellation < 0 || constellation > 6) {
            throw new IllegalArgumentException(
                    "Wanderer constellation must be between 0 and 6");
        }
        name = "Wanderer";
        characterId = CharacterId.WANDERER;
        element = Element.ANEMO;
        this.weapon = weapon;
        this.artifacts = new ArtifactSet[] { artifacts };
        this.constellation = constellation;
        baseStats.set(StatType.BASE_HP,
                getTalentValue("Base HP", 10164.0));
        baseStats.set(StatType.BASE_ATK,
                getTalentValue("Base ATK", 328.0));
        baseStats.set(StatType.BASE_DEF,
                getTalentValue("Base DEF", 607.0));
        baseStats.add(StatType.CRIT_RATE,
                getTalentValue("Ascension CRIT Rate", 0.192));
        setSkillCD(getTalentValue(
                "Windfavored Skill Cooldown", 6.0));
        setBurstCD(getTalentValue("Burst Cooldown", 15.0));
    }

    /** Binds accepted-hit callbacks to exactly one simulator instance. */
    @Override
    public void initializeForSimulator(CombatSimulator simulator) {
        if (simulator == null) {
            throw new IllegalArgumentException(
                    "Wanderer simulator is required");
        }
        if (!simulator.getPartyMembers().contains(this)) {
            throw new IllegalArgumentException(
                    "Wanderer must belong to the simulator party");
        }
        if (initializedSimulator != null
                && initializedSimulator != simulator) {
            throw new IllegalStateException(
                    "Wanderer cannot be reused across simulators");
        }
        if (initializedSimulator == simulator) {
            return;
        }
        initializedSimulator = simulator;
        simulator.addDamageListener((actor, action, damage, time) -> {
            if (actor == this
                    && action == resolvingAction
                    && damage > 0.0) {
                handleAcceptedHit(
                        simulator,
                        resolvingHitKind,
                        resolvingMultiplier,
                        time);
            }
        });
    }

    /** Captures Windfavored resources, gates, and every delayed owner event. */
    @Override
    public State captureCharacterState() {
        return new WandererState(
                this,
                normalAttackStep,
                windfavoredGeneration,
                windfavoredActive,
                windfavoredStartTime,
                skydwellerPoints,
                c6RestoreCount,
                nextParticleTime,
                nextC6RestoreTime,
                pendingSnapshots,
                pendingHits,
                pendingCommands);
    }

    /** Accepts state captured from this exact Wanderer instance only. */
    @Override
    public boolean acceptsCharacterState(State state) {
        return state instanceof WandererState
                && ((WandererState) state).owner == this;
    }

    /** Restores Wanderer-owned state and surviving work exactly once. */
    @Override
    public void restoreCharacterState(
            State state,
            CombatSimulator simulator) {
        if (!acceptsCharacterState(state)) {
            throw new IllegalArgumentException("Unexpected Wanderer state");
        }
        initializeForSimulator(simulator);
        WandererState restored = (WandererState) state;
        normalAttackStep = restored.normalAttackStep;
        windfavoredGeneration = restored.windfavoredGeneration;
        windfavoredActive = restored.windfavoredActive;
        windfavoredStartTime = restored.windfavoredStartTime;
        skydwellerPoints = restored.skydwellerPoints;
        c6RestoreCount = restored.c6RestoreCount;
        nextParticleTime = restored.nextParticleTime;
        nextC6RestoreTime = restored.nextC6RestoreTime;
        pendingSnapshots = copySnapshots(restored.pendingSnapshots);
        pendingHits = copyHits(restored.pendingHits);
        pendingCommands = copyCommands(restored.pendingCommands);
        double currentTime = simulator.getCurrentTime();
        pendingSnapshots.removeIf(snapshot ->
                snapshot.time < currentTime - EPSILON);
        pendingHits.removeIf(hit -> hit.time < currentTime - EPSILON);
        pendingCommands.removeIf(command ->
                command.time < currentTime - EPSILON);
        for (PendingSnapshot snapshot
                : new ArrayList<>(pendingSnapshots)) {
            scheduleSnapshot(simulator, snapshot);
        }
        for (PendingHit hit : new ArrayList<>(pendingHits)) {
            scheduleHit(simulator, hit);
        }
        for (PendingCommand command
                : new ArrayList<>(pendingCommands)) {
            scheduleCommand(simulator, command);
        }
    }

    /** Returns Wanderer's 60-Energy Burst cost. */
    @Override
    public double getEnergyCost() {
        return getTalentValue("Energy Cost", 60.0);
    }

    /** Applies C1's attack speed only during a live Windfavored state. */
    @Override
    public void applyPassive(StatsContainer stats) {
        if (constellation >= 1
                && initializedSimulator != null
                && isWindfavoredActive(
                        initializedSimulator.getCurrentTime())) {
            stats.add(StatType.ATK_SPD,
                    getTalentValue("C1 Attack Speed", 0.10));
        }
    }

    /** Ends Windfavored and starts its cooldown when Wanderer switches out. */
    @Override
    public void onSwitchOut(CombatSimulator simulator) {
        normalAttackStep = 0;
        if (windfavoredActive) {
            endWindfavored(simulator, true);
        }
    }

    /** Resets the Normal chain on field entry. */
    @Override
    public void onSwitchIn(CombatSimulator simulator) {
        normalAttackStep = 0;
    }

    /** Returns whether Windfavored is live in its half-open point window. */
    public boolean isWindfavoredActive(double currentTime) {
        return windfavoredActive && skydwellerPoints > 0;
    }

    /** Returns current integer Kuugoryoku points. */
    public int getSkydwellerPoints() {
        return Math.max(0, skydwellerPoints);
    }

    /** Returns the number of C6 four-point restorations used this form. */
    public int getC6RestoreCount() {
        return c6RestoreCount;
    }

    /** Returns the number of unresolved Wanderer-owned hits. */
    public int getPendingHitCount() {
        return pendingHits.size();
    }

    /** Reports that A1 source-element absorption is intentionally excluded. */
    public boolean isA1AbsorptionRepresented() {
        return false;
    }

    /** Reports that A4 dash/probability consumption is intentionally excluded. */
    public boolean isA4Represented() {
        return false;
    }

    /** Dispatches Wanderer's represented typed action set. */
    @Override
    public void onAction(
            CharacterActionRequest request,
            CombatSimulator simulator) {
        if (request == null) {
            throw new IllegalArgumentException("Wanderer action is required");
        }
        initializeForSimulator(simulator);
        if (request.getKey() == CharacterActionKey.SKILL
                && request.getSkillMode() != SkillActionMode.PRESS) {
            throw new IllegalArgumentException(
                    "Wanderer supports Press Skill only");
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
                songOfTheWind(simulator);
                break;
            case BURST:
                fiveCeremonialPlays(simulator);
                break;
            case DASH:
                throw new IllegalArgumentException(
                        "Wanderer dash and A4 are outside this slice");
            default:
                throw new IllegalArgumentException(
                        "Unsupported action for Wanderer: "
                                + request.getKey());
        }
    }

    private void normalAttack(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        int step = normalAttackStep;
        boolean windfavored = isWindfavoredActive(castTime);
        int[][] releaseFrames = windfavored
                ? WINDFAVORED_RELEASE_FRAMES : NORMAL_RELEASE_FRAMES;
        HitKind kind = windfavored
                ? HitKind.WINDFAVORED_NORMAL : HitKind.NORMAL;
        for (int hit = 0; hit < releaseFrames[step].length; hit++) {
            double releaseTime = castTime
                    + releaseFrames[step][hit] * FRAME;
            queueSnapshot(simulator, new PendingSnapshot(
                    releaseTime,
                    releaseTime + 5.0 * FRAME,
                    kind,
                    step,
                    hit,
                    Double.NaN,
                    0.0));
        }
        normalAttackStep = (normalAttackStep + 1)
                % NORMAL_T9.length;
        int durationFrames = windfavored
                ? WINDFAVORED_DURATION_FRAMES[step]
                : NORMAL_DURATION_FRAMES[step];
        advanceAttackTime(simulator,
                durationFrames * FRAME,
                ActionType.NORMAL);
    }

    private void chargedAttack(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        boolean windfavored = isWindfavoredActive(castTime);
        int hitmark = windfavored ? 36 : 34;
        queueSnapshot(simulator, new PendingSnapshot(
                castTime + hitmark * FRAME,
                castTime + hitmark * FRAME,
                windfavored
                        ? HitKind.WINDFAVORED_CHARGED
                        : HitKind.CHARGED,
                0,
                0,
                Double.NaN,
                0.0));
        advanceAttackTime(simulator,
                (windfavored ? 70.0 : 69.0) * FRAME,
                ActionType.CHARGE);
    }

    private void highPlunge(CombatSimulator simulator) {
        if (isWindfavoredActive(simulator.getCurrentTime())) {
            throw new IllegalArgumentException(
                    "High Plunge is unavailable during Windfavored");
        }
        AttackAction plunge = attack(
                "Yuuban Meigen High Plunge",
                getTalentValue("High Plunge", 2.607632),
                StatType.PLUNGING_ATTACK_DMG_BONUS,
                ActionType.PLUNGE,
                ICDType.None,
                ICDTag.PlungeAttack,
                1.0);
        plunge.setAnimationDuration(1.0);
        plunge.setShatterTrigger(true);
        simulator.performAction(characterId, plunge);
    }

    private void songOfTheWind(CombatSimulator simulator) {
        if (windfavoredActive) {
            endWindfavored(simulator, true);
            simulator.advanceTime(26.0 * FRAME);
            return;
        }
        double castTime = simulator.getCurrentTime();
        windfavoredGeneration++;
        windfavoredActive = true;
        windfavoredStartTime = castTime;
        skydwellerPoints = (int) getTalentValue(
                "Windfavored Points", 100.0);
        c6RestoreCount = 0;
        nextC6RestoreTime = Double.NEGATIVE_INFINITY;
        normalAttackStep = 0;
        long generation = windfavoredGeneration;
        queueCommand(simulator, new PendingCommand(
                castTime + depletionInterval(),
                CommandKind.DEPLETE_POINT,
                1.0,
                generation));
        queueSnapshot(simulator, new PendingSnapshot(
                castTime + 2.0 * FRAME,
                castTime + 2.0 * FRAME,
                HitKind.SKILL,
                0,
                0,
                Double.NaN,
                0.0));
        simulator.advanceTime(28.0 * FRAME);
    }

    private void fiveCeremonialPlays(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        boolean windfavored = isWindfavoredActive(castTime);
        double c2Bonus = 0.0;
        if (windfavored && constellation >= 2) {
            int spentPoints = Math.max(0,
                    (int) getTalentValue("Windfavored Points", 100.0)
                            - skydwellerPoints);
            c2Bonus = Math.min(
                    getTalentValue("C2 Burst Bonus Cap", 2.0),
                    spentPoints * getTalentValue(
                            "C2 Bonus Per Spent Point", 0.04));
        }
        markBurstUsed(castTime, simulator.getApplicableBuffs(this));
        queueSnapshot(simulator, new PendingSnapshot(
                castTime + 55.0 * FRAME,
                castTime + 92.0 * FRAME,
                HitKind.BURST,
                0,
                0,
                Double.NaN,
                c2Bonus));
        if (windfavored) {
            endWindfavored(simulator, false);
            queueCommand(simulator, new PendingCommand(
                    castTime + 145.0 * FRAME,
                    CommandKind.START_SKILL_COOLDOWN,
                    0.0,
                    windfavoredGeneration));
            simulator.advanceTime(145.0 * FRAME);
            return;
        }
        simulator.advanceTime(101.0 * FRAME);
    }

    private void handleAcceptedHit(
            CombatSimulator simulator,
            HitKind kind,
            double multiplier,
            double hitTime) {
        if (kind != HitKind.WINDFAVORED_NORMAL
                && kind != HitKind.WINDFAVORED_CHARGED) {
            return;
        }
        if (!isWindfavoredActive(hitTime)) {
            return;
        }
        triggerParticle(simulator, hitTime);
        if (kind == HitKind.WINDFAVORED_NORMAL
                && constellation >= 6) {
            triggerC6(simulator, multiplier, hitTime);
        }
    }

    private void triggerParticle(
            CombatSimulator simulator,
            double hitTime) {
        if (hitTime + EPSILON < nextParticleTime) {
            return;
        }
        nextParticleTime = hitTime
                + getTalentValue("Particle Cooldown", 2.0);
        queueCommand(simulator, new PendingCommand(
                hitTime + getTalentValue(
                        "Particle Travel Frames", 100.0) * FRAME,
                CommandKind.PARTICLE,
                1.0,
                0L));
    }

    private void triggerC6(
            CombatSimulator simulator,
            double originalMultiplier,
            double hitTime) {
        if (c6RestoreCount < (int) getTalentValue(
                "C6 Restore Limit", 5.0)
                && skydwellerPoints < (int) getTalentValue(
                        "C6 Restore Threshold", 40.0)
                && hitTime + EPSILON >= nextC6RestoreTime) {
            c6RestoreCount++;
            skydwellerPoints += (int) getTalentValue(
                    "C6 Restore Amount", 4.0);
            nextC6RestoreTime = hitTime
                    + getTalentValue("C6 Restore Cooldown", 0.2);
        }
        double followUpTime = hitTime + 8.0 * FRAME;
        queueSnapshot(simulator, new PendingSnapshot(
                followUpTime,
                followUpTime,
                HitKind.C6_EXTRA,
                0,
                0,
                originalMultiplier * getTalentValue(
                        "C6 Extra Normal Ratio", 0.40),
                0.0));
    }

    private void resolveSnapshot(
            CombatSimulator simulator,
            PendingSnapshot pending) {
        StatsContainer snapshot = captureLiveStats(pending.time);
        if (pending.kind == HitKind.BURST) {
            snapshot.add(StatType.BURST_DMG_BONUS, pending.bonus);
            for (int hit = 0; hit < 5; hit++) {
                queueHit(simulator, new PendingHit(
                        pending.impactTime + hit * 6.0 * FRAME,
                        HitKind.BURST,
                        hit,
                        0,
                        Double.NaN,
                        snapshot));
            }
            return;
        }
        queueHit(simulator, new PendingHit(
                pending.impactTime,
                pending.kind,
                pending.index,
                pending.variant,
                pending.multiplierOverride,
                snapshot));
    }

    private void resolveHit(
            CombatSimulator simulator,
            PendingHit hit) {
        double multiplier = multiplierFor(hit);
        AttackAction action;
        switch (hit.kind) {
            case NORMAL:
            case WINDFAVORED_NORMAL:
                action = attack(
                        normalName(hit),
                        multiplier,
                        StatType.NORMAL_ATTACK_DMG_BONUS,
                        ActionType.NORMAL,
                        ICDType.Standard,
                        ICDTag.NormalAttack,
                        1.0);
                break;
            case CHARGED:
            case WINDFAVORED_CHARGED:
                action = attack(
                        hit.kind == HitKind.WINDFAVORED_CHARGED
                                ? "Yuuban Meigen Charged (Windfavored)"
                                : "Yuuban Meigen Charged",
                        multiplier,
                        StatType.CHARGED_ATTACK_DMG_BONUS,
                        ActionType.CHARGE,
                        ICDType.None,
                        ICDTag.ChargedAttack,
                        1.0);
                break;
            case SKILL:
                action = attack(
                        "Hanega: Song of the Wind",
                        multiplier,
                        StatType.SKILL_DMG_BONUS,
                        ActionType.SKILL,
                        ICDType.None,
                        ICDTag.ElementalSkill,
                        1.0);
                break;
            case BURST:
                action = attack(
                        "Kyougen: Five Ceremonial Plays Hit "
                                + (hit.index + 1),
                        multiplier,
                        StatType.BURST_DMG_BONUS,
                        ActionType.BURST,
                        ICDType.Standard,
                        ICDTag.ElementalBurst,
                        1.0);
                break;
            case C6_EXTRA:
                action = attack(
                        "Shugen: The Curtains' Melancholic Sway",
                        multiplier,
                        StatType.NORMAL_ATTACK_DMG_BONUS,
                        ActionType.NORMAL,
                        ICDType.WandererC6,
                        ICDTag.Wanderer_C6,
                        1.0);
                break;
            default:
                throw new IllegalStateException(
                        "Unknown Wanderer hit kind " + hit.kind);
        }
        action.setStatSnapshot(hit.snapshot);
        resolvingAction = action;
        resolvingHitKind = hit.kind;
        resolvingMultiplier = multiplier;
        try {
            simulator.performActionWithoutTimeAdvance(characterId, action);
        } finally {
            resolvingAction = null;
            resolvingHitKind = null;
            resolvingMultiplier = 0.0;
        }
    }

    private AttackAction attack(
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
                Element.ANEMO,
                StatType.BASE_ATK,
                bonusStat,
                0.0,
                actionType);
        action.setICD(icdType, icdTag, gauge);
        action.setCountsAsSkillDmg(actionType == ActionType.SKILL);
        action.setCountsAsBurstDmg(actionType == ActionType.BURST);
        return action;
    }

    private double multiplierFor(PendingHit hit) {
        if (!Double.isNaN(hit.multiplierOverride)) {
            return hit.multiplierOverride;
        }
        switch (hit.kind) {
            case NORMAL:
                return normalValue(hit.index, hit.variant);
            case WINDFAVORED_NORMAL:
                return normalValue(hit.index, hit.variant)
                        * windfavoredNormalModifier();
            case CHARGED:
                return getTalentValue("Charged", 2.245360);
            case WINDFAVORED_CHARGED:
                return getTalentValue("Charged", 2.245360)
                        * windfavoredChargedModifier();
            case SKILL:
                return getTalentValue(
                        constellation >= 5 ? "Skill C5" : "Skill",
                        constellation >= 5 ? 1.904000 : 1.618400);
            case BURST:
                return getTalentValue(
                        constellation >= 3
                                ? "Burst Hit C3" : "Burst Hit",
                        constellation >= 3 ? 2.944000 : 2.502400);
            default:
                throw new IllegalStateException(
                        "Missing multiplier for " + hit.kind);
        }
    }

    private double normalValue(int step, int hit) {
        String key = "N" + (step + 1);
        if (NORMAL_T9[step].length > 1) {
            key += "-" + (hit + 1);
        }
        return getTalentValue(key, NORMAL_T9[step][hit]);
    }

    private double windfavoredNormalModifier() {
        return getTalentValue(
                constellation >= 5
                        ? "Windfavored Normal Modifier C5"
                        : "Windfavored Normal Modifier",
                constellation >= 5 ? 1.588550 : 1.511525);
    }

    private double windfavoredChargedModifier() {
        return getTalentValue(
                constellation >= 5
                        ? "Windfavored Charged Modifier C5"
                        : "Windfavored Charged Modifier",
                constellation >= 5 ? 1.470840 : 1.409220);
    }

    private String normalName(PendingHit hit) {
        String name = "Yuuban Meigen N" + (hit.index + 1);
        if (NORMAL_T9[hit.index].length > 1) {
            name += "-" + (hit.variant + 1);
        }
        if (hit.kind == HitKind.WINDFAVORED_NORMAL) {
            name += " (Windfavored)";
        }
        return name;
    }

    private void depletePoint(
            CombatSimulator simulator,
            long generation,
            int tickIndex) {
        if (generation != windfavoredGeneration
                || !windfavoredActive) {
            return;
        }
        skydwellerPoints = Math.max(0, skydwellerPoints - 1);
        if (skydwellerPoints == 0) {
            endWindfavored(simulator, true);
            return;
        }
        queueCommand(simulator, new PendingCommand(
                windfavoredStartTime
                        + (tickIndex + 1) * depletionInterval(),
                CommandKind.DEPLETE_POINT,
                tickIndex + 1,
                generation));
    }

    private double depletionInterval() {
        return getTalentValue("Point Depletion Frames", 6.0) * FRAME;
    }

    private void endWindfavored(
            CombatSimulator simulator,
            boolean startCooldown) {
        long endingGeneration = windfavoredGeneration;
        windfavoredActive = false;
        windfavoredStartTime = Double.NEGATIVE_INFINITY;
        skydwellerPoints = 0;
        normalAttackStep = 0;
        windfavoredGeneration++;
        pendingCommands.removeIf(command ->
                command.kind == CommandKind.DEPLETE_POINT
                        && command.generation == endingGeneration);
        if (startCooldown) {
            startSkillCooldown(simulator);
        }
    }

    private void startSkillCooldown(CombatSimulator simulator) {
        setSkillCD(getTalentValue(
                "Windfavored Skill Cooldown", 6.0));
        markSkillUsed(simulator.getCurrentTime(),
                simulator.getApplicableBuffs(this));
    }

    private void advanceAttackTime(
            CombatSimulator simulator,
            double baseDuration,
            ActionType actionType) {
        StatsContainer stats = captureLiveStats(simulator.getCurrentTime());
        double speed = stats.get(StatType.ATK_SPD);
        if (actionType == ActionType.NORMAL) {
            speed += stats.get(StatType.NORMAL_ATTACK_SPD);
        }
        speed = Math.max(0.0, Math.min(0.60, speed));
        simulator.advanceTime(baseDuration / (1.0 + speed));
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

    private void queueSnapshot(
            CombatSimulator simulator,
            PendingSnapshot snapshot) {
        pendingSnapshots.add(snapshot);
        scheduleSnapshot(simulator, snapshot);
    }

    private void scheduleSnapshot(
            CombatSimulator simulator,
            PendingSnapshot snapshot) {
        schedule(simulator, snapshot.time, activeSimulator -> {
            if (!pendingSnapshots.remove(snapshot)) {
                return;
            }
            resolveSnapshot(activeSimulator, snapshot);
        });
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
                case DEPLETE_POINT:
                    depletePoint(
                            activeSimulator,
                            command.generation,
                            (int) command.value);
                    break;
                case START_SKILL_COOLDOWN:
                    startSkillCooldown(activeSimulator);
                    break;
                case PARTICLE:
                    activeSimulator.getEnergyDistributor()
                            .distributeParticles(
                                    Element.ANEMO,
                                    command.value,
                                    ParticleType.PARTICLE);
                    break;
                default:
                    throw new IllegalStateException(
                            "Unknown Wanderer command " + command.kind);
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

    private static List<PendingSnapshot> copySnapshots(
            List<PendingSnapshot> source) {
        List<PendingSnapshot> copy = new ArrayList<>();
        for (PendingSnapshot snapshot : source) {
            copy.add(snapshot.copy());
        }
        return copy;
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
        WINDFAVORED_NORMAL,
        CHARGED,
        WINDFAVORED_CHARGED,
        SKILL,
        BURST,
        C6_EXTRA
    }

    private enum CommandKind {
        DEPLETE_POINT,
        START_SKILL_COOLDOWN,
        PARTICLE
    }

    private static final class PendingSnapshot {
        private final double time;
        private final double impactTime;
        private final HitKind kind;
        private final int index;
        private final int variant;
        private final double multiplierOverride;
        private final double bonus;

        private PendingSnapshot(
                double time,
                double impactTime,
                HitKind kind,
                int index,
                int variant,
                double multiplierOverride,
                double bonus) {
            this.time = time;
            this.impactTime = impactTime;
            this.kind = kind;
            this.index = index;
            this.variant = variant;
            this.multiplierOverride = multiplierOverride;
            this.bonus = bonus;
        }

        private PendingSnapshot copy() {
            return new PendingSnapshot(
                    time,
                    impactTime,
                    kind,
                    index,
                    variant,
                    multiplierOverride,
                    bonus);
        }
    }

    private static final class PendingHit {
        private final double time;
        private final HitKind kind;
        private final int index;
        private final int variant;
        private final double multiplierOverride;
        private final StatsContainer snapshot;

        private PendingHit(
                double time,
                HitKind kind,
                int index,
                int variant,
                double multiplierOverride,
                StatsContainer snapshot) {
            this.time = time;
            this.kind = kind;
            this.index = index;
            this.variant = variant;
            this.multiplierOverride = multiplierOverride;
            this.snapshot = snapshot.merge(null);
        }

        private PendingHit copy() {
            return new PendingHit(
                    time,
                    kind,
                    index,
                    variant,
                    multiplierOverride,
                    snapshot);
        }
    }

    private static final class PendingCommand {
        private final double time;
        private final CommandKind kind;
        private final double value;
        private final long generation;

        private PendingCommand(
                double time,
                CommandKind kind,
                double value,
                long generation) {
            this.time = time;
            this.kind = kind;
            this.value = value;
            this.generation = generation;
        }

        private PendingCommand copy() {
            return new PendingCommand(time, kind, value, generation);
        }
    }

    private static final class WandererState implements State {
        private final Wanderer owner;
        private final int normalAttackStep;
        private final long windfavoredGeneration;
        private final boolean windfavoredActive;
        private final double windfavoredStartTime;
        private final int skydwellerPoints;
        private final int c6RestoreCount;
        private final double nextParticleTime;
        private final double nextC6RestoreTime;
        private final List<PendingSnapshot> pendingSnapshots;
        private final List<PendingHit> pendingHits;
        private final List<PendingCommand> pendingCommands;

        private WandererState(
                Wanderer owner,
                int normalAttackStep,
                long windfavoredGeneration,
                boolean windfavoredActive,
                double windfavoredStartTime,
                int skydwellerPoints,
                int c6RestoreCount,
                double nextParticleTime,
                double nextC6RestoreTime,
                List<PendingSnapshot> pendingSnapshots,
                List<PendingHit> pendingHits,
                List<PendingCommand> pendingCommands) {
            this.owner = owner;
            this.normalAttackStep = normalAttackStep;
            this.windfavoredGeneration = windfavoredGeneration;
            this.windfavoredActive = windfavoredActive;
            this.windfavoredStartTime = windfavoredStartTime;
            this.skydwellerPoints = skydwellerPoints;
            this.c6RestoreCount = c6RestoreCount;
            this.nextParticleTime = nextParticleTime;
            this.nextC6RestoreTime = nextC6RestoreTime;
            this.pendingSnapshots = copySnapshots(pendingSnapshots);
            this.pendingHits = copyHits(pendingHits);
            this.pendingCommands = copyCommands(pendingCommands);
        }
    }
}

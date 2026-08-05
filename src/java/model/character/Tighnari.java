package model.character;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.DoubleSupplier;

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
import simulation.action.SkillActionMode;
import simulation.event.SimpleTimerEvent;

/**
 * Tighnari's stationary single-target Wreath Arrow kit through C6.
 *
 * <p>Timings, multipliers, gauges, and snapshots follow pinned gcsim
 * {@code ef41805d} and maintained KQM Tighnari character/evidence pages.
 * Every Wreath impact creates four Clusterblooms against the represented fixed
 * target; C6 creates one separate no-ICD arrow.</p>
 *
 * <p>Aiming input, weak points, projectile pathing, obstacles, range, taunt
 * behavior, geometry, and multi-target selection are outside this slice.</p>
 */
public final class Tighnari extends Character implements
        ReactionAwareCharacter,
        SimulatorInitializedCharacterEffect,
        SnapshotAwareCharacterEffect,
        SwitchAwareCharacter {
    private static final double FRAME = 1.0 / 60.0;
    private static final double EPSILON = 1e-9;
    private static final double PROJECTILE_TRAVEL = 10.0 * FRAME;
    private static final double CLUSTER_TRAVEL = 35.0 * FRAME;
    private static final double PARTICLE_TRAVEL = 100.0 * FRAME;
    private static final int[][] NORMAL_RELEASE_FRAMES = {
        { 14 }, { 12 }, { 13, 25 }, { 28 }
    };
    private static final int[] NORMAL_DURATIONS = { 26, 23, 37, 68 };
    private static final String[][] NORMAL_KEYS = {
        { "N1" }, { "N2" }, { "N3-1", "N3-2" }, { "N4" }
    };
    private static final double[][] NORMAL_MULTIPLIERS = {
        { 0.82002 }, { 0.77104 }, { 0.48585, 0.48585 }, { 1.26084 }
    };
    private static final int[] BURST_PRIMARY_FRAMES = {
        112, 117, 120, 121, 126, 128
    };
    private static final int[] BURST_SECONDARY_FRAMES = {
        147, 153, 160, 161, 171, 175
    };

    private final DoubleSupplier particleDrawSource;
    private CombatSimulator initializedSimulator;
    private int normalAttackStep;
    private long skillGeneration;
    private long burstGeneration;
    private int suffusionCount;
    private double suffusionExpirationTime = Double.NEGATIVE_INFINITY;
    private double skillFieldExpirationTime = Double.NEGATIVE_INFINITY;
    private double c2ExpirationTime = Double.NEGATIVE_INFINITY;
    private double c4ExpirationTime = Double.NEGATIVE_INFINITY;
    private boolean resolvingBurstHit;
    private List<PendingHit> pendingHits = new ArrayList<>();
    private List<PendingCommand> pendingCommands = new ArrayList<>();

    /** Constructs repository-default C6 Tighnari. */
    public Tighnari(Weapon weapon, ArtifactSet artifacts) {
        this(weapon, artifacts, TalentDataManager.getInstance(), 6,
                Math::random);
    }

    /** Constructs Tighnari at an explicit constellation. */
    public Tighnari(
            Weapon weapon,
            ArtifactSet artifacts,
            int constellation) {
        this(weapon, artifacts, TalentDataManager.getInstance(), constellation,
                Math::random);
    }

    /** Constructs Tighnari with injectable talent data and particle draw. */
    public Tighnari(
            Weapon weapon,
            ArtifactSet artifacts,
            TalentDataSource talentData,
            int constellation,
            DoubleSupplier particleDrawSource) {
        super(talentData);
        if (constellation < 0 || constellation > 6) {
            throw new IllegalArgumentException(
                    "Tighnari constellation must be between 0 and 6");
        }
        if (particleDrawSource == null) {
            throw new IllegalArgumentException(
                    "Tighnari particle draw source is required");
        }
        name = "Tighnari";
        characterId = CharacterId.TIGHNARI;
        element = Element.DENDRO;
        this.weapon = weapon;
        this.artifacts = new ArtifactSet[] { artifacts };
        this.constellation = constellation;
        this.particleDrawSource = particleDrawSource;
        baseStats.set(StatType.BASE_HP,
                getTalentValue("Base HP", 10850.0));
        baseStats.set(StatType.BASE_ATK,
                getTalentValue("Base ATK", 268.0));
        baseStats.set(StatType.BASE_DEF,
                getTalentValue("Base DEF", 630.0));
        baseStats.add(StatType.DENDRO_DMG_BONUS,
                getTalentValue("Ascension Dendro DMG Bonus", 0.288));
        setSkillCD(12.0);
        setBurstCD(12.0);
    }

    /** Binds reaction and pending-event state to exactly one simulator. */
    @Override
    public void initializeForSimulator(CombatSimulator simulator) {
        if (simulator == null) {
            throw new IllegalArgumentException(
                    "Tighnari simulator is required");
        }
        if (initializedSimulator != null
                && initializedSimulator != simulator) {
            throw new IllegalStateException(
                    "Tighnari cannot be reused across simulators");
        }
        if (initializedSimulator == simulator) {
            return;
        }
        if (!simulator.getPartyMembers().contains(this)) {
            throw new IllegalArgumentException(
                    "Tighnari must belong to the target simulator party");
        }
        initializedSimulator = simulator;
        simulator.addReactionListener(this);
    }

    /** Captures all Tighnari-owned counters, windows, and delayed work. */
    @Override
    public State captureCharacterState() {
        return new TighnariState(
                this,
                normalAttackStep,
                skillGeneration,
                burstGeneration,
                suffusionCount,
                suffusionExpirationTime,
                skillFieldExpirationTime,
                c2ExpirationTime,
                c4ExpirationTime,
                pendingHits,
                pendingCommands);
    }

    /** Accepts only state captured from this exact Tighnari instance. */
    @Override
    public boolean acceptsCharacterState(State state) {
        return state instanceof TighnariState
                && ((TighnariState) state).owner == this;
    }

    /** Restores Tighnari state and schedules each surviving event once. */
    @Override
    public void restoreCharacterState(
            State state,
            CombatSimulator simulator) {
        if (!acceptsCharacterState(state)) {
            throw new IllegalArgumentException("Unexpected Tighnari state");
        }
        initializeForSimulator(simulator);
        TighnariState restored = (TighnariState) state;
        normalAttackStep = restored.normalAttackStep;
        skillGeneration = restored.skillGeneration;
        burstGeneration = restored.burstGeneration;
        suffusionCount = restored.suffusionCount;
        suffusionExpirationTime = restored.suffusionExpirationTime;
        skillFieldExpirationTime = restored.skillFieldExpirationTime;
        c2ExpirationTime = restored.c2ExpirationTime;
        c4ExpirationTime = restored.c4ExpirationTime;
        resolvingBurstHit = false;
        pendingHits = copyHits(restored.pendingHits);
        pendingCommands = copyCommands(restored.pendingCommands);
        double currentTime = simulator.getCurrentTime();
        pendingHits.removeIf(hit -> hit.time < currentTime - EPSILON);
        pendingCommands.removeIf(command ->
                command.time < currentTime - EPSILON);
        for (PendingHit hit : new ArrayList<>(pendingHits)) {
            scheduleHit(simulator, hit);
        }
        for (PendingCommand command : new ArrayList<>(pendingCommands)) {
            scheduleCommand(simulator, command);
        }
    }

    /** Returns Tighnari's 40-Energy Burst cost. */
    @Override
    public double getEnergyCost() {
        return getTalentValue("Energy Cost", 40.0);
    }

    /** Tighnari's combat passives are applied at action boundaries. */
    @Override
    public void applyPassive(StatsContainer stats) {
        // A1/A4 and constellations depend on action or field state.
    }

    /** Resets the four-shot Normal string on switch. */
    @Override
    public void onSwitchOut(CombatSimulator simulator) {
        normalAttackStep = 0;
    }

    /** Returns remaining accelerated Wreath shots at the supplied time. */
    public int getSuffusionCount(double currentTime) {
        expireSuffusion(currentTime);
        return suffusionCount;
    }

    /** Returns the half-open Vijnana Suffusion expiration timestamp. */
    public double getSuffusionExpirationTime() {
        return suffusionExpirationTime;
    }

    /** Returns the stationary Skill field expiration timestamp. */
    public double getSkillFieldExpirationTime() {
        return skillFieldExpirationTime;
    }

    /** Returns the C2 Dendro bonus expiration including its linger. */
    public double getC2ExpirationTime() {
        return c2ExpirationTime;
    }

    /** Returns the current C4 party-EM expiration timestamp. */
    public double getC4ExpirationTime() {
        return c4ExpirationTime;
    }

    /** Upgrades C4 only when one of Tighnari's Burst hits reacts. */
    @Override
    public void onReaction(
            ReactionResult result,
            Character source,
            double time,
            CombatSimulator simulator) {
        if (simulator != initializedSimulator
                || constellation < 4
                || !resolvingBurstHit
                || source != this
                || result == null
                || !isC4Reaction(result.getKind())) {
            return;
        }
        applyC4Buff(simulator, time, true);
    }

    /** Dispatches Tighnari's represented action set. */
    @Override
    public void onAction(
            CharacterActionRequest request,
            CombatSimulator simulator) {
        if (request == null) {
            throw new IllegalArgumentException("Tighnari action is required");
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
                wreathArrow(simulator);
                break;
            case SKILL:
                if (request.getSkillMode() != SkillActionMode.PRESS) {
                    throw new IllegalArgumentException(
                            "Tighnari supports Press Skill only");
                }
                vijnanaPhalaMine(simulator);
                break;
            case BURST:
                fashionersTanglevineShaft(simulator);
                break;
            default:
                throw new IllegalArgumentException(
                        "Unsupported action for Tighnari: "
                                + request.getKey());
        }
    }

    private void normalAttack(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        int step = normalAttackStep;
        for (int hit = 0; hit < NORMAL_RELEASE_FRAMES[step].length; hit++) {
            queueCommand(simulator, new PendingCommand(
                    castTime + NORMAL_RELEASE_FRAMES[step][hit] * FRAME,
                    CommandKind.NORMAL_RELEASE,
                    0L,
                    step,
                    hit));
        }
        normalAttackStep = (normalAttackStep + 1) % 4;
        simulator.advanceTime(NORMAL_DURATIONS[step] * FRAME);
    }

    private void wreathArrow(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        boolean accelerated = getSuffusionCount(castTime) > 0;
        if (accelerated) {
            suffusionCount--;
        }
        int releaseFrames;
        int durationFrames;
        if (accelerated && constellation >= 6) {
            releaseFrames = 0;
            durationFrames = 8;
        } else if (accelerated) {
            releaseFrames = 33;
            durationFrames = 41;
        } else if (constellation >= 6) {
            releaseFrames = 121;
            durationFrames = 129;
        } else {
            releaseFrames = 175;
            durationFrames = 183;
        }
        queueCommand(simulator, new PendingCommand(
                castTime + releaseFrames * FRAME,
                CommandKind.WREATH_RELEASE,
                0L,
                0,
                0));
        simulator.advanceTime(durationFrames * FRAME);
    }

    private void vijnanaPhalaMine(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        long generation = ++skillGeneration;
        queueCommand(simulator, new PendingCommand(
                castTime + 13.0 * FRAME,
                CommandKind.SKILL_ACTIVATE,
                generation,
                0,
                0));
        simulator.advanceTime(30.0 * FRAME);
    }

    private void fashionersTanglevineShaft(
            CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        long generation = ++burstGeneration;
        if (constellation >= 4) {
            applyC4Buff(simulator, castTime, false);
        }
        markBurstCooldownUsed(castTime, simulator.getApplicableBuffs(this));
        queueCommand(simulator, new PendingCommand(
                castTime + 7.0 * FRAME,
                CommandKind.BURST_ENERGY,
                generation,
                0,
                0));
        queueCommand(simulator, new PendingCommand(
                castTime + 77.0 * FRAME,
                CommandKind.BURST_RELEASE,
                generation,
                0,
                0));
        simulator.advanceTime(118.0 * FRAME);
    }

    private void releaseNormal(
            CombatSimulator simulator,
            int step,
            int hit) {
        StatsContainer snapshot = captureLiveStats(simulator.getCurrentTime());
        queueHit(simulator, new PendingHit(
                simulator.getCurrentTime() + PROJECTILE_TRAVEL,
                HitKind.NORMAL,
                step,
                hit,
                0L,
                snapshot));
    }

    private void releaseWreath(CombatSimulator simulator) {
        double currentTime = simulator.getCurrentTime();
        StatsContainer snapshot = captureLiveStats(currentTime);
        queueCommand(simulator, new PendingCommand(
                currentTime + FRAME,
                CommandKind.A1_ACTIVATE,
                0L,
                0,
                0));
        queueHit(simulator, new PendingHit(
                currentTime + PROJECTILE_TRAVEL,
                HitKind.WREATH,
                0,
                0,
                0L,
                snapshot));
    }

    private void activateSkill(
            CombatSimulator simulator,
            long generation) {
        if (generation != skillGeneration) {
            return;
        }
        double currentTime = simulator.getCurrentTime();
        markSkillUsed(currentTime, simulator.getApplicableBuffs(this));
        suffusionCount = (int) getTalentValue(
                "Vijnana Suffusion Wreath Arrow Count", 3.0);
        suffusionExpirationTime = currentTime + getTalentValue(
                "Vijnana Suffusion Duration", 12.0);
        queueCommand(simulator, new PendingCommand(
                currentTime + 2.0 * FRAME,
                CommandKind.SKILL_SNAPSHOT,
                generation,
                0,
                0));
    }

    private void snapshotSkill(
            CombatSimulator simulator,
            long generation) {
        if (generation != skillGeneration) {
            return;
        }
        int particleCount = 0;
        if (simulator.getEnemy() != null) {
            particleCount = validatedDraw() < 0.5 ? 4 : 3;
        }
        StatsContainer snapshot = captureLiveStats(
                simulator.getCurrentTime());
        queueHit(simulator, new PendingHit(
                simulator.getCurrentTime() + 5.0 * FRAME,
                HitKind.SKILL,
                0,
                particleCount,
                generation,
                snapshot));
    }

    private void releaseBurst(
            CombatSimulator simulator,
            long generation) {
        if (generation != burstGeneration) {
            return;
        }
        double currentTime = simulator.getCurrentTime();
        StatsContainer snapshot = captureLiveStats(currentTime);
        double castTime = currentTime - 77.0 * FRAME;
        for (int index = 0; index < BURST_PRIMARY_FRAMES.length; index++) {
            queueHit(simulator, new PendingHit(
                    castTime + BURST_PRIMARY_FRAMES[index] * FRAME,
                    HitKind.BURST_PRIMARY,
                    index,
                    0,
                    generation,
                    snapshot));
        }
    }

    private void resolveHit(CombatSimulator simulator, PendingHit hit) {
        if ((hit.kind == HitKind.SKILL
                && hit.generation != skillGeneration)
                || ((hit.kind == HitKind.BURST_PRIMARY
                        || hit.kind == HitKind.BURST_SECONDARY)
                        && hit.generation != burstGeneration)) {
            return;
        }
        switch (hit.kind) {
            case NORMAL:
                resolveNormal(simulator, hit);
                break;
            case WREATH:
                resolveWreath(simulator, hit);
                break;
            case CLUSTERBLOOM:
                resolveClusterbloom(simulator, hit, false);
                break;
            case C6_CLUSTERBLOOM:
                resolveClusterbloom(simulator, hit, true);
                break;
            case SKILL:
                resolveSkill(simulator, hit);
                break;
            case BURST_PRIMARY:
                resolveBurst(simulator, hit, false);
                break;
            case BURST_SECONDARY:
                resolveBurst(simulator, hit, true);
                break;
            default:
                throw new IllegalStateException("Unknown Tighnari hit kind");
        }
    }

    private void resolveNormal(
            CombatSimulator simulator,
            PendingHit hit) {
        String key = NORMAL_KEYS[hit.index][hit.subIndex];
        AttackAction action = attack(
                "Khanda Barrier-Buster " + key,
                getTalentValue(
                        key,
                        NORMAL_MULTIPLIERS[hit.index][hit.subIndex]),
                Element.PHYSICAL,
                StatType.NORMAL_ATTACK_DMG_BONUS,
                ActionType.NORMAL,
                ICDType.Standard,
                ICDTag.NormalAttack,
                0.0);
        action.setStatSnapshot(hit.snapshot);
        simulator.performActionWithoutTimeAdvance(characterId, action);
    }

    private void resolveWreath(
            CombatSimulator simulator,
            PendingHit hit) {
        AttackAction action = attack(
                "Khanda Barrier-Buster Wreath Arrow",
                getTalentValue("Wreath Arrow", 1.4824),
                Element.DENDRO,
                StatType.CHARGED_ATTACK_DMG_BONUS,
                ActionType.CHARGE,
                ICDType.None,
                ICDTag.None,
                1.0);
        prepareChargedOrBurst(action, hit.snapshot, true);
        simulator.performActionWithoutTimeAdvance(characterId, action);

        StatsContainer clusterSnapshot = captureLiveStats(
                simulator.getCurrentTime());
        double impactTime = simulator.getCurrentTime() + CLUSTER_TRAVEL;
        for (int index = 0; index < 4; index++) {
            queueHit(simulator, new PendingHit(
                    impactTime,
                    HitKind.CLUSTERBLOOM,
                    index,
                    0,
                    0L,
                    clusterSnapshot));
        }
        if (constellation >= 6) {
            queueHit(simulator, new PendingHit(
                    impactTime,
                    HitKind.C6_CLUSTERBLOOM,
                    0,
                    0,
                    0L,
                    clusterSnapshot));
        }
    }

    private void resolveClusterbloom(
            CombatSimulator simulator,
            PendingHit hit,
            boolean c6Arrow) {
        AttackAction action = attack(
                c6Arrow
                        ? "Khanda Barrier-Buster C6 Clusterbloom Arrow"
                        : "Khanda Barrier-Buster Clusterbloom Arrow",
                getTalentValue(
                        c6Arrow
                                ? "C6 Additional Clusterbloom Arrow"
                                : "Clusterbloom Arrow",
                        c6Arrow ? 1.5 : 0.6562),
                Element.DENDRO,
                StatType.CHARGED_ATTACK_DMG_BONUS,
                ActionType.CHARGE,
                c6Arrow ? ICDType.None : ICDType.TighnariClusterbloom,
                c6Arrow ? ICDTag.None : ICDTag.Tighnari_Clusterbloom,
                1.0);
        prepareChargedOrBurst(action, hit.snapshot, true);
        simulator.performActionWithoutTimeAdvance(characterId, action);
    }

    private void resolveSkill(
            CombatSimulator simulator,
            PendingHit hit) {
        AttackAction action = attack(
                "Vijnana-Phala Mine",
                getTalentValue(
                        constellation >= 5 ? "Skill C5" : "Skill",
                        constellation >= 5 ? 2.992 : 2.5432),
                Element.DENDRO,
                StatType.SKILL_DMG_BONUS,
                ActionType.SKILL,
                ICDType.None,
                ICDTag.None,
                1.0);
        action.setStatSnapshot(hit.snapshot);
        simulator.performActionWithoutTimeAdvance(characterId, action);
        activateSkillField(simulator);
        if (hit.subIndex > 0) {
            queueCommand(simulator, new PendingCommand(
                    simulator.getCurrentTime() + PARTICLE_TRAVEL,
                    CommandKind.PARTICLE,
                    0L,
                    hit.subIndex,
                    0));
        }
    }

    private void resolveBurst(
            CombatSimulator simulator,
            PendingHit hit,
            boolean secondary) {
        String key;
        double fallback;
        if (secondary) {
            key = constellation >= 3
                    ? "Secondary Tanglevine Shaft C3"
                    : "Secondary Tanglevine Shaft";
            fallback = constellation >= 3 ? 1.3596 : 1.15566;
        } else {
            key = constellation >= 3
                    ? "Tanglevine Shaft C3" : "Tanglevine Shaft";
            fallback = constellation >= 3 ? 1.1124 : 0.94554;
        }
        AttackAction action = attack(
                secondary
                        ? "Fashioner's Tanglevine Shaft Secondary"
                        : "Fashioner's Tanglevine Shaft Primary",
                getTalentValue(key, fallback),
                Element.DENDRO,
                StatType.BURST_DMG_BONUS,
                ActionType.BURST,
                ICDType.Standard,
                ICDTag.ElementalBurst,
                1.0);
        prepareChargedOrBurst(action, hit.snapshot, false);
        resolvingBurstHit = true;
        try {
            simulator.performActionWithoutTimeAdvance(characterId, action);
        } finally {
            resolvingBurstHit = false;
        }
        if (!secondary) {
            StatsContainer secondarySnapshot = captureLiveStats(
                    simulator.getCurrentTime());
            queueHit(simulator, new PendingHit(
                    simulator.getCurrentTime()
                            + (BURST_SECONDARY_FRAMES[hit.index]
                                    - BURST_PRIMARY_FRAMES[hit.index])
                                    * FRAME,
                    HitKind.BURST_SECONDARY,
                    hit.index,
                    0,
                    hit.generation,
                    secondarySnapshot));
        }
    }

    private void activateSkillField(CombatSimulator simulator) {
        double currentTime = simulator.getCurrentTime();
        skillFieldExpirationTime = currentTime + getTalentValue(
                "Skill Field Duration", 8.0);
        if (constellation < 2 || simulator.getEnemy() == null) {
            return;
        }
        c2ExpirationTime = skillFieldExpirationTime + getTalentValue(
                "C2 Linger Duration", 6.0);
        removeBuff(BuffId.TIGHNARI_C2_DENDRO_DMG_BONUS);
        addBuff(new SimpleBuff(
                "Tighnari Origins Known From the Stem",
                BuffId.TIGHNARI_C2_DENDRO_DMG_BONUS,
                c2ExpirationTime - currentTime,
                currentTime,
                stats -> stats.add(
                        StatType.DENDRO_DMG_BONUS,
                        getTalentValue("C2 Dendro DMG Bonus", 0.20))));
    }

    private void prepareChargedOrBurst(
            AttackAction action,
            StatsContainer snapshot,
            boolean charged) {
        action.setStatSnapshot(snapshot);
        double elementalMastery = snapshot.get(StatType.ELEMENTAL_MASTERY);
        double a4Bonus = Math.min(
                getTalentValue("A4 Max DMG Bonus", 0.60),
                elementalMastery
                        * getTalentValue("A4 EM DMG Bonus Ratio", 0.0006));
        action.addBonusStat(
                charged
                        ? StatType.CHARGED_ATTACK_DMG_BONUS
                        : StatType.BURST_DMG_BONUS,
                a4Bonus);
        if (charged && constellation >= 1) {
            action.addBonusStat(
                    StatType.CRIT_RATE,
                    getTalentValue("C1 Charged CRIT Rate", 0.15));
        }
    }

    private void applyA1(double currentTime) {
        removeBuff(BuffId.TIGHNARI_A1_ELEMENTAL_MASTERY);
        addBuff(new SimpleBuff(
                "Tighnari Keen Sight",
                BuffId.TIGHNARI_A1_ELEMENTAL_MASTERY,
                getTalentValue("A1 Duration", 4.0),
                currentTime,
                stats -> stats.add(
                        StatType.ELEMENTAL_MASTERY,
                        getTalentValue("A1 Elemental Mastery", 50.0))));
    }

    private void applyC4Buff(
            CombatSimulator simulator,
            double currentTime,
            boolean upgraded) {
        double amount = getTalentValue(
                "C4 Base Elemental Mastery", 60.0);
        if (upgraded) {
            amount += getTalentValue(
                    "C4 Reaction Additional Elemental Mastery", 60.0);
        }
        c4ExpirationTime = currentTime
                + getTalentValue("C4 Duration", 8.0);
        double buffAmount = amount;
        simulator.applyTeamBuffNoStack(new SimpleBuff(
                "Tighnari Withering Glimpsed in the Leaves",
                BuffId.TIGHNARI_C4_PARTY_ELEMENTAL_MASTERY,
                getTalentValue("C4 Duration", 8.0),
                currentTime,
                stats -> stats.add(
                        StatType.ELEMENTAL_MASTERY, buffAmount))
                .sourcedBy(characterId));
    }

    private boolean isC4Reaction(ReactionResult.Kind kind) {
        return kind == ReactionResult.Kind.BURNING
                || kind == ReactionResult.Kind.BLOOM
                || kind == ReactionResult.Kind.LUNAR_BLOOM
                || kind == ReactionResult.Kind.QUICKEN
                || kind == ReactionResult.Kind.SPREAD;
    }

    private void expireSuffusion(double currentTime) {
        if (currentTime >= suffusionExpirationTime) {
            suffusionCount = 0;
        }
    }

    private double validatedDraw() {
        double draw = particleDrawSource.getAsDouble();
        if (!Double.isFinite(draw) || draw < 0.0 || draw >= 1.0) {
            throw new IllegalStateException(
                    "Tighnari particle draw must be in [0, 1)");
        }
        return draw;
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

    private void queueHit(CombatSimulator simulator, PendingHit hit) {
        pendingHits.add(hit);
        scheduleHit(simulator, hit);
    }

    private void scheduleHit(CombatSimulator simulator, PendingHit hit) {
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
                case NORMAL_RELEASE:
                    releaseNormal(
                            activeSimulator,
                            command.index,
                            command.subIndex);
                    break;
                case WREATH_RELEASE:
                    releaseWreath(activeSimulator);
                    break;
                case SKILL_ACTIVATE:
                    activateSkill(activeSimulator, command.generation);
                    break;
                case SKILL_SNAPSHOT:
                    snapshotSkill(activeSimulator, command.generation);
                    break;
                case BURST_ENERGY:
                    if (command.generation == burstGeneration) {
                        spendBurstEnergy(activeSimulator.getCurrentTime());
                    }
                    break;
                case BURST_RELEASE:
                    releaseBurst(activeSimulator, command.generation);
                    break;
                case PARTICLE:
                    activeSimulator.getEnergyDistributor().distributeParticles(
                            Element.DENDRO,
                            command.index,
                            ParticleType.PARTICLE);
                    break;
                case A1_ACTIVATE:
                    applyA1(activeSimulator.getCurrentTime());
                    break;
                default:
                    throw new IllegalStateException(
                            "Unknown Tighnari command kind");
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

    private static AttackAction attack(
            String displayName,
            double multiplier,
            Element element,
            StatType bonusStat,
            ActionType actionType,
            ICDType icdType,
            ICDTag icdTag,
            double gauge) {
        AttackAction action = new AttackAction(
                displayName,
                multiplier,
                element,
                StatType.BASE_ATK,
                bonusStat,
                0.0,
                actionType);
        action.setICD(icdType, icdTag, gauge);
        action.setCountsAsSkillDmg(actionType == ActionType.SKILL);
        action.setCountsAsBurstDmg(actionType == ActionType.BURST);
        return action;
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
        WREATH,
        CLUSTERBLOOM,
        C6_CLUSTERBLOOM,
        SKILL,
        BURST_PRIMARY,
        BURST_SECONDARY
    }

    private enum CommandKind {
        NORMAL_RELEASE,
        WREATH_RELEASE,
        SKILL_ACTIVATE,
        SKILL_SNAPSHOT,
        BURST_ENERGY,
        BURST_RELEASE,
        PARTICLE,
        A1_ACTIVATE
    }

    /** Immutable delayed Tighnari hit description. */
    private static final class PendingHit {
        private final double time;
        private final HitKind kind;
        private final int index;
        private final int subIndex;
        private final long generation;
        private final StatsContainer snapshot;

        private PendingHit(
                double time,
                HitKind kind,
                int index,
                int subIndex,
                long generation,
                StatsContainer snapshot) {
            this.time = time;
            this.kind = kind;
            this.index = index;
            this.subIndex = subIndex;
            this.generation = generation;
            this.snapshot = snapshot == null
                    ? null : snapshot.merge(null);
        }

        private PendingHit copy() {
            return new PendingHit(
                    time,
                    kind,
                    index,
                    subIndex,
                    generation,
                    snapshot);
        }
    }

    /** Immutable delayed Tighnari command description. */
    private static final class PendingCommand {
        private final double time;
        private final CommandKind kind;
        private final long generation;
        private final int index;
        private final int subIndex;

        private PendingCommand(
                double time,
                CommandKind kind,
                long generation,
                int index,
                int subIndex) {
            this.time = time;
            this.kind = kind;
            this.generation = generation;
            this.index = index;
            this.subIndex = subIndex;
        }

        private PendingCommand copy() {
            return new PendingCommand(
                    time, kind, generation, index, subIndex);
        }
    }

    /** Immutable Tighnari-owned simulator snapshot payload. */
    private static final class TighnariState implements State {
        private final Tighnari owner;
        private final int normalAttackStep;
        private final long skillGeneration;
        private final long burstGeneration;
        private final int suffusionCount;
        private final double suffusionExpirationTime;
        private final double skillFieldExpirationTime;
        private final double c2ExpirationTime;
        private final double c4ExpirationTime;
        private final List<PendingHit> pendingHits;
        private final List<PendingCommand> pendingCommands;

        private TighnariState(
                Tighnari owner,
                int normalAttackStep,
                long skillGeneration,
                long burstGeneration,
                int suffusionCount,
                double suffusionExpirationTime,
                double skillFieldExpirationTime,
                double c2ExpirationTime,
                double c4ExpirationTime,
                List<PendingHit> pendingHits,
                List<PendingCommand> pendingCommands) {
            this.owner = owner;
            this.normalAttackStep = normalAttackStep;
            this.skillGeneration = skillGeneration;
            this.burstGeneration = burstGeneration;
            this.suffusionCount = suffusionCount;
            this.suffusionExpirationTime = suffusionExpirationTime;
            this.skillFieldExpirationTime = skillFieldExpirationTime;
            this.c2ExpirationTime = c2ExpirationTime;
            this.c4ExpirationTime = c4ExpirationTime;
            this.pendingHits = copyHits(pendingHits);
            this.pendingCommands = copyCommands(pendingCommands);
        }
    }
}

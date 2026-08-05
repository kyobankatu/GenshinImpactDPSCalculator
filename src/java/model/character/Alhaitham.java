package model.character;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
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
 * Alhaitham's stationary single-target Chisel-Light Mirror kit through C6.
 *
 * <p>Mirror decay, Projection cadence and snapshots, delayed Burst conversion,
 * infusion, ATK+EM scaling, A1/A4, and representable constellations follow
 * pinned gcsim {@code ef41805d} and maintained KQM evidence.</p>
 *
 * <p>Projection positioning, multi-target selection, hold-Skill movement,
 * hitlag, stamina, interruption, and plunge pathing are excluded.</p>
 */
public final class Alhaitham extends Character implements
        SimulatorInitializedCharacterEffect,
        SnapshotAwareCharacterEffect,
        SwitchAwareCharacter {
    private static final double FRAME = 1.0 / 60.0;
    private static final double EPSILON = 1e-9;
    private static final int[][] NORMAL_HIT_FRAMES = {
        { 11 }, { 13 }, { 15, 29 }, { 21 }, { 35 }
    };
    private static final int[] NORMAL_DURATIONS = { 15, 22, 44, 30, 67 };
    private static final String[][] NORMAL_KEYS = {
        { "N1" }, { "N2" }, { "N3-1", "N3-2" }, { "N4" }, { "N5" }
    };
    private static final double[][] NORMAL_MULTIPLIERS = {
        { 0.90989 }, { 0.932374 }, { 0.627931, 0.627931 },
        { 1.226665 }, { 1.540516 }
    };
    private static final int[] CHARGED_HIT_FRAMES = { 19, 27 };
    private static final int[] PROJECTION_SNAPSHOT_FRAMES = { 20, 22, 26 };
    private static final int[][] PROJECTION_HIT_FRAMES = {
        { 39 }, { 28, 37 }, { 32, 41, 51 }
    };

    private CombatSimulator initializedSimulator;
    private int normalAttackStep;
    private int mirrorCount;
    private long mirrorGeneration;
    private long skillGeneration;
    private long burstGeneration;
    private double nextProjectionTime = Double.NEGATIVE_INFINITY;
    private double nextParticleTime = Double.NEGATIVE_INFINITY;
    private double nextA1Time = Double.NEGATIVE_INFINITY;
    private double nextC1Time = Double.NEGATIVE_INFINITY;
    private double burstCastElementalMastery;
    private AttackAction resolvingProjectionAction;
    private final double[] c2Expirations = new double[] {
        Double.NEGATIVE_INFINITY,
        Double.NEGATIVE_INFINITY,
        Double.NEGATIVE_INFINITY,
        Double.NEGATIVE_INFINITY
    };
    private int c2Cursor;
    private double c6ExpirationTime = Double.NEGATIVE_INFINITY;
    private List<PendingHit> pendingHits = new ArrayList<>();
    private List<PendingCommand> pendingCommands = new ArrayList<>();

    /** Constructs repository-default C6 Alhaitham. */
    public Alhaitham(Weapon weapon, ArtifactSet artifacts) {
        this(weapon, artifacts, TalentDataManager.getInstance(), 6);
    }

    /** Constructs Alhaitham at an explicit constellation. */
    public Alhaitham(
            Weapon weapon,
            ArtifactSet artifacts,
            int constellation) {
        this(weapon, artifacts, TalentDataManager.getInstance(), constellation);
    }

    /** Constructs Alhaitham with injectable talent data. */
    public Alhaitham(
            Weapon weapon,
            ArtifactSet artifacts,
            TalentDataSource talentData,
            int constellation) {
        super(talentData);
        if (constellation < 0 || constellation > 6) {
            throw new IllegalArgumentException(
                    "Alhaitham constellation must be between 0 and 6");
        }
        name = "Alhaitham";
        characterId = CharacterId.ALHAITHAM;
        element = Element.DENDRO;
        this.weapon = weapon;
        this.artifacts = new ArtifactSet[] { artifacts };
        this.constellation = constellation;
        baseStats.set(StatType.BASE_HP,
                getTalentValue("Base HP", 13348.0));
        baseStats.set(StatType.BASE_ATK,
                getTalentValue("Base ATK", 313.0));
        baseStats.set(StatType.BASE_DEF,
                getTalentValue("Base DEF", 782.0));
        baseStats.add(StatType.DENDRO_DMG_BONUS,
                getTalentValue("Ascension Dendro DMG Bonus", 0.288));
        setSkillCD(getTalentValue("Skill Cooldown", 18.0));
        setBurstCD(18.0);
    }

    /** Binds Projection and constellation listeners to exactly one simulator. */
    @Override
    public void initializeForSimulator(CombatSimulator simulator) {
        if (simulator == null) {
            throw new IllegalArgumentException(
                    "Alhaitham simulator is required");
        }
        if (initializedSimulator != null
                && initializedSimulator != simulator) {
            throw new IllegalStateException(
                    "Alhaitham cannot be reused across simulators");
        }
        if (initializedSimulator == simulator) {
            return;
        }
        if (!simulator.getPartyMembers().contains(this)) {
            throw new IllegalArgumentException(
                    "Alhaitham must belong to the target simulator party");
        }
        initializedSimulator = simulator;
        simulator.addDamageListener((actor, action, damage, time) ->
                handleResolvedDamage(
                        simulator, actor, action, damage, time));
    }

    /** Captures mirrors, constellation windows, and all delayed work. */
    @Override
    public State captureCharacterState() {
        return new AlhaithamState(
                this,
                normalAttackStep,
                mirrorCount,
                mirrorGeneration,
                skillGeneration,
                burstGeneration,
                nextProjectionTime,
                nextParticleTime,
                nextA1Time,
                nextC1Time,
                burstCastElementalMastery,
                c2Expirations,
                c2Cursor,
                c6ExpirationTime,
                pendingHits,
                pendingCommands);
    }

    /** Accepts only state captured from this exact Alhaitham instance. */
    @Override
    public boolean acceptsCharacterState(State state) {
        return state instanceof AlhaithamState
                && ((AlhaithamState) state).owner == this;
    }

    /** Restores owner state and reconstructs each surviving event once. */
    @Override
    public void restoreCharacterState(
            State state,
            CombatSimulator simulator) {
        if (!acceptsCharacterState(state)) {
            throw new IllegalArgumentException("Unexpected Alhaitham state");
        }
        initializeForSimulator(simulator);
        AlhaithamState restored = (AlhaithamState) state;
        normalAttackStep = restored.normalAttackStep;
        mirrorCount = restored.mirrorCount;
        mirrorGeneration = restored.mirrorGeneration;
        skillGeneration = restored.skillGeneration;
        burstGeneration = restored.burstGeneration;
        nextProjectionTime = restored.nextProjectionTime;
        nextParticleTime = restored.nextParticleTime;
        nextA1Time = restored.nextA1Time;
        nextC1Time = restored.nextC1Time;
        burstCastElementalMastery = restored.burstCastElementalMastery;
        System.arraycopy(restored.c2Expirations, 0,
                c2Expirations, 0, c2Expirations.length);
        c2Cursor = restored.c2Cursor;
        c6ExpirationTime = restored.c6ExpirationTime;
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

    /** Returns Alhaitham's 70-Energy Burst cost. */
    @Override
    public double getEnergyCost() {
        return getTalentValue("Energy Cost", 70.0);
    }

    /** Applies independent C2 stacks and the extendable C6 window. */
    @Override
    public void applyPassive(StatsContainer stats) {
        double currentTime = initializedSimulator == null
                ? Double.NEGATIVE_INFINITY
                : initializedSimulator.getCurrentTime();
        int c2Stacks = getC2Stacks(currentTime);
        if (constellation >= 2 && c2Stacks > 0) {
            stats.add(StatType.ELEMENTAL_MASTERY,
                    c2Stacks * getTalentValue(
                            "C2 Elemental Mastery Per Stack", 50.0));
        }
        if (constellation >= 6 && currentTime < c6ExpirationTime) {
            stats.add(StatType.CRIT_RATE,
                    getTalentValue("C6 CRIT Rate", 0.10));
            stats.add(StatType.CRIT_DMG,
                    getTalentValue("C6 CRIT DMG", 0.70));
        }
    }

    /** Removes all Mirrors and resets the Normal string on switch. */
    @Override
    public void onSwitchOut(CombatSimulator simulator) {
        mirrorCount = 0;
        mirrorGeneration++;
        normalAttackStep = 0;
    }

    /** Resets the five-step Normal string on entry. */
    @Override
    public void onSwitchIn(CombatSimulator simulator) {
        normalAttackStep = 0;
    }

    /** Returns current Chisel-Light Mirror count. */
    public int getMirrorCount() {
        return mirrorCount;
    }

    /** Returns current independently timed C2 stack count. */
    public int getC2Stacks(double currentTime) {
        int count = 0;
        for (double expiration : c2Expirations) {
            if (currentTime < expiration) {
                count++;
            }
        }
        return count;
    }

    /** Returns C6's current expiration timestamp. */
    public double getC6ExpirationTime() {
        return c6ExpirationTime;
    }

    /** Dispatches Alhaitham's represented typed action set. */
    @Override
    public void onAction(
            CharacterActionRequest request,
            CombatSimulator simulator) {
        if (request == null) {
            throw new IllegalArgumentException(
                    "Alhaitham action is required");
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
                if (request.getSkillMode() != SkillActionMode.PRESS) {
                    throw new IllegalArgumentException(
                            "Alhaitham fixed-target slice supports Press Skill only");
                }
                universality(simulator);
                break;
            case BURST:
                particularField(simulator);
                break;
            default:
                throw new IllegalArgumentException(
                        "Unsupported action for Alhaitham: "
                                + request.getKey());
        }
    }

    private void normalAttack(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        int step = normalAttackStep;
        for (int hit = 0; hit < NORMAL_HIT_FRAMES[step].length; hit++) {
            queueHit(simulator, new PendingHit(
                    castTime + resolveNormalDuration(
                            simulator,
                            NORMAL_HIT_FRAMES[step][hit] * FRAME),
                    HitKind.NORMAL,
                    step,
                    hit,
                    0,
                    0L,
                    null));
        }
        normalAttackStep = (normalAttackStep + 1) % 5;
        simulator.advanceTime(resolveNormalDuration(
                simulator, NORMAL_DURATIONS[step] * FRAME));
    }

    private void chargedAttack(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        for (int hit = 0; hit < CHARGED_HIT_FRAMES.length; hit++) {
            queueHit(simulator, new PendingHit(
                    castTime + CHARGED_HIT_FRAMES[hit] * FRAME,
                    HitKind.CHARGED,
                    hit,
                    0,
                    0,
                    0L,
                    null));
        }
        simulator.advanceTime(50.0 * FRAME);
    }

    private void highPlunge(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        queueHit(simulator, new PendingHit(
                castTime + 46.0 * FRAME,
                HitKind.PLUNGE,
                0,
                0,
                0,
                0L,
                null));
        simulator.advanceTime(59.0 * FRAME);
    }

    private void universality(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        long generation = ++skillGeneration;
        StatsContainer castStats = captureLiveStats(castTime);
        queueCommand(simulator, new PendingCommand(
                castTime + getTalentValue(
                        "Skill Mirror Gain Frames", 15.0) * FRAME,
                CommandKind.SKILL_GAIN,
                generation,
                0));
        queueHit(simulator, new PendingHit(
                castTime + getTalentValue(
                        "Skill Hit Frames", 19.0) * FRAME,
                HitKind.SKILL,
                0,
                0,
                0,
                generation,
                castStats));
        simulator.advanceTime(27.0 * FRAME);
    }

    private void particularField(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        long generation = ++burstGeneration;
        int consumed = consumeMirrors();
        int generated = constellation >= 6 ? 3 : 3 - consumed;
        burstCastElementalMastery = captureLiveStats(castTime).get(
                StatType.ELEMENTAL_MASTERY);
        markBurstCooldownUsed(
                castTime, simulator.getApplicableBuffs(this));
        if (constellation >= 4) {
            applyC4PartyBuff(simulator, castTime, consumed);
        }
        queueCommand(simulator, new PendingCommand(
                castTime + 6.0 * FRAME,
                CommandKind.BURST_ENERGY,
                generation,
                0));
        queueCommand(simulator, new PendingCommand(
                castTime + getTalentValue(
                        "Burst Snapshot Frames", 67.0) * FRAME,
                CommandKind.BURST_SNAPSHOT,
                generation,
                4 + 2 * consumed));
        queueCommand(simulator, new PendingCommand(
                castTime + getTalentValue(
                        "Burst Mirror Command Frames", 184.0) * FRAME,
                CommandKind.BURST_MIRRORS,
                generation,
                generated));
        simulator.advanceTime(88.0 * FRAME);
    }

    private void resolveHit(CombatSimulator simulator, PendingHit hit) {
        switch (hit.kind) {
            case NORMAL:
                resolveNormal(simulator, hit);
                break;
            case CHARGED:
                resolveCharged(simulator, hit);
                break;
            case PLUNGE:
                resolvePlunge(simulator, hit);
                break;
            case SKILL:
                if (hit.generation == skillGeneration) {
                    resolveSkill(simulator, hit);
                }
                break;
            case PROJECTION:
                resolveProjection(simulator, hit);
                break;
            case BURST:
                if (hit.generation == burstGeneration) {
                    resolveBurst(simulator, hit);
                }
                break;
            default:
                throw new IllegalStateException("Unknown Alhaitham hit kind");
        }
    }

    private void resolveNormal(
            CombatSimulator simulator,
            PendingHit hit) {
        String key = NORMAL_KEYS[hit.index][hit.subIndex];
        boolean infused = mirrorCount > 0;
        AttackAction action = attack(
                "Abductive Reasoning " + key,
                getTalentValue(
                        key,
                        NORMAL_MULTIPLIERS[hit.index][hit.subIndex]),
                infused ? Element.DENDRO : Element.PHYSICAL,
                StatType.NORMAL_ATTACK_DMG_BONUS,
                ActionType.NORMAL,
                ICDType.Standard,
                ICDTag.NormalAttack,
                infused ? 1.0 : 0.0);
        action.setStatSnapshot(captureLiveStats(simulator.getCurrentTime()));
        simulator.performActionWithoutTimeAdvance(characterId, action);
    }

    private void resolveCharged(
            CombatSimulator simulator,
            PendingHit hit) {
        boolean infused = mirrorCount > 0;
        AttackAction action = attack(
                "Abductive Reasoning Charged Attack " + (hit.index + 1),
                getTalentValue(
                        "Charged Attack " + (hit.index + 1), 1.01515),
                infused ? Element.DENDRO : Element.PHYSICAL,
                StatType.CHARGED_ATTACK_DMG_BONUS,
                ActionType.CHARGE,
                infused ? ICDType.AlhaithamCharged : ICDType.None,
                infused ? ICDTag.Alhaitham_Charged : ICDTag.ChargedAttack,
                infused ? 1.0 : 0.0);
        action.setStatSnapshot(captureLiveStats(simulator.getCurrentTime()));
        simulator.performActionWithoutTimeAdvance(characterId, action);
    }

    private void resolvePlunge(
            CombatSimulator simulator,
            PendingHit hit) {
        boolean infused = mirrorCount > 0;
        AttackAction action = attack(
                "Abductive Reasoning High Plunge",
                getTalentValue("High Plunge", 2.933586),
                infused ? Element.DENDRO : Element.PHYSICAL,
                StatType.PLUNGING_ATTACK_DMG_BONUS,
                ActionType.PLUNGE,
                ICDType.None,
                ICDTag.PlungeAttack,
                infused ? 1.0 : 0.0);
        action.setStatSnapshot(captureLiveStats(simulator.getCurrentTime()));
        simulator.performActionWithoutTimeAdvance(characterId, action);
    }

    private void resolveSkill(
            CombatSimulator simulator,
            PendingHit hit) {
        StatsContainer snapshot = captureLiveStats(simulator.getCurrentTime());
        double emRatio = getTalentValue(
                constellation >= 3 ? "Rush EM C3" : "Rush EM",
                constellation >= 3 ? 3.0976 : 2.63296);
        snapshot.add(StatType.FLAT_DMG_BONUS,
                emRatio * hit.snapshot.get(StatType.ELEMENTAL_MASTERY));
        AttackAction action = attack(
                "Universality: An Elaboration on Form",
                getTalentValue(
                        constellation >= 3 ? "Rush ATK C3" : "Rush ATK",
                        constellation >= 3 ? 3.872 : 3.2912),
                Element.DENDRO,
                StatType.SKILL_DMG_BONUS,
                ActionType.SKILL,
                ICDType.None,
                ICDTag.ElementalSkill,
                1.0);
        action.setStatSnapshot(snapshot);
        simulator.performActionWithoutTimeAdvance(characterId, action);
    }

    private void resolveProjection(
            CombatSimulator simulator,
            PendingHit hit) {
        AttackAction action = attack(
                "Chisel-Light Mirror: Projection Attack " + hit.value,
                getTalentValue(
                        constellation >= 3
                                ? "Projection ATK C3" : "Projection ATK",
                        constellation >= 3 ? 1.344 : 1.1424),
                Element.DENDRO,
                StatType.SKILL_DMG_BONUS,
                ActionType.SKILL,
                ICDType.AlhaithamProjection,
                ICDTag.Alhaitham_Projection,
                1.0);
        action.setStatSnapshot(hit.snapshot);
        resolvingProjectionAction = action;
        try {
            simulator.performActionWithoutTimeAdvance(characterId, action);
        } finally {
            resolvingProjectionAction = null;
        }
    }

    private void resolveBurst(
            CombatSimulator simulator,
            PendingHit hit) {
        AttackAction action = attack(
                "Particular Field: Fetters of Phenomena "
                        + (hit.index + 1),
                getTalentValue(
                        constellation >= 5 ? "Burst ATK C5" : "Burst ATK",
                        constellation >= 5 ? 2.432 : 2.0672),
                Element.DENDRO,
                StatType.BURST_DMG_BONUS,
                ActionType.BURST,
                ICDType.Standard,
                ICDTag.ElementalBurst,
                1.0);
        action.setStatSnapshot(hit.snapshot);
        simulator.performActionWithoutTimeAdvance(characterId, action);
    }

    private void handleResolvedDamage(
            CombatSimulator simulator,
            Character actor,
            AttackAction action,
            double damage,
            double time) {
        if (actor != this || action == null || damage <= 0.0) {
            return;
        }
        if (action == resolvingProjectionAction) {
            handleProjectionHit(simulator, time);
            return;
        }
        ActionType type = action.getActionType();
        if (type != ActionType.NORMAL
                && type != ActionType.CHARGE
                && type != ActionType.PLUNGE) {
            return;
        }
        if ((type == ActionType.CHARGE || type == ActionType.PLUNGE)
                && time + EPSILON >= nextA1Time) {
            nextA1Time = time + getTalentValue("A1 Cooldown", 12.0);
            gainMirrors(simulator, time, 1);
        }
        if (simulator.getActiveCharacter() == this
                && mirrorCount > 0
                && time + EPSILON >= nextProjectionTime) {
            triggerProjection(simulator, time, mirrorCount);
        }
    }

    private void triggerProjection(
            CombatSimulator simulator,
            double triggerTime,
            int count) {
        nextProjectionTime = triggerTime
                + getTalentValue("Projection Cooldown", 1.6);
        queueCommand(simulator, new PendingCommand(
                triggerTime + PROJECTION_SNAPSHOT_FRAMES[count - 1] * FRAME,
                CommandKind.PROJECTION_SNAPSHOT,
                0L,
                count,
                captureLiveStats(triggerTime).get(
                        StatType.ELEMENTAL_MASTERY)));
    }

    private void snapshotProjection(
            CombatSimulator simulator,
            int count,
            double triggerElementalMastery) {
        double triggerTime = simulator.getCurrentTime()
                - PROJECTION_SNAPSHOT_FRAMES[count - 1] * FRAME;
        StatsContainer snapshot = captureLiveStats(
                simulator.getCurrentTime());
        double emRatio = getTalentValue(
                constellation >= 3
                        ? "Projection EM C3" : "Projection EM",
                constellation >= 3 ? 2.688 : 2.2848);
        snapshot.add(StatType.FLAT_DMG_BONUS,
                emRatio * triggerElementalMastery);
        double a4Bonus = Math.min(
                getTalentValue("A4 Maximum DMG Bonus", 1.0),
                snapshot.get(StatType.ELEMENTAL_MASTERY)
                        * getTalentValue(
                                "A4 EM DMG Bonus Ratio", 0.001));
        snapshot.add(StatType.SKILL_DMG_BONUS, a4Bonus);
        for (int index = 0; index < count; index++) {
            queueHit(simulator, new PendingHit(
                    triggerTime
                            + PROJECTION_HIT_FRAMES[count - 1][index] * FRAME,
                    HitKind.PROJECTION,
                    index,
                    0,
                    count,
                    0L,
                    snapshot));
        }
    }

    private void handleProjectionHit(
            CombatSimulator simulator,
            double currentTime) {
        if (constellation >= 1
                && currentTime + EPSILON >= nextC1Time) {
            reduceSkillCooldown(
                    currentTime,
                    getTalentValue("C1 Skill Cooldown Reduction", 1.2));
            nextC1Time = currentTime
                    + getTalentValue("C1 Trigger Cooldown", 1.0);
        }
        if (currentTime + EPSILON < nextParticleTime) {
            return;
        }
        nextParticleTime = currentTime
                + getTalentValue("Projection Particle Cooldown", 1.5);
        queueCommand(simulator, new PendingCommand(
                currentTime + getTalentValue(
                        "Projection Particle Travel Frames", 100.0) * FRAME,
                CommandKind.PARTICLE,
                0L,
                1));
    }

    private void snapshotBurst(
            CombatSimulator simulator,
            long generation,
            int hitCount) {
        if (generation != burstGeneration) {
            return;
        }
        double snapshotTime = simulator.getCurrentTime();
        double castTime = snapshotTime
                - getTalentValue("Burst Snapshot Frames", 67.0) * FRAME;
        StatsContainer snapshot = captureLiveStats(snapshotTime);
        double emRatio = getTalentValue(
                constellation >= 3 ? "Burst EM C3" : "Burst EM",
                constellation >= 3 ? 1.9456 : 1.65376);
        snapshot.add(StatType.FLAT_DMG_BONUS,
                emRatio * burstCastElementalMastery);
        double a4Bonus = Math.min(
                getTalentValue("A4 Maximum DMG Bonus", 1.0),
                snapshot.get(StatType.ELEMENTAL_MASTERY)
                        * getTalentValue(
                                "A4 EM DMG Bonus Ratio", 0.001));
        snapshot.add(StatType.BURST_DMG_BONUS, a4Bonus);
        for (int index = 0; index < hitCount; index++) {
            queueHit(simulator, new PendingHit(
                    castTime + (getTalentValue(
                            "Burst First Hit Frames", 94.0)
                            + index * getTalentValue(
                                    "Burst Hit Interval Frames", 21.0))
                            * FRAME,
                    HitKind.BURST,
                    index,
                    0,
                    hitCount,
                    generation,
                    snapshot));
        }
    }

    private void gainMirrors(
            CombatSimulator simulator,
            double currentTime,
            int generated) {
        if (generated <= 0) {
            return;
        }
        if (constellation >= 2) {
            for (int index = 0; index < generated; index++) {
                c2Expirations[c2Cursor] = currentTime
                        + getTalentValue("C2 Duration", 8.0);
                c2Cursor = (c2Cursor + 1) % c2Expirations.length;
            }
        }
        int previous = mirrorCount;
        int overflow = Math.max(0, previous + generated - 3);
        mirrorCount = Math.min(3, previous + generated);
        if (constellation >= 6 && overflow > 0) {
            extendC6(currentTime, overflow);
        }
        if (previous == 0 || overflow > 0) {
            long generation = ++mirrorGeneration;
            queueCommand(simulator, new PendingCommand(
                    currentTime + getTalentValue(
                            "Mirror Interval Frames", 233.0) * FRAME,
                    CommandKind.MIRROR_LOSS,
                    generation,
                    1));
        }
    }

    private int consumeMirrors() {
        int consumed = mirrorCount;
        mirrorCount = 0;
        mirrorGeneration++;
        return consumed;
    }

    private void loseMirror(
            CombatSimulator simulator,
            long generation) {
        if (generation != mirrorGeneration || mirrorCount <= 0) {
            return;
        }
        mirrorCount--;
        if (mirrorCount > 0) {
            queueCommand(simulator, new PendingCommand(
                    simulator.getCurrentTime() + getTalentValue(
                            "Mirror Interval Frames", 233.0) * FRAME,
                    CommandKind.MIRROR_LOSS,
                    generation,
                    1));
        }
    }

    private void extendC6(double currentTime, int overflow) {
        double duration = getTalentValue("C6 Duration", 6.0);
        double extension = getTalentValue(
                "C6 Extension Per Overflow", 6.0);
        for (int index = 0; index < overflow; index++) {
            if (currentTime < c6ExpirationTime) {
                c6ExpirationTime += extension;
            } else {
                c6ExpirationTime = currentTime + duration;
            }
        }
    }

    private void applyC4PartyBuff(
            CombatSimulator simulator,
            double currentTime,
            int consumed) {
        for (Character member : simulator.getPartyMembers()) {
            if (member == this) {
                continue;
            }
            member.removeBuff(BuffId.ALHAITHAM_C4_PARTY_ELEMENTAL_MASTERY);
            if (consumed > 0) {
                member.addBuff(new SimpleBuff(
                        "Alhaitham Elucidation Party EM",
                        BuffId.ALHAITHAM_C4_PARTY_ELEMENTAL_MASTERY,
                        getTalentValue("C4 Duration", 15.0),
                        currentTime,
                        stats -> stats.add(
                                StatType.ELEMENTAL_MASTERY,
                                consumed * getTalentValue(
                                        "C4 Party EM Per Consumed Mirror",
                                        30.0)))
                        .sourcedBy(characterId));
            }
        }
    }

    private void applyC4OwnerBuff(double currentTime, int generated) {
        removeBuff(BuffId.ALHAITHAM_C4_DENDRO_DMG_BONUS);
        if (generated <= 0) {
            return;
        }
        addBuff(new SimpleBuff(
                "Alhaitham Elucidation Dendro DMG",
                BuffId.ALHAITHAM_C4_DENDRO_DMG_BONUS,
                getTalentValue("C4 Duration", 15.0),
                currentTime,
                stats -> stats.add(
                        StatType.DENDRO_DMG_BONUS,
                        generated * getTalentValue(
                                "C4 Dendro DMG Per Generated Mirror", 0.10))));
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

    private double resolveNormalDuration(
            CombatSimulator simulator,
            double duration) {
        StatsContainer stats = captureLiveStats(simulator.getCurrentTime());
        double speed = Math.min(
                0.60,
                stats.get(StatType.ATK_SPD)
                        + stats.get(StatType.NORMAL_ATTACK_SPD));
        return speed <= 0.0 ? duration : duration / (1.0 + speed);
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
                case SKILL_GAIN:
                    if (command.generation == skillGeneration) {
                        markSkillUsed(
                                activeSimulator.getCurrentTime(),
                                activeSimulator.getApplicableBuffs(this));
                        gainMirrors(
                                activeSimulator,
                                activeSimulator.getCurrentTime(),
                                mirrorCount == 0 ? 2 : 1);
                    }
                    break;
                case MIRROR_LOSS:
                    loseMirror(activeSimulator, command.generation);
                    break;
                case PROJECTION_SNAPSHOT:
                    snapshotProjection(
                            activeSimulator,
                            command.value,
                            command.scalar);
                    break;
                case BURST_ENERGY:
                    if (command.generation == burstGeneration) {
                        spendBurstEnergy(activeSimulator.getCurrentTime());
                    }
                    break;
                case BURST_SNAPSHOT:
                    snapshotBurst(
                            activeSimulator,
                            command.generation,
                            command.value);
                    break;
                case BURST_MIRRORS:
                    if (command.generation == burstGeneration
                            && activeSimulator.getActiveCharacter() == this) {
                        gainMirrors(
                                activeSimulator,
                                activeSimulator.getCurrentTime(),
                                command.value);
                        if (constellation >= 4) {
                            applyC4OwnerBuff(
                                    activeSimulator.getCurrentTime(),
                                    command.value);
                        }
                    }
                    break;
                case PARTICLE:
                    activeSimulator.getEnergyDistributor().distributeParticles(
                            Element.DENDRO,
                            command.value,
                            ParticleType.PARTICLE);
                    break;
                default:
                    throw new IllegalStateException(
                            "Unknown Alhaitham command kind");
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
        CHARGED,
        PLUNGE,
        SKILL,
        PROJECTION,
        BURST
    }

    private enum CommandKind {
        SKILL_GAIN,
        MIRROR_LOSS,
        PROJECTION_SNAPSHOT,
        BURST_ENERGY,
        BURST_SNAPSHOT,
        BURST_MIRRORS,
        PARTICLE
    }

    /** Immutable delayed Alhaitham hit description. */
    private static final class PendingHit {
        private final double time;
        private final HitKind kind;
        private final int index;
        private final int subIndex;
        private final int value;
        private final long generation;
        private final StatsContainer snapshot;

        private PendingHit(
                double time,
                HitKind kind,
                int index,
                int subIndex,
                int value,
                long generation,
                StatsContainer snapshot) {
            this.time = time;
            this.kind = kind;
            this.index = index;
            this.subIndex = subIndex;
            this.value = value;
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
                    value,
                    generation,
                    snapshot);
        }
    }

    /** Immutable delayed Alhaitham command description. */
    private static final class PendingCommand {
        private final double time;
        private final CommandKind kind;
        private final long generation;
        private final int value;
        private final double scalar;

        private PendingCommand(
                double time,
                CommandKind kind,
                long generation,
                int value) {
            this(time, kind, generation, value, 0.0);
        }

        private PendingCommand(
                double time,
                CommandKind kind,
                long generation,
                int value,
                double scalar) {
            this.time = time;
            this.kind = kind;
            this.generation = generation;
            this.value = value;
            this.scalar = scalar;
        }

        private PendingCommand copy() {
            return new PendingCommand(
                    time, kind, generation, value, scalar);
        }
    }

    /** Immutable owner-bound Alhaitham rollback payload. */
    private static final class AlhaithamState implements State {
        private final Alhaitham owner;
        private final int normalAttackStep;
        private final int mirrorCount;
        private final long mirrorGeneration;
        private final long skillGeneration;
        private final long burstGeneration;
        private final double nextProjectionTime;
        private final double nextParticleTime;
        private final double nextA1Time;
        private final double nextC1Time;
        private final double burstCastElementalMastery;
        private final double[] c2Expirations;
        private final int c2Cursor;
        private final double c6ExpirationTime;
        private final List<PendingHit> pendingHits;
        private final List<PendingCommand> pendingCommands;

        private AlhaithamState(
                Alhaitham owner,
                int normalAttackStep,
                int mirrorCount,
                long mirrorGeneration,
                long skillGeneration,
                long burstGeneration,
                double nextProjectionTime,
                double nextParticleTime,
                double nextA1Time,
                double nextC1Time,
                double burstCastElementalMastery,
                double[] c2Expirations,
                int c2Cursor,
                double c6ExpirationTime,
                List<PendingHit> pendingHits,
                List<PendingCommand> pendingCommands) {
            this.owner = owner;
            this.normalAttackStep = normalAttackStep;
            this.mirrorCount = mirrorCount;
            this.mirrorGeneration = mirrorGeneration;
            this.skillGeneration = skillGeneration;
            this.burstGeneration = burstGeneration;
            this.nextProjectionTime = nextProjectionTime;
            this.nextParticleTime = nextParticleTime;
            this.nextA1Time = nextA1Time;
            this.nextC1Time = nextC1Time;
            this.burstCastElementalMastery = burstCastElementalMastery;
            this.c2Expirations = Arrays.copyOf(
                    c2Expirations, c2Expirations.length);
            this.c2Cursor = c2Cursor;
            this.c6ExpirationTime = c6ExpirationTime;
            this.pendingHits = copyHits(pendingHits);
            this.pendingCommands = copyCommands(pendingCommands);
        }
    }
}

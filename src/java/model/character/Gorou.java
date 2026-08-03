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
import simulation.event.SimpleTimerEvent;

/**
 * Gorou's stationary single-target field-support kit through C6.
 *
 * <p>Lv. 90 values, hitmarks, cooldown and Energy timing, gauge, ICD, field
 * cadence, and constellation boundaries follow pinned gcsim
 * {@code ef41805d} and KQM TCL {@code 80ba6241}. General's War Banner and
 * General's Glory update the active character every 0.3 seconds and each
 * update lingers for two seconds. Crystal Collapse and A4 read live impact
 * stats.</p>
 *
 * <p>C4 healing, shields, shard pulling, interruption resistance, geometry,
 * hitlag, multi-target selection, and aimed attacks are intentionally
 * excluded. Standard Crystallize pickup is exposed as an explicit notification
 * because the simulator has no shard-pickup event; Lunar-Crystallize can extend
 * C2 directly from its sourced reaction event.</p>
 */
public final class Gorou extends Character implements
        ReactionAwareCharacter,
        SimulatorInitializedCharacterEffect,
        SnapshotAwareCharacterEffect,
        SwitchAwareCharacter,
        TargetDependentTeamEffect {
    private static final double FRAME = 1.0 / 60.0;
    private static final double EPSILON = 1e-9;
    private static final double PARTICLE_TRAVEL = 100.0 * FRAME;
    private static final int[] NORMAL_RELEASES = { 17, 12, 27, 31 };
    private static final int[] NORMAL_DURATIONS = { 21, 26, 43, 55 };
    private static final double[] NORMAL_MULTIPLIERS = {
            0.69362, 0.68256, 0.9085, 1.08388
    };
    private static final double FIELD_TICK_DELAY = 17.0 * FRAME;
    private static final double FIELD_TICK_INTERVAL = 18.0 * FRAME;
    private static final double FIELD_LINGER = 120.0 * FRAME;
    private static final double SKILL_FIELD_DURATION = 600.0 * FRAME;
    private static final double BURST_FIELD_DURATION = 540.0 * FRAME;
    private static final double C2_MAX_EXTENSION = 180.0 * FRAME;
    private static final double COLLAPSE_INTERVAL = 90.0 * FRAME;

    private CombatSimulator initializedSimulator;
    private int normalAttackStep;
    private long skillGeneration;
    private long burstGeneration;
    private long fieldGeneration;
    private long latestFieldTickSerial;
    private FieldKind fieldKind = FieldKind.NONE;
    private double fieldEndTime = Double.NEGATIVE_INFINITY;
    private int fieldGeoCount;
    private double fieldDefBonus;
    private int c2ExtensionCount;
    private double nextC1Time = Double.NEGATIVE_INFINITY;
    private double nextC2Time = Double.NEGATIVE_INFINITY;
    private double c6ExpirationTime = Double.NEGATIVE_INFINITY;
    private double c6GeoCritDmg;
    private List<PendingHit> pendingHits = new ArrayList<>();
    private List<PendingCommand> pendingCommands = new ArrayList<>();

    /** Constructs repository-default C6 Gorou. */
    public Gorou(Weapon weapon, ArtifactSet artifacts) {
        this(weapon, artifacts, TalentDataManager.getInstance(), 6);
    }

    /** Constructs Gorou at an explicit constellation. */
    public Gorou(Weapon weapon, ArtifactSet artifacts, int constellation) {
        this(weapon, artifacts, TalentDataManager.getInstance(), constellation);
    }

    /** Constructs Gorou with injectable talent data and constellation state. */
    public Gorou(
            Weapon weapon,
            ArtifactSet artifacts,
            TalentDataSource talentData,
            int constellation) {
        super(talentData);
        if (constellation < 0 || constellation > 6) {
            throw new IllegalArgumentException(
                    "Gorou constellation must be between 0 and 6");
        }
        name = "Gorou";
        characterId = CharacterId.GOROU;
        element = Element.GEO;
        this.weapon = weapon;
        this.artifacts = new ArtifactSet[] { artifacts };
        this.constellation = constellation;
        baseStats.set(StatType.BASE_HP,
                getTalentValue("Base HP", 9570.0));
        baseStats.set(StatType.BASE_ATK,
                getTalentValue("Base ATK", 183.0));
        baseStats.set(StatType.BASE_DEF,
                getTalentValue("Base DEF", 648.0));
        baseStats.add(StatType.GEO_DMG_BONUS,
                getTalentValue("Ascension Geo DMG", 0.24));
        setSkillCD(10.0);
        setBurstCD(20.0);
    }

    /** Binds Gorou's C1/C2 listeners and owned events to one simulator. */
    @Override
    public void initializeForSimulator(CombatSimulator simulator) {
        if (simulator == null) {
            throw new IllegalArgumentException("Gorou simulator is required");
        }
        if (initializedSimulator != null
                && initializedSimulator != simulator) {
            throw new IllegalStateException(
                    "Gorou cannot be reused across CombatSimulator instances");
        }
        if (initializedSimulator == simulator) {
            return;
        }
        initializedSimulator = simulator;
        simulator.addDamageListener((actor, action, damage, time) ->
                onResolvedDamage(actor, action, damage, time, simulator));
        simulator.addReactionListener(this);
    }

    /** Captures Gorou-owned windows and all reconstructible future work. */
    @Override
    public State captureCharacterState() {
        return new GorouState(
                this,
                normalAttackStep,
                skillGeneration,
                burstGeneration,
                fieldGeneration,
                latestFieldTickSerial,
                fieldKind,
                fieldEndTime,
                fieldGeoCount,
                fieldDefBonus,
                c2ExtensionCount,
                nextC1Time,
                nextC2Time,
                c6ExpirationTime,
                c6GeoCritDmg,
                pendingHits,
                pendingCommands);
    }

    /** Accepts only payloads captured from this exact Gorou instance. */
    @Override
    public boolean acceptsCharacterState(State state) {
        return state instanceof GorouState
                && ((GorouState) state).owner == this;
    }

    /** Restores every surviving Gorou-owned event exactly once. */
    @Override
    public void restoreCharacterState(
            State state,
            CombatSimulator simulator) {
        if (!acceptsCharacterState(state)) {
            throw new IllegalArgumentException(
                    "Unexpected Gorou character state");
        }
        initializeForSimulator(simulator);
        GorouState restored = (GorouState) state;
        normalAttackStep = restored.normalAttackStep;
        skillGeneration = restored.skillGeneration;
        burstGeneration = restored.burstGeneration;
        fieldGeneration = restored.fieldGeneration;
        latestFieldTickSerial = restored.latestFieldTickSerial;
        fieldKind = restored.fieldKind;
        fieldEndTime = restored.fieldEndTime;
        fieldGeoCount = restored.fieldGeoCount;
        fieldDefBonus = restored.fieldDefBonus;
        c2ExtensionCount = restored.c2ExtensionCount;
        nextC1Time = restored.nextC1Time;
        nextC2Time = restored.nextC2Time;
        c6ExpirationTime = restored.c6ExpirationTime;
        c6GeoCritDmg = restored.c6GeoCritDmg;
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

    /** Returns Gorou's 80-Energy Burst cost. */
    @Override
    public double getEnergyCost() {
        return getTalentValue("Energy Cost", 80.0);
    }

    /** Gorou has no unconditional stat passive in the modeled slice. */
    @Override
    public void applyPassive(StatsContainer stats) {
        // A1, A4, and C6 are action- or field-dependent.
    }

    /** Resets Gorou's Normal string while preserving his deployed fields. */
    @Override
    public void onSwitchOut(CombatSimulator simulator) {
        normalAttackStep = 0;
    }

    /** Returns whether a Skill or Burst field exists at the supplied time. */
    public boolean isFieldActive(double currentTime) {
        return fieldKind != FieldKind.NONE
                && currentTime + EPSILON >= fieldStartTime()
                && currentTime < fieldEndTime;
    }

    /** Returns the current field's half-open end time. */
    public double getFieldEndTime() {
        return fieldEndTime;
    }

    /** Returns the number of accepted C2 one-second extensions. */
    public int getC2ExtensionCount() {
        return c2ExtensionCount;
    }

    /** Returns the live C6 Geo-only CRIT DMG tier. */
    public double getC6GeoCritDmg(double currentTime) {
        return constellation >= 6 && currentTime < c6ExpirationTime
                ? c6GeoCritDmg : 0.0;
    }

    /** Applies C6 after snapshot resolution so deployables cannot retain it. */
    @Override
    public void applyTargetDependentTeamStats(
            StatsContainer stats,
            Character attacker,
            model.entity.Enemy target,
            AttackAction action,
            double currentTime) {
        if (initializedSimulator == null
                || attacker == null
                || !initializedSimulator.getPartyMembers().contains(attacker)
                || target == null
                || action == null
                || action.getElement() != Element.GEO) {
            return;
        }
        stats.add(StatType.GEO_CRIT_DMG,
                getC6GeoCritDmg(currentTime));
    }

    /**
     * Notifies Gorou that the active character explicitly obtained a standard
     * Crystallize shard.
     *
     * @param simulator bound simulator at the pickup time
     * @return {@code true} when C2 extended General's Glory
     */
    public boolean notifyCrystallizeShardObtained(
            CombatSimulator simulator) {
        initializeForSimulator(simulator);
        return tryExtendC2(simulator.getCurrentTime());
    }

    /** Extends C2 directly for active-source Lunar-Crystallize reactions. */
    @Override
    public void onReaction(
            ReactionResult result,
            Character source,
            double time,
            CombatSimulator simulator) {
        if (simulator != initializedSimulator
                || constellation < 2
                || result == null
                || result.getKind()
                        != ReactionResult.Kind.LUNAR_CRYSTALLIZE
                || source == null
                || source != simulator.getActiveCharacter()) {
            return;
        }
        tryExtendC2(time);
    }

    /** Dispatches Gorou's supported typed offensive actions. */
    @Override
    public void onAction(
            CharacterActionRequest request,
            CombatSimulator simulator) {
        if (request == null) {
            throw new IllegalArgumentException("Gorou action is required");
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
                inuzakaAllRoundDefense(simulator);
                break;
            case BURST:
                juugaForwardUntoVictory(simulator);
                break;
            default:
                throw new IllegalArgumentException(
                        "Unsupported action for Gorou: "
                                + request.getKey());
        }
    }

    private void normalAttack(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        int step = normalAttackStep;
        queueCommand(simulator, new PendingCommand(
                castTime + NORMAL_RELEASES[step] * FRAME,
                CommandKind.NORMAL_RELEASE,
                step,
                0.0));
        normalAttackStep = (normalAttackStep + 1)
                % NORMAL_MULTIPLIERS.length;
        simulator.advanceTime(NORMAL_DURATIONS[step] * FRAME);
    }

    private void highPlunge(CombatSimulator simulator) {
        AttackAction plunge = attack(
                "Ripping Fang Fletching High Plunge",
                getTalentValue("High Plunge", 2.607632),
                Element.PHYSICAL,
                StatType.BASE_ATK,
                StatType.PLUNGING_ATTACK_DMG_BONUS,
                ActionType.PLUNGE,
                ICDType.None,
                ICDTag.None,
                0.0,
                true);
        // Pinned sources expose no Gorou plunge frames; retain bow's 1 s policy.
        plunge.setAnimationDuration(1.0);
        simulator.performAction(characterId, plunge);
    }

    private void inuzakaAllRoundDefense(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        long generation = ++skillGeneration;
        queueCommand(simulator, new PendingCommand(
                castTime + 32.0 * FRAME,
                CommandKind.SKILL_COOLDOWN,
                generation,
                0.0));
        queueHit(simulator, new PendingHit(
                castTime + 34.0 * FRAME,
                HitKind.SKILL,
                0,
                generation,
                null));
        simulator.advanceTime(47.0 * FRAME);
    }

    private void juugaForwardUntoVictory(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        long generation = ++burstGeneration;
        markBurstCooldownUsed(
                castTime, simulator.getApplicableBuffs(this));
        queueCommand(simulator, new PendingCommand(
                castTime + 7.0 * FRAME,
                CommandKind.BURST_ENERGY,
                generation,
                0.0));
        queueHit(simulator, new PendingHit(
                castTime + 31.0 * FRAME,
                HitKind.BURST_INITIAL,
                0,
                generation,
                null));
        simulator.advanceTime(56.0 * FRAME);
    }

    private void resolveHit(CombatSimulator simulator, PendingHit hit) {
        if (isStale(hit)) {
            return;
        }
        switch (hit.kind) {
            case NORMAL:
                resolveNormal(simulator, hit);
                break;
            case SKILL:
                resolveSkill(simulator, hit);
                break;
            case BURST_INITIAL:
                resolveBurstInitial(simulator, hit);
                break;
            case CRYSTAL_COLLAPSE:
                resolveCrystalCollapse(simulator, hit);
                break;
            default:
                throw new IllegalStateException("Unknown Gorou hit kind");
        }
    }

    private void resolveNormal(
            CombatSimulator simulator,
            PendingHit hit) {
        AttackAction normal = attack(
                "Ripping Fang Fletching N" + (hit.index + 1),
                getTalentValue(
                        "N" + (hit.index + 1),
                        NORMAL_MULTIPLIERS[hit.index]),
                Element.PHYSICAL,
                StatType.BASE_ATK,
                StatType.NORMAL_ATTACK_DMG_BONUS,
                ActionType.NORMAL,
                ICDType.None,
                ICDTag.None,
                0.0,
                false);
        normal.setStatSnapshot(hit.snapshot);
        simulator.performActionWithoutTimeAdvance(characterId, normal);
    }

    private void resolveSkill(
            CombatSimulator simulator,
            PendingHit hit) {
        double currentTime = simulator.getCurrentTime();
        double additiveDamage = captureActionStats(
                simulator, currentTime).getTotalDef()
                * getTalentValue("A4 Skill DEF Ratio", 1.56);
        AttackAction skill = gorouAttack(
                "Inuzaka All-Round Defense",
                getTalentValue(
                        constellation >= 3
                                ? "Skill DMG C3" : "Skill DMG",
                        constellation >= 3 ? 2.144 : 1.8224),
                Element.GEO,
                StatType.BASE_ATK,
                StatType.SKILL_DMG_BONUS,
                ActionType.SKILL,
                ICDType.None,
                ICDTag.None,
                1.0,
                true,
                additiveDamage);
        if (simulator.getEnemy() != null) {
            queueCommand(simulator, new PendingCommand(
                    currentTime + PARTICLE_TRAVEL,
                    CommandKind.PARTICLE,
                    hit.generation,
                    2.0));
        }
        if (!isBurstFieldActive(currentTime)) {
            startField(
                    simulator,
                    FieldKind.SKILL,
                    currentTime,
                    SKILL_FIELD_DURATION);
        }
        simulator.performActionWithoutTimeAdvance(characterId, skill);
        applyC6(simulator, currentTime);
    }

    private void resolveBurstInitial(
            CombatSimulator simulator,
            PendingHit hit) {
        double currentTime = simulator.getCurrentTime();
        startField(
                simulator,
                FieldKind.BURST,
                currentTime,
                BURST_FIELD_DURATION);
        applyA1(simulator, currentTime);
        applyC6(simulator, currentTime);
        AttackAction burst = burstAttack(
                "Juuga: Forward Unto Victory",
                getTalentValue(
                        constellation >= 5
                                ? "Burst Initial C5" : "Burst Initial",
                        constellation >= 5 ? 1.96432 : 1.669672),
                simulator,
                currentTime,
                true);
        scheduleBurstWork(simulator, hit.generation, currentTime);
        simulator.performActionWithoutTimeAdvance(characterId, burst);
    }

    private void resolveCrystalCollapse(
            CombatSimulator simulator,
            PendingHit hit) {
        double currentTime = simulator.getCurrentTime();
        if (!isBurstFieldActive(currentTime)) {
            return;
        }
        AttackAction collapse = burstAttack(
                "Crystal Collapse",
                getTalentValue(
                        constellation >= 5
                                ? "Crystal Collapse C5"
                                : "Crystal Collapse",
                        constellation >= 5 ? 1.226 : 1.0421),
                simulator,
                currentTime,
                false);
        simulator.performActionWithoutTimeAdvance(characterId, collapse);
    }

    private AttackAction burstAttack(
            String displayName,
            double multiplier,
            CombatSimulator simulator,
            double currentTime,
            boolean blunt) {
        double additiveDamage = captureActionStats(
                simulator, currentTime).getTotalDef()
                * getTalentValue("A4 Burst DEF Ratio", 0.156);
        return gorouAttack(
                displayName,
                multiplier,
                Element.GEO,
                StatType.BASE_DEF,
                StatType.BURST_DMG_BONUS,
                ActionType.BURST,
                displayName.equals("Crystal Collapse")
                        ? ICDType.Standard : ICDType.None,
                displayName.equals("Crystal Collapse")
                        ? ICDTag.ElementalBurst : ICDTag.None,
                1.0,
                blunt,
                additiveDamage);
    }

    private void startField(
            CombatSimulator simulator,
            FieldKind kind,
            double startTime,
            double duration) {
        fieldGeneration++;
        fieldKind = kind;
        fieldEndTime = startTime + duration;
        fieldGeoCount = countGeoPartyMembers(simulator);
        fieldDefBonus = getTalentValue(
                constellation >= 3
                        ? "DEF Increase C3" : "DEF Increase",
                constellation >= 3 ? 412.32 : 350.472);
        c2ExtensionCount = 0;
        nextC2Time = Double.NEGATIVE_INFINITY;
        double maxDuration = duration;
        if (kind == FieldKind.BURST && constellation >= 2) {
            maxDuration += C2_MAX_EXTENSION;
        }
        for (double tick = startTime + FIELD_TICK_DELAY;
                tick < startTime + maxDuration - EPSILON;
                tick += FIELD_TICK_INTERVAL) {
            queueCommand(simulator, new PendingCommand(
                    tick,
                    CommandKind.FIELD_TICK,
                    fieldGeneration,
                    0.0));
        }
    }

    private void scheduleBurstWork(
            CombatSimulator simulator,
            long generation,
            double hitTime) {
        double maxEnd = hitTime + BURST_FIELD_DURATION;
        if (constellation >= 2) {
            maxEnd += C2_MAX_EXTENSION;
        }
        for (double tick = hitTime + COLLAPSE_INTERVAL;
                tick < maxEnd - EPSILON;
                tick += COLLAPSE_INTERVAL) {
            queueHit(simulator, new PendingHit(
                    tick,
                    HitKind.CRYSTAL_COLLAPSE,
                    0,
                    generation,
                    null));
        }
    }

    private void applyFieldTick(
            CombatSimulator simulator,
            long generation,
            double currentTime) {
        if (generation != fieldGeneration
                || !isFieldActive(currentTime)) {
            return;
        }
        long serial = ++latestFieldTickSerial;
        simulator.applyFieldBuff(new SimpleBuff(
                "Gorou General's War Banner",
                BuffId.GOROU_GENERAL_WAR_BANNER,
                FIELD_LINGER,
                currentTime,
                stats -> {
                    if (generation != fieldGeneration
                            || serial != latestFieldTickSerial) {
                        return;
                    }
                    stats.add(StatType.DEF_FLAT, fieldDefBonus);
                    if (fieldGeoCount >= 3) {
                        stats.add(StatType.GEO_DMG_BONUS, 0.15);
                    }
                }).sourcedBy(characterId));
    }

    private void applyA1(
            CombatSimulator simulator,
            double currentTime) {
        simulator.applyTeamBuffNoStack(new SimpleBuff(
                "Gorou Heedless of the Wind and Weather",
                BuffId.GOROU_A1_DEF_BONUS,
                12.0,
                currentTime,
                stats -> stats.add(
                        StatType.DEF_PERCENT,
                        getTalentValue("A1 DEF Bonus", 0.25)))
                .sourcedBy(characterId));
    }

    private void applyC6(
            CombatSimulator simulator,
            double currentTime) {
        if (constellation < 6) {
            return;
        }
        int geoCount = countGeoPartyMembers(simulator);
        double amount;
        if (geoCount >= 3) {
            amount = getTalentValue("C6 Crunch", 0.40);
        } else if (geoCount == 2) {
            amount = getTalentValue("C6 Impregnable", 0.20);
        } else {
            amount = getTalentValue("C6 Standing Firm", 0.10);
        }
        c6GeoCritDmg = amount;
        c6ExpirationTime = currentTime + 12.0;
    }

    private void onResolvedDamage(
            Character actor,
            AttackAction action,
            double damage,
            double time,
            CombatSimulator simulator) {
        if (simulator != initializedSimulator
                || constellation < 1
                || actor == null
                || actor == this
                || !simulator.getPartyMembers().contains(actor)
                || action == null
                || action.getElement() != Element.GEO
                || damage <= 0.0
                || !isFieldActive(time)
                || time + EPSILON < nextC1Time) {
            return;
        }
        reduceSkillCooldown(time, 2.0);
        nextC1Time = time + 600.0 * FRAME;
    }

    private boolean tryExtendC2(double time) {
        if (constellation < 2
                || !isBurstFieldActive(time)
                || c2ExtensionCount >= 3
                || time + EPSILON < nextC2Time) {
            return false;
        }
        fieldEndTime += 60.0 * FRAME;
        c2ExtensionCount++;
        nextC2Time = time + 6.0 * FRAME;
        return true;
    }

    private boolean isBurstFieldActive(double time) {
        return fieldKind == FieldKind.BURST
                && time < fieldEndTime;
    }

    private double fieldStartTime() {
        if (fieldKind == FieldKind.NONE) {
            return Double.POSITIVE_INFINITY;
        }
        return fieldEndTime - (fieldKind == FieldKind.SKILL
                ? SKILL_FIELD_DURATION
                : BURST_FIELD_DURATION
                        + c2ExtensionCount * 60.0 * FRAME);
    }

    private int countGeoPartyMembers(CombatSimulator simulator) {
        int count = 0;
        for (Character member : simulator.getPartyMembers()) {
            if (member.getElement() == Element.GEO) {
                count++;
            }
        }
        return Math.max(1, count);
    }

    private StatsContainer captureActionStats(
            CombatSimulator simulator,
            double currentTime) {
        StatsContainer stats = getEffectiveStats(currentTime);
        for (Buff buff : simulator.getApplicableBuffs(this)) {
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
        schedule(simulator, hit.time, activeSim -> {
            if (!pendingHits.remove(hit)) {
                return;
            }
            resolveHit(activeSim, hit);
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
                            captureActionStats(activeSim,
                                    activeSim.getCurrentTime())));
                    break;
                case SKILL_COOLDOWN:
                    if (command.generation == skillGeneration) {
                        markSkillUsed(
                                activeSim.getCurrentTime(),
                                activeSim.getApplicableBuffs(this));
                    }
                    break;
                case PARTICLE:
                    if (command.generation == skillGeneration) {
                        activeSim.getEnergyDistributor().distributeParticles(
                                Element.GEO,
                                command.value,
                                ParticleType.PARTICLE);
                    }
                    break;
                case BURST_ENERGY:
                    if (command.generation == burstGeneration) {
                        spendBurstEnergy(activeSim.getCurrentTime());
                    }
                    break;
                case FIELD_TICK:
                    applyFieldTick(
                            activeSim,
                            command.generation,
                            activeSim.getCurrentTime());
                    break;
                default:
                    throw new IllegalStateException(
                            "Unknown Gorou command kind");
            }
        });
    }

    private boolean isStale(PendingHit hit) {
        switch (hit.kind) {
            case SKILL:
                return hit.generation != skillGeneration;
            case BURST_INITIAL:
            case CRYSTAL_COLLAPSE:
                return hit.generation != burstGeneration;
            default:
                return false;
        }
    }

    private static AttackAction attack(
            String displayName,
            double multiplier,
            Element element,
            StatType scalingStat,
            StatType bonusStat,
            ActionType actionType,
            ICDType icdType,
            ICDTag icdTag,
            double gauge,
            boolean blunt) {
        AttackAction action = new AttackAction(
                displayName,
                multiplier,
                element,
                scalingStat,
                bonusStat,
                0.0,
                actionType);
        action.setICD(icdType, icdTag, gauge);
        action.setShatterTrigger(blunt);
        action.setCountsAsSkillDmg(actionType == ActionType.SKILL);
        action.setCountsAsBurstDmg(actionType == ActionType.BURST);
        return action;
    }

    private static AttackAction gorouAttack(
            String displayName,
            double multiplier,
            Element element,
            StatType scalingStat,
            StatType bonusStat,
            ActionType actionType,
            ICDType icdType,
            ICDTag icdTag,
            double gauge,
            boolean blunt,
            double additiveBaseDamage) {
        AttackAction action = new GorouAttackAction(
                displayName,
                multiplier,
                element,
                scalingStat,
                bonusStat,
                actionType,
                additiveBaseDamage);
        action.setICD(icdType, icdTag, gauge);
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

    private enum FieldKind {
        NONE,
        SKILL,
        BURST
    }

    private enum HitKind {
        NORMAL,
        SKILL,
        BURST_INITIAL,
        CRYSTAL_COLLAPSE
    }

    private enum CommandKind {
        NORMAL_RELEASE,
        SKILL_COOLDOWN,
        PARTICLE,
        BURST_ENERGY,
        FIELD_TICK
    }

    /**
     * Preserves Gorou A4's sourced mixed-stat base damage through the resolver's
     * generic Catalyze reset. Geo attacks cannot receive Aggravate or Spread,
     * so their fixed impact-time addition cannot conflict with that reset.
     */
    private static final class GorouAttackAction extends AttackAction {
        private final double fixedAdditiveBaseDamage;

        private GorouAttackAction(
                String displayName,
                double multiplier,
                Element element,
                StatType scalingStat,
                StatType bonusStat,
                ActionType actionType,
                double fixedAdditiveBaseDamage) {
            super(
                    displayName,
                    multiplier,
                    element,
                    scalingStat,
                    bonusStat,
                    0.0,
                    actionType);
            this.fixedAdditiveBaseDamage = fixedAdditiveBaseDamage;
        }

        @Override
        public void setAdditiveBaseDmgBonus(double value) {
            // The generic resolver clears Catalyze state; Gorou's A4 is fixed.
        }

        @Override
        public double getAdditiveBaseDmgBonus() {
            return fixedAdditiveBaseDamage;
        }
    }

    /** Immutable delayed Gorou hit description. */
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
            this.snapshot = snapshot == null
                    ? null : snapshot.merge(null);
        }

        private PendingHit copy() {
            return new PendingHit(
                    time, kind, index, generation, snapshot);
        }
    }

    /** Immutable delayed Gorou non-damage command. */
    private static final class PendingCommand {
        private final double time;
        private final CommandKind kind;
        private final long generation;
        private final double value;

        private PendingCommand(
                double time,
                CommandKind kind,
                long generation,
                double value) {
            this.time = time;
            this.kind = kind;
            this.generation = generation;
            this.value = value;
        }

        private PendingCommand copy() {
            return new PendingCommand(time, kind, generation, value);
        }
    }

    /** Immutable Gorou-owned simulator snapshot payload. */
    private static final class GorouState implements State {
        private final Gorou owner;
        private final int normalAttackStep;
        private final long skillGeneration;
        private final long burstGeneration;
        private final long fieldGeneration;
        private final long latestFieldTickSerial;
        private final FieldKind fieldKind;
        private final double fieldEndTime;
        private final int fieldGeoCount;
        private final double fieldDefBonus;
        private final int c2ExtensionCount;
        private final double nextC1Time;
        private final double nextC2Time;
        private final double c6ExpirationTime;
        private final double c6GeoCritDmg;
        private final List<PendingHit> pendingHits;
        private final List<PendingCommand> pendingCommands;

        private GorouState(
                Gorou owner,
                int normalAttackStep,
                long skillGeneration,
                long burstGeneration,
                long fieldGeneration,
                long latestFieldTickSerial,
                FieldKind fieldKind,
                double fieldEndTime,
                int fieldGeoCount,
                double fieldDefBonus,
                int c2ExtensionCount,
                double nextC1Time,
                double nextC2Time,
                double c6ExpirationTime,
                double c6GeoCritDmg,
                List<PendingHit> pendingHits,
                List<PendingCommand> pendingCommands) {
            this.owner = owner;
            this.normalAttackStep = normalAttackStep;
            this.skillGeneration = skillGeneration;
            this.burstGeneration = burstGeneration;
            this.fieldGeneration = fieldGeneration;
            this.latestFieldTickSerial = latestFieldTickSerial;
            this.fieldKind = fieldKind;
            this.fieldEndTime = fieldEndTime;
            this.fieldGeoCount = fieldGeoCount;
            this.fieldDefBonus = fieldDefBonus;
            this.c2ExtensionCount = c2ExtensionCount;
            this.nextC1Time = nextC1Time;
            this.nextC2Time = nextC2Time;
            this.c6ExpirationTime = c6ExpirationTime;
            this.c6GeoCritDmg = c6GeoCritDmg;
            this.pendingHits = copyHits(pendingHits);
            this.pendingCommands = copyCommands(pendingCommands);
        }
    }
}

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
import simulation.action.HitlagProfile;
import simulation.action.SkillActionMode;
import simulation.event.SimpleTimerEvent;

/**
 * Yaoyao's stationary single-target offensive radish slice through C5.
 *
 * <p>This bounded implementation follows pinned gcsim {@code ef41805d} for
 * action frames, Yuegui spawn and throw cadence, separate Skill/Burst radish
 * elemental-application windows, particles, Burst lifetime, and C2/C4. Skill
 * recasts replace the prior throwing Yuegui. Jumping Yuegui exist only while
 * Yaoyao remains on field during Moonjade Descent.</p>
 *
 * <p>Healing, HP-threshold targeting, movement-triggered A1, C1 geometry,
 * C6 Mega Radishes, collision, and multi-target geometry are intentionally
 * excluded rather than approximated.</p>
 */
public final class Yaoyao extends Character implements
        SimulatorInitializedCharacterEffect,
        SnapshotAwareCharacterEffect,
        SwitchAwareCharacter {
    private static final double FRAME = 1.0 / 60.0;
    private static final double EPSILON = 1e-9;
    /** Hitlag data pinned to gcsim {@code 3647a07a7cc3004bc1e79d9bb5f7444de20dceaa}. */
    private static final HitlagProfile[][] NORMAL_HITLAG = {
        { new HitlagProfile(0.06, 0.01, true, false, false) },
        { new HitlagProfile(0.06, 0.01, true, false, false) },
        { HitlagProfile.none(), new HitlagProfile(0.01, 0.01, true, false, false) },
        { new HitlagProfile(0.09, 0.01, true, false, false) }
    };
    private static final HitlagProfile CHARGED_HITLAG =
            new HitlagProfile(0.0, 0.01, true, true, false);
    private static final HitlagProfile RADISH_HITLAG =
            new HitlagProfile(0.0, 0.0, true, true, false);
    private static final HitlagProfile BURST_INITIAL_HITLAG =
            new HitlagProfile(0.02, 0.05, false, false, false);
    private static final int[][] NORMAL_HIT_FRAMES = {
        { 13 }, { 16 }, { 12, 31 }, { 21 }
    };
    private static final int[] NORMAL_DURATIONS = { 28, 31, 51, 59 };
    private static final double[][] NORMAL_T9 = {
        { 0.937003 },
        { 0.871623 },
        { 0.576463, 0.605282 },
        { 1.431764 }
    };
    private static final int[] BURST_YUEGUI_SPAWN_FRAMES = {
        104, 162, 221
    };

    private CombatSimulator initializedSimulator;
    private int normalAttackStep;
    private long skillGeneration;
    private long burstGeneration;
    private double burstExpirationTime = Double.NEGATIVE_INFINITY;
    private double nextSkillParticleAllowedTime = Double.NEGATIVE_INFINITY;
    private double nextSkillApplicationTime = Double.NEGATIVE_INFINITY;
    private double nextBurstApplicationTime = Double.NEGATIVE_INFINITY;
    private double nextC2EnergyAllowedTime = Double.NEGATIVE_INFINITY;
    private double c4ElementalMastery;
    private double c4ExpirationTime = Double.NEGATIVE_INFINITY;
    private List<PendingHit> pendingHits = new ArrayList<>();
    private List<PendingCommand> pendingCommands = new ArrayList<>();

    /** Constructs repository-default C6 Yaoyao. */
    public Yaoyao(Weapon weapon, ArtifactSet artifacts) {
        this(weapon, artifacts, TalentDataManager.getInstance(), 6);
    }

    /** Constructs Yaoyao at an explicit constellation. */
    public Yaoyao(
            Weapon weapon,
            ArtifactSet artifacts,
            int constellation) {
        this(
                weapon,
                artifacts,
                TalentDataManager.getInstance(),
                constellation);
    }

    /** Constructs Yaoyao with injectable talent data and constellation state. */
    public Yaoyao(
            Weapon weapon,
            ArtifactSet artifacts,
            TalentDataSource talentData,
            int constellation) {
        super(talentData);
        if (constellation < 0 || constellation > 6) {
            throw new IllegalArgumentException(
                    "Yaoyao constellation must be between 0 and 6");
        }
        name = "Yaoyao";
        characterId = CharacterId.YAOYAO;
        element = Element.DENDRO;
        this.weapon = weapon;
        this.artifacts = new ArtifactSet[] { artifacts };
        this.constellation = constellation;
        baseStats.set(StatType.BASE_HP,
                getTalentValue("Base HP", 12289.0));
        baseStats.set(StatType.BASE_ATK,
                getTalentValue("Base ATK", 212.0));
        baseStats.set(StatType.BASE_DEF,
                getTalentValue("Base DEF", 751.0));
        baseStats.add(StatType.HP_PERCENT,
                getTalentValue("Ascension HP Percent", 0.24));
        setSkillCD(getTalentValue("Skill Cooldown", 15.0));
        setBurstCD(getTalentValue("Burst Cooldown", 20.0));
    }

    /** Binds Yaoyao-owned delayed work to exactly one simulator. */
    @Override
    public void initializeForSimulator(CombatSimulator simulator) {
        if (simulator == null) {
            throw new IllegalArgumentException("Yaoyao simulator is required");
        }
        if (!simulator.getPartyMembers().contains(this)) {
            throw new IllegalArgumentException(
                    "Yaoyao must belong to the simulator party");
        }
        if (initializedSimulator != null
                && initializedSimulator != simulator) {
            throw new IllegalStateException(
                    "Yaoyao cannot be reused across simulators");
        }
        initializedSimulator = simulator;
    }

    /** Captures summon generations, windows, and reconstructable future work. */
    @Override
    public State captureCharacterState() {
        return new YaoyaoState(
                this,
                normalAttackStep,
                skillGeneration,
                burstGeneration,
                burstExpirationTime,
                nextSkillParticleAllowedTime,
                nextSkillApplicationTime,
                nextBurstApplicationTime,
                nextC2EnergyAllowedTime,
                c4ElementalMastery,
                c4ExpirationTime,
                pendingHits,
                pendingCommands);
    }

    /** Accepts state captured from this exact Yaoyao instance only. */
    @Override
    public boolean acceptsCharacterState(State state) {
        return state instanceof YaoyaoState
                && ((YaoyaoState) state).owner == this;
    }

    /** Restores state and registers each surviving future event once. */
    @Override
    public void restoreCharacterState(
            State state,
            CombatSimulator simulator) {
        if (!acceptsCharacterState(state)) {
            throw new IllegalArgumentException("Unexpected Yaoyao state");
        }
        initializeForSimulator(simulator);
        YaoyaoState restored = (YaoyaoState) state;
        normalAttackStep = restored.normalAttackStep;
        skillGeneration = restored.skillGeneration;
        burstGeneration = restored.burstGeneration;
        burstExpirationTime = restored.burstExpirationTime;
        nextSkillParticleAllowedTime =
                restored.nextSkillParticleAllowedTime;
        nextSkillApplicationTime = restored.nextSkillApplicationTime;
        nextBurstApplicationTime = restored.nextBurstApplicationTime;
        nextC2EnergyAllowedTime = restored.nextC2EnergyAllowedTime;
        c4ElementalMastery = restored.c4ElementalMastery;
        c4ExpirationTime = restored.c4ExpirationTime;
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

    /** Returns Yaoyao's 80-Energy Burst cost. */
    @Override
    public double getEnergyCost() {
        return getTalentValue("Energy Cost", 80.0);
    }

    /** Applies C4's cast-time Max-HP conversion during its sourced window. */
    @Override
    public void applyPassive(StatsContainer stats) {
        if (constellation >= 4
                && initializedSimulator != null
                && initializedSimulator.getCurrentTime() + EPSILON
                        < c4ExpirationTime) {
            stats.add(StatType.ELEMENTAL_MASTERY, c4ElementalMastery);
        }
    }

    /** Resets the Normal string and ends on-field-only Adeptal Legacy. */
    @Override
    public void onSwitchOut(CombatSimulator simulator) {
        normalAttackStep = 0;
        if (isBurstActive(simulator.getCurrentTime())) {
            endBurst();
        }
    }

    /** Returns whether Adeptal Legacy is active at the supplied timestamp. */
    public boolean isBurstActive(double currentTime) {
        return currentTime + EPSILON < burstExpirationTime;
    }

    /** Returns the active C4 Elemental Mastery amount for regression checks. */
    public double getC4ElementalMastery(double currentTime) {
        return currentTime + EPSILON < c4ExpirationTime
                ? c4ElementalMastery : 0.0;
    }

    /** Returns the number of unresolved Yaoyao-owned damage hits. */
    public int getPendingHitCount() {
        return pendingHits.size();
    }

    /** Dispatches Yaoyao's represented typed offensive actions. */
    @Override
    public void onAction(
            CharacterActionRequest request,
            CombatSimulator simulator) {
        if (request == null) {
            throw new IllegalArgumentException("Yaoyao action is required");
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
                            "Yaoyao Hold Skill is outside this slice");
                }
                raphanusSkyCluster(simulator);
                break;
            case BURST:
                moonjadeDescent(simulator);
                break;
            default:
                throw new IllegalArgumentException(
                        "Unsupported action for Yaoyao: "
                                + request.getKey());
        }
    }

    private void normalAttack(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        int step = normalAttackStep;
        for (int hit = 0; hit < NORMAL_HIT_FRAMES[step].length; hit++) {
            queueHit(simulator, new PendingHit(
                    castTime + NORMAL_HIT_FRAMES[step][hit] * FRAME,
                    HitKind.NORMAL,
                    step,
                    hit,
                    0L,
                    null));
        }
        normalAttackStep = (normalAttackStep + 1)
                % NORMAL_HIT_FRAMES.length;
        simulator.advanceTime(NORMAL_DURATIONS[step] * FRAME);
    }

    private void chargedAttack(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        queueHit(simulator, new PendingHit(
                castTime + 24.0 * FRAME,
                HitKind.CHARGED,
                0,
                0,
                0L,
                null));
        simulator.advanceTime(55.0 * FRAME);
    }

    private void highPlunge(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        queueHit(simulator, new PendingHit(
                castTime + 46.0 * FRAME,
                HitKind.PLUNGE,
                0,
                0,
                0L,
                null));
        simulator.advanceTime(77.0 * FRAME);
    }

    private void raphanusSkyCluster(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        long generation = ++skillGeneration;
        pendingHits.removeIf(hit -> hit.kind == HitKind.SKILL_RADISH);
        pendingCommands.removeIf(command ->
                command.kind == CommandKind.SKILL_SPAWN);
        activateC4(castTime);
        queueCommand(simulator, new PendingCommand(
                castTime + 15.0 * FRAME,
                CommandKind.SKILL_COOLDOWN,
                generation,
                0.0));
        queueCommand(simulator, new PendingCommand(
                castTime + getTalentValue(
                        "Skill Spawn Delay Frames", 63.0) * FRAME,
                CommandKind.SKILL_SPAWN,
                generation,
                0.0));
        simulator.advanceTime(52.0 * FRAME);
    }

    private void moonjadeDescent(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        long generation = ++burstGeneration;
        burstExpirationTime = castTime + getTalentValue(
                "Burst Duration Frames", 360.0) * FRAME;
        activateC4(castTime);
        markBurstCooldownUsed(
                castTime, simulator.getApplicableBuffs(this));
        StatsContainer castSnapshot = captureLiveStats(castTime);
        queueCommand(simulator, new PendingCommand(
                castTime + 7.0 * FRAME,
                CommandKind.BURST_ENERGY,
                generation,
                0.0));
        queueHit(simulator, new PendingHit(
                castTime + 16.0 * FRAME,
                HitKind.BURST_INITIAL,
                0,
                0,
                generation,
                castSnapshot));
        for (int spawnFrame : BURST_YUEGUI_SPAWN_FRAMES) {
            queueCommand(simulator, new PendingCommand(
                    castTime + spawnFrame * FRAME,
                    CommandKind.BURST_SPAWN,
                    generation,
                    0.0));
        }
        queueCommand(simulator, new PendingCommand(
                burstExpirationTime,
                CommandKind.BURST_EXPIRE,
                generation,
                0.0));
        simulator.advanceTime(63.0 * FRAME);
    }

    private void activateC4(double currentTime) {
        if (constellation < 4) {
            return;
        }
        StatsContainer stats = captureLiveStats(currentTime);
        c4ElementalMastery = Math.min(
                getTalentValue("C4 EM Cap", 120.0),
                stats.getTotalHp() * getTalentValue(
                        "C4 Max HP Conversion", 0.003));
        c4ExpirationTime = currentTime
                + getTalentValue("C4 Duration", 8.8);
    }

    private void spawnSkillYuegui(
            CombatSimulator simulator,
            long generation) {
        if (generation != skillGeneration) {
            return;
        }
        StatsContainer snapshot = captureLiveStats(simulator.getCurrentTime());
        int count = (int) getTalentValue("Skill Radish Count", 10.0);
        double firstHitFrames = getTalentValue(
                "Radish First Hit Frames", 42.0);
        double intervalFrames = getTalentValue(
                "Radish Interval Frames", 60.0);
        for (int index = 0; index < count; index++) {
            queueHit(simulator, new PendingHit(
                    simulator.getCurrentTime()
                            + (firstHitFrames + intervalFrames * index)
                                    * FRAME,
                    HitKind.SKILL_RADISH,
                    index,
                    0,
                    generation,
                    snapshot));
        }
    }

    private void spawnBurstYuegui(
            CombatSimulator simulator,
            long generation) {
        if (generation != burstGeneration
                || !isBurstActive(simulator.getCurrentTime())
                || simulator.getActiveCharacter() != this) {
            return;
        }
        StatsContainer snapshot = captureLiveStats(simulator.getCurrentTime());
        double firstHit = simulator.getCurrentTime()
                + getTalentValue("Radish First Hit Frames", 42.0) * FRAME;
        double interval = getTalentValue(
                "Radish Interval Frames", 60.0) * FRAME;
        int index = 0;
        for (double hitTime = firstHit;
                hitTime + EPSILON < burstExpirationTime;
                hitTime += interval) {
            queueHit(simulator, new PendingHit(
                    hitTime,
                    HitKind.BURST_RADISH,
                    index++,
                    0,
                    generation,
                    snapshot));
        }
    }

    private void resolveHit(
            CombatSimulator simulator,
            PendingHit hit) {
        switch (hit.kind) {
            case NORMAL:
                performHit(
                        simulator,
                        hit,
                        "Toss 'N' Turn Spear N" + (hit.index + 1),
                        normalValue(hit.index, hit.variant),
                        Element.PHYSICAL,
                        StatType.NORMAL_ATTACK_DMG_BONUS,
                        ActionType.NORMAL,
                        ICDTag.NormalAttack,
                        0.0);
                break;
            case CHARGED:
                performHit(
                        simulator,
                        hit,
                        "Toss 'N' Turn Spear Charged",
                        getTalentValue("Charged Attack", 2.069800),
                        Element.PHYSICAL,
                        StatType.CHARGED_ATTACK_DMG_BONUS,
                        ActionType.CHARGE,
                        ICDTag.ChargedAttack,
                        0.0);
                break;
            case PLUNGE:
                performHit(
                        simulator,
                        hit,
                        "Toss 'N' Turn Spear High Plunge",
                        getTalentValue("High Plunge", 2.933586),
                        Element.PHYSICAL,
                        StatType.PLUNGING_ATTACK_DMG_BONUS,
                        ActionType.PLUNGE,
                        ICDTag.PlungeAttack,
                        0.0);
                break;
            case SKILL_RADISH:
                if (hit.generation != skillGeneration) {
                    return;
                }
                resolveSkillRadish(simulator, hit);
                break;
            case BURST_INITIAL:
                if (hit.generation != burstGeneration) {
                    return;
                }
                performHit(
                        simulator,
                        hit,
                        "Moonjade Descent",
                        burstValue(
                                "Moonjade Descent",
                                1.947520,
                                2.291200),
                        Element.DENDRO,
                        StatType.BURST_DMG_BONUS,
                        ActionType.BURST,
                        ICDTag.None,
                        1.0);
                break;
            case BURST_RADISH:
                if (hit.generation != burstGeneration
                        || !isBurstActive(hit.time)
                        || simulator.getActiveCharacter() != this) {
                    return;
                }
                resolveBurstRadish(simulator, hit);
                break;
            default:
                throw new IllegalStateException(
                        "Unknown Yaoyao hit kind " + hit.kind);
        }
    }

    private void resolveSkillRadish(
            CombatSimulator simulator,
            PendingHit hit) {
        if (isBurstActive(hit.time)
                && simulator.getActiveCharacter() == this) {
            performBurstRadishDamage(simulator, hit);
        } else {
            double gauge = acceptElementApplication(
                    hit.time,
                    true) ? 1.0 : 0.0;
            performHit(
                    simulator,
                    hit,
                    "Yuegui White Jade Radish",
                    skillValue(),
                    Element.DENDRO,
                    StatType.SKILL_DMG_BONUS,
                    ActionType.SKILL,
                    ICDTag.ElementalSkill,
                    gauge);
        }
        if (simulator.getEnemy() == null
                || hit.time + EPSILON < nextSkillParticleAllowedTime) {
            return;
        }
        nextSkillParticleAllowedTime = hit.time
                + getTalentValue("Skill Particle ICD", 1.5);
        queueCommand(simulator, new PendingCommand(
                hit.time + getTalentValue(
                        "Particle Travel Frames", 100.0) * FRAME,
                CommandKind.PARTICLE,
                0L,
                1.0));
    }

    private void resolveBurstRadish(
            CombatSimulator simulator,
            PendingHit hit) {
        performBurstRadishDamage(simulator, hit);
    }

    private void performBurstRadishDamage(
            CombatSimulator simulator,
            PendingHit hit) {
        double gauge = acceptElementApplication(
                hit.time,
                false) ? 1.0 : 0.0;
        performHit(
                simulator,
                hit,
                "Adeptal Legacy White Jade Radish",
                burstValue(
                        "Adeptal Legacy Radish",
                        1.226720,
                        1.443200),
                Element.DENDRO,
                StatType.BURST_DMG_BONUS,
                ActionType.BURST,
                ICDTag.ElementalBurst,
                gauge);
        if (constellation >= 2
                && simulator.getEnemy() != null
                && hit.time + EPSILON >= nextC2EnergyAllowedTime) {
            receiveFlatEnergy(getTalentValue("C2 Energy", 3.0));
            nextC2EnergyAllowedTime = hit.time
                    + getTalentValue("C2 Energy ICD", 0.8);
        }
    }

    private boolean acceptElementApplication(
            double currentTime,
            boolean skillRadish) {
        if (skillRadish) {
            if (currentTime + EPSILON < nextSkillApplicationTime) {
                return false;
            }
            nextSkillApplicationTime = currentTime
                    + getTalentValue("Skill Elemental ICD", 2.5);
            return true;
        }
        if (currentTime + EPSILON < nextBurstApplicationTime) {
            return false;
        }
        nextBurstApplicationTime = currentTime + 1.5;
        return true;
    }

    private void performHit(
            CombatSimulator simulator,
            PendingHit hit,
            String displayName,
            double multiplier,
            Element hitElement,
            StatType bonusStat,
            ActionType actionType,
            ICDTag icdTag,
            double gaugeUnits) {
        AttackAction action = new AttackAction(
                displayName,
                multiplier,
                hitElement,
                StatType.BASE_ATK,
                bonusStat,
                0.0,
                actionType);
        // Character-local gates preserve Yaoyao's non-generic time-only ICDs.
        action.setICD(ICDType.None, icdTag, gaugeUnits);
        action.setHitlagProfile(hitlagProfile(hit));
        action.setCountsAsSkillDmg(actionType == ActionType.SKILL);
        action.setCountsAsBurstDmg(actionType == ActionType.BURST);
        action.setStatSnapshot(hit.snapshot == null
                ? captureLiveStats(simulator.getCurrentTime())
                : hit.snapshot);
        simulator.performActionWithoutTimeAdvance(characterId, action);
    }

    private static HitlagProfile hitlagProfile(PendingHit hit) {
        if (hit.kind == HitKind.NORMAL) {
            return NORMAL_HITLAG[hit.index][hit.variant];
        }
        if (hit.kind == HitKind.CHARGED) {
            return CHARGED_HITLAG;
        }
        if (hit.kind == HitKind.SKILL_RADISH
                || hit.kind == HitKind.BURST_RADISH) {
            return RADISH_HITLAG;
        }
        if (hit.kind == HitKind.BURST_INITIAL) {
            return BURST_INITIAL_HITLAG;
        }
        return HitlagProfile.none();
    }

    private double normalValue(int step, int variant) {
        String key = "N" + (step + 1);
        if (NORMAL_HIT_FRAMES[step].length > 1) {
            key += " Hit " + (variant + 1);
        }
        return getTalentValue(key, NORMAL_T9[step][variant]);
    }

    private double skillValue() {
        String key = constellation >= 3
                ? "White Jade Radish C3" : "White Jade Radish";
        return getTalentValue(key,
                constellation >= 3 ? 0.598400 : 0.508640);
    }

    private double burstValue(
            String key,
            double talentNine,
            double talentTwelve) {
        String suffix = constellation >= 5 ? " C5" : "";
        return getTalentValue(key + suffix,
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

    private void endBurst() {
        burstExpirationTime = Double.NEGATIVE_INFINITY;
        burstGeneration++;
        pendingHits.removeIf(hit ->
                hit.kind == HitKind.BURST_RADISH);
        pendingCommands.removeIf(command ->
                command.kind == CommandKind.BURST_SPAWN
                        || command.kind == CommandKind.BURST_EXPIRE);
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
                    if (command.generation == skillGeneration) {
                        markSkillUsed(
                                activeSimulator.getCurrentTime(),
                                activeSimulator.getApplicableBuffs(this));
                    }
                    break;
                case SKILL_SPAWN:
                    spawnSkillYuegui(
                            activeSimulator, command.generation);
                    break;
                case BURST_ENERGY:
                    if (command.generation == burstGeneration) {
                        spendBurstEnergy(activeSimulator.getCurrentTime());
                    }
                    break;
                case BURST_SPAWN:
                    spawnBurstYuegui(
                            activeSimulator, command.generation);
                    break;
                case BURST_EXPIRE:
                    if (command.generation == burstGeneration) {
                        endBurst();
                    }
                    break;
                case PARTICLE:
                    activeSimulator.getEnergyDistributor()
                            .distributeParticles(
                                    Element.DENDRO,
                                    command.value,
                                    ParticleType.PARTICLE);
                    break;
                default:
                    throw new IllegalStateException(
                            "Unknown Yaoyao command kind " + command.kind);
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
        CHARGED,
        PLUNGE,
        SKILL_RADISH,
        BURST_INITIAL,
        BURST_RADISH
    }

    private enum CommandKind {
        SKILL_COOLDOWN,
        SKILL_SPAWN,
        BURST_ENERGY,
        BURST_SPAWN,
        BURST_EXPIRE,
        PARTICLE
    }

    /** Immutable delayed hit with summon generation and spawn-time stats. */
    private static final class PendingHit {
        private final double time;
        private final HitKind kind;
        private final int index;
        private final int variant;
        private final long generation;
        private final StatsContainer snapshot;

        private PendingHit(
                double time,
                HitKind kind,
                int index,
                int variant,
                long generation,
                StatsContainer snapshot) {
            this.time = time;
            this.kind = kind;
            this.index = index;
            this.variant = variant;
            this.generation = generation;
            this.snapshot = snapshot == null
                    ? null : snapshot.merge(null);
        }

        private PendingHit copy() {
            return new PendingHit(
                    time, kind, index, variant, generation, snapshot);
        }
    }

    /** Immutable delayed state command. */
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

    /** Immutable snapshot of all mutable Yaoyao-owned simulator state. */
    private static final class YaoyaoState implements State {
        private final Yaoyao owner;
        private final int normalAttackStep;
        private final long skillGeneration;
        private final long burstGeneration;
        private final double burstExpirationTime;
        private final double nextSkillParticleAllowedTime;
        private final double nextSkillApplicationTime;
        private final double nextBurstApplicationTime;
        private final double nextC2EnergyAllowedTime;
        private final double c4ElementalMastery;
        private final double c4ExpirationTime;
        private final List<PendingHit> pendingHits;
        private final List<PendingCommand> pendingCommands;

        private YaoyaoState(
                Yaoyao owner,
                int normalAttackStep,
                long skillGeneration,
                long burstGeneration,
                double burstExpirationTime,
                double nextSkillParticleAllowedTime,
                double nextSkillApplicationTime,
                double nextBurstApplicationTime,
                double nextC2EnergyAllowedTime,
                double c4ElementalMastery,
                double c4ExpirationTime,
                List<PendingHit> pendingHits,
                List<PendingCommand> pendingCommands) {
            this.owner = owner;
            this.normalAttackStep = normalAttackStep;
            this.skillGeneration = skillGeneration;
            this.burstGeneration = burstGeneration;
            this.burstExpirationTime = burstExpirationTime;
            this.nextSkillParticleAllowedTime =
                    nextSkillParticleAllowedTime;
            this.nextSkillApplicationTime = nextSkillApplicationTime;
            this.nextBurstApplicationTime = nextBurstApplicationTime;
            this.nextC2EnergyAllowedTime = nextC2EnergyAllowedTime;
            this.c4ElementalMastery = c4ElementalMastery;
            this.c4ExpirationTime = c4ExpirationTime;
            this.pendingHits = copyHits(pendingHits);
            this.pendingCommands = copyCommands(pendingCommands);
        }
    }
}

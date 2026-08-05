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
 * Freminet's stationary single-target Pressure kit through C6.
 *
 * <p>Normal/Frost ordering, Pressure branches, particles, Burst acceleration,
 * reaction passives, and constellations follow pinned gcsim
 * {@code ef41805d}. The sourced 0U Spiritbreath hit is represented as direct
 * Cryo damage without adding a separate Arkhe aura system.</p>
 *
 * <p>Underwater utility, geometry, hitlag, airborne validation, and defensive
 * behavior are outside this vertical slice.</p>
 */
public final class Freminet extends Character implements
        ReactionAwareCharacter,
        SimulatorInitializedCharacterEffect,
        SnapshotAwareCharacterEffect,
        SwitchAwareCharacter {
    private static final double FRAME = 1.0 / 60.0;
    private static final double EPSILON = 1e-9;
    private static final double PARTICLE_TRAVEL = 100.0 * FRAME;
    private static final int[] NORMAL_HIT_FRAMES = { 26, 23, 31, 42 };
    private static final int[] NORMAL_DURATION_FRAMES = { 47, 49, 65, 86 };
    private static final int[] FROST_DELAY_FRAMES = { 10, 9, 10, 10 };
    private static final String[] NORMAL_KEYS = { "N1", "N2", "N3", "N4" };
    private static final double[] NORMAL_T9 = {
        1.547626, 1.482182, 1.872189, 2.274552
    };
    private static final double[] NORMAL_T12 = {
        1.900249, 1.819895, 2.298764, 2.792805
    };
    private static final double[] PRESSURE_CRYO_T9 = {
        3.408160, 1.704080, 1.192856, 0.681632
    };
    private static final double[] PRESSURE_CRYO_T12 = {
        4.009600, 2.004800, 1.403360, 0.801920
    };
    private static final double[] PRESSURE_PHYSICAL_T9 = {
        0.827696, 1.448468, 2.069240, 4.138480
    };
    private static final double[] PRESSURE_PHYSICAL_T12 = {
        0.973760, 1.704080, 2.434400, 4.868800
    };

    private CombatSimulator initializedSimulator;
    private int normalAttackStep;
    private int pressureLevel;
    private int c4Stacks;
    private int c6Stacks;
    private double persTimeUntil = Double.NEGATIVE_INFINITY;
    private double burstUntil = Double.NEGATIVE_INFINITY;
    private double a4Until = Double.NEGATIVE_INFINITY;
    private double c4Until = Double.NEGATIVE_INFINITY;
    private double c6Until = Double.NEGATIVE_INFINITY;
    private double nextReactionStackTime = Double.NEGATIVE_INFINITY;
    private double nextSpiritbreathTime = Double.NEGATIVE_INFINITY;
    private List<PendingHit> pendingHits = new ArrayList<>();
    private List<PendingCommand> pendingCommands = new ArrayList<>();

    /** Constructs repository-default C6 Freminet. */
    public Freminet(Weapon weapon, ArtifactSet artifacts) {
        this(weapon, artifacts, TalentDataManager.getInstance(), 6);
    }

    /** Constructs Freminet at an explicit constellation. */
    public Freminet(Weapon weapon, ArtifactSet artifacts, int constellation) {
        this(weapon, artifacts, TalentDataManager.getInstance(), constellation);
    }

    /** Constructs Freminet with injectable talent data. */
    public Freminet(
            Weapon weapon,
            ArtifactSet artifacts,
            TalentDataSource talentData,
            int constellation) {
        super(talentData);
        if (constellation < 0 || constellation > 6) {
            throw new IllegalArgumentException(
                    "Freminet constellation must be between 0 and 6");
        }
        name = "Freminet";
        characterId = CharacterId.FREMINET;
        element = Element.CRYO;
        this.weapon = weapon;
        this.artifacts = new ArtifactSet[] { artifacts };
        this.constellation = constellation;
        baseStats.set(StatType.BASE_HP,
                getTalentValue("Base HP", 12071.0));
        baseStats.set(StatType.BASE_ATK,
                getTalentValue("Base ATK", 255.0));
        baseStats.set(StatType.BASE_DEF,
                getTalentValue("Base DEF", 708.0));
        baseStats.add(StatType.ATK_PERCENT,
                getTalentValue("Ascension ATK", 0.24));
        setSkillCD(getTalentValue("Skill Cooldown", 10.0));
        setBurstCD(getTalentValue("Burst Cooldown", 15.0));
    }

    /** Binds reaction and delayed-work state to one simulator. */
    @Override
    public void initializeForSimulator(CombatSimulator simulator) {
        if (simulator == null) {
            throw new IllegalArgumentException("Freminet simulator is required");
        }
        if (initializedSimulator != null
                && initializedSimulator != simulator) {
            throw new IllegalStateException(
                    "Freminet cannot be reused across simulators");
        }
        if (initializedSimulator == simulator) {
            return;
        }
        if (!simulator.getPartyMembers().contains(this)) {
            throw new IllegalArgumentException(
                    "Freminet must belong to the target simulator party");
        }
        initializedSimulator = simulator;
        simulator.addReactionListener(this);
    }

    /** Captures Pressure, windows, stacks, gates, and delayed work. */
    @Override
    public State captureCharacterState() {
        return new FreminetState(
                this,
                normalAttackStep,
                pressureLevel,
                c4Stacks,
                c6Stacks,
                persTimeUntil,
                burstUntil,
                a4Until,
                c4Until,
                c6Until,
                nextReactionStackTime,
                nextSpiritbreathTime,
                pendingHits,
                pendingCommands);
    }

    /** Accepts only state captured from this exact Freminet instance. */
    @Override
    public boolean acceptsCharacterState(State state) {
        return state instanceof FreminetState
                && ((FreminetState) state).owner == this;
    }

    /** Restores state and reconstructs each surviving event once. */
    @Override
    public void restoreCharacterState(
            State state,
            CombatSimulator simulator) {
        if (!acceptsCharacterState(state)) {
            throw new IllegalArgumentException("Unexpected Freminet state");
        }
        initializeForSimulator(simulator);
        FreminetState restored = (FreminetState) state;
        normalAttackStep = restored.normalAttackStep;
        pressureLevel = restored.pressureLevel;
        c4Stacks = restored.c4Stacks;
        c6Stacks = restored.c6Stacks;
        persTimeUntil = restored.persTimeUntil;
        burstUntil = restored.burstUntil;
        a4Until = restored.a4Until;
        c4Until = restored.c4Until;
        c6Until = restored.c6Until;
        nextReactionStackTime = restored.nextReactionStackTime;
        nextSpiritbreathTime = restored.nextSpiritbreathTime;
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

    /** Returns Freminet's 60-Energy Burst cost. */
    @Override
    public double getEnergyCost() {
        return getTalentValue("Energy Cost", 60.0);
    }

    /** Runtime passives are represented by timed state and owner buffs. */
    @Override
    public void applyPassive(StatsContainer stats) {
        // A4 and C1 are action-specific; C4/C6 use timed owner buffs.
    }

    /** Hides the base cooldown while Pers Time permits a Pressure recast. */
    @Override
    public double getSkillCDRemaining(double currentTime) {
        normalizeAt(currentTime);
        if (currentTime < persTimeUntil) {
            return 0.0;
        }
        return super.getSkillCDRemaining(currentTime);
    }

    /** Allows Pressure recasts while Pers Time is active. */
    @Override
    public boolean canSkill(double currentTime) {
        return getSkillCDRemaining(currentTime) <= EPSILON;
    }

    /** Ends Burst acceleration and resets the Normal string on switch-out. */
    @Override
    public void onSwitchOut(CombatSimulator simulator) {
        normalAttackStep = 0;
        if (simulator == initializedSimulator
                && simulator.getCurrentTime() < burstUntil) {
            burstUntil = Double.NEGATIVE_INFINITY;
        }
    }

    /** Applies reaction-driven A4, C4, and C6 state. */
    @Override
    public void onReaction(
            ReactionResult result,
            Character source,
            double time,
            CombatSimulator simulator) {
        if (simulator != initializedSimulator
                || source != this
                || result == null) {
            return;
        }
        ReactionResult.Kind kind = result.getKind();
        if (kind == ReactionResult.Kind.SHATTER) {
            a4Until = time + getTalentValue("A4 Duration", 5.0);
        }
        if (constellation < 4
                || (kind != ReactionResult.Kind.SHATTER
                        && kind != ReactionResult.Kind.FROZEN
                        && kind != ReactionResult.Kind.SUPERCONDUCT)
                || time < nextReactionStackTime) {
            return;
        }
        nextReactionStackTime = time
                + getTalentValue("C4 C6 Cooldown", 0.3);
        if (time >= c4Until) {
            c4Stacks = 0;
        }
        c4Stacks = Math.min(2, c4Stacks + 1);
        c4Until = time + getTalentValue("C4 Duration", 6.0);
        refreshC4Buff(time);
        if (constellation >= 6) {
            if (time >= c6Until) {
                c6Stacks = 0;
            }
            c6Stacks = Math.min(3, c6Stacks + 1);
            c6Until = time + getTalentValue("C6 Duration", 6.0);
            refreshC6Buff(time);
        }
    }

    /** Returns the current Pressure level after expiry normalization. */
    public int getPressureLevel(double currentTime) {
        normalizeAt(currentTime);
        return pressureLevel;
    }

    /** Returns whether Pers Time is active at the supplied time. */
    public boolean isPersTimeActive(double currentTime) {
        normalizeAt(currentTime);
        return currentTime < persTimeUntil;
    }

    /** Returns whether Subnautical Hunter mode is active. */
    public boolean isBurstActive(double currentTime) {
        normalizeAt(currentTime);
        return currentTime < burstUntil;
    }

    /** Returns the current C4 stack count after expiry normalization. */
    public int getC4Stacks(double currentTime) {
        normalizeAt(currentTime);
        return c4Stacks;
    }

    /** Returns the current C6 stack count after expiry normalization. */
    public int getC6Stacks(double currentTime) {
        normalizeAt(currentTime);
        return c6Stacks;
    }

    /** Dispatches Freminet's represented typed action set. */
    @Override
    public void onAction(
            CharacterActionRequest request,
            CombatSimulator simulator) {
        if (request == null) {
            throw new IllegalArgumentException("Freminet action is required");
        }
        initializeForSimulator(simulator);
        normalizeAt(simulator.getCurrentTime());
        if (request.getKey() != CharacterActionKey.NORMAL) {
            normalAttackStep = 0;
        }
        switch (request.getKey()) {
            case NORMAL:
                if (pressureLevel >= 4
                        && simulator.getCurrentTime() < persTimeUntil) {
                    normalAttackStep = 0;
                    detonatePressure(simulator);
                } else {
                    normalAttack(simulator);
                }
                break;
            case PLUNGE:
                highPlunge(simulator);
                break;
            case SKILL:
                if (simulator.getCurrentTime() < persTimeUntil) {
                    detonatePressure(simulator);
                } else {
                    upwardThrust(simulator);
                }
                break;
            case BURST:
                shadowhuntersAmbush(simulator);
                break;
            default:
                throw new IllegalArgumentException(
                        "Unsupported action for Freminet: " + request.getKey());
        }
    }

    private void normalAttack(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        int step = normalAttackStep;
        queueHit(simulator, new PendingHit(
                castTime + NORMAL_HIT_FRAMES[step] * FRAME,
                HitKind.NORMAL,
                step,
                0,
                null));
        if (castTime < persTimeUntil) {
            boolean burst = castTime < burstUntil;
            queueHit(simulator, new PendingHit(
                    castTime + (NORMAL_HIT_FRAMES[step]
                            + FROST_DELAY_FRAMES[step]) * FRAME,
                    HitKind.FROST,
                    step,
                    burst ? 1 : 0,
                    null));
            pressureLevel = Math.min(4,
                    pressureLevel + (burst ? 2 : 1));
        }
        normalAttackStep = (normalAttackStep + 1) % NORMAL_HIT_FRAMES.length;
        simulator.advanceTime(NORMAL_DURATION_FRAMES[step] * FRAME);
    }

    private void highPlunge(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        queueHit(simulator, new PendingHit(
                castTime + 47.0 * FRAME,
                HitKind.PLUNGE,
                0,
                0,
                null));
        simulator.advanceTime(78.0 * FRAME);
    }

    private void upwardThrust(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        pressureLevel = 0;
        persTimeUntil = castTime + getTalentValue("Pers Time Duration", 10.0);
        queueHit(simulator, new PendingHit(
                castTime + 29.0 * FRAME,
                HitKind.THRUST,
                0,
                0,
                captureLiveStats(castTime)));
        if (castTime >= nextSpiritbreathTime) {
            nextSpiritbreathTime = castTime
                    + getTalentValue("Spiritbreath Cooldown", 9.0);
            queueHit(simulator, new PendingHit(
                    castTime + 62.0 * FRAME,
                    HitKind.SPIRITBREATH,
                    0,
                    0,
                    null));
        }
        queueCommand(simulator, new PendingCommand(
                castTime + 35.0 * FRAME,
                CommandKind.SKILL_COOLDOWN,
                castTime < burstUntil ? 1 : 0));
        simulator.advanceTime(46.0 * FRAME);
    }

    private void detonatePressure(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        int level = pressureLevel;
        StatsContainer snapshot = captureLiveStats(castTime);
        if (constellation >= 1) {
            snapshot.add(StatType.CRIT_RATE,
                    getTalentValue("C1 Pressure CRIT Rate", 0.15));
        }
        if (castTime < a4Until) {
            snapshot.add(StatType.SKILL_DMG_BONUS,
                    getTalentValue("A4 Pressure DMG Bonus", 0.40));
        }
        int hitFrame = level == 4 ? 37 : 42;
        if (level <= 3) {
            queueHit(simulator, new PendingHit(
                    castTime + hitFrame * FRAME,
                    HitKind.PRESSURE_CRYO,
                    level,
                    0,
                    snapshot));
        }
        if (level >= 1) {
            queueHit(simulator, new PendingHit(
                    castTime + hitFrame * FRAME,
                    HitKind.PRESSURE_PHYSICAL,
                    level,
                    0,
                    snapshot));
        }
        if (level < 4) {
            reduceSkillCooldown(castTime,
                    getTalentValue("A1 Skill Cooldown Reduction", 1.0));
        }
        if (constellation >= 2) {
            receiveFlatEnergy(level == 4
                    ? getTalentValue("C2 Level 4 Energy", 3.0)
                    : getTalentValue("C2 Energy", 2.0));
        }
        persTimeUntil = Double.NEGATIVE_INFINITY;
        pressureLevel = 0;
        simulator.advanceTime((level == 4 ? 59.0 : 55.0) * FRAME);
    }

    private void shadowhuntersAmbush(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        burstUntil = castTime + getTalentValue("Burst Duration", 10.0);
        resetSkillCooldown(castTime);
        markBurstCooldownUsed(castTime, simulator.getApplicableBuffs(this));
        queueCommand(simulator, new PendingCommand(
                castTime + 4.0 * FRAME,
                CommandKind.BURST_ENERGY,
                0));
        queueHit(simulator, new PendingHit(
                castTime + 44.0 * FRAME,
                HitKind.BURST,
                0,
                0,
                null));
        simulator.advanceTime(65.0 * FRAME);
    }

    private void resolveHit(CombatSimulator simulator, PendingHit hit) {
        switch (hit.kind) {
            case NORMAL:
                resolveNormal(simulator, hit);
                break;
            case PLUNGE:
                String plungeSuffix = constellation >= 3 ? " C3" : "";
                performHit(simulator, hit, "Flowing Eddies High Plunge",
                        getTalentValue(
                                "High Plunge" + plungeSuffix,
                                constellation >= 3
                                        ? 4.202331 : 3.422517),
                        Element.PHYSICAL,
                        StatType.PLUNGING_ATTACK_DMG_BONUS,
                        ActionType.PLUNGE,
                        ICDType.None,
                        ICDTag.PlungeAttack,
                        0.0);
                break;
            case THRUST:
                performHit(simulator, hit, "Pressurized Floe: Upward Thrust",
                        skillValue("Upward Thrust", 1.411680, 1.660800),
                        Element.CRYO,
                        StatType.SKILL_DMG_BONUS,
                        ActionType.SKILL,
                        ICDType.Standard,
                        ICDTag.ElementalSkill,
                        1.0);
                queueParticles(simulator, isBurstActive(hit.time) ? 1 : 2);
                break;
            case SPIRITBREATH:
                performHit(simulator, hit, "Pressurized Floe: Spiritbreath Thorn",
                        skillValue("Spiritbreath Thorn", 0.244800, 0.288000),
                        Element.CRYO,
                        StatType.SKILL_DMG_BONUS,
                        ActionType.SKILL,
                        ICDType.None,
                        ICDTag.ElementalSkill,
                        0.0);
                break;
            case FROST:
                performHit(simulator, hit, "Pressurized Floe: Pers Time Frost",
                        skillValue("Frost", 0.121720, 0.143200)
                                * (hit.variant == 1 ? 2.0 : 1.0),
                        Element.CRYO,
                        StatType.SKILL_DMG_BONUS,
                        ActionType.SKILL,
                        ICDType.Standard,
                        ICDTag.ElementalSkill,
                        1.0);
                break;
            case PRESSURE_CRYO:
                resolvePressureCryo(simulator, hit);
                break;
            case PRESSURE_PHYSICAL:
                resolvePressurePhysical(simulator, hit);
                if (hit.index == 4) {
                    queueParticles(simulator,
                            isBurstActive(hit.time) ? 1 : 2);
                }
                break;
            case BURST:
                performHit(simulator, hit, "Shadowhunter's Ambush",
                        getTalentValue(
                                "Shadowhunter's Ambush", 5.412800),
                        Element.CRYO,
                        StatType.BURST_DMG_BONUS,
                        ActionType.BURST,
                        ICDType.None,
                        ICDTag.ElementalBurst,
                        1.0);
                break;
            default:
                throw new IllegalStateException("Unknown Freminet hit kind");
        }
    }

    private void resolveNormal(CombatSimulator simulator, PendingHit hit) {
        String suffix = constellation >= 3 ? " C3" : "";
        double fallback = constellation >= 3
                ? NORMAL_T12[hit.index] : NORMAL_T9[hit.index];
        performHit(
                simulator,
                hit,
                "Flowing Eddies " + NORMAL_KEYS[hit.index],
                getTalentValue(NORMAL_KEYS[hit.index] + suffix, fallback),
                Element.PHYSICAL,
                StatType.NORMAL_ATTACK_DMG_BONUS,
                ActionType.NORMAL,
                ICDType.Standard,
                ICDTag.NormalAttack,
                1.0);
    }

    private void resolvePressureCryo(
            CombatSimulator simulator,
            PendingHit hit) {
        String suffix = constellation >= 5 ? " C5" : "";
        double fallback = constellation >= 5
                ? PRESSURE_CRYO_T12[hit.index]
                : PRESSURE_CRYO_T9[hit.index];
        performHit(
                simulator,
                hit,
                "Shattering Pressure Cryo Level " + hit.index,
                getTalentValue(
                        "Pressure Cryo Level " + hit.index + suffix,
                        fallback),
                Element.CRYO,
                StatType.SKILL_DMG_BONUS,
                ActionType.SKILL,
                ICDType.Standard,
                ICDTag.ElementalSkill,
                1.0);
    }

    private void resolvePressurePhysical(
            CombatSimulator simulator,
            PendingHit hit) {
        String suffix = constellation >= 5 ? " C5" : "";
        double fallback = constellation >= 5
                ? PRESSURE_PHYSICAL_T12[hit.index - 1]
                : PRESSURE_PHYSICAL_T9[hit.index - 1];
        performHit(
                simulator,
                hit,
                "Shattering Pressure Physical Level " + hit.index,
                getTalentValue(
                        "Pressure Physical Level " + hit.index + suffix,
                        fallback),
                Element.PHYSICAL,
                StatType.SKILL_DMG_BONUS,
                ActionType.SKILL,
                ICDType.None,
                ICDTag.ElementalSkill,
                1.0);
    }

    private void performHit(
            CombatSimulator simulator,
            PendingHit hit,
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
        action.setStatSnapshot(hit.snapshot == null
                ? captureLiveStats(simulator.getCurrentTime())
                : hit.snapshot);
        simulator.performActionWithoutTimeAdvance(characterId, action);
    }

    private double skillValue(
            String key,
            double talentNine,
            double talentTwelve) {
        String suffix = constellation >= 5 ? " C5" : "";
        return getTalentValue(
                key + suffix,
                constellation >= 5 ? talentTwelve : talentNine);
    }

    private void queueParticles(CombatSimulator simulator, int count) {
        queueCommand(simulator, new PendingCommand(
                simulator.getCurrentTime() + PARTICLE_TRAVEL,
                CommandKind.PARTICLE,
                count));
    }

    private void refreshC4Buff(double currentTime) {
        removeBuff(BuffId.FREMINET_C4_ATK);
        addBuff(new SimpleBuff(
                "Freminet C4 ATK",
                BuffId.FREMINET_C4_ATK,
                getTalentValue("C4 Duration", 6.0),
                currentTime,
                stats -> stats.add(
                        StatType.ATK_PERCENT,
                        getTalentValue("C4 ATK Per Stack", 0.09)
                                * c4Stacks))
                .sourcedBy(characterId));
    }

    private void refreshC6Buff(double currentTime) {
        removeBuff(BuffId.FREMINET_C6_CRIT_DMG);
        addBuff(new SimpleBuff(
                "Freminet C6 CRIT DMG",
                BuffId.FREMINET_C6_CRIT_DMG,
                getTalentValue("C6 Duration", 6.0),
                currentTime,
                stats -> stats.add(
                        StatType.CRIT_DMG,
                        getTalentValue("C6 CRIT DMG Per Stack", 0.12)
                                * c6Stacks))
                .sourcedBy(characterId));
    }

    private void normalizeAt(double currentTime) {
        if (currentTime >= persTimeUntil) {
            pressureLevel = 0;
        }
        if (currentTime >= c4Until) {
            c4Stacks = 0;
        }
        if (currentTime >= c6Until) {
            c6Stacks = 0;
        }
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
                case SKILL_COOLDOWN:
                    if (command.value == 1) {
                        setSkillCD(3.0);
                        markSkillUsed(activeSimulator.getCurrentTime(),
                                activeSimulator.getApplicableBuffs(this));
                        setSkillCD(getTalentValue("Skill Cooldown", 10.0));
                    } else {
                        markSkillUsed(activeSimulator.getCurrentTime(),
                                activeSimulator.getApplicableBuffs(this));
                    }
                    break;
                case BURST_ENERGY:
                    spendBurstEnergy(activeSimulator.getCurrentTime());
                    break;
                case PARTICLE:
                    activeSimulator.getEnergyDistributor().distributeParticles(
                            Element.CRYO,
                            command.value,
                            ParticleType.PARTICLE);
                    break;
                default:
                    throw new IllegalStateException(
                            "Unknown Freminet command kind");
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
        PLUNGE,
        THRUST,
        SPIRITBREATH,
        FROST,
        PRESSURE_CRYO,
        PRESSURE_PHYSICAL,
        BURST
    }

    private enum CommandKind {
        SKILL_COOLDOWN,
        BURST_ENERGY,
        PARTICLE
    }

    /** Immutable delayed Freminet hit description. */
    private static final class PendingHit {
        private final double time;
        private final HitKind kind;
        private final int index;
        private final int variant;
        private final StatsContainer snapshot;

        private PendingHit(
                double time,
                HitKind kind,
                int index,
                int variant,
                StatsContainer snapshot) {
            this.time = time;
            this.kind = kind;
            this.index = index;
            this.variant = variant;
            this.snapshot = snapshot == null ? null : snapshot.merge(null);
        }

        private PendingHit copy() {
            return new PendingHit(time, kind, index, variant, snapshot);
        }
    }

    /** Immutable delayed Freminet command description. */
    private static final class PendingCommand {
        private final double time;
        private final CommandKind kind;
        private final int value;

        private PendingCommand(double time, CommandKind kind, int value) {
            this.time = time;
            this.kind = kind;
            this.value = value;
        }

        private PendingCommand copy() {
            return new PendingCommand(time, kind, value);
        }
    }

    /** Immutable owner-bound Freminet rollback payload. */
    private static final class FreminetState implements State {
        private final Freminet owner;
        private final int normalAttackStep;
        private final int pressureLevel;
        private final int c4Stacks;
        private final int c6Stacks;
        private final double persTimeUntil;
        private final double burstUntil;
        private final double a4Until;
        private final double c4Until;
        private final double c6Until;
        private final double nextReactionStackTime;
        private final double nextSpiritbreathTime;
        private final List<PendingHit> pendingHits;
        private final List<PendingCommand> pendingCommands;

        private FreminetState(
                Freminet owner,
                int normalAttackStep,
                int pressureLevel,
                int c4Stacks,
                int c6Stacks,
                double persTimeUntil,
                double burstUntil,
                double a4Until,
                double c4Until,
                double c6Until,
                double nextReactionStackTime,
                double nextSpiritbreathTime,
                List<PendingHit> pendingHits,
                List<PendingCommand> pendingCommands) {
            this.owner = owner;
            this.normalAttackStep = normalAttackStep;
            this.pressureLevel = pressureLevel;
            this.c4Stacks = c4Stacks;
            this.c6Stacks = c6Stacks;
            this.persTimeUntil = persTimeUntil;
            this.burstUntil = burstUntil;
            this.a4Until = a4Until;
            this.c4Until = c4Until;
            this.c6Until = c6Until;
            this.nextReactionStackTime = nextReactionStackTime;
            this.nextSpiritbreathTime = nextSpiritbreathTime;
            this.pendingHits = copyHits(pendingHits);
            this.pendingCommands = copyCommands(pendingCommands);
        }
    }
}

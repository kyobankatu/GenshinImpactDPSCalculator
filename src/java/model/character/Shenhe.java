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
import simulation.action.HitlagProfile;
import simulation.action.SkillActionMode;
import simulation.event.SimpleTimerEvent;

/**
 * Shenhe's stationary single-target Icy Quill support slice through C6.
 *
 * <p>Lv. 90 stats, Talent 9/12 values, frames, gauges, particles, and support
 * rules follow pinned gcsim {@code ef41805d} and maintained KQM Shenhe
 * character/evidence pages. Every recipient owns an independent five- or
 * seven-trigger Quill quota. Eligible Cryo hits read Shenhe's live ATK before
 * damage and consume quota only after that hit resolves.</p>
 *
 * <p>Field geometry is represented as a stationary active-character field.
 * Hitlag extension, movement, stamina, multi-target quota use, and shields are
 * outside this slice.</p>
 */
public final class Shenhe extends Character implements
        SimulatorInitializedCharacterEffect,
        SnapshotAwareCharacterEffect,
        SwitchAwareCharacter,
        TargetDependentTeamEffect {
    private static final double FRAME = 1.0 / 60.0;
    private static final double EPSILON = 1e-9;
    /** Hitlag data pinned to gcsim {@code 3647a07a7cc3004bc1e79d9bb5f7444de20dceaa}. */
    private static final HitlagProfile[][] NORMAL_HITLAG = {
        { new HitlagProfile(0.02, 0.01, true, false, false) },
        { new HitlagProfile(0.02, 0.01, true, false, false) },
        { new HitlagProfile(0.02, 0.01, true, false, false) },
        { HitlagProfile.none(), new HitlagProfile(0.02, 0.01, true, false, false) },
        { new HitlagProfile(0.10, 0.01, true, false, false) }
    };
    private static final HitlagProfile CHARGED_HITLAG =
            new HitlagProfile(0.0, 0.01, true, true, false);
    private static final HitlagProfile PRESS_SKILL_HITLAG =
            new HitlagProfile(0.0, 0.01, true, true, false);
    private static final double PARTICLE_TRAVEL = 100.0 * FRAME;
    private static final int[][] NORMAL_HITMARKS = {
        { 14 }, { 17 }, { 19 }, { 14, 18 }, { 26 }
    };
    private static final int[] NORMAL_DURATIONS = { 29, 23, 38, 30, 59 };
    private static final String[][] NORMAL_KEYS = {
        { "N1" }, { "N2" }, { "N3" },
        { "N4-1", "N4-2" }, { "N5" }
    };
    private static final double[][] NORMAL_MULTIPLIERS = {
        { 0.79474 }, { 0.73944 }, { 0.9796 },
        { 0.48348, 0.48348 }, { 1.20554 }
    };
    private static final int[] BURST_DOT_FRAMES = {
        82, 112, 199, 231, 316, 350,
        433, 463, 550, 582, 667, 701,
        784, 814, 901, 933, 1018, 1052
    };

    private CombatSimulator initializedSimulator;
    private int normalAttackStep;
    private long skillGeneration;
    private long burstGeneration;
    private double icyQuillExpirationTime = Double.NEGATIVE_INFINITY;
    private final Map<CharacterId, Integer> icyQuillQuotas =
            new EnumMap<>(CharacterId.class);
    private int c4StackCount;
    private double c4ExpirationTime = Double.NEGATIVE_INFINITY;
    private double burstFieldExpirationTime = Double.NEGATIVE_INFINITY;
    private double burstShredExpirationTime = Double.NEGATIVE_INFINITY;
    private List<PendingHit> pendingHits = new ArrayList<>();
    private List<PendingCommand> pendingCommands = new ArrayList<>();

    /** Constructs repository-default C6 Shenhe. */
    public Shenhe(Weapon weapon, ArtifactSet artifacts) {
        this(weapon, artifacts, TalentDataManager.getInstance(), 6);
    }

    /** Constructs Shenhe at an explicit constellation. */
    public Shenhe(
            Weapon weapon,
            ArtifactSet artifacts,
            int constellation) {
        this(weapon, artifacts, TalentDataManager.getInstance(), constellation);
    }

    /** Constructs Shenhe with injectable talent data and constellation. */
    public Shenhe(
            Weapon weapon,
            ArtifactSet artifacts,
            TalentDataSource talentData,
            int constellation) {
        super(talentData);
        if (constellation < 0 || constellation > 6) {
            throw new IllegalArgumentException(
                    "Shenhe constellation must be between 0 and 6");
        }
        name = "Shenhe";
        characterId = CharacterId.SHENHE;
        element = Element.CRYO;
        this.weapon = weapon;
        this.artifacts = new ArtifactSet[] { artifacts };
        this.constellation = constellation;
        baseStats.set(StatType.BASE_HP,
                getTalentValue("Base HP", 12993.0));
        baseStats.set(StatType.BASE_ATK,
                getTalentValue("Base ATK", 304.0));
        baseStats.set(StatType.BASE_DEF,
                getTalentValue("Base DEF", 830.0));
        baseStats.add(StatType.ATK_PERCENT,
                getTalentValue("Ascension ATK Percent", 0.288));
        setSkillCD(10.0);
        setBurstCD(20.0);
        if (constellation >= 1) {
            setSkillMaxCharges(2);
        }
    }

    /** Binds per-hit Quill consumption to exactly one simulator. */
    @Override
    public void initializeForSimulator(CombatSimulator simulator) {
        if (simulator == null) {
            throw new IllegalArgumentException("Shenhe simulator is required");
        }
        if (initializedSimulator != null
                && initializedSimulator != simulator) {
            throw new IllegalStateException(
                    "Shenhe cannot be reused across simulators");
        }
        if (initializedSimulator == simulator) {
            return;
        }
        if (!simulator.getPartyMembers().contains(this)) {
            throw new IllegalArgumentException(
                    "Shenhe must belong to the target simulator party");
        }
        initializedSimulator = simulator;
        simulator.addDamageListener(this::consumeQuillAfterDamage);
    }

    /** Captures all Shenhe-owned progression and delayed work. */
    @Override
    public State captureCharacterState() {
        return new ShenheState(
                this,
                normalAttackStep,
                skillGeneration,
                burstGeneration,
                icyQuillExpirationTime,
                icyQuillQuotas,
                c4StackCount,
                c4ExpirationTime,
                burstFieldExpirationTime,
                burstShredExpirationTime,
                pendingHits,
                pendingCommands);
    }

    /** Accepts only state captured from this exact Shenhe instance. */
    @Override
    public boolean acceptsCharacterState(State state) {
        return state instanceof ShenheState
                && ((ShenheState) state).owner == this;
    }

    /** Restores Shenhe state and schedules each surviving event once. */
    @Override
    public void restoreCharacterState(
            State state,
            CombatSimulator simulator) {
        if (!acceptsCharacterState(state)) {
            throw new IllegalArgumentException("Unexpected Shenhe state");
        }
        initializeForSimulator(simulator);
        ShenheState restored = (ShenheState) state;
        normalAttackStep = restored.normalAttackStep;
        skillGeneration = restored.skillGeneration;
        burstGeneration = restored.burstGeneration;
        icyQuillExpirationTime = restored.icyQuillExpirationTime;
        icyQuillQuotas.clear();
        icyQuillQuotas.putAll(restored.icyQuillQuotas);
        c4StackCount = restored.c4StackCount;
        c4ExpirationTime = restored.c4ExpirationTime;
        burstFieldExpirationTime = restored.burstFieldExpirationTime;
        burstShredExpirationTime = restored.burstShredExpirationTime;
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

    /** Returns Shenhe's 80-Energy Burst cost. */
    @Override
    public double getEnergyCost() {
        return getTalentValue("Energy Cost", 80.0);
    }

    /** Shenhe's ascension ATK is loaded structurally. */
    @Override
    public void applyPassive(StatsContainer stats) {
        // Combat passives are event- and target-dependent.
    }

    /** Supports both Press and Hold Skill inputs. */
    @Override
    protected boolean supportsSkillActionMode(SkillActionMode mode) {
        return mode == SkillActionMode.PRESS
                || mode == SkillActionMode.HOLD;
    }

    /** Resets Shenhe's Normal string without clearing support state. */
    @Override
    public void onSwitchOut(CombatSimulator simulator) {
        normalAttackStep = 0;
    }

    /** Returns one recipient's live Quill quota. */
    public int getIcyQuillQuota(
            CharacterId recipientId,
            double currentTime) {
        if (currentTime >= icyQuillExpirationTime) {
            return 0;
        }
        return icyQuillQuotas.getOrDefault(recipientId, 0);
    }

    /** Returns the current half-open Quill expiration timestamp. */
    public double getIcyQuillExpirationTime() {
        return icyQuillExpirationTime;
    }

    /** Returns the current C4 stack count after applying expiry. */
    public int getC4StackCount(double currentTime) {
        expireC4(currentTime);
        return c4StackCount;
    }

    /** Returns the current stationary Burst field expiration timestamp. */
    public double getBurstFieldExpirationTime() {
        return burstFieldExpirationTime;
    }

    /** Returns the current Burst resistance-shred expiration timestamp. */
    public double getBurstShredExpirationTime() {
        return burstShredExpirationTime;
    }

    /** Adds Shenhe's live-ATK Quill value before an eligible Cryo hit. */
    @Override
    public void applyTargetDependentTeamStats(
            StatsContainer stats,
            Character attacker,
            model.entity.Enemy target,
            AttackAction action,
            double currentTime) {
        if (!qualifiesForQuill(attacker, target, action, currentTime)) {
            return;
        }
        double liveAttack = captureLiveStats(currentTime).getTotalAtk();
        stats.add(StatType.FLAT_DMG_BONUS,
                liveAttack * getQuillRatio());
    }

    /** Dispatches Shenhe's represented action set. */
    @Override
    public void onAction(
            CharacterActionRequest request,
            CombatSimulator simulator) {
        if (request == null) {
            throw new IllegalArgumentException("Shenhe action is required");
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
            case SKILL:
                springSpiritSummoning(simulator, request.getSkillMode());
                break;
            case BURST:
                divineMaidensDeliverance(simulator);
                break;
            default:
                throw new IllegalArgumentException(
                        "Unsupported action for Shenhe: " + request.getKey());
        }
    }

    private void normalAttack(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        int step = normalAttackStep;
        for (int hit = 0; hit < NORMAL_HITMARKS[step].length; hit++) {
            queueHit(simulator, new PendingHit(
                    castTime + NORMAL_HITMARKS[step][hit] * FRAME,
                    HitKind.NORMAL,
                    step,
                    hit,
                    0L,
                    null));
        }
        normalAttackStep = (normalAttackStep + 1) % 5;
        simulator.advanceTime(NORMAL_DURATIONS[step] * FRAME);
    }

    private void chargedAttack(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        queueHit(simulator, new PendingHit(
                castTime + 25.0 * FRAME,
                HitKind.CHARGED,
                0,
                0,
                0L,
                null));
        simulator.advanceTime(52.0 * FRAME);
    }

    private void springSpiritSummoning(
            CombatSimulator simulator,
            SkillActionMode mode) {
        if (mode != SkillActionMode.PRESS && mode != SkillActionMode.HOLD) {
            throw new IllegalArgumentException(
                    "Unsupported Shenhe Skill mode: " + mode);
        }
        boolean hold = mode == SkillActionMode.HOLD;
        double castTime = simulator.getCurrentTime();
        long generation = ++skillGeneration;
        setSkillCD(hold ? 15.0 : 10.0);
        markSkillUsed(castTime, simulator.getApplicableBuffs(this));
        setSkillCD(10.0);
        queueCommand(simulator, new PendingCommand(
                castTime + (hold ? 32.0 : 3.0) * FRAME,
                CommandKind.SKILL_ACTIVATION,
                generation,
                hold ? 1.0 : 0.0));
        queueHit(simulator, new PendingHit(
                castTime + (hold ? 33.0 : 4.0) * FRAME,
                HitKind.SKILL,
                hold ? 1 : 0,
                0,
                generation,
                null));
        simulator.advanceTime((hold ? 78.0 : 38.0) * FRAME);
    }

    private void divineMaidensDeliverance(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        long generation = ++burstGeneration;
        markBurstCooldownUsed(castTime, simulator.getApplicableBuffs(this));
        queueCommand(simulator, new PendingCommand(
                castTime + 5.0 * FRAME,
                CommandKind.BURST_ENERGY,
                generation,
                0.0));
        queueCommand(simulator, new PendingCommand(
                castTime + 47.0 * FRAME,
                CommandKind.BURST_FIELD,
                generation,
                castTime));
        queueHit(simulator, new PendingHit(
                castTime + 78.0 * FRAME,
                HitKind.BURST_INITIAL,
                0,
                0,
                generation,
                null));
        simulator.advanceTime(98.0 * FRAME);
    }

    private void resolveHit(CombatSimulator simulator, PendingHit hit) {
        if ((hit.kind == HitKind.SKILL
                && hit.generation != skillGeneration)
                || ((hit.kind == HitKind.BURST_INITIAL
                        || hit.kind == HitKind.BURST_DOT)
                        && hit.generation != burstGeneration)) {
            return;
        }
        switch (hit.kind) {
            case NORMAL:
                resolveNormal(simulator, hit);
                break;
            case CHARGED:
                resolveCharged(simulator);
                break;
            case SKILL:
                resolveSkill(simulator, hit);
                break;
            case BURST_INITIAL:
                resolveBurstInitial(simulator);
                break;
            case BURST_DOT:
                resolveBurstDot(simulator, hit);
                break;
            default:
                throw new IllegalStateException("Unknown Shenhe hit kind");
        }
    }

    private void resolveNormal(CombatSimulator simulator, PendingHit hit) {
        String key = NORMAL_KEYS[hit.index][hit.subIndex];
        AttackAction action = attack(
                "Dawnstar Piercer " + key,
                getTalentValue(
                        key, NORMAL_MULTIPLIERS[hit.index][hit.subIndex]),
                Element.PHYSICAL,
                StatType.NORMAL_ATTACK_DMG_BONUS,
                ActionType.NORMAL,
                ICDType.Standard,
                ICDTag.NormalAttack,
                0.0);
        action.setHitlagProfile(NORMAL_HITLAG[hit.index][hit.subIndex]);
        simulator.performActionWithoutTimeAdvance(characterId, action);
    }

    private void resolveCharged(CombatSimulator simulator) {
        AttackAction action = attack(
                "Dawnstar Piercer Charged",
                getTalentValue("Charged", 2.033302),
                Element.PHYSICAL,
                StatType.CHARGED_ATTACK_DMG_BONUS,
                ActionType.CHARGE,
                ICDType.Standard,
                ICDTag.ChargedAttack,
                0.0);
        action.setHitlagProfile(CHARGED_HITLAG);
        simulator.performActionWithoutTimeAdvance(characterId, action);
    }

    private void resolveSkill(CombatSimulator simulator, PendingHit hit) {
        boolean hold = hit.index == 1;
        String key;
        double fallback;
        if (hold) {
            key = constellation >= 3 ? "Hold C3" : "Hold";
            fallback = constellation >= 3 ? 3.776 : 3.2096;
        } else {
            key = constellation >= 3 ? "Press C3" : "Press";
            fallback = constellation >= 3 ? 2.784 : 2.3664;
        }
        AttackAction action = attack(
                hold
                        ? "Spring Spirit Summoning Hold"
                        : "Spring Spirit Summoning Press",
                getTalentValue(key, fallback),
                Element.CRYO,
                StatType.SKILL_DMG_BONUS,
                ActionType.SKILL,
                ICDType.None,
                ICDTag.None,
                hold ? 2.0 : 1.0);
        if (!hold) {
            action.setHitlagProfile(PRESS_SKILL_HITLAG);
        }
        int stacks = consumeC4Stacks(simulator.getCurrentTime());
        if (stacks > 0) {
            action.addBonusStat(
                    StatType.SKILL_DMG_BONUS,
                    stacks * getTalentValue(
                            "C4 Skill DMG Bonus Per Stack", 0.05));
        }
        simulator.performActionWithoutTimeAdvance(characterId, action);
        if (simulator.getEnemy() != null) {
            queueCommand(simulator, new PendingCommand(
                    simulator.getCurrentTime() + PARTICLE_TRAVEL,
                    CommandKind.PARTICLE,
                    0L,
                    hold ? 4.0 : 3.0));
        }
    }

    private void resolveBurstInitial(CombatSimulator simulator) {
        simulator.performActionWithoutTimeAdvance(characterId, attack(
                "Divine Maiden's Deliverance Initial",
                getTalentValue(
                        constellation >= 5
                                ? "Burst Initial C5" : "Burst Initial",
                        constellation >= 5 ? 2.016 : 1.7136),
                Element.CRYO,
                StatType.BURST_DMG_BONUS,
                ActionType.BURST,
                ICDType.None,
                ICDTag.None,
                1.0));
    }

    private void resolveBurstDot(
            CombatSimulator simulator,
            PendingHit hit) {
        AttackAction action = attack(
                "Divine Maiden's Deliverance DoT",
                getTalentValue(
                        constellation >= 5 ? "Burst DoT C5" : "Burst DoT",
                        constellation >= 5 ? 0.6624 : 0.56304),
                Element.CRYO,
                StatType.BURST_DMG_BONUS,
                ActionType.BURST,
                ICDType.Standard,
                ICDTag.ElementalBurst,
                1.0);
        action.setStatSnapshot(hit.snapshot);
        simulator.performActionWithoutTimeAdvance(characterId, action);
    }

    private void activateSkillSupport(
            CombatSimulator simulator,
            boolean hold) {
        double currentTime = simulator.getCurrentTime();
        double duration = hold ? 15.0 : 10.0;
        icyQuillExpirationTime = currentTime + duration;
        icyQuillQuotas.clear();
        for (Character member : simulator.getPartyMembers()) {
            icyQuillQuotas.put(member.getCharacterId(), hold ? 7 : 5);
        }
        BuffId buffId = hold
                ? BuffId.SHENHE_A4_NORMAL_CHARGED_PLUNGE_DMG
                : BuffId.SHENHE_A4_SKILL_BURST_DMG;
        simulator.applyTeamBuffNoStack(new SimpleBuff(
                hold
                        ? "Shenhe Spirit Communion Seal Hold"
                        : "Shenhe Spirit Communion Seal Press",
                buffId,
                duration,
                currentTime,
                stats -> {
                    if (hold) {
                        stats.add(
                                StatType.NORMAL_ATTACK_DMG_BONUS,
                                getTalentValue(
                                        "A4 Hold Normal Charged Plunge DMG Bonus",
                                        0.15));
                        stats.add(
                                StatType.CHARGED_ATTACK_DMG_BONUS,
                                getTalentValue(
                                        "A4 Hold Normal Charged Plunge DMG Bonus",
                                        0.15));
                        stats.add(
                                StatType.PLUNGING_ATTACK_DMG_BONUS,
                                getTalentValue(
                                        "A4 Hold Normal Charged Plunge DMG Bonus",
                                        0.15));
                    } else {
                        stats.add(
                                StatType.SKILL_DMG_BONUS,
                                getTalentValue(
                                        "A4 Press Skill Burst DMG Bonus",
                                        0.15));
                        stats.add(
                                StatType.BURST_DMG_BONUS,
                                getTalentValue(
                                        "A4 Press Skill Burst DMG Bonus",
                                        0.15));
                    }
                }).sourcedBy(characterId));
    }

    private void activateBurstField(
            CombatSimulator simulator,
            long generation,
            double castTime) {
        if (generation != burstGeneration) {
            return;
        }
        double currentTime = simulator.getCurrentTime();
        double fieldDuration = constellation >= 2 ? 18.0 : 12.0;
        burstFieldExpirationTime = currentTime + fieldDuration;
        burstShredExpirationTime = burstFieldExpirationTime + 2.0;
        StatsContainer snapshot = captureLiveStats(currentTime);
        double shred = getTalentValue(
                constellation >= 5 ? "Burst RES Shred C5" : "Burst RES Shred",
                constellation >= 5 ? 0.15 : 0.14);
        simulator.applyTeamBuffNoStack(new SimpleBuff(
                "Shenhe Divine Maiden Resistance Shred",
                BuffId.SHENHE_BURST_RES_SHRED,
                fieldDuration + 2.0,
                currentTime,
                stats -> {
                    stats.add(StatType.CRYO_RES_SHRED, shred);
                    stats.add(StatType.PHYS_RES_SHRED, shred);
                }).sourcedBy(characterId));
        simulator.applyFieldBuff(new SimpleBuff(
                "Shenhe Divine Maiden Active Bonus",
                BuffId.SHENHE_BURST_ACTIVE_BONUS,
                fieldDuration,
                currentTime,
                stats -> {
                    stats.add(
                            StatType.CRYO_DMG_BONUS,
                            getTalentValue("A1 Cryo DMG Bonus", 0.15));
                    if (constellation >= 2) {
                        stats.add(
                                StatType.CRYO_CRIT_DMG,
                                getTalentValue("C2 Cryo CRIT DMG", 0.15));
                    }
                }).sourcedBy(characterId));

        int hitCount = constellation >= 2 ? 18 : 12;
        for (int i = 0; i < hitCount; i++) {
            queueHit(simulator, new PendingHit(
                    castTime + BURST_DOT_FRAMES[i] * FRAME,
                    HitKind.BURST_DOT,
                    i,
                    0,
                    generation,
                    snapshot));
        }
    }

    private boolean qualifiesForQuill(
            Character attacker,
            model.entity.Enemy target,
            AttackAction action,
            double currentTime) {
        return initializedSimulator != null
                && attacker != null
                && initializedSimulator.getPartyMembers().contains(attacker)
                && target != null
                && action != null
                && action.getElement() == Element.CRYO
                && !action.isLunarConsidered()
                && action.isHitEffectTrigger()
                && action.getDamagePercent() > 0.0
                && isQuillAction(action)
                && getIcyQuillQuota(
                        attacker.getCharacterId(), currentTime) > 0;
    }

    private boolean isQuillAction(AttackAction action) {
        ActionType type = action.getActionType();
        return type == ActionType.NORMAL
                || type == ActionType.CHARGE
                || type == ActionType.PLUNGE
                || type == ActionType.SKILL
                || type == ActionType.BURST
                || action.isCountsAsSkillDmg()
                || action.isCountsAsBurstDmg();
    }

    private void consumeQuillAfterDamage(
            Character actor,
            AttackAction action,
            double damage,
            double time) {
        if (damage <= 0.0
                || !qualifiesForQuill(
                        actor,
                        initializedSimulator.getEnemy(),
                        action,
                        time)) {
            return;
        }
        boolean freeAtC6 = constellation >= 6
                && (action.getActionType() == ActionType.NORMAL
                        || action.getActionType() == ActionType.CHARGE);
        if (!freeAtC6) {
            icyQuillQuotas.computeIfPresent(
                    actor.getCharacterId(),
                    (id, remaining) -> Math.max(0, remaining - 1));
        }
        if (constellation >= 4) {
            expireC4(time);
            c4StackCount = Math.min(
                    (int) getTalentValue("C4 Max Stacks", 50.0),
                    c4StackCount + 1);
            c4ExpirationTime = time
                    + getTalentValue("C4 Stack Duration", 60.0);
        }
    }

    private double getQuillRatio() {
        return getTalentValue(
                constellation >= 3 ? "Quill ATK Ratio C3" : "Quill ATK Ratio",
                constellation >= 3 ? 0.91312 : 0.776152);
    }

    private int consumeC4Stacks(double currentTime) {
        if (constellation < 4) {
            return 0;
        }
        expireC4(currentTime);
        int stacks = c4StackCount;
        c4StackCount = 0;
        c4ExpirationTime = Double.NEGATIVE_INFINITY;
        return stacks;
    }

    private void expireC4(double currentTime) {
        if (currentTime >= c4ExpirationTime) {
            c4StackCount = 0;
            c4ExpirationTime = Double.NEGATIVE_INFINITY;
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
                case SKILL_ACTIVATION:
                    if (command.generation == skillGeneration) {
                        activateSkillSupport(
                                activeSimulator, command.value > 0.5);
                    }
                    break;
                case PARTICLE:
                    activeSimulator.getEnergyDistributor().distributeParticles(
                            Element.CRYO,
                            command.value,
                            ParticleType.PARTICLE);
                    break;
                case BURST_ENERGY:
                    if (command.generation == burstGeneration) {
                        spendBurstEnergy(activeSimulator.getCurrentTime());
                    }
                    break;
                case BURST_FIELD:
                    activateBurstField(
                            activeSimulator,
                            command.generation,
                            command.value);
                    break;
                default:
                    throw new IllegalStateException(
                            "Unknown Shenhe command kind");
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
        SKILL,
        BURST_INITIAL,
        BURST_DOT
    }

    private enum CommandKind {
        SKILL_ACTIVATION,
        PARTICLE,
        BURST_ENERGY,
        BURST_FIELD
    }

    /** Immutable delayed Shenhe hit description. */
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

    /** Immutable delayed Shenhe command description. */
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

    /** Immutable Shenhe-owned simulator snapshot payload. */
    private static final class ShenheState implements State {
        private final Shenhe owner;
        private final int normalAttackStep;
        private final long skillGeneration;
        private final long burstGeneration;
        private final double icyQuillExpirationTime;
        private final Map<CharacterId, Integer> icyQuillQuotas;
        private final int c4StackCount;
        private final double c4ExpirationTime;
        private final double burstFieldExpirationTime;
        private final double burstShredExpirationTime;
        private final List<PendingHit> pendingHits;
        private final List<PendingCommand> pendingCommands;

        private ShenheState(
                Shenhe owner,
                int normalAttackStep,
                long skillGeneration,
                long burstGeneration,
                double icyQuillExpirationTime,
                Map<CharacterId, Integer> icyQuillQuotas,
                int c4StackCount,
                double c4ExpirationTime,
                double burstFieldExpirationTime,
                double burstShredExpirationTime,
                List<PendingHit> pendingHits,
                List<PendingCommand> pendingCommands) {
            this.owner = owner;
            this.normalAttackStep = normalAttackStep;
            this.skillGeneration = skillGeneration;
            this.burstGeneration = burstGeneration;
            this.icyQuillExpirationTime = icyQuillExpirationTime;
            this.icyQuillQuotas = new EnumMap<>(CharacterId.class);
            this.icyQuillQuotas.putAll(icyQuillQuotas);
            this.c4StackCount = c4StackCount;
            this.c4ExpirationTime = c4ExpirationTime;
            this.burstFieldExpirationTime = burstFieldExpirationTime;
            this.burstShredExpirationTime = burstShredExpirationTime;
            this.pendingHits = copyHits(pendingHits);
            this.pendingCommands = copyCommands(pendingCommands);
        }
    }
}

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
import model.entity.ArtifactSet;
import model.entity.Character;
import model.entity.Enemy;
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
 * Xianyun's stationary fixed-target Driftcloud and Starwicker slice.
 *
 * <p>Lv. 90 data, catalyst attacks, one-to-three Skyladder inputs,
 * Driftcloud Wave, particles, Burst damage, eight Adeptal Assistance stacks,
 * A1/A4, and representable C1-C3/C5-C6 behavior follow pinned gcsim
 * {@code ef41805d}. A fixed accepted Driftcloud hit represents one enemy and
 * therefore grants exactly one A1 stack.</p>
 *
 * <p>Skyladder collision damage, healing and player HP, movement and
 * geometry, random or multi-target selection, low/collision Plunge variants,
 * stamina, airborne height, and hitlag are excluded instead of being
 * approximated. A typed Plunge outside Cloud Transmogrification is the
 * repository's fixed high-Plunge packet.</p>
 */
public final class Xianyun extends Character implements
        SimulatorInitializedCharacterEffect,
        SnapshotAwareCharacterEffect,
        SwitchAwareCharacter,
        TargetDependentTeamEffect {
    private static final double FRAME = 1.0 / 60.0;
    private static final double EPSILON = 1e-9;
    private static final int[] NORMAL_HIT_FRAMES = { 12, 14, 34, 38 };
    private static final int[] NORMAL_DURATIONS = { 34, 38, 65, 93 };
    private static final double[] NORMAL_T9 = {
        0.685141, 0.660538, 0.830919, 1.103586
    };
    private static final int[] SKILL_INPUT_FRAMES = { 14, 15, 18 };
    private static final int[] WAVE_HIT_FRAMES = { 35, 40, 46 };
    private static final int[] WAVE_DURATIONS = { 65, 70, 76 };

    private CombatSimulator initializedSimulator;
    private int normalAttackStep;
    private int skillLeapCount;
    private boolean skillSequenceUsesC6;
    private double skillStateExpirationTime = Double.NEGATIVE_INFINITY;
    private double burstExpirationTime = Double.NEGATIVE_INFINITY;
    private int adeptalAssistanceStacks;
    private double c6ExpirationTime = Double.NEGATIVE_INFINITY;
    private int c6FreeSkillUses;
    private final List<Double> a1StackExpirations = new ArrayList<>();
    private AttackAction resolvingAction;
    private boolean resolvingWave;
    private boolean resolvingParticleEligible;
    private List<PendingHit> pendingHits = new ArrayList<>();
    private List<PendingCommand> pendingCommands = new ArrayList<>();

    /** Constructs repository-default C6 Xianyun. */
    public Xianyun(Weapon weapon, ArtifactSet artifacts) {
        this(weapon, artifacts, TalentDataManager.getInstance(), 6);
    }

    /** Constructs Xianyun at an explicit constellation. */
    public Xianyun(
            Weapon weapon,
            ArtifactSet artifacts,
            int constellation) {
        this(weapon, artifacts, TalentDataManager.getInstance(), constellation);
    }

    /**
     * Constructs Xianyun with injectable static talent data.
     *
     * @param weapon equipped catalyst, or {@code null}
     * @param artifacts equipped artifact set, or {@code null}
     * @param talentData static character-data source
     * @param constellation constellation level in {@code [0, 6]}
     */
    public Xianyun(
            Weapon weapon,
            ArtifactSet artifacts,
            TalentDataSource talentData,
            int constellation) {
        super(talentData);
        if (constellation < 0 || constellation > 6) {
            throw new IllegalArgumentException(
                    "Xianyun constellation must be between 0 and 6");
        }
        name = "Xianyun";
        characterId = CharacterId.XIANYUN;
        element = Element.ANEMO;
        this.weapon = weapon;
        this.artifacts = new ArtifactSet[] { artifacts };
        this.constellation = constellation;
        baseStats.set(StatType.BASE_HP,
                getTalentValue("Base HP", 10409.0));
        baseStats.set(StatType.BASE_ATK,
                getTalentValue("Base ATK", 335.0));
        baseStats.set(StatType.BASE_DEF,
                getTalentValue("Base DEF", 573.0));
        baseStats.add(StatType.ATK_PERCENT,
                getTalentValue("Ascension ATK Percent", 0.288));
        setSkillCD(getTalentValue("Skill Cooldown", 12.0));
        setBurstCD(getTalentValue("Burst Cooldown", 18.0));
        if (constellation >= 1) {
            setSkillMaxCharges(2);
        }
    }

    /** Binds Starwicker and accepted-hit state to one owning simulator. */
    @Override
    public void initializeForSimulator(CombatSimulator simulator) {
        if (simulator == null) {
            throw new IllegalArgumentException("Xianyun simulator is required");
        }
        if (!simulator.getPartyMembers().contains(this)) {
            throw new IllegalArgumentException(
                    "Xianyun must belong to the simulator party");
        }
        if (initializedSimulator != null
                && initializedSimulator != simulator) {
            throw new IllegalStateException(
                    "Xianyun cannot be reused across simulators");
        }
        if (initializedSimulator == simulator) {
            return;
        }
        initializedSimulator = simulator;
        simulator.addDamageListener(this::handleAcceptedDamage);
    }

    /** Captures all mutable Xianyun-owned state and delayed work. */
    @Override
    public State captureCharacterState() {
        return new XianyunState(
                this,
                normalAttackStep,
                skillLeapCount,
                skillSequenceUsesC6,
                skillStateExpirationTime,
                burstExpirationTime,
                adeptalAssistanceStacks,
                c6ExpirationTime,
                c6FreeSkillUses,
                a1StackExpirations,
                pendingHits,
                pendingCommands);
    }

    /** Accepts state captured from this exact Xianyun instance only. */
    @Override
    public boolean acceptsCharacterState(State state) {
        return state instanceof XianyunState
                && ((XianyunState) state).owner == this;
    }

    /** Restores Xianyun-owned state and reconstructs pending events once. */
    @Override
    public void restoreCharacterState(
            State state,
            CombatSimulator simulator) {
        if (!acceptsCharacterState(state)) {
            throw new IllegalArgumentException("Unexpected Xianyun state");
        }
        initializeForSimulator(simulator);
        XianyunState restored = (XianyunState) state;
        normalAttackStep = restored.normalAttackStep;
        skillLeapCount = restored.skillLeapCount;
        skillSequenceUsesC6 = restored.skillSequenceUsesC6;
        skillStateExpirationTime = restored.skillStateExpirationTime;
        burstExpirationTime = restored.burstExpirationTime;
        adeptalAssistanceStacks = restored.adeptalAssistanceStacks;
        c6ExpirationTime = restored.c6ExpirationTime;
        c6FreeSkillUses = restored.c6FreeSkillUses;
        a1StackExpirations.clear();
        a1StackExpirations.addAll(restored.a1StackExpirations);
        pendingHits = copyHits(restored.pendingHits);
        pendingCommands = copyCommands(restored.pendingCommands);
        resolvingAction = null;
        resolvingWave = false;
        resolvingParticleEligible = false;
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

    /** Returns Xianyun's 70-Energy Burst cost. */
    @Override
    public double getEnergyCost() {
        return getTalentValue("Energy Cost", 70.0);
    }

    /** Xianyun has no unconditional passive stat. */
    @Override
    public void applyPassive(StatsContainer stats) {
    }

    /** Resets only the catalyst Normal string on switch-out. */
    @Override
    public void onSwitchOut(CombatSimulator simulator) {
        normalAttackStep = 0;
    }

    /** Lets valid Skyladder recasts and C6 free starts pass the gateway. */
    @Override
    public double getSkillCDRemaining(double currentTime) {
        refreshTimedState(currentTime);
        if (skillLeapCount > 0 && skillLeapCount < 3) {
            return 0.0;
        }
        if (skillLeapCount == 3) {
            double stateWait = Math.max(0.0,
                    skillStateExpirationTime - currentTime);
            if (constellation >= 6
                    && c6FreeSkillUses > 0
                    && skillStateExpirationTime + EPSILON
                            < c6ExpirationTime) {
                return stateWait;
            }
            double cooldownWait = super.getSkillCDRemaining(currentTime);
            if (!skillSequenceUsesC6) {
                cooldownWait = Math.max(
                        0.0,
                        cooldownWait - getTalentValue(
                                "Unused Skill Cooldown Reduction", 3.0));
            }
            return Math.max(stateWait, cooldownWait);
        }
        if (isC6FreeSkillActive(currentTime)) {
            return 0.0;
        }
        return super.getSkillCDRemaining(currentTime);
    }

    /** Returns whether a represented Skill start or continuation is ready. */
    @Override
    public boolean canSkill(double currentTime) {
        return getSkillCDRemaining(currentTime) <= EPSILON;
    }

    /** Returns the active Skyladder count after applying lazy expiry. */
    public int getSkillLeapCount(double currentTime) {
        refreshTimedState(currentTime);
        return skillLeapCount;
    }

    /** Returns remaining Burst Adeptal Assistance stacks. */
    public int getAdeptalAssistanceStacks(double currentTime) {
        refreshTimedState(currentTime);
        return adeptalAssistanceStacks;
    }

    /** Returns remaining C6 cooldown-free Skill sequence starts. */
    public int getC6FreeSkillUses(double currentTime) {
        refreshTimedState(currentTime);
        return c6FreeSkillUses;
    }

    /** Returns the current A1 fixed-target stack count, capped at four. */
    public int getA1StackCount(double currentTime) {
        expireA1Stacks(currentTime);
        return Math.min(4, a1StackExpirations.size());
    }

    /** Returns the number of unresolved Xianyun-owned hits. */
    public int getPendingHitCount() {
        return pendingHits.size();
    }

    /** Reports that Skyladder collision damage is geometry-gated and inactive. */
    public boolean isSkyladderCollisionDamageRepresented() {
        return false;
    }

    /** Reports that healing and player-HP effects are outside this slice. */
    public boolean isHealingRepresented() {
        return false;
    }

    /** Reports that low/collision Plunge height state is outside this slice. */
    public boolean isPlungeHeightStateRepresented() {
        return false;
    }

    /** Applies live A1 CRIT and A4 flat damage to eligible party Plunges. */
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
                || !initializedSimulator.getPartyMembers().contains(attacker)
                || action.getActionType() != ActionType.PLUNGE
                || !action.isHitEffectTrigger()
                || action.getDamagePercent() <= 0.0) {
            return;
        }
        int a1Stacks = getA1StackCount(currentTime);
        if (a1Stacks > 0) {
            stats.add(
                    StatType.PLUNGING_ATTACK_CRIT_RATE,
                    a1CritRate(a1Stacks));
        }
        if (initializedSimulator.getActiveCharacter() != attacker
                || getAdeptalAssistanceStacks(currentTime) <= 0) {
            return;
        }
        double xianyunAttack = captureLiveStats(currentTime).getTotalAtk();
        double ratio = getTalentValue(
                constellation >= 2
                        ? "C2 A4 ATK Ratio" : "A4 ATK Ratio",
                constellation >= 2 ? 4.0 : 2.0);
        double cap = getTalentValue(
                constellation >= 2
                        ? "C2 A4 Flat DMG Cap" : "A4 Flat DMG Cap",
                constellation >= 2 ? 18000.0 : 9000.0);
        stats.add(StatType.FLAT_DMG_BONUS,
                Math.min(cap, ratio * xianyunAttack));
    }

    /** Dispatches Xianyun's represented typed action set. */
    @Override
    public void onAction(
            CharacterActionRequest request,
            CombatSimulator simulator) {
        if (request == null) {
            throw new IllegalArgumentException("Xianyun action is required");
        }
        initializeForSimulator(simulator);
        refreshTimedState(simulator.getCurrentTime());
        if (request.getKey() == CharacterActionKey.SKILL
                && request.getSkillMode() != SkillActionMode.PRESS) {
            throw new IllegalArgumentException(
                    "Xianyun supports Press Skill only");
        }
        if (skillLeapCount > 0
                && (request.getKey() == CharacterActionKey.NORMAL
                        || request.getKey() == CharacterActionKey.CHARGE)) {
            throw new IllegalArgumentException(
                    "Xianyun cannot use Normal or Charged attacks during "
                            + "Cloud Transmogrification");
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
                plunge(simulator);
                break;
            case SKILL:
                skyladder(simulator);
                break;
            case BURST:
                starsGatherAtDusk(simulator);
                break;
            default:
                throw new IllegalArgumentException(
                        "Unsupported action for Xianyun: "
                                + request.getKey());
        }
    }

    private void normalAttack(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        int step = normalAttackStep;
        queueHit(simulator, new PendingHit(
                castTime + NORMAL_HIT_FRAMES[step] * FRAME,
                HitKind.NORMAL,
                step,
                captureLiveStats(castTime),
                false,
                false));
        normalAttackStep = (normalAttackStep + 1) % NORMAL_T9.length;
        simulator.advanceTime(NORMAL_DURATIONS[step] * FRAME);
    }

    private void chargedAttack(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        queueHit(simulator, new PendingHit(
                castTime + 56.0 * FRAME,
                HitKind.CHARGED,
                0,
                captureLiveStats(castTime),
                false,
                false));
        simulator.advanceTime(73.0 * FRAME);
    }

    private void plunge(CombatSimulator simulator) {
        refreshTimedState(simulator.getCurrentTime());
        if (skillLeapCount > 0) {
            driftcloudWave(simulator);
            return;
        }
        double castTime = simulator.getCurrentTime();
        queueHit(simulator, new PendingHit(
                castTime + 46.0 * FRAME,
                HitKind.HIGH_PLUNGE,
                0,
                captureLiveStats(castTime),
                false,
                false));
        simulator.advanceTime(68.0 * FRAME);
    }

    private void skyladder(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        refreshTimedState(castTime);
        if (skillLeapCount == 0) {
            skillSequenceUsesC6 = isC6FreeSkillActive(castTime);
            if (skillSequenceUsesC6) {
                c6FreeSkillUses--;
            } else {
                markSkillUsed(castTime,
                        simulator.getApplicableBuffs(this));
            }
        }
        if (skillLeapCount >= 3) {
            throw new IllegalStateException(
                    "Xianyun cannot exceed three Skyladder inputs");
        }
        skillLeapCount++;
        skillStateExpirationTime = castTime
                + getTalentValue(
                        "Skill State " + skillLeapCount + " Frames",
                        skillStateFrames(skillLeapCount)) * FRAME;
        if (constellation >= 2) {
            removeBuff(BuffId.XIANYUN_C2_ATK);
            addBuff(new SimpleBuff(
                    "Xianyun Aloof From the World",
                    BuffId.XIANYUN_C2_ATK,
                    getTalentValue("C2 Duration", 15.0),
                    castTime,
                    stats -> stats.add(
                            StatType.ATK_PERCENT,
                            getTalentValue(
                                    "C2 ATK Percent", 0.20))));
        }
        simulator.advanceTime(
                SKILL_INPUT_FRAMES[skillLeapCount - 1] * FRAME);
    }

    private void driftcloudWave(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        int leapCount = skillLeapCount;
        StatsContainer snapshot = captureLiveStats(castTime);
        if (constellation >= 6) {
            snapshot.add(
                    StatType.PLUNGING_ATTACK_CRIT_DMG,
                    c6CritDamage(leapCount));
        }
        queueHit(simulator, new PendingHit(
                castTime + WAVE_HIT_FRAMES[leapCount - 1] * FRAME,
                HitKind.DRIFTCLOUD,
                leapCount,
                snapshot,
                true,
                !skillSequenceUsesC6));
        skillLeapCount = 0;
        skillSequenceUsesC6 = false;
        skillStateExpirationTime = Double.NEGATIVE_INFINITY;
        simulator.advanceTime(WAVE_DURATIONS[leapCount - 1] * FRAME);
    }

    private void starsGatherAtDusk(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        markBurstCooldownUsed(
                castTime, simulator.getApplicableBuffs(this));
        queueCommand(simulator, new PendingCommand(
                castTime + 18.0 * FRAME,
                CommandKind.BURST_ENERGY));
        queueCommand(simulator, new PendingCommand(
                castTime + 75.0 * FRAME,
                CommandKind.BURST_ACTIVATE));
        simulator.advanceTime(103.0 * FRAME);
    }

    private void activateBurst(CombatSimulator simulator) {
        double currentTime = simulator.getCurrentTime();
        burstExpirationTime = currentTime
                + getTalentValue("Burst Duration", 16.0);
        adeptalAssistanceStacks = (int) getTalentValue(
                "Adeptal Assistance Stacks", 8.0);
        if (constellation >= 6) {
            c6ExpirationTime = currentTime
                    + getTalentValue("C6 Duration", 16.0);
            c6FreeSkillUses = (int) getTalentValue(
                    "C6 Free Skill Uses", 8.0);
        }
        queueHit(simulator, new PendingHit(
                currentTime,
                HitKind.BURST_INITIAL,
                0,
                captureLiveStats(currentTime),
                false,
                false));
    }

    private void handleAcceptedDamage(
            Character actor,
            AttackAction action,
            double damage,
            double time) {
        if (damage <= 0.0 || action == null || actor == null) {
            return;
        }
        if (action == resolvingAction && resolvingWave) {
            addA1Stack(time);
            if (resolvingParticleEligible) {
                queueCommand(initializedSimulator, new PendingCommand(
                        time + getTalentValue(
                                "Particle Travel Frames", 100.0) * FRAME,
                        CommandKind.PARTICLE));
            }
        }
        if (action.getActionType() != ActionType.PLUNGE
                || initializedSimulator.getActiveCharacter() != actor
                || getAdeptalAssistanceStacks(time) <= 0) {
            return;
        }
        adeptalAssistanceStacks--;
        queueHit(initializedSimulator, new PendingHit(
                time + 5.0 * FRAME,
                HitKind.STARWICKER,
                0,
                captureLiveStats(time),
                false,
                false));
    }

    private void addA1Stack(double currentTime) {
        expireA1Stacks(currentTime);
        a1StackExpirations.add(currentTime
                + getTalentValue("A1 Stack Duration", 20.0));
    }

    private void expireA1Stacks(double currentTime) {
        a1StackExpirations.removeIf(
                expiry -> currentTime + EPSILON >= expiry);
    }

    private void refreshTimedState(double currentTime) {
        if (skillLeapCount > 0
                && currentTime + EPSILON >= skillStateExpirationTime) {
            if (!skillSequenceUsesC6) {
                reduceSkillCooldown(
                        skillStateExpirationTime,
                        getTalentValue(
                                "Unused Skill Cooldown Reduction", 3.0));
            }
            skillLeapCount = 0;
            skillSequenceUsesC6 = false;
            skillStateExpirationTime = Double.NEGATIVE_INFINITY;
        }
        if (currentTime + EPSILON >= burstExpirationTime) {
            adeptalAssistanceStacks = 0;
        }
        if (currentTime + EPSILON >= c6ExpirationTime) {
            c6FreeSkillUses = 0;
        }
        expireA1Stacks(currentTime);
    }

    private boolean isC6FreeSkillActive(double currentTime) {
        return constellation >= 6
                && currentTime + EPSILON < c6ExpirationTime
                && c6FreeSkillUses > 0;
    }

    private double a1CritRate(int stacks) {
        switch (stacks) {
            case 1:
                return getTalentValue("A1 Stack 1 CRIT Rate", 0.04);
            case 2:
                return getTalentValue("A1 Stack 2 CRIT Rate", 0.06);
            case 3:
                return getTalentValue("A1 Stack 3 CRIT Rate", 0.08);
            default:
                return getTalentValue("A1 Stack 4 CRIT Rate", 0.10);
        }
    }

    private double c6CritDamage(int leapCount) {
        return getTalentValue(
                "C6 Wave " + leapCount + " CRIT DMG",
                leapCount == 1 ? 0.15 : leapCount == 2 ? 0.35 : 0.70);
    }

    private double skillStateFrames(int leapCount) {
        return leapCount == 1 ? 220.0 : leapCount == 2 ? 238.0 : 179.0;
    }

    private double waveMultiplier(int leapCount) {
        boolean c5 = constellation >= 5;
        String key = "Driftcloud Wave " + leapCount + (c5 ? " C5" : "");
        double fallback;
        if (leapCount == 1) {
            fallback = c5 ? 2.32 : 1.972;
        } else if (leapCount == 2) {
            fallback = c5 ? 2.96 : 2.516;
        } else {
            fallback = c5 ? 6.752 : 5.7392;
        }
        return getTalentValue(key, fallback);
    }

    private double burstMultiplier(String baseKey, double t9, double c3) {
        return getTalentValue(
                baseKey + (constellation >= 3 ? " C3" : ""),
                constellation >= 3 ? c3 : t9);
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

    private void resolveHit(
            CombatSimulator simulator,
            PendingHit hit) {
        if (simulator.getEnemy() == null) {
            return;
        }
        AttackAction action;
        switch (hit.kind) {
            case NORMAL:
                action = attack(
                        "Word of Wind and Flower N" + (hit.index + 1),
                        getTalentValue(
                                "N" + (hit.index + 1),
                                NORMAL_T9[hit.index]),
                        StatType.NORMAL_ATTACK_DMG_BONUS,
                        ActionType.NORMAL,
                        ICDType.Standard,
                        ICDTag.NormalAttack);
                break;
            case CHARGED:
                action = attack(
                        "Word of Wind and Flower Charged Attack",
                        getTalentValue("Charged Attack", 2.09304),
                        StatType.CHARGED_ATTACK_DMG_BONUS,
                        ActionType.CHARGE,
                        ICDType.None,
                        ICDTag.None);
                break;
            case HIGH_PLUNGE:
                action = attack(
                        "Word of Wind and Flower High Plunge",
                        getTalentValue("High Plunge", 2.607632),
                        StatType.PLUNGING_ATTACK_DMG_BONUS,
                        ActionType.PLUNGE,
                        ICDType.None,
                        ICDTag.None);
                break;
            case DRIFTCLOUD:
                action = attack(
                        "Driftcloud Wave (" + hit.index + " Leaps)",
                        waveMultiplier(hit.index),
                        StatType.PLUNGING_ATTACK_DMG_BONUS,
                        ActionType.PLUNGE,
                        ICDType.None,
                        ICDTag.None);
                break;
            case BURST_INITIAL:
                action = attack(
                        "Stars Gather at Dusk (Initial)",
                        burstMultiplier(
                                "Stars Gather at Dusk", 1.836, 2.16),
                        StatType.BURST_DMG_BONUS,
                        ActionType.BURST,
                        ICDType.None,
                        ICDTag.None);
                break;
            case STARWICKER:
                action = attack(
                        "Starwicker",
                        burstMultiplier("Starwicker", 0.6664, 0.784),
                        StatType.BURST_DMG_BONUS,
                        ActionType.BURST,
                        ICDType.Standard,
                        ICDTag.ElementalBurst);
                break;
            default:
                throw new IllegalStateException(
                        "Unknown Xianyun hit kind " + hit.kind);
        }
        action.setCountsAsBurstDmg(
                hit.kind == HitKind.BURST_INITIAL
                        || hit.kind == HitKind.STARWICKER);
        action.setStatSnapshot(hit.snapshot);
        resolvingAction = action;
        resolvingWave = hit.wave;
        resolvingParticleEligible = hit.particleEligible;
        try {
            simulator.performActionWithoutTimeAdvance(characterId, action);
        } finally {
            resolvingAction = null;
            resolvingWave = false;
            resolvingParticleEligible = false;
        }
    }

    private static AttackAction attack(
            String name,
            double multiplier,
            StatType bonusStat,
            ActionType actionType,
            ICDType icdType,
            ICDTag icdTag) {
        AttackAction action = new AttackAction(
                name,
                multiplier,
                Element.ANEMO,
                StatType.BASE_ATK,
                bonusStat,
                0.0,
                actionType);
        action.setICD(icdType, icdTag, 1.0);
        return action;
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
                case BURST_ENERGY:
                    spendBurstEnergy(activeSimulator.getCurrentTime());
                    break;
                case BURST_ACTIVATE:
                    activateBurst(activeSimulator);
                    break;
                case PARTICLE:
                    activeSimulator.getEnergyDistributor()
                            .distributeParticles(
                                    Element.ANEMO,
                                    getTalentValue("Particle Count", 5.0),
                                    ParticleType.PARTICLE);
                    break;
                default:
                    throw new IllegalStateException(
                            "Unknown Xianyun command kind " + command.kind);
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
        HIGH_PLUNGE,
        DRIFTCLOUD,
        BURST_INITIAL,
        STARWICKER
    }

    private enum CommandKind {
        BURST_ENERGY,
        BURST_ACTIVATE,
        PARTICLE
    }

    /** Immutable future Xianyun damage packet with release-time stats. */
    private static final class PendingHit {
        private final double time;
        private final HitKind kind;
        private final int index;
        private final StatsContainer snapshot;
        private final boolean wave;
        private final boolean particleEligible;

        private PendingHit(
                double time,
                HitKind kind,
                int index,
                StatsContainer snapshot,
                boolean wave,
                boolean particleEligible) {
            this.time = time;
            this.kind = kind;
            this.index = index;
            this.snapshot = snapshot.merge(null);
            this.wave = wave;
            this.particleEligible = particleEligible;
        }

        private PendingHit copy() {
            return new PendingHit(
                    time,
                    kind,
                    index,
                    snapshot,
                    wave,
                    particleEligible);
        }
    }

    /** Immutable future Xianyun state command. */
    private static final class PendingCommand {
        private final double time;
        private final CommandKind kind;

        private PendingCommand(double time, CommandKind kind) {
            this.time = time;
            this.kind = kind;
        }

        private PendingCommand copy() {
            return new PendingCommand(time, kind);
        }
    }

    /** Immutable snapshot of all mutable Xianyun-owned simulator state. */
    private static final class XianyunState implements State {
        private final Xianyun owner;
        private final int normalAttackStep;
        private final int skillLeapCount;
        private final boolean skillSequenceUsesC6;
        private final double skillStateExpirationTime;
        private final double burstExpirationTime;
        private final int adeptalAssistanceStacks;
        private final double c6ExpirationTime;
        private final int c6FreeSkillUses;
        private final List<Double> a1StackExpirations;
        private final List<PendingHit> pendingHits;
        private final List<PendingCommand> pendingCommands;

        private XianyunState(
                Xianyun owner,
                int normalAttackStep,
                int skillLeapCount,
                boolean skillSequenceUsesC6,
                double skillStateExpirationTime,
                double burstExpirationTime,
                int adeptalAssistanceStacks,
                double c6ExpirationTime,
                int c6FreeSkillUses,
                List<Double> a1StackExpirations,
                List<PendingHit> pendingHits,
                List<PendingCommand> pendingCommands) {
            this.owner = owner;
            this.normalAttackStep = normalAttackStep;
            this.skillLeapCount = skillLeapCount;
            this.skillSequenceUsesC6 = skillSequenceUsesC6;
            this.skillStateExpirationTime = skillStateExpirationTime;
            this.burstExpirationTime = burstExpirationTime;
            this.adeptalAssistanceStacks = adeptalAssistanceStacks;
            this.c6ExpirationTime = c6ExpirationTime;
            this.c6FreeSkillUses = c6FreeSkillUses;
            this.a1StackExpirations = new ArrayList<>(a1StackExpirations);
            this.pendingHits = copyHits(pendingHits);
            this.pendingCommands = copyCommands(pendingCommands);
        }
    }
}

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
import simulation.event.SimpleTimerEvent;

/**
 * Xiao's fixed-target offensive Burst-infusion slice through C5.
 *
 * <p>Whirlwind Thrust, Lemniscatic Wind Cycling, Bane of All Evil,
 * particles, A1, A4, and C1-C3/C5 follow pinned gcsim
 * {@code ef41805d}. Burst state converts Normal, Charged, and High Plunge
 * damage to Anemo and applies the sourced action bonus. Skill charges,
 * delayed state transitions, and queued hit snapshots survive rollback.</p>
 *
 * <p>Player HP drain and healing, C4, C6's multi-target plunge reset,
 * collision damage, geometry, stamina, jump height, low-plunge selection,
 * and complete hitlag coverage are intentionally excluded without approximation.</p>
 */
public final class Xiao extends Character implements
        SimulatorInitializedCharacterEffect,
        SnapshotAwareCharacterEffect,
        SwitchAwareCharacter {
    private static final double FRAME = 1.0 / 60.0;
    private static final double EPSILON = 1e-9;
    /** Hitlag data pinned to gcsim {@code 3647a07a7cc3004bc1e79d9bb5f7444de20dceaa}. */
    private static final HitlagProfile[][] BURST_NORMAL_HITLAG = {
        {
            new HitlagProfile(0.01, 0.01, false, false, false),
            new HitlagProfile(0.01, 0.01, true, false, false)
        },
        { new HitlagProfile(0.01, 0.01, true, false, false) },
        { new HitlagProfile(0.01, 0.01, true, false, false) },
        {
            new HitlagProfile(0.02, 0.01, false, false, false),
            new HitlagProfile(0.02, 0.01, true, false, false)
        },
        { new HitlagProfile(0.02, 0.01, true, false, false) },
        { new HitlagProfile(0.04, 0.01, true, false, false) }
    };
    private static final HitlagProfile[][] NORMAL_HITLAG = {
        {
            new HitlagProfile(0.0, 0.01, false, false, false),
            new HitlagProfile(0.01, 0.01, true, false, false)
        },
        { new HitlagProfile(0.01, 0.01, true, false, false) },
        { new HitlagProfile(0.01, 0.01, true, false, false) },
        {
            new HitlagProfile(0.02, 0.01, false, false, false),
            new HitlagProfile(0.02, 0.01, true, false, false)
        },
        { new HitlagProfile(0.02, 0.01, true, false, false) },
        { new HitlagProfile(0.04, 0.01, true, false, false) }
    };
    private static final HitlagProfile CHARGED_HITLAG =
            new HitlagProfile(0.02, 0.01, true, false, false);
    private static final HitlagProfile BURST_CHARGED_HITLAG =
            new HitlagProfile(0.04, 0.01, true, false, false);
    private static final int[][] NORMAL_HIT_FRAMES = {
        { 4, 17 }, { 15 }, { 15 }, { 14, 31 }, { 16 }, { 39 }
    };
    private static final int[] NORMAL_DURATIONS = {
        26, 27, 38, 42, 30, 79
    };
    private static final double[][] NORMAL_T9 = {
        { 0.463240, 0.463240 },
        { 0.957560 },
        { 1.152920 },
        { 0.633440, 0.633440 },
        { 1.203240 },
        { 1.611720 }
    };

    private CombatSimulator initializedSimulator;
    private int normalAttackStep;
    private double burstStartTime = Double.NEGATIVE_INFINITY;
    private double burstExpirationTime = Double.NEGATIVE_INFINITY;
    private int a4Stacks;
    private double a4ExpirationTime = Double.NEGATIVE_INFINITY;
    private List<PendingHit> pendingHits = new ArrayList<>();
    private List<PendingCommand> pendingCommands = new ArrayList<>();

    /** Constructs repository-default C6 Xiao. */
    public Xiao(Weapon weapon, ArtifactSet artifacts) {
        this(weapon, artifacts, TalentDataManager.getInstance(), 6);
    }

    /** Constructs Xiao at an explicit constellation. */
    public Xiao(Weapon weapon, ArtifactSet artifacts, int constellation) {
        this(weapon, artifacts, TalentDataManager.getInstance(), constellation);
    }

    /**
     * Constructs Xiao with injectable static talent data.
     *
     * @param weapon equipped polearm, or {@code null}
     * @param artifacts equipped artifact set, or {@code null}
     * @param talentData static character-data source
     * @param constellation constellation level in {@code [0, 6]}
     */
    public Xiao(
            Weapon weapon,
            ArtifactSet artifacts,
            TalentDataSource talentData,
            int constellation) {
        super(talentData);
        if (constellation < 0 || constellation > 6) {
            throw new IllegalArgumentException(
                    "Xiao constellation must be between 0 and 6");
        }
        name = "Xiao";
        characterId = CharacterId.XIAO;
        element = Element.ANEMO;
        this.weapon = weapon;
        this.artifacts = new ArtifactSet[] { artifacts };
        this.constellation = constellation;
        baseStats.set(StatType.BASE_HP,
                getTalentValue("Base HP", 12736.0));
        baseStats.set(StatType.BASE_ATK,
                getTalentValue("Base ATK", 349.0));
        baseStats.set(StatType.BASE_DEF,
                getTalentValue("Base DEF", 799.0));
        baseStats.add(StatType.CRIT_RATE,
                getTalentValue("Ascension CRIT Rate", 0.192));
        setSkillCD(getTalentValue("Skill Cooldown", 10.0));
        setBurstCD(getTalentValue("Burst Cooldown", 18.0));
        setSkillMaxCharges(constellation >= 1 ? 3 : 2);
    }

    /** Binds Xiao's delayed work and dynamic C2 state to one simulator. */
    @Override
    public void initializeForSimulator(CombatSimulator simulator) {
        if (simulator == null) {
            throw new IllegalArgumentException("Xiao simulator is required");
        }
        if (!simulator.getPartyMembers().contains(this)) {
            throw new IllegalArgumentException(
                    "Xiao must belong to the simulator party");
        }
        if (initializedSimulator != null) {
            if (initializedSimulator != simulator) {
                throw new IllegalStateException(
                        "Xiao cannot be reused across simulators");
            }
            return;
        }
        initializedSimulator = simulator;
    }

    /** Captures combo, Burst/A4 windows, and reconstructable future work. */
    @Override
    public State captureCharacterState() {
        return new XiaoState(
                this,
                normalAttackStep,
                burstStartTime,
                burstExpirationTime,
                a4Stacks,
                a4ExpirationTime,
                pendingHits,
                pendingCommands);
    }

    /** Accepts state captured from this exact Xiao instance only. */
    @Override
    public boolean acceptsCharacterState(State state) {
        return state instanceof XiaoState
                && ((XiaoState) state).owner == this;
    }

    /** Restores Xiao-owned state and registers each surviving event once. */
    @Override
    public void restoreCharacterState(
            State state,
            CombatSimulator simulator) {
        if (!acceptsCharacterState(state)) {
            throw new IllegalArgumentException("Unexpected Xiao state");
        }
        initializeForSimulator(simulator);
        XiaoState restored = (XiaoState) state;
        normalAttackStep = restored.normalAttackStep;
        burstStartTime = restored.burstStartTime;
        burstExpirationTime = restored.burstExpirationTime;
        a4Stacks = restored.a4Stacks;
        a4ExpirationTime = restored.a4ExpirationTime;
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

    /** Returns Xiao's 70-Energy Burst cost. */
    @Override
    public double getEnergyCost() {
        return getTalentValue("Energy Cost", 70.0);
    }

    /** Applies C2's exact off-field Energy Recharge branch. */
    @Override
    public void applyPassive(StatsContainer stats) {
        if (constellation >= 2
                && initializedSimulator != null
                && initializedSimulator.getActiveCharacter() != this) {
            stats.add(StatType.ENERGY_RECHARGE,
                    getTalentValue(
                            "C2 Off-Field Energy Recharge", 0.25));
        }
    }

    /** Ends Bane of All Evil and resets the Normal string on switch-out. */
    @Override
    public void onSwitchOut(CombatSimulator simulator) {
        normalAttackStep = 0;
        burstStartTime = Double.NEGATIVE_INFINITY;
        burstExpirationTime = Double.NEGATIVE_INFINITY;
    }

    /** Returns whether Bane of All Evil is active at a half-open boundary. */
    public boolean isBurstActive(double currentTime) {
        return currentTime + EPSILON < burstExpirationTime;
    }

    /** Returns A1's generic damage bonus at the supplied action time. */
    public double getA1DamageBonus(double currentTime) {
        if (!isBurstActive(currentTime)) {
            return 0.0;
        }
        double initial = getTalentValue(
                "A1 Initial DMG Bonus", 0.05);
        double interval = getTalentValue("A1 Stack Interval", 3.0);
        int tier = 1 + (int) Math.floor(
                Math.max(0.0, currentTime - burstStartTime) / interval);
        return Math.min(
                getTalentValue("A1 Max DMG Bonus", 0.25),
                tier * initial);
    }

    /** Returns current unexpired A4 stacks. */
    public int getA4Stacks(double currentTime) {
        expireA4IfNeeded(currentTime);
        return a4Stacks;
    }

    /** Returns the number of unresolved Xiao-owned damage hits. */
    public int getPendingHitCount() {
        return pendingHits.size();
    }

    /** Dispatches Xiao's represented typed offensive actions. */
    @Override
    public void onAction(
            CharacterActionRequest request,
            CombatSimulator simulator) {
        if (request == null) {
            throw new IllegalArgumentException("Xiao action is required");
        }
        initializeForSimulator(simulator);
        expireA4IfNeeded(simulator.getCurrentTime());
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
                lemniscaticWindCycling(simulator);
                break;
            case BURST:
                baneOfAllEvil(simulator);
                break;
            default:
                throw new IllegalArgumentException(
                        "Unsupported action for Xiao: " + request.getKey());
        }
    }

    private void normalAttack(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        int step = normalAttackStep;
        boolean enhanced = isBurstActive(castTime);
        StatsContainer snapshot = captureActionStats(
                castTime, enhanced, StatType.NORMAL_ATTACK_DMG_BONUS, 0.0);
        for (int hit = 0; hit < NORMAL_HIT_FRAMES[step].length; hit++) {
            queueHit(simulator, new PendingHit(
                    castTime + NORMAL_HIT_FRAMES[step][hit] * FRAME,
                    HitKind.NORMAL,
                    step,
                    hit,
                    enhanced,
                    false,
                    snapshot));
        }
        normalAttackStep = (normalAttackStep + 1)
                % NORMAL_HIT_FRAMES.length;
        simulator.advanceTime(NORMAL_DURATIONS[step] * FRAME);
    }

    private void chargedAttack(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        boolean enhanced = isBurstActive(castTime);
        StatsContainer snapshot = captureActionStats(
                castTime, enhanced, StatType.CHARGED_ATTACK_DMG_BONUS, 0.0);
        queueHit(simulator, new PendingHit(
                castTime + 16.0 * FRAME,
                HitKind.CHARGED,
                0,
                0,
                enhanced,
                false,
                snapshot));
        normalAttackStep = 0;
        simulator.advanceTime(45.0 * FRAME);
    }

    private void highPlunge(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        boolean enhanced = isBurstActive(castTime);
        StatsContainer snapshot = captureActionStats(
                castTime, enhanced, StatType.PLUNGING_ATTACK_DMG_BONUS, 0.0);
        queueHit(simulator, new PendingHit(
                castTime + 46.0 * FRAME,
                HitKind.PLUNGE,
                0,
                0,
                enhanced,
                false,
                snapshot));
        normalAttackStep = 0;
        simulator.advanceTime(66.0 * FRAME);
    }

    private void lemniscaticWindCycling(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        boolean suppressParticles = isBurstActive(castTime);
        double a4Bonus = getA4Stacks(castTime)
                * getTalentValue("A4 Skill DMG Per Stack", 0.15);
        StatsContainer snapshot = captureActionStats(
                castTime, false, StatType.SKILL_DMG_BONUS, a4Bonus);
        markSkillUsed(castTime, simulator.getApplicableBuffs(this));
        queueHit(simulator, new PendingHit(
                castTime + 4.0 * FRAME,
                HitKind.SKILL,
                0,
                0,
                false,
                !suppressParticles,
                snapshot));
        queueCommand(simulator, new PendingCommand(
                castTime + getTalentValue(
                        "A4 Activation Frames", 15.0) * FRAME,
                CommandKind.A4_STACK,
                0.0));
        normalAttackStep = 0;
        simulator.advanceTime(37.0 * FRAME);
    }

    private void baneOfAllEvil(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        burstStartTime = castTime;
        burstExpirationTime = castTime
                + getTalentValue("Burst Duration", 15.95);
        queueCommand(simulator, new PendingCommand(
                castTime + 29.0 * FRAME,
                CommandKind.BURST_COOLDOWN,
                0.0));
        queueCommand(simulator, new PendingCommand(
                castTime + 36.0 * FRAME,
                CommandKind.BURST_ENERGY,
                0.0));
        normalAttackStep = 0;
        simulator.advanceTime(82.0 * FRAME);
    }

    private void resolveHit(CombatSimulator simulator, PendingHit hit) {
        switch (hit.kind) {
            case NORMAL:
                performHit(
                        simulator,
                        hit,
                        "Whirlwind Thrust N" + (hit.index + 1)
                                + (NORMAL_HIT_FRAMES[hit.index].length > 1
                                        ? " Hit " + (hit.variant + 1) : ""),
                        normalValue(hit.index, hit.variant),
                        hit.burstEnhanced ? Element.ANEMO : Element.PHYSICAL,
                        StatType.NORMAL_ATTACK_DMG_BONUS,
                        ActionType.NORMAL,
                        ICDType.Standard,
                        ICDTag.NormalAttack,
                        hit.burstEnhanced ? 1.0 : 0.0);
                break;
            case CHARGED:
                performHit(
                        simulator,
                        hit,
                        "Whirlwind Thrust Charged Attack",
                        getTalentValue("Charged Attack", 2.036480),
                        hit.burstEnhanced ? Element.ANEMO : Element.PHYSICAL,
                        StatType.CHARGED_ATTACK_DMG_BONUS,
                        ActionType.CHARGE,
                        ICDType.Standard,
                        hit.burstEnhanced
                                ? ICDTag.NormalAttack : ICDTag.ChargedAttack,
                        hit.burstEnhanced ? 1.0 : 0.0);
                break;
            case PLUNGE:
                performHit(
                        simulator,
                        hit,
                        "Whirlwind Thrust High Plunge",
                        getTalentValue("High Plunge", 3.754990),
                        hit.burstEnhanced ? Element.ANEMO : Element.PHYSICAL,
                        StatType.PLUNGING_ATTACK_DMG_BONUS,
                        ActionType.PLUNGE,
                        ICDType.None,
                        ICDTag.None,
                        hit.burstEnhanced ? 1.0 : 0.0);
                break;
            case SKILL:
                performHit(
                        simulator,
                        hit,
                        "Lemniscatic Wind Cycling",
                        skillValue(),
                        Element.ANEMO,
                        StatType.SKILL_DMG_BONUS,
                        ActionType.SKILL,
                        ICDType.None,
                        ICDTag.ElementalSkill,
                        1.0);
                if (hit.generatesParticles && simulator.getEnemy() != null) {
                    queueCommand(simulator, new PendingCommand(
                            hit.time + getTalentValue(
                                    "Particle Travel Frames", 100.0) * FRAME,
                            CommandKind.PARTICLE,
                            getTalentValue("Particle Count", 3.0)));
                }
                break;
            default:
                throw new IllegalStateException(
                        "Unknown Xiao hit kind " + hit.kind);
        }
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
            double gaugeUnits) {
        AttackAction action = new AttackAction(
                displayName,
                multiplier,
                hitElement,
                StatType.BASE_ATK,
                bonusStat,
                0.0,
                actionType);
        action.setICD(icdType, icdTag, gaugeUnits);
        action.setHitlagProfile(hitlagProfile(hit));
        action.setCountsAsSkillDmg(actionType == ActionType.SKILL);
        action.setStatSnapshot(hit.snapshot);
        simulator.performActionWithoutTimeAdvance(characterId, action);
    }

    private static HitlagProfile hitlagProfile(PendingHit hit) {
        if (hit.kind == HitKind.CHARGED) {
            return hit.burstEnhanced
                    ? BURST_CHARGED_HITLAG : CHARGED_HITLAG;
        }
        if (hit.kind == HitKind.NORMAL) {
            HitlagProfile[][] profiles = hit.burstEnhanced
                    ? BURST_NORMAL_HITLAG : NORMAL_HITLAG;
            return profiles[hit.index][hit.variant];
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
        return getTalentValue(
                constellation >= 3
                        ? "Lemniscatic Wind Cycling C3"
                        : "Lemniscatic Wind Cycling",
                constellation >= 3 ? 5.056000 : 4.297600);
    }

    private double burstAttackBonus() {
        return getTalentValue(
                constellation >= 5
                        ? "Bane of All Evil Bonus C5"
                        : "Bane of All Evil Bonus",
                constellation >= 5 ? 1.043000 : 0.906500);
    }

    private StatsContainer captureActionStats(
            double currentTime,
            boolean burstEnhanced,
            StatType burstBonusStat,
            double extraActionBonus) {
        StatsContainer stats = getEffectiveStats(currentTime);
        if (initializedSimulator != null) {
            for (Buff buff : initializedSimulator.getApplicableBuffs(this)) {
                if (!buff.isExpired(currentTime)) {
                    buff.apply(stats, currentTime);
                }
            }
        }
        double a1Bonus = getA1DamageBonus(currentTime);
        if (a1Bonus != 0.0) {
            stats.add(StatType.DMG_BONUS_ALL, a1Bonus);
        }
        if (burstEnhanced && burstBonusStat != null) {
            stats.add(burstBonusStat, burstAttackBonus());
        }
        if (extraActionBonus != 0.0 && burstBonusStat != null) {
            stats.add(burstBonusStat, extraActionBonus);
        }
        return stats;
    }

    private void gainA4Stack(double currentTime) {
        expireA4IfNeeded(currentTime);
        a4Stacks = Math.min(
                (int) getTalentValue("A4 Max Stacks", 3.0),
                a4Stacks + 1);
        a4ExpirationTime = currentTime
                + getTalentValue("A4 Duration", 7.0);
    }

    private void expireA4IfNeeded(double currentTime) {
        if (currentTime + EPSILON >= a4ExpirationTime) {
            a4Stacks = 0;
        }
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
                case A4_STACK:
                    gainA4Stack(activeSimulator.getCurrentTime());
                    break;
                case BURST_COOLDOWN:
                    markBurstCooldownUsed(
                            activeSimulator.getCurrentTime(),
                            activeSimulator.getApplicableBuffs(this));
                    break;
                case BURST_ENERGY:
                    spendBurstEnergy(activeSimulator.getCurrentTime());
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
                            "Unknown Xiao command kind " + command.kind);
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
        SKILL
    }

    private enum CommandKind {
        A4_STACK,
        BURST_COOLDOWN,
        BURST_ENERGY,
        PARTICLE
    }

    /** Immutable future Xiao damage hit with queue-time stats. */
    private static final class PendingHit {
        private final double time;
        private final HitKind kind;
        private final int index;
        private final int variant;
        private final boolean burstEnhanced;
        private final boolean generatesParticles;
        private final StatsContainer snapshot;

        private PendingHit(
                double time,
                HitKind kind,
                int index,
                int variant,
                boolean burstEnhanced,
                boolean generatesParticles,
                StatsContainer snapshot) {
            this.time = time;
            this.kind = kind;
            this.index = index;
            this.variant = variant;
            this.burstEnhanced = burstEnhanced;
            this.generatesParticles = generatesParticles;
            this.snapshot = snapshot.merge(null);
        }

        private PendingHit copy() {
            return new PendingHit(
                    time,
                    kind,
                    index,
                    variant,
                    burstEnhanced,
                    generatesParticles,
                    snapshot);
        }
    }

    /** Immutable future Xiao state-only command. */
    private static final class PendingCommand {
        private final double time;
        private final CommandKind kind;
        private final double value;

        private PendingCommand(
                double time,
                CommandKind kind,
                double value) {
            this.time = time;
            this.kind = kind;
            this.value = value;
        }

        private PendingCommand copy() {
            return new PendingCommand(time, kind, value);
        }
    }

    /** Immutable snapshot of all mutable Xiao-owned simulator state. */
    private static final class XiaoState implements State {
        private final Xiao owner;
        private final int normalAttackStep;
        private final double burstStartTime;
        private final double burstExpirationTime;
        private final int a4Stacks;
        private final double a4ExpirationTime;
        private final List<PendingHit> pendingHits;
        private final List<PendingCommand> pendingCommands;

        private XiaoState(
                Xiao owner,
                int normalAttackStep,
                double burstStartTime,
                double burstExpirationTime,
                int a4Stacks,
                double a4ExpirationTime,
                List<PendingHit> pendingHits,
                List<PendingCommand> pendingCommands) {
            this.owner = owner;
            this.normalAttackStep = normalAttackStep;
            this.burstStartTime = burstStartTime;
            this.burstExpirationTime = burstExpirationTime;
            this.a4Stacks = a4Stacks;
            this.a4ExpirationTime = a4ExpirationTime;
            this.pendingHits = copyHits(pendingHits);
            this.pendingCommands = copyCommands(pendingCommands);
        }
    }
}

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
 * Candace's fixed-target offensive Crimson Crown kit through C6.
 *
 * <p>Physical basics, Press/Hold Skill timing, HP-scaling damage, particles,
 * Burst support, the first outgoing switch wave, and C1-C6 follow pinned gcsim
 * {@code ef41805d}. The team Normal bonus uses the repository's stat-buff
 * boundary and therefore assumes the recipient's represented Normal is
 * elemental.</p>
 *
 * <p>Shield absorption, A1's incoming-hit response, generic cross-character
 * Hydro infusion, later third-party switch waves, geometry, and hitlag extension are
 * outside this vertical slice.</p>
 */
public final class Candace extends Character implements
        SimulatorInitializedCharacterEffect,
        SnapshotAwareCharacterEffect,
        SwitchAwareCharacter {
    private static final double FRAME = 1.0 / 60.0;
    private static final double EPSILON = 1e-9;
    private static final double PARTICLE_TRAVEL = 100.0 * FRAME;
    private static final int[][] NORMAL_HIT_FRAMES = {
        { 11 }, { 16 }, { 16, 39 }, { 43 }
    };
    private static final int[] NORMAL_DURATIONS = { 32, 33, 48, 69 };
    private static final String[][] NORMAL_KEYS = {
        { "N1" }, { "N2" }, { "N3 Hit 1", "N3 Hit 2" }, { "N4" }
    };
    private static final double[][] NORMAL_T9 = {
        { 1.117060 }, { 1.123380 }, { 0.651987, 0.796873 }, { 1.744320 }
    };

    /**
     * Per-hit hitlag from gcsim config YAML pinned at
     * {@code 3647a07a7cc3004bc1e79d9bb5f7444de20dceaa}.
     */
    private static final HitlagProfile NORMAL_SHORT_HITLAG =
            new HitlagProfile(0.03, 0.01, true, false, false);
    private static final HitlagProfile NORMAL_FINAL_HITLAG =
            new HitlagProfile(0.04, 0.01, true, false, false);
    private static final HitlagProfile CHARGED_HITLAG =
            new HitlagProfile(0.0, 0.01, true, true, false);
    private static final HitlagProfile SKILL_HITLAG =
            new HitlagProfile(0.05, 0.01, true, false, false);
    private static final HitlagProfile BURST_HITLAG =
            new HitlagProfile(0.0, 0.01, true, false, false);

    private CombatSimulator initializedSimulator;
    private int normalAttackStep;
    private int switchWaveCount;
    private double burstUntil = Double.NEGATIVE_INFINITY;
    private double nextParticleTime = Double.NEGATIVE_INFINITY;
    private double nextC6Time = Double.NEGATIVE_INFINITY;
    private List<PendingHit> pendingHits = new ArrayList<>();
    private List<PendingCommand> pendingCommands = new ArrayList<>();

    /** Constructs repository-default C6 Candace. */
    public Candace(Weapon weapon, ArtifactSet artifacts) {
        this(weapon, artifacts, TalentDataManager.getInstance(), 6);
    }

    /** Constructs Candace at an explicit constellation. */
    public Candace(Weapon weapon, ArtifactSet artifacts, int constellation) {
        this(weapon, artifacts, TalentDataManager.getInstance(), constellation);
    }

    /** Constructs Candace with injectable talent data. */
    public Candace(
            Weapon weapon,
            ArtifactSet artifacts,
            TalentDataSource talentData,
            int constellation) {
        super(talentData);
        if (constellation < 0 || constellation > 6) {
            throw new IllegalArgumentException(
                    "Candace constellation must be between 0 and 6");
        }
        name = "Candace";
        characterId = CharacterId.CANDACE;
        element = Element.HYDRO;
        this.weapon = weapon;
        this.artifacts = new ArtifactSet[] { artifacts };
        this.constellation = constellation;
        baseStats.set(StatType.BASE_HP,
                getTalentValue("Base HP", 10875.0));
        baseStats.set(StatType.BASE_ATK,
                getTalentValue("Base ATK", 212.0));
        baseStats.set(StatType.BASE_DEF,
                getTalentValue("Base DEF", 682.0));
        baseStats.add(StatType.HP_PERCENT,
                getTalentValue("Ascension HP", 0.24));
        setSkillCD(getTalentValue("Skill Press Cooldown", 6.0));
        setBurstCD(getTalentValue("Burst Cooldown", 15.0));
    }

    /** Binds delayed work and C6 damage observation to one simulator. */
    @Override
    public void initializeForSimulator(CombatSimulator simulator) {
        if (simulator == null) {
            throw new IllegalArgumentException("Candace simulator is required");
        }
        if (initializedSimulator != null
                && initializedSimulator != simulator) {
            throw new IllegalStateException(
                    "Candace cannot be reused across simulators");
        }
        if (initializedSimulator == simulator) {
            return;
        }
        if (!simulator.getPartyMembers().contains(this)) {
            throw new IllegalArgumentException(
                    "Candace must belong to the target simulator party");
        }
        initializedSimulator = simulator;
        simulator.addDamageListener((actor, action, damage, time) ->
                onResolvedDamage(actor, action, damage, time, simulator));
    }

    /** Captures windows, gates, counters, and delayed work. */
    @Override
    public State captureCharacterState() {
        return new CandaceState(
                this,
                normalAttackStep,
                switchWaveCount,
                burstUntil,
                nextParticleTime,
                nextC6Time,
                pendingHits,
                pendingCommands);
    }

    /** Accepts only state captured from this exact Candace instance. */
    @Override
    public boolean acceptsCharacterState(State state) {
        return state instanceof CandaceState
                && ((CandaceState) state).owner == this;
    }

    /** Restores Candace state and reconstructs surviving events once. */
    @Override
    public void restoreCharacterState(
            State state,
            CombatSimulator simulator) {
        if (!acceptsCharacterState(state)) {
            throw new IllegalArgumentException("Unexpected Candace state");
        }
        initializeForSimulator(simulator);
        CandaceState restored = (CandaceState) state;
        normalAttackStep = restored.normalAttackStep;
        switchWaveCount = restored.switchWaveCount;
        burstUntil = restored.burstUntil;
        nextParticleTime = restored.nextParticleTime;
        nextC6Time = restored.nextC6Time;
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

    /** Returns Candace's 60-Energy Burst cost. */
    @Override
    public double getEnergyCost() {
        return getTalentValue("Energy Cost", 60.0);
    }

    /** Candace has no unconditional static passive stat. */
    @Override
    public void applyPassive(StatsContainer stats) {
    }

    /** Supports both Press and Hold Skill actions. */
    @Override
    protected boolean supportsSkillActionMode(SkillActionMode mode) {
        return mode == SkillActionMode.PRESS || mode == SkillActionMode.HOLD;
    }

    /** Queues the first represented Burst wave when Candace leaves the field. */
    @Override
    public void onSwitchOut(CombatSimulator simulator) {
        normalAttackStep = 0;
        double currentTime = simulator.getCurrentTime();
        if (simulator == initializedSimulator
                && currentTime < burstUntil
                && switchWaveCount < 3) {
            switchWaveCount++;
            queueHit(simulator, new PendingHit(
                    currentTime + FRAME,
                    HitKind.BURST_WAVE,
                    0,
                    0,
                    captureLiveStats(currentTime).getTotalHp(),
                    null));
        }
    }

    /** Returns whether Crimson Crown is active at the supplied time. */
    public boolean isCrimsonCrownActive(double currentTime) {
        return currentTime < burstUntil;
    }

    /** Dispatches Candace's represented typed action set. */
    @Override
    public void onAction(
            CharacterActionRequest request,
            CombatSimulator simulator) {
        if (request == null) {
            throw new IllegalArgumentException("Candace action is required");
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
                heronsSanctum(simulator,
                        request.getSkillMode() == SkillActionMode.HOLD);
                break;
            case BURST:
                wagtailsTide(simulator);
                break;
            default:
                throw new IllegalArgumentException(
                        "Unsupported action for Candace: " + request.getKey());
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
                    Double.NaN,
                    null));
        }
        normalAttackStep = (normalAttackStep + 1) % NORMAL_HIT_FRAMES.length;
        simulator.advanceTime(NORMAL_DURATIONS[step] * FRAME);
    }

    private void chargedAttack(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        queueHit(simulator, new PendingHit(
                castTime + 25.0 * FRAME,
                HitKind.CHARGED,
                0,
                0,
                Double.NaN,
                null));
        simulator.advanceTime(53.0 * FRAME);
    }

    private void highPlunge(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        queueHit(simulator, new PendingHit(
                castTime + 45.0 * FRAME,
                HitKind.PLUNGE,
                0,
                0,
                Double.NaN,
                null));
        simulator.advanceTime(82.0 * FRAME);
    }

    private void heronsSanctum(
            CombatSimulator simulator,
            boolean hold) {
        double castTime = simulator.getCurrentTime();
        int hitFrame = hold ? 91 : 16;
        queueHit(simulator, new PendingHit(
                castTime + hitFrame * FRAME,
                hold ? HitKind.SKILL_HOLD : HitKind.SKILL_PRESS,
                0,
                0,
                captureLiveStats(castTime).getTotalHp(),
                null));
        queueCommand(simulator, new PendingCommand(
                castTime + (hold ? 89.0 : 14.0) * FRAME,
                CommandKind.SKILL_COOLDOWN,
                hold ? 1 : 0));
        simulator.advanceTime((hold ? 113.0 : 26.0) * FRAME);
    }

    private void wagtailsTide(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        switchWaveCount = 0;
        markBurstCooldownUsed(castTime, simulator.getApplicableBuffs(this));
        queueCommand(simulator, new PendingCommand(
                castTime + 4.0 * FRAME,
                CommandKind.BURST_ENERGY,
                0));
        queueHit(simulator, new PendingHit(
                castTime + 33.0 * FRAME,
                HitKind.BURST_INITIAL,
                0,
                0,
                captureLiveStats(castTime).getTotalHp(),
                null));
        simulator.advanceTime(51.0 * FRAME);
    }

    private void resolveHit(CombatSimulator simulator, PendingHit hit) {
        switch (hit.kind) {
            case NORMAL:
                resolveNormal(simulator, hit);
                break;
            case CHARGED:
                performHit(simulator, hit, "Gleaming Spear Charged Attack",
                        getTalentValue("Charged Attack", 2.281520),
                        Element.PHYSICAL,
                        StatType.BASE_ATK,
                        StatType.CHARGED_ATTACK_DMG_BONUS,
                        ActionType.CHARGE,
                        ICDType.Standard,
                        ICDTag.ChargedAttack,
                        1.0);
                break;
            case PLUNGE:
                performHit(simulator, hit, "Gleaming Spear High Plunge",
                        getTalentValue("High Plunge", 2.933586),
                        Element.PHYSICAL,
                        StatType.BASE_ATK,
                        StatType.PLUNGING_ATTACK_DMG_BONUS,
                        ActionType.PLUNGE,
                        ICDType.None,
                        ICDTag.PlungeAttack,
                        0.0);
                break;
            case SKILL_PRESS:
                resolveSkill(simulator, hit, false);
                break;
            case SKILL_HOLD:
                resolveSkill(simulator, hit, true);
                break;
            case BURST_INITIAL:
                resolveBurstInitial(simulator, hit);
                break;
            case BURST_WAVE:
                resolveBurstWave(simulator, hit, false);
                break;
            case C6_WAVE:
                resolveBurstWave(simulator, hit, true);
                break;
            default:
                throw new IllegalStateException("Unknown Candace hit kind");
        }
    }

    private void resolveNormal(CombatSimulator simulator, PendingHit hit) {
        int step = hit.index;
        performHit(simulator, hit,
                "Gleaming Spear " + NORMAL_KEYS[step][hit.variant],
                getTalentValue(
                        NORMAL_KEYS[step][hit.variant],
                        NORMAL_T9[step][hit.variant]),
                Element.PHYSICAL,
                StatType.BASE_ATK,
                StatType.NORMAL_ATTACK_DMG_BONUS,
                ActionType.NORMAL,
                ICDType.Standard,
                ICDTag.NormalAttack,
                1.0);
    }

    private void resolveSkill(
            CombatSimulator simulator,
            PendingHit hit,
            boolean hold) {
        String key = hold ? "Heron's Sanctum Hold" : "Heron's Sanctum Press";
        String suffix = constellation >= 5 ? " C5" : "";
        double fallback = hold
                ? (constellation >= 5 ? 0.380800 : 0.323680)
                : (constellation >= 5 ? 0.240000 : 0.204000);
        performHit(simulator, hit,
                hold
                        ? "Sacred Rite: Heron's Sanctum (Charged)"
                        : "Sacred Rite: Heron's Sanctum",
                getTalentValue(key + suffix, fallback),
                Element.HYDRO,
                StatType.BASE_HP,
                StatType.SKILL_DMG_BONUS,
                ActionType.SKILL,
                ICDType.None,
                ICDTag.ElementalSkill,
                1.0);
        if (constellation >= 2) {
            applyC2(simulator.getCurrentTime());
        }
        if (simulator.getCurrentTime() + EPSILON >= nextParticleTime) {
            nextParticleTime = simulator.getCurrentTime() + 0.5;
            queueCommand(simulator, new PendingCommand(
                    simulator.getCurrentTime() + PARTICLE_TRAVEL,
                    CommandKind.PARTICLE,
                    hold ? 3 : 2));
        }
    }

    private void resolveBurstInitial(
            CombatSimulator simulator,
            PendingHit hit) {
        performHit(simulator, hit,
                "Sacred Rite: Wagtail's Tide (Initial)",
                burstValue("Wagtail Initial", 0.112377, 0.132208),
                Element.HYDRO,
                StatType.BASE_HP,
                StatType.BURST_DMG_BONUS,
                ActionType.BURST,
                ICDType.None,
                ICDTag.ElementalBurst,
                1.0);
        double duration = getTalentValue(
                constellation >= 1
                        ? "Burst Duration C1" : "Burst Duration",
                constellation >= 1 ? 12.0 : 9.0);
        burstUntil = simulator.getCurrentTime() + duration;
        applyCrimsonCrown(simulator, duration);
    }

    private void resolveBurstWave(
            CombatSimulator simulator,
            PendingHit hit,
            boolean c6) {
        performHit(simulator, hit,
                c6
                        ? "The Overflow (C6)"
                        : "Sacred Rite: Wagtail's Tide (Wave)",
                c6
                        ? getTalentValue("C6 Wave", 0.15)
                        : burstValue("Wagtail Wave", 0.112377, 0.132208),
                Element.HYDRO,
                StatType.BASE_HP,
                StatType.BURST_DMG_BONUS,
                ActionType.BURST,
                ICDType.None,
                ICDTag.ElementalBurst,
                1.0);
    }

    private void applyC2(double currentTime) {
        removeBuff(BuffId.CANDACE_C2_MAX_HP);
        addBuff(new SimpleBuff(
                "Candace C2 Max HP",
                BuffId.CANDACE_C2_MAX_HP,
                getTalentValue("C2 Duration", 15.0),
                currentTime,
                stats -> stats.add(
                        StatType.HP_PERCENT,
                        getTalentValue("C2 Max HP", 0.20)))
                .sourcedBy(characterId));
    }

    private void applyCrimsonCrown(
            CombatSimulator simulator,
            double duration) {
        SimpleBuff buff = new SimpleBuff(
                "Candace Prayer of the Crimson Crown",
                BuffId.CANDACE_CRIMSON_CROWN_NORMAL_DMG,
                duration,
                simulator.getCurrentTime(),
                stats -> {
                    double maxHp = currentMaxHpForA4(
                            initializedSimulator.getCurrentTime());
                    double amount = getTalentValue(
                            "Base Normal DMG Bonus", 0.20)
                            + getTalentValue(
                                    "A4 Bonus Per 1000 HP", 0.005)
                                    * maxHp / 1000.0;
                    stats.add(
                            StatType.ELEMENTAL_NORMAL_ATTACK_DMG_BONUS,
                            amount);
                });
        buff.sourcedBy(characterId);
        simulator.applyTeamBuffNoStack(buff);
    }

    private double currentMaxHpForA4(double currentTime) {
        StatsContainer stats = getEffectiveStats(currentTime);
        for (Buff buff : initializedSimulator.getApplicableBuffs(this)) {
            if (buff.getId()
                            != BuffId.CANDACE_CRIMSON_CROWN_NORMAL_DMG
                    && !buff.isExpired(currentTime)) {
                buff.apply(stats, currentTime);
            }
        }
        return stats.getTotalHp();
    }

    private void onResolvedDamage(
            Character actor,
            AttackAction action,
            double damage,
            double time,
            CombatSimulator simulator) {
        if (constellation < 6
                || simulator != initializedSimulator
                || time >= burstUntil
                || actor == null
                || actor == this
                || actor != simulator.getActiveCharacter()
                || action == null
                || action.getActionType() != ActionType.NORMAL
                || action.getElement() == Element.PHYSICAL
                || damage <= 0.0
                || time + EPSILON < nextC6Time) {
            return;
        }
        nextC6Time = time + getTalentValue("C6 Cooldown", 2.3);
        queueHit(simulator, new PendingHit(
                time + FRAME,
                HitKind.C6_WAVE,
                0,
                0,
                captureLiveStats(time).getTotalHp(),
                null));
    }

    private double burstValue(
            String key,
            double talentNine,
            double talentTwelve) {
        String suffix = constellation >= 3 ? " C3" : "";
        return getTalentValue(
                key + suffix,
                constellation >= 3 ? talentTwelve : talentNine);
    }

    private void performHit(
            CombatSimulator simulator,
            PendingHit hit,
            String displayName,
            double multiplier,
            Element hitElement,
            StatType scalingStat,
            StatType bonusStat,
            ActionType actionType,
            ICDType icdType,
            ICDTag icdTag,
            double gauge) {
        AttackAction action = new AttackAction(
                displayName,
                multiplier,
                hitElement,
                scalingStat,
                bonusStat,
                0.0,
                actionType);
        action.setICD(icdType, icdTag, gauge);
        action.setCountsAsSkillDmg(actionType == ActionType.SKILL);
        action.setCountsAsBurstDmg(actionType == ActionType.BURST);
        action.setHitlagProfile(hitlagProfile(hit));
        StatsContainer snapshot = hit.snapshot == null
                ? captureLiveStats(simulator.getCurrentTime())
                : hit.snapshot.merge(null);
        if (Double.isFinite(hit.scalingHp)) {
            snapshot.set(StatType.BASE_HP, hit.scalingHp);
            snapshot.set(StatType.HP_PERCENT, 0.0);
            snapshot.set(StatType.HP_FLAT, 0.0);
        }
        action.setStatSnapshot(snapshot);
        simulator.performActionWithoutTimeAdvance(characterId, action);
    }

    private static HitlagProfile hitlagProfile(PendingHit hit) {
        switch (hit.kind) {
            case NORMAL:
                if (hit.index < 2
                        || (hit.index == 2 && hit.variant == 1)) {
                    return NORMAL_SHORT_HITLAG;
                }
                return hit.index == 3
                        ? NORMAL_FINAL_HITLAG : HitlagProfile.none();
            case CHARGED:
                return CHARGED_HITLAG;
            case SKILL_PRESS:
            case SKILL_HOLD:
                return SKILL_HITLAG;
            case BURST_INITIAL:
            case BURST_WAVE:
            case C6_WAVE:
                return BURST_HITLAG;
            default:
                return HitlagProfile.none();
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
                    double cooldown = command.value == 1
                            && constellation < 4
                                    ? getTalentValue(
                                            "Skill Hold Cooldown", 9.0)
                                    : getTalentValue(
                                            "Skill Press Cooldown", 6.0);
                    setSkillCD(cooldown);
                    markSkillUsed(activeSimulator.getCurrentTime(),
                            activeSimulator.getApplicableBuffs(this));
                    setSkillCD(getTalentValue(
                            "Skill Press Cooldown", 6.0));
                    break;
                case BURST_ENERGY:
                    spendBurstEnergy(activeSimulator.getCurrentTime());
                    break;
                case PARTICLE:
                    activeSimulator.getEnergyDistributor().distributeParticles(
                            Element.HYDRO,
                            command.value,
                            ParticleType.PARTICLE);
                    break;
                default:
                    throw new IllegalStateException(
                            "Unknown Candace command kind");
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
        SKILL_PRESS,
        SKILL_HOLD,
        BURST_INITIAL,
        BURST_WAVE,
        C6_WAVE
    }

    private enum CommandKind {
        SKILL_COOLDOWN,
        BURST_ENERGY,
        PARTICLE
    }

    /** Immutable delayed Candace hit description. */
    private static final class PendingHit {
        private final double time;
        private final HitKind kind;
        private final int index;
        private final int variant;
        private final double scalingHp;
        private final StatsContainer snapshot;

        private PendingHit(
                double time,
                HitKind kind,
                int index,
                int variant,
                double scalingHp,
                StatsContainer snapshot) {
            this.time = time;
            this.kind = kind;
            this.index = index;
            this.variant = variant;
            this.scalingHp = scalingHp;
            this.snapshot = snapshot == null ? null : snapshot.merge(null);
        }

        private PendingHit copy() {
            return new PendingHit(
                    time, kind, index, variant, scalingHp, snapshot);
        }
    }

    /** Immutable delayed Candace command description. */
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

    /** Immutable owner-bound Candace rollback payload. */
    private static final class CandaceState implements State {
        private final Candace owner;
        private final int normalAttackStep;
        private final int switchWaveCount;
        private final double burstUntil;
        private final double nextParticleTime;
        private final double nextC6Time;
        private final List<PendingHit> pendingHits;
        private final List<PendingCommand> pendingCommands;

        private CandaceState(
                Candace owner,
                int normalAttackStep,
                int switchWaveCount,
                double burstUntil,
                double nextParticleTime,
                double nextC6Time,
                List<PendingHit> pendingHits,
                List<PendingCommand> pendingCommands) {
            this.owner = owner;
            this.normalAttackStep = normalAttackStep;
            this.switchWaveCount = switchWaveCount;
            this.burstUntil = burstUntil;
            this.nextParticleTime = nextParticleTime;
            this.nextC6Time = nextC6Time;
            this.pendingHits = copyHits(pendingHits);
            this.pendingCommands = copyCommands(pendingCommands);
        }
    }
}

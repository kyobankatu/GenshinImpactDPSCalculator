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
import simulation.action.HitlagProfile;
import simulation.action.SkillActionMode;
import simulation.event.SimpleTimerEvent;

/**
 * Mika's fixed-target physical and Soulwind support kit.
 *
 * <p>Physical Normal/Charged attacks, Press/Hold Skill, particles, Soulwind,
 * fixed-target C2 Detector, C5, and C6 follow pinned gcsim
 * {@code ef41805d}. Healing, Eagleplume, multi-target Detector generation, and
 * A4 are intentionally outside this slice.</p>
 */
public final class Mika extends Character implements
        SimulatorInitializedCharacterEffect,
        SnapshotAwareCharacterEffect,
        SwitchAwareCharacter,
        TargetDependentTeamEffect {
    private static final double FRAME = 1.0 / 60.0;
    private static final double EPSILON = 1e-9;
    private static final double PARTICLE_TRAVEL = 100.0 * FRAME;
    private static final int[][] NORMAL_HIT_FRAMES = {
        { 20 }, { 14 }, { 16 }, { 15, 24 }, { 30 }
    };
    private static final int[] NORMAL_DURATIONS = { 35, 28, 41, 48, 71 };
    private static final String[][] NORMAL_KEYS = {
        { "N1" }, { "N2" }, { "N3" },
        { "N4 Hit 1", "N4 Hit 2" }, { "N5" }
    };
    private static final double[][] NORMAL_T9 = {
        { 0.794835 }, { 0.762476 }, { 1.001341 },
        { 0.507338, 0.507338 }, { 1.302110 }
    };
    /**
     * Per-hit metadata from gcsim pinned at
     * {@code 3647a07a7cc3004bc1e79d9bb5f7444de20dceaa}.
     */
    private static final HitlagProfile NORMAL_HITLAG_SHORT_NO_DEFENSE =
            new HitlagProfile(0.02, 0.01, false, false, false);
    private static final HitlagProfile NORMAL_HITLAG_SHORT =
            new HitlagProfile(0.02, 0.01, true, false, false);
    private static final HitlagProfile NORMAL_HITLAG_DEFENSE_ONLY =
            new HitlagProfile(0.0, 0.01, true, false, false);
    private static final HitlagProfile NORMAL_HITLAG_LONG =
            new HitlagProfile(0.04, 0.01, true, false, false);
    private static final HitlagProfile CHARGED_HITLAG =
            new HitlagProfile(0.0, 0.01, true, true, false);
    private static final HitlagProfile PRESS_SKILL_HITLAG =
            new HitlagProfile(0.0, 0.01, true, false, false);

    private CombatSimulator initializedSimulator;
    private int normalAttackStep;
    private int detectorStacks;
    private double soulwindUntil = Double.NEGATIVE_INFINITY;
    private List<PendingHit> pendingHits = new ArrayList<>();
    private List<PendingCommand> pendingCommands = new ArrayList<>();

    /** Constructs repository-default C6 Mika. */
    public Mika(Weapon weapon, ArtifactSet artifacts) {
        this(weapon, artifacts, TalentDataManager.getInstance(), 6);
    }

    /** Constructs Mika at an explicit constellation. */
    public Mika(Weapon weapon, ArtifactSet artifacts, int constellation) {
        this(weapon, artifacts, TalentDataManager.getInstance(), constellation);
    }

    /** Constructs Mika with injectable talent data. */
    public Mika(
            Weapon weapon,
            ArtifactSet artifacts,
            TalentDataSource talentData,
            int constellation) {
        super(talentData);
        if (constellation < 0 || constellation > 6) {
            throw new IllegalArgumentException(
                    "Mika constellation must be between 0 and 6");
        }
        name = "Mika";
        characterId = CharacterId.MIKA;
        element = Element.CRYO;
        this.weapon = weapon;
        this.artifacts = new ArtifactSet[] { artifacts };
        this.constellation = constellation;
        baseStats.set(StatType.BASE_HP,
                getTalentValue("Base HP", 12506.0));
        baseStats.set(StatType.BASE_ATK,
                getTalentValue("Base ATK", 223.0));
        baseStats.set(StatType.BASE_DEF,
                getTalentValue("Base DEF", 713.0));
        baseStats.add(StatType.HP_PERCENT,
                getTalentValue("Ascension HP Percent", 0.24));
        setSkillCD(getTalentValue("Skill Cooldown", 15.0));
    }

    /** Binds Mika's delayed work to one simulator. */
    @Override
    public void initializeForSimulator(CombatSimulator simulator) {
        if (simulator == null) {
            throw new IllegalArgumentException("Mika simulator is required");
        }
        if (!simulator.getPartyMembers().contains(this)) {
            throw new IllegalArgumentException(
                    "Mika must belong to the simulator party");
        }
        if (initializedSimulator != null
                && initializedSimulator != simulator) {
            throw new IllegalStateException(
                    "Mika cannot be reused across simulators");
        }
        initializedSimulator = simulator;
    }

    /** Captures Normal, Soulwind, Detector, and delayed-event state. */
    @Override
    public State captureCharacterState() {
        return new MikaState(this, normalAttackStep, detectorStacks,
                soulwindUntil, pendingHits, pendingCommands);
    }

    /** Accepts state from this exact Mika instance only. */
    @Override
    public boolean acceptsCharacterState(State state) {
        return state instanceof MikaState
                && ((MikaState) state).owner == this;
    }

    /** Restores surviving delayed work once. */
    @Override
    public void restoreCharacterState(
            State state,
            CombatSimulator simulator) {
        if (!acceptsCharacterState(state)) {
            throw new IllegalArgumentException("Unexpected Mika state");
        }
        initializeForSimulator(simulator);
        MikaState restored = (MikaState) state;
        normalAttackStep = restored.normalAttackStep;
        detectorStacks = restored.detectorStacks;
        soulwindUntil = restored.soulwindUntil;
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

    /** Mika's deferred Burst costs 70 Energy. */
    @Override
    public double getEnergyCost() {
        return getTalentValue("Energy Cost", 70.0);
    }

    /** Mika has no unconditional static offensive passive. */
    @Override
    public void applyPassive(StatsContainer stats) {
    }

    /** Supports both Skill input modes. */
    @Override
    protected boolean supportsSkillActionMode(SkillActionMode mode) {
        return mode == SkillActionMode.PRESS || mode == SkillActionMode.HOLD;
    }

    /** Resets Mika's Normal string on switch-out. */
    @Override
    public void onSwitchOut(CombatSimulator simulator) {
        normalAttackStep = 0;
    }

    /** Returns whether Soulwind is active in its half-open window. */
    public boolean isSoulwindActive(double currentTime) {
        return currentTime < soulwindUntil;
    }

    /** Returns fixed-target Detector stacks during Soulwind. */
    public int getDetectorStacks(double currentTime) {
        return isSoulwindActive(currentTime) ? detectorStacks : 0;
    }

    /** Applies Detector and C6 only to the active Physical attacker. */
    @Override
    public void applyTargetDependentTeamStats(
            StatsContainer stats,
            Character attacker,
            Enemy target,
            AttackAction action,
            double currentTime) {
        if (target == null
                || initializedSimulator == null
                || attacker != initializedSimulator.getActiveCharacter()
                || action.getElement() != Element.PHYSICAL
                || !isSoulwindActive(currentTime)) {
            return;
        }
        stats.add(StatType.PHYSICAL_DMG_BONUS,
                0.10 * detectorStacks);
        if (constellation >= 6) {
            stats.add(StatType.PHYSICAL_CRIT_DMG, 0.60);
        }
    }

    /** Dispatches Mika's represented actions. */
    @Override
    public void onAction(
            CharacterActionRequest request,
            CombatSimulator simulator) {
        if (request == null) {
            throw new IllegalArgumentException("Mika action is required");
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
                starfrostSwirl(simulator,
                        request.getSkillMode() == SkillActionMode.HOLD);
                break;
            default:
                throw new IllegalArgumentException(
                        "Unsupported action for Mika: " + request.getKey());
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
                    hit));
        }
        normalAttackStep = (normalAttackStep + 1) % NORMAL_HIT_FRAMES.length;
        simulator.advanceTime(resolveNormalDuration(
                simulator, NORMAL_DURATIONS[step] * FRAME));
    }

    private void chargedAttack(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        queueHit(simulator, new PendingHit(
                castTime + 26.0 * FRAME,
                HitKind.CHARGED,
                0,
                0));
        simulator.advanceTime(66.0 * FRAME);
    }

    private void starfrostSwirl(
            CombatSimulator simulator,
            boolean hold) {
        double castTime = simulator.getCurrentTime();
        int buffFrame = hold ? 12 : 16;
        int impactFrame = hold ? 15 : 18;
        queueCommand(simulator, new PendingCommand(
                castTime + buffFrame * FRAME,
                CommandKind.SOULWIND,
                0));
        queueHit(simulator, new PendingHit(
                castTime + impactFrame * FRAME,
                HitKind.SKILL,
                0,
                hold ? 1 : 0));
        simulator.advanceTime((hold ? 46.0 : 39.0) * FRAME);
    }

    private void resolveHit(CombatSimulator simulator, PendingHit hit) {
        switch (hit.kind) {
            case NORMAL:
                performHit(simulator,
                        "Spear of Favonius N" + (hit.index + 1)
                                + " Hit " + (hit.variant + 1),
                        getTalentValue(NORMAL_KEYS[hit.index][hit.variant],
                                NORMAL_T9[hit.index][hit.variant]),
                        Element.PHYSICAL,
                        StatType.NORMAL_ATTACK_DMG_BONUS,
                        ActionType.NORMAL,
                        ICDType.Standard,
                        ICDTag.NormalAttack,
                        1.0,
                        normalHitlag(hit.index));
                break;
            case CHARGED:
                performHit(simulator,
                        "Spear of Favonius Charged Attack",
                        getTalentValue("Charged Attack", 2.071380),
                        Element.PHYSICAL,
                        StatType.CHARGED_ATTACK_DMG_BONUS,
                        ActionType.CHARGE,
                        ICDType.Standard,
                        ICDTag.ChargedAttack,
                        1.0,
                        CHARGED_HITLAG);
                break;
            case SKILL:
                resolveSkill(simulator, hit.variant == 1);
                break;
            default:
                throw new IllegalStateException("Unknown Mika hit kind");
        }
    }

    private void resolveSkill(
            CombatSimulator simulator,
            boolean hold) {
        String key = hold ? "Rimestar Flare" : "Flowfrost Arrow";
        performHit(simulator,
                key,
                skillValue(key,
                        hold ? 1.428000 : 1.142400,
                        hold ? 1.680000 : 1.344000),
                Element.CRYO,
                StatType.SKILL_DMG_BONUS,
                ActionType.SKILL,
                hold ? ICDType.Standard : ICDType.None,
                ICDTag.ElementalSkill,
                1.0,
                hold ? null : PRESS_SKILL_HITLAG);
        if (constellation >= 2) {
            detectorStacks = 1;
        }
        queueCommand(simulator, new PendingCommand(
                simulator.getCurrentTime() + PARTICLE_TRAVEL,
                CommandKind.PARTICLE,
                4));
    }

    private void applySoulwind(
            CombatSimulator simulator,
            double currentTime) {
        detectorStacks = 0;
        soulwindUntil = currentTime
                + getTalentValue("Soulwind Duration", 12.0);
        double attackSpeed = skillValue(
                "ATK Speed", 0.21, 0.24);
        SimpleBuff buff = new SimpleBuff(
                "Mika Soulwind Attack Speed",
                BuffId.MIKA_SOULWIND_ATTACK_SPEED,
                getTalentValue("Soulwind Duration", 12.0),
                currentTime,
                stats -> stats.add(StatType.ATK_SPD, attackSpeed));
        buff.sourcedBy(characterId);
        simulator.applyTeamBuffNoStack(buff);
        markSkillUsed(currentTime, simulator.getApplicableBuffs(this));
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

    private void performHit(
            CombatSimulator simulator,
            String displayName,
            double multiplier,
            Element hitElement,
            StatType bonusStat,
            ActionType actionType,
            ICDType icdType,
            ICDTag icdTag,
            double gauge) {
        performHit(
                simulator,
                displayName,
                multiplier,
                hitElement,
                bonusStat,
                actionType,
                icdType,
                icdTag,
                gauge,
                null);
    }

    private void performHit(
            CombatSimulator simulator,
            String displayName,
            double multiplier,
            Element hitElement,
            StatType bonusStat,
            ActionType actionType,
            ICDType icdType,
            ICDTag icdTag,
            double gauge,
            HitlagProfile hitlagProfile) {
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
        if (hitlagProfile != null) {
            action.setHitlagProfile(hitlagProfile);
        }
        action.setStatSnapshot(captureLiveStats(simulator.getCurrentTime()));
        simulator.performActionWithoutTimeAdvance(characterId, action);
    }

    private static HitlagProfile normalHitlag(int step) {
        if (step == 0 || step == 2) {
            return NORMAL_HITLAG_SHORT_NO_DEFENSE;
        }
        if (step == 1) {
            return NORMAL_HITLAG_SHORT;
        }
        if (step == 3) {
            return NORMAL_HITLAG_DEFENSE_ONLY;
        }
        return NORMAL_HITLAG_LONG;
    }

    private double resolveNormalDuration(
            CombatSimulator simulator,
            double duration) {
        StatsContainer stats = captureLiveStats(simulator.getCurrentTime());
        double speed = Math.min(0.60,
                stats.get(StatType.ATK_SPD)
                        + stats.get(StatType.NORMAL_ATTACK_SPD));
        return speed <= 0.0 ? duration : duration / (1.0 + speed);
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
                case SOULWIND:
                    applySoulwind(activeSimulator,
                            activeSimulator.getCurrentTime());
                    break;
                case PARTICLE:
                    activeSimulator.getEnergyDistributor().distributeParticles(
                            Element.CRYO,
                            command.value,
                            ParticleType.PARTICLE);
                    break;
                default:
                    throw new IllegalStateException(
                            "Unknown Mika command kind");
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
        SKILL
    }

    private enum CommandKind {
        SOULWIND,
        PARTICLE
    }

    private static final class PendingHit {
        private final double time;
        private final HitKind kind;
        private final int index;
        private final int variant;

        private PendingHit(
                double time,
                HitKind kind,
                int index,
                int variant) {
            this.time = time;
            this.kind = kind;
            this.index = index;
            this.variant = variant;
        }

        private PendingHit copy() {
            return new PendingHit(time, kind, index, variant);
        }
    }

    private static final class PendingCommand {
        private final double time;
        private final CommandKind kind;
        private final int value;

        private PendingCommand(
                double time,
                CommandKind kind,
                int value) {
            this.time = time;
            this.kind = kind;
            this.value = value;
        }

        private PendingCommand copy() {
            return new PendingCommand(time, kind, value);
        }
    }

    private static final class MikaState implements State {
        private final Mika owner;
        private final int normalAttackStep;
        private final int detectorStacks;
        private final double soulwindUntil;
        private final List<PendingHit> pendingHits;
        private final List<PendingCommand> pendingCommands;

        private MikaState(
                Mika owner,
                int normalAttackStep,
                int detectorStacks,
                double soulwindUntil,
                List<PendingHit> pendingHits,
                List<PendingCommand> pendingCommands) {
            this.owner = owner;
            this.normalAttackStep = normalAttackStep;
            this.detectorStacks = detectorStacks;
            this.soulwindUntil = soulwindUntil;
            this.pendingHits = copyHits(pendingHits);
            this.pendingCommands = copyCommands(pendingCommands);
        }
    }
}

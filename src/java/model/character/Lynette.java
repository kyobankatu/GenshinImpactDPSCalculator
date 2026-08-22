package model.character;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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
 * Lynette's fixed-target Bogglecat offensive kit through C6.
 *
 * <p>Physical basics, Press and deterministic minimum-Hold Skill, the 0U
 * Surging Blade, particles, Burst Anemo ticks, A1, C3-C6, and delayed timing
 * follow pinned gcsim {@code ef41805d}.</p>
 *
 * <p>Healing and HP drain, C1 pull, absorbed-element Vivid Shots and dependent
 * C2/A4, variable Hold duration, and geometry are outside this vertical
 * slice.</p>
 */
public final class Lynette extends Character implements
        SimulatorInitializedCharacterEffect,
        SnapshotAwareCharacterEffect,
        SwitchAwareCharacter {
    private static final double FRAME = 1.0 / 60.0;
    private static final double EPSILON = 1e-9;
    private static final double PARTICLE_TRAVEL = 100.0 * FRAME;
    private static final int[][] NORMAL_HIT_FRAMES = {
        { 17 }, { 13 }, { 23, 31 }, { 29 }
    };
    private static final int[] NORMAL_DURATIONS = { 30, 27, 47, 68 };
    private static final String[][] NORMAL_KEYS = {
        { "N1" }, { "N2" }, { "N3 Hit 1", "N3 Hit 2" }, { "N4" }
    };
    private static final double[][] NORMAL_T9 = {
        { 0.791501 }, { 0.691013 }, { 0.511920, 0.396706 }, { 1.160273 }
    };
    private static final int[] BURST_TICK_FRAMES = {
        136, 195, 254, 313, 372, 431, 490, 549, 608, 667, 726
    };
    /**
     * Per-hit metadata from gcsim pinned at
     * {@code 3647a07a7cc3004bc1e79d9bb5f7444de20dceaa}.
     */
    private static final HitlagProfile NORMAL_HITLAG_SHORT =
            new HitlagProfile(0.03, 0.01, true, false, false);
    private static final HitlagProfile NORMAL_HITLAG_N3_FIRST =
            new HitlagProfile(0.06, 0.01, true, false, false);
    private static final HitlagProfile SKILL_HITLAG =
            new HitlagProfile(0.0, 0.01, true, false, false);

    private CombatSimulator initializedSimulator;
    private int normalAttackStep;
    private double c6Until = Double.NEGATIVE_INFINITY;
    private double nextArkheTime = Double.NEGATIVE_INFINITY;
    private double nextParticleTime = Double.NEGATIVE_INFINITY;
    private List<PendingHit> pendingHits = new ArrayList<>();
    private List<PendingCommand> pendingCommands = new ArrayList<>();

    /** Constructs repository-default C6 Lynette. */
    public Lynette(Weapon weapon, ArtifactSet artifacts) {
        this(weapon, artifacts, TalentDataManager.getInstance(), 6);
    }

    /** Constructs Lynette at an explicit constellation. */
    public Lynette(Weapon weapon, ArtifactSet artifacts, int constellation) {
        this(weapon, artifacts, TalentDataManager.getInstance(), constellation);
    }

    /** Constructs Lynette with injectable talent data. */
    public Lynette(
            Weapon weapon,
            ArtifactSet artifacts,
            TalentDataSource talentData,
            int constellation) {
        super(talentData);
        if (constellation < 0 || constellation > 6) {
            throw new IllegalArgumentException(
                    "Lynette constellation must be between 0 and 6");
        }
        name = "Lynette";
        characterId = CharacterId.LYNETTE;
        element = Element.ANEMO;
        this.weapon = weapon;
        this.artifacts = new ArtifactSet[] { artifacts };
        this.constellation = constellation;
        baseStats.set(StatType.BASE_HP,
                getTalentValue("Base HP", 12397.0));
        baseStats.set(StatType.BASE_ATK,
                getTalentValue("Base ATK", 232.0));
        baseStats.set(StatType.BASE_DEF,
                getTalentValue("Base DEF", 712.0));
        baseStats.add(StatType.ANEMO_DMG_BONUS,
                getTalentValue("Ascension Anemo DMG", 0.24));
        setSkillCD(getTalentValue("Skill Cooldown", 12.0));
        setBurstCD(getTalentValue("Burst Cooldown", 18.0));
        if (constellation >= 4) {
            setSkillMaxCharges(2);
        }
    }

    /** Binds delayed state to one simulator. */
    @Override
    public void initializeForSimulator(CombatSimulator simulator) {
        if (simulator == null) {
            throw new IllegalArgumentException("Lynette simulator is required");
        }
        if (initializedSimulator != null
                && initializedSimulator != simulator) {
            throw new IllegalStateException(
                    "Lynette cannot be reused across simulators");
        }
        if (initializedSimulator == simulator) {
            return;
        }
        if (!simulator.getPartyMembers().contains(this)) {
            throw new IllegalArgumentException(
                    "Lynette must belong to the target simulator party");
        }
        initializedSimulator = simulator;
    }

    /** Captures infusion, gates, counters, and delayed work. */
    @Override
    public State captureCharacterState() {
        return new LynetteState(
                this,
                normalAttackStep,
                c6Until,
                nextArkheTime,
                nextParticleTime,
                pendingHits,
                pendingCommands);
    }

    /** Accepts only state captured from this exact Lynette instance. */
    @Override
    public boolean acceptsCharacterState(State state) {
        return state instanceof LynetteState
                && ((LynetteState) state).owner == this;
    }

    /** Restores Lynette state and reconstructs surviving events once. */
    @Override
    public void restoreCharacterState(
            State state,
            CombatSimulator simulator) {
        if (!acceptsCharacterState(state)) {
            throw new IllegalArgumentException("Unexpected Lynette state");
        }
        initializeForSimulator(simulator);
        LynetteState restored = (LynetteState) state;
        normalAttackStep = restored.normalAttackStep;
        c6Until = restored.c6Until;
        nextArkheTime = restored.nextArkheTime;
        nextParticleTime = restored.nextParticleTime;
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

    /** Returns Lynette's 70-Energy Burst cost. */
    @Override
    public double getEnergyCost() {
        return getTalentValue("Energy Cost", 70.0);
    }

    /** Lynette has no unconditional static passive stat. */
    @Override
    public void applyPassive(StatsContainer stats) {
    }

    /** Supports Press and deterministic minimum-Hold Skill actions. */
    @Override
    protected boolean supportsSkillActionMode(SkillActionMode mode) {
        return mode == SkillActionMode.PRESS || mode == SkillActionMode.HOLD;
    }

    /** Resets only Lynette's Normal string on switch-out. */
    @Override
    public void onSwitchOut(CombatSimulator simulator) {
        normalAttackStep = 0;
    }

    /** Returns whether C6's Anemo infusion window is active. */
    public boolean isC6InfusionActive(double currentTime) {
        return currentTime < c6Until;
    }

    /** Dispatches Lynette's represented typed action set. */
    @Override
    public void onAction(
            CharacterActionRequest request,
            CombatSimulator simulator) {
        if (request == null) {
            throw new IllegalArgumentException("Lynette action is required");
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
                enigmaticFeint(simulator,
                        request.getSkillMode() == SkillActionMode.HOLD);
                break;
            case BURST:
                astonishingShift(simulator);
                break;
            default:
                throw new IllegalArgumentException(
                        "Unsupported action for Lynette: " + request.getKey());
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
                    null));
        }
        normalAttackStep = (normalAttackStep + 1) % NORMAL_HIT_FRAMES.length;
        simulator.advanceTime(NORMAL_DURATIONS[step] * FRAME);
    }

    private void chargedAttack(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        queueHit(simulator, new PendingHit(
                castTime + 12.0 * FRAME,
                HitKind.CHARGED,
                0,
                0,
                null));
        queueHit(simulator, new PendingHit(
                castTime + 13.0 * FRAME,
                HitKind.CHARGED,
                0,
                1,
                null));
        simulator.advanceTime(51.0 * FRAME);
    }

    private void highPlunge(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        queueHit(simulator, new PendingHit(
                castTime + 47.0 * FRAME,
                HitKind.PLUNGE,
                0,
                0,
                null));
        simulator.advanceTime(75.0 * FRAME);
    }

    private void enigmaticFeint(
            CombatSimulator simulator,
            boolean hold) {
        double castTime = simulator.getCurrentTime();
        int mainFrame = hold ? 51 : 28;
        int cooldownFrame = hold ? 49 : 26;
        int c6Frame = hold ? 40 : 17;
        queueHit(simulator, new PendingHit(
                castTime + mainFrame * FRAME,
                HitKind.SKILL,
                0,
                hold ? 1 : 0,
                null));
        queueCommand(simulator, new PendingCommand(
                castTime + cooldownFrame * FRAME,
                CommandKind.SKILL_COOLDOWN,
                0));
        if (constellation >= 6) {
            queueCommand(simulator, new PendingCommand(
                    castTime + c6Frame * FRAME,
                    CommandKind.C6,
                    0));
        }
        simulator.advanceTime((hold ? 76.0 : 58.0) * FRAME);
    }

    private void astonishingShift(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        markBurstCooldownUsed(castTime, simulator.getApplicableBuffs(this));
        applyA1(simulator, castTime);
        queueCommand(simulator, new PendingCommand(
                castTime + 6.0 * FRAME,
                CommandKind.BURST_ENERGY,
                0));
        queueHit(simulator, new PendingHit(
                castTime + 18.0 * FRAME,
                HitKind.BURST_INITIAL,
                0,
                0,
                null));
        for (int tick = 0; tick < BURST_TICK_FRAMES.length; tick++) {
            queueHit(simulator, new PendingHit(
                    castTime + BURST_TICK_FRAMES[tick] * FRAME,
                    HitKind.BURST_TICK,
                    tick,
                    0,
                    null));
        }
        simulator.advanceTime(56.0 * FRAME);
    }

    private void resolveHit(CombatSimulator simulator, PendingHit hit) {
        switch (hit.kind) {
            case NORMAL:
                resolveNormal(simulator, hit);
                break;
            case CHARGED:
                performHit(simulator, hit,
                        "Rapid Rites Charged Hit " + (hit.variant + 1),
                        getTalentValue(
                                hit.variant == 0
                                        ? "Charged Hit 1" : "Charged Hit 2",
                                hit.variant == 0 ? 0.812120 : 1.128120),
                        infusedElement(hit.time),
                        StatType.CHARGED_ATTACK_DMG_BONUS,
                        ActionType.CHARGE,
                        ICDType.Standard,
                        ICDTag.ChargedAttack,
                        1.0);
                break;
            case PLUNGE:
                performHit(simulator, hit, "Rapid Rites High Plunge",
                        getTalentValue("High Plunge", 2.933586),
                        infusedElement(hit.time),
                        StatType.PLUNGING_ATTACK_DMG_BONUS,
                        ActionType.PLUNGE,
                        ICDType.None,
                        ICDTag.PlungeAttack,
                        0.0);
                break;
            case SKILL:
                resolveSkill(simulator, hit);
                break;
            case ARKHE:
                performHit(simulator, hit, "Surging Blade (Lynette)",
                        skillValue("Surging Blade", 0.530400, 0.624000),
                        Element.ANEMO,
                        StatType.SKILL_DMG_BONUS,
                        ActionType.SKILL,
                        ICDType.None,
                        ICDTag.ElementalSkill,
                        0.0);
                break;
            case BURST_INITIAL:
                performHit(simulator, hit,
                        "Magic Trick: Astonishing Shift",
                        burstValue(
                                "Magic Trick Initial", 1.414400, 1.664000),
                        Element.ANEMO,
                        StatType.BURST_DMG_BONUS,
                        ActionType.BURST,
                        ICDType.Standard,
                        ICDTag.ElementalBurst,
                        1.0);
                break;
            case BURST_TICK:
                performHit(simulator, hit,
                        "Bogglecat Box Tick " + (hit.index + 1),
                        burstValue("Bogglecat Box", 0.870400, 1.024000),
                        Element.ANEMO,
                        StatType.BURST_DMG_BONUS,
                        ActionType.BURST,
                        ICDType.Standard,
                        ICDTag.ElementalBurst,
                        1.0);
                break;
            default:
                throw new IllegalStateException("Unknown Lynette hit kind");
        }
    }

    private void resolveNormal(CombatSimulator simulator, PendingHit hit) {
        performHit(simulator, hit,
                "Rapid Rites " + NORMAL_KEYS[hit.index][hit.variant],
                getTalentValue(
                        NORMAL_KEYS[hit.index][hit.variant],
                        NORMAL_T9[hit.index][hit.variant]),
                infusedElement(hit.time),
                StatType.NORMAL_ATTACK_DMG_BONUS,
                ActionType.NORMAL,
                ICDType.Standard,
                ICDTag.NormalAttack,
                1.0);
    }

    private void resolveSkill(CombatSimulator simulator, PendingHit hit) {
        performHit(simulator, hit, "Enigmatic Feint",
                skillValue("Enigma Thrust", 4.556000, 5.360000),
                Element.ANEMO,
                StatType.SKILL_DMG_BONUS,
                ActionType.SKILL,
                ICDType.None,
                ICDTag.ElementalSkill,
                1.0);
        double currentTime = simulator.getCurrentTime();
        if (currentTime + EPSILON >= nextParticleTime) {
            nextParticleTime = currentTime + 0.6;
            queueCommand(simulator, new PendingCommand(
                    currentTime + PARTICLE_TRAVEL,
                    CommandKind.PARTICLE,
                    4));
        }
        if (currentTime + EPSILON >= nextArkheTime) {
            nextArkheTime = currentTime
                    + getTalentValue("Arkhe Cooldown", 10.0);
            queueHit(simulator, new PendingHit(
                    currentTime + (hit.variant == 1 ? 28.0 : 30.0) * FRAME,
                    HitKind.ARKHE,
                    0,
                    0,
                    null));
        }
    }

    private void applyA1(CombatSimulator simulator, double currentTime) {
        Set<Element> elements = new HashSet<>();
        for (Character member : simulator.getPartyMembers()) {
            elements.add(member.getElement());
        }
        double amount = getTalentValue("A1 Base ATK", 0.08)
                + Math.max(0, elements.size() - 1)
                        * getTalentValue("A1 ATK Per Extra Element", 0.04);
        SimpleBuff buff = new SimpleBuff(
                "Lynette A1 Party ATK",
                BuffId.LYNETTE_A1_PARTY_ATK,
                getTalentValue("A1 Duration", 10.0),
                currentTime,
                stats -> stats.add(StatType.ATK_PERCENT, amount));
        buff.sourcedBy(characterId);
        simulator.applyTeamBuffNoStack(buff);
    }

    private void applyC6(double currentTime) {
        c6Until = currentTime + getTalentValue("C6 Duration", 6.4);
        removeBuff(BuffId.LYNETTE_C6_ANEMO_DMG);
        addBuff(new SimpleBuff(
                "Lynette C6 Anemo DMG",
                BuffId.LYNETTE_C6_ANEMO_DMG,
                getTalentValue("C6 Duration", 6.4),
                currentTime,
                stats -> stats.add(
                        StatType.ANEMO_DMG_BONUS,
                        getTalentValue("C6 Anemo DMG", 0.20)))
                .sourcedBy(characterId));
    }

    private Element infusedElement(double currentTime) {
        return currentTime < c6Until ? Element.ANEMO : Element.PHYSICAL;
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
        HitlagProfile hitlagProfile = hitlagProfile(hit);
        if (hitlagProfile != null) {
            action.setHitlagProfile(hitlagProfile);
        }
        action.setStatSnapshot(hit.snapshot == null
                ? captureLiveStats(simulator.getCurrentTime())
                : hit.snapshot);
        simulator.performActionWithoutTimeAdvance(characterId, action);
    }

    private static HitlagProfile hitlagProfile(PendingHit hit) {
        if (hit.kind == HitKind.SKILL || hit.kind == HitKind.ARKHE) {
            return SKILL_HITLAG;
        }
        if (hit.kind != HitKind.NORMAL) {
            return null;
        }
        if (hit.index <= 1) {
            return NORMAL_HITLAG_SHORT;
        }
        if (hit.index == 2 && hit.variant == 0) {
            return NORMAL_HITLAG_N3_FIRST;
        }
        return null;
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
                    markSkillUsed(activeSimulator.getCurrentTime(),
                            activeSimulator.getApplicableBuffs(this));
                    break;
                case BURST_ENERGY:
                    spendBurstEnergy(activeSimulator.getCurrentTime());
                    break;
                case PARTICLE:
                    activeSimulator.getEnergyDistributor().distributeParticles(
                            Element.ANEMO,
                            command.value,
                            ParticleType.PARTICLE);
                    break;
                case C6:
                    applyC6(activeSimulator.getCurrentTime());
                    break;
                default:
                    throw new IllegalStateException(
                            "Unknown Lynette command kind");
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
        SKILL,
        ARKHE,
        BURST_INITIAL,
        BURST_TICK
    }

    private enum CommandKind {
        SKILL_COOLDOWN,
        BURST_ENERGY,
        PARTICLE,
        C6
    }

    /** Immutable delayed Lynette hit description. */
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

    /** Immutable delayed Lynette command description. */
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

    /** Immutable owner-bound Lynette rollback payload. */
    private static final class LynetteState implements State {
        private final Lynette owner;
        private final int normalAttackStep;
        private final double c6Until;
        private final double nextArkheTime;
        private final double nextParticleTime;
        private final List<PendingHit> pendingHits;
        private final List<PendingCommand> pendingCommands;

        private LynetteState(
                Lynette owner,
                int normalAttackStep,
                double c6Until,
                double nextArkheTime,
                double nextParticleTime,
                List<PendingHit> pendingHits,
                List<PendingCommand> pendingCommands) {
            this.owner = owner;
            this.normalAttackStep = normalAttackStep;
            this.c6Until = c6Until;
            this.nextArkheTime = nextArkheTime;
            this.nextParticleTime = nextParticleTime;
            this.pendingHits = copyHits(pendingHits);
            this.pendingCommands = copyCommands(pendingCommands);
        }
    }
}

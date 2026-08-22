package model.character;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import mechanics.buff.Buff;
import mechanics.data.TalentDataManager;
import mechanics.data.TalentDataSource;
import mechanics.energy.ParticleType;
import mechanics.reaction.ReactionResult;
import model.entity.ArtifactSet;
import model.entity.Character;
import model.entity.ReactionAwareCharacter;
import model.entity.SimulatorInitializedCharacterEffect;
import model.entity.SnapshotAwareCharacterEffect;
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
 * Sayu's stationary Press Skill and offensive Daruma slice through C6.
 *
 * <p>Lv. 90 data, frame timings, application groups, particles, and
 * constellation values follow pinned gcsim {@code ef41805d} and KQM TCL
 * {@code 80ba6241}. Both Press hits snapshot independently at cast time. The
 * Burst initial hit and all seven Daruma attacks share one cast-time snapshot;
 * C6 adds {@code snapshot ATK * min(snapshot EM * 0.002, 4)} as flat base
 * damage to Daruma attacks.</p>
 *
 * <p>Hold movement and absorption, Charged attacks, healing, player HP,
 * geometry, complete hitlag coverage, and multi-target selection are intentionally excluded.</p>
 */
public final class Sayu extends Character implements
        ReactionAwareCharacter,
        SimulatorInitializedCharacterEffect,
        SnapshotAwareCharacterEffect {
    private static final double FRAME = 1.0 / 60.0;
    private static final double EPSILON = 1e-9;
    private static final double PARTICLE_TRAVEL = 100.0 * FRAME;
    /** Hitlag data pinned to gcsim {@code 3647a07a7cc3004bc1e79d9bb5f7444de20dceaa}. */
    private static final HitlagProfile[] NORMAL_HITLAG = {
        new HitlagProfile(0.10, 0.01, true, false, false),
        new HitlagProfile(0.10, 0.01, true, false, false),
        HitlagProfile.none(),
        new HitlagProfile(0.08, 0.01, true, false, false)
    };
    private static final HitlagProfile N3_SECOND_HITLAG =
            new HitlagProfile(0.08, 0.01, true, false, false);
    private static final HitlagProfile TAP_KICK_HITLAG =
            new HitlagProfile(0.02, 0.05, false, false, false);
    private static final HitlagProfile BURST_INITIAL_HITLAG =
            new HitlagProfile(0.02, 0.05, false, false, false);
    private static final int[] NORMAL_HITMARKS = { 23, 29, 26, 35 };
    private static final int[] NORMAL_DURATIONS = { 36, 48, 52, 71 };
    private static final int BURST_TICK_COUNT = 7;
    // KQM lists 114f; this offensive slice follows gcsim's executable
    // attack-event schedule at 145f because the two sources remain unresolved.
    private static final int BURST_FIRST_TICK_FRAME = 145;
    private static final int BURST_TICK_INTERVAL_FRAMES = 90;

    private CombatSimulator initializedSimulator;
    private int normalAttackStep;
    private long skillGeneration;
    private long burstGeneration;
    private double nextC4EnergyTime = Double.NEGATIVE_INFINITY;
    private List<PendingHit> pendingHits = new ArrayList<>();
    private List<PendingCommand> pendingCommands = new ArrayList<>();

    /** Constructs repository-default C6 Sayu. */
    public Sayu(Weapon weapon, ArtifactSet artifacts) {
        this(weapon, artifacts, TalentDataManager.getInstance(), 6);
    }

    /** Constructs Sayu at an explicit constellation. */
    public Sayu(Weapon weapon, ArtifactSet artifacts, int constellation) {
        this(weapon, artifacts, TalentDataManager.getInstance(), constellation);
    }

    /** Constructs Sayu with injectable talent data and constellation state. */
    public Sayu(
            Weapon weapon,
            ArtifactSet artifacts,
            TalentDataSource talentData,
            int constellation) {
        super(talentData);
        if (constellation < 0 || constellation > 6) {
            throw new IllegalArgumentException(
                    "Sayu constellation must be between 0 and 6");
        }
        name = "Sayu";
        characterId = CharacterId.SAYU;
        element = Element.ANEMO;
        this.weapon = weapon;
        this.artifacts = new ArtifactSet[] { artifacts };
        this.constellation = constellation;
        baseStats.set(StatType.BASE_HP,
                getTalentValue("Base HP", 11854.0));
        baseStats.set(StatType.BASE_ATK,
                getTalentValue("Base ATK", 244.0));
        baseStats.set(StatType.BASE_DEF,
                getTalentValue("Base DEF", 745.0));
        baseStats.add(StatType.ELEMENTAL_MASTERY,
                getTalentValue("Ascension Elemental Mastery", 96.0));
        setSkillCD(6.0);
        setBurstCD(20.0);
    }

    /** Binds Sayu's C4 reaction listener and owned events to one simulator. */
    @Override
    public void initializeForSimulator(CombatSimulator simulator) {
        if (simulator == null) {
            throw new IllegalArgumentException("Sayu simulator is required");
        }
        if (initializedSimulator != null
                && initializedSimulator != simulator) {
            throw new IllegalStateException(
                    "Sayu cannot be reused across CombatSimulator instances");
        }
        if (initializedSimulator == simulator) {
            return;
        }
        initializedSimulator = simulator;
        simulator.addReactionListener(this);
    }

    /** Captures Sayu's chain, C4 gate, generations, and future events. */
    @Override
    public State captureCharacterState() {
        return new SayuState(
                this,
                normalAttackStep,
                skillGeneration,
                burstGeneration,
                nextC4EnergyTime,
                pendingHits,
                pendingCommands);
    }

    /** Accepts only state captured from this exact Sayu instance. */
    @Override
    public boolean acceptsCharacterState(State state) {
        return state instanceof SayuState
                && ((SayuState) state).owner == this;
    }

    /** Restores surviving Sayu-owned events exactly once. */
    @Override
    public void restoreCharacterState(
            State state,
            CombatSimulator simulator) {
        if (!acceptsCharacterState(state)) {
            throw new IllegalArgumentException(
                    "Unexpected Sayu character state");
        }
        initializeForSimulator(simulator);
        SayuState restored = (SayuState) state;
        normalAttackStep = restored.normalAttackStep;
        skillGeneration = restored.skillGeneration;
        burstGeneration = restored.burstGeneration;
        nextC4EnergyTime = restored.nextC4EnergyTime;
        pendingHits = copyHits(restored.pendingHits);
        pendingCommands = copyCommands(restored.pendingCommands);
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

    /** Returns Sayu's 80-Energy Burst cost. */
    @Override
    public double getEnergyCost() {
        return getTalentValue("Energy Cost", 80.0);
    }

    /** Sayu's only represented ascension stat is loaded structurally. */
    @Override
    public void applyPassive(StatsContainer stats) {
        // Healing and exploration passives are outside this offensive slice.
    }

    /** Restores C4 Energy for an active Sayu-triggered Swirl every two seconds. */
    @Override
    public void onReaction(
            ReactionResult result,
            Character source,
            double time,
            CombatSimulator simulator) {
        if (simulator != initializedSimulator
                || constellation < 4
                || result == null
                || result.getKind() != ReactionResult.Kind.SWIRL
                || source != this
                || simulator.getActiveCharacter() != this
                || time + EPSILON < nextC4EnergyTime) {
            return;
        }
        receiveFlatEnergy(getTalentValue("C4 Energy", 1.2));
        nextC4EnergyTime = time + 2.0;
    }

    /** Dispatches Normal, High Plunge, Press Skill, and Burst requests. */
    @Override
    public void onAction(
            CharacterActionRequest request,
            CombatSimulator simulator) {
        if (request == null) {
            throw new IllegalArgumentException("Sayu action is required");
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
                if (request.getSkillMode() != SkillActionMode.PRESS) {
                    throw new IllegalArgumentException(
                            "Sayu Hold Skill is outside this slice");
                }
                fuuinDashPress(simulator);
                break;
            case BURST:
                mujinaFlurry(simulator);
                break;
            default:
                throw new IllegalArgumentException(
                        "Unsupported action for Sayu: " + request.getKey());
        }
    }

    private void normalAttack(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        int step = normalAttackStep;
        StatsContainer snapshot = captureActionStats(simulator, castTime);
        if (step == 2) {
            queueHit(simulator, new PendingHit(
                    castTime + 14.0 * FRAME,
                    HitKind.NORMAL,
                    step,
                    0,
                    0L,
                    snapshot));
        }
        queueHit(simulator, new PendingHit(
                castTime + NORMAL_HITMARKS[step] * FRAME,
                HitKind.NORMAL,
                step,
                step == 2 ? 1 : 0,
                0L,
                snapshot));
        normalAttackStep = (normalAttackStep + 1) % 4;
        simulator.advanceTime(NORMAL_DURATIONS[step] * FRAME);
    }

    private void highPlunge(CombatSimulator simulator) {
        AttackAction plunge = attack(
                "Shuumatsuban Ninja Blade High Plunge",
                getTalentValue("High Plunge", 3.422517),
                Element.PHYSICAL,
                StatType.PLUNGING_ATTACK_DMG_BONUS,
                ActionType.PLUNGE,
                ICDType.None,
                ICDTag.None,
                0.0,
                true);
        plunge.setAnimationDuration(1.0);
        simulator.performAction(characterId, plunge);
    }

    private void fuuinDashPress(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        long generation = ++skillGeneration;
        StatsContainer wheelSnapshot = captureActionStats(
                simulator, castTime);
        StatsContainer kickSnapshot = captureActionStats(
                simulator, castTime);
        queueHit(simulator, new PendingHit(
                castTime + 7.0 * FRAME,
                HitKind.SKILL_WINDWHEEL,
                0,
                generation,
                wheelSnapshot));
        queueCommand(simulator, new PendingCommand(
                castTime + 14.0 * FRAME,
                CommandKind.SKILL_COOLDOWN,
                generation,
                0.0));
        queueHit(simulator, new PendingHit(
                castTime + 25.0 * FRAME,
                HitKind.SKILL_KICK,
                0,
                generation,
                kickSnapshot));
        simulator.advanceTime(44.0 * FRAME);
    }

    private void mujinaFlurry(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        long generation = ++burstGeneration;
        StatsContainer snapshot = captureActionStats(simulator, castTime);
        markBurstCooldownUsed(
                castTime, simulator.getApplicableBuffs(this));
        queueCommand(simulator, new PendingCommand(
                castTime + 7.0 * FRAME,
                CommandKind.BURST_ENERGY,
                generation,
                0.0));
        queueHit(simulator, new PendingHit(
                castTime + 12.0 * FRAME,
                HitKind.BURST_INITIAL,
                0,
                generation,
                snapshot));
        for (int tick = 0; tick < BURST_TICK_COUNT; tick++) {
            queueHit(simulator, new PendingHit(
                    castTime + (BURST_FIRST_TICK_FRAME
                            + tick * BURST_TICK_INTERVAL_FRAMES) * FRAME,
                    HitKind.BURST_DARUMA,
                    tick,
                    generation,
                    snapshot));
        }
        simulator.advanceTime(65.0 * FRAME);
    }

    private void resolveHit(CombatSimulator simulator, PendingHit hit) {
        if (isStale(hit)) {
            return;
        }
        switch (hit.kind) {
            case NORMAL:
                resolveNormal(simulator, hit);
                break;
            case SKILL_WINDWHEEL:
                resolveWindwheel(simulator, hit);
                break;
            case SKILL_KICK:
                resolveKick(simulator, hit);
                break;
            case BURST_INITIAL:
                resolveBurstInitial(simulator, hit);
                break;
            case BURST_DARUMA:
                resolveDaruma(simulator, hit);
                break;
            default:
                throw new IllegalStateException("Unknown Sayu hit kind");
        }
    }

    private void resolveNormal(CombatSimulator simulator, PendingHit hit) {
        int step = hit.index;
        AttackAction normal = attack(
                "Shuumatsuban Ninja Blade N" + (step + 1),
                getTalentValue("N" + (step + 1),
                        new double[] {
                            1.3272, 1.3114, 0.7979, 1.80278
                        }[step]),
                Element.PHYSICAL,
                StatType.NORMAL_ATTACK_DMG_BONUS,
                ActionType.NORMAL,
                ICDType.None,
                ICDTag.None,
                0.0,
                true);
        normal.setStatSnapshot(hit.snapshot);
        normal.setHitlagProfile(step == 2 && hit.variant == 1
                ? N3_SECOND_HITLAG : NORMAL_HITLAG[step]);
        simulator.performActionWithoutTimeAdvance(characterId, normal);
    }

    private void resolveWindwheel(
            CombatSimulator simulator,
            PendingHit hit) {
        AttackAction wheel = attack(
                "Fuufuu Windwheel Press",
                getTalentValue(
                        constellation >= 5
                                ? "Windwheel C5" : "Windwheel",
                        constellation >= 5 ? 0.720 : 0.612),
                Element.ANEMO,
                StatType.SKILL_DMG_BONUS,
                ActionType.SKILL,
                ICDType.Standard,
                ICDTag.ElementalSkill,
                1.0,
                false);
        wheel.setStatSnapshot(hit.snapshot);
        simulator.performActionWithoutTimeAdvance(characterId, wheel);
    }

    private void resolveKick(CombatSimulator simulator, PendingHit hit) {
        AttackAction kick = attack(
                "Fuufuu Whirlwind Kick Press",
                getTalentValue(
                        constellation >= 5
                                ? "Press Kick C5" : "Press Kick",
                        constellation >= 5 ? 3.168 : 2.6928),
                Element.ANEMO,
                StatType.SKILL_DMG_BONUS,
                ActionType.SKILL,
                ICDType.None,
                ICDTag.None,
                1.0,
                false);
        if (constellation >= 2) {
            kick.addBonusStat(
                    StatType.SKILL_DMG_BONUS,
                    getTalentValue("C2 Press Kick DMG Bonus", 0.033));
        }
        kick.setStatSnapshot(hit.snapshot);
        kick.setHitlagProfile(TAP_KICK_HITLAG);
        simulator.performActionWithoutTimeAdvance(characterId, kick);
        if (simulator.getEnemy() != null) {
            queueCommand(simulator, new PendingCommand(
                    simulator.getCurrentTime() + PARTICLE_TRAVEL,
                    CommandKind.PARTICLE,
                    hit.generation,
                    2.0));
        }
    }

    private void resolveBurstInitial(
            CombatSimulator simulator,
            PendingHit hit) {
        AttackAction initial = attack(
                "Yoohoo Art: Mujina Flurry",
                getTalentValue(
                        constellation >= 3 ? "Initial C3" : "Initial",
                        constellation >= 3 ? 2.336 : 1.9856),
                Element.ANEMO,
                StatType.BURST_DMG_BONUS,
                ActionType.BURST,
                ICDType.None,
                ICDTag.None,
                1.0,
                false);
        initial.setStatSnapshot(hit.snapshot);
        initial.setHitlagProfile(BURST_INITIAL_HITLAG);
        simulator.performActionWithoutTimeAdvance(characterId, initial);
    }

    private void resolveDaruma(
            CombatSimulator simulator,
            PendingHit hit) {
        StatsContainer snapshot = hit.snapshot.merge(null);
        if (constellation >= 6) {
            double ratio = Math.min(
                    snapshot.get(StatType.ELEMENTAL_MASTERY)
                            * getTalentValue("C6 EM Damage Ratio", 0.002),
                    getTalentValue("C6 ATK Cap", 4.0));
            snapshot.add(
                    StatType.FLAT_DMG_BONUS,
                    snapshot.getTotalAtk() * ratio);
        }
        AttackAction daruma = attack(
                "Muji-Muji Daruma " + (hit.index + 1),
                getTalentValue(
                        constellation >= 3 ? "Daruma C3" : "Daruma",
                        constellation >= 3 ? 1.04 : 0.884),
                Element.ANEMO,
                StatType.BURST_DMG_BONUS,
                ActionType.BURST,
                ICDType.Standard,
                ICDTag.ElementalBurst,
                1.0,
                false);
        daruma.setStatSnapshot(snapshot);
        simulator.performActionWithoutTimeAdvance(characterId, daruma);
    }

    private boolean isStale(PendingHit hit) {
        switch (hit.kind) {
            case SKILL_WINDWHEEL:
            case SKILL_KICK:
                return hit.generation != skillGeneration;
            case BURST_INITIAL:
            case BURST_DARUMA:
                return hit.generation != burstGeneration;
            default:
                return false;
        }
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

    private static AttackAction attack(
            String displayName,
            double multiplier,
            Element element,
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
                StatType.BASE_ATK,
                bonusStat,
                0.0,
                actionType);
        action.setICD(icdType, icdTag, gauge);
        action.setShatterTrigger(blunt);
        action.setCountsAsSkillDmg(actionType == ActionType.SKILL);
        action.setCountsAsBurstDmg(actionType == ActionType.BURST);
        return action;
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
                                Element.ANEMO,
                                command.value,
                                ParticleType.PARTICLE);
                    }
                    break;
                case BURST_ENERGY:
                    if (command.generation == burstGeneration) {
                        spendBurstEnergy(activeSim.getCurrentTime());
                    }
                    break;
                default:
                    throw new IllegalStateException(
                            "Unknown Sayu command kind");
            }
        });
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

    private enum HitKind {
        NORMAL,
        SKILL_WINDWHEEL,
        SKILL_KICK,
        BURST_INITIAL,
        BURST_DARUMA
    }

    private enum CommandKind {
        SKILL_COOLDOWN,
        PARTICLE,
        BURST_ENERGY
    }

    /** Immutable delayed Sayu hit description. */
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
                long generation,
                StatsContainer snapshot) {
            this(time, kind, index, 0, generation, snapshot);
        }

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

    /** Immutable delayed Sayu command description. */
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

    /** Immutable Sayu-owned simulator snapshot payload. */
    private static final class SayuState implements State {
        private final Sayu owner;
        private final int normalAttackStep;
        private final long skillGeneration;
        private final long burstGeneration;
        private final double nextC4EnergyTime;
        private final List<PendingHit> pendingHits;
        private final List<PendingCommand> pendingCommands;

        private SayuState(
                Sayu owner,
                int normalAttackStep,
                long skillGeneration,
                long burstGeneration,
                double nextC4EnergyTime,
                List<PendingHit> pendingHits,
                List<PendingCommand> pendingCommands) {
            this.owner = owner;
            this.normalAttackStep = normalAttackStep;
            this.skillGeneration = skillGeneration;
            this.burstGeneration = burstGeneration;
            this.nextC4EnergyTime = nextC4EnergyTime;
            this.pendingHits = copyHits(pendingHits);
            this.pendingCommands = copyCommands(pendingCommands);
        }
    }
}

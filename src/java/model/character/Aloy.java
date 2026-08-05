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
import simulation.event.SimpleTimerEvent;

/**
 * Aloy's stationary fixed-target Coil and Rushing Ice kit.
 *
 * <p>Values and timings follow maintained KQM evidence and pinned gcsim
 * {@code ef41805d}. Frozen Wilds always lands its Freeze Bomb and exactly two
 * Chillwater Bomblets against the represented fixed target.</p>
 *
 * <p>Projectile geometry, variable Bomblet contact, weak points, manual
 * aiming, enemy ATK reduction, movement, and hitlag extension are outside this
 * slice.</p>
 */
public final class Aloy extends Character implements
        SimulatorInitializedCharacterEffect,
        SnapshotAwareCharacterEffect,
        SwitchAwareCharacter {
    private static final double FRAME = 1.0 / 60.0;
    private static final double EPSILON = 1e-9;
    private static final double PROJECTILE_TRAVEL = 10.0 * FRAME;
    private static final double PARTICLE_TRAVEL = 100.0 * FRAME;
    private static final int[][] NORMAL_RELEASE_FRAMES = {
        { 11, 24 }, { 16 }, { 23 }, { 30 }
    };
    private static final int[] NORMAL_DURATIONS = { 31, 28, 38, 61 };
    private static final String[][] NORMAL_KEYS = {
        { "N1-1", "N1-2" }, { "N2" }, { "N3" }, { "N4" }
    };
    private static final double[][] NORMAL_MULTIPLIERS = {
        { 0.3552, 0.3996 }, { 0.7252 }, { 0.888 }, { 1.10408 }
    };

    private CombatSimulator initializedSimulator;
    private int normalAttackStep;
    private int coilCount;
    private long skillGeneration;
    private long burstGeneration;
    private long fieldExitGeneration;
    private double coilIcdExpirationTime = Double.NEGATIVE_INFINITY;
    private double rushingIceStartTime = Double.NEGATIVE_INFINITY;
    private double rushingIceExpirationTime = Double.NEGATIVE_INFINITY;
    private List<PendingHit> pendingHits = new ArrayList<>();
    private List<PendingCommand> pendingCommands = new ArrayList<>();

    /** Constructs Aloy at her only supported constellation level, C0. */
    public Aloy(Weapon weapon, ArtifactSet artifacts) {
        this(weapon, artifacts, TalentDataManager.getInstance(), 0);
    }

    /** Constructs Aloy with explicit data and constellation validation. */
    public Aloy(
            Weapon weapon,
            ArtifactSet artifacts,
            TalentDataSource talentData,
            int constellation) {
        super(talentData);
        if (constellation != 0) {
            throw new IllegalArgumentException("Aloy supports C0 only");
        }
        name = "Aloy";
        characterId = CharacterId.ALOY;
        element = Element.CRYO;
        this.weapon = weapon;
        this.artifacts = new ArtifactSet[] { artifacts };
        this.constellation = constellation;
        baseStats.set(StatType.BASE_HP,
                getTalentValue("Base HP", 10899.0));
        baseStats.set(StatType.BASE_ATK,
                getTalentValue("Base ATK", 234.0));
        baseStats.set(StatType.BASE_DEF,
                getTalentValue("Base DEF", 676.0));
        baseStats.add(StatType.CRYO_DMG_BONUS,
                getTalentValue("Ascension Cryo DMG Bonus", 0.288));
        setSkillCD(20.0);
        setBurstCD(12.0);
    }

    /** Binds Aloy's delayed state to exactly one simulator. */
    @Override
    public void initializeForSimulator(CombatSimulator simulator) {
        if (simulator == null) {
            throw new IllegalArgumentException("Aloy simulator is required");
        }
        if (initializedSimulator != null
                && initializedSimulator != simulator) {
            throw new IllegalStateException(
                    "Aloy cannot be reused across simulators");
        }
        if (initializedSimulator == simulator) {
            return;
        }
        if (!simulator.getPartyMembers().contains(this)) {
            throw new IllegalArgumentException(
                    "Aloy must belong to the target simulator party");
        }
        initializedSimulator = simulator;
    }

    /** Captures Aloy-owned counters, windows, and delayed work. */
    @Override
    public State captureCharacterState() {
        return new AloyState(
                this,
                normalAttackStep,
                coilCount,
                skillGeneration,
                burstGeneration,
                fieldExitGeneration,
                coilIcdExpirationTime,
                rushingIceStartTime,
                rushingIceExpirationTime,
                pendingHits,
                pendingCommands);
    }

    /** Accepts only state captured from this exact Aloy instance. */
    @Override
    public boolean acceptsCharacterState(State state) {
        return state instanceof AloyState
                && ((AloyState) state).owner == this;
    }

    /** Restores Aloy state and reconstructs each future event once. */
    @Override
    public void restoreCharacterState(
            State state,
            CombatSimulator simulator) {
        if (!acceptsCharacterState(state)) {
            throw new IllegalArgumentException("Unexpected Aloy state");
        }
        initializeForSimulator(simulator);
        AloyState restored = (AloyState) state;
        normalAttackStep = restored.normalAttackStep;
        coilCount = restored.coilCount;
        skillGeneration = restored.skillGeneration;
        burstGeneration = restored.burstGeneration;
        fieldExitGeneration = restored.fieldExitGeneration;
        coilIcdExpirationTime = restored.coilIcdExpirationTime;
        rushingIceStartTime = restored.rushingIceStartTime;
        rushingIceExpirationTime = restored.rushingIceExpirationTime;
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

    /** Returns Aloy's 40-Energy Burst cost. */
    @Override
    public double getEnergyCost() {
        return getTalentValue("Energy Cost", 40.0);
    }

    /** Aloy's combat passives are represented by runtime buffs. */
    @Override
    public void applyPassive(StatsContainer stats) {
        // A1 and A4 depend on Coil and Rushing Ice state.
    }

    /** Starts the 30-second Coil clear and resets the Normal string. */
    @Override
    public void onSwitchOut(CombatSimulator simulator) {
        normalAttackStep = 0;
        long generation = ++fieldExitGeneration;
        queueCommand(simulator, new PendingCommand(
                simulator.getCurrentTime()
                        + getTalentValue("Coil Field Exit Clear Delay", 30.0),
                CommandKind.CLEAR_COILS,
                generation,
                0,
                0));
    }

    /** Resets Normal progression without changing the last-exit Coil timer. */
    @Override
    public void onSwitchIn(CombatSimulator simulator) {
        normalAttackStep = 0;
    }

    /** Returns Aloy's current Coil count outside Rushing Ice. */
    public int getCoilCount() {
        return coilCount;
    }

    /** Returns whether Rushing Ice is active at the supplied time. */
    public boolean isRushingIceActive(double currentTime) {
        return currentTime < rushingIceExpirationTime;
    }

    /** Returns the half-open Rushing Ice expiration timestamp. */
    public double getRushingIceExpirationTime() {
        return rushingIceExpirationTime;
    }

    /** Returns the live A4 stack count from one through ten. */
    public int getA4StackCount(double currentTime) {
        if (!isRushingIceActive(currentTime)) {
            return 0;
        }
        return Math.min(10, 1 + (int) Math.floor(
                Math.max(0.0, currentTime - rushingIceStartTime)));
    }

    /** Dispatches Aloy's represented action set. */
    @Override
    public void onAction(
            CharacterActionRequest request,
            CombatSimulator simulator) {
        if (request == null) {
            throw new IllegalArgumentException("Aloy action is required");
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
                fullyChargedAimedShot(simulator);
                break;
            case SKILL:
                frozenWilds(simulator);
                break;
            case BURST:
                propheciesOfDawn(simulator);
                break;
            default:
                throw new IllegalArgumentException(
                        "Unsupported action for Aloy: " + request.getKey());
        }
    }

    private void normalAttack(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        int step = normalAttackStep;
        for (int hit = 0; hit < NORMAL_RELEASE_FRAMES[step].length; hit++) {
            queueCommand(simulator, new PendingCommand(
                    castTime + NORMAL_RELEASE_FRAMES[step][hit] * FRAME,
                    CommandKind.NORMAL_RELEASE,
                    0L,
                    step,
                    hit));
        }
        normalAttackStep = (normalAttackStep + 1) % 4;
        simulator.advanceTime(NORMAL_DURATIONS[step] * FRAME);
    }

    private void fullyChargedAimedShot(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        queueCommand(simulator, new PendingCommand(
                castTime + getTalentValue(
                        "Fully Charged Release Frame", 86.0) * FRAME,
                CommandKind.CHARGED_RELEASE,
                0L,
                0,
                0));
        simulator.advanceTime(getTalentValue(
                "Fully Charged Recovery Frames", 96.0) * FRAME);
    }

    private void frozenWilds(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        long generation = ++skillGeneration;
        StatsContainer snapshot = captureLiveStats(castTime);
        queueCommand(simulator, new PendingCommand(
                castTime + 19.0 * FRAME,
                CommandKind.SKILL_COOLDOWN,
                generation,
                0,
                0));
        double impactTime = castTime + getTalentValue(
                "Skill Impact Frame", 24.0) * FRAME;
        queueHit(simulator, new PendingHit(
                impactTime,
                HitKind.FREEZE_BOMB,
                0,
                generation,
                snapshot));
        for (int index = 0; index < 2; index++) {
            queueHit(simulator, new PendingHit(
                    impactTime + (index + 1) * 6.0 * FRAME,
                    HitKind.BOMBLET,
                    index,
                    generation,
                    snapshot));
        }
        simulator.advanceTime(getTalentValue(
                "Skill Recovery Frames", 70.0) * FRAME);
    }

    private void propheciesOfDawn(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        long generation = ++burstGeneration;
        markBurstCooldownUsed(castTime, simulator.getApplicableBuffs(this));
        StatsContainer snapshot = captureLiveStats(castTime);
        queueCommand(simulator, new PendingCommand(
                castTime + 2.0 * FRAME,
                CommandKind.BURST_ENERGY,
                generation,
                0,
                0));
        queueHit(simulator, new PendingHit(
                castTime + 100.0 * FRAME,
                HitKind.BURST,
                0,
                generation,
                snapshot));
        simulator.advanceTime(117.0 * FRAME);
    }

    private void releaseNormal(
            CombatSimulator simulator,
            int step,
            int hit) {
        double releaseTime = simulator.getCurrentTime();
        boolean rushing = isRushingIceActive(releaseTime);
        queueHit(simulator, new PendingHit(
                releaseTime + PROJECTILE_TRAVEL,
                HitKind.NORMAL,
                step * 10 + hit,
                0L,
                captureLiveStats(releaseTime),
                rushing,
                coilCount));
    }

    private void releaseCharged(CombatSimulator simulator) {
        queueHit(simulator, new PendingHit(
                simulator.getCurrentTime() + PROJECTILE_TRAVEL,
                HitKind.CHARGED,
                0,
                0L,
                captureLiveStats(simulator.getCurrentTime())));
    }

    private void resolveHit(CombatSimulator simulator, PendingHit hit) {
        if ((hit.kind == HitKind.FREEZE_BOMB
                || hit.kind == HitKind.BOMBLET)
                && hit.generation != skillGeneration) {
            return;
        }
        if (hit.kind == HitKind.BURST
                && hit.generation != burstGeneration) {
            return;
        }
        switch (hit.kind) {
            case NORMAL:
                resolveNormal(simulator, hit);
                break;
            case CHARGED:
                resolveCharged(simulator, hit);
                break;
            case FREEZE_BOMB:
                resolveFreezeBomb(simulator, hit);
                break;
            case BOMBLET:
                resolveBomblet(simulator, hit);
                break;
            case BURST:
                resolveBurst(simulator, hit);
                break;
            default:
                throw new IllegalStateException("Unknown Aloy hit kind");
        }
    }

    private void resolveNormal(CombatSimulator simulator, PendingHit hit) {
        int step = hit.index / 10;
        int subIndex = hit.index % 10;
        AttackAction action = attack(
                "Rapid Fire " + NORMAL_KEYS[step][subIndex],
                getTalentValue(
                        NORMAL_KEYS[step][subIndex],
                        NORMAL_MULTIPLIERS[step][subIndex]),
                hit.rushingIce ? Element.CRYO : Element.PHYSICAL,
                StatType.NORMAL_ATTACK_DMG_BONUS,
                ActionType.NORMAL,
                hit.rushingIce ? ICDType.Standard : ICDType.None,
                hit.rushingIce ? ICDTag.NormalAttack : ICDTag.None,
                hit.rushingIce ? 1.0 : 0.0);
        action.setStatSnapshot(hit.snapshot);
        if (hit.rushingIce) {
            action.addBonusStat(
                    StatType.NORMAL_ATTACK_DMG_BONUS,
                    getTalentValue(
                            "Rushing Ice Normal Attack DMG Bonus", 0.45325));
        } else if (hit.coilCount > 0) {
            action.addBonusStat(
                    StatType.NORMAL_ATTACK_DMG_BONUS,
                    getCoilNormalBonus(hit.coilCount));
        }
        simulator.performActionWithoutTimeAdvance(characterId, action);
    }

    private void resolveCharged(
            CombatSimulator simulator,
            PendingHit hit) {
        AttackAction action = attack(
                "Rapid Fire Fully Charged Aimed Shot",
                getTalentValue("Fully Charged Aimed Shot", 2.108),
                Element.CRYO,
                StatType.CHARGED_ATTACK_DMG_BONUS,
                ActionType.CHARGE,
                ICDType.None,
                ICDTag.None,
                1.0);
        action.setStatSnapshot(hit.snapshot);
        simulator.performActionWithoutTimeAdvance(characterId, action);
    }

    private void resolveFreezeBomb(
            CombatSimulator simulator,
            PendingHit hit) {
        AttackAction action = attack(
                "Frozen Wilds Freeze Bomb",
                getTalentValue("Freeze Bomb", 3.0192),
                Element.CRYO,
                StatType.SKILL_DMG_BONUS,
                ActionType.SKILL,
                ICDType.None,
                ICDTag.None,
                1.0);
        action.setStatSnapshot(hit.snapshot);
        simulator.performActionWithoutTimeAdvance(characterId, action);
        if (simulator.getEnemy() != null) {
            queueCommand(simulator, new PendingCommand(
                    simulator.getCurrentTime() + PARTICLE_TRAVEL,
                    CommandKind.PARTICLE,
                    0L,
                    5,
                    0));
        }
    }

    private void resolveBomblet(
            CombatSimulator simulator,
            PendingHit hit) {
        AttackAction action = attack(
                "Frozen Wilds Chillwater Bomblet",
                getTalentValue("Chillwater Bomblet", 0.68),
                Element.CRYO,
                StatType.SKILL_DMG_BONUS,
                ActionType.SKILL,
                ICDType.Standard,
                ICDTag.ElementalSkill,
                1.0);
        action.setStatSnapshot(hit.snapshot);
        simulator.performActionWithoutTimeAdvance(characterId, action);
        if (simulator.getEnemy() != null) {
            gainCoil(simulator);
        }
    }

    private void resolveBurst(
            CombatSimulator simulator,
            PendingHit hit) {
        AttackAction action = attack(
                "Prophecies of Dawn",
                getTalentValue("Prophecies of Dawn", 6.1064),
                Element.CRYO,
                StatType.BURST_DMG_BONUS,
                ActionType.BURST,
                ICDType.Standard,
                ICDTag.ElementalBurst,
                2.0);
        action.setStatSnapshot(hit.snapshot);
        simulator.performActionWithoutTimeAdvance(characterId, action);
    }

    private void gainCoil(CombatSimulator simulator) {
        double currentTime = simulator.getCurrentTime();
        if (isRushingIceActive(currentTime)
                || currentTime < coilIcdExpirationTime - EPSILON) {
            return;
        }
        coilCount++;
        coilIcdExpirationTime = currentTime
                + getTalentValue("Coil Gain Cooldown", 0.1);
        applyA1(simulator, currentTime);
        if (coilCount >= 4) {
            activateRushingIce(currentTime);
        }
    }

    private void applyA1(
            CombatSimulator simulator,
            double currentTime) {
        double duration = getTalentValue("A1 Duration", 10.0);
        removeBuff(BuffId.ALOY_A1_OWNER_ATK);
        addBuff(new SimpleBuff(
                "Aloy Combat Override Owner",
                BuffId.ALOY_A1_OWNER_ATK,
                duration,
                currentTime,
                stats -> stats.add(
                        StatType.ATK_PERCENT,
                        getTalentValue("A1 Owner ATK Bonus", 0.16))));
        simulator.applyTeamBuffNoStack(new SimpleBuff(
                "Aloy Combat Override Team",
                BuffId.ALOY_A1_TEAM_ATK,
                duration,
                currentTime,
                stats -> stats.add(
                        StatType.ATK_PERCENT,
                        getTalentValue("A1 Team ATK Bonus", 0.08)))
                .exclude(characterId)
                .sourcedBy(characterId));
    }

    private void activateRushingIce(double currentTime) {
        coilCount = 0;
        rushingIceStartTime = currentTime;
        rushingIceExpirationTime = currentTime
                + getTalentValue("Rushing Ice Duration", 10.0);
        removeBuff(BuffId.ALOY_A4_CRYO_DMG_BONUS);
        addBuff(new AloyA4Buff(this, currentTime,
                rushingIceExpirationTime - currentTime));
    }

    private double getCoilNormalBonus(int stacks) {
        switch (stacks) {
            case 1:
                return getTalentValue(
                        "Coil Stack 1 Normal Attack DMG Bonus", 0.09065);
            case 2:
                return getTalentValue(
                        "Coil Stack 2 Normal Attack DMG Bonus", 0.1813);
            default:
                return getTalentValue(
                        "Coil Stack 3 Normal Attack DMG Bonus", 0.27195);
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
                case NORMAL_RELEASE:
                    releaseNormal(
                            activeSimulator, command.index, command.subIndex);
                    break;
                case CHARGED_RELEASE:
                    releaseCharged(activeSimulator);
                    break;
                case SKILL_COOLDOWN:
                    if (command.generation == skillGeneration) {
                        markSkillUsed(
                                activeSimulator.getCurrentTime(),
                                activeSimulator.getApplicableBuffs(this));
                    }
                    break;
                case BURST_ENERGY:
                    if (command.generation == burstGeneration) {
                        spendBurstEnergy(activeSimulator.getCurrentTime());
                    }
                    break;
                case PARTICLE:
                    activeSimulator.getEnergyDistributor().distributeParticles(
                            Element.CRYO,
                            command.index,
                            ParticleType.PARTICLE);
                    break;
                case CLEAR_COILS:
                    if (command.generation == fieldExitGeneration) {
                        coilCount = 0;
                    }
                    break;
                default:
                    throw new IllegalStateException("Unknown Aloy command kind");
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
                false,
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
        FREEZE_BOMB,
        BOMBLET,
        BURST
    }

    private enum CommandKind {
        NORMAL_RELEASE,
        CHARGED_RELEASE,
        SKILL_COOLDOWN,
        BURST_ENERGY,
        PARTICLE,
        CLEAR_COILS
    }

    /** Dynamic A4 Cryo bonus bound to one Rushing Ice window. */
    private static final class AloyA4Buff extends Buff {
        private final Aloy owner;

        private AloyA4Buff(Aloy owner, double startTime, double duration) {
            super("Aloy Strong Strike", BuffId.ALOY_A4_CRYO_DMG_BONUS,
                    duration, startTime);
            this.owner = owner;
        }

        @Override
        protected void applyStats(
                StatsContainer stats,
                double currentTime) {
            stats.add(
                    StatType.CRYO_DMG_BONUS,
                    owner.getA4StackCount(currentTime)
                            * owner.getTalentValue(
                                    "A4 Cryo DMG Bonus Per Stack", 0.035));
        }
    }

    /** Immutable delayed Aloy hit description. */
    private static final class PendingHit {
        private final double time;
        private final HitKind kind;
        private final int index;
        private final long generation;
        private final StatsContainer snapshot;
        private final boolean rushingIce;
        private final int coilCount;

        private PendingHit(
                double time,
                HitKind kind,
                int index,
                long generation,
                StatsContainer snapshot) {
            this(time, kind, index, generation, snapshot, false, 0);
        }

        private PendingHit(
                double time,
                HitKind kind,
                int index,
                long generation,
                StatsContainer snapshot,
                boolean rushingIce,
                int coilCount) {
            this.time = time;
            this.kind = kind;
            this.index = index;
            this.generation = generation;
            this.snapshot = snapshot == null
                    ? null : snapshot.merge(null);
            this.rushingIce = rushingIce;
            this.coilCount = coilCount;
        }

        private PendingHit copy() {
            return new PendingHit(
                    time, kind, index, generation, snapshot,
                    rushingIce, coilCount);
        }
    }

    /** Immutable delayed Aloy command description. */
    private static final class PendingCommand {
        private final double time;
        private final CommandKind kind;
        private final long generation;
        private final int index;
        private final int subIndex;

        private PendingCommand(
                double time,
                CommandKind kind,
                long generation,
                int index,
                int subIndex) {
            this.time = time;
            this.kind = kind;
            this.generation = generation;
            this.index = index;
            this.subIndex = subIndex;
        }

        private PendingCommand copy() {
            return new PendingCommand(
                    time, kind, generation, index, subIndex);
        }
    }

    /** Immutable Aloy-owned simulator snapshot payload. */
    private static final class AloyState implements State {
        private final Aloy owner;
        private final int normalAttackStep;
        private final int coilCount;
        private final long skillGeneration;
        private final long burstGeneration;
        private final long fieldExitGeneration;
        private final double coilIcdExpirationTime;
        private final double rushingIceStartTime;
        private final double rushingIceExpirationTime;
        private final List<PendingHit> pendingHits;
        private final List<PendingCommand> pendingCommands;

        private AloyState(
                Aloy owner,
                int normalAttackStep,
                int coilCount,
                long skillGeneration,
                long burstGeneration,
                long fieldExitGeneration,
                double coilIcdExpirationTime,
                double rushingIceStartTime,
                double rushingIceExpirationTime,
                List<PendingHit> pendingHits,
                List<PendingCommand> pendingCommands) {
            this.owner = owner;
            this.normalAttackStep = normalAttackStep;
            this.coilCount = coilCount;
            this.skillGeneration = skillGeneration;
            this.burstGeneration = burstGeneration;
            this.fieldExitGeneration = fieldExitGeneration;
            this.coilIcdExpirationTime = coilIcdExpirationTime;
            this.rushingIceStartTime = rushingIceStartTime;
            this.rushingIceExpirationTime = rushingIceExpirationTime;
            this.pendingHits = copyHits(pendingHits);
            this.pendingCommands = copyCommands(pendingCommands);
        }
    }
}

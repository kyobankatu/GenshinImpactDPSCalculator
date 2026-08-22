package model.character;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.DoubleSupplier;

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
 * Dehya's stationary single-target Fiery Sanctum offensive slice through C6.
 *
 * <p>Sandstorm Assault, High Plunge, both Molten Inferno placements, the
 * 2.5-second coordinated-attack gate, particles, Leonine Bite's automatic
 * fist-to-kick sequence, and representable offensive constellations follow
 * pinned gcsim {@code ef41805d}. Field damage snapshots at placement; other
 * delayed hits retain their queue-time stats.</p>
 *
 * <p>Player damage intake, mitigation and redirection, healing and current HP,
 * interruption resistance, movement and auto-targeting, geometry and
 * multi-target behavior, stamina, hitlag extension, Charged Attack, low Plunge, C2's
 * incoming-hit damage branch, and unsupported Burst input acceleration are
 * excluded rather than approximated.</p>
 */
public final class Dehya extends Character implements
        SimulatorInitializedCharacterEffect,
        SnapshotAwareCharacterEffect,
        SwitchAwareCharacter {
    private static final double FRAME = 1.0 / 60.0;
    private static final double EPSILON = 1e-9;
    private static final double PARTICLE_TRAVEL = 100.0 * FRAME;
    private static final int[] NORMAL_HIT_FRAMES = { 22, 26, 26, 41 };
    private static final int[] NORMAL_DURATIONS = { 31, 34, 43, 85 };
    private static final double[] NORMAL_T9 = {
        1.141234, 1.133745, 1.407875, 1.750703
    };

    /**
     * Per-hit hitlag from gcsim pinned at
     * {@code 3647a07a7cc3004bc1e79d9bb5f7444de20dceaa}.
     */
    private static final HitlagProfile[] NORMAL_HITLAG = {
        new HitlagProfile(0.09, 0.01, true, false, false),
        new HitlagProfile(0.10, 0.01, true, false, false),
        new HitlagProfile(0.08, 0.01, true, false, false),
        new HitlagProfile(0.12, 0.01, true, false, false)
    };
    private static final HitlagProfile RECAST_HITLAG =
            new HitlagProfile(0.02, 0.01, false, false, false);
    private static final HitlagProfile COORDINATED_HITLAG =
            new HitlagProfile(0.02, 0.01, false, true, false);

    private final DoubleSupplier criticalRandom;
    private CombatSimulator initializedSimulator;
    private int normalAttackStep;
    private boolean recastUsed;
    private boolean burstKickPending;
    private long fieldGeneration;
    private long burstGeneration;
    private double fieldExpirationTime = Double.NEGATIVE_INFINITY;
    private double nextCoordinatedAllowedTime = Double.NEGATIVE_INFINITY;
    private double burstStartTime = Double.POSITIVE_INFINITY;
    private double burstExpirationTime = Double.NEGATIVE_INFINITY;
    private double savedFieldDuration;
    private int c6Stacks;
    private StatsContainer fieldSnapshot;
    private List<PendingHit> pendingHits = new ArrayList<>();
    private List<PendingCommand> pendingCommands = new ArrayList<>();

    /** Constructs repository-default C6 Dehya. */
    public Dehya(Weapon weapon, ArtifactSet artifacts) {
        this(weapon, artifacts, TalentDataManager.getInstance(), 6,
                Math::random);
    }

    /** Constructs Dehya at an explicit constellation. */
    public Dehya(
            Weapon weapon,
            ArtifactSet artifacts,
            int constellation) {
        this(weapon, artifacts, TalentDataManager.getInstance(),
                constellation, Math::random);
    }

    /**
     * Constructs Dehya with injectable talent data and C6 critical draws.
     *
     * @param weapon equipped claymore, or {@code null}
     * @param artifacts equipped artifact set, or {@code null}
     * @param talentData static character-data source
     * @param constellation constellation level in {@code [0, 6]}
     * @param criticalRandom C6 critical draw source in {@code [0, 1)}
     */
    public Dehya(
            Weapon weapon,
            ArtifactSet artifacts,
            TalentDataSource talentData,
            int constellation,
            DoubleSupplier criticalRandom) {
        super(talentData);
        if (constellation < 0 || constellation > 6) {
            throw new IllegalArgumentException(
                    "Dehya constellation must be between 0 and 6");
        }
        if (criticalRandom == null) {
            throw new IllegalArgumentException(
                    "Dehya critical random source is required");
        }
        name = "Dehya";
        characterId = CharacterId.DEHYA;
        element = Element.PYRO;
        this.weapon = weapon;
        this.artifacts = new ArtifactSet[] { artifacts };
        this.constellation = constellation;
        this.criticalRandom = criticalRandom;
        baseStats.set(StatType.BASE_HP,
                getTalentValue("Base HP", 15675.0));
        baseStats.set(StatType.BASE_ATK,
                getTalentValue("Base ATK", 265.0));
        baseStats.set(StatType.BASE_DEF,
                getTalentValue("Base DEF", 628.0));
        baseStats.add(StatType.HP_PERCENT,
                getTalentValue("Ascension HP Percent", 0.288));
        setSkillCD(getTalentValue("Skill Cooldown", 20.0));
        setBurstCD(getTalentValue("Burst Cooldown", 18.0));
    }

    /** Binds field damage listeners and mutable state to one simulator. */
    @Override
    public void initializeForSimulator(CombatSimulator simulator) {
        if (simulator == null) {
            throw new IllegalArgumentException("Dehya simulator is required");
        }
        if (!simulator.getPartyMembers().contains(this)) {
            throw new IllegalArgumentException(
                    "Dehya must belong to the simulator party");
        }
        if (initializedSimulator != null
                && initializedSimulator != simulator) {
            throw new IllegalStateException(
                    "Dehya cannot be reused across simulators");
        }
        if (initializedSimulator == simulator) {
            return;
        }
        initializedSimulator = simulator;
        simulator.addDamageListener((actor, action, damage, time) ->
                handlePartyDamage(simulator, damage, time));
        simulator.addIndirectDamageListener((owner, damage, time) ->
                handlePartyDamage(simulator, damage, time));
    }

    /** Captures all field, Burst, gate, and reconstructable future state. */
    @Override
    public State captureCharacterState() {
        return new DehyaState(
                this,
                normalAttackStep,
                recastUsed,
                burstKickPending,
                fieldGeneration,
                burstGeneration,
                fieldExpirationTime,
                nextCoordinatedAllowedTime,
                burstStartTime,
                burstExpirationTime,
                savedFieldDuration,
                c6Stacks,
                fieldSnapshot,
                pendingHits,
                pendingCommands);
    }

    /** Accepts state captured from this exact Dehya instance only. */
    @Override
    public boolean acceptsCharacterState(State state) {
        return state instanceof DehyaState
                && ((DehyaState) state).owner == this;
    }

    /** Restores mutable state and schedules each surviving event once. */
    @Override
    public void restoreCharacterState(
            State state,
            CombatSimulator simulator) {
        if (!acceptsCharacterState(state)) {
            throw new IllegalArgumentException("Unexpected Dehya state");
        }
        initializeForSimulator(simulator);
        DehyaState restored = (DehyaState) state;
        normalAttackStep = restored.normalAttackStep;
        recastUsed = restored.recastUsed;
        burstKickPending = restored.burstKickPending;
        fieldGeneration = restored.fieldGeneration;
        burstGeneration = restored.burstGeneration;
        fieldExpirationTime = restored.fieldExpirationTime;
        nextCoordinatedAllowedTime =
                restored.nextCoordinatedAllowedTime;
        burstStartTime = restored.burstStartTime;
        burstExpirationTime = restored.burstExpirationTime;
        savedFieldDuration = restored.savedFieldDuration;
        c6Stacks = restored.c6Stacks;
        fieldSnapshot = copyStats(restored.fieldSnapshot);
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

    /** Returns Dehya's 70-Energy Burst cost. */
    @Override
    public double getEnergyCost() {
        return getTalentValue("Energy Cost", 70.0);
    }

    /** Applies C1's unconditional 20 percent Max HP increase. */
    @Override
    public void applyPassive(StatsContainer stats) {
        if (constellation >= 1) {
            stats.add(StatType.HP_PERCENT,
                    getTalentValue("C1 HP Percent", 0.20));
        }
    }

    /** Lets the one Ranging Flame recast bypass the original Skill cooldown. */
    @Override
    public double getSkillCDRemaining(double currentTime) {
        if (isFierySanctumActive(currentTime) && !recastUsed) {
            return 0.0;
        }
        return super.getSkillCDRemaining(currentTime);
    }

    /** Cancels the Burst sequence and restores a stored field after switching. */
    @Override
    public void onSwitchOut(CombatSimulator simulator) {
        normalAttackStep = 0;
        if (!isBurstActive(simulator.getCurrentTime())
                && !burstKickPending) {
            return;
        }
        burstGeneration++;
        burstStartTime = Double.POSITIVE_INFINITY;
        burstExpirationTime = Double.NEGATIVE_INFINITY;
        burstKickPending = false;
        restoreStoredField(
                simulator,
                simulator.getCurrentTime() + 46.0 * FRAME);
    }

    /** Returns whether the current Fiery Sanctum field remains active. */
    public boolean isFierySanctumActive(double currentTime) {
        return currentTime + EPSILON < fieldExpirationTime;
    }

    /** Returns the current field expiry timestamp. */
    public double getFieldExpirationTime() {
        return fieldExpirationTime;
    }

    /** Returns whether the represented Blazing Lioness form is active. */
    public boolean isBurstActive(double currentTime) {
        return currentTime + EPSILON >= burstStartTime
                && currentTime + EPSILON < burstExpirationTime;
    }

    /** Returns the number of C6 critical extensions in the current Burst. */
    public int getC6Stacks() {
        return c6Stacks;
    }

    /** Returns the next timestamp accepted by the field trigger gate. */
    public double getNextCoordinatedAllowedTime() {
        return nextCoordinatedAllowedTime;
    }

    /** Returns the number of unresolved Dehya-owned damage hits. */
    public int getPendingHitCount() {
        return pendingHits.size();
    }

    /** Dispatches Dehya's represented typed action set. */
    @Override
    public void onAction(
            CharacterActionRequest request,
            CombatSimulator simulator) {
        if (request == null) {
            throw new IllegalArgumentException("Dehya action is required");
        }
        initializeForSimulator(simulator);
        if (isBurstActive(simulator.getCurrentTime()) || burstKickPending) {
            throw new IllegalArgumentException(
                    "Dehya Burst input acceleration is outside this slice");
        }
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
                            "Dehya supports Tap Skill only");
                }
                moltenInferno(simulator);
                break;
            case BURST:
                leonineBite(simulator);
                break;
            case CHARGE:
                throw new IllegalArgumentException(
                        "Dehya Charged Attack is not implemented by the pinned source");
            default:
                throw new IllegalArgumentException(
                        "Unsupported action for Dehya: " + request.getKey());
        }
    }

    private void normalAttack(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        int step = normalAttackStep;
        queueHit(simulator, new PendingHit(
                castTime + NORMAL_HIT_FRAMES[step] * FRAME,
                HitKind.NORMAL,
                step,
                0L,
                captureLiveStats(castTime),
                0.0));
        normalAttackStep = (normalAttackStep + 1)
                % NORMAL_HIT_FRAMES.length;
        simulator.advanceTime(NORMAL_DURATIONS[step] * FRAME);
    }

    private void highPlunge(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        queueHit(simulator, new PendingHit(
                castTime + 40.0 * FRAME,
                HitKind.HIGH_PLUNGE,
                0,
                0L,
                captureLiveStats(castTime),
                0.0));
        simulator.advanceTime(66.0 * FRAME);
    }

    private void moltenInferno(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        if (isFierySanctumActive(castTime) && !recastUsed) {
            rangingFlame(simulator, castTime);
            return;
        }
        indomitableFlame(simulator, castTime);
    }

    private void indomitableFlame(
            CombatSimulator simulator,
            double castTime) {
        long generation = ++fieldGeneration;
        fieldExpirationTime = Double.NEGATIVE_INFINITY;
        recastUsed = false;
        StatsContainer snapshot = captureLiveStats(castTime);
        queueCommand(simulator, new PendingCommand(
                castTime + 18.0 * FRAME,
                CommandKind.SKILL_COOLDOWN,
                generation,
                0.0,
                null,
                false));
        queueHit(simulator, new PendingHit(
                castTime + 20.0 * FRAME,
                HitKind.SKILL_INITIAL,
                0,
                generation,
                snapshot,
                skillC1FlatDamage(snapshot)));
        queueCommand(simulator, new PendingCommand(
                castTime + 21.0 * FRAME,
                CommandKind.PLACE_FIELD,
                generation,
                getTalentValue("Fiery Sanctum Duration", 12.0),
                snapshot,
                false));
        simulator.advanceTime(39.0 * FRAME);
    }

    private void rangingFlame(
            CombatSimulator simulator,
            double castTime) {
        double remaining = Math.max(0.0, fieldExpirationTime - castTime);
        double duration = remaining
                + getTalentValue("Ranging Flame Extension", 0.4);
        if (constellation >= 2) {
            duration += getTalentValue("C2 Field Extension", 6.0);
        }
        long generation = ++fieldGeneration;
        fieldExpirationTime = Double.NEGATIVE_INFINITY;
        recastUsed = true;
        StatsContainer snapshot = captureLiveStats(castTime);
        queueHit(simulator, new PendingHit(
                castTime + 40.0 * FRAME,
                HitKind.SKILL_RECAST,
                0,
                generation,
                snapshot,
                skillC1FlatDamage(snapshot)));
        queueCommand(simulator, new PendingCommand(
                castTime + 41.0 * FRAME,
                CommandKind.PLACE_FIELD,
                generation,
                duration,
                snapshot,
                true));
        simulator.advanceTime(74.0 * FRAME);
    }

    private void leonineBite(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        savedFieldDuration = 0.0;
        if (isFierySanctumActive(castTime)) {
            savedFieldDuration = Math.max(
                    0.0,
                    fieldExpirationTime - castTime)
                    + getTalentValue("Ranging Flame Extension", 0.4);
            fieldGeneration++;
            fieldExpirationTime = Double.NEGATIVE_INFINITY;
        }
        long generation = ++burstGeneration;
        c6Stacks = 0;
        burstKickPending = false;
        burstStartTime = castTime + 105.0 * FRAME;
        burstExpirationTime = burstStartTime
                + getTalentValue("Burst Form Duration", 4.1);
        queueCommand(simulator, new PendingCommand(
                castTime + FRAME,
                CommandKind.BURST_COOLDOWN,
                generation,
                0.0,
                null,
                false));
        queueCommand(simulator, new PendingCommand(
                castTime + 15.0 * FRAME,
                CommandKind.BURST_ENERGY,
                generation,
                0.0,
                null,
                false));
        queueHit(simulator, new PendingHit(
                burstStartTime,
                HitKind.BURST_FIST,
                0,
                generation,
                null,
                0.0));
        simulator.advanceTime(105.0 * FRAME);
    }

    private void handlePartyDamage(
            CombatSimulator simulator,
            double damage,
            double time) {
        if (!(damage > 0.0)
                || !isFierySanctumActive(time)
                || time + EPSILON < nextCoordinatedAllowedTime
                || fieldSnapshot == null) {
            return;
        }
        nextCoordinatedAllowedTime = time
                + getTalentValue("Coordinated Attack ICD", 2.5);
        StatsContainer snapshot = copyStats(fieldSnapshot);
        double hpRatio = skillValue(
                "Fiery Sanctum Max HP",
                0.017544,
                0.020640);
        if (constellation >= 1) {
            hpRatio += getTalentValue("C1 Skill Max HP Ratio", 0.036);
        }
        queueHit(simulator, new PendingHit(
                time + 2.0 * FRAME,
                HitKind.SKILL_COORDINATED,
                0,
                fieldGeneration,
                snapshot,
                snapshot.getTotalHp() * hpRatio));
        queueCommand(simulator, new PendingCommand(
                time + PARTICLE_TRAVEL,
                CommandKind.PARTICLE,
                fieldGeneration,
                getTalentValue("Particle Count", 1.0),
                null,
                false));
    }

    private void resolveHit(CombatSimulator simulator, PendingHit hit) {
        switch (hit.kind) {
            case NORMAL:
                performHit(
                        simulator,
                        hit,
                        "Sandstorm Assault N" + (hit.index + 1),
                        getTalentValue(
                                "N" + (hit.index + 1),
                                NORMAL_T9[hit.index]),
                        Element.PHYSICAL,
                        StatType.NORMAL_ATTACK_DMG_BONUS,
                        ActionType.NORMAL,
                        ICDType.Standard,
                        ICDTag.NormalAttack,
                        false);
                break;
            case HIGH_PLUNGE:
                performHit(
                        simulator,
                        hit,
                        "Sandstorm Assault High Plunge",
                        getTalentValue("High Plunge", 3.422517),
                        Element.PHYSICAL,
                        StatType.PLUNGING_ATTACK_DMG_BONUS,
                        ActionType.PLUNGE,
                        ICDType.None,
                        ICDTag.None,
                        true);
                break;
            case SKILL_INITIAL:
                performHit(
                        simulator,
                        hit,
                        "Molten Inferno: Indomitable Flame",
                        skillValue(
                                "Indomitable Flame",
                                1.918960,
                                2.257600),
                        Element.PYRO,
                        StatType.SKILL_DMG_BONUS,
                        ActionType.SKILL,
                        ICDType.None,
                        ICDTag.ElementalSkill,
                        true);
                break;
            case SKILL_RECAST:
                performHit(
                        simulator,
                        hit,
                        "Molten Inferno: Ranging Flame",
                        skillValue("Ranging Flame", 2.257600, 2.656000),
                        Element.PYRO,
                        StatType.SKILL_DMG_BONUS,
                        ActionType.SKILL,
                        ICDType.None,
                        ICDTag.ElementalSkill,
                        true);
                break;
            case SKILL_COORDINATED:
                performHit(
                        simulator,
                        hit,
                        "Fiery Sanctum Coordinated Attack",
                        skillValue(
                                "Fiery Sanctum ATK",
                                1.023400,
                                1.204000),
                        Element.PYRO,
                        StatType.SKILL_DMG_BONUS,
                        ActionType.SKILL,
                        ICDType.None,
                        ICDTag.ElementalSkill,
                        false);
                break;
            case BURST_FIST:
                resolveBurstFist(simulator, hit);
                break;
            case BURST_KICK:
                resolveBurstKick(simulator, hit);
                break;
            default:
                throw new IllegalStateException(
                        "Unknown Dehya hit kind " + hit.kind);
        }
    }

    private void resolveBurstFist(
            CombatSimulator simulator,
            PendingHit hit) {
        if (hit.generation != burstGeneration) {
            return;
        }
        StatsContainer snapshot = captureLiveStats(hit.time);
        double hpRatio = burstValue(
                "Flame-Mane's Fist Max HP",
                0.028764,
                0.033840);
        if (constellation >= 1) {
            hpRatio += getTalentValue("C1 Burst Max HP Ratio", 0.060);
        }
        PendingHit resolved = hit.withSnapshotAndFlatDamage(
                snapshot,
                snapshot.getTotalHp() * hpRatio);
        performBurstHit(
                simulator,
                resolved,
                "Flame-Mane's Fist " + (hit.index + 1),
                burstValue(
                        "Flame-Mane's Fist ATK",
                        1.677900,
                        1.974000));
        applyC4Energy();
        maybeExtendC6(snapshot);
        if (hit.time + EPSILON < burstExpirationTime) {
            queueHit(simulator, new PendingHit(
                    hit.time + 50.0 * FRAME,
                    HitKind.BURST_FIST,
                    hit.index + 1,
                    hit.generation,
                    null,
                    0.0));
            return;
        }
        burstKickPending = true;
        queueHit(simulator, new PendingHit(
                hit.time + 46.0 * FRAME,
                HitKind.BURST_KICK,
                0,
                hit.generation,
                null,
                0.0));
    }

    private void resolveBurstKick(
            CombatSimulator simulator,
            PendingHit hit) {
        if (hit.generation != burstGeneration || !burstKickPending) {
            return;
        }
        burstKickPending = false;
        StatsContainer snapshot = captureLiveStats(hit.time);
        double hpRatio = burstValue(
                "Incineration Drive Max HP",
                0.040596,
                0.047760);
        if (constellation >= 1) {
            hpRatio += getTalentValue("C1 Burst Max HP Ratio", 0.060);
        }
        PendingHit resolved = hit.withSnapshotAndFlatDamage(
                snapshot,
                snapshot.getTotalHp() * hpRatio);
        performBurstHit(
                simulator,
                resolved,
                "Incineration Drive",
                burstValue(
                        "Incineration Drive ATK",
                        2.368100,
                        2.786000));
        applyC4Energy();
        burstStartTime = Double.POSITIVE_INFINITY;
        burstExpirationTime = Double.NEGATIVE_INFINITY;
        restoreStoredField(simulator, hit.time + FRAME);
    }

    private void performBurstHit(
            CombatSimulator simulator,
            PendingHit hit,
            String displayName,
            double multiplier) {
        AttackAction action = createAttackAction(
                displayName,
                multiplier,
                Element.PYRO,
                StatType.BURST_DMG_BONUS,
                ActionType.BURST,
                hit.flatDamage);
        action.setICD(
                hit.kind == HitKind.BURST_FIST
                        ? ICDType.Standard : ICDType.None,
                hit.kind == HitKind.BURST_FIST
                        ? ICDTag.ElementalBurst : ICDTag.None,
                1.0);
        action.setCountsAsBurstDmg(true);
        action.setShatterTrigger(true);
        if (constellation >= 6) {
            action.addBonusStat(
                    StatType.CRIT_RATE,
                    getTalentValue("C6 Burst CRIT Rate", 0.10));
            action.addBonusStat(
                    StatType.CRIT_DMG,
                    c6Stacks * getTalentValue(
                            "C6 Burst CRIT DMG Per Stack", 0.15));
        }
        action.setStatSnapshot(hit.snapshot);
        simulator.performActionWithoutTimeAdvance(characterId, action);
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
            boolean shatterTrigger) {
        AttackAction action = createAttackAction(
                displayName,
                multiplier,
                hitElement,
                bonusStat,
                actionType,
                hit.flatDamage);
        action.setICD(icdType, icdTag, hitElement == Element.PYRO ? 1.0 : 0.0);
        action.setCountsAsSkillDmg(actionType == ActionType.SKILL);
        action.setShatterTrigger(shatterTrigger);
        action.setHitlagProfile(hitlagProfile(hit));
        action.setStatSnapshot(hit.snapshot == null
                ? captureLiveStats(simulator.getCurrentTime())
                : hit.snapshot);
        simulator.performActionWithoutTimeAdvance(characterId, action);
    }

    private static HitlagProfile hitlagProfile(PendingHit hit) {
        switch (hit.kind) {
            case NORMAL:
                return NORMAL_HITLAG[hit.index];
            case SKILL_RECAST:
                return RECAST_HITLAG;
            case SKILL_COORDINATED:
                return COORDINATED_HITLAG;
            default:
                return HitlagProfile.none();
        }
    }

    private AttackAction createAttackAction(
            String displayName,
            double multiplier,
            Element hitElement,
            StatType bonusStat,
            ActionType actionType,
            double flatDamage) {
        if (flatDamage == 0.0) {
            return new AttackAction(
                    displayName,
                    multiplier,
                    hitElement,
                    StatType.BASE_ATK,
                    bonusStat,
                    0.0,
                    actionType);
        }
        return new DehyaAttackAction(
                displayName,
                multiplier,
                hitElement,
                bonusStat,
                actionType,
                flatDamage);
    }

    private void maybeExtendC6(StatsContainer snapshot) {
        int maximum = (int) getTalentValue("C6 Maximum Stacks", 4.0);
        if (constellation < 6 || c6Stacks >= maximum) {
            return;
        }
        double criticalRate = Math.max(
                0.0,
                Math.min(
                        1.0,
                        snapshot.get(StatType.CRIT_RATE)
                                + getTalentValue(
                                        "C6 Burst CRIT Rate", 0.10)));
        if (criticalRandom.getAsDouble() >= criticalRate) {
            return;
        }
        c6Stacks++;
        burstExpirationTime += getTalentValue(
                "C6 Extension Per Stack", 0.5);
    }

    private void applyC4Energy() {
        if (constellation >= 4) {
            receiveFlatEnergy(getTalentValue("C4 Energy", 1.5));
        }
    }

    private double skillC1FlatDamage(StatsContainer snapshot) {
        if (constellation < 1) {
            return 0.0;
        }
        return snapshot.getTotalHp()
                * getTalentValue("C1 Skill Max HP Ratio", 0.036);
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

    private void restoreStoredField(
            CombatSimulator simulator,
            double time) {
        if (!(savedFieldDuration > 0.0) || fieldSnapshot == null) {
            savedFieldDuration = 0.0;
            return;
        }
        long generation = ++fieldGeneration;
        double duration = savedFieldDuration;
        savedFieldDuration = 0.0;
        queueCommand(simulator, new PendingCommand(
                time,
                CommandKind.PLACE_FIELD,
                generation,
                duration,
                fieldSnapshot,
                recastUsed));
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
                    markSkillUsed(
                            activeSimulator.getCurrentTime(),
                            activeSimulator.getApplicableBuffs(this));
                    break;
                case BURST_COOLDOWN:
                    if (command.generation == burstGeneration) {
                        markBurstCooldownUsed(
                                activeSimulator.getCurrentTime(),
                                activeSimulator.getApplicableBuffs(this));
                    }
                    break;
                case BURST_ENERGY:
                    if (command.generation == burstGeneration) {
                        spendBurstEnergy(activeSimulator.getCurrentTime());
                    }
                    break;
                case PLACE_FIELD:
                    if (command.generation == fieldGeneration) {
                        fieldSnapshot = copyStats(command.snapshot);
                        fieldExpirationTime = command.time + command.value;
                        nextCoordinatedAllowedTime = command.time;
                        recastUsed = command.recastUsed;
                    }
                    break;
                case PARTICLE:
                    activeSimulator.getEnergyDistributor()
                            .distributeParticles(
                                    Element.PYRO,
                                    command.value,
                                    ParticleType.PARTICLE);
                    break;
                default:
                    throw new IllegalStateException(
                            "Unknown Dehya command kind " + command.kind);
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

    private static StatsContainer copyStats(StatsContainer source) {
        return source == null ? null : source.merge(null);
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
        HIGH_PLUNGE,
        SKILL_INITIAL,
        SKILL_RECAST,
        SKILL_COORDINATED,
        BURST_FIST,
        BURST_KICK
    }

    private enum CommandKind {
        SKILL_COOLDOWN,
        BURST_COOLDOWN,
        BURST_ENERGY,
        PLACE_FIELD,
        PARTICLE
    }

    /** Attack action with a fixed Max-HP-derived additive base component. */
    private static final class DehyaAttackAction extends AttackAction {
        private final double fixedAdditiveBaseDamage;

        private DehyaAttackAction(
                String displayName,
                double multiplier,
                Element element,
                StatType bonusStat,
                ActionType actionType,
                double fixedAdditiveBaseDamage) {
            super(
                    displayName,
                    multiplier,
                    element,
                    StatType.BASE_ATK,
                    bonusStat,
                    0.0,
                    actionType);
            this.fixedAdditiveBaseDamage = fixedAdditiveBaseDamage;
        }

        @Override
        public void setAdditiveBaseDmgBonus(double value) {
            // The generic resolver only clears Catalyze-owned additions.
        }

        @Override
        public double getAdditiveBaseDmgBonus() {
            return fixedAdditiveBaseDamage;
        }
    }

    /** Immutable future damage hit with queue-time stats and generation. */
    private static final class PendingHit {
        private final double time;
        private final HitKind kind;
        private final int index;
        private final long generation;
        private final StatsContainer snapshot;
        private final double flatDamage;

        private PendingHit(
                double time,
                HitKind kind,
                int index,
                long generation,
                StatsContainer snapshot,
                double flatDamage) {
            this.time = time;
            this.kind = kind;
            this.index = index;
            this.generation = generation;
            this.snapshot = copyStats(snapshot);
            this.flatDamage = flatDamage;
        }

        private PendingHit withSnapshotAndFlatDamage(
                StatsContainer newSnapshot,
                double newFlatDamage) {
            return new PendingHit(
                    time,
                    kind,
                    index,
                    generation,
                    newSnapshot,
                    newFlatDamage);
        }

        private PendingHit copy() {
            return new PendingHit(
                    time,
                    kind,
                    index,
                    generation,
                    snapshot,
                    flatDamage);
        }
    }

    /** Immutable delayed state command. */
    private static final class PendingCommand {
        private final double time;
        private final CommandKind kind;
        private final long generation;
        private final double value;
        private final StatsContainer snapshot;
        private final boolean recastUsed;

        private PendingCommand(
                double time,
                CommandKind kind,
                long generation,
                double value,
                StatsContainer snapshot,
                boolean recastUsed) {
            this.time = time;
            this.kind = kind;
            this.generation = generation;
            this.value = value;
            this.snapshot = copyStats(snapshot);
            this.recastUsed = recastUsed;
        }

        private PendingCommand copy() {
            return new PendingCommand(
                    time,
                    kind,
                    generation,
                    value,
                    snapshot,
                    recastUsed);
        }
    }

    /** Immutable owner-bound snapshot of all Dehya-specific runtime state. */
    private static final class DehyaState implements State {
        private final Dehya owner;
        private final int normalAttackStep;
        private final boolean recastUsed;
        private final boolean burstKickPending;
        private final long fieldGeneration;
        private final long burstGeneration;
        private final double fieldExpirationTime;
        private final double nextCoordinatedAllowedTime;
        private final double burstStartTime;
        private final double burstExpirationTime;
        private final double savedFieldDuration;
        private final int c6Stacks;
        private final StatsContainer fieldSnapshot;
        private final List<PendingHit> pendingHits;
        private final List<PendingCommand> pendingCommands;

        private DehyaState(
                Dehya owner,
                int normalAttackStep,
                boolean recastUsed,
                boolean burstKickPending,
                long fieldGeneration,
                long burstGeneration,
                double fieldExpirationTime,
                double nextCoordinatedAllowedTime,
                double burstStartTime,
                double burstExpirationTime,
                double savedFieldDuration,
                int c6Stacks,
                StatsContainer fieldSnapshot,
                List<PendingHit> pendingHits,
                List<PendingCommand> pendingCommands) {
            this.owner = owner;
            this.normalAttackStep = normalAttackStep;
            this.recastUsed = recastUsed;
            this.burstKickPending = burstKickPending;
            this.fieldGeneration = fieldGeneration;
            this.burstGeneration = burstGeneration;
            this.fieldExpirationTime = fieldExpirationTime;
            this.nextCoordinatedAllowedTime =
                    nextCoordinatedAllowedTime;
            this.burstStartTime = burstStartTime;
            this.burstExpirationTime = burstExpirationTime;
            this.savedFieldDuration = savedFieldDuration;
            this.c6Stacks = c6Stacks;
            this.fieldSnapshot = copyStats(fieldSnapshot);
            this.pendingHits = copyHits(pendingHits);
            this.pendingCommands = copyCommands(pendingCommands);
        }
    }
}

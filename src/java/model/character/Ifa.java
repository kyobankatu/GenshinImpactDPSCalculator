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
import simulation.action.SkillActionMode;
import simulation.event.SimpleTimerEvent;

/**
 * Ifa's deterministic fixed-target offensive slice through C6.
 *
 * <p>Level-90 data and talent values follow Genshin Optimizer
 * {@code 61c5556a}; hitmarks, action lengths, Energy timing, particles,
 * Nightsoul drain, ICD sequences, and constellation gates follow gcsim PR
 * {@code #2639} at {@code 94bb4fe}. Ifa's owner-local Blessing lasts through
 * the sourced hundred 0.8-point drain ticks. Tonic Shot's private twelve-
 * second alternating application sequence is encoded as explicit gauge values
 * because this content-only unit cannot add a shared ICD type.</p>
 *
 * <p>Healing, flight and falling, swap lock, plunge geometry, target geometry,
 * multiple targets, defensive state, team Nightsoul-point aggregation, and
 * external Nightsoul Burst signaling fail closed. Consequently A1, A4, and C2
 * do not synthesize unsupported team plumbing. The fixed target can receive
 * one sourced Sedation Mark selected from its Pyro, Hydro, Electro, or Cryo
 * Aura at the Burst detection frame.</p>
 */
public final class Ifa extends Character implements
        SimulatorInitializedCharacterEffect,
        SnapshotAwareCharacterEffect,
        SwitchAwareCharacter {
    private static final double FRAME = 1.0 / 60.0;
    private static final double EPSILON = 1e-9;
    private static final int[] NORMAL_HIT_FRAMES = { 10, 13, 42 };
    private static final int[] NORMAL_DURATIONS = { 27, 31, 90 };
    private static final double[] NORMAL_T9 = {
        0.911322, 0.806942, 1.270893
    };
    private static final Element[] SEDATION_PRIORITY = {
        Element.PYRO,
        Element.HYDRO,
        Element.ELECTRO,
        Element.CRYO
    };

    private final DoubleSupplier particleRandom;
    private final DoubleSupplier c6Random;
    private CombatSimulator initializedSimulator;
    private int normalAttackStep;
    private boolean nightsoulBlessing;
    private long nightsoulGeneration;
    private double nightsoulStartTime = Double.NEGATIVE_INFINITY;
    private double nightsoulExpirationTime = Double.NEGATIVE_INFINITY;
    private boolean particleGenerated;
    private double nextC1EnergyTime = Double.NEGATIVE_INFINITY;
    private double tonicLastApplicationTime = Double.NEGATIVE_INFINITY;
    private int tonicSuppressedHits;
    private double c4ExpirationTime = Double.NEGATIVE_INFINITY;
    private long eventGeneration;
    private AttackAction resolvingAction;
    private boolean resolvingPrimaryTonic;
    private List<PendingHit> pendingHits = new ArrayList<>();
    private List<PendingCommand> pendingCommands = new ArrayList<>();

    /** Constructs repository-default C6 Ifa with runtime random draws. */
    public Ifa(Weapon weapon, ArtifactSet artifacts) {
        this(
                weapon,
                artifacts,
                TalentDataManager.getInstance(),
                6,
                Math::random,
                Math::random);
    }

    /** Constructs Ifa at an explicit constellation. */
    public Ifa(
            Weapon weapon,
            ArtifactSet artifacts,
            int constellation) {
        this(
                weapon,
                artifacts,
                TalentDataManager.getInstance(),
                constellation,
                Math::random,
                Math::random);
    }

    /**
     * Constructs Ifa with injectable static data and random sources.
     *
     * @param weapon equipped catalyst, or {@code null}
     * @param artifacts equipped artifact set, or {@code null}
     * @param talentData source for level-90 stats and talent values
     * @param constellation constellation level in {@code [0, 6]}
     * @param particleRandom draw source in {@code [0, 1)} for 4-or-5 particles
     * @param c6Random draw source in {@code [0, 1)} for the C6 extra shot
     */
    public Ifa(
            Weapon weapon,
            ArtifactSet artifacts,
            TalentDataSource talentData,
            int constellation,
            DoubleSupplier particleRandom,
            DoubleSupplier c6Random) {
        super(talentData);
        if (constellation < 0 || constellation > 6) {
            throw new IllegalArgumentException(
                    "Ifa constellation must be between 0 and 6");
        }
        if (particleRandom == null || c6Random == null) {
            throw new IllegalArgumentException(
                    "Ifa random sources are required");
        }
        name = "Ifa";
        characterId = CharacterId.IFA;
        element = Element.ANEMO;
        this.weapon = weapon;
        this.artifacts = new ArtifactSet[] { artifacts };
        this.constellation = constellation;
        this.particleRandom = particleRandom;
        this.c6Random = c6Random;
        baseStats.set(StatType.BASE_HP,
                getTalentValue("Base HP", 10081.0));
        baseStats.set(StatType.BASE_ATK,
                getTalentValue("Base ATK", 178.0));
        baseStats.set(StatType.BASE_DEF,
                getTalentValue("Base DEF", 605.0));
        baseStats.add(StatType.ELEMENTAL_MASTERY,
                getTalentValue(
                        "Ascension Elemental Mastery", 96.0));
        setSkillCD(getTalentValue("Skill Cooldown", 7.5));
        setBurstCD(getTalentValue("Burst Cooldown", 15.0));
    }

    /** Binds Ifa's accepted-hit listener and delayed work to one simulator. */
    @Override
    public void initializeForSimulator(CombatSimulator simulator) {
        if (simulator == null) {
            throw new IllegalArgumentException("Ifa simulator is required");
        }
        if (!simulator.getPartyMembers().contains(this)) {
            throw new IllegalArgumentException(
                    "Ifa must belong to the simulator party");
        }
        if (initializedSimulator != null
                && initializedSimulator != simulator) {
            throw new IllegalStateException(
                    "Ifa cannot be reused across simulators");
        }
        if (initializedSimulator == simulator) {
            return;
        }
        initializedSimulator = simulator;
        simulator.addDamageListener((actor, action, damage, time) -> {
            if (actor == this
                    && action == resolvingAction
                    && resolvingPrimaryTonic
                    && damage > 0.0) {
                onAcceptedPrimaryTonic(simulator, time);
            }
        });
    }

    /** Captures Ifa's owner gates and all reconstructable delayed work. */
    @Override
    public State captureCharacterState() {
        return new IfaState(
                this,
                normalAttackStep,
                nightsoulBlessing,
                nightsoulGeneration,
                nightsoulStartTime,
                nightsoulExpirationTime,
                particleGenerated,
                nextC1EnergyTime,
                tonicLastApplicationTime,
                tonicSuppressedHits,
                c4ExpirationTime,
                eventGeneration,
                pendingHits,
                pendingCommands);
    }

    /** Accepts state captured from this exact Ifa instance only. */
    @Override
    public boolean acceptsCharacterState(State state) {
        return state instanceof IfaState
                && ((IfaState) state).owner == this;
    }

    /** Restores Ifa state while invalidating all pre-restore timer callbacks. */
    @Override
    public void restoreCharacterState(
            State state,
            CombatSimulator simulator) {
        if (!acceptsCharacterState(state)) {
            throw new IllegalArgumentException("Unexpected Ifa state");
        }
        initializeForSimulator(simulator);
        IfaState restored = (IfaState) state;
        normalAttackStep = restored.normalAttackStep;
        nightsoulBlessing = restored.nightsoulBlessing;
        nightsoulGeneration = restored.nightsoulGeneration;
        nightsoulStartTime = restored.nightsoulStartTime;
        nightsoulExpirationTime = restored.nightsoulExpirationTime;
        particleGenerated = restored.particleGenerated;
        nextC1EnergyTime = restored.nextC1EnergyTime;
        tonicLastApplicationTime = restored.tonicLastApplicationTime;
        tonicSuppressedHits = restored.tonicSuppressedHits;
        c4ExpirationTime = restored.c4ExpirationTime;
        eventGeneration = Math.max(
                eventGeneration, restored.eventGeneration) + 1L;
        pendingHits = copyHits(restored.pendingHits);
        pendingCommands = copyCommands(restored.pendingCommands);
        resolvingAction = null;
        resolvingPrimaryTonic = false;
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

    /** Returns Ifa's 60-Energy Burst cost. */
    @Override
    public double getEnergyCost() {
        return getTalentValue("Energy Cost", 60.0);
    }

    /** Applies the representable C4 self-EM window. */
    @Override
    public void applyPassive(StatsContainer stats) {
        double currentTime = initializedSimulator == null
                ? Double.NEGATIVE_INFINITY
                : initializedSimulator.getCurrentTime();
        if (constellation >= 4
                && currentTime + EPSILON < c4ExpirationTime) {
            stats.add(StatType.ELEMENTAL_MASTERY,
                    getTalentValue("C4 Elemental Mastery", 100.0));
        }
    }

    /** Resets only Ifa's grounded Normal string on switch-out. */
    @Override
    public void onSwitchOut(CombatSimulator simulator) {
        normalAttackStep = 0;
    }

    /** Resets only Ifa's grounded Normal string on switch-in. */
    @Override
    public void onSwitchIn(CombatSimulator simulator) {
        normalAttackStep = 0;
    }

    /** Allows the sourced Skill recast while Blessing remains active. */
    @Override
    public double getSkillCDRemaining(double currentTime) {
        if (isNightsoulBlessingActive(currentTime)) {
            return 0.0;
        }
        return super.getSkillCDRemaining(currentTime);
    }

    /** Returns whether Ifa's half-open owner Blessing is active. */
    public boolean isNightsoulBlessingActive(double currentTime) {
        return nightsoulBlessing
                && currentTime + EPSILON < nightsoulExpirationTime;
    }

    /** Returns owner Nightsoul points after sourced discrete drain ticks. */
    public double getNightsoulPoints(double currentTime) {
        if (!isNightsoulBlessingActive(currentTime)) {
            return 0.0;
        }
        double firstDrainTime = nightsoulStartTime
                + getTalentValue(
                        "First Nightsoul Drain Frames", 4.0) * FRAME;
        if (currentTime + EPSILON < firstDrainTime) {
            return getTalentValue("Maximum Nightsoul Points", 80.0);
        }
        double interval = getTalentValue(
                "Nightsoul Drain Interval Frames", 6.0) * FRAME;
        int ticks = (int) Math.floor(
                (currentTime - firstDrainTime + EPSILON) / interval) + 1;
        return Math.max(0.0,
                getTalentValue("Maximum Nightsoul Points", 80.0)
                        - ticks * getTalentValue(
                                "Nightsoul Drain Per Tick", 0.8));
    }

    /** Returns the exact natural Blessing expiration timestamp. */
    public double getNightsoulExpirationTime() {
        return nightsoulExpirationTime;
    }

    /** Returns whether C4's owner EM window is active. */
    public boolean isC4Active(double currentTime) {
        return constellation >= 4
                && currentTime + EPSILON < c4ExpirationTime;
    }

    /** Returns the number of unresolved Ifa-owned hits. */
    public int getPendingHitCount() {
        return pendingHits.size();
    }

    /** Returns the number of unresolved Ifa-owned commands. */
    public int getPendingCommandCount() {
        return pendingCommands.size();
    }

    /** Reports that Tonic Shot healing is deliberately excluded. */
    public boolean isHealingRepresented() {
        return false;
    }

    /** Reports that movement, falling, and swap lock are excluded. */
    public boolean isMovementStateRepresented() {
        return false;
    }

    /** Reports that target geometry and multiple targets are excluded. */
    public boolean isMultiTargetGeometryRepresented() {
        return false;
    }

    /** Reports that team Nightsoul aggregation and burst signals are excluded. */
    public boolean isTeamNightsoulPlumbingRepresented() {
        return false;
    }

    /** Dispatches Ifa's bounded fixed-target action set. */
    @Override
    public void onAction(
            CharacterActionRequest request,
            CombatSimulator simulator) {
        if (request == null) {
            throw new IllegalArgumentException("Ifa action is required");
        }
        initializeForSimulator(simulator);
        if (request.getKey() == CharacterActionKey.SKILL
                && request.getSkillMode() != SkillActionMode.PRESS) {
            throw new IllegalArgumentException(
                    "Ifa Hold Skill plunge is outside fixed-target scope");
        }
        if (request.getKey() != CharacterActionKey.NORMAL) {
            normalAttackStep = 0;
        }
        switch (request.getKey()) {
            case NORMAL:
                if (isNightsoulBlessingActive(
                        simulator.getCurrentTime())) {
                    tonicShot(simulator, true);
                } else {
                    normalAttack(simulator);
                }
                break;
            case CHARGE:
                if (isNightsoulBlessingActive(
                        simulator.getCurrentTime())) {
                    tonicShot(simulator, false);
                } else {
                    chargedAttack(simulator);
                }
                break;
            case SKILL:
                skill(simulator);
                break;
            case BURST:
                burst(simulator);
                break;
            default:
                throw new IllegalArgumentException(
                        "Unsupported fixed-target action for Ifa: "
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
                Element.ANEMO,
                0L,
                null));
        normalAttackStep = (normalAttackStep + 1)
                % NORMAL_HIT_FRAMES.length;
        simulator.advanceTime(NORMAL_DURATIONS[step] * FRAME);
    }

    private void chargedAttack(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        queueHit(simulator, new PendingHit(
                castTime + 45.0 * FRAME,
                HitKind.CHARGED,
                0,
                Element.ANEMO,
                0L,
                null));
        simulator.advanceTime(86.0 * FRAME);
    }

    private void skill(CombatSimulator simulator) {
        if (isNightsoulBlessingActive(simulator.getCurrentTime())) {
            exitNightsoulBlessing(
                    simulator, simulator.getCurrentTime());
            simulator.advanceTime(44.0 * FRAME);
            return;
        }
        enterNightsoulBlessing(simulator);
        simulator.advanceTime(31.0 * FRAME);
    }

    private void enterNightsoulBlessing(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        nightsoulBlessing = true;
        nightsoulGeneration++;
        nightsoulStartTime = castTime;
        particleGenerated = false;
        int drainTicks = (int) Math.round(
                getTalentValue("Maximum Nightsoul Points", 80.0)
                        / getTalentValue(
                                "Nightsoul Drain Per Tick", 0.8));
        double endFrames = getTalentValue(
                "First Nightsoul Drain Frames", 4.0)
                + (drainTicks - 1) * getTalentValue(
                        "Nightsoul Drain Interval Frames", 6.0);
        nightsoulExpirationTime = castTime + endFrames * FRAME;
        queueCommand(simulator, new PendingCommand(
                nightsoulExpirationTime,
                CommandKind.NIGHTSOUL_END,
                nightsoulGeneration,
                0.0));
    }

    private void exitNightsoulBlessing(
            CombatSimulator simulator,
            double currentTime) {
        if (!nightsoulBlessing) {
            return;
        }
        long endingGeneration = nightsoulGeneration;
        nightsoulBlessing = false;
        nightsoulStartTime = Double.NEGATIVE_INFINITY;
        nightsoulExpirationTime = Double.NEGATIVE_INFINITY;
        particleGenerated = false;
        normalAttackStep = 0;
        pendingCommands.removeIf(command ->
                command.kind == CommandKind.NIGHTSOUL_END
                        && command.generation == endingGeneration);
        nightsoulGeneration++;
        markSkillUsed(
                currentTime, simulator.getApplicableBuffs(this));
    }

    private void tonicShot(
            CombatSimulator simulator,
            boolean holdAttack) {
        double castTime = simulator.getCurrentTime();
        long generation = nightsoulGeneration;
        int releaseFrame = holdAttack ? 1 : 3;
        queueHit(simulator, new PendingHit(
                castTime + releaseFrame * FRAME,
                HitKind.TONIC,
                0,
                Element.ANEMO,
                generation,
                null));
        if (holdAttack
                && constellation >= 6
                && validatedRandomDraw(
                        c6Random, "C6 extra shot")
                        < getTalentValue(
                                "C6 Extra Shot Chance", 0.5)) {
            queueHit(simulator, new PendingHit(
                    castTime + (releaseFrame + 1) * FRAME,
                    HitKind.C6_TONIC,
                    0,
                    Element.ANEMO,
                    generation,
                    null));
        }
        simulator.advanceTime(54.0 * FRAME);
    }

    private void burst(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        StatsContainer snapshot = captureLiveStats(castTime);
        markBurstCooldownUsed(
                castTime, simulator.getApplicableBuffs(this));
        queueCommand(simulator, new PendingCommand(
                castTime + 4.0 * FRAME,
                CommandKind.BURST_ENERGY,
                0L,
                0.0));
        queueCommand(simulator, new PendingCommand(
                castTime + 41.0 * FRAME,
                CommandKind.SEDATION_DETECT,
                0L,
                0.0));
        queueHit(simulator, new PendingHit(
                castTime + 41.0 * FRAME,
                HitKind.BURST,
                0,
                Element.ANEMO,
                0L,
                snapshot));
        if (constellation >= 4) {
            c4ExpirationTime = castTime
                    + getTalentValue("C4 Duration", 15.0);
        }
        simulator.advanceTime(
                (isNightsoulBlessingActive(castTime) ? 79.0 : 95.0)
                        * FRAME);
    }

    private void detectSedationMark(CombatSimulator simulator) {
        if (simulator.getEnemy() == null) {
            return;
        }
        Element selected = null;
        for (Element candidate : SEDATION_PRIORITY) {
            if (simulator.getEnemy().getAuraUnits(
                    candidate, simulator.getCurrentTime()) > 0.0) {
                selected = candidate;
                break;
            }
        }
        if (selected == null) {
            return;
        }
        queueHit(simulator, new PendingHit(
                simulator.getCurrentTime() + 38.0 * FRAME,
                HitKind.SEDATION_MARK,
                0,
                selected,
                0L,
                captureLiveStats(simulator.getCurrentTime())));
    }

    private void onAcceptedPrimaryTonic(
            CombatSimulator simulator,
            double hitTime) {
        if (!particleGenerated) {
            particleGenerated = true;
            double draw = validatedRandomDraw(
                    particleRandom, "particle count");
            double count = getTalentValue("Particle Count", 4.0);
            if (draw < getTalentValue(
                    "Particle Bonus Chance", 0.3)) {
                count += getTalentValue("Particle Bonus Count", 1.0);
            }
            queueCommand(simulator, new PendingCommand(
                    hitTime + getTalentValue(
                            "Particle Travel Frames", 100.0) * FRAME,
                    CommandKind.PARTICLE,
                    nightsoulGeneration,
                    count));
        }
        if (constellation >= 1
                && hitTime + EPSILON >= nextC1EnergyTime) {
            receiveFlatEnergy(getTalentValue("C1 Flat Energy", 6.0));
            nextC1EnergyTime = hitTime
                    + getTalentValue("C1 Cooldown", 8.0);
        }
    }

    private void resolveHit(
            CombatSimulator simulator,
            PendingHit hit) {
        switch (hit.kind) {
            case NORMAL:
                performHit(
                        simulator,
                        hit,
                        "Rite of Dispelling Winds N" + (hit.index + 1),
                        getTalentValue(
                                "N" + (hit.index + 1),
                                NORMAL_T9[hit.index]),
                        StatType.NORMAL_ATTACK_DMG_BONUS,
                        ActionType.NORMAL,
                        ICDType.Standard,
                        ICDTag.NormalAttack,
                        1.0,
                        false);
                break;
            case CHARGED:
                performHit(
                        simulator,
                        hit,
                        "Rite of Dispelling Winds Charged Attack",
                        getTalentValue("Charged Attack", 2.499680),
                        StatType.CHARGED_ATTACK_DMG_BONUS,
                        ActionType.CHARGE,
                        ICDType.None,
                        ICDTag.None,
                        1.0,
                        false);
                break;
            case TONIC:
                if (hit.generation == nightsoulGeneration
                        && isNightsoulBlessingActive(hit.time)) {
                    performTonic(simulator, hit, false);
                }
                break;
            case C6_TONIC:
                if (hit.generation == nightsoulGeneration
                        && isNightsoulBlessingActive(hit.time)) {
                    performTonic(simulator, hit, true);
                }
                break;
            case BURST:
                performHit(
                        simulator,
                        hit,
                        "Compound Sedation Field",
                        burstValue("Compound Sedation Field", 8.644160,
                                10.169600),
                        StatType.BURST_DMG_BONUS,
                        ActionType.BURST,
                        ICDType.None,
                        ICDTag.None,
                        1.0,
                        false);
                break;
            case SEDATION_MARK:
                performHit(
                        simulator,
                        hit,
                        "Sedation Mark",
                        burstValue("Sedation Mark", 1.852320, 2.179200),
                        StatType.BURST_DMG_BONUS,
                        ActionType.BURST,
                        ICDType.None,
                        ICDTag.ElementalBurst,
                        1.0,
                        false);
                break;
            default:
                throw new IllegalStateException(
                        "Unknown Ifa hit kind " + hit.kind);
        }
    }

    private void performTonic(
            CombatSimulator simulator,
            PendingHit hit,
            boolean c6) {
        if (simulator.getEnemy() == null) {
            return;
        }
        double multiplier = c6
                ? getTalentValue("C6 Extra Shot", 1.2)
                : skillValue();
        performHit(
                simulator,
                hit,
                c6 ? "Tonic Shot C6" : "Tonic Shot",
                multiplier,
                StatType.NORMAL_ATTACK_DMG_BONUS,
                ActionType.NORMAL,
                ICDType.None,
                ICDTag.ElementalSkill,
                nextTonicGauge(hit.time),
                !c6);
    }

    private void performHit(
            CombatSimulator simulator,
            PendingHit hit,
            String displayName,
            double multiplier,
            StatType bonusStat,
            ActionType actionType,
            ICDType icdType,
            ICDTag icdTag,
            double gauge,
            boolean primaryTonic) {
        if (simulator.getEnemy() == null) {
            return;
        }
        AttackAction action = new AttackAction(
                displayName,
                multiplier,
                hit.element,
                StatType.BASE_ATK,
                bonusStat,
                0.0,
                actionType);
        action.setICD(icdType, icdTag, gauge);
        action.setCountsAsBurstDmg(actionType == ActionType.BURST);
        StatsContainer snapshot = hit.snapshot == null
                ? captureLiveStats(simulator.getCurrentTime())
                : hit.snapshot.merge(null);
        action.setStatSnapshot(snapshot);
        resolvingAction = action;
        resolvingPrimaryTonic = primaryTonic;
        try {
            simulator.performActionWithoutTimeAdvance(characterId, action);
        } finally {
            resolvingAction = null;
            resolvingPrimaryTonic = false;
        }
    }

    private double nextTonicGauge(double currentTime) {
        double reset = getTalentValue("Tonic ICD Reset", 12.0);
        if (currentTime - tonicLastApplicationTime + EPSILON >= reset) {
            tonicLastApplicationTime = currentTime;
            tonicSuppressedHits = 0;
            return 1.0;
        }
        tonicSuppressedHits++;
        if (tonicSuppressedHits >= 2) {
            tonicLastApplicationTime = currentTime;
            tonicSuppressedHits = 0;
            return 1.0;
        }
        return 0.0;
    }

    private double skillValue() {
        boolean c3 = constellation >= 3;
        return getTalentValue(
                c3 ? "Tonic Shot C3" : "Tonic Shot",
                c3 ? 2.667200 : 2.267120);
    }

    private double burstValue(
            String key,
            double talentNine,
            double talentTwelve) {
        boolean c5 = constellation >= 5;
        return getTalentValue(
                c5 ? key + " C5" : key,
                c5 ? talentTwelve : talentNine);
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

    private static double validatedRandomDraw(
            DoubleSupplier source,
            String purpose) {
        double draw = source.getAsDouble();
        if (!Double.isFinite(draw) || draw < 0.0 || draw >= 1.0) {
            throw new IllegalStateException(
                    "Ifa " + purpose + " random draw must be in [0, 1)");
        }
        return draw;
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
        long scheduledGeneration = eventGeneration;
        schedule(simulator, hit.time, activeSimulator -> {
            if (scheduledGeneration != eventGeneration
                    || !pendingHits.remove(hit)) {
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
        long scheduledGeneration = eventGeneration;
        schedule(simulator, command.time, activeSimulator -> {
            if (scheduledGeneration != eventGeneration
                    || !pendingCommands.remove(command)) {
                return;
            }
            switch (command.kind) {
                case NIGHTSOUL_END:
                    if (command.generation == nightsoulGeneration) {
                        exitNightsoulBlessing(
                                activeSimulator, command.time);
                    }
                    break;
                case BURST_ENERGY:
                    spendBurstEnergy(command.time);
                    break;
                case SEDATION_DETECT:
                    detectSedationMark(activeSimulator);
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
                            "Unknown Ifa command " + command.kind);
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
        TONIC,
        C6_TONIC,
        BURST,
        SEDATION_MARK
    }

    private enum CommandKind {
        NIGHTSOUL_END,
        BURST_ENERGY,
        SEDATION_DETECT,
        PARTICLE
    }

    private static final class PendingHit {
        private final double time;
        private final HitKind kind;
        private final int index;
        private final Element element;
        private final long generation;
        private final StatsContainer snapshot;

        private PendingHit(
                double time,
                HitKind kind,
                int index,
                Element element,
                long generation,
                StatsContainer snapshot) {
            this.time = time;
            this.kind = kind;
            this.index = index;
            this.element = element;
            this.generation = generation;
            this.snapshot = snapshot == null
                    ? null : snapshot.merge(null);
        }

        private PendingHit copy() {
            return new PendingHit(
                    time, kind, index, element, generation, snapshot);
        }
    }

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

    private static final class IfaState implements State {
        private final Ifa owner;
        private final int normalAttackStep;
        private final boolean nightsoulBlessing;
        private final long nightsoulGeneration;
        private final double nightsoulStartTime;
        private final double nightsoulExpirationTime;
        private final boolean particleGenerated;
        private final double nextC1EnergyTime;
        private final double tonicLastApplicationTime;
        private final int tonicSuppressedHits;
        private final double c4ExpirationTime;
        private final long eventGeneration;
        private final List<PendingHit> pendingHits;
        private final List<PendingCommand> pendingCommands;

        private IfaState(
                Ifa owner,
                int normalAttackStep,
                boolean nightsoulBlessing,
                long nightsoulGeneration,
                double nightsoulStartTime,
                double nightsoulExpirationTime,
                boolean particleGenerated,
                double nextC1EnergyTime,
                double tonicLastApplicationTime,
                int tonicSuppressedHits,
                double c4ExpirationTime,
                long eventGeneration,
                List<PendingHit> pendingHits,
                List<PendingCommand> pendingCommands) {
            this.owner = owner;
            this.normalAttackStep = normalAttackStep;
            this.nightsoulBlessing = nightsoulBlessing;
            this.nightsoulGeneration = nightsoulGeneration;
            this.nightsoulStartTime = nightsoulStartTime;
            this.nightsoulExpirationTime = nightsoulExpirationTime;
            this.particleGenerated = particleGenerated;
            this.nextC1EnergyTime = nextC1EnergyTime;
            this.tonicLastApplicationTime = tonicLastApplicationTime;
            this.tonicSuppressedHits = tonicSuppressedHits;
            this.c4ExpirationTime = c4ExpirationTime;
            this.eventGeneration = eventGeneration;
            this.pendingHits = copyHits(pendingHits);
            this.pendingCommands = copyCommands(pendingCommands);
        }
    }
}

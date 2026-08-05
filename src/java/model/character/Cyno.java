package model.character;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.DoubleSupplier;

import mechanics.buff.Buff;
import mechanics.data.TalentDataManager;
import mechanics.data.TalentDataSource;
import mechanics.energy.ParticleType;
import mechanics.reaction.ReactionResult;
import model.entity.ArtifactSet;
import model.entity.Character;
import model.entity.FormStateProvider;
import model.entity.ReactionAwareCharacter;
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
 * Cyno's stationary single-target Pactsworn Pathclearer kit through C6.
 *
 * <p>Timings and values follow pinned gcsim {@code ef41805d} and maintained
 * KQM Cyno evidence. Endseer timing, Judication, Duststalker Bolts, form
 * extension, and C1-C6 are represented with character-owned rollback state.</p>
 *
 * <p>Target geometry, hitlag, interruption resistance, player damage,
 * charged-attack stamina, and the Witch/Stellar variants are outside this
 * fixed-target slice.</p>
 */
public final class Cyno extends Character implements
        FormStateProvider,
        ReactionAwareCharacter,
        SimulatorInitializedCharacterEffect,
        SnapshotAwareCharacterEffect,
        SwitchAwareCharacter {
    private static final double FRAME = 1.0 / 60.0;
    private static final double EPSILON = 1e-9;
    private static final int[][] NORMAL_HIT_FRAMES = {
        { 14 }, { 17 }, { 13, 22 }, { 27 }
    };
    private static final int[] NORMAL_DURATIONS = { 15, 22, 27, 58 };
    private static final String[][] NORMAL_KEYS = {
        { "N1" }, { "N2" }, { "N3-1", "N3-2" }, { "N4" }
    };
    private static final double[][] NORMAL_MULTIPLIERS = {
        { 0.904961 }, { 0.880408 },
        { 0.538417, 0.538417 }, { 1.394271 }
    };
    private static final int[][] FORM_NORMAL_HIT_FRAMES = {
        { 12 }, { 14 }, { 18 }, { 5, 14 }, { 40 }
    };
    private static final int[] FORM_NORMAL_DURATIONS = {
        16, 31, 41, 27, 62
    };
    private static final String[][] FORM_NORMAL_KEYS = {
        { "Pactsworn N1" }, { "Pactsworn N2" },
        { "Pactsworn N3" },
        { "Pactsworn N4-1", "Pactsworn N4-2" },
        { "Pactsworn N5" }
    };
    private static final double[][] FORM_NORMAL_MULTIPLIERS = {
        { 1.438227 }, { 1.515125 }, { 1.922339 },
        { 0.94973, 0.94973 }, { 2.403891 }
    };

    private final DoubleSupplier particleDrawSource;
    private CombatSimulator initializedSimulator;
    private int normalAttackStep;
    private int formNormalAttackStep;
    private long formGeneration;
    private double formStartTime = Double.NEGATIVE_INFINITY;
    private double formExpirationTime = Double.NEGATIVE_INFINITY;
    private double endseerStartTime = Double.NEGATIVE_INFINITY;
    private double endseerExpirationTime = Double.NEGATIVE_INFINITY;
    private double c1ExpirationTime = Double.NEGATIVE_INFINITY;
    private int c2Stacks;
    private double c2ExpirationTime = Double.NEGATIVE_INFINITY;
    private double c2NextTriggerTime = Double.NEGATIVE_INFINITY;
    private int c4TriggerCount;
    private int c6Stacks;
    private double c6ExpirationTime = Double.NEGATIVE_INFINITY;
    private double c6NextTriggerTime = Double.NEGATIVE_INFINITY;
    private final List<Double> particleDrawTape = new ArrayList<>();
    private int particleDrawCursor;
    private List<PendingHit> pendingHits = new ArrayList<>();
    private List<PendingCommand> pendingCommands = new ArrayList<>();

    /** Constructs repository-default C6 Cyno. */
    public Cyno(Weapon weapon, ArtifactSet artifacts) {
        this(weapon, artifacts, TalentDataManager.getInstance(), 6,
                Math::random);
    }

    /** Constructs Cyno at an explicit constellation. */
    public Cyno(Weapon weapon, ArtifactSet artifacts, int constellation) {
        this(weapon, artifacts, TalentDataManager.getInstance(), constellation,
                Math::random);
    }

    /** Constructs Cyno with explicit constellation and particle randomness. */
    public Cyno(
            Weapon weapon,
            ArtifactSet artifacts,
            int constellation,
            DoubleSupplier particleDrawSource) {
        this(weapon, artifacts, TalentDataManager.getInstance(), constellation,
                particleDrawSource);
    }

    /** Constructs Cyno with injectable talent data and particle randomness. */
    public Cyno(
            Weapon weapon,
            ArtifactSet artifacts,
            TalentDataSource talentData,
            int constellation,
            DoubleSupplier particleDrawSource) {
        super(talentData);
        if (constellation < 0 || constellation > 6) {
            throw new IllegalArgumentException(
                    "Cyno constellation must be between 0 and 6");
        }
        if (particleDrawSource == null) {
            throw new IllegalArgumentException(
                    "Cyno particle draw source is required");
        }
        name = "Cyno";
        characterId = CharacterId.CYNO;
        element = Element.ELECTRO;
        this.weapon = weapon;
        this.artifacts = new ArtifactSet[] { artifacts };
        this.constellation = constellation;
        this.particleDrawSource = particleDrawSource;
        baseStats.set(StatType.BASE_HP,
                getTalentValue("Base HP", 12491.0));
        baseStats.set(StatType.BASE_ATK,
                getTalentValue("Base ATK", 318.0));
        baseStats.set(StatType.BASE_DEF,
                getTalentValue("Base DEF", 859.0));
        baseStats.add(StatType.CRIT_DMG,
                getTalentValue("Ascension CRIT DMG", 0.384));
        setSkillCD(7.5);
        setBurstCD(20.0);
    }

    /** Binds Cyno's listeners and delayed state to one simulator. */
    @Override
    public void initializeForSimulator(CombatSimulator simulator) {
        if (simulator == null) {
            throw new IllegalArgumentException("Cyno simulator is required");
        }
        if (initializedSimulator != null
                && initializedSimulator != simulator) {
            throw new IllegalStateException(
                    "Cyno cannot be reused across simulators");
        }
        if (initializedSimulator == simulator) {
            return;
        }
        if (!simulator.getPartyMembers().contains(this)) {
            throw new IllegalArgumentException(
                    "Cyno must belong to the target simulator party");
        }
        initializedSimulator = simulator;
        simulator.addReactionListener(this);
        simulator.addDamageListener((actor, action, damage, time) ->
                handleResolvedDamage(
                        simulator, actor, action, damage, time));
    }

    /** Captures all Cyno-owned form counters and delayed work. */
    @Override
    public State captureCharacterState() {
        return new CynoState(
                this,
                normalAttackStep,
                formNormalAttackStep,
                formGeneration,
                formStartTime,
                formExpirationTime,
                endseerStartTime,
                endseerExpirationTime,
                c1ExpirationTime,
                c2Stacks,
                c2ExpirationTime,
                c2NextTriggerTime,
                c4TriggerCount,
                c6Stacks,
                c6ExpirationTime,
                c6NextTriggerTime,
                particleDrawCursor,
                pendingHits,
                pendingCommands);
    }

    /** Accepts state captured from this exact Cyno instance only. */
    @Override
    public boolean acceptsCharacterState(State state) {
        return state instanceof CynoState
                && ((CynoState) state).owner == this;
    }

    /** Restores Cyno state and reconstructs each future event once. */
    @Override
    public void restoreCharacterState(
            State state,
            CombatSimulator simulator) {
        if (!acceptsCharacterState(state)) {
            throw new IllegalArgumentException("Unexpected Cyno state");
        }
        initializeForSimulator(simulator);
        CynoState restored = (CynoState) state;
        normalAttackStep = restored.normalAttackStep;
        formNormalAttackStep = restored.formNormalAttackStep;
        formGeneration = restored.formGeneration;
        formStartTime = restored.formStartTime;
        formExpirationTime = restored.formExpirationTime;
        endseerStartTime = restored.endseerStartTime;
        endseerExpirationTime = restored.endseerExpirationTime;
        c1ExpirationTime = restored.c1ExpirationTime;
        c2Stacks = restored.c2Stacks;
        c2ExpirationTime = restored.c2ExpirationTime;
        c2NextTriggerTime = restored.c2NextTriggerTime;
        c4TriggerCount = restored.c4TriggerCount;
        c6Stacks = restored.c6Stacks;
        c6ExpirationTime = restored.c6ExpirationTime;
        c6NextTriggerTime = restored.c6NextTriggerTime;
        particleDrawCursor = restored.particleDrawCursor;
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

    /** Returns Cyno's 80-Energy Burst cost. */
    @Override
    public double getEnergyCost() {
        return getTalentValue("Energy Cost", 80.0);
    }

    /** Reports the half-open Pactsworn form interval. */
    @Override
    public boolean isFormActive(double currentTime) {
        return currentTime >= formStartTime
                && currentTime < formExpirationTime;
    }

    /** Applies form EM and active C1/C2 bonuses at the queried time. */
    @Override
    public void applyPassive(StatsContainer stats) {
        double currentTime = initializedSimulator == null
                ? Double.NEGATIVE_INFINITY
                : initializedSimulator.getCurrentTime();
        if (isFormActive(currentTime)) {
            stats.add(StatType.ELEMENTAL_MASTERY,
                    getTalentValue("Burst Elemental Mastery", 100.0));
        }
        if (constellation >= 1 && currentTime < c1ExpirationTime) {
            stats.add(StatType.ATK_SPD,
                    getTalentValue("C1 Normal Attack Speed", 0.20));
        }
        if (constellation >= 2 && currentTime < c2ExpirationTime) {
            stats.add(StatType.ELECTRO_DMG_BONUS,
                    c2Stacks * getTalentValue(
                            "C2 Electro DMG Per Stack", 0.10));
        }
    }

    /** Ends the Burst form and resets both Normal strings on switch. */
    @Override
    public void onSwitchOut(CombatSimulator simulator) {
        formExpirationTime = Math.min(
                formExpirationTime, simulator.getCurrentTime());
        formGeneration++;
        normalAttackStep = 0;
        formNormalAttackStep = 0;
        endseerStartTime = Double.NEGATIVE_INFINITY;
        endseerExpirationTime = Double.NEGATIVE_INFINITY;
    }

    /** Restores both Normal strings to their first step on entry. */
    @Override
    public void onSwitchIn(CombatSimulator simulator) {
        normalAttackStep = 0;
        formNormalAttackStep = 0;
    }

    /** Returns the current form expiration timestamp for diagnostics. */
    public double getFormExpirationTime() {
        return formExpirationTime;
    }

    /** Returns whether the Endseer window is open at the supplied time. */
    public boolean isEndseerActive(double currentTime) {
        return currentTime >= endseerStartTime
                && currentTime < endseerExpirationTime;
    }

    /** Returns current unexpired C2 stacks. */
    public int getC2Stacks(double currentTime) {
        return currentTime < c2ExpirationTime ? c2Stacks : 0;
    }

    /** Returns the number of C4 team-Energy triggers in this form. */
    public int getC4TriggerCount() {
        return c4TriggerCount;
    }

    /** Returns current unexpired C6 Duststalker Bolt stacks. */
    public int getC6Stacks(double currentTime) {
        return currentTime < c6ExpirationTime ? c6Stacks : 0;
    }

    /** Grants team Energy for Cyno-owned Electro reactions during the form. */
    @Override
    public void onReaction(
            ReactionResult result,
            Character source,
            double time,
            CombatSimulator simulator) {
        if (simulator != initializedSimulator
                || constellation < 4
                || source != this
                || result == null
                || !isFormActive(time)
                || c4TriggerCount >= (int) getTalentValue(
                        "C4 Max Triggers", 5.0)
                || !isC4Reaction(result)) {
            return;
        }
        c4TriggerCount++;
        double energy = getTalentValue("C4 Team Energy", 3.0);
        for (Character member : simulator.getPartyMembers()) {
            if (member != this) {
                member.receiveFlatEnergy(energy);
            }
        }
    }

    /** Dispatches Cyno's represented action set. */
    @Override
    public void onAction(
            CharacterActionRequest request,
            CombatSimulator simulator) {
        if (request == null) {
            throw new IllegalArgumentException("Cyno action is required");
        }
        initializeForSimulator(simulator);
        if (request.getKey() != CharacterActionKey.NORMAL) {
            normalAttackStep = 0;
            formNormalAttackStep = 0;
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
                if (request.getSkillMode() != SkillActionMode.PRESS) {
                    throw new IllegalArgumentException(
                            "Cyno supports Press Skill only");
                }
                elementalSkill(simulator);
                break;
            case BURST:
                sacredRite(simulator);
                break;
            default:
                throw new IllegalArgumentException(
                        "Unsupported action for Cyno: " + request.getKey());
        }
    }

    private void normalAttack(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        boolean form = isFormActive(castTime);
        int step = form ? formNormalAttackStep : normalAttackStep;
        int[][] hitFrames = form
                ? FORM_NORMAL_HIT_FRAMES : NORMAL_HIT_FRAMES;
        double maximumHitDelay = 0.0;
        for (int hit = 0; hit < hitFrames[step].length; hit++) {
            double hitDelay = hitFrames[step][hit] * FRAME;
            if (form) {
                hitDelay = resolveNormalDuration(simulator, hitDelay);
                maximumHitDelay = Math.max(maximumHitDelay, hitDelay);
            }
            queueHit(simulator, new PendingHit(
                    castTime + hitDelay,
                    form ? HitKind.FORM_NORMAL : HitKind.NORMAL,
                    step,
                    hit,
                    formGeneration));
        }
        if (form) {
            preserveFormThrough(castTime, maximumHitDelay);
        }
        if (form) {
            formNormalAttackStep = (formNormalAttackStep + 1) % 5;
        } else {
            normalAttackStep = (normalAttackStep + 1) % 4;
        }
        double duration = (form
                ? FORM_NORMAL_DURATIONS[step] : NORMAL_DURATIONS[step])
                * FRAME;
        simulator.advanceTime(form
                ? resolveNormalDuration(simulator, duration) : duration);
    }

    private void chargedAttack(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        boolean form = isFormActive(castTime);
        double delay = (form ? 27.0 : 24.0) * FRAME;
        double duration = (form ? 65.0 : 63.0) * FRAME;
        if (form) {
            preserveFormThrough(castTime, delay);
        }
        queueHit(simulator, new PendingHit(
                castTime + delay,
                form ? HitKind.FORM_CHARGED : HitKind.CHARGED,
                0,
                0,
                formGeneration));
        simulator.advanceTime(duration);
    }

    private void highPlunge(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        boolean form = isFormActive(castTime);
        if (form) {
            preserveFormThrough(castTime, 48.0 * FRAME);
        }
        queueHit(simulator, new PendingHit(
                castTime + (form ? 48.0 : 46.0) * FRAME,
                form ? HitKind.FORM_PLUNGE : HitKind.PLUNGE,
                0,
                0,
                formGeneration));
        simulator.advanceTime((form ? 60.0 : 59.0) * FRAME);
    }

    private void elementalSkill(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        boolean form = isFormActive(castTime);
        boolean judication = form && isEndseerActive(castTime);
        if (form) {
            extendForm(castTime);
            preserveFormThrough(castTime, 28.0 * FRAME);
        }
        setSkillCD(form ? 3.0 : 7.5);
        markSkillUsed(castTime, simulator.getApplicableBuffs(this));
        queueHit(simulator, new PendingHit(
                castTime + (form ? 28.0 : 21.0) * FRAME,
                form ? HitKind.FORM_SKILL : HitKind.SKILL,
                judication ? 1 : 0,
                0,
                formGeneration));
        simulator.advanceTime((form ? 34.0 : 43.0) * FRAME);
    }

    private void sacredRite(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        long generation = ++formGeneration;
        markBurstCooldownUsed(
                castTime, simulator.getApplicableBuffs(this));
        queueCommand(simulator, new PendingCommand(
                castTime + 6.0 * FRAME,
                CommandKind.BURST_ENERGY,
                generation));
        queueCommand(simulator, new PendingCommand(
                castTime + 84.0 * FRAME,
                CommandKind.FORM_ACTIVATE,
                generation));
        queueCommand(simulator, new PendingCommand(
                castTime + getTalentValue(
                        "Endseer First Delay Frames", 328.0) * FRAME,
                CommandKind.ENDSEER_OPEN,
                generation));
        simulator.advanceTime(86.0 * FRAME);
    }

    private void resolveHit(CombatSimulator simulator, PendingHit hit) {
        switch (hit.kind) {
            case NORMAL:
                resolveNormal(simulator, hit, false);
                break;
            case FORM_NORMAL:
                if (hit.generation == formGeneration) {
                    resolveNormal(simulator, hit, true);
                }
                break;
            case CHARGED:
                resolveCharged(simulator, false);
                break;
            case FORM_CHARGED:
                if (hit.generation == formGeneration) {
                    resolveCharged(simulator, true);
                }
                break;
            case SKILL:
                resolveSkill(simulator, false, false);
                break;
            case FORM_SKILL:
                if (hit.generation == formGeneration) {
                    resolveSkill(simulator, true, hit.index == 1);
                }
                break;
            case PLUNGE:
                resolvePlunge(simulator, false);
                break;
            case FORM_PLUNGE:
                if (hit.generation == formGeneration) {
                    resolvePlunge(simulator, true);
                }
                break;
            default:
                throw new IllegalStateException("Unknown Cyno hit kind");
        }
    }

    private void resolveNormal(
            CombatSimulator simulator,
            PendingHit hit,
            boolean form) {
        String key = (form ? FORM_NORMAL_KEYS : NORMAL_KEYS)
                [hit.index][hit.subIndex];
        double fallback = (form
                ? FORM_NORMAL_MULTIPLIERS : NORMAL_MULTIPLIERS)
                [hit.index][hit.subIndex];
        String talentKey = form && constellation >= 3
                ? key + " C3" : key;
        double c3Fallback = fallback;
        if (form && constellation >= 3) {
            c3Fallback = c3NormalFallback(hit.index, hit.subIndex);
        }
        AttackAction action = attack(
                form ? "Pactsworn Pathclearer " + key
                        : "Invoker's Spear " + key,
                getTalentValue(talentKey, c3Fallback),
                form ? Element.ELECTRO : Element.PHYSICAL,
                StatType.NORMAL_ATTACK_DMG_BONUS,
                ActionType.NORMAL,
                ICDType.Standard,
                ICDTag.NormalAttack,
                form ? 1.0 : 0.0);
        StatsContainer stats = captureLiveStats(simulator.getCurrentTime());
        if (form) {
            stats.add(StatType.FLAT_DMG_BONUS,
                    stats.get(StatType.ELEMENTAL_MASTERY)
                            * getTalentValue(
                                    "A4 Pactsworn Normal EM Ratio", 1.5));
        }
        action.setStatSnapshot(stats);
        simulator.performActionWithoutTimeAdvance(characterId, action);
    }

    private void resolveCharged(
            CombatSimulator simulator,
            boolean form) {
        String key = form && constellation >= 3
                ? "Pactsworn Charged C3"
                : form ? "Pactsworn Charged" : "Charged Attack";
        double fallback = form
                ? constellation >= 3 ? 2.2795 : 1.8565
                : 2.24834;
        AttackAction action = attack(
                form ? "Pactsworn Pathclearer Charged Attack"
                        : "Invoker's Spear Charged Attack",
                getTalentValue(key, fallback),
                form ? Element.ELECTRO : Element.PHYSICAL,
                StatType.CHARGED_ATTACK_DMG_BONUS,
                ActionType.CHARGE,
                ICDType.None,
                ICDTag.ChargedAttack,
                form ? 1.0 : 0.0);
        action.setStatSnapshot(captureLiveStats(simulator.getCurrentTime()));
        simulator.performActionWithoutTimeAdvance(characterId, action);
    }

    private void resolveSkill(
            CombatSimulator simulator,
            boolean form,
            boolean judication) {
        String key;
        double fallback;
        if (form) {
            key = constellation >= 5
                    ? "Mortuary Rite C5" : "Mortuary Rite";
            fallback = constellation >= 5 ? 3.136 : 2.6656;
        } else {
            key = constellation >= 5 ? "Skill C5" : "Skill";
            fallback = constellation >= 5 ? 2.608 : 2.2168;
        }
        AttackAction action = attack(
                form ? "Mortuary Rite"
                        : "Secret Rite: Chasmic Soulfarer",
                getTalentValue(key, fallback),
                Element.ELECTRO,
                StatType.SKILL_DMG_BONUS,
                ActionType.SKILL,
                ICDType.None,
                ICDTag.ElementalSkill,
                1.0);
        if (judication) {
            action.addBonusStat(
                    StatType.SKILL_DMG_BONUS,
                    getTalentValue("A1 Skill DMG Bonus", 0.35));
        }
        action.setStatSnapshot(captureLiveStats(simulator.getCurrentTime()));
        simulator.performActionWithoutTimeAdvance(characterId, action);
        double particleCount = form
                ? nextParticleDraw() < 0.33 ? 2.0 : 1.0
                : getTalentValue("Skill Expected Particles", 3.0);
        if (simulator.getEnemy() != null && particleCount > 0.0) {
            simulator.getEnergyDistributor().distributeParticles(
                    Element.ELECTRO,
                    particleCount,
                    ParticleType.PARTICLE);
        }
        if (!form) {
            return;
        }
        if (judication) {
            endseerExpirationTime = simulator.getCurrentTime();
            refreshC1(simulator.getCurrentTime());
            grantC6Stacks(simulator.getCurrentTime());
            for (int index = 0; index < 3; index++) {
                resolveDuststalkerBolt(simulator, false);
            }
        }
    }

    private void activateForm(
            CombatSimulator simulator,
            long generation) {
        if (generation != formGeneration) {
            return;
        }
        double currentTime = simulator.getCurrentTime();
        formStartTime = currentTime;
        formExpirationTime = currentTime
                + getTalentValue("Burst Form Duration", 10.0);
        formNormalAttackStep = 0;
        c4TriggerCount = 0;
        refreshC1(currentTime);
        grantC6Stacks(currentTime);
        AttackAction action = attack(
                "Sacred Rite: Wolf's Swiftness",
                0.0,
                Element.ELECTRO,
                StatType.BURST_DMG_BONUS,
                ActionType.BURST,
                ICDType.None,
                ICDTag.ElementalBurst,
                0.0);
        action.setStatSnapshot(captureLiveStats(currentTime));
        simulator.performActionWithoutTimeAdvance(characterId, action);
    }

    private void resolvePlunge(
            CombatSimulator simulator,
            boolean form) {
        String key = form && constellation >= 3
                ? "Pactsworn High Plunge C3"
                : form ? "Pactsworn High Plunge" : "High Plunge";
        double fallback = form && constellation >= 3
                ? 3.601998 : 2.933586;
        AttackAction action = attack(
                form ? "Pactsworn Pathclearer High Plunge"
                        : "Invoker's Spear High Plunge",
                getTalentValue(key, fallback),
                form ? Element.ELECTRO : Element.PHYSICAL,
                StatType.PLUNGING_ATTACK_DMG_BONUS,
                ActionType.PLUNGE,
                ICDType.None,
                ICDTag.PlungeAttack,
                form ? 1.0 : 0.0);
        action.setStatSnapshot(captureLiveStats(simulator.getCurrentTime()));
        simulator.performActionWithoutTimeAdvance(characterId, action);
    }

    private void openEndseer(
            CombatSimulator simulator,
            long generation) {
        double currentTime = simulator.getCurrentTime();
        if (generation != formGeneration || !isFormActive(currentTime)) {
            return;
        }
        endseerStartTime = currentTime;
        endseerExpirationTime = currentTime
                + getTalentValue("Endseer Duration Frames", 84.0) * FRAME;
        double nextTime = currentTime
                + getTalentValue("Endseer Interval Frames", 234.0) * FRAME;
        if (nextTime < formStartTime
                + getTalentValue("Burst Form Max Duration", 18.0)) {
            queueCommand(simulator, new PendingCommand(
                    nextTime,
                    CommandKind.ENDSEER_OPEN,
                    generation));
        }
    }

    private void extendForm(double currentTime) {
        double maximum = formStartTime
                + getTalentValue("Burst Form Max Duration", 18.0);
        formExpirationTime = Math.min(
                maximum,
                formExpirationTime + getTalentValue(
                        "Burst Form Extension", 4.0));
    }

    private void preserveFormThrough(
            double castTime,
            double hitDelay) {
        double requiredExpiration = castTime + hitDelay + FRAME;
        if (formExpirationTime > castTime
                && formExpirationTime < requiredExpiration) {
            formExpirationTime = requiredExpiration;
        }
    }

    private void handleResolvedDamage(
            CombatSimulator simulator,
            Character actor,
            AttackAction action,
            double damage,
            double time) {
        if (actor != this
                || action == null
                || damage <= 0.0
                || action.getActionType() != ActionType.NORMAL) {
            return;
        }
        if (constellation >= 2 && time + EPSILON >= c2NextTriggerTime) {
            if (time >= c2ExpirationTime) {
                c2Stacks = 0;
            }
            c2Stacks = Math.min(
                    (int) getTalentValue("C2 Max Stacks", 5.0),
                    c2Stacks + 1);
            c2ExpirationTime = time
                    + getTalentValue("C2 Duration", 4.0);
            c2NextTriggerTime = time
                    + getTalentValue("C2 Trigger Cooldown", 0.10);
        }
        if (!isFormActive(time)
                || action.getElement() != Element.ELECTRO) {
            return;
        }
        if (constellation >= 6
                && time + EPSILON >= c6NextTriggerTime
                && getC6Stacks(time) > 0) {
            c6Stacks--;
            c6NextTriggerTime = time
                    + getTalentValue("C6 Trigger Cooldown", 0.40);
            resolveDuststalkerBolt(simulator, true);
        }
    }

    private void resolveDuststalkerBolt(
            CombatSimulator simulator,
            boolean c6) {
        StatsContainer stats = captureLiveStats(simulator.getCurrentTime());
        stats.add(StatType.FLAT_DMG_BONUS,
                stats.get(StatType.ELEMENTAL_MASTERY)
                        * getTalentValue(
                                "A4 Duststalker Bolt EM Ratio", 2.5));
        AttackAction bolt = attack(
                c6 ? "C6 Duststalker Bolt" : "Duststalker Bolt",
                getTalentValue("Duststalker Bolt ATK Ratio", 1.0),
                Element.ELECTRO,
                StatType.SKILL_DMG_BONUS,
                ActionType.SKILL,
                ICDType.Standard,
                c6 ? ICDTag.Cyno_C6_DuststalkerBolt
                        : ICDTag.Cyno_DuststalkerBolt,
                1.0);
        bolt.setStatSnapshot(stats);
        simulator.performActionWithoutTimeAdvance(characterId, bolt);
    }

    private void refreshC1(double currentTime) {
        if (constellation >= 1) {
            c1ExpirationTime = currentTime
                    + getTalentValue("C1 Duration", 10.0);
        }
    }

    private void grantC6Stacks(double currentTime) {
        if (constellation < 6) {
            return;
        }
        if (currentTime >= c6ExpirationTime) {
            c6Stacks = 0;
        }
        c6Stacks = Math.min(
                (int) getTalentValue("C6 Max Stacks", 8.0),
                c6Stacks + (int) getTalentValue(
                        "C6 Stacks Per Activation", 4.0));
        c6ExpirationTime = currentTime
                + getTalentValue("C6 Duration", 8.0);
    }

    private boolean isC4Reaction(ReactionResult result) {
        ReactionResult.Kind kind = result.getKind();
        return kind == ReactionResult.Kind.ELECTRO_CHARGED
                || kind == ReactionResult.Kind.LUNAR_CHARGED
                || kind == ReactionResult.Kind.SUPERCONDUCT
                || kind == ReactionResult.Kind.OVERLOAD
                || kind == ReactionResult.Kind.OVERLOADED
                || kind == ReactionResult.Kind.QUICKEN
                || kind == ReactionResult.Kind.AGGRAVATE
                || kind == ReactionResult.Kind.HYPERBLOOM
                || (kind == ReactionResult.Kind.SWIRL
                        && result.getRelatedElement() == Element.ELECTRO);
    }

    private double resolveNormalDuration(
            CombatSimulator simulator,
            double duration) {
        StatsContainer stats = captureLiveStats(simulator.getCurrentTime());
        double speed = Math.min(
                0.60,
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

    private double validatedDraw() {
        double draw = particleDrawSource.getAsDouble();
        if (!Double.isFinite(draw) || draw < 0.0 || draw >= 1.0) {
            throw new IllegalStateException(
                    "Cyno particle draw must be in [0, 1)");
        }
        return draw;
    }

    private double nextParticleDraw() {
        if (particleDrawCursor < particleDrawTape.size()) {
            return particleDrawTape.get(particleDrawCursor++);
        }
        double draw = validatedDraw();
        particleDrawTape.add(draw);
        particleDrawCursor++;
        return draw;
    }

    private double c3NormalFallback(int step, int hit) {
        double[][] values = {
            { 1.765924 }, { 1.860344 }, { 2.36034 },
            { 1.166124, 1.166124 }, { 2.951613 }
        };
        return values[step][hit];
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
                    if (command.generation == formGeneration) {
                        spendBurstEnergy(activeSimulator.getCurrentTime());
                    }
                    break;
                case FORM_ACTIVATE:
                    activateForm(activeSimulator, command.generation);
                    break;
                case ENDSEER_OPEN:
                    openEndseer(activeSimulator, command.generation);
                    break;
                default:
                    throw new IllegalStateException(
                            "Unknown Cyno command kind");
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
        FORM_NORMAL,
        CHARGED,
        FORM_CHARGED,
        SKILL,
        FORM_SKILL,
        PLUNGE,
        FORM_PLUNGE
    }

    private enum CommandKind {
        BURST_ENERGY,
        FORM_ACTIVATE,
        ENDSEER_OPEN
    }

    /** Immutable delayed Cyno hit description. */
    private static final class PendingHit {
        private final double time;
        private final HitKind kind;
        private final int index;
        private final int subIndex;
        private final long generation;

        private PendingHit(
                double time,
                HitKind kind,
                int index,
                int subIndex,
                long generation) {
            this.time = time;
            this.kind = kind;
            this.index = index;
            this.subIndex = subIndex;
            this.generation = generation;
        }

        private PendingHit copy() {
            return new PendingHit(
                    time, kind, index, subIndex, generation);
        }
    }

    /** Immutable delayed Cyno command description. */
    private static final class PendingCommand {
        private final double time;
        private final CommandKind kind;
        private final long generation;

        private PendingCommand(
                double time,
                CommandKind kind,
                long generation) {
            this.time = time;
            this.kind = kind;
            this.generation = generation;
        }

        private PendingCommand copy() {
            return new PendingCommand(time, kind, generation);
        }
    }

    /** Immutable owner-bound Cyno rollback payload. */
    private static final class CynoState implements State {
        private final Cyno owner;
        private final int normalAttackStep;
        private final int formNormalAttackStep;
        private final long formGeneration;
        private final double formStartTime;
        private final double formExpirationTime;
        private final double endseerStartTime;
        private final double endseerExpirationTime;
        private final double c1ExpirationTime;
        private final int c2Stacks;
        private final double c2ExpirationTime;
        private final double c2NextTriggerTime;
        private final int c4TriggerCount;
        private final int c6Stacks;
        private final double c6ExpirationTime;
        private final double c6NextTriggerTime;
        private final int particleDrawCursor;
        private final List<PendingHit> pendingHits;
        private final List<PendingCommand> pendingCommands;

        private CynoState(
                Cyno owner,
                int normalAttackStep,
                int formNormalAttackStep,
                long formGeneration,
                double formStartTime,
                double formExpirationTime,
                double endseerStartTime,
                double endseerExpirationTime,
                double c1ExpirationTime,
                int c2Stacks,
                double c2ExpirationTime,
                double c2NextTriggerTime,
                int c4TriggerCount,
                int c6Stacks,
                double c6ExpirationTime,
                double c6NextTriggerTime,
                int particleDrawCursor,
                List<PendingHit> pendingHits,
                List<PendingCommand> pendingCommands) {
            this.owner = owner;
            this.normalAttackStep = normalAttackStep;
            this.formNormalAttackStep = formNormalAttackStep;
            this.formGeneration = formGeneration;
            this.formStartTime = formStartTime;
            this.formExpirationTime = formExpirationTime;
            this.endseerStartTime = endseerStartTime;
            this.endseerExpirationTime = endseerExpirationTime;
            this.c1ExpirationTime = c1ExpirationTime;
            this.c2Stacks = c2Stacks;
            this.c2ExpirationTime = c2ExpirationTime;
            this.c2NextTriggerTime = c2NextTriggerTime;
            this.c4TriggerCount = c4TriggerCount;
            this.c6Stacks = c6Stacks;
            this.c6ExpirationTime = c6ExpirationTime;
            this.c6NextTriggerTime = c6NextTriggerTime;
            this.particleDrawCursor = particleDrawCursor;
            this.pendingHits = copyHits(pendingHits);
            this.pendingCommands = copyCommands(pendingCommands);
        }
    }
}

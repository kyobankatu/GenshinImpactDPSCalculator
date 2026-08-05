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
 * Kamisato Ayaka's stationary fixed-target Frostflake kit through C6.
 *
 * <p>Normal, Charged, and high Plunge attacks, Senho's exit application and
 * five-second infusion, Hyouka, particles, Soumetsu's nineteen cuts and final
 * Bloom, A1/A4, and representable constellations follow pinned gcsim
 * {@code ef41805d}. Random C1 and particle outcomes are replayable across
 * simulator rollback.</p>
 *
 * <p>Movement, stamina, geometry, multi-target selection, weak points,
 * low Plunge, hitlag, and defensive behavior are intentionally excluded.
 * C4 owns one expiration because this simulator has one fixed enemy.</p>
 */
public final class KamisatoAyaka extends Character implements
        SimulatorInitializedCharacterEffect,
        SnapshotAwareCharacterEffect,
        SwitchAwareCharacter,
        TargetDependentTeamEffect {
    private static final double FRAME = 1.0 / 60.0;
    private static final double EPSILON = 1e-9;
    private static final int[][] NORMAL_HIT_FRAMES = {
        { 8 }, { 10 }, { 16 }, { 8, 15, 22 }, { 27 }
    };
    private static final int[] NORMAL_DURATIONS = { 22, 20, 32, 23, 66 };
    private static final String[][] NORMAL_KEYS = {
        { "N1" }, { "N2" }, { "N3" },
        { "N4-1", "N4-2", "N4-3" }, { "N5" }
    };
    private static final double[][] NORMAL_MULTIPLIERS = {
        { 0.840070 }, { 0.894438 }, { 1.150493 },
        { 0.416061, 0.416061, 0.416061 }, { 1.436362 }
    };
    private static final int[] CHARGED_HIT_FRAMES = { 27, 33, 39 };
    private static final int BURST_FIRST_HIT_FRAME = 104;
    private static final int BURST_INTERVAL_FRAMES = 15;
    private static final int BURST_CUT_COUNT = 19;
    private static final int BURST_BLOOM_FRAME = 404;

    private final DoubleSupplier particleDrawSource;
    private final DoubleSupplier c1DrawSource;
    private final List<Double> particleDrawTape = new ArrayList<>();
    private final List<Double> c1DrawTape = new ArrayList<>();
    private CombatSimulator initializedSimulator;
    private int normalAttackStep;
    private int particleDrawCursor;
    private int c1DrawCursor;
    private double infusionExpirationTime = Double.NEGATIVE_INFINITY;
    private double a1ExpirationTime = Double.NEGATIVE_INFINITY;
    private double a4ExpirationTime = Double.NEGATIVE_INFINITY;
    private double nextParticleAllowedTime = Double.NEGATIVE_INFINITY;
    private double nextC1AllowedTime = Double.NEGATIVE_INFINITY;
    private double c4ExpirationTime = Double.NEGATIVE_INFINITY;
    private boolean c6Ready;
    private List<PendingHit> pendingHits = new ArrayList<>();
    private List<PendingCommand> pendingCommands = new ArrayList<>();

    /** Constructs repository-default C6 Ayaka with stochastic draws. */
    public KamisatoAyaka(Weapon weapon, ArtifactSet artifacts) {
        this(weapon, artifacts, TalentDataManager.getInstance(), 6,
                Math::random, Math::random);
    }

    /** Constructs Ayaka at an explicit constellation with stochastic draws. */
    public KamisatoAyaka(
            Weapon weapon,
            ArtifactSet artifacts,
            int constellation) {
        this(weapon, artifacts, TalentDataManager.getInstance(), constellation,
                Math::random, Math::random);
    }

    /** Constructs Ayaka with deterministic particle and C1 draws. */
    public KamisatoAyaka(
            Weapon weapon,
            ArtifactSet artifacts,
            int constellation,
            DoubleSupplier particleDrawSource,
            DoubleSupplier c1DrawSource) {
        this(weapon, artifacts, TalentDataManager.getInstance(), constellation,
                particleDrawSource, c1DrawSource);
    }

    /** Constructs Ayaka with injectable talent data and probability sources. */
    public KamisatoAyaka(
            Weapon weapon,
            ArtifactSet artifacts,
            TalentDataSource talentData,
            int constellation,
            DoubleSupplier particleDrawSource,
            DoubleSupplier c1DrawSource) {
        super(talentData);
        if (constellation < 0 || constellation > 6) {
            throw new IllegalArgumentException(
                    "Kamisato Ayaka constellation must be between 0 and 6");
        }
        if (particleDrawSource == null) {
            throw new IllegalArgumentException(
                    "Kamisato Ayaka particle draw source is required");
        }
        if (c1DrawSource == null) {
            throw new IllegalArgumentException(
                    "Kamisato Ayaka C1 draw source is required");
        }
        name = "Kamisato Ayaka";
        characterId = CharacterId.KAMISATO_AYAKA;
        element = Element.CRYO;
        this.weapon = weapon;
        this.artifacts = new ArtifactSet[] { artifacts };
        this.constellation = constellation;
        this.particleDrawSource = particleDrawSource;
        this.c1DrawSource = c1DrawSource;
        c6Ready = constellation >= 6;
        baseStats.set(StatType.BASE_HP,
                getTalentValue("Base HP", 12858.0));
        baseStats.set(StatType.BASE_ATK,
                getTalentValue("Base ATK", 342.0));
        baseStats.set(StatType.BASE_DEF,
                getTalentValue("Base DEF", 784.0));
        baseStats.add(StatType.CRIT_DMG,
                getTalentValue("Ascension CRIT DMG", 0.384));
        setSkillCD(getTalentValue("Skill Cooldown", 10.0));
        setBurstCD(getTalentValue("Burst Cooldown", 20.0));
    }

    /** Binds Ayaka's delayed work to exactly one simulator. */
    @Override
    public void initializeForSimulator(CombatSimulator simulator) {
        if (simulator == null) {
            throw new IllegalArgumentException(
                    "Kamisato Ayaka simulator is required");
        }
        if (!simulator.getPartyMembers().contains(this)) {
            throw new IllegalArgumentException(
                    "Kamisato Ayaka must belong to the simulator party");
        }
        if (initializedSimulator != null
                && initializedSimulator != simulator) {
            throw new IllegalStateException(
                    "Kamisato Ayaka cannot be reused across simulators");
        }
        initializedSimulator = simulator;
    }

    /** Captures all owner-local windows, draws, and reconstructable work. */
    @Override
    public State captureCharacterState() {
        return new AyakaState(
                this,
                normalAttackStep,
                particleDrawCursor,
                c1DrawCursor,
                infusionExpirationTime,
                a1ExpirationTime,
                a4ExpirationTime,
                nextParticleAllowedTime,
                nextC1AllowedTime,
                c4ExpirationTime,
                c6Ready,
                pendingHits,
                pendingCommands);
    }

    /** Accepts only state produced by this exact Ayaka instance. */
    @Override
    public boolean acceptsCharacterState(State state) {
        return state instanceof AyakaState
                && ((AyakaState) state).owner == this;
    }

    /** Restores Ayaka-owned state and re-registers future work once. */
    @Override
    public void restoreCharacterState(
            State state,
            CombatSimulator simulator) {
        if (!acceptsCharacterState(state)) {
            throw new IllegalArgumentException(
                    "Unexpected Kamisato Ayaka state");
        }
        initializeForSimulator(simulator);
        AyakaState restored = (AyakaState) state;
        normalAttackStep = restored.normalAttackStep;
        particleDrawCursor = restored.particleDrawCursor;
        c1DrawCursor = restored.c1DrawCursor;
        infusionExpirationTime = restored.infusionExpirationTime;
        a1ExpirationTime = restored.a1ExpirationTime;
        a4ExpirationTime = restored.a4ExpirationTime;
        nextParticleAllowedTime = restored.nextParticleAllowedTime;
        nextC1AllowedTime = restored.nextC1AllowedTime;
        c4ExpirationTime = restored.c4ExpirationTime;
        c6Ready = restored.c6Ready;
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

    /** Returns Soumetsu's 80-Energy cost. */
    @Override
    public double getEnergyCost() {
        return getTalentValue("Energy Cost", 80.0);
    }

    /** Applies live A1 category bonuses and A4 Cryo bonus. */
    @Override
    public void applyPassive(StatsContainer stats) {
        double currentTime = initializedSimulator == null
                ? 0.0 : initializedSimulator.getCurrentTime();
        if (currentTime + EPSILON < a1ExpirationTime) {
            double bonus = getTalentValue("A1 Normal Charged DMG Bonus", 0.30);
            stats.add(StatType.NORMAL_ATTACK_DMG_BONUS, bonus);
            stats.add(StatType.CHARGED_ATTACK_DMG_BONUS, bonus);
        }
        if (currentTime + EPSILON < a4ExpirationTime) {
            stats.add(StatType.CRYO_DMG_BONUS,
                    getTalentValue("A4 Cryo DMG Bonus", 0.18));
        }
    }

    /** Applies C4 as live fixed-target DEF reduction after Burst damage. */
    @Override
    public void applyTargetDependentTeamStats(
            StatsContainer stats,
            Character attacker,
            Enemy target,
            AttackAction action,
            double currentTime) {
        if (constellation >= 4
                && currentTime + EPSILON < c4ExpirationTime) {
            stats.add(StatType.ENEMY_DEF_REDUCTION,
                    getTalentValue("C4 DEF Reduction", 0.30));
        }
    }

    /** Resets Kabuki progression when Ayaka leaves the field. */
    @Override
    public void onSwitchOut(CombatSimulator simulator) {
        normalAttackStep = 0;
    }

    /** Returns whether Senho's Cryo weapon infusion is live. */
    public boolean isCryoInfusionActive(double currentTime) {
        return currentTime + EPSILON < infusionExpirationTime;
    }

    /** Returns whether A1's Normal and Charged window is live. */
    public boolean isA1Active(double currentTime) {
        return currentTime + EPSILON < a1ExpirationTime;
    }

    /** Returns whether A4's owner Cryo bonus is live. */
    public boolean isA4Active(double currentTime) {
        return currentTime + EPSILON < a4ExpirationTime;
    }

    /** Returns whether the fixed target remains under C4. */
    public boolean isC4Active(double currentTime) {
        return currentTime + EPSILON < c4ExpirationTime;
    }

    /** Returns whether C6 is ready to empower the next Charged Attack. */
    public boolean isC6Ready() {
        return c6Ready;
    }

    /** Returns the number of unresolved Ayaka-owned damage/application hits. */
    public int getPendingHitCount() {
        return pendingHits.size();
    }

    /** Dispatches Ayaka's represented typed action set. */
    @Override
    public void onAction(
            CharacterActionRequest request,
            CombatSimulator simulator) {
        if (request == null) {
            throw new IllegalArgumentException(
                    "Kamisato Ayaka action is required");
        }
        initializeForSimulator(simulator);
        if (request.getKey() == CharacterActionKey.SKILL
                && request.getSkillMode() != SkillActionMode.PRESS) {
            throw new IllegalArgumentException(
                    "Kamisato Ayaka supports only Press Skill");
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
                highPlunge(simulator);
                break;
            case DASH:
                senho(simulator);
                break;
            case SKILL:
                hyouka(simulator);
                break;
            case BURST:
                soumetsu(simulator);
                break;
            default:
                throw new IllegalArgumentException(
                        "Unsupported action for Kamisato Ayaka: "
                                + request.getKey());
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
                    false,
                    null));
        }
        normalAttackStep = (normalAttackStep + 1)
                % NORMAL_HIT_FRAMES.length;
        simulator.advanceTime(NORMAL_DURATIONS[step] * FRAME);
    }

    private void chargedAttack(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        boolean c6Boosted = constellation >= 6 && c6Ready;
        for (int hit = 0; hit < CHARGED_HIT_FRAMES.length; hit++) {
            queueHit(simulator, new PendingHit(
                    castTime + CHARGED_HIT_FRAMES[hit] * FRAME,
                    HitKind.CHARGED,
                    0,
                    hit,
                    c6Boosted,
                    null));
        }
        simulator.advanceTime(71.0 * FRAME);
    }

    private void highPlunge(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        queueHit(simulator, new PendingHit(
                castTime + 48.0 * FRAME,
                HitKind.HIGH_PLUNGE,
                0,
                0,
                false,
                null));
        simulator.advanceTime(74.0 * FRAME);
    }

    private void senho(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        queueHit(simulator, new PendingHit(
                castTime + 20.0 * FRAME,
                HitKind.DASH,
                0,
                0,
                false,
                null));
        simulator.advanceTime(35.0 * FRAME);
    }

    private void hyouka(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        markSkillUsed(castTime, simulator.getApplicableBuffs(this));
        a1ExpirationTime = castTime
                + getTalentValue("A1 Duration", 6.0);
        queueHit(simulator, new PendingHit(
                castTime + 33.0 * FRAME,
                HitKind.SKILL,
                0,
                0,
                false,
                null));
        simulator.advanceTime(49.0 * FRAME);
    }

    private void soumetsu(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        StatsContainer snapshot = captureLiveStats(castTime);
        markBurstCooldownUsed(castTime, simulator.getApplicableBuffs(this));
        queueCommand(simulator, new PendingCommand(
                castTime + 8.0 * FRAME,
                CommandKind.BURST_ENERGY,
                0.0));
        for (int cut = 0; cut < BURST_CUT_COUNT; cut++) {
            double time = castTime
                    + (BURST_FIRST_HIT_FRAME
                            + cut * BURST_INTERVAL_FRAMES) * FRAME;
            queueHit(simulator, new PendingHit(
                    time, HitKind.BURST_CUT, 0, cut, false, snapshot));
            if (constellation >= 2) {
                queueHit(simulator, new PendingHit(
                        time, HitKind.BURST_CUT, 1, cut, false, snapshot));
                queueHit(simulator, new PendingHit(
                        time, HitKind.BURST_CUT, 2, cut, false, snapshot));
            }
        }
        double bloomTime = castTime + BURST_BLOOM_FRAME * FRAME;
        queueHit(simulator, new PendingHit(
                bloomTime, HitKind.BURST_BLOOM, 0, 0, false, snapshot));
        if (constellation >= 2) {
            queueHit(simulator, new PendingHit(
                    bloomTime, HitKind.BURST_BLOOM, 1, 0, false, snapshot));
            queueHit(simulator, new PendingHit(
                    bloomTime, HitKind.BURST_BLOOM, 2, 0, false, snapshot));
        }
        simulator.advanceTime(125.0 * FRAME);
    }

    private void resolveHit(CombatSimulator simulator, PendingHit hit) {
        switch (hit.kind) {
            case NORMAL:
                resolveNormalHit(simulator, hit);
                break;
            case CHARGED:
                resolveChargedHit(simulator, hit);
                break;
            case HIGH_PLUNGE:
                resolvePlungeHit(simulator, hit);
                break;
            case DASH:
                resolveDashHit(simulator, hit);
                break;
            case SKILL:
                resolveSkillHit(simulator, hit);
                break;
            case BURST_CUT:
                resolveBurstHit(simulator, hit, false);
                break;
            case BURST_BLOOM:
                resolveBurstHit(simulator, hit, true);
                break;
            default:
                throw new IllegalStateException(
                        "Unknown Kamisato Ayaka hit kind " + hit.kind);
        }
    }

    private void resolveNormalHit(
            CombatSimulator simulator,
            PendingHit hit) {
        Element hitElement = infusedElement(hit.time);
        ICDTag tag = hit.index == 4
                ? ICDTag.Ayaka_NormalFive : ICDTag.NormalAttack;
        performHit(
                simulator,
                hit,
                "Kamisato Art: Kabuki "
                        + NORMAL_KEYS[hit.index][hit.subIndex],
                getTalentValue(
                        NORMAL_KEYS[hit.index][hit.subIndex],
                        NORMAL_MULTIPLIERS[hit.index][hit.subIndex]),
                hitElement,
                StatType.NORMAL_ATTACK_DMG_BONUS,
                ActionType.NORMAL,
                ICDType.Standard,
                tag,
                hitElement == Element.CRYO ? 1.0 : 0.0);
        attemptC1(hit.time, hitElement);
    }

    private void resolveChargedHit(
            CombatSimulator simulator,
            PendingHit hit) {
        Element hitElement = infusedElement(hit.time);
        AttackAction action = performHit(
                simulator,
                hit,
                "Kamisato Art: Kabuki Charged " + (hit.subIndex + 1),
                getTalentValue("Charged Attack", 1.012780),
                hitElement,
                StatType.CHARGED_ATTACK_DMG_BONUS,
                ActionType.CHARGE,
                ICDType.Standard,
                ICDTag.Ayaka_Charged,
                hitElement == Element.CRYO ? 1.0 : 0.0,
                hit.c6Boosted
                        ? getTalentValue("C6 Charged DMG Bonus", 2.98)
                        : 0.0);
        attemptC1(hit.time, hitElement);
        if (hit.subIndex == 0 && hit.c6Boosted && c6Ready) {
            c6Ready = false;
            queueCommand(simulator, new PendingCommand(
                    hit.time + getTalentValue("C6 Ready Delay", 10.5),
                    CommandKind.C6_READY,
                    0.0));
        }
        if (action == null) {
            throw new IllegalStateException("Ayaka Charged hit did not resolve");
        }
    }

    private void resolvePlungeHit(
            CombatSimulator simulator,
            PendingHit hit) {
        Element hitElement = infusedElement(hit.time);
        performHit(
                simulator,
                hit,
                "Kamisato Art: Kabuki High Plunge",
                getTalentValue("High Plunge", 2.933586),
                hitElement,
                StatType.PLUNGING_ATTACK_DMG_BONUS,
                ActionType.PLUNGE,
                ICDType.None,
                ICDTag.PlungeAttack,
                hitElement == Element.CRYO ? 1.0 : 0.0);
    }

    private void resolveDashHit(
            CombatSimulator simulator,
            PendingHit hit) {
        performHit(
                simulator,
                hit,
                "Kamisato Art: Senho Exit",
                0.0,
                Element.CRYO,
                null,
                ActionType.OTHER,
                ICDType.Standard,
                ICDTag.Ayaka_Dash,
                1.0);
        if (simulator.getEnemy() == null) {
            return;
        }
        infusionExpirationTime = hit.time
                + getTalentValue("Infusion Duration", 5.0);
        a4ExpirationTime = hit.time
                + getTalentValue("A4 Duration", 10.0);
    }

    private void resolveSkillHit(
            CombatSimulator simulator,
            PendingHit hit) {
        performHit(
                simulator,
                hit,
                "Kamisato Art: Hyouka",
                skillValue(),
                Element.CRYO,
                StatType.SKILL_DMG_BONUS,
                ActionType.SKILL,
                ICDType.Standard,
                ICDTag.ElementalSkill,
                2.0);
        if (simulator.getEnemy() != null
                && hit.time + EPSILON >= nextParticleAllowedTime) {
            nextParticleAllowedTime = hit.time
                    + getTalentValue("Particle Cooldown", 0.3);
            queueCommand(simulator, new PendingCommand(
                    hit.time
                            + getTalentValue("Particle Travel Frames", 100.0)
                                    * FRAME,
                    CommandKind.PARTICLE,
                    nextParticleCount()));
        }
    }

    private void resolveBurstHit(
            CombatSimulator simulator,
            PendingHit hit,
            boolean bloom) {
        double factor = hit.index == 0 ? 1.0
                : getTalentValue("C2 Frostflake Factor", 0.20);
        String variant = hit.index == 0
                ? "" : " Mini " + hit.index;
        performHit(
                simulator,
                hit,
                "Kamisato Art: Soumetsu" + variant
                        + (bloom ? " Bloom" : " Cutting "
                                + (hit.subIndex + 1)),
                (bloom ? burstBloomValue() : burstCutValue()) * factor,
                Element.CRYO,
                StatType.BURST_DMG_BONUS,
                ActionType.BURST,
                ICDType.Standard,
                ICDTag.ElementalBurst,
                1.0);
        if (constellation >= 4 && simulator.getEnemy() != null) {
            c4ExpirationTime = hit.time
                    + getTalentValue("C4 Duration", 6.0);
        }
    }

    private AttackAction performHit(
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
        return performHit(
                simulator,
                hit,
                displayName,
                multiplier,
                hitElement,
                bonusStat,
                actionType,
                icdType,
                icdTag,
                gaugeUnits,
                0.0);
    }

    private AttackAction performHit(
            CombatSimulator simulator,
            PendingHit hit,
            String displayName,
            double multiplier,
            Element hitElement,
            StatType bonusStat,
            ActionType actionType,
            ICDType icdType,
            ICDTag icdTag,
            double gaugeUnits,
            double chargedBonus) {
        AttackAction action = new AttackAction(
                displayName,
                multiplier,
                hitElement,
                StatType.BASE_ATK,
                bonusStat,
                0.0,
                actionType);
        action.setICD(icdType, icdTag, gaugeUnits);
        action.setCountsAsSkillDmg(actionType == ActionType.SKILL);
        action.setCountsAsBurstDmg(actionType == ActionType.BURST);
        if (hit.snapshot != null) {
            action.setStatSnapshot(hit.snapshot);
        }
        if (chargedBonus != 0.0) {
            action.addBonusStat(
                    StatType.CHARGED_ATTACK_DMG_BONUS,
                    chargedBonus);
        }
        simulator.performActionWithoutTimeAdvance(characterId, action);
        return action;
    }

    private Element infusedElement(double currentTime) {
        return isCryoInfusionActive(currentTime)
                ? Element.CRYO : Element.PHYSICAL;
    }

    private void attemptC1(double currentTime, Element hitElement) {
        if (constellation < 1
                || hitElement != Element.CRYO
                || currentTime + EPSILON < nextC1AllowedTime) {
            return;
        }
        if (nextC1Draw() < 0.50) {
            return;
        }
        reduceSkillCooldown(
                currentTime,
                getTalentValue("C1 Skill Cooldown Reduction", 0.3));
        nextC1AllowedTime = currentTime
                + getTalentValue("C1 Proc Cooldown", 0.1);
    }

    private double skillValue() {
        return getTalentValue(
                constellation >= 5 ? "Hyouka C5" : "Hyouka",
                constellation >= 5 ? 4.784000 : 4.066400);
    }

    private double burstCutValue() {
        return getTalentValue(
                constellation >= 3 ? "Soumetsu Cutting C3"
                        : "Soumetsu Cutting",
                constellation >= 3 ? 2.246000 : 1.909100);
    }

    private double burstBloomValue() {
        return getTalentValue(
                constellation >= 3 ? "Soumetsu Bloom C3"
                        : "Soumetsu Bloom",
                constellation >= 3 ? 3.369000 : 2.863650);
    }

    private int nextParticleCount() {
        double draw = consumeDraw(
                particleDrawSource,
                particleDrawTape,
                particleDrawCursor,
                "Kamisato Ayaka particle");
        particleDrawCursor++;
        return draw < 0.50 ? 4 : 5;
    }

    private double nextC1Draw() {
        double draw = consumeDraw(
                c1DrawSource,
                c1DrawTape,
                c1DrawCursor,
                "Kamisato Ayaka C1");
        c1DrawCursor++;
        return draw;
    }

    private static double consumeDraw(
            DoubleSupplier source,
            List<Double> tape,
            int cursor,
            String label) {
        if (cursor < tape.size()) {
            return tape.get(cursor);
        }
        double draw = source.getAsDouble();
        if (Double.isNaN(draw) || draw < 0.0 || draw > 1.0) {
            throw new IllegalStateException(label + " draw must be in [0, 1]");
        }
        tape.add(draw);
        return draw;
    }

    private StatsContainer captureLiveStats(double currentTime) {
        StatsContainer stats = getEffectiveStats(currentTime);
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
                case PARTICLE:
                    activeSimulator.getEnergyDistributor()
                            .distributeParticles(
                                    Element.CRYO,
                                    command.value,
                                    ParticleType.PARTICLE);
                    break;
                case BURST_ENERGY:
                    spendBurstEnergy(activeSimulator.getCurrentTime());
                    break;
                case C6_READY:
                    c6Ready = constellation >= 6;
                    break;
                default:
                    throw new IllegalStateException(
                            "Unknown Kamisato Ayaka command kind "
                                    + command.kind);
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
        DASH,
        SKILL,
        BURST_CUT,
        BURST_BLOOM
    }

    private enum CommandKind {
        PARTICLE,
        BURST_ENERGY,
        C6_READY
    }

    /** Immutable delayed Ayaka hit description. */
    private static final class PendingHit {
        private final double time;
        private final HitKind kind;
        private final int index;
        private final int subIndex;
        private final boolean c6Boosted;
        private final StatsContainer snapshot;

        private PendingHit(
                double time,
                HitKind kind,
                int index,
                int subIndex,
                boolean c6Boosted,
                StatsContainer snapshot) {
            this.time = time;
            this.kind = kind;
            this.index = index;
            this.subIndex = subIndex;
            this.c6Boosted = c6Boosted;
            this.snapshot = snapshot == null
                    ? null : snapshot.merge(null);
        }

        private PendingHit copy() {
            return new PendingHit(
                    time, kind, index, subIndex, c6Boosted, snapshot);
        }
    }

    /** Immutable delayed Ayaka state command. */
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

    /** Immutable owner-bound Ayaka rollback payload. */
    private static final class AyakaState implements State {
        private final KamisatoAyaka owner;
        private final int normalAttackStep;
        private final int particleDrawCursor;
        private final int c1DrawCursor;
        private final double infusionExpirationTime;
        private final double a1ExpirationTime;
        private final double a4ExpirationTime;
        private final double nextParticleAllowedTime;
        private final double nextC1AllowedTime;
        private final double c4ExpirationTime;
        private final boolean c6Ready;
        private final List<PendingHit> pendingHits;
        private final List<PendingCommand> pendingCommands;

        private AyakaState(
                KamisatoAyaka owner,
                int normalAttackStep,
                int particleDrawCursor,
                int c1DrawCursor,
                double infusionExpirationTime,
                double a1ExpirationTime,
                double a4ExpirationTime,
                double nextParticleAllowedTime,
                double nextC1AllowedTime,
                double c4ExpirationTime,
                boolean c6Ready,
                List<PendingHit> pendingHits,
                List<PendingCommand> pendingCommands) {
            this.owner = owner;
            this.normalAttackStep = normalAttackStep;
            this.particleDrawCursor = particleDrawCursor;
            this.c1DrawCursor = c1DrawCursor;
            this.infusionExpirationTime = infusionExpirationTime;
            this.a1ExpirationTime = a1ExpirationTime;
            this.a4ExpirationTime = a4ExpirationTime;
            this.nextParticleAllowedTime = nextParticleAllowedTime;
            this.nextC1AllowedTime = nextC1AllowedTime;
            this.c4ExpirationTime = c4ExpirationTime;
            this.c6Ready = c6Ready;
            this.pendingHits = copyHits(pendingHits);
            this.pendingCommands = copyCommands(pendingCommands);
        }
    }
}

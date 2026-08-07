package model.character;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import mechanics.buff.Buff;
import mechanics.buff.BuffId;
import mechanics.buff.SimpleBuff;
import mechanics.data.TalentDataManager;
import mechanics.data.TalentDataSource;
import mechanics.energy.ParticleType;
import mechanics.reaction.ReactionResult;
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
 * Varka's deterministic fixed-target Sturm und Drang slice.
 *
 * <p>Level-90 claymore basics, high Plunge, Windbound Execution, the
 * Sturm und Drang replacement string, Four Winds' Ascension, Azure Devour,
 * Northwind Avatar, particles, private application groups, A1/A4, and
 * representable offensive C1-C6 behavior follow pinned gcsim revision
 * {@code ef41805d}. The PHEC conversion uses the source priority order of
 * Pyro, Hydro, Electro, then Cryo from the live party composition.</p>
 *
 * <p>Player HP, healing, defense, movement and geometry, multi-target and
 * random selection, hitlag, stamina, low Plunge, exploration, and unsupported
 * Hexerei team state fail closed. Hexerei cooldown acceleration therefore
 * uses the source-backed non-team fallback of 0.5 seconds per accepted Normal
 * action.</p>
 */
public final class Varka extends Character implements
        SimulatorInitializedCharacterEffect,
        SnapshotAwareCharacterEffect,
        SwitchAwareCharacter,
        CombatSimulator.ReactionListener {
    private static final double FRAME = 1.0 / 60.0;
    private static final double EPSILON = 1e-9;
    private static final int[] NORMAL_DURATIONS = { 46, 46, 60, 47, 82 };
    private static final int[][] NORMAL_HIT_FRAMES = {
        { 19 }, { 18, 28 }, { 27, 43 }, { 19, 24 }, { 44, 45 }
    };
    private static final String[][] NORMAL_KEYS = {
        { "N1" },
        { "N2-1", "N2-2" },
        { "N3-1", "N3-2" },
        { "N4-1", "N4-2" },
        { "N5-1", "N5-2" }
    };
    private static final double[][] NORMAL_T9 = {
        { 1.202633 },
        { 0.440719, 0.818478 },
        { 0.595924, 1.106716 },
        { 1.018394, 0.548366 },
        { 1.281450, 0.690011 }
    };
    private static final double[][] STURM_NORMAL_T9 = {
        { 1.503291 },
        { 0.550899, 1.023097 },
        { 0.744905, 1.383395 },
        { 1.272992, 0.685457 },
        { 1.601812, 0.862514 }
    };
    private static final double[][] STURM_NORMAL_C3 = {
        { 1.845813 },
        { 0.676420, 1.256208 },
        { 0.914630, 1.698599 },
        { 1.563041, 0.841637 },
        { 1.966782, 1.059036 }
    };

    private CombatSimulator initializedSimulator;
    private int normalAttackStep;
    private long stanceGeneration;
    private boolean stanceActive;
    private double stanceExpirationTime = Double.NEGATIVE_INFINITY;
    private Element conversionElement = Element.PHYSICAL;
    private int fourWindsCharges;
    private int fourWindsRechargesStarted;
    private int fourWindsReductionStacks;
    private long fourWindsChargeGeneration;
    private double nextFourWindsChargeTime = Double.POSITIVE_INFINITY;
    private boolean c1FirstSpecialAvailable;
    private double c6FreeSkillUntil = Double.NEGATIVE_INFINITY;
    private double c6FreeChargeUntil = Double.NEGATIVE_INFINITY;
    private int a4Stacks;
    private double a4ExpirationTime = Double.NEGATIVE_INFINITY;
    private EnumMap<CharacterId, Double> a4SourceCooldowns =
            new EnumMap<>(CharacterId.class);
    private List<PendingHit> pendingHits = new ArrayList<>();
    private List<PendingCommand> pendingCommands = new ArrayList<>();

    /** Constructs repository-default C6 Varka. */
    public Varka(Weapon weapon, ArtifactSet artifacts) {
        this(weapon, artifacts, TalentDataManager.getInstance(), 6);
    }

    /** Constructs Varka at an explicit constellation. */
    public Varka(
            Weapon weapon,
            ArtifactSet artifacts,
            int constellation) {
        this(weapon, artifacts, TalentDataManager.getInstance(),
                constellation);
    }

    /**
     * Constructs Varka with injectable static talent data.
     *
     * @param weapon equipped claymore, or {@code null}
     * @param artifacts equipped artifact set, or {@code null}
     * @param talentData static character-data source
     * @param constellation constellation level in {@code [0, 6]}
     */
    public Varka(
            Weapon weapon,
            ArtifactSet artifacts,
            TalentDataSource talentData,
            int constellation) {
        super(talentData);
        if (constellation < 0 || constellation > 6) {
            throw new IllegalArgumentException(
                    "Varka constellation must be between 0 and 6");
        }
        name = "Varka";
        characterId = CharacterId.VARKA;
        element = Element.ANEMO;
        this.weapon = weapon;
        this.artifacts = new ArtifactSet[] { artifacts };
        this.constellation = constellation;
        baseStats.set(StatType.BASE_HP,
                getTalentValue("Base HP", 12613.0));
        baseStats.set(StatType.BASE_ATK,
                getTalentValue("Base ATK", 353.0));
        baseStats.set(StatType.BASE_DEF,
                getTalentValue("Base DEF", 795.0));
        baseStats.add(StatType.CRIT_DMG,
                getTalentValue("Ascension CRIT DMG", 0.384));
        setSkillCD(getTalentValue("Skill Cooldown", 16.0));
        setBurstCD(getTalentValue("Burst Cooldown", 15.0));
    }

    /** Binds composition and Swirl listeners to one simulator. */
    @Override
    public void initializeForSimulator(CombatSimulator simulator) {
        if (simulator == null) {
            throw new IllegalArgumentException("Varka simulator is required");
        }
        if (!simulator.getPartyMembers().contains(this)) {
            throw new IllegalArgumentException(
                    "Varka must belong to the simulator party");
        }
        if (initializedSimulator != null
                && initializedSimulator != simulator) {
            throw new IllegalStateException(
                    "Varka cannot be reused across simulators");
        }
        if (initializedSimulator == simulator) {
            return;
        }
        initializedSimulator = simulator;
        conversionElement = selectConversionElement(simulator);
        simulator.addReactionListener(this);
    }

    /** Captures stance, charge, passive, and delayed-event state. */
    @Override
    public State captureCharacterState() {
        return new VarkaState(
                this,
                normalAttackStep,
                stanceGeneration,
                stanceActive,
                stanceExpirationTime,
                conversionElement,
                fourWindsCharges,
                fourWindsRechargesStarted,
                fourWindsReductionStacks,
                fourWindsChargeGeneration,
                nextFourWindsChargeTime,
                c1FirstSpecialAvailable,
                c6FreeSkillUntil,
                c6FreeChargeUntil,
                a4Stacks,
                a4ExpirationTime,
                a4SourceCooldowns,
                pendingHits,
                pendingCommands);
    }

    /** Accepts state captured from this exact Varka instance only. */
    @Override
    public boolean acceptsCharacterState(State state) {
        return state instanceof VarkaState
                && ((VarkaState) state).owner == this;
    }

    /** Restores all Varka-owned state and unresolved work exactly once. */
    @Override
    public void restoreCharacterState(
            State state,
            CombatSimulator simulator) {
        if (!acceptsCharacterState(state)) {
            throw new IllegalArgumentException("Unexpected Varka state");
        }
        initializeForSimulator(simulator);
        VarkaState restored = (VarkaState) state;
        normalAttackStep = restored.normalAttackStep;
        stanceGeneration = restored.stanceGeneration;
        stanceActive = restored.stanceActive;
        stanceExpirationTime = restored.stanceExpirationTime;
        conversionElement = restored.conversionElement;
        fourWindsCharges = restored.fourWindsCharges;
        fourWindsRechargesStarted = restored.fourWindsRechargesStarted;
        fourWindsReductionStacks = restored.fourWindsReductionStacks;
        fourWindsChargeGeneration = restored.fourWindsChargeGeneration;
        nextFourWindsChargeTime = restored.nextFourWindsChargeTime;
        c1FirstSpecialAvailable = restored.c1FirstSpecialAvailable;
        c6FreeSkillUntil = restored.c6FreeSkillUntil;
        c6FreeChargeUntil = restored.c6FreeChargeUntil;
        a4Stacks = restored.a4Stacks;
        a4ExpirationTime = restored.a4ExpirationTime;
        a4SourceCooldowns = copyCooldowns(restored.a4SourceCooldowns);
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

    /** Returns Varka's 60-Energy Burst cost. */
    @Override
    public double getEnergyCost() {
        return getTalentValue("Energy Cost", 60.0);
    }

    /** Varka's represented passives are conditional and applied per hit. */
    @Override
    public void applyPassive(StatsContainer stats) {
    }

    /** Allows a ready Four Winds recast to bypass the base Skill cooldown. */
    @Override
    public double getSkillCDRemaining(double currentTime) {
        if (!isStanceActiveAt(currentTime)
                || conversionElement == Element.PHYSICAL) {
            return super.getSkillCDRemaining(currentTime);
        }
        if (fourWindsCharges > 0
                || currentTime + EPSILON < c6FreeSkillUntil) {
            return 0.0;
        }
        if (nextFourWindsChargeTime
                <= stanceExpirationTime + EPSILON) {
            return Math.max(0.0,
                    nextFourWindsChargeTime - currentTime);
        }
        return super.getSkillCDRemaining(currentTime);
    }

    /** Ends Sturm und Drang when Varka leaves the field. */
    @Override
    public void onSwitchOut(CombatSimulator simulator) {
        normalAttackStep = 0;
        endStance();
    }

    /** Resets the claymore sequence when Varka returns. */
    @Override
    public void onSwitchIn(CombatSimulator simulator) {
        normalAttackStep = 0;
    }

    /** Records source-backed PHEC Swirls for A4 and C4. */
    @Override
    public void onReaction(
            ReactionResult result,
            Character source,
            double time,
            CombatSimulator simulator) {
        if (simulator != initializedSimulator
                || result == null
                || result.getKind() != ReactionResult.Kind.SWIRL
                || source == null
                || !simulator.getPartyMembers().contains(source)
                || !isPhec(result.getSwirlElement())) {
            return;
        }
        expireA4(time);
        double nextAllowed = a4SourceCooldowns.getOrDefault(
                source.getCharacterId(), Double.NEGATIVE_INFINITY);
        if (time + EPSILON >= nextAllowed) {
            a4Stacks = Math.min(
                    (int) getTalentValue("A4 Maximum Stacks", 4.0),
                    a4Stacks + 1);
            a4ExpirationTime = time
                    + getTalentValue("A4 Duration", 8.0);
            a4SourceCooldowns.put(
                    source.getCharacterId(),
                    time + getTalentValue(
                            "A4 Source Cooldown", 1.0));
        }
        if (constellation >= 4 && source == this) {
            applyC4Buffs(simulator, result.getSwirlElement(), time);
        }
    }

    /** Dispatches the bounded typed offensive action set. */
    @Override
    public void onAction(
            CharacterActionRequest request,
            CombatSimulator simulator) {
        if (request == null) {
            throw new IllegalArgumentException("Varka action is required");
        }
        initializeForSimulator(simulator);
        if (!isStanceActiveAt(simulator.getCurrentTime())) {
            conversionElement = selectConversionElement(simulator);
        }
        if (request.getKey() == CharacterActionKey.SKILL
                && request.getSkillMode() != SkillActionMode.PRESS) {
            throw new IllegalArgumentException(
                    "Varka supports Press Skill only");
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
            case SKILL:
                if (isStanceActiveAt(simulator.getCurrentTime())
                        && conversionElement != Element.PHYSICAL) {
                    fourWindsAscension(simulator);
                } else {
                    windboundExecution(simulator);
                }
                break;
            case BURST:
                northwindAvatar(simulator);
                break;
            default:
                throw new IllegalArgumentException(
                        "Unsupported action for Varka: "
                                + request.getKey());
        }
    }

    /** Returns whether the local Sturm und Drang window is live. */
    public boolean isSturmUndDrangActive() {
        return initializedSimulator != null
                && isStanceActiveAt(
                        initializedSimulator.getCurrentTime());
    }

    /** Returns Varka's fixed PHEC conversion selection. */
    public Element getConversionElement() {
        return conversionElement;
    }

    /** Returns currently available Four Winds charges. */
    public int getFourWindsCharges() {
        return fourWindsCharges;
    }

    /** Returns active A4 stacks after applying their shared expiry. */
    public int getA4Stacks(double currentTime) {
        expireA4(currentTime);
        return a4Stacks;
    }

    private void normalAttack(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        int step = normalAttackStep;
        boolean sturm = isStanceActiveAt(castTime);
        double compositionMultiplier = sturm
                ? stanceCompositionMultiplier(simulator) : 1.0;
        for (int hit = 0; hit < NORMAL_HIT_FRAMES[step].length; hit++) {
            Element hitElement = sturm
                    ? alternatingElement(step, hit)
                    : Element.PHYSICAL;
            String key = sturm
                    ? "Sturm " + NORMAL_KEYS[step][hit]
                    : NORMAL_KEYS[step][hit];
            double fallback = sturm
                    ? skillNormalFallback(step, hit)
                    : NORMAL_T9[step][hit];
            queueHit(simulator, new PendingHit(
                    castTime + NORMAL_HIT_FRAMES[step][hit] * FRAME,
                    (sturm ? "Sturm und Drang " : "Favonius Bladework ")
                            + NORMAL_KEYS[step][hit],
                    getTalentValue(key, fallback)
                            * compositionMultiplier,
                    hitElement,
                    StatType.NORMAL_ATTACK_DMG_BONUS,
                    ActionType.NORMAL,
                    ICDType.Standard,
                    sturm ? normalIcdTag(hitElement)
                            : ICDTag.NormalAttack,
                    hitElement == Element.PHYSICAL ? 0.0 : 1.0,
                    true,
                    sturm && hit == 0));
        }
        normalAttackStep = (normalAttackStep + 1)
                % NORMAL_HIT_FRAMES.length;
        simulator.advanceTime(NORMAL_DURATIONS[step] * FRAME);
    }

    private void chargedAttack(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        if (isStanceActiveAt(castTime)) {
            if (fourWindsCharges > 0
                    || castTime + EPSILON < c6FreeChargeUntil) {
                azureDevour(simulator);
            } else {
                sturmChargedAttack(simulator);
            }
            return;
        }
        double[] fallback = { 1.573364, 0.847196 };
        for (int hit = 0; hit < 2; hit++) {
            queueHit(simulator, new PendingHit(
                    castTime + 41.0 * FRAME,
                    "Favonius Bladework Charged Hit " + (hit + 1),
                    getTalentValue(
                            "Charged Hit " + (hit + 1),
                            fallback[hit]),
                    Element.PHYSICAL,
                    StatType.CHARGED_ATTACK_DMG_BONUS,
                    ActionType.CHARGE,
                    ICDType.Standard,
                    ICDTag.NormalAttack,
                    0.0,
                    true,
                    false));
        }
        simulator.advanceTime(67.0 * FRAME);
    }

    private void sturmChargedAttack(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        Element[] elements = { conversionElement, Element.ANEMO };
        double multiplier = stanceCompositionMultiplier(simulator);
        for (int hit = 0; hit < 2; hit++) {
            String key = "Sturm Charged Hit " + (hit + 1)
                    + (constellation >= 3 ? " C3" : "");
            double fallback = constellation >= 3
                    ? new double[] { 2.414815, 1.300285 }[hit]
                    : new double[] { 1.966705, 1.058995 }[hit];
            queueHit(simulator, new PendingHit(
                    castTime + 41.0 * FRAME,
                    "Sturm und Drang Charged Hit " + (hit + 1),
                    getTalentValue(key, fallback) * multiplier,
                    elements[hit],
                    StatType.CHARGED_ATTACK_DMG_BONUS,
                    ActionType.CHARGE,
                    ICDType.Standard,
                    extraIcdTag(elements[hit]),
                    1.0,
                    true,
                    false));
        }
        simulator.advanceTime(67.0 * FRAME);
    }

    private void azureDevour(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        boolean free = castTime + EPSILON < c6FreeChargeUntil;
        double specialMultiplier = consumeC1SpecialMultiplier();
        double compositionMultiplier = stanceCompositionMultiplier(simulator);
        int[] hitFrames = { 40, 40, 60, 60 };
        for (int hit = 0; hit < hitFrames.length; hit++) {
            boolean elementHit = hit % 2 == 0;
            Element hitElement = elementHit
                    ? conversionElement : Element.ANEMO;
            String key = elementHit
                    ? "Azure Devour Element Hit"
                    : "Azure Devour Anemo Hit";
            if (constellation >= 3) {
                key += " C3";
            }
            double fallback;
            if (constellation >= 3) {
                fallback = elementHit ? 1.872000 : 1.008000;
            } else {
                fallback = elementHit ? 1.591200 : 0.856800;
            }
            queueHit(simulator, new PendingHit(
                    castTime + hitFrames[hit] * FRAME,
                    "Azure Devour Hit " + (hit + 1),
                    getTalentValue(key, fallback)
                            * compositionMultiplier
                            * specialMultiplier,
                    hitElement,
                    StatType.CHARGED_ATTACK_DMG_BONUS,
                    ActionType.CHARGE,
                    ICDType.Standard,
                    extraIcdTag(hitElement),
                    1.0,
                    true,
                    false));
        }
        if (!free) {
            consumeFourWindsCharge();
        }
        activateC6FreeSkill(castTime);
        queueC2Hit(simulator, castTime);
        simulator.advanceTime(75.0 * FRAME);
    }

    private void highPlunge(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        queueHit(simulator, new PendingHit(
                castTime + 48.0 * FRAME,
                "Favonius Bladework High Plunge",
                getTalentValue("High Plunge", 2.933586),
                Element.PHYSICAL,
                StatType.PLUNGING_ATTACK_DMG_BONUS,
                ActionType.PLUNGE,
                ICDType.None,
                ICDTag.None,
                0.0,
                false,
                false));
        simulator.advanceTime(74.0 * FRAME);
    }

    private void windboundExecution(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        String key = constellation >= 3
                ? "Windbound Execution C3"
                : "Windbound Execution";
        double fallback = constellation >= 3 ? 5.568000 : 4.732800;
        queueHit(simulator, new PendingHit(
                castTime + 40.0 * FRAME,
                "Windbound Execution",
                getTalentValue(key, fallback),
                Element.ANEMO,
                StatType.SKILL_DMG_BONUS,
                ActionType.SKILL,
                ICDType.None,
                ICDTag.ElementalSkill,
                1.0,
                false,
                false));
        queueCommand(simulator, new PendingCommand(
                castTime + 39.0 * FRAME,
                CommandKind.SKILL_COOLDOWN,
                0L));
        long generation = ++stanceGeneration;
        queueCommand(simulator, new PendingCommand(
                castTime + 40.0 * FRAME,
                CommandKind.STANCE_START,
                generation));
        simulator.advanceTime(55.0 * FRAME);
    }

    private void fourWindsAscension(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        boolean free = castTime + EPSILON < c6FreeSkillUntil;
        double specialMultiplier = consumeC1SpecialMultiplier();
        double compositionMultiplier = stanceCompositionMultiplier(simulator);
        int[] hitFrames = { 34, 43 };
        Element[] elements = { conversionElement, Element.ANEMO };
        String[] keys = {
            "Four Winds Element Hit", "Four Winds Anemo Hit"
        };
        double[] talentNine = { 2.987920, 1.608880 };
        double[] talentTwelve = { 3.515200, 1.892800 };
        for (int hit = 0; hit < 2; hit++) {
            String key = keys[hit]
                    + (constellation >= 3 ? " C3" : "");
            queueHit(simulator, new PendingHit(
                    castTime + hitFrames[hit] * FRAME,
                    "Four Winds' Ascension Hit " + (hit + 1),
                    getTalentValue(
                            key,
                            constellation >= 3
                                    ? talentTwelve[hit]
                                    : talentNine[hit])
                            * compositionMultiplier
                            * specialMultiplier,
                    elements[hit],
                    StatType.SKILL_DMG_BONUS,
                    ActionType.SKILL,
                    ICDType.None,
                    ICDTag.ElementalSkill,
                    1.0,
                    true,
                    false));
        }
        if (!free) {
            consumeFourWindsCharge();
        }
        activateC6FreeCharge(castTime);
        queueC2Hit(simulator, castTime);
        simulator.advanceTime(68.0 * FRAME);
    }

    private void northwindAvatar(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        markBurstCooldownUsed(
                castTime, simulator.getApplicableBuffs(this));
        queueCommand(simulator, new PendingCommand(
                castTime + 4.0 * FRAME,
                CommandKind.BURST_ENERGY,
                0L));
        if (isStanceActiveAt(castTime)) {
            stanceExpirationTime += getTalentValue(
                    "Burst Stance Extension", 2.3);
            queueCommand(simulator, new PendingCommand(
                    stanceExpirationTime,
                    CommandKind.STANCE_END,
                    stanceGeneration));
        }
        Element firstElement = conversionElement == Element.PHYSICAL
                ? Element.ANEMO : conversionElement;
        Element[] elements = { firstElement, Element.ANEMO };
        int[] hitFrames = { 112, 131 };
        double[] talentNine = { 5.728320, 3.084480 };
        double[] talentTwelve = { 6.739200, 3.628800 };
        for (int hit = 0; hit < 2; hit++) {
            String key = "Northwind Avatar Hit " + (hit + 1)
                    + (constellation >= 5 ? " C5" : "");
            queueHit(simulator, new PendingHit(
                    castTime + hitFrames[hit] * FRAME,
                    "Northwind Avatar Hit " + (hit + 1),
                    getTalentValue(
                            key,
                            constellation >= 5
                                    ? talentTwelve[hit]
                                    : talentNine[hit]),
                    elements[hit],
                    StatType.BURST_DMG_BONUS,
                    ActionType.BURST,
                    ICDType.None,
                    ICDTag.ElementalBurst,
                    1.0,
                    false,
                    false));
        }
        simulator.advanceTime(152.0 * FRAME);
    }

    private void queueC2Hit(
            CombatSimulator simulator,
            double castTime) {
        if (constellation < 2) {
            return;
        }
        queueHit(simulator, new PendingHit(
                castTime + 10.0 * FRAME,
                "Varka C2 Northwind",
                getTalentValue("C2 Northwind Multiplier", 8.0),
                Element.ANEMO,
                StatType.DMG_BONUS_ALL,
                ActionType.OTHER,
                ICDType.None,
                ICDTag.None,
                1.0,
                true,
                false));
    }

    private void startStance(
            CombatSimulator simulator,
            long generation) {
        if (generation != stanceGeneration) {
            return;
        }
        double currentTime = simulator.getCurrentTime();
        stanceActive = true;
        stanceExpirationTime = currentTime
                + getTalentValue("Stance Duration", 12.0);
        fourWindsCharges = constellation >= 1 ? 1 : 0;
        fourWindsRechargesStarted = 0;
        fourWindsReductionStacks = 0;
        c1FirstSpecialAvailable = constellation >= 1;
        c6FreeSkillUntil = Double.NEGATIVE_INFINITY;
        c6FreeChargeUntil = Double.NEGATIVE_INFINITY;
        if (conversionElement != Element.PHYSICAL) {
            startFourWindsRecharge(simulator, currentTime);
        }
        queueCommand(simulator, new PendingCommand(
                stanceExpirationTime,
                CommandKind.STANCE_END,
                generation));
    }

    private void endStance() {
        if (!stanceActive) {
            return;
        }
        stanceActive = false;
        stanceExpirationTime = Double.NEGATIVE_INFINITY;
        fourWindsCharges = 0;
        fourWindsRechargesStarted = 0;
        fourWindsReductionStacks = 0;
        nextFourWindsChargeTime = Double.POSITIVE_INFINITY;
        c1FirstSpecialAvailable = false;
        c6FreeSkillUntil = Double.NEGATIVE_INFINITY;
        c6FreeChargeUntil = Double.NEGATIVE_INFINITY;
        stanceGeneration++;
        fourWindsChargeGeneration++;
    }

    private void startFourWindsRecharge(
            CombatSimulator simulator,
            double currentTime) {
        int maximum = (int) getTalentValue(
                "Four Winds Recharge Count", 2.0);
        if (!stanceActive || fourWindsRechargesStarted >= maximum) {
            nextFourWindsChargeTime = Double.POSITIVE_INFINITY;
            return;
        }
        fourWindsRechargesStarted++;
        nextFourWindsChargeTime = currentTime
                + getTalentValue("Four Winds Charge Cooldown", 11.0);
        long generation = ++fourWindsChargeGeneration;
        queueCommand(simulator, new PendingCommand(
                nextFourWindsChargeTime,
                CommandKind.FOUR_WINDS_CHARGE,
                generation));
    }

    private void restoreFourWindsCharge(
            CombatSimulator simulator,
            long generation) {
        if (!stanceActive
                || generation != fourWindsChargeGeneration
                || simulator.getCurrentTime() + EPSILON
                        < nextFourWindsChargeTime) {
            return;
        }
        fourWindsCharges++;
        nextFourWindsChargeTime = Double.POSITIVE_INFINITY;
        startFourWindsRecharge(
                simulator, simulator.getCurrentTime());
    }

    private void reduceFourWindsCharge(CombatSimulator simulator) {
        if (!stanceActive
                || !Double.isFinite(nextFourWindsChargeTime)
                || fourWindsReductionStacks >= (int) getTalentValue(
                        "Normal Charge Reduction Cap", 15.0)) {
            return;
        }
        fourWindsReductionStacks++;
        nextFourWindsChargeTime = Math.max(
                simulator.getCurrentTime(),
                nextFourWindsChargeTime
                        - getTalentValue(
                                "Normal Charge Reduction", 0.5));
        long generation = ++fourWindsChargeGeneration;
        queueCommand(simulator, new PendingCommand(
                nextFourWindsChargeTime,
                CommandKind.FOUR_WINDS_CHARGE,
                generation));
    }

    private void consumeFourWindsCharge() {
        if (fourWindsCharges <= 0) {
            throw new IllegalStateException(
                    "Varka has no Four Winds charge");
        }
        fourWindsCharges--;
    }

    private double consumeC1SpecialMultiplier() {
        if (!c1FirstSpecialAvailable) {
            return 1.0;
        }
        c1FirstSpecialAvailable = false;
        return getTalentValue("C1 First Special Multiplier", 2.0);
    }

    private void activateC6FreeCharge(double currentTime) {
        if (constellation >= 6) {
            c6FreeChargeUntil = currentTime
                    + getTalentValue("C6 Free Action Duration", 3.0);
        }
    }

    private void activateC6FreeSkill(double currentTime) {
        if (constellation >= 6) {
            c6FreeSkillUntil = currentTime
                    + getTalentValue("C6 Free Action Duration", 3.0);
        }
    }

    private void resolveHit(
            CombatSimulator simulator,
            PendingHit hit) {
        AttackAction action = new AttackAction(
                hit.displayName,
                hit.multiplier,
                hit.element,
                StatType.BASE_ATK,
                hit.bonusStat,
                0.0,
                hit.actionType);
        action.setICD(hit.icdType, hit.icdTag, hit.gauge);
        action.setCountsAsSkillDmg(hit.actionType == ActionType.SKILL);
        action.setCountsAsBurstDmg(hit.actionType == ActionType.BURST);
        double currentTime = simulator.getCurrentTime();
        double a1Bonus = a1DamageBonus(currentTime, hit.element);
        if (a1Bonus > 0.0) {
            action.addBonusStat(StatType.DMG_BONUS_ALL, a1Bonus);
        }
        expireA4(currentTime);
        if (hit.a4Eligible && a4Stacks > 0) {
            action.addBonusStat(
                    StatType.DMG_BONUS_ALL,
                    a4Stacks * getTalentValue(
                            "A4 Stack DMG Bonus", 0.075));
        }
        if (constellation >= 6 && a4Stacks > 0) {
            action.addBonusStat(
                    StatType.CRIT_DMG,
                    a4Stacks * getTalentValue(
                            "C6 Stack CRIT DMG", 0.20));
        }
        simulator.performActionWithoutTimeAdvance(characterId, action);
        if (hit.reduceCharge && simulator.getEnemy() != null) {
            reduceFourWindsCharge(simulator);
        }
        if (hit.displayName.equals("Windbound Execution")
                && simulator.getEnemy() != null) {
            queueCommand(simulator, new PendingCommand(
                    currentTime + getTalentValue(
                            "Particle Travel Frames", 100.0) * FRAME,
                    CommandKind.PARTICLE,
                    0L));
        }
    }

    private double a1DamageBonus(
            double currentTime,
            Element hitElement) {
        if (hitElement != Element.ANEMO
                && hitElement != conversionElement) {
            return 0.0;
        }
        StatsContainer stats = getEffectiveStats(currentTime);
        if (initializedSimulator != null) {
            for (Buff buff
                    : initializedSimulator.getApplicableBuffs(this)) {
                if (!buff.isExpired(currentTime)) {
                    buff.apply(stats, currentTime);
                }
            }
        }
        return Math.min(
                getTalentValue("A1 Maximum DMG Bonus", 0.25),
                stats.getTotalAtk()
                        * getTalentValue(
                                "A1 ATK Conversion", 0.0001));
    }

    private double stanceCompositionMultiplier(
            CombatSimulator simulator) {
        int anemoCount = 0;
        EnumMap<Element, Integer> phecCounts =
                new EnumMap<>(Element.class);
        for (Character member : simulator.getPartyMembers()) {
            if (member.getElement() == Element.ANEMO) {
                anemoCount++;
            }
            if (isPhec(member.getElement())) {
                phecCounts.merge(member.getElement(), 1, Integer::sum);
            }
        }
        boolean twoAnemo = anemoCount >= 2;
        boolean twoPhec = false;
        for (Integer count : phecCounts.values()) {
            if (count >= 2) {
                twoPhec = true;
                break;
            }
        }
        if (twoAnemo && twoPhec) {
            return 2.2;
        }
        if (twoAnemo || twoPhec) {
            return 1.4;
        }
        return 1.0;
    }

    private void applyC4Buffs(
            CombatSimulator simulator,
            Element swirledElement,
            double currentTime) {
        double duration = getTalentValue("C4 Duration", 10.0);
        double bonus = getTalentValue(
                "C4 Elemental DMG Bonus", 0.20);
        simulator.applyTeamBuffNoStack(new SimpleBuff(
                "Varka C4 Anemo DMG",
                BuffId.VARKA_C4_ANEMO_DMG,
                duration,
                currentTime,
                stats -> stats.add(
                        StatType.ANEMO_DMG_BONUS, bonus))
                .sourcedBy(characterId));
        BuffId buffId = c4BuffId(swirledElement);
        StatType statType = elementalDamageStat(swirledElement);
        if (buffId != null && statType != null) {
            simulator.applyTeamBuffNoStack(new SimpleBuff(
                    "Varka C4 " + swirledElement + " DMG",
                    buffId,
                    duration,
                    currentTime,
                    stats -> stats.add(statType, bonus))
                    .sourcedBy(characterId));
        }
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
                case STANCE_START:
                    startStance(activeSimulator, command.generation);
                    break;
                case STANCE_END:
                    if (command.generation == stanceGeneration
                            && stanceActive
                            && activeSimulator.getCurrentTime() + EPSILON
                                    >= stanceExpirationTime) {
                        endStance();
                    }
                    break;
                case FOUR_WINDS_CHARGE:
                    restoreFourWindsCharge(
                            activeSimulator, command.generation);
                    break;
                case PARTICLE:
                    activeSimulator.getEnergyDistributor()
                            .distributeParticles(
                                    Element.ANEMO,
                                    getTalentValue("Particle Count", 6.0),
                                    ParticleType.PARTICLE);
                    break;
                case BURST_ENERGY:
                    spendBurstEnergy(activeSimulator.getCurrentTime());
                    break;
                default:
                    throw new IllegalStateException(
                            "Unknown Varka command " + command.kind);
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

    private boolean isStanceActiveAt(double currentTime) {
        return stanceActive
                && currentTime < stanceExpirationTime - EPSILON;
    }

    private void expireA4(double currentTime) {
        if (a4Stacks > 0
                && currentTime + EPSILON >= a4ExpirationTime) {
            a4Stacks = 0;
            a4ExpirationTime = Double.NEGATIVE_INFINITY;
        }
    }

    private double skillNormalFallback(int step, int hit) {
        return constellation >= 3
                ? STURM_NORMAL_C3[step][hit]
                : STURM_NORMAL_T9[step][hit];
    }

    private Element alternatingElement(int step, int hit) {
        int offset = step == 1 || step == 2 ? 1 : 0;
        return (hit + offset) % 2 == 0
                ? conversionElement : Element.ANEMO;
    }

    private ICDTag normalIcdTag(Element hitElement) {
        if (hitElement == Element.ANEMO) {
            return ICDTag.Varka_NormalWind;
        }
        if (hitElement == Element.PHYSICAL) {
            return ICDTag.NormalAttack;
        }
        return ICDTag.Varka_NormalElement;
    }

    private ICDTag extraIcdTag(Element hitElement) {
        if (hitElement == Element.ANEMO) {
            return ICDTag.Varka_ExtraWind;
        }
        if (hitElement == Element.PHYSICAL) {
            return ICDTag.NormalAttack;
        }
        return ICDTag.Varka_ExtraElement;
    }

    private static Element selectConversionElement(
            CombatSimulator simulator) {
        Element[] priority = {
            Element.PYRO,
            Element.HYDRO,
            Element.ELECTRO,
            Element.CRYO
        };
        for (Element candidate : priority) {
            for (Character member : simulator.getPartyMembers()) {
                if (member.getElement() == candidate) {
                    return candidate;
                }
            }
        }
        return Element.PHYSICAL;
    }

    private static boolean isPhec(Element element) {
        return element == Element.PYRO
                || element == Element.HYDRO
                || element == Element.ELECTRO
                || element == Element.CRYO;
    }

    private static StatType elementalDamageStat(Element element) {
        if (element == Element.PYRO) {
            return StatType.PYRO_DMG_BONUS;
        }
        if (element == Element.HYDRO) {
            return StatType.HYDRO_DMG_BONUS;
        }
        if (element == Element.ELECTRO) {
            return StatType.ELECTRO_DMG_BONUS;
        }
        if (element == Element.CRYO) {
            return StatType.CRYO_DMG_BONUS;
        }
        return null;
    }

    private static BuffId c4BuffId(Element element) {
        if (element == Element.PYRO) {
            return BuffId.VARKA_C4_PYRO_DMG;
        }
        if (element == Element.HYDRO) {
            return BuffId.VARKA_C4_HYDRO_DMG;
        }
        if (element == Element.ELECTRO) {
            return BuffId.VARKA_C4_ELECTRO_DMG;
        }
        if (element == Element.CRYO) {
            return BuffId.VARKA_C4_CRYO_DMG;
        }
        return null;
    }

    private static EnumMap<CharacterId, Double> copyCooldowns(
            Map<CharacterId, Double> source) {
        EnumMap<CharacterId, Double> copy =
                new EnumMap<>(CharacterId.class);
        copy.putAll(source);
        return copy;
    }

    private static List<PendingHit> copyHits(
            List<PendingHit> source) {
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

    private enum CommandKind {
        SKILL_COOLDOWN,
        STANCE_START,
        STANCE_END,
        FOUR_WINDS_CHARGE,
        PARTICLE,
        BURST_ENERGY
    }

    /** Immutable delayed Varka hit. */
    private static final class PendingHit {
        private final double time;
        private final String displayName;
        private final double multiplier;
        private final Element element;
        private final StatType bonusStat;
        private final ActionType actionType;
        private final ICDType icdType;
        private final ICDTag icdTag;
        private final double gauge;
        private final boolean a4Eligible;
        private final boolean reduceCharge;

        private PendingHit(
                double time,
                String displayName,
                double multiplier,
                Element element,
                StatType bonusStat,
                ActionType actionType,
                ICDType icdType,
                ICDTag icdTag,
                double gauge,
                boolean a4Eligible,
                boolean reduceCharge) {
            this.time = time;
            this.displayName = displayName;
            this.multiplier = multiplier;
            this.element = element;
            this.bonusStat = bonusStat;
            this.actionType = actionType;
            this.icdType = icdType;
            this.icdTag = icdTag;
            this.gauge = gauge;
            this.a4Eligible = a4Eligible;
            this.reduceCharge = reduceCharge;
        }

        private PendingHit copy() {
            return new PendingHit(
                    time,
                    displayName,
                    multiplier,
                    element,
                    bonusStat,
                    actionType,
                    icdType,
                    icdTag,
                    gauge,
                    a4Eligible,
                    reduceCharge);
        }
    }

    /** Immutable delayed Varka state command. */
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

    /** Immutable Varka-owned snapshot payload. */
    private static final class VarkaState implements State {
        private final Varka owner;
        private final int normalAttackStep;
        private final long stanceGeneration;
        private final boolean stanceActive;
        private final double stanceExpirationTime;
        private final Element conversionElement;
        private final int fourWindsCharges;
        private final int fourWindsRechargesStarted;
        private final int fourWindsReductionStacks;
        private final long fourWindsChargeGeneration;
        private final double nextFourWindsChargeTime;
        private final boolean c1FirstSpecialAvailable;
        private final double c6FreeSkillUntil;
        private final double c6FreeChargeUntil;
        private final int a4Stacks;
        private final double a4ExpirationTime;
        private final EnumMap<CharacterId, Double> a4SourceCooldowns;
        private final List<PendingHit> pendingHits;
        private final List<PendingCommand> pendingCommands;

        private VarkaState(
                Varka owner,
                int normalAttackStep,
                long stanceGeneration,
                boolean stanceActive,
                double stanceExpirationTime,
                Element conversionElement,
                int fourWindsCharges,
                int fourWindsRechargesStarted,
                int fourWindsReductionStacks,
                long fourWindsChargeGeneration,
                double nextFourWindsChargeTime,
                boolean c1FirstSpecialAvailable,
                double c6FreeSkillUntil,
                double c6FreeChargeUntil,
                int a4Stacks,
                double a4ExpirationTime,
                Map<CharacterId, Double> a4SourceCooldowns,
                List<PendingHit> pendingHits,
                List<PendingCommand> pendingCommands) {
            this.owner = owner;
            this.normalAttackStep = normalAttackStep;
            this.stanceGeneration = stanceGeneration;
            this.stanceActive = stanceActive;
            this.stanceExpirationTime = stanceExpirationTime;
            this.conversionElement = conversionElement;
            this.fourWindsCharges = fourWindsCharges;
            this.fourWindsRechargesStarted = fourWindsRechargesStarted;
            this.fourWindsReductionStacks = fourWindsReductionStacks;
            this.fourWindsChargeGeneration = fourWindsChargeGeneration;
            this.nextFourWindsChargeTime = nextFourWindsChargeTime;
            this.c1FirstSpecialAvailable = c1FirstSpecialAvailable;
            this.c6FreeSkillUntil = c6FreeSkillUntil;
            this.c6FreeChargeUntil = c6FreeChargeUntil;
            this.a4Stacks = a4Stacks;
            this.a4ExpirationTime = a4ExpirationTime;
            this.a4SourceCooldowns = copyCooldowns(a4SourceCooldowns);
            this.pendingHits = copyHits(pendingHits);
            this.pendingCommands = copyCommands(pendingCommands);
        }
    }
}

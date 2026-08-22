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
import mechanics.reaction.ReactionResult;
import model.entity.ArtifactSet;
import model.entity.Character;
import model.entity.Enemy;
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
import simulation.action.HitlagProfile;
import simulation.action.SkillActionMode;
import simulation.event.SimpleTimerEvent;

/**
 * Kaedehara Kazuha's stationary single-target Swirl support kit through C6.
 *
 * <p>Timings, multipliers, gauges, absorption priority, and snapshots follow
 * pinned gcsim {@code ef41805d} and maintained KQM Kazuha evidence. A Skill
 * command includes the immediate high Midare Ranzan used by this bounded
 * rotation model.</p>
 *
 * <p>Suction, position, weight, fall damage, gliding, stamina, incoming damage,
 * self or environmental aura, special Plunge skips, and multi-target geometry
 * are outside this slice.</p>
 */
public final class KaedeharaKazuha extends Character implements
        ReactionAwareCharacter,
        SimulatorInitializedCharacterEffect,
        SnapshotAwareCharacterEffect,
        SwitchAwareCharacter {
    private static final double FRAME = 1.0 / 60.0;
    private static final double EPSILON = 1e-9;
    private static final double PARTICLE_TRAVEL = 100.0 * FRAME;
    private static final int[][] NORMAL_HITMARKS = {
        { 13 }, { 11 }, { 16, 26 }, { 16 }, { 15, 19, 28 }
    };
    private static final int[] NORMAL_DURATIONS = { 22, 26, 41, 46, 80 };
    private static final String[][] NORMAL_KEYS = {
        { "N1" }, { "N2" }, { "N3-1", "N3-2" }, { "N4" },
        { "N5-1", "N5-2", "N5-3" }
    };
    private static final double[][] NORMAL_MULTIPLIERS = {
        { 0.82634 }, { 0.83108 }, { 0.474, 0.5688 }, { 1.11548 },
        { 0.4661, 0.4661, 0.4661 }
    };
    private static final int[] BURST_DOT_FRAMES = {
        140, 257, 374, 491, 608
    };
    private static final Element[] ABSORPTION_PRIORITY = {
        Element.PYRO, Element.HYDRO, Element.ELECTRO, Element.CRYO
    };
    /**
     * Per-hit metadata from gcsim pinned at
     * {@code 3647a07a7cc3004bc1e79d9bb5f7444de20dceaa}.
     */
    private static final HitlagProfile NORMAL_HITLAG_SHORT =
            new HitlagProfile(0.03, 0.01, true, false, false);
    private static final HitlagProfile NORMAL_HITLAG_N3_FIRST =
            new HitlagProfile(0.01, 0.01, false, false, false);
    private static final HitlagProfile NORMAL_HITLAG_N3_SECOND =
            new HitlagProfile(0.05, 0.01, true, false, false);
    private static final HitlagProfile NORMAL_HITLAG_N4 =
            new HitlagProfile(0.06, 0.01, true, false, false);
    private static final HitlagProfile NORMAL_HITLAG_N5_EDGE =
            new HitlagProfile(0.0, 0.05, true, false, false);
    private static final HitlagProfile BURST_INITIAL_HITLAG =
            new HitlagProfile(0.05, 0.05, false, false, false);

    private CombatSimulator initializedSimulator;
    private int normalAttackStep;
    private long skillGeneration;
    private long burstGeneration;
    private Element skillAbsorption;
    private Element burstAbsorption;
    private double c2ExpirationTime = Double.NEGATIVE_INFINITY;
    private double c6ExpirationTime = Double.NEGATIVE_INFINITY;
    private boolean resolvingC6BasicHit;
    private List<PendingHit> pendingHits = new ArrayList<>();
    private List<PendingCommand> pendingCommands = new ArrayList<>();

    /** Constructs repository-default C6 Kazuha. */
    public KaedeharaKazuha(Weapon weapon, ArtifactSet artifacts) {
        this(weapon, artifacts, TalentDataManager.getInstance(), 6);
    }

    /** Constructs Kazuha at an explicit constellation. */
    public KaedeharaKazuha(
            Weapon weapon,
            ArtifactSet artifacts,
            int constellation) {
        this(weapon, artifacts, TalentDataManager.getInstance(), constellation);
    }

    /** Constructs Kazuha with an injectable talent data source. */
    public KaedeharaKazuha(
            Weapon weapon,
            ArtifactSet artifacts,
            TalentDataSource talentData,
            int constellation) {
        super(talentData);
        if (constellation < 0 || constellation > 6) {
            throw new IllegalArgumentException(
                    "Kaedehara Kazuha constellation must be between 0 and 6");
        }
        name = "Kaedehara Kazuha";
        characterId = CharacterId.KAEDEHARA_KAZUHA;
        element = Element.ANEMO;
        this.weapon = weapon;
        this.artifacts = new ArtifactSet[] { artifacts };
        this.constellation = constellation;
        baseStats.set(StatType.BASE_HP,
                getTalentValue("Base HP", 13348.0));
        baseStats.set(StatType.BASE_ATK,
                getTalentValue("Base ATK", 297.0));
        baseStats.set(StatType.BASE_DEF,
                getTalentValue("Base DEF", 807.0));
        baseStats.add(StatType.ELEMENTAL_MASTERY,
                getTalentValue("Ascension Elemental Mastery", 115.0));
        setSkillCD(6.0);
        setBurstCD(15.0);
    }

    /** Binds reaction and delayed-event state to exactly one simulator. */
    @Override
    public void initializeForSimulator(CombatSimulator simulator) {
        if (simulator == null) {
            throw new IllegalArgumentException("Kazuha simulator is required");
        }
        if (initializedSimulator != null
                && initializedSimulator != simulator) {
            throw new IllegalStateException(
                    "Kazuha cannot be reused across simulators");
        }
        if (initializedSimulator == simulator) {
            return;
        }
        if (!simulator.getPartyMembers().contains(this)) {
            throw new IllegalArgumentException(
                    "Kazuha must belong to the target simulator party");
        }
        initializedSimulator = simulator;
        simulator.addReactionListener(this);
    }

    /** Captures all Kazuha-owned counters, windows, and delayed work. */
    @Override
    public State captureCharacterState() {
        return new KazuhaState(
                this,
                normalAttackStep,
                skillGeneration,
                burstGeneration,
                skillAbsorption,
                burstAbsorption,
                c2ExpirationTime,
                c6ExpirationTime,
                pendingHits,
                pendingCommands);
    }

    /** Accepts only state captured from this exact Kazuha instance. */
    @Override
    public boolean acceptsCharacterState(State state) {
        return state instanceof KazuhaState
                && ((KazuhaState) state).owner == this;
    }

    /** Restores Kazuha state and schedules each surviving event once. */
    @Override
    public void restoreCharacterState(
            State state,
            CombatSimulator simulator) {
        if (!acceptsCharacterState(state)) {
            throw new IllegalArgumentException("Unexpected Kazuha state");
        }
        initializeForSimulator(simulator);
        KazuhaState restored = (KazuhaState) state;
        normalAttackStep = restored.normalAttackStep;
        skillGeneration = restored.skillGeneration;
        burstGeneration = restored.burstGeneration;
        skillAbsorption = restored.skillAbsorption;
        burstAbsorption = restored.burstAbsorption;
        c2ExpirationTime = restored.c2ExpirationTime;
        c6ExpirationTime = restored.c6ExpirationTime;
        resolvingC6BasicHit = false;
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

    /** Returns Kazuha's 60-Energy Burst cost. */
    @Override
    public double getEnergyCost() {
        return getTalentValue("Energy Cost", 60.0);
    }

    /** Kazuha's passives are action- or reaction-driven. */
    @Override
    public void applyPassive(StatsContainer stats) {
        // A1/A4 and constellations depend on runtime state.
    }

    /** Supports both Press and Hold Skill inputs. */
    @Override
    protected boolean supportsSkillActionMode(SkillActionMode mode) {
        return mode == SkillActionMode.PRESS
                || mode == SkillActionMode.HOLD;
    }

    /** Resets the five-step Normal string on switch. */
    @Override
    public void onSwitchOut(CombatSimulator simulator) {
        normalAttackStep = 0;
    }

    /** Returns the C2 field's half-open expiration timestamp. */
    public double getC2ExpirationTime() {
        return c2ExpirationTime;
    }

    /** Returns the C6 infusion's half-open expiration timestamp. */
    public double getC6ExpirationTime() {
        return c6ExpirationTime;
    }

    /** Returns the element selected by the latest Skill hit. */
    public Element getSkillAbsorption() {
        return skillAbsorption;
    }

    /** Returns the element selected by the latest Burst field. */
    public Element getBurstAbsorption() {
        return burstAbsorption;
    }

    /** Applies A4 from an actual Kazuha-triggered Swirl. */
    @Override
    public void onReaction(
            ReactionResult result,
            Character source,
            double time,
            CombatSimulator simulator) {
        if (simulator != initializedSimulator
                || source != this
                || result == null
                || !result.isSwirl()
                || resolvingC6BasicHit) {
            return;
        }
        Element swirledElement = result.getSwirlElement();
        BuffId buffId = a4BuffId(swirledElement);
        StatType bonusStat = swirledElement == null
                ? null : swirledElement.getBonusStatType();
        if (buffId == null || bonusStat == null) {
            return;
        }
        double elementalMastery = captureLiveStats(time).get(
                StatType.ELEMENTAL_MASTERY);
        double bonus = elementalMastery
                * getTalentValue("A4 Elemental DMG Bonus Per EM", 0.0004);
        simulator.applyTeamBuffNoStack(new SimpleBuff(
                "Kazuha Poetics of Fuubutsu " + swirledElement.name(),
                buffId,
                getTalentValue("A4 Duration", 8.0),
                time,
                stats -> stats.add(bonusStat, bonus))
                .sourcedBy(characterId));
    }

    /** Dispatches Kazuha's represented action set. */
    @Override
    public void onAction(
            CharacterActionRequest request,
            CombatSimulator simulator) {
        if (request == null) {
            throw new IllegalArgumentException("Kazuha action is required");
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
                chihayaburu(simulator, request.getSkillMode());
                break;
            case BURST:
                kazuhaSlash(simulator);
                break;
            default:
                throw new IllegalArgumentException(
                        "Unsupported action for Kazuha: " + request.getKey());
        }
    }

    private void normalAttack(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        int step = normalAttackStep;
        for (int hit = 0; hit < NORMAL_HITMARKS[step].length; hit++) {
            queueCommand(simulator, new PendingCommand(
                    castTime + NORMAL_HITMARKS[step][hit] * FRAME,
                    CommandKind.BASIC_HIT,
                    0L,
                    step,
                    hit));
        }
        normalAttackStep = (normalAttackStep + 1) % 5;
        simulator.advanceTime(NORMAL_DURATIONS[step] * FRAME);
    }

    private void chargedAttack(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        queueCommand(simulator, new PendingCommand(
                castTime + 20.0 * FRAME,
                CommandKind.CHARGED_HIT,
                0L,
                0,
                0));
        queueCommand(simulator, new PendingCommand(
                castTime + 20.0 * FRAME,
                CommandKind.CHARGED_HIT,
                0L,
                1,
                0));
        simulator.advanceTime(getTalentValue(
                "Charged Recovery Frames", 55.0) * FRAME);
    }

    private void highPlunge(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        queueCommand(simulator, new PendingCommand(
                castTime + 40.0 * FRAME,
                CommandKind.EXTERNAL_PLUNGE_HIT,
                0L,
                0,
                0));
        simulator.advanceTime(41.0 * FRAME);
    }

    private void chihayaburu(
            CombatSimulator simulator,
            SkillActionMode mode) {
        if (mode != SkillActionMode.PRESS && mode != SkillActionMode.HOLD) {
            throw new IllegalArgumentException(
                    "Unsupported Kazuha Skill mode: " + mode);
        }
        boolean hold = mode == SkillActionMode.HOLD;
        double castTime = simulator.getCurrentTime();
        long generation = ++skillGeneration;
        skillAbsorption = null;
        // C4's source has no resolved hitmark; this slice grants it at input.
        if (constellation >= 4
                && getCurrentEnergy()
                        < getTalentValue("C4 Energy Threshold", 45.0)) {
            receiveFlatEnergy(getTalentValue(
                    hold ? "C4 Hold Flat Energy" : "C4 Press Flat Energy",
                    hold ? 4.0 : 3.0));
        }
        StatsContainer skillSnapshot = captureLiveStats(castTime);
        int hitFrame = hold ? 33 : 10;
        int cooldownFrame = hold ? 31 : 8;
        int plungeInputFrame = hold ? 58 : 24;
        queueCommand(simulator, new PendingCommand(
                castTime + cooldownFrame * FRAME,
                CommandKind.SKILL_COOLDOWN,
                generation,
                hold ? 1 : 0,
                0));
        queueHit(simulator, new PendingHit(
                castTime + hitFrame * FRAME,
                HitKind.SKILL,
                hold ? 1 : 0,
                hold ? 4 : 3,
                generation,
                skillSnapshot,
                null));
        int absorptionChecks = hold ? 6 : 1;
        for (int index = 0; index < absorptionChecks; index++) {
            queueCommand(simulator, new PendingCommand(
                    castTime + (1.0 + index * 6.0) * FRAME,
                    CommandKind.SKILL_ABSORPTION,
                    generation,
                    0,
                    0));
        }
        queueCommand(simulator, new PendingCommand(
                castTime + plungeInputFrame * FRAME,
                CommandKind.MIDARE_INPUT,
                generation,
                hold ? 1 : 0,
                0));
        simulator.advanceTime((hold ? 99.0 : 61.0) * FRAME);
    }

    private void kazuhaSlash(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        long generation = ++burstGeneration;
        burstAbsorption = null;
        markBurstCooldownUsed(castTime, simulator.getApplicableBuffs(this));
        if (constellation >= 1) {
            resetSkillCooldown(castTime);
        }
        StatsContainer initialSnapshot = captureLiveStats(castTime);
        queueCommand(simulator, new PendingCommand(
                castTime + 5.0 * FRAME,
                CommandKind.BURST_ENERGY,
                generation,
                0,
                0));
        queueCommand(simulator, new PendingCommand(
                castTime + getTalentValue(
                        "Burst Snapshot Frame", 81.0) * FRAME,
                CommandKind.BURST_SNAPSHOT,
                generation,
                0,
                0));
        queueHit(simulator, new PendingHit(
                castTime + getTalentValue(
                        "Burst Initial Hit Frame", 82.0) * FRAME,
                HitKind.BURST_INITIAL,
                0,
                0,
                generation,
                initialSnapshot,
                null));
        simulator.advanceTime(93.0 * FRAME);
    }

    private void resolveBasicHit(
            CombatSimulator simulator,
            int step,
            int hit) {
        String key = NORMAL_KEYS[step][hit];
        AttackAction action = basicAttack(
                "Garyuu Bladework " + key,
                getTalentValue(key, NORMAL_MULTIPLIERS[step][hit]),
                StatType.NORMAL_ATTACK_DMG_BONUS,
                ActionType.NORMAL);
        HitlagProfile hitlagProfile = normalHitlag(step, hit);
        if (hitlagProfile != null) {
            action.setHitlagProfile(hitlagProfile);
        }
        resolvingC6BasicHit = action.getElement() == Element.ANEMO;
        try {
            simulator.performActionWithoutTimeAdvance(characterId, action);
        } finally {
            resolvingC6BasicHit = false;
        }
    }

    private void resolveChargedHit(
            CombatSimulator simulator,
            int hit) {
        String key = hit == 0 ? "Charged-1" : "Charged-2";
        AttackAction action = basicAttack(
                "Garyuu Bladework " + key,
                getTalentValue(key, hit == 0 ? 0.79 : 1.37144),
                StatType.CHARGED_ATTACK_DMG_BONUS,
                ActionType.CHARGE);
        resolvingC6BasicHit = action.getElement() == Element.ANEMO;
        try {
            simulator.performActionWithoutTimeAdvance(characterId, action);
        } finally {
            resolvingC6BasicHit = false;
        }
    }

    private void resolveExternalPlunge(CombatSimulator simulator) {
        AttackAction action = basicAttack(
                "Garyuu Bladework High Plunge",
                getTalentValue("High Plunge", 3.75499),
                StatType.PLUNGING_ATTACK_DMG_BONUS,
                ActionType.PLUNGE);
        resolvingC6BasicHit = action.getElement() == Element.ANEMO;
        try {
            simulator.performActionWithoutTimeAdvance(characterId, action);
        } finally {
            resolvingC6BasicHit = false;
        }
    }

    private AttackAction basicAttack(
            String displayName,
            double multiplier,
            StatType bonusStat,
            ActionType actionType) {
        double currentTime = initializedSimulator.getCurrentTime();
        boolean infused = constellation >= 6
                && currentTime < c6ExpirationTime;
        Element attackElement = infused ? Element.ANEMO : Element.PHYSICAL;
        boolean sharedIcd = infused && actionType != ActionType.PLUNGE;
        AttackAction action = attack(
                displayName,
                multiplier,
                attackElement,
                bonusStat,
                actionType,
                sharedIcd ? ICDType.Standard : ICDType.None,
                sharedIcd ? ICDTag.Kazuha_C6_Infusion : ICDTag.None,
                infused ? 1.0 : 0.0);
        if (infused) {
            StatsContainer snapshot = captureLiveStats(currentTime);
            action.setStatSnapshot(snapshot);
            action.addBonusStat(
                    bonusStat,
                    snapshot.get(StatType.ELEMENTAL_MASTERY)
                            * getTalentValue(
                                    "C6 Normal Charged Plunge DMG Bonus Per EM",
                                    0.002));
        }
        return action;
    }

    private void resolveHit(
            CombatSimulator simulator,
            PendingHit hit) {
        if ((hit.kind == HitKind.SKILL
                || hit.kind == HitKind.A1_ABSORBED
                || hit.kind == HitKind.MIDARE)
                && hit.generation != skillGeneration) {
            return;
        }
        if ((hit.kind == HitKind.BURST_INITIAL
                || hit.kind == HitKind.BURST_DOT
                || hit.kind == HitKind.BURST_ABSORBED_DOT)
                && hit.generation != burstGeneration) {
            return;
        }
        switch (hit.kind) {
            case SKILL:
                resolveSkill(simulator, hit);
                break;
            case A1_ABSORBED:
                resolveA1(simulator, hit);
                break;
            case MIDARE:
                resolveMidare(simulator, hit);
                break;
            case BURST_INITIAL:
                resolveBurstInitial(simulator, hit);
                break;
            case BURST_DOT:
                resolveBurstDot(simulator, hit, false);
                break;
            case BURST_ABSORBED_DOT:
                resolveBurstDot(simulator, hit, true);
                break;
            default:
                throw new IllegalStateException("Unknown Kazuha hit kind");
        }
    }

    private void resolveSkill(
            CombatSimulator simulator,
            PendingHit hit) {
        boolean hold = hit.index == 1;
        activateC6(simulator, simulator.getCurrentTime());
        AttackAction action = attack(
                hold ? "Chihayaburu Hold" : "Chihayaburu Press",
                getTalentValue(
                        hold
                                ? (constellation >= 3
                                        ? "Hold C3" : "Hold")
                                : (constellation >= 3
                                        ? "Press C3" : "Press"),
                        hold
                                ? (constellation >= 3 ? 5.216 : 4.4336)
                                : (constellation >= 3 ? 3.84 : 3.264)),
                Element.ANEMO,
                StatType.SKILL_DMG_BONUS,
                ActionType.SKILL,
                ICDType.None,
                ICDTag.None,
                hold ? 2.0 : 1.0);
        action.setStatSnapshot(hit.snapshot);
        simulator.performActionWithoutTimeAdvance(characterId, action);
        if (simulator.getEnemy() != null) {
            queueCommand(simulator, new PendingCommand(
                    simulator.getCurrentTime() + PARTICLE_TRAVEL,
                    CommandKind.PARTICLE,
                    0L,
                    hit.subIndex,
                    0));
        }
    }

    private void resolveA1(
            CombatSimulator simulator,
            PendingHit hit) {
        if (hit.absorbedElement == null) {
            return;
        }
        AttackAction action = attack(
                "Chihayaburu Soumon Swordsmanship",
                getTalentValue("A1 Additional Elemental DMG", 2.0),
                hit.absorbedElement,
                StatType.PLUNGING_ATTACK_DMG_BONUS,
                ActionType.PLUNGE,
                ICDType.None,
                ICDTag.None,
                getTalentValue("A1 Additional Gauge Units", 1.0));
        action.setStatSnapshot(hit.snapshot);
        applyC6PlungeBonus(action, simulator.getCurrentTime());
        simulator.performActionWithoutTimeAdvance(characterId, action);
    }

    private void resolveMidare(
            CombatSimulator simulator,
            PendingHit hit) {
        AttackAction action = attack(
                "Midare Ranzan High Plunge",
                getTalentValue("High Plunge", 3.75499),
                Element.ANEMO,
                StatType.PLUNGING_ATTACK_DMG_BONUS,
                ActionType.PLUNGE,
                ICDType.None,
                ICDTag.None,
                1.0);
        action.setStatSnapshot(hit.snapshot);
        applyC6PlungeBonus(action, simulator.getCurrentTime());
        simulator.performActionWithoutTimeAdvance(characterId, action);
    }

    private void applyC6PlungeBonus(
            AttackAction action,
            double currentTime) {
        if (constellation < 6 || currentTime >= c6ExpirationTime) {
            return;
        }
        action.addBonusStat(
                StatType.PLUNGING_ATTACK_DMG_BONUS,
                captureLiveStats(currentTime).get(StatType.ELEMENTAL_MASTERY)
                        * getTalentValue(
                                "C6 Normal Charged Plunge DMG Bonus Per EM",
                                0.002));
    }

    private void resolveBurstInitial(
            CombatSimulator simulator,
            PendingHit hit) {
        activateC6(simulator, simulator.getCurrentTime());
        AttackAction action = attack(
                "Kazuha Slash Initial",
                getTalentValue(
                        constellation >= 5
                                ? "Burst Initial C5" : "Burst Initial",
                        constellation >= 5 ? 5.248 : 4.4608),
                Element.ANEMO,
                StatType.BURST_DMG_BONUS,
                ActionType.BURST,
                ICDType.None,
                ICDTag.None,
                getTalentValue("Burst Initial Gauge Units", 2.0));
        action.setStatSnapshot(hit.snapshot);
        action.setHitlagProfile(BURST_INITIAL_HITLAG);
        simulator.performActionWithoutTimeAdvance(characterId, action);
    }

    private static HitlagProfile normalHitlag(int step, int hit) {
        if (step <= 1) {
            return NORMAL_HITLAG_SHORT;
        }
        if (step == 2) {
            return hit == 0
                    ? NORMAL_HITLAG_N3_FIRST
                    : NORMAL_HITLAG_N3_SECOND;
        }
        if (step == 3) {
            return NORMAL_HITLAG_N4;
        }
        return hit == 1 ? null : NORMAL_HITLAG_N5_EDGE;
    }

    private void resolveBurstDot(
            CombatSimulator simulator,
            PendingHit hit,
            boolean absorbed) {
        Element absorbedElement = hit.absorbedElement == null
                ? burstAbsorption : hit.absorbedElement;
        if (absorbed && absorbedElement == null) {
            return;
        }
        AttackAction action = attack(
                absorbed ? "Kazuha Slash Absorbed DoT" : "Kazuha Slash DoT",
                getTalentValue(
                        absorbed
                                ? (constellation >= 5
                                        ? "Burst Absorbed DoT C5"
                                        : "Burst Absorbed DoT")
                                : (constellation >= 5
                                        ? "Burst DoT C5" : "Burst DoT"),
                        absorbed
                                ? (constellation >= 5 ? 0.72 : 0.612)
                                : (constellation >= 5 ? 2.4 : 2.04)),
                absorbed ? absorbedElement : Element.ANEMO,
                StatType.BURST_DMG_BONUS,
                ActionType.BURST,
                ICDType.None,
                ICDTag.None,
                getTalentValue("Burst DoT Gauge Units", 1.0));
        action.setStatSnapshot(hit.snapshot);
        simulator.performActionWithoutTimeAdvance(characterId, action);
    }

    private void startSkillCooldown(
            CombatSimulator simulator,
            long generation,
            boolean hold) {
        if (generation != skillGeneration) {
            return;
        }
        double baseCooldown = getTalentValue(
                hold ? "Hold Skill Cooldown" : "Press Skill Cooldown",
                hold ? 9.0 : 6.0);
        double ratio = constellation >= 1
                ? getTalentValue("C1 Skill Cooldown Ratio", 0.9) : 1.0;
        setSkillCD(baseCooldown * ratio);
        markSkillUsed(
                simulator.getCurrentTime(),
                simulator.getApplicableBuffs(this));
        setSkillCD(6.0);
    }

    private void startMidare(
            CombatSimulator simulator,
            long generation,
            boolean hold) {
        if (generation != skillGeneration) {
            return;
        }
        StatsContainer snapshot = captureLiveStats(simulator.getCurrentTime());
        int a1Frames = hold ? 40 : 36;
        int plungeFrames = hold ? 41 : 37;
        if (skillAbsorption != null) {
            queueHit(simulator, new PendingHit(
                    simulator.getCurrentTime() + a1Frames * FRAME,
                    HitKind.A1_ABSORBED,
                    0,
                    0,
                    generation,
                    snapshot,
                    skillAbsorption));
        }
        queueHit(simulator, new PendingHit(
                simulator.getCurrentTime() + plungeFrames * FRAME,
                HitKind.MIDARE,
                0,
                0,
                generation,
                snapshot,
                null));
    }

    private void snapshotBurst(
            CombatSimulator simulator,
            long generation) {
        if (generation != burstGeneration) {
            return;
        }
        burstAbsorption = selectAbsorption(simulator);
        StatsContainer snapshot = captureLiveStats(simulator.getCurrentTime());
        double castTime = simulator.getCurrentTime()
                - getTalentValue("Burst Snapshot Frame", 81.0) * FRAME;
        if (constellation >= 2) {
            activateC2(
                    simulator,
                    castTime + (BURST_DOT_FRAMES[
                            BURST_DOT_FRAMES.length - 1] + 1.0) * FRAME);
        }
        for (int index = 0; index < BURST_DOT_FRAMES.length; index++) {
            double time = castTime + BURST_DOT_FRAMES[index] * FRAME;
            queueHit(simulator, new PendingHit(
                    time,
                    HitKind.BURST_DOT,
                    index,
                    0,
                    generation,
                    snapshot,
                    null));
            queueHit(simulator, new PendingHit(
                    time,
                    HitKind.BURST_ABSORBED_DOT,
                    index,
                    0,
                    generation,
                    snapshot,
                    null));
        }
        for (int index = 1; index <= 16; index++) {
            queueCommand(simulator, new PendingCommand(
                    simulator.getCurrentTime() + index * 18.0 * FRAME,
                    CommandKind.BURST_ABSORPTION,
                    generation,
                    0,
                    0));
        }
    }

    private void activateC2(
            CombatSimulator simulator,
            double expirationTime) {
        double currentTime = simulator.getCurrentTime();
        double duration = expirationTime - currentTime;
        double amount = getTalentValue("C2 Elemental Mastery", 200.0);
        c2ExpirationTime = expirationTime;
        removeBuff(BuffId.KAZUHA_C2_OWNER_ELEMENTAL_MASTERY);
        addBuff(new SimpleBuff(
                "Kazuha Yamaarashi Tailwind Owner",
                BuffId.KAZUHA_C2_OWNER_ELEMENTAL_MASTERY,
                duration,
                currentTime,
                stats -> {
                    if (initializedSimulator.getActiveCharacter() != this) {
                        stats.add(StatType.ELEMENTAL_MASTERY, amount);
                    }
                }));
        simulator.applyFieldBuff(new SimpleBuff(
                "Kazuha Yamaarashi Tailwind Active",
                BuffId.KAZUHA_C2_ACTIVE_ELEMENTAL_MASTERY,
                duration,
                currentTime,
                stats -> stats.add(StatType.ELEMENTAL_MASTERY, amount))
                .sourcedBy(characterId));
    }

    private void activateC6(
            CombatSimulator simulator,
            double currentTime) {
        if (constellation < 6) {
            return;
        }
        double duration = getTalentValue("C6 Infusion Duration", 5.0);
        c6ExpirationTime = currentTime + duration;
        removeBuff(BuffId.KAZUHA_C6_INFUSION);
        addBuff(new SimpleBuff(
                "Kazuha Crimson Momiji Infusion",
                BuffId.KAZUHA_C6_INFUSION,
                duration,
                currentTime,
                stats -> {
                    // Element and live EM scaling are resolved per basic hit.
                }));
    }

    private Element selectAbsorption(CombatSimulator simulator) {
        Enemy enemy = simulator.getEnemy();
        if (enemy == null) {
            return null;
        }
        double currentTime = simulator.getCurrentTime();
        for (Element candidate : ABSORPTION_PRIORITY) {
            if (enemy.getAuraUnits(candidate, currentTime) > 0.0) {
                return candidate;
            }
        }
        return null;
    }

    private BuffId a4BuffId(Element element) {
        if (element == null) {
            return null;
        }
        switch (element) {
            case PYRO:
                return BuffId.KAZUHA_A4_PYRO_DMG_BONUS;
            case HYDRO:
                return BuffId.KAZUHA_A4_HYDRO_DMG_BONUS;
            case ELECTRO:
                return BuffId.KAZUHA_A4_ELECTRO_DMG_BONUS;
            case CRYO:
                return BuffId.KAZUHA_A4_CRYO_DMG_BONUS;
            default:
                return null;
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
                case BASIC_HIT:
                    resolveBasicHit(
                            activeSimulator, command.index, command.subIndex);
                    break;
                case CHARGED_HIT:
                    resolveChargedHit(activeSimulator, command.index);
                    break;
                case EXTERNAL_PLUNGE_HIT:
                    resolveExternalPlunge(activeSimulator);
                    break;
                case SKILL_COOLDOWN:
                    startSkillCooldown(
                            activeSimulator,
                            command.generation,
                            command.index == 1);
                    break;
                case SKILL_ABSORPTION:
                    if (command.generation == skillGeneration
                            && skillAbsorption == null) {
                        skillAbsorption = selectAbsorption(activeSimulator);
                    }
                    break;
                case MIDARE_INPUT:
                    startMidare(
                            activeSimulator,
                            command.generation,
                            command.index == 1);
                    break;
                case BURST_ENERGY:
                    if (command.generation == burstGeneration) {
                        spendBurstEnergy(activeSimulator.getCurrentTime());
                    }
                    break;
                case BURST_SNAPSHOT:
                    snapshotBurst(activeSimulator, command.generation);
                    break;
                case BURST_ABSORPTION:
                    if (command.generation == burstGeneration
                            && burstAbsorption == null) {
                        burstAbsorption = selectAbsorption(activeSimulator);
                    }
                    break;
                case PARTICLE:
                    activeSimulator.getEnergyDistributor().distributeParticles(
                            Element.ANEMO,
                            command.index,
                            ParticleType.PARTICLE);
                    break;
                default:
                    throw new IllegalStateException(
                            "Unknown Kazuha command kind");
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
        SKILL,
        A1_ABSORBED,
        MIDARE,
        BURST_INITIAL,
        BURST_DOT,
        BURST_ABSORBED_DOT
    }

    private enum CommandKind {
        BASIC_HIT,
        CHARGED_HIT,
        EXTERNAL_PLUNGE_HIT,
        SKILL_COOLDOWN,
        SKILL_ABSORPTION,
        MIDARE_INPUT,
        BURST_ENERGY,
        BURST_SNAPSHOT,
        BURST_ABSORPTION,
        PARTICLE
    }

    /** Immutable delayed Kazuha hit description. */
    private static final class PendingHit {
        private final double time;
        private final HitKind kind;
        private final int index;
        private final int subIndex;
        private final long generation;
        private final StatsContainer snapshot;
        private final Element absorbedElement;

        private PendingHit(
                double time,
                HitKind kind,
                int index,
                int subIndex,
                long generation,
                StatsContainer snapshot,
                Element absorbedElement) {
            this.time = time;
            this.kind = kind;
            this.index = index;
            this.subIndex = subIndex;
            this.generation = generation;
            this.snapshot = snapshot == null
                    ? null : snapshot.merge(null);
            this.absorbedElement = absorbedElement;
        }

        private PendingHit copy() {
            return new PendingHit(
                    time,
                    kind,
                    index,
                    subIndex,
                    generation,
                    snapshot,
                    absorbedElement);
        }
    }

    /** Immutable delayed Kazuha command description. */
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

    /** Immutable Kazuha-owned simulator snapshot payload. */
    private static final class KazuhaState implements State {
        private final KaedeharaKazuha owner;
        private final int normalAttackStep;
        private final long skillGeneration;
        private final long burstGeneration;
        private final Element skillAbsorption;
        private final Element burstAbsorption;
        private final double c2ExpirationTime;
        private final double c6ExpirationTime;
        private final List<PendingHit> pendingHits;
        private final List<PendingCommand> pendingCommands;

        private KazuhaState(
                KaedeharaKazuha owner,
                int normalAttackStep,
                long skillGeneration,
                long burstGeneration,
                Element skillAbsorption,
                Element burstAbsorption,
                double c2ExpirationTime,
                double c6ExpirationTime,
                List<PendingHit> pendingHits,
                List<PendingCommand> pendingCommands) {
            this.owner = owner;
            this.normalAttackStep = normalAttackStep;
            this.skillGeneration = skillGeneration;
            this.burstGeneration = burstGeneration;
            this.skillAbsorption = skillAbsorption;
            this.burstAbsorption = burstAbsorption;
            this.c2ExpirationTime = c2ExpirationTime;
            this.c6ExpirationTime = c6ExpirationTime;
            this.pendingHits = copyHits(pendingHits);
            this.pendingCommands = copyCommands(pendingCommands);
        }
    }
}

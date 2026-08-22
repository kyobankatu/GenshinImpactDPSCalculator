package model.character;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import mechanics.buff.ActiveCharacterBuff;
import mechanics.buff.Buff;
import mechanics.buff.BuffId;
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
import simulation.action.HitlagProfile;
import simulation.action.SkillActionMode;
import simulation.event.SimpleTimerEvent;

/**
 * Durin's deterministic fixed-target dual-form vertical slice.
 *
 * <p>Level-90 sword basics, high Plunge, the six-second Skill choice window,
 * white Skill recast, Normal-triggered black Skill recast, four particles,
 * both three-hit Burst openings and their periodic dragons, A1/A4, and
 * representable offensive C1-C6 behavior follow pinned gcsim revision
 * {@code ef41805d}. Delayed state is generation-gated and reconstructed from
 * owner-bound snapshots.</p>
 *
 * <p>Player HP, healing, defense and interruption state, movement and target
 * geometry, multi-target or random selection, hitlag extension, stamina, low Plunge,
 * exploration, Hexerei party-count amplification, continuing Burning-hit
 * observation, and C4's random C1-stack preservation fail closed. The slice
 * uses the source-backed base A1 values and explicit fixed-target reactions.</p>
 */
public final class Durin extends Character implements
        SimulatorInitializedCharacterEffect,
        SnapshotAwareCharacterEffect,
        SwitchAwareCharacter {
    private static final double FRAME = 1.0 / 60.0;
    private static final double EPSILON = 1e-9;
    private static final int[] NORMAL_DURATIONS = { 29, 30, 55, 66 };
    private static final int[][] NORMAL_HIT_FRAMES = {
        { 11 }, { 9 }, { 14, 37 }, { 38 }
    };
    private static final int[] BURST_OPENING_FRAMES = { 97, 121, 154 };
    private static final double[] NORMAL_T9 = {
        0.838696, 0.753344, 0.535762, 1.307213
    };
    private static final double[] WHITE_OPENING_T9 = {
        2.022320, 1.638800, 1.901280
    };
    private static final double[] WHITE_OPENING_C3 = {
        2.379200, 1.928000, 2.236800
    };
    private static final double[] BLACK_OPENING_T9 = {
        2.132480, 1.729920, 1.901280
    };
    private static final double[] BLACK_OPENING_C3 = {
        2.508800, 2.035200, 2.236800
    };

    /**
     * Normal-attack hitlag from gcsim config YAML pinned at
     * {@code 3647a07a7cc3004bc1e79d9bb5f7444de20dceaa}.
     */
    private static final HitlagProfile NORMAL_FIRST_HITLAG =
            new HitlagProfile(0.02, 0.01, false, false, false);
    private static final HitlagProfile NORMAL_MIDDLE_HITLAG =
            new HitlagProfile(0.03, 0.01, false, false, false);
    private static final HitlagProfile NORMAL_FINAL_HITLAG =
            new HitlagProfile(0.05, 0.01, false, false, false);

    private CombatSimulator initializedSimulator;
    private int normalAttackStep;
    private long skillGeneration;
    private boolean selectionActive;
    private double selectionUntil = Double.NEGATIVE_INFINITY;
    private double selectionAnchor = Double.NEGATIVE_INFINITY;
    private Form form = Form.WHITE;
    private double formUntil = Double.NEGATIVE_INFINITY;
    private long burstGeneration;
    private Form burstForm = Form.NONE;
    private double burstUntil = Double.NEGATIVE_INFINITY;
    private int a4Stacks;
    private int blackC1Stacks;
    private double nextParticleTime = Double.NEGATIVE_INFINITY;
    private double whiteDefReductionUntil = Double.NEGATIVE_INFINITY;
    private Map<CharacterId, Integer> whiteC1Stacks =
            new EnumMap<>(CharacterId.class);
    private Map<Element, Double> a1ShredExpirations =
            new EnumMap<>(Element.class);
    private Map<Element, Double> c2BonusExpirations =
            new EnumMap<>(Element.class);
    private List<PendingHit> pendingHits = new ArrayList<>();
    private List<PendingCommand> pendingCommands = new ArrayList<>();

    /** Constructs repository-default C6 Durin. */
    public Durin(Weapon weapon, ArtifactSet artifacts) {
        this(weapon, artifacts, TalentDataManager.getInstance(), 6);
    }

    /** Constructs Durin at an explicit constellation. */
    public Durin(
            Weapon weapon,
            ArtifactSet artifacts,
            int constellation) {
        this(weapon, artifacts, TalentDataManager.getInstance(),
                constellation);
    }

    /**
     * Constructs Durin with injectable static talent data.
     *
     * @param weapon equipped sword, or {@code null}
     * @param artifacts equipped artifact set, or {@code null}
     * @param talentData static character-data source
     * @param constellation constellation level in {@code [0, 6]}
     */
    public Durin(
            Weapon weapon,
            ArtifactSet artifacts,
            TalentDataSource talentData,
            int constellation) {
        super(talentData);
        if (constellation < 0 || constellation > 6) {
            throw new IllegalArgumentException(
                    "Durin constellation must be between 0 and 6");
        }
        name = "Durin";
        characterId = CharacterId.DURIN;
        element = Element.PYRO;
        this.weapon = weapon;
        this.artifacts = new ArtifactSet[] { artifacts };
        this.constellation = constellation;
        baseStats.set(StatType.BASE_HP,
                getTalentValue("Base HP", 12429.0));
        baseStats.set(StatType.BASE_ATK,
                getTalentValue("Base ATK", 347.0));
        baseStats.set(StatType.BASE_DEF,
                getTalentValue("Base DEF", 822.0));
        baseStats.add(StatType.CRIT_DMG,
                getTalentValue("Ascension CRIT DMG", 0.384));
        setSkillCD(getTalentValue("Skill Cooldown", 12.0));
        setBurstCD(getTalentValue("Burst Cooldown", 18.0));
    }

    /** Binds reaction, damage-consumption, and dynamic support state. */
    @Override
    public void initializeForSimulator(CombatSimulator simulator) {
        if (simulator == null) {
            throw new IllegalArgumentException("Durin simulator is required");
        }
        if (!simulator.getPartyMembers().contains(this)) {
            throw new IllegalArgumentException(
                    "Durin must belong to the simulator party");
        }
        if (initializedSimulator != null) {
            if (initializedSimulator != simulator) {
                throw new IllegalStateException(
                        "Durin cannot be reused across simulators");
            }
            return;
        }
        initializedSimulator = simulator;
        simulator.addReactionListener(this::handleReaction);
        simulator.addDamageListener((actor, action, damage, time) ->
                consumeWhiteC1(actor, action, damage, time));
        simulator.applyTeamBuffNoStack(new Buff(
                "Durin Dual-Form Support",
                BuffId.DURIN_DUAL_FORM_SUPPORT,
                Double.MAX_VALUE,
                0.0) {
            @Override
            protected void applyStats(
                    StatsContainer stats,
                    double currentTime) {
                applyDynamicSupport(stats, currentTime);
            }
        }.sourcedBy(characterId));
    }

    /** Captures form, support gates, cooldown anchors, and delayed work. */
    @Override
    public State captureCharacterState() {
        return new DurinState(
                this,
                normalAttackStep,
                skillGeneration,
                selectionActive,
                selectionUntil,
                selectionAnchor,
                form,
                formUntil,
                burstGeneration,
                burstForm,
                burstUntil,
                a4Stacks,
                blackC1Stacks,
                nextParticleTime,
                whiteDefReductionUntil,
                whiteC1Stacks,
                a1ShredExpirations,
                c2BonusExpirations,
                pendingHits,
                pendingCommands);
    }

    /** Accepts state captured by this exact Durin instance only. */
    @Override
    public boolean acceptsCharacterState(State state) {
        return state instanceof DurinState
                && ((DurinState) state).owner == this;
    }

    /** Restores all local state and reconstructs unresolved work once. */
    @Override
    public void restoreCharacterState(
            State state,
            CombatSimulator simulator) {
        if (!acceptsCharacterState(state)) {
            throw new IllegalArgumentException("Unexpected Durin state");
        }
        initializeForSimulator(simulator);
        DurinState restored = (DurinState) state;
        normalAttackStep = restored.normalAttackStep;
        skillGeneration = restored.skillGeneration;
        selectionActive = restored.selectionActive;
        selectionUntil = restored.selectionUntil;
        selectionAnchor = restored.selectionAnchor;
        form = restored.form;
        formUntil = restored.formUntil;
        burstGeneration = restored.burstGeneration;
        burstForm = restored.burstForm;
        burstUntil = restored.burstUntil;
        a4Stacks = restored.a4Stacks;
        blackC1Stacks = restored.blackC1Stacks;
        nextParticleTime = restored.nextParticleTime;
        whiteDefReductionUntil = restored.whiteDefReductionUntil;
        whiteC1Stacks = new EnumMap<>(restored.whiteC1Stacks);
        a1ShredExpirations = new EnumMap<>(restored.a1ShredExpirations);
        c2BonusExpirations = new EnumMap<>(restored.c2BonusExpirations);
        pendingHits = copyHits(restored.pendingHits);
        pendingCommands = copyCommands(restored.pendingCommands);
        double currentTime = simulator.getCurrentTime();
        refreshTemporalState(currentTime);
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

    /** Returns Durin's source-backed 70-Energy Burst cost. */
    @Override
    public double getEnergyCost() {
        return getTalentValue("Energy Cost", 70.0);
    }

    /** Applies black-form A1's amplifying-reaction bonus while active. */
    @Override
    public void applyPassive(StatsContainer stats) {
        double currentTime = currentTime();
        if (burstForm == Form.BLACK
                && currentTime + EPSILON < burstUntil) {
            double bonus = getTalentValue(
                    "A1 Amplifying Bonus", 0.40);
            stats.add(StatType.VAPORIZE_DMG_BONUS, bonus);
            stats.add(StatType.MELT_DMG_BONUS, bonus);
        }
    }

    /** Resets only the sword Normal string on switch-out. */
    @Override
    public void onSwitchOut(CombatSimulator simulator) {
        normalAttackStep = 0;
    }

    /** Resets only the sword Normal string on switch-in. */
    @Override
    public void onSwitchIn(CombatSimulator simulator) {
        normalAttackStep = 0;
    }

    /** Returns the selected thirty-second Skill form. */
    public String getSelectedForm() {
        refreshFromInitializedSimulator();
        return isFormActive(currentTime()) ? form.name() : Form.NONE.name();
    }

    /** Returns whether the Skill choice window remains open. */
    public boolean isSkillSelectionActive() {
        refreshFromInitializedSimulator();
        return selectionActive;
    }

    /** Returns the currently active Burst form. */
    public String getBurstForm() {
        refreshFromInitializedSimulator();
        return isBurstActive(currentTime())
                ? burstForm.name() : Form.NONE.name();
    }

    /** Returns remaining A4-enhanced periodic hits. */
    public int getA4Stacks() {
        refreshFromInitializedSimulator();
        return a4Stacks;
    }

    /** Returns remaining local black-form C1 stacks. */
    public int getBlackC1Stacks() {
        return blackC1Stacks;
    }

    /** Returns unresolved Durin-owned delayed hits. */
    public int getPendingHitCount() {
        return pendingHits.size();
    }

    /** Reports excluded HP, healing, and defensive state. */
    public boolean isPlayerHpHealingDefenseRepresented() {
        return false;
    }

    /** Reports excluded movement and target geometry. */
    public boolean isMovementGeometryRepresented() {
        return false;
    }

    /** Reports excluded multi-target and random selection. */
    public boolean isMultiTargetRandomSelectionRepresented() {
        return false;
    }

    /** Reports excluded complete hitlag coverage and stamina. */
    public boolean isHitlagStaminaRepresented() {
        return false;
    }

    /** Reports excluded low Plunge and exploration behavior. */
    public boolean isLowPlungeExplorationRepresented() {
        return false;
    }

    /** Reports excluded Hexerei and continuing Burning team plumbing. */
    public boolean isUnsupportedTeamStateRepresented() {
        return false;
    }

    /** Dispatches Durin's bounded typed action set. */
    @Override
    public void onAction(
            CharacterActionRequest request,
            CombatSimulator simulator) {
        if (request == null) {
            throw new IllegalArgumentException("Durin action is required");
        }
        initializeForSimulator(simulator);
        refreshTemporalState(simulator.getCurrentTime());
        if (request.getKey() == CharacterActionKey.SKILL
                && request.getSkillMode() != SkillActionMode.PRESS) {
            throw new IllegalArgumentException(
                    "Durin supports Press Skill only");
        }
        if (request.getKey() != CharacterActionKey.NORMAL) {
            normalAttackStep = 0;
        }
        switch (request.getKey()) {
            case NORMAL:
                if (selectionActive) {
                    blackSkillRecast(simulator);
                } else {
                    normalAttack(simulator);
                }
                break;
            case CHARGE:
                chargedAttack(simulator);
                normalAttackStep = 0;
                break;
            case PLUNGE:
                highPlunge(simulator);
                normalAttackStep = 0;
                break;
            case SKILL:
                if (selectionActive) {
                    whiteSkillRecast(simulator);
                } else {
                    openSkillSelection(simulator);
                }
                break;
            case BURST:
                burst(simulator);
                normalAttackStep = 0;
                break;
            default:
                throw new IllegalArgumentException(
                        "Unsupported action for Durin: "
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
                    Form.NONE,
                    0L));
        }
        normalAttackStep = (normalAttackStep + 1)
                % NORMAL_HIT_FRAMES.length;
        simulator.advanceTime(NORMAL_DURATIONS[step] * FRAME);
    }

    private void chargedAttack(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        queueHit(simulator, new PendingHit(
                castTime + 17.0 * FRAME,
                HitKind.CHARGED,
                0,
                0,
                Form.NONE,
                0L));
        simulator.advanceTime(58.0 * FRAME);
    }

    private void highPlunge(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        queueHit(simulator, new PendingHit(
                castTime + 48.0 * FRAME,
                HitKind.HIGH_PLUNGE,
                0,
                0,
                Form.NONE,
                0L));
        simulator.advanceTime(74.0 * FRAME);
    }

    private void openSkillSelection(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        long generation = ++skillGeneration;
        selectionActive = true;
        selectionAnchor = castTime;
        selectionUntil = castTime + 6.0;
        queueCommand(simulator, new PendingCommand(
                selectionUntil,
                CommandKind.FINALIZE_SKILL_COOLDOWN,
                generation,
                0.0,
                Form.NONE));
        simulator.advanceTime(49.0 * FRAME);
    }

    private void whiteSkillRecast(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        finalizeSkillChoice(Form.WHITE, castTime);
        queueHit(simulator, new PendingHit(
                castTime + 35.0 * FRAME,
                HitKind.WHITE_SKILL,
                0,
                0,
                Form.WHITE,
                skillGeneration));
        restoreSkillEnergy();
        simulator.advanceTime(83.0 * FRAME);
    }

    private void blackSkillRecast(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        finalizeSkillChoice(Form.BLACK, castTime);
        int[] hitFrames = { 32, 37, 42 };
        for (int index = 0; index < hitFrames.length; index++) {
            queueHit(simulator, new PendingHit(
                    castTime + hitFrames[index] * FRAME,
                    HitKind.BLACK_SKILL,
                    index,
                    0,
                    Form.BLACK,
                    skillGeneration));
        }
        restoreSkillEnergy();
        simulator.advanceTime(67.0 * FRAME);
    }

    private void finalizeSkillChoice(Form selected, double currentTime) {
        markSkillUsed(selectionAnchor);
        selectionActive = false;
        selectionUntil = Double.NEGATIVE_INFINITY;
        form = selected;
        formUntil = currentTime + 30.0;
    }

    private void restoreSkillEnergy() {
        String key = constellation >= 5
                ? "Energy Regeneration C5" : "Energy Regeneration";
        receiveFlatEnergy(getTalentValue(
                key, constellation >= 5 ? 39.0 : 30.0));
    }

    private void burst(CombatSimulator simulator) {
        double castTime = simulator.getCurrentTime();
        Form selected = isFormActive(castTime) && form == Form.BLACK
                ? Form.BLACK : Form.WHITE;
        long generation = ++burstGeneration;
        markBurstCooldownUsed(
                castTime, simulator.getApplicableBuffs(this));
        queueCommand(simulator, new PendingCommand(
                castTime + 10.0 * FRAME,
                CommandKind.SPEND_BURST_ENERGY,
                generation,
                getEnergyCost(),
                selected));
        queueCommand(simulator, new PendingCommand(
                castTime + BURST_OPENING_FRAMES[0] * FRAME,
                CommandKind.ACTIVATE_BURST,
                generation,
                0.0,
                selected));
        for (int index = 0; index < BURST_OPENING_FRAMES.length; index++) {
            queueHit(simulator, new PendingHit(
                    castTime + BURST_OPENING_FRAMES[index] * FRAME,
                    HitKind.BURST_OPENING,
                    index,
                    0,
                    selected,
                    generation));
        }
        int ticks = selected == Form.WHITE ? 20 : 16;
        double interval = selected == Form.WHITE ? 58.8 : 73.6;
        double first = selected == Form.WHITE
                ? 154.0 + 60.0 - interval
                : 154.0 + 95.0 - interval;
        for (int index = 0; index < ticks; index++) {
            queueHit(simulator, new PendingHit(
                    castTime + Math.ceil(first + interval * index) * FRAME,
                    HitKind.BURST_PERIODIC,
                    index,
                    0,
                    selected,
                    generation));
        }
        simulator.advanceTime(104.0 * FRAME);
    }

    private void activateBurst(
            CombatSimulator simulator,
            Form selected,
            long generation) {
        if (generation != burstGeneration) {
            return;
        }
        double currentTime = simulator.getCurrentTime();
        burstForm = selected;
        burstUntil = currentTime + 20.5;
        a4Stacks = (int) getTalentValue("A4 Stack Count", 10.0);
        if (constellation >= 1) {
            int stacks = (int) getTalentValue("C1 Stack Count", 20.0);
            if (selected == Form.WHITE) {
                blackC1Stacks = 0;
                grantWhiteC1(simulator, stacks, currentTime);
            } else {
                whiteC1Stacks.clear();
                blackC1Stacks = stacks;
            }
        }
    }

    private void grantWhiteC1(
            CombatSimulator simulator,
            int stacks,
            double currentTime) {
        whiteC1Stacks.clear();
        for (Character member : simulator.getPartyMembers()) {
            if (member == this) {
                continue;
            }
            whiteC1Stacks.put(member.getCharacterId(), stacks);
            member.removeBuff(BuffId.DURIN_C1_WHITE_FLAT_DMG);
            member.addBuff(new ActiveCharacterBuff(
                    "Durin C1 White Flame",
                    BuffId.DURIN_C1_WHITE_FLAT_DMG,
                    20.5,
                    currentTime,
                    simulator,
                    member,
                    stats -> {
                        Integer remaining = whiteC1Stacks.get(
                                member.getCharacterId());
                        if (remaining != null && remaining > 0
                                && isBurstActive(simulator.getCurrentTime())
                                && burstForm == Form.WHITE) {
                            stats.add(
                                    StatType.FLAT_DMG_BONUS,
                                    liveAttack(simulator.getCurrentTime())
                                            * getTalentValue(
                                                    "C1 White Flat ATK Ratio",
                                                    0.60));
                        }
                    }).sourcedBy(characterId));
        }
    }

    private void handleReaction(
            ReactionResult result,
            Character trigger,
            double time,
            CombatSimulator simulator) {
        if (result == null || !isBurstActive(time)) {
            return;
        }
        Element[] elements = reactionElements(result);
        if (elements.length == 0) {
            return;
        }
        if (burstForm == Form.WHITE && isWhiteA1Reaction(result)) {
            double expiry = time + getTalentValue("A1 Duration", 6.0);
            for (Element element : elements) {
                a1ShredExpirations.put(element, expiry);
            }
        }
        if (constellation >= 2) {
            double expiry = time + getTalentValue("C2 Duration", 6.0);
            for (Element element : elements) {
                c2BonusExpirations.put(element, expiry);
            }
        }
    }

    private void consumeWhiteC1(
            Character actor,
            AttackAction action,
            double damage,
            double time) {
        if (constellation < 1 || actor == null || action == null
                || damage <= 0.0 || actor == this
                || initializedSimulator.getActiveCharacter() != actor
                || !isBurstActive(time) || burstForm != Form.WHITE) {
            return;
        }
        Integer remaining = whiteC1Stacks.get(actor.getCharacterId());
        if (remaining == null || remaining <= 0) {
            return;
        }
        int next = remaining - 1;
        whiteC1Stacks.put(actor.getCharacterId(), next);
        if (next <= 0) {
            actor.removeBuff(BuffId.DURIN_C1_WHITE_FLAT_DMG);
        }
    }

    private void resolveHit(
            CombatSimulator simulator,
            PendingHit hit) {
        refreshTemporalState(simulator.getCurrentTime());
        if ((hit.kind == HitKind.BURST_OPENING
                || hit.kind == HitKind.BURST_PERIODIC)
                && hit.generation != burstGeneration) {
            return;
        }
        double multiplier;
        String displayName;
        Element hitElement;
        StatType bonusStat;
        ActionType actionType;
        ICDType icdType;
        ICDTag icdTag;
        switch (hit.kind) {
            case NORMAL:
                multiplier = normalValue(hit.index);
                displayName = "Radiant Wingslash N" + (hit.index + 1)
                        + (NORMAL_HIT_FRAMES[hit.index].length > 1
                                ? " Hit " + (hit.variant + 1) : "");
                hitElement = Element.PHYSICAL;
                bonusStat = StatType.NORMAL_ATTACK_DMG_BONUS;
                actionType = ActionType.NORMAL;
                icdType = ICDType.Standard;
                icdTag = ICDTag.NormalAttack;
                break;
            case CHARGED:
                multiplier = getTalentValue("Charged Attack", 2.084020);
                displayName = "Radiant Wingslash Charged Attack";
                hitElement = Element.PHYSICAL;
                bonusStat = StatType.CHARGED_ATTACK_DMG_BONUS;
                actionType = ActionType.CHARGE;
                icdType = ICDType.Standard;
                icdTag = ICDTag.ChargedAttack;
                break;
            case HIGH_PLUNGE:
                multiplier = getTalentValue("High Plunge", 2.933586);
                displayName = "Radiant Wingslash High Plunge";
                hitElement = Element.PHYSICAL;
                bonusStat = StatType.PLUNGING_ATTACK_DMG_BONUS;
                actionType = ActionType.PLUNGE;
                icdType = ICDType.None;
                icdTag = ICDTag.None;
                break;
            case WHITE_SKILL:
                multiplier = skillValue("Confirmation of Purity", 1.7952,
                        2.1120);
                displayName = "Transmutation: Confirmation of Purity";
                hitElement = Element.PYRO;
                bonusStat = StatType.SKILL_DMG_BONUS;
                actionType = ActionType.SKILL;
                icdType = ICDType.Standard;
                icdTag = ICDTag.ElementalSkill;
                break;
            case BLACK_SKILL:
                multiplier = blackSkillValue(hit.index);
                displayName = "Transmutation: Denial of Darkness Hit "
                        + (hit.index + 1);
                hitElement = Element.PYRO;
                bonusStat = StatType.SKILL_DMG_BONUS;
                actionType = ActionType.SKILL;
                icdType = ICDType.DurinBlackSkill;
                icdTag = ICDTag.Durin_BlackSkill;
                break;
            case BURST_OPENING:
                multiplier = openingValue(hit.form, hit.index);
                displayName = hit.form == Form.WHITE
                        ? "Principle of Purity: As the Light Shifts Hit "
                                + (hit.index + 1)
                        : "Principle of Darkness: As the Stars Smolder Hit "
                                + (hit.index + 1);
                hitElement = Element.PYRO;
                bonusStat = StatType.BURST_DMG_BONUS;
                actionType = ActionType.BURST;
                icdType = ICDType.Standard;
                icdTag = ICDTag.Durin_BurstOpening;
                break;
            case BURST_PERIODIC:
                multiplier = periodicValue(hit.form);
                if (a4Stacks > 0 && isBurstActive(simulator.getCurrentTime())) {
                    double bonus = Math.min(
                            liveAttack(simulator.getCurrentTime())
                                    * getTalentValue("A4 ATK Ratio", 0.0003),
                            getTalentValue("A4 Maximum Bonus", 0.75));
                    multiplier *= 1.0 + bonus;
                    a4Stacks--;
                }
                displayName = hit.form == Form.WHITE
                        ? "Dragon of White Flame " + (hit.index + 1)
                        : "Dragon of Dark Decay " + (hit.index + 1);
                hitElement = Element.PYRO;
                bonusStat = StatType.BURST_DMG_BONUS;
                actionType = ActionType.BURST;
                icdType = hit.form == Form.WHITE
                        ? ICDType.DurinWhiteBurst
                        : ICDType.DurinBlackBurst;
                icdTag = hit.form == Form.WHITE
                        ? ICDTag.Durin_WhiteBurst
                        : ICDTag.Durin_BlackBurst;
                break;
            default:
                throw new IllegalStateException(
                        "Unknown Durin hit kind " + hit.kind);
        }
        AttackAction action = new AttackAction(
                displayName,
                multiplier,
                hitElement,
                StatType.BASE_ATK,
                bonusStat,
                0.0,
                actionType);
        action.setICD(icdType, icdTag, 1.0);
        action.setCountsAsSkillDmg(actionType == ActionType.SKILL);
        action.setCountsAsBurstDmg(actionType == ActionType.BURST);
        action.setHitEffectTrigger(true);
        if (hit.kind == HitKind.NORMAL) {
            if (hit.index == 0) {
                action.setHitlagProfile(NORMAL_FIRST_HITLAG);
            } else if (hit.index == 1
                    || (hit.index == 2 && hit.variant == 1)) {
                action.setHitlagProfile(NORMAL_MIDDLE_HITLAG);
            } else if (hit.index == 3) {
                action.setHitlagProfile(NORMAL_FINAL_HITLAG);
            }
        }
        if (actionType == ActionType.CHARGE
                || actionType == ActionType.PLUNGE) {
            action.setShatterTrigger(true);
        }
        if (actionType == ActionType.BURST && constellation >= 4) {
            action.addBonusStat(
                    StatType.DMG_BONUS_ALL,
                    getTalentValue("C4 Burst DMG Bonus", 0.40));
        }
        if (actionType == ActionType.BURST
                && hit.form == Form.BLACK
                && constellation >= 1
                && blackC1Stacks >= 2) {
            action.addBonusStat(
                    StatType.FLAT_DMG_BONUS,
                    liveAttack(simulator.getCurrentTime())
                            * getTalentValue(
                                    "C1 Black Flat ATK Ratio", 1.50));
            blackC1Stacks -= 2;
        }
        if (actionType == ActionType.BURST && constellation >= 6) {
            action.setDefenseIgnore(hit.form == Form.WHITE
                    ? getTalentValue("C6 White DEF Ignore", 0.30)
                    : getTalentValue("C6 Black DEF Ignore", 0.70));
        }
        simulator.performActionWithoutTimeAdvance(characterId, action);
        if ((hit.kind == HitKind.WHITE_SKILL
                || hit.kind == HitKind.BLACK_SKILL)
                && simulator.getCurrentTime() + EPSILON
                        >= nextParticleTime) {
            nextParticleTime = simulator.getCurrentTime() + 0.3;
            queueCommand(simulator, new PendingCommand(
                    simulator.getCurrentTime() + 100.0 * FRAME,
                    CommandKind.PARTICLE,
                    hit.generation,
                    4.0,
                    hit.form));
        }
        if (actionType == ActionType.BURST
                && hit.form == Form.WHITE
                && constellation >= 6) {
            whiteDefReductionUntil = simulator.getCurrentTime()
                    + 6.0;
        }
    }

    private void applyDynamicSupport(
            StatsContainer stats,
            double currentTime) {
        double shred = getTalentValue("A1 RES Shred", 0.20);
        for (Map.Entry<Element, Double> entry
                : a1ShredExpirations.entrySet()) {
            if (currentTime + EPSILON < entry.getValue()) {
                stats.add(resShredStat(entry.getKey()), shred);
            }
        }
        if (constellation >= 2) {
            double bonus = getTalentValue(
                    "C2 Elemental DMG Bonus", 0.50);
            for (Map.Entry<Element, Double> entry
                    : c2BonusExpirations.entrySet()) {
                if (currentTime + EPSILON < entry.getValue()) {
                    stats.add(entry.getKey().getBonusStatType(), bonus);
                }
            }
        }
        if (constellation >= 6
                && currentTime + EPSILON < whiteDefReductionUntil) {
            stats.add(
                    StatType.ENEMY_DEF_REDUCTION,
                    getTalentValue("C6 White DEF Reduction", 0.30));
        }
    }

    private double liveAttack(double currentTime) {
        StatsContainer stats = getEffectiveStats(currentTime);
        if (initializedSimulator != null) {
            for (Buff buff : initializedSimulator.getApplicableBuffs(this)) {
                if (!buff.isExpired(currentTime)) {
                    buff.apply(stats, currentTime);
                }
            }
        }
        return stats.getTotalAtk();
    }

    private double normalValue(int step) {
        String key = step == 2 ? "N3 Hit" : "N" + (step + 1);
        return getTalentValue(key, NORMAL_T9[step]);
    }

    private double skillValue(
            String key,
            double talentNine,
            double c5) {
        return constellation >= 5
                ? getTalentValue(key + " C5", c5)
                : getTalentValue(key, talentNine);
    }

    private double blackSkillValue(int index) {
        double[] talentNine = { 1.228080, 0.904400, 1.098880 };
        double[] c5 = { 1.444800, 1.064000, 1.292800 };
        return skillValue(
                "Denial of Darkness Hit " + (index + 1),
                talentNine[index],
                c5[index]);
    }

    private double openingValue(Form selected, int index) {
        double[] values;
        if (selected == Form.WHITE) {
            values = constellation >= 3
                    ? WHITE_OPENING_C3 : WHITE_OPENING_T9;
        } else {
            values = constellation >= 3
                    ? BLACK_OPENING_C3 : BLACK_OPENING_T9;
        }
        String key = (selected == Form.WHITE
                ? "White Opening Hit " : "Black Opening Hit ")
                + (index + 1) + (constellation >= 3 ? " C3" : "");
        return getTalentValue(key, values[index]);
    }

    private double periodicValue(Form selected) {
        String key = selected == Form.WHITE
                ? "Dragon of White Flame" : "Dragon of Dark Decay";
        double value;
        if (selected == Form.WHITE) {
            value = constellation >= 3 ? 1.8928 : 1.60888;
        } else {
            value = constellation >= 3 ? 2.5968 : 2.20728;
        }
        if (constellation >= 3) {
            key += " C3";
        }
        return getTalentValue(key, value);
    }

    private static Element[] reactionElements(ReactionResult result) {
        switch (result.getKind()) {
            case OVERLOAD:
            case OVERLOADED:
                return new Element[] { Element.PYRO, Element.ELECTRO };
            case SWIRL:
                return result.getRelatedElement() == Element.PYRO
                        ? new Element[] { Element.PYRO, Element.ANEMO }
                        : new Element[0];
            case CRYSTALLIZE:
                return result.getRelatedElement() == Element.PYRO
                        ? new Element[] { Element.PYRO, Element.GEO }
                        : new Element[0];
            case BURNING:
                return new Element[] { Element.PYRO, Element.DENDRO };
            case VAPORIZE:
                return new Element[] { Element.PYRO, Element.HYDRO };
            case MELT:
                return new Element[] { Element.PYRO, Element.CRYO };
            default:
                return new Element[0];
        }
    }

    private static boolean isWhiteA1Reaction(ReactionResult result) {
        switch (result.getKind()) {
            case OVERLOAD:
            case OVERLOADED:
            case SWIRL:
            case CRYSTALLIZE:
            case BURNING:
                return true;
            default:
                return false;
        }
    }

    private static StatType resShredStat(Element element) {
        switch (element) {
            case PYRO:
                return StatType.PYRO_RES_SHRED;
            case HYDRO:
                return StatType.HYDRO_RES_SHRED;
            case ANEMO:
                return StatType.ANEMO_RES_SHRED;
            case ELECTRO:
                return StatType.ELECTRO_RES_SHRED;
            case DENDRO:
                return StatType.DENDRO_RES_SHRED;
            case CRYO:
                return StatType.CRYO_RES_SHRED;
            case GEO:
                return StatType.GEO_RES_SHRED;
            default:
                throw new IllegalArgumentException(
                        "Durin does not shred " + element);
        }
    }

    private boolean isFormActive(double currentTime) {
        return form != Form.NONE && currentTime + EPSILON < formUntil;
    }

    private boolean isBurstActive(double currentTime) {
        return burstForm != Form.NONE
                && currentTime + EPSILON < burstUntil;
    }

    private double currentTime() {
        return initializedSimulator == null
                ? Double.NEGATIVE_INFINITY
                : initializedSimulator.getCurrentTime();
    }

    private void refreshFromInitializedSimulator() {
        if (initializedSimulator != null) {
            refreshTemporalState(initializedSimulator.getCurrentTime());
        }
    }

    private void refreshTemporalState(double currentTime) {
        if (selectionActive
                && currentTime + EPSILON >= selectionUntil) {
            selectionActive = false;
            selectionUntil = Double.NEGATIVE_INFINITY;
        }
        if (!isFormActive(currentTime)) {
            form = Form.NONE;
            formUntil = Double.NEGATIVE_INFINITY;
        }
        if (!isBurstActive(currentTime)) {
            burstForm = Form.NONE;
            burstUntil = Double.NEGATIVE_INFINITY;
            a4Stacks = 0;
            blackC1Stacks = 0;
            whiteC1Stacks.clear();
        }
        a1ShredExpirations.entrySet().removeIf(entry ->
                currentTime + EPSILON >= entry.getValue());
        c2BonusExpirations.entrySet().removeIf(entry ->
                currentTime + EPSILON >= entry.getValue());
        if (currentTime + EPSILON >= whiteDefReductionUntil) {
            whiteDefReductionUntil = Double.NEGATIVE_INFINITY;
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
                case FINALIZE_SKILL_COOLDOWN:
                    if (selectionActive
                            && command.generation == skillGeneration) {
                        markSkillUsed(selectionAnchor);
                        selectionActive = false;
                        selectionUntil = Double.NEGATIVE_INFINITY;
                    }
                    break;
                case SPEND_BURST_ENERGY:
                    if (command.generation == burstGeneration) {
                        spendBurstEnergy(activeSimulator.getCurrentTime());
                    }
                    break;
                case ACTIVATE_BURST:
                    activateBurst(
                            activeSimulator,
                            command.form,
                            command.generation);
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
                            "Unknown Durin command " + command.kind);
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

    private enum Form {
        NONE,
        WHITE,
        BLACK
    }

    private enum HitKind {
        NORMAL,
        CHARGED,
        HIGH_PLUNGE,
        WHITE_SKILL,
        BLACK_SKILL,
        BURST_OPENING,
        BURST_PERIODIC
    }

    private enum CommandKind {
        FINALIZE_SKILL_COOLDOWN,
        SPEND_BURST_ENERGY,
        ACTIVATE_BURST,
        PARTICLE
    }

    /** Immutable delayed hit payload. */
    private static final class PendingHit {
        private final double time;
        private final HitKind kind;
        private final int index;
        private final int variant;
        private final Form form;
        private final long generation;

        private PendingHit(
                double time,
                HitKind kind,
                int index,
                int variant,
                Form form,
                long generation) {
            this.time = time;
            this.kind = kind;
            this.index = index;
            this.variant = variant;
            this.form = form;
            this.generation = generation;
        }

        private PendingHit copy() {
            return new PendingHit(
                    time, kind, index, variant, form, generation);
        }
    }

    /** Immutable delayed state-command payload. */
    private static final class PendingCommand {
        private final double time;
        private final CommandKind kind;
        private final long generation;
        private final double value;
        private final Form form;

        private PendingCommand(
                double time,
                CommandKind kind,
                long generation,
                double value,
                Form form) {
            this.time = time;
            this.kind = kind;
            this.generation = generation;
            this.value = value;
            this.form = form;
        }

        private PendingCommand copy() {
            return new PendingCommand(
                    time, kind, generation, value, form);
        }
    }

    /** Immutable owner-bound snapshot of all Durin-specific state. */
    private static final class DurinState implements State {
        private final Durin owner;
        private final int normalAttackStep;
        private final long skillGeneration;
        private final boolean selectionActive;
        private final double selectionUntil;
        private final double selectionAnchor;
        private final Form form;
        private final double formUntil;
        private final long burstGeneration;
        private final Form burstForm;
        private final double burstUntil;
        private final int a4Stacks;
        private final int blackC1Stacks;
        private final double nextParticleTime;
        private final double whiteDefReductionUntil;
        private final Map<CharacterId, Integer> whiteC1Stacks;
        private final Map<Element, Double> a1ShredExpirations;
        private final Map<Element, Double> c2BonusExpirations;
        private final List<PendingHit> pendingHits;
        private final List<PendingCommand> pendingCommands;

        private DurinState(
                Durin owner,
                int normalAttackStep,
                long skillGeneration,
                boolean selectionActive,
                double selectionUntil,
                double selectionAnchor,
                Form form,
                double formUntil,
                long burstGeneration,
                Form burstForm,
                double burstUntil,
                int a4Stacks,
                int blackC1Stacks,
                double nextParticleTime,
                double whiteDefReductionUntil,
                Map<CharacterId, Integer> whiteC1Stacks,
                Map<Element, Double> a1ShredExpirations,
                Map<Element, Double> c2BonusExpirations,
                List<PendingHit> pendingHits,
                List<PendingCommand> pendingCommands) {
            this.owner = owner;
            this.normalAttackStep = normalAttackStep;
            this.skillGeneration = skillGeneration;
            this.selectionActive = selectionActive;
            this.selectionUntil = selectionUntil;
            this.selectionAnchor = selectionAnchor;
            this.form = form;
            this.formUntil = formUntil;
            this.burstGeneration = burstGeneration;
            this.burstForm = burstForm;
            this.burstUntil = burstUntil;
            this.a4Stacks = a4Stacks;
            this.blackC1Stacks = blackC1Stacks;
            this.nextParticleTime = nextParticleTime;
            this.whiteDefReductionUntil = whiteDefReductionUntil;
            this.whiteC1Stacks = new EnumMap<>(whiteC1Stacks);
            this.a1ShredExpirations =
                    new EnumMap<>(a1ShredExpirations);
            this.c2BonusExpirations =
                    new EnumMap<>(c2BonusExpirations);
            this.pendingHits = copyHits(pendingHits);
            this.pendingCommands = copyCommands(pendingCommands);
        }
    }
}

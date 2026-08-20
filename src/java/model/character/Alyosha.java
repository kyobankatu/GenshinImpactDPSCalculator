package model.character;

import java.util.ArrayList;
import java.util.List;

import mechanics.buff.Buff;
import mechanics.buff.SimpleBuff;
import mechanics.data.TalentDataManager;
import mechanics.data.TalentDataSource;
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
import simulation.action.SkillActionMode;

/**
 * Alyosha's source-backed fixed-target offensive slice for Version 7.0.
 *
 * <p>Lv. 90 stats, base Lv. 9 multipliers, C3/C5 Lv. 12 multipliers,
 * durations, cooldowns, A4, and represented constellation values follow
 * Genshin Optimizer commit {@code d791814a}. Hunter's Mark is represented on
 * one fixed target: applying it to an already marked target consumes the mark
 * and grants Hunter's Precision. Burst cadence is manual because authoritative
 * hitmarks are unavailable.</p>
 *
 * <p>Animation frames, elemental gauge, ICD groups, particles, healing,
 * taunting, movement, targeting, and multi-target behavior deliberately fail
 * closed. Every represented hit therefore has zero animation time and zero
 * gauge application.</p>
 */
public final class Alyosha extends Character implements
        ReactionAwareCharacter,
        SimulatorInitializedCharacterEffect,
        SnapshotAwareCharacterEffect {
    private static final int NORMAL_ATTACK_COUNT = 4;
    private static final int MAX_C6_PRECISION_STACKS = 2;
    private static final double EPSILON = 1e-9;

    private CombatSimulator initializedSimulator;
    private int normalAttackStep;
    private double hunterMarkUntil = Double.NEGATIVE_INFINITY;
    private final List<Double> precisionExpirations = new ArrayList<>();
    private double burstUntil = Double.NEGATIVE_INFINITY;
    private double nextC1EnergyAt = Double.NEGATIVE_INFINITY;

    /** Constructs repository-default C0 Alyosha. */
    public Alyosha(Weapon weapon, ArtifactSet artifacts) {
        this(weapon, artifacts, TalentDataManager.getInstance(), 0);
    }

    /**
     * Constructs Alyosha at an explicit constellation.
     *
     * @param weapon equipped polearm, or {@code null}
     * @param artifacts equipped artifact set, or {@code null}
     * @param constellation constellation in the inclusive range 0-6
     */
    public Alyosha(
            Weapon weapon,
            ArtifactSet artifacts,
            int constellation) {
        this(
                weapon,
                artifacts,
                TalentDataManager.getInstance(),
                constellation);
    }

    /**
     * Constructs Alyosha with injectable sourced talent data.
     *
     * @param weapon equipped polearm, or {@code null}
     * @param artifacts equipped artifact set, or {@code null}
     * @param talentData character-data source
     * @param constellation constellation in the inclusive range 0-6
     */
    public Alyosha(
            Weapon weapon,
            ArtifactSet artifacts,
            TalentDataSource talentData,
            int constellation) {
        super(talentData);
        if (constellation < 0 || constellation > 6) {
            throw new IllegalArgumentException(
                    "Alyosha constellation must be between 0 and 6");
        }
        name = "Alyosha";
        characterId = CharacterId.ALYOSHA;
        element = Element.ELECTRO;
        this.weapon = weapon;
        this.artifacts = new ArtifactSet[] { artifacts };
        this.constellation = constellation;

        baseStats.set(StatType.BASE_HP,
                getTalentValue("Base HP", 11962.4065));
        baseStats.set(StatType.BASE_ATK,
                getTalentValue("Base ATK", 265.4965));
        baseStats.set(StatType.BASE_DEF,
                getTalentValue("Base DEF", 702.9972));
        baseStats.add(StatType.ENERGY_RECHARGE,
                getTalentValue("Ascension Energy Recharge", 0.2667));
        setSkillCD(getTalentValue("Skill Cooldown", 15.0));
        setBurstCD(getTalentValue("Burst Cooldown", 18.0));
    }

    /** Binds C1 reaction notifications to one simulator instance. */
    @Override
    public void initializeForSimulator(CombatSimulator simulator) {
        if (simulator == null) {
            throw new IllegalArgumentException("Alyosha simulator is required");
        }
        if (initializedSimulator != null
                && initializedSimulator != simulator) {
            throw new IllegalStateException(
                    "Alyosha cannot be reused across simulators");
        }
        if (initializedSimulator == simulator) {
            return;
        }
        if (!simulator.getPartyMembers().contains(this)) {
            throw new IllegalArgumentException(
                    "Alyosha must belong to the simulator party");
        }
        initializedSimulator = simulator;
        if (constellation >= 1) {
            simulator.addReactionListener(this);
        }
    }

    /** Captures every Alyosha-owned fixed-target state value. */
    @Override
    public State captureCharacterState() {
        return new AlyoshaState(
                this,
                normalAttackStep,
                hunterMarkUntil,
                precisionExpirations,
                burstUntil,
                nextC1EnergyAt);
    }

    /** Accepts only state captured from this exact Alyosha instance. */
    @Override
    public boolean acceptsCharacterState(State state) {
        return state instanceof AlyoshaState
                && ((AlyoshaState) state).owner == this;
    }

    /** Restores all character-owned state without creating inferred events. */
    @Override
    public void restoreCharacterState(
            State state,
            CombatSimulator simulator) {
        if (!acceptsCharacterState(state)) {
            throw new IllegalArgumentException("Unexpected Alyosha state");
        }
        initializeForSimulator(simulator);
        AlyoshaState restored = (AlyoshaState) state;
        normalAttackStep = restored.normalAttackStep;
        hunterMarkUntil = restored.hunterMarkUntil;
        precisionExpirations.clear();
        precisionExpirations.addAll(restored.precisionExpirations);
        burstUntil = restored.burstUntil;
        nextC1EnergyAt = restored.nextC1EnergyAt;
    }

    /** Returns Alyosha's sourced 70-Energy Burst cost. */
    @Override
    public double getEnergyCost() {
        return getTalentValue("Energy Cost", 70.0);
    }

    /** Applies A4's live ER-scaled Skill and Burst damage bonus. */
    @Override
    public void applyPassive(StatsContainer stats) {
        double bonus = Math.min(
                getTalentValue("A4 Maximum DMG Bonus", 0.70),
                stats.getTotalEnergyRecharge()
                        * getTalentValue(
                                "A4 Skill Burst Bonus Per ER", 0.35));
        stats.add(StatType.SKILL_DMG_BONUS, bonus);
        stats.add(StatType.BURST_DMG_BONUS, bonus);
    }

    /** Alyosha supports both sourced Press and Hold Skill modes. */
    @Override
    protected boolean supportsSkillActionMode(SkillActionMode mode) {
        return mode == SkillActionMode.PRESS
                || mode == SkillActionMode.HOLD;
    }

    /** Dispatches the represented zero-frame fixed-target action set. */
    @Override
    public void onAction(
            CharacterActionRequest request,
            CombatSimulator simulator) {
        if (request == null) {
            throw new IllegalArgumentException("Alyosha action is required");
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
            case SKILL:
                elementalSkill(request.getSkillMode(), simulator);
                break;
            case BURST:
                elementalBurst(simulator);
                break;
            default:
                throw new IllegalArgumentException(
                        "Unsupported action for Alyosha: " + request.getKey());
        }
    }

    /** Handles C1 for typed Electro-related reactions from nearby members. */
    @Override
    public void onReaction(
            ReactionResult result,
            Character source,
            double time,
            CombatSimulator simulator) {
        if (constellation < 1
                || simulator != initializedSimulator
                || result == null
                || source == null
                || !simulator.getPartyMembers().contains(source)
                || !isElectroRelatedReaction(result)
                || time + EPSILON < nextC1EnergyAt) {
            return;
        }
        receiveFlatEnergy(getTalentValue("C1 Flat Energy", 15.0));
        nextC1EnergyAt = time
                + getTalentValue("C1 Energy Cooldown", 18.0);
    }

    /** Returns whether the fixed target currently carries Hunter's Mark. */
    public boolean isHunterMarkActive(double currentTime) {
        normalizeAt(currentTime);
        return currentTime < hunterMarkUntil;
    }

    /** Returns active Hunter's Precision stacks after exact-expiry pruning. */
    public int getPrecisionStackCount(double currentTime) {
        normalizeAt(currentTime);
        return precisionExpirations.size();
    }

    /** Returns whether Hunter's Advance is active at the supplied time. */
    public boolean isBurstFieldActive(double currentTime) {
        normalizeAt(currentTime);
        return currentTime < burstUntil;
    }

    /** Returns C1's next accepted Electro-related reaction timestamp. */
    public double getNextC1EnergyAt() {
        return nextC1EnergyAt;
    }

    /** Reports that Burst field and Tugarin cadence are not inferred. */
    public boolean isAutomaticBurstCadenceRepresented() {
        return false;
    }

    /** Reports that frames, gauge, ICD, and particles are not represented. */
    public boolean hasSourceBackedAnimationGaugeIcdAndParticles() {
        return false;
    }

    /** Reports that Tugarin healing and C4 healing are not represented. */
    public boolean isHealingRepresented() {
        return false;
    }

    /**
     * Resolves one explicitly requested Fulgurite Hunting Field damage tick.
     *
     * @param simulator bound simulator with an active Hunter's Advance field
     */
    public void triggerFulguriteFieldTick(CombatSimulator simulator) {
        validateManualBurstTick(simulator);
        performInertHit(
                simulator,
                "Fulgurite Hunting Field",
                getBurstTalentValue(
                        "Fulgurite Hunting Field",
                        "Fulgurite Hunting Field C5",
                        1.27432,
                        1.4992),
                Element.ELECTRO,
                StatType.BURST_DMG_BONUS,
                ActionType.BURST);
    }

    /**
     * Resolves one explicitly requested Tugarin attack and its mark effects.
     *
     * @param simulator bound simulator with an active Hunter's Advance field
     */
    public void triggerTugarinTick(CombatSimulator simulator) {
        validateManualBurstTick(simulator);
        performInertHit(
                simulator,
                "Tugarin",
                getBurstTalentValue(
                        "Tugarin",
                        "Tugarin C5",
                        0.853794,
                        1.004464),
                Element.ELECTRO,
                StatType.BURST_DMG_BONUS,
                ActionType.BURST);
        if (isHunterMarkActive(simulator.getCurrentTime())) {
            hunterMarkUntil = Double.NEGATIVE_INFINITY;
            grantPrecision(simulator);
        }
        if (constellation >= 2) {
            applyHunterMarkWithoutActivation(simulator.getCurrentTime());
        }
    }

    private void normalAttack(CombatSimulator simulator) {
        switch (normalAttackStep) {
            case 0:
                performInertHit(
                        simulator,
                        "Skirmishing Spear N1",
                        getTalentValue("N1", 0.87848),
                        Element.PHYSICAL,
                        StatType.NORMAL_ATTACK_DMG_BONUS,
                        ActionType.NORMAL);
                break;
            case 1:
                performInertHit(
                        simulator,
                        "Skirmishing Spear N2",
                        getTalentValue("N2", 0.8848),
                        Element.PHYSICAL,
                        StatType.NORMAL_ATTACK_DMG_BONUS,
                        ActionType.NORMAL);
                break;
            case 2:
                performInertHit(
                        simulator,
                        "Skirmishing Spear N3-1",
                        getTalentValue("N3 Hit 1", 0.62884),
                        Element.PHYSICAL,
                        StatType.NORMAL_ATTACK_DMG_BONUS,
                        ActionType.NORMAL);
                performInertHit(
                        simulator,
                        "Skirmishing Spear N3-2",
                        getTalentValue("N3 Hit 2", 0.5846),
                        Element.PHYSICAL,
                        StatType.NORMAL_ATTACK_DMG_BONUS,
                        ActionType.NORMAL);
                break;
            case 3:
                performInertHit(
                        simulator,
                        "Skirmishing Spear N4",
                        getTalentValue("N4", 1.39356),
                        Element.PHYSICAL,
                        StatType.NORMAL_ATTACK_DMG_BONUS,
                        ActionType.NORMAL);
                applyOrActivateHunterMark(simulator);
                break;
            default:
                throw new IllegalStateException(
                        "Unexpected Alyosha Normal step: " + normalAttackStep);
        }
        normalAttackStep = (normalAttackStep + 1) % NORMAL_ATTACK_COUNT;
    }

    private void chargedAttack(CombatSimulator simulator) {
        performInertHit(
                simulator,
                "Skirmishing Spear Charged Attack",
                getTalentValue("Charged Attack", 2.03978),
                Element.PHYSICAL,
                StatType.CHARGED_ATTACK_DMG_BONUS,
                ActionType.CHARGE);
    }

    private void elementalSkill(
            SkillActionMode mode,
            CombatSimulator simulator) {
        if (!supportsSkillActionMode(mode)) {
            throw new IllegalArgumentException(
                    "Unsupported Alyosha Skill mode: " + mode);
        }
        markSkillUsed(
                simulator.getCurrentTime(),
                simulator.getApplicableBuffs(this));
        boolean hold = mode == SkillActionMode.HOLD;
        performInertHit(
                simulator,
                hold ? "Thunderbolt Strike Hold" : "Thunderbolt Strike Press",
                hold
                        ? getSkillTalentValue(
                                "Hold Skill", "Hold Skill C3",
                                6.0928, 7.168)
                        : getSkillTalentValue(
                                "Press Skill", "Press Skill C3",
                                4.87424, 5.7344),
                Element.ELECTRO,
                StatType.SKILL_DMG_BONUS,
                ActionType.SKILL);
        applyOrActivateHunterMark(simulator);
    }

    private void elementalBurst(CombatSimulator simulator) {
        markBurstUsed(
                simulator.getCurrentTime(),
                simulator.getApplicableBuffs(this));
        burstUntil = simulator.getCurrentTime()
                + getTalentValue("Burst Duration", 14.0)
                + (constellation >= 2
                        ? getTalentValue("C2 Burst Duration Bonus", 6.0)
                        : 0.0);
    }

    private void applyOrActivateHunterMark(CombatSimulator simulator) {
        double currentTime = simulator.getCurrentTime();
        if (isHunterMarkActive(currentTime)) {
            hunterMarkUntil = Double.NEGATIVE_INFINITY;
            grantPrecision(simulator);
            return;
        }
        hunterMarkUntil = currentTime
                + getTalentValue("Hunter's Mark Duration", 15.0);
    }

    private void applyHunterMarkWithoutActivation(double currentTime) {
        hunterMarkUntil = currentTime
                + getTalentValue("Hunter's Mark Duration", 15.0);
    }

    private void grantPrecision(CombatSimulator simulator) {
        double currentTime = simulator.getCurrentTime();
        normalizeAt(currentTime);
        int maximumStacks = constellation >= 6
                ? MAX_C6_PRECISION_STACKS : 1;
        if (precisionExpirations.size() >= maximumStacks) {
            return;
        }
        double duration = getTalentValue("Hunter's Precision Duration", 15.0);
        double expiration = currentTime + duration;
        precisionExpirations.add(expiration);
        double attackBonus = getSkillTalentValue(
                "Hunter's Precision ATK Bonus",
                "Hunter's Precision ATK Bonus C3",
                0.2014,
                0.23744);
        simulator.applyFieldBuff(new Buff(
                "Alyosha Hunter's Precision",
                duration,
                currentTime) {
            @Override
            protected void applyStats(
                    StatsContainer stats,
                    double buffTime) {
                stats.add(StatType.ATK_PERCENT, attackBonus);
                if (simulator.getStellarReactionManager()
                        .hasStellarConductRadiance(buffTime)) {
                    stats.add(
                            StatType.STELLAR_CONDUCT_DMG_BONUS,
                            getTalentValue(
                                    "Precision Stellar-Conduct DMG Bonus",
                                    0.20));
                }
            }
        }.sourcedBy(characterId));

        if (constellation >= 6 && precisionExpirations.size() == 2) {
            double emDuration = Math.max(
                    0.0,
                    Math.min(
                            precisionExpirations.get(0),
                            precisionExpirations.get(1)) - currentTime);
            double emBonus = getTalentValue("C6 Precision EM", 100.0);
            simulator.applyFieldBuff(new SimpleBuff(
                    "Alyosha C6 Hunter's Precision EM",
                    emDuration,
                    currentTime,
                    stats -> stats.add(
                            StatType.ELEMENTAL_MASTERY,
                            emBonus))
                    .sourcedBy(characterId));
        }
    }

    private void validateManualBurstTick(CombatSimulator simulator) {
        if (simulator == null || simulator != initializedSimulator) {
            throw new IllegalArgumentException(
                    "Alyosha manual tick requires the bound simulator");
        }
        if (!isBurstFieldActive(simulator.getCurrentTime())) {
            throw new IllegalStateException(
                    "Hunter's Advance is not active");
        }
    }

    private boolean isElectroRelatedReaction(ReactionResult result) {
        switch (result.getKind()) {
            case ELECTRO_CHARGED:
            case LUNAR_CHARGED:
            case SUPERCONDUCT:
            case OVERLOAD:
            case OVERLOADED:
            case QUICKEN:
            case AGGRAVATE:
            case HYPERBLOOM:
            case THUNDERCLOUD_STRIKE:
                return true;
            case STELLAR_CONDUCT:
                return result.isStateful();
            case SWIRL:
            case CRYSTALLIZE:
                return result.getRelatedElement() == Element.ELECTRO;
            default:
                return false;
        }
    }

    private double getSkillTalentValue(
            String baseKey,
            String c3Key,
            double baseDefault,
            double c3Default) {
        if (constellation >= 3) {
            return getTalentValue(c3Key, c3Default);
        }
        return getTalentValue(baseKey, baseDefault);
    }

    private double getBurstTalentValue(
            String baseKey,
            String c5Key,
            double baseDefault,
            double c5Default) {
        if (constellation >= 5) {
            return getTalentValue(c5Key, c5Default);
        }
        return getTalentValue(baseKey, baseDefault);
    }

    private void performInertHit(
            CombatSimulator simulator,
            String actionName,
            double multiplier,
            Element damageElement,
            StatType bonusStat,
            ActionType actionType) {
        AttackAction action = new AttackAction(
                actionName,
                multiplier,
                damageElement,
                StatType.BASE_ATK,
                bonusStat,
                0.0,
                actionType);
        action.setICD(ICDType.None, ICDTag.None, 0.0);
        simulator.performAction(characterId, action);
    }

    private void normalizeAt(double currentTime) {
        if (currentTime >= hunterMarkUntil) {
            hunterMarkUntil = Double.NEGATIVE_INFINITY;
        }
        precisionExpirations.removeIf(
                expiration -> currentTime >= expiration);
        if (currentTime >= burstUntil) {
            burstUntil = Double.NEGATIVE_INFINITY;
        }
    }

    /** Immutable Alyosha-owned state tied to one character instance. */
    private static final class AlyoshaState implements State {
        private final Alyosha owner;
        private final int normalAttackStep;
        private final double hunterMarkUntil;
        private final List<Double> precisionExpirations;
        private final double burstUntil;
        private final double nextC1EnergyAt;

        private AlyoshaState(
                Alyosha owner,
                int normalAttackStep,
                double hunterMarkUntil,
                List<Double> precisionExpirations,
                double burstUntil,
                double nextC1EnergyAt) {
            this.owner = owner;
            this.normalAttackStep = normalAttackStep;
            this.hunterMarkUntil = hunterMarkUntil;
            this.precisionExpirations = new ArrayList<>(precisionExpirations);
            this.burstUntil = burstUntil;
            this.nextC1EnergyAt = nextC1EnergyAt;
        }
    }
}

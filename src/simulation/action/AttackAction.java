package simulation.action;

import model.stats.StatsContainer;
import model.type.StatType;
import model.type.Element;
import model.type.ICDType;
import model.type.ICDTag;

import model.type.ActionType;
import java.util.Map;
import java.util.HashMap;

/**
 * Describes a single discrete damage-dealing action performed by a character.
 * Instances are passed to {@link simulation.CombatSimulator#performAction(String, AttackAction)}
 * and consumed by {@code DamageCalculator} to produce a final damage value.
 *
 * <p>Key responsibilities:
 * <ul>
 *   <li>Carries the talent motion value ({@link #damagePercent}), scaling stat, element,
 *       and {@link model.type.ActionType} needed by the damage formula.</li>
 *   <li>Specifies ICD group and gauge units for elemental aura application.</li>
 *   <li>Controls animation duration so that {@code advanceTime} correctly consumes
 *       cast time after each action.</li>
 *   <li>Supports custom Lunar mechanics via {@link #isLunarConsidered} and
 *       {@link #lunarReactionType}.</li>
 * </ul>
 */
public class AttackAction {
    private String name; // "Musou no Hitotachi"
    private double damagePercent; // 7.21 (721%) // Formerly motionValue
    private Element element; // ELECTRO
    private StatType scalingStat; // BASE_ATK usually
    private StatType bonusStat; // e.g. BURST_DMG_BONUS // Formerly bonusStatType
    private double animationDuration; // Default

    /**
     * Overrides the animation duration for this action.
     * The simulator advances time by this value after the action resolves,
     * factoring in ATK SPD for NORMAL / CHARGE actions.
     *
     * @param duration animation duration in seconds
     */
    public void setAnimationDuration(double duration) {
        this.animationDuration = duration;
    }

    private boolean useSnapshot;

    // ICD & Gauge Parameters
    private model.type.ICDType icdType; // Default
    private model.type.ICDTag icdTag; // Default (shared)
    private double gaugeUnits = 1.0; // Default 1.0 Global Units (GU)

    private boolean isLunarConsidered = false;
    private String lunarReactionType = null; // "Charged", "Bloom", "Crystallize"

    /**
     * Marks whether this action participates in the custom Lunar damage system.
     * When {@code true}, the simulator may fire a synthetic {@code ReactionResult} for
     * Lunar-specific hooks before normal damage calculation.
     *
     * @param b {@code true} to enable Lunar consideration
     */
    public void setLunarConsidered(boolean b) {
        this.isLunarConsidered = b;
    }

    /**
     * Returns whether this action is considered part of the Lunar damage system.
     *
     * @return {@code true} if Lunar hooks are active for this action
     */
    public boolean isLunarConsidered() {
        return isLunarConsidered;
    }

    /**
     * Sets the Lunar reaction sub-type (e.g. {@code "Charged"}, {@code "Bloom"},
     * {@code "Crystallize"}) and implicitly enables {@link #isLunarConsidered}.
     * This causes the simulator to notify reaction listeners with a synthetic
     * {@code "Lunar-<type>"} result before the normal damage step.
     *
     * @param type the Lunar reaction sub-type string, or {@code null} to clear
     */
    public void setLunarReactionType(String type) {
        this.lunarReactionType = type;
        if (type != null)
            this.isLunarConsidered = true;
    }

    /**
     * Returns the Lunar reaction sub-type for this action, or {@code null} if none.
     *
     * @return the Lunar reaction type string
     */
    public String getLunarReactionType() {
        return lunarReactionType;
    }

    private ActionType actionType;
    private boolean countsAsBurstDmg; // New flag for The Catch interaction
    private boolean countsAsSkillDmg; // New flag for Wolf-Fang interaction
    private double defenseIgnore = 0.0; // Defense Ignore 0.0 - 1.0 (e.g. Raiden C2)

    // Constructors

    /**
     * Minimal constructor. Uses 0.5 s animation, no bonus stat, no snapshot, {@link ActionType#OTHER}.
     *
     * @param name          display name of the action
     * @param damagePercent talent multiplier as a decimal (e.g. {@code 7.21} for 721%)
     * @param element       elemental infusion of the hit
     * @param scalingStat   stat used as the base for scaling (usually {@code BASE_ATK})
     */
    public AttackAction(String name, double damagePercent, Element element, StatType scalingStat) {
        this(name, damagePercent, element, scalingStat, null, 0.5, false, ActionType.OTHER);
    }

    /**
     * Constructor with bonus stat override. Uses 0.5 s animation, no snapshot, {@link ActionType#OTHER}.
     *
     * @param name          display name of the action
     * @param damagePercent talent multiplier as a decimal
     * @param element       elemental infusion of the hit
     * @param scalingStat   base scaling stat
     * @param bonusStat     additional DMG bonus stat to include (e.g. {@code BURST_DMG_BONUS})
     */
    public AttackAction(String name, double damagePercent, Element element, StatType scalingStat,
            StatType bonusStat) {
        this(name, damagePercent, element, scalingStat, bonusStat, 0.5, false, ActionType.OTHER);
    }

    /**
     * Constructor with bonus stat and custom animation duration. No snapshot, {@link ActionType#OTHER}.
     *
     * @param name          display name of the action
     * @param damagePercent talent multiplier as a decimal
     * @param element       elemental infusion of the hit
     * @param scalingStat   base scaling stat
     * @param bonusStat     additional DMG bonus stat
     * @param duration      animation duration in seconds
     */
    public AttackAction(String name, double damagePercent, Element element, StatType scalingStat, StatType bonusStat,
            double duration) {
        this(name, damagePercent, element, scalingStat, bonusStat, duration, false, ActionType.OTHER);
    }

    /**
     * Constructor with snapshot control. Uses {@link ActionType#OTHER}.
     *
     * @param name          display name of the action
     * @param damagePercent talent multiplier as a decimal
     * @param element       elemental infusion of the hit
     * @param scalingStat   base scaling stat
     * @param bonusStat     additional DMG bonus stat
     * @param duration      animation duration in seconds
     * @param useSnapshot   if {@code true}, stats are snapshotted at cast time rather than
     *                      re-evaluated at damage time
     */
    public AttackAction(String name, double damagePercent, Element element, StatType scalingStat, StatType bonusStat,
            double duration, boolean useSnapshot) {
        this(name, damagePercent, element, scalingStat, bonusStat, duration, useSnapshot, ActionType.OTHER);
    }

    /**
     * Constructor with explicit {@link ActionType}. No snapshot.
     *
     * @param name          display name of the action
     * @param damagePercent talent multiplier as a decimal
     * @param element       elemental infusion of the hit
     * @param scalingStat   base scaling stat
     * @param bonusStat     additional DMG bonus stat
     * @param duration      animation duration in seconds
     * @param actionType    the ability category (NORMAL, SKILL, BURST, etc.)
     */
    public AttackAction(String name, double damagePercent, Element element, StatType scalingStat, StatType bonusStat,
            double duration, ActionType actionType) {
        this(name, damagePercent, element, scalingStat, bonusStat, duration, false, actionType);
    }

    /**
     * Full constructor used internally by all shorter overloads.
     * Initializes ICD to {@link ICDType#Standard} / {@link ICDTag#None} with 1.0 GU.
     *
     * @param name          display name of the action
     * @param damagePercent talent multiplier as a decimal
     * @param element       elemental infusion of the hit
     * @param scalingStat   base scaling stat
     * @param bonusStat     additional DMG bonus stat
     * @param duration      animation duration in seconds
     * @param useSnapshot   whether stats are snapshotted at cast time
     * @param actionType    the ability category
     */
    public AttackAction(String name, double damagePercent, Element element, StatType scalingStat, StatType bonusStat,
            double duration, boolean useSnapshot, ActionType actionType) {
        this.name = name;
        this.damagePercent = damagePercent;
        this.element = element;
        this.scalingStat = scalingStat;
        this.bonusStat = bonusStat;
        this.animationDuration = duration;
        this.useSnapshot = useSnapshot;
        this.actionType = actionType;
        this.icdType = ICDType.Standard; // Default
        this.icdTag = ICDTag.None; // Default (shared)
        this.gaugeUnits = 1.0; // Default 1.0 Global Units (GU)
    }

    // Extra Bonuses
    private Map<StatType, Double> extraBonuses = new HashMap<>();

    /**
     * Adds an extra per-action stat bonus that is applied only when this action is calculated.
     * Useful for constellation or passive bonuses scoped to a specific hit.
     * Multiple calls for the same {@link StatType} accumulate additively.
     *
     * @param type  the stat to modify
     * @param value the additive amount to add
     */
    public void addBonusStat(StatType type, double value) {
        extraBonuses.merge(type, value, Double::sum);
    }

    /**
     * Returns the map of per-action extra stat bonuses set via {@link #addBonusStat}.
     *
     * @return an unmodifiable view of the extra bonus map
     */
    public Map<StatType, Double> getExtraBonuses() {
        return extraBonuses;
    }

    /**
     * Marks this action as counting as Burst damage for weapon / artifact interactions
     * that specifically trigger on Burst hits (e.g. The Catch).
     *
     * @param countsAsBurstDmg {@code true} to enable Burst damage classification
     */
    public void setCountsAsBurstDmg(boolean countsAsBurstDmg) {
        this.countsAsBurstDmg = countsAsBurstDmg;
    }

    /**
     * Returns whether this action is classified as Burst damage for bonus interactions.
     *
     * @return {@code true} if this action counts as Burst damage
     */
    public boolean isCountsAsBurstDmg() {
        return countsAsBurstDmg;
    }

    /**
     * Marks this action as counting as Skill damage for weapon / artifact interactions
     * that specifically trigger on Skill hits (e.g. Wolf-Fang).
     *
     * @param countsAsSkillDmg {@code true} to enable Skill damage classification
     */
    public void setCountsAsSkillDmg(boolean countsAsSkillDmg) {
        this.countsAsSkillDmg = countsAsSkillDmg;
    }

    /**
     * Returns whether this action is classified as Skill damage for bonus interactions.
     *
     * @return {@code true} if this action counts as Skill damage
     */
    public boolean isCountsAsSkillDmg() {
        return countsAsSkillDmg;
    }

    /**
     * Sets the ICD parameters for elemental application tracking.
     * Calls to {@code ICDManager} use these values to determine whether
     * this hit actually applies an elemental gauge to the enemy.
     *
     * @param type  the {@link ICDType} (e.g. Standard 2.5 s / 3-hit rule)
     * @param tag   the {@link ICDTag} grouping tag that separates independent ICD sequences
     * @param gauge the gauge units (GU) applied on a successful element application
     */
    public void setICD(ICDType type, ICDTag tag, double gauge) {
        this.icdType = type;
        this.icdTag = tag;
        this.gaugeUnits = gauge;
    }

    /**
     * Returns the ICD type used to evaluate element application eligibility.
     *
     * @return the {@link ICDType}
     */
    public ICDType getICDType() {
        return icdType;
    }

    /**
     * Returns the ICD tag used to group hits into independent ICD sequences.
     *
     * @return the {@link ICDTag}
     */
    public ICDTag getICDTag() {
        return icdTag;
    }

    /**
     * Returns the elemental gauge units applied by this hit when ICD permits.
     *
     * @return gauge units (GU)
     */
    public double getGaugeUnits() {
        return gaugeUnits;
    }

    /**
     * Returns whether this action uses snapshotted stats from cast time.
     * When {@code true}, buffs that expire before the hit still contribute.
     *
     * @return {@code true} if stats are snapshotted at cast time
     */
    public boolean isUseSnapshot() {
        return useSnapshot;
    }

    /**
     * Returns the animation duration used to advance simulation time after the action.
     *
     * @return animation duration in seconds
     */
    public double getAnimationDuration() {
        return animationDuration;
    }

    /**
     * Returns the display name of this action (e.g. {@code "Musou no Hitotachi"}).
     *
     * @return action name
     */
    public String getName() {
        return name;
    }

    /**
     * Returns the talent multiplier as a decimal (e.g. {@code 7.21} represents 721%).
     *
     * @return damage percent / motion value
     */
    public double getDamagePercent() {
        return damagePercent;
    }

    /**
     * Returns the element of this hit, used for aura application and reaction checks.
     *
     * @return hit element
     */
    public Element getElement() {
        return element;
    }

    /**
     * Returns the primary stat this action scales from (e.g. {@code BASE_ATK}).
     *
     * @return the scaling {@link StatType}
     */
    public StatType getScalingStat() {
        return scalingStat;
    }

    /**
     * Returns the secondary DMG bonus stat specific to this action's ability type
     * (e.g. {@code BURST_DMG_BONUS}), or {@code null} if none.
     *
     * @return the bonus {@link StatType}, or {@code null}
     */
    public StatType getBonusStat() {
        return bonusStat;
    }

    /**
     * Deprecated compatibility method. Always returns {@code null}.
     *
     * @return {@code null}
     * @deprecated Use {@link #getElement()} instead.
     */
    // Compatibility method if DamageCalculator uses it?
    public model.type.StatType getElementType() {
        return null; // Deprecated or unused
    }

    /**
     * Returns the ability category of this action (NORMAL, SKILL, BURST, etc.).
     *
     * @return the {@link ActionType}
     */
    public ActionType getActionType() {
        return actionType;
    }

    /**
     * Compatibility alias for {@link #getDamagePercent()}.
     *
     * @return the damage percent / motion value
     * @deprecated Use {@link #getDamagePercent()} instead.
     */
    public double getMotionValue() {
        return damagePercent; // Compatibility, though deprecated
    }

    /**
     * Resolves the numeric value of the scaling stat from the provided {@link StatsContainer}.
     * Handles ATK, HP, and DEF stat families; falls back to total ATK for unknown stats.
     *
     * @param stats the stat container to resolve from
     * @return the resolved numeric stat value
     */
    public double getScalingStatValue(StatsContainer stats) {
        if (scalingStat == StatType.BASE_ATK || scalingStat == StatType.ATK_PERCENT
                || scalingStat == StatType.ATK_FLAT) {
            return stats.getTotalAtk();
        }
        if (scalingStat == StatType.BASE_HP || scalingStat == StatType.HP_PERCENT) {
            return stats.getTotalHp();
        }
        if (scalingStat == StatType.BASE_DEF || scalingStat == StatType.DEF_PERCENT) {
            return stats.getTotalDef();
        }
        return stats.getTotalAtk(); // Fallback
    }

    /**
     * Sets the defense ignore fraction for this action (range 0.0–1.0).
     * Used for constellation effects such as Raiden C2.
     *
     * @param val defense ignore value (0.0 = none, 1.0 = full ignore)
     */
    public void setDefenseIgnore(double val) {
        this.defenseIgnore = val;
    }

    /**
     * Returns the defense ignore fraction applied during damage calculation.
     *
     * @return defense ignore value (0.0–1.0)
     */
    public double getDefenseIgnore() {
        return defenseIgnore;
    }

    private String debugFormula;

    /**
     * Stores a human-readable formula string used in HTML report tooltips and debug logs.
     *
     * @param debugFormula the formula string to attach
     */
    public void setDebugFormula(String debugFormula) {
        this.debugFormula = debugFormula;
    }

    /**
     * Returns the debug formula string for this action, or {@code null} if not set.
     *
     * @return debug formula string
     */
    public String getDebugFormula() {
        return debugFormula;
    }
}

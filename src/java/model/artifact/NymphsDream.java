package model.artifact;

import java.util.Objects;

import model.entity.ArtifactSet;
import model.entity.Character;
import model.entity.DamageTriggeredArtifactEffect;
import model.entity.SimulatorInitializedArtifactEffect;
import model.stats.StatsContainer;
import model.type.ActionType;
import model.type.StatType;
import simulation.CombatSimulator;
import simulation.action.AttackAction;

/**
 * Nymph's Dream artifact set with independent damage-category windows.
 *
 * <p>The fixed two-piece bonus grants 15% Hydro DMG Bonus. Positive owner hits
 * activate or refresh one Normal, Charged, Plunging, Skill, or Burst category
 * for the half-open interval {@code [hit, hit + 8)}. One, two, or at least
 * three active categories grant progressively larger ATK and Hydro bonuses.
 * Damage callbacks run after resolution, so the triggering hit uses the
 * pre-hit tier.</p>
 */
public class NymphsDream extends ArtifactSet
        implements SimulatorInitializedArtifactEffect, DamageTriggeredArtifactEffect {
    private static final double CATEGORY_DURATION = 8.0;

    private Character owner;
    private CombatSimulator simulator;
    private double normalExpiration = Double.NEGATIVE_INFINITY;
    private double chargedExpiration = Double.NEGATIVE_INFINITY;
    private double plungingExpiration = Double.NEGATIVE_INFINITY;
    private double skillExpiration = Double.NEGATIVE_INFINITY;
    private double burstExpiration = Double.NEGATIVE_INFINITY;

    /** Categories tracked independently by the four-piece effect. */
    private enum HitCategory {
        NORMAL,
        CHARGED,
        PLUNGING,
        SKILL,
        BURST
    }

    /** Constructs Nymph's Dream with a fresh fixed-stat container. */
    public NymphsDream() {
        this(new StatsContainer());
    }

    /**
     * Constructs Nymph's Dream while preserving supplied main/sub stats.
     *
     * @param stats non-null artifact main and sub stats
     */
    public NymphsDream(StatsContainer stats) {
        super("Nymph's Dream", Objects.requireNonNull(stats, "stats"));
        getStats().add(StatType.HYDRO_DMG_BONUS, 0.15);
    }

    /**
     * Binds the category windows to exactly one owner and simulator.
     *
     * <p>Repeating the identical binding is idempotent. Reusing one artifact
     * instance for another owner or simulator is rejected.</p>
     *
     * @param equippedOwner character carrying this artifact set
     * @param sim simulator containing the equipped owner
     * @param startsActive whether the owner starts as the active character
     */
    @Override
    public void initializeForSimulator(
            Character equippedOwner,
            CombatSimulator sim,
            boolean startsActive) {
        if (equippedOwner == null || sim == null) {
            throw new IllegalArgumentException("Artifact owner and simulator are required");
        }
        if (simulator != null) {
            if (owner != equippedOwner || simulator != sim) {
                throw new IllegalStateException(
                        "Nymph's Dream is already bound to another simulator");
            }
            return;
        }
        owner = equippedOwner;
        simulator = sim;
    }

    /**
     * Activates or refreshes the category of an attributed positive owner hit.
     *
     * <p>A primary Normal, Charged, Plunging, Skill, or Burst action keeps its
     * natural category even when a secondary damage-classification flag is set.
     * Skill and Burst flags classify otherwise uncategorized actions. Invalid
     * bindings, null actions, non-positive damage, and unflagged Dash/Other
     * actions are inert. Field state does not gate the effect.</p>
     *
     * @param sim simulator dispatching the resolved damage
     * @param action action that produced the damage
     * @param damage final post-mitigation damage
     * @param callbackOwner artifact wearer supplied by the dispatcher
     */
    @Override
    public void onDamage(
            CombatSimulator sim,
            AttackAction action,
            double damage,
            Character callbackOwner) {
        if (simulator == null
                || sim != simulator
                || callbackOwner != owner
                || action == null
                || !(damage > 0.0)) {
            return;
        }

        HitCategory category = classify(action);
        if (category == null) {
            return;
        }
        refreshCategory(category, sim.getCurrentTime() + CATEGORY_DURATION);
    }

    /**
     * Applies the four-piece tier resolved from active category windows.
     *
     * @param totalStats aggregated stats container to mutate in place
     */
    @Override
    public void applyPassive(StatsContainer totalStats) {
        if (owner == null || simulator == null) {
            return;
        }

        int activeCategories = countActiveCategories(simulator.getCurrentTime());
        if (activeCategories >= 3) {
            totalStats.add(StatType.ATK_PERCENT, 0.25);
            totalStats.add(StatType.HYDRO_DMG_BONUS, 0.15);
        } else if (activeCategories == 2) {
            totalStats.add(StatType.ATK_PERCENT, 0.16);
            totalStats.add(StatType.HYDRO_DMG_BONUS, 0.09);
        } else if (activeCategories == 1) {
            totalStats.add(StatType.ATK_PERCENT, 0.07);
            totalStats.add(StatType.HYDRO_DMG_BONUS, 0.04);
        }
    }

    /** Returns the natural or fallback Skill/Burst category of an action. */
    private HitCategory classify(AttackAction action) {
        ActionType actionType = action.getActionType();
        if (actionType != null) {
            switch (actionType) {
                case NORMAL:
                    return HitCategory.NORMAL;
                case CHARGE:
                case EXTRA:
                    return HitCategory.CHARGED;
                case PLUNGE:
                    return HitCategory.PLUNGING;
                case SKILL:
                    return HitCategory.SKILL;
                case BURST:
                    return HitCategory.BURST;
                default:
                    break;
            }
        }
        if (action.isCountsAsSkillDmg()) {
            return HitCategory.SKILL;
        }
        if (action.isCountsAsBurstDmg()) {
            return HitCategory.BURST;
        }
        return null;
    }

    /** Refreshes exactly one category window. */
    private void refreshCategory(HitCategory category, double expiration) {
        switch (category) {
            case NORMAL:
                normalExpiration = expiration;
                break;
            case CHARGED:
                chargedExpiration = expiration;
                break;
            case PLUNGING:
                plungingExpiration = expiration;
                break;
            case SKILL:
                skillExpiration = expiration;
                break;
            case BURST:
                burstExpiration = expiration;
                break;
            default:
                throw new IllegalStateException("Unsupported Nymph's Dream hit category");
        }
    }

    /** Counts category windows active at one simulator timestamp. */
    private int countActiveCategories(double currentTime) {
        int activeCategories = 0;
        if (currentTime < normalExpiration) {
            activeCategories++;
        }
        if (currentTime < chargedExpiration) {
            activeCategories++;
        }
        if (currentTime < plungingExpiration) {
            activeCategories++;
        }
        if (currentTime < skillExpiration) {
            activeCategories++;
        }
        if (currentTime < burstExpiration) {
            activeCategories++;
        }
        return activeCategories;
    }
}

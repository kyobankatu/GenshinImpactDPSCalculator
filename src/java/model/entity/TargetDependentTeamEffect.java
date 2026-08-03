package model.entity;

import model.stats.StatsContainer;
import simulation.action.AttackAction;

/**
 * Capability for party effects that depend on the live target state.
 *
 * <p>The supplied stats are a per-hit copy owned by the damage formula. This
 * hook runs after snapshot resolution so target-bound effects apply at impact
 * without becoming part of the attacker's stored snapshot.</p>
 */
public interface TargetDependentTeamEffect {
    /**
     * Applies target-dependent party stats for one direct hit.
     *
     * @param stats per-hit stats container to mutate
     * @param attacker character performing the hit
     * @param target enemy being hit
     * @param action attack being resolved
     * @param currentTime simulation time used for live target state
     */
    void applyTargetDependentTeamStats(
            StatsContainer stats,
            Character attacker,
            Enemy target,
            AttackAction action,
            double currentTime);
}

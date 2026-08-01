package model.entity;

import model.stats.StatsContainer;

/**
 * Capability for weapon stats that depend on the current enemy state.
 *
 * <p>The supplied stats are a per-hit copy owned by the damage formula. An
 * implementation may mutate that copy but must not retain it or modify the
 * character's structural, effective, or snapshotted stats.</p>
 */
public interface TargetDependentWeaponEffect {
    /**
     * Applies target-dependent stats for one direct hit.
     *
     * @param stats       per-hit stats container to mutate
     * @param target      enemy being hit
     * @param currentTime simulation time in seconds used for live target state
     */
    void applyTargetDependentStats(StatsContainer stats, Enemy target, double currentTime);
}

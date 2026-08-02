package model.entity;

import model.stats.StatsContainer;

/**
 * Capability for artifact stats that depend on the current enemy state.
 *
 * <p>The supplied stats are a per-hit copy owned by the damage formula. An
 * implementation may mutate that copy but must not retain it or modify the
 * character's structural, effective, or snapshotted stats.</p>
 */
public interface TargetDependentArtifactEffect {
    /**
     * Applies target-dependent artifact stats for one direct hit.
     *
     * @param stats per-hit stats container to mutate
     * @param target enemy being hit
     * @param currentTime simulation time used for live target state
     */
    void applyTargetDependentStats(
            StatsContainer stats,
            Enemy target,
            double currentTime);
}

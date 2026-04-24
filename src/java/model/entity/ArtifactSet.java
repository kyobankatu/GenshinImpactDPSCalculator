package model.entity;

import model.stats.StatsContainer;

/**
 * Represents an artifact set (e.g. "Emblem of Severed Fate 4pc") equipped on a
 * {@link Character}.
 *
 * <p>An artifact set contributes fixed stats via {@link #getStats()} and may
 * modify stats dynamically through {@link #applyPassive}. Event-driven set
 * bonuses should implement focused capability interfaces such as
 * {@link ReactionAwareArtifact}, {@link DamageTriggeredArtifactEffect},
 * {@link SwitchAwareArtifact}, or {@link BurstTriggeredArtifactEffect}.
 */
public class ArtifactSet {
    private String name; // e.g., "Emblem of Severed Fate (4pc)"
    private StatsContainer stats;

    /**
     * Constructs an artifact set with the given name and flat stat container.
     *
     * @param name  display name of the set (e.g. "Emblem of Severed Fate (4pc)")
     * @param stats stats contributed by the set's fixed bonuses (main stats,
     *              2pc bonus)
     */
    public ArtifactSet(String name, StatsContainer stats) {
        this.name = name;
        this.stats = stats;
    }

    /**
     * Returns the artifact set's flat stat container.
     *
     * @return stats container
     */
    public StatsContainer getStats() {
        return stats;
    }

    /**
     * Returns the artifact set's display name.
     *
     * @return artifact set name
     */
    public String getName() {
        return name;
    }

    /**
     * Applies the artifact set's dynamic passive bonus to the provided stats
     * container. Called during stat compilation after flat stats have been
     * merged. Example: Emblem 4pc converts ER% into Burst DMG%.
     * Default implementation is a no-op.
     *
     * @param totalStats the aggregated stats container to mutate in-place
     */
    public void applyPassive(StatsContainer totalStats) {
        // Default: do nothing
    }
}

package model.artifact;

import java.util.Objects;

import model.entity.ArtifactSet;
import model.stats.StatsContainer;

/**
 * Prayers to Springtime at the simulator's player-status boundary.
 *
 * <p>The one-piece effect shortens Cryo status on the wearer. Player-applied
 * elemental status duration is not modeled, so the effect remains inert and
 * must not be represented as an outgoing combat stat.</p>
 */
public class PrayersToSpringtime extends ArtifactSet {
    /** Constructs Prayers to Springtime with a fresh stat container. */
    public PrayersToSpringtime() {
        this(new StatsContainer());
    }

    /**
     * Constructs the set while preserving supplied artifact stats.
     *
     * @param stats non-null artifact main and sub stats
     */
    public PrayersToSpringtime(StatsContainer stats) {
        super("Prayers to Springtime", Objects.requireNonNull(stats, "stats"));
    }
}

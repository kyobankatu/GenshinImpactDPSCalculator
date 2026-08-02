package model.artifact;

import java.util.Objects;

import model.entity.ArtifactSet;
import model.stats.StatsContainer;

/**
 * Prayers for Illumination at the simulator's player-status boundary.
 *
 * <p>The one-piece effect shortens Pyro status on the wearer. Player-applied
 * elemental status duration is not modeled, so the effect remains inert and
 * must not be represented as an outgoing combat stat.</p>
 */
public class PrayersForIllumination extends ArtifactSet {
    /** Constructs Prayers for Illumination with a fresh stat container. */
    public PrayersForIllumination() {
        this(new StatsContainer());
    }

    /**
     * Constructs the set while preserving supplied artifact stats.
     *
     * @param stats non-null artifact main and sub stats
     */
    public PrayersForIllumination(StatsContainer stats) {
        super("Prayers for Illumination", Objects.requireNonNull(stats, "stats"));
    }
}

package model.artifact;

import java.util.Objects;

import model.entity.ArtifactSet;
import model.stats.StatsContainer;

/**
 * Prayers for Destiny at the simulator's player-status boundary.
 *
 * <p>The one-piece effect shortens Hydro status on the wearer. Player-applied
 * elemental status duration is not modeled, so the effect remains inert and
 * must not be represented as an outgoing combat stat.</p>
 */
public class PrayersForDestiny extends ArtifactSet {
    /** Constructs Prayers for Destiny with a fresh stat container. */
    public PrayersForDestiny() {
        this(new StatsContainer());
    }

    /**
     * Constructs Prayers for Destiny while preserving supplied artifact stats.
     *
     * @param stats non-null artifact main and sub stats
     */
    public PrayersForDestiny(StatsContainer stats) {
        super("Prayers for Destiny", Objects.requireNonNull(stats, "stats"));
    }
}

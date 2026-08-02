package model.artifact;

import java.util.Objects;

import model.entity.ArtifactSet;
import model.stats.StatsContainer;

/**
 * Prayers for Wisdom at the simulator's player-status boundary.
 *
 * <p>The one-piece effect shortens Electro status on the wearer. Player-applied
 * elemental status duration is not modeled, so the effect remains inert and
 * must not be represented as an outgoing combat stat.</p>
 */
public class PrayersForWisdom extends ArtifactSet {
    /** Constructs Prayers for Wisdom with a fresh stat container. */
    public PrayersForWisdom() {
        this(new StatsContainer());
    }

    /**
     * Constructs Prayers for Wisdom while preserving supplied artifact stats.
     *
     * @param stats non-null artifact main and sub stats
     */
    public PrayersForWisdom(StatsContainer stats) {
        super("Prayers for Wisdom", Objects.requireNonNull(stats, "stats"));
    }
}

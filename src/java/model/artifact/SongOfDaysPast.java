package model.artifact;

import java.util.Objects;

import model.entity.ArtifactSet;
import model.stats.StatsContainer;
import model.type.StatType;

/**
 * Song of Days Past at the simulator's no-player-healing boundary.
 *
 * <p>The fixed two-piece bonus grants 15% outgoing Healing Bonus. Without
 * healing and overhealing events, Yearning cannot record an amount and the
 * subsequent five-hit flat-damage effect remains inactive.</p>
 */
public class SongOfDaysPast extends ArtifactSet {
    /** Constructs Song of Days Past with fresh stats. */
    public SongOfDaysPast() {
        this(new StatsContainer());
    }

    /**
     * Constructs Song while preserving supplied main and sub stats.
     *
     * @param stats non-null artifact main and sub stats
     */
    public SongOfDaysPast(StatsContainer stats) {
        super("Song of Days Past", Objects.requireNonNull(stats, "stats"));
        getStats().add(StatType.HEALING_BONUS, 0.15);
    }
}

package model.artifact;

import java.util.Objects;

import model.entity.ArtifactSet;
import model.stats.StatsContainer;

/**
 * Traveling Doctor at the simulator's player-healing boundary.
 *
 * <p>The two-piece effect increases healing received by the wearer and the
 * four-piece effect restores the wearer's HP after a Burst. Incoming healing
 * and player HP restoration are not modeled, so both effects remain inert.</p>
 */
public class TravelingDoctor extends ArtifactSet {
    /** Constructs Traveling Doctor with a fresh stat container. */
    public TravelingDoctor() {
        this(new StatsContainer());
    }

    /**
     * Constructs Traveling Doctor while preserving supplied artifact stats.
     *
     * @param stats non-null artifact main and sub stats
     */
    public TravelingDoctor(StatsContainer stats) {
        super("Traveling Doctor", Objects.requireNonNull(stats, "stats"));
    }
}

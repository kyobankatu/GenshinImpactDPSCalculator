package model.artifact;

import java.util.Objects;

import model.entity.ArtifactSet;
import model.stats.StatsContainer;
import model.type.StatType;

/**
 * Berserker artifact set at the simulator's full-player-HP boundary.
 *
 * <p>The fixed two-piece bonus grants 12% CRIT Rate. Player current HP never
 * falls below 70% in the supported runtime, so the four-piece bonus remains
 * inactive.</p>
 */
public class Berserker extends ArtifactSet {
    /** Constructs Berserker with a fresh stat container. */
    public Berserker() {
        this(new StatsContainer());
    }

    /**
     * Constructs Berserker while preserving supplied main and sub stats.
     *
     * @param stats non-null artifact main and sub stats
     */
    public Berserker(StatsContainer stats) {
        super("Berserker", Objects.requireNonNull(stats, "stats"));
        getStats().add(StatType.CRIT_RATE, 0.12);
    }
}

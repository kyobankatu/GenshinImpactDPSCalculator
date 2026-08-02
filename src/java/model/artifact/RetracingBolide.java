package model.artifact;

import java.util.Objects;

import model.entity.ArtifactSet;
import model.stats.StatsContainer;

/**
 * Retracing Bolide at the simulator's defensive-state boundary.
 *
 * <p>The two-piece bonus modifies owner shield strength and the four-piece
 * bonus requires a live shield. Player shields are not represented as a
 * general runtime state, so both effects remain inactive rather than becoming
 * unconditional outgoing stats.</p>
 */
public class RetracingBolide extends ArtifactSet {
    /** Constructs Retracing Bolide with a fresh stat container. */
    public RetracingBolide() {
        this(new StatsContainer());
    }

    /**
     * Constructs Retracing Bolide while preserving supplied artifact stats.
     *
     * @param stats non-null artifact main and sub stats
     */
    public RetracingBolide(StatsContainer stats) {
        super("Retracing Bolide", Objects.requireNonNull(stats, "stats"));
    }
}

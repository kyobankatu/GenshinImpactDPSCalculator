package model.artifact;

import java.util.Objects;

import model.entity.ArtifactSet;
import model.stats.StatsContainer;

/**
 * Tiny Miracle at the simulator's incoming-damage boundary.
 *
 * <p>Both set effects modify the wearer's elemental resistance, with the
 * four-piece effect reacting to incoming elemental damage. Player resistance
 * and incoming damage are not modeled, so neither effect contributes outgoing
 * or enemy-facing stats.</p>
 */
public class TinyMiracle extends ArtifactSet {
    /** Constructs Tiny Miracle with a fresh stat container. */
    public TinyMiracle() {
        this(new StatsContainer());
    }

    /**
     * Constructs Tiny Miracle while preserving supplied artifact stats.
     *
     * @param stats non-null artifact main and sub stats
     */
    public TinyMiracle(StatsContainer stats) {
        super("Tiny Miracle", Objects.requireNonNull(stats, "stats"));
    }
}

package model.artifact;

import java.util.Objects;

import model.entity.ArtifactSet;
import model.stats.StatsContainer;
import model.type.StatType;

/**
 * Fragment of Harmonic Whimsy at the simulator's Bond-of-Life boundary.
 *
 * <p>The fixed two-piece bonus grants 18% ATK. Bond of Life is not modeled, so
 * the four-piece damage stacks remain inactive.</p>
 */
public class FragmentOfHarmonicWhimsy extends ArtifactSet {
    /** Constructs Fragment of Harmonic Whimsy with fresh stats. */
    public FragmentOfHarmonicWhimsy() {
        this(new StatsContainer());
    }

    /**
     * Constructs Fragment while preserving supplied main and sub stats.
     *
     * @param stats non-null artifact main and sub stats
     */
    public FragmentOfHarmonicWhimsy(StatsContainer stats) {
        super("Fragment of Harmonic Whimsy",
                Objects.requireNonNull(stats, "stats"));
        getStats().add(StatType.ATK_PERCENT, 0.18);
    }
}

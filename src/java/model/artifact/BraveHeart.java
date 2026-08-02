package model.artifact;

import java.util.Objects;

import model.entity.ArtifactSet;
import model.stats.StatsContainer;
import model.type.StatType;

/**
 * Brave Heart artifact set at the simulator's missing-enemy-HP boundary.
 *
 * <p>The fixed two-piece bonus grants 18% ATK. Enemy current HP is not modeled,
 * so the four-piece above-50% damage condition remains inactive.</p>
 */
public class BraveHeart extends ArtifactSet {
    /** Constructs Brave Heart with a fresh stat container. */
    public BraveHeart() {
        this(new StatsContainer());
    }

    /**
     * Constructs Brave Heart while preserving supplied main and sub stats.
     *
     * @param stats non-null artifact main and sub stats
     */
    public BraveHeart(StatsContainer stats) {
        super("Brave Heart", Objects.requireNonNull(stats, "stats"));
        getStats().add(StatType.ATK_PERCENT, 0.18);
    }
}

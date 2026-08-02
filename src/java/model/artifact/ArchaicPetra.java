package model.artifact;

import java.util.Objects;

import model.entity.ArtifactSet;
import model.stats.StatsContainer;
import model.type.StatType;

/**
 * Archaic Petra at the simulator's missing-Crystallize-pickup boundary.
 *
 * <p>The fixed two-piece bonus grants 15% Geo DMG. Standard Crystallize shard
 * pickup and its selected-element party buff are not modeled, so the
 * four-piece effect remains inactive.</p>
 */
public class ArchaicPetra extends ArtifactSet {
    /** Constructs Archaic Petra with a fresh stat container. */
    public ArchaicPetra() {
        this(new StatsContainer());
    }

    /**
     * Constructs Archaic Petra while preserving supplied main and sub stats.
     *
     * @param stats non-null artifact main and sub stats
     */
    public ArchaicPetra(StatsContainer stats) {
        super("Archaic Petra", Objects.requireNonNull(stats, "stats"));
        getStats().add(StatType.GEO_DMG_BONUS, 0.15);
    }
}

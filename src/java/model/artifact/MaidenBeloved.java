package model.artifact;

import java.util.Objects;

import model.entity.ArtifactSet;
import model.stats.StatsContainer;
import model.type.StatType;

/**
 * Maiden Beloved at the simulator's no-player-healing boundary.
 *
 * <p>The fixed two-piece bonus grants 15% outgoing Healing Bonus. Incoming
 * healing received by party members is not modeled, so the four-piece effect
 * remains inactive.</p>
 */
public class MaidenBeloved extends ArtifactSet {
    /** Constructs Maiden Beloved with a fresh stat container. */
    public MaidenBeloved() {
        this(new StatsContainer());
    }

    /**
     * Constructs Maiden Beloved while preserving supplied main and sub stats.
     *
     * @param stats non-null artifact main and sub stats
     */
    public MaidenBeloved(StatsContainer stats) {
        super("Maiden Beloved", Objects.requireNonNull(stats, "stats"));
        getStats().add(StatType.HEALING_BONUS, 0.15);
    }
}

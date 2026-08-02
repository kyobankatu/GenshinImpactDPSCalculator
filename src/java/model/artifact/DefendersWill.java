package model.artifact;

import java.util.Objects;

import model.entity.ArtifactSet;
import model.stats.StatsContainer;
import model.type.StatType;

/**
 * Defender's Will at the simulator's player-resistance boundary.
 *
 * <p>The fixed two-piece bonus grants 30% DEF. Player elemental resistance and
 * incoming damage are not modeled, so the four-piece effect remains inert.</p>
 */
public class DefendersWill extends ArtifactSet {
    /** Constructs Defender's Will with a fresh stat container. */
    public DefendersWill() {
        this(new StatsContainer());
    }

    /**
     * Constructs Defender's Will while preserving supplied main and sub stats.
     *
     * @param stats non-null artifact main and sub stats
     */
    public DefendersWill(StatsContainer stats) {
        super("Defender's Will", Objects.requireNonNull(stats, "stats"));
        getStats().add(StatType.DEF_PERCENT, 0.30);
    }
}

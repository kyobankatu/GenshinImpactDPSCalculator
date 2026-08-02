package model.artifact;

import java.util.Objects;

import model.entity.ArtifactSet;
import model.stats.StatsContainer;
import model.type.StatType;

/**
 * Unfinished Reverie at the simulator's combat-proximity boundary.
 *
 * <p>The fixed two-piece bonus grants 18% ATK. The four-piece damage ramp
 * depends on leaving combat and the proximity of Burning opponents, neither
 * of which is represented by the single-target combat state.</p>
 */
public class UnfinishedReverie extends ArtifactSet {
    /** Constructs Unfinished Reverie with fresh stats. */
    public UnfinishedReverie() {
        this(new StatsContainer());
    }

    /**
     * Constructs Unfinished Reverie while preserving supplied stats.
     *
     * @param stats non-null artifact main and sub stats
     */
    public UnfinishedReverie(StatsContainer stats) {
        super("Unfinished Reverie", Objects.requireNonNull(stats, "stats"));
        getStats().add(StatType.ATK_PERCENT, 0.18);
    }
}

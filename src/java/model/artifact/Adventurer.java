package model.artifact;

import java.util.Objects;

import model.entity.ArtifactSet;
import model.stats.StatsContainer;
import model.type.StatType;

/**
 * Adventurer artifact set at the simulator's combat-only boundary.
 *
 * <p>The fixed two-piece bonus grants 1,000 flat HP. The four-piece effect is
 * triggered by opening exploration rewards and therefore remains inert in the
 * combat simulator.</p>
 */
public class Adventurer extends ArtifactSet {
    /** Constructs Adventurer with a fresh stat container. */
    public Adventurer() {
        this(new StatsContainer());
    }

    /**
     * Constructs Adventurer while preserving supplied main and sub stats.
     *
     * @param stats non-null artifact main and sub stats
     */
    public Adventurer(StatsContainer stats) {
        super("Adventurer", Objects.requireNonNull(stats, "stats"));
        getStats().add(StatType.HP_FLAT, 1000.0);
    }
}

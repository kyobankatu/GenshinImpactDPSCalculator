package model.artifact;

import java.util.Objects;

import model.entity.ArtifactSet;
import model.stats.StatsContainer;
import model.type.StatType;

/**
 * Lucky Dog artifact set at the simulator's combat-only boundary.
 *
 * <p>The fixed two-piece bonus grants 100 flat DEF. Mora pickup and player
 * healing are outside the simulator, so the four-piece effect is inert.</p>
 */
public class LuckyDog extends ArtifactSet {
    /** Constructs Lucky Dog with a fresh stat container. */
    public LuckyDog() {
        this(new StatsContainer());
    }

    /**
     * Constructs Lucky Dog while preserving supplied main and sub stats.
     *
     * @param stats non-null artifact main and sub stats
     */
    public LuckyDog(StatsContainer stats) {
        super("Lucky Dog", Objects.requireNonNull(stats, "stats"));
        getStats().add(StatType.DEF_FLAT, 100.0);
    }
}

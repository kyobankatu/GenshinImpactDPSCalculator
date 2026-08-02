package model.artifact;

import java.util.Objects;

import model.entity.ArtifactSet;
import model.stats.StatsContainer;
import model.type.StatType;

/** Celestial Gift artifact set with its fixed Energy Recharge bonus. */
public class CelestialGift extends ArtifactSet {
    /** Constructs Celestial Gift with a fresh stat container. */
    public CelestialGift() {
        this(new StatsContainer());
    }

    /**
     * Constructs Celestial Gift while preserving supplied main and sub stats.
     *
     * @param stats non-null artifact main and sub stats
     */
    public CelestialGift(StatsContainer stats) {
        super("Celestial Gift", Objects.requireNonNull(stats, "stats"));
        getStats().add(StatType.ENERGY_RECHARGE, 0.20);
    }
}

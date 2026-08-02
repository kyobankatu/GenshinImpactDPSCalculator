package model.artifact;

import java.util.Objects;

import model.entity.ArtifactSet;
import model.stats.StatsContainer;
import model.type.StatType;

/** Resolution of Sojourner artifact set with a Charged Attack CRIT bonus. */
public class ResolutionOfSojourner extends ArtifactSet {
    /** Constructs Resolution of Sojourner with a fresh stat container. */
    public ResolutionOfSojourner() {
        this(new StatsContainer());
    }

    /**
     * Constructs Resolution while preserving supplied main and sub stats.
     *
     * @param stats non-null artifact main and sub stats
     */
    public ResolutionOfSojourner(StatsContainer stats) {
        super("Resolution of Sojourner", Objects.requireNonNull(stats, "stats"));
        getStats().add(StatType.ATK_PERCENT, 0.18);
        getStats().add(StatType.CHARGED_ATTACK_CRIT_RATE, 0.30);
    }
}

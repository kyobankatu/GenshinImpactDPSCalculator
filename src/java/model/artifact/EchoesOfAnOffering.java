package model.artifact;

import java.util.Objects;

import model.entity.ArtifactSet;
import model.stats.StatsContainer;
import model.type.StatType;

/**
 * Echoes of an Offering at the simulator's probabilistic-hit boundary.
 *
 * <p>The fixed two-piece bonus grants 18% ATK. Valley Rite depends on
 * probability, ping, and linked multi-hit behavior that the deterministic
 * hit contract does not expose, so the four-piece proc remains inactive.</p>
 */
public class EchoesOfAnOffering extends ArtifactSet {
    /** Constructs Echoes of an Offering with fresh stats. */
    public EchoesOfAnOffering() {
        this(new StatsContainer());
    }

    /**
     * Constructs Echoes while preserving supplied main and sub stats.
     *
     * @param stats non-null artifact main and sub stats
     */
    public EchoesOfAnOffering(StatsContainer stats) {
        super("Echoes of an Offering", Objects.requireNonNull(stats, "stats"));
        getStats().add(StatType.ATK_PERCENT, 0.18);
    }
}

package model.artifact;

import java.util.Objects;

import model.entity.ArtifactSet;
import model.stats.StatsContainer;
import model.type.StatType;

/**
 * Ocean-Hued Clam at the simulator's no-player-healing boundary.
 *
 * <p>The fixed two-piece bonus grants 15% outgoing Healing Bonus. Healing and
 * overhealing events are not modeled, so Sea-Dyed Foam accumulation and
 * Physical damage remain inactive.</p>
 */
public class OceanHuedClam extends ArtifactSet {
    /** Constructs Ocean-Hued Clam with fresh stats. */
    public OceanHuedClam() {
        this(new StatsContainer());
    }

    /**
     * Constructs Ocean-Hued Clam while preserving supplied main and sub stats.
     *
     * @param stats non-null artifact main and sub stats
     */
    public OceanHuedClam(StatsContainer stats) {
        super("Ocean-Hued Clam", Objects.requireNonNull(stats, "stats"));
        getStats().add(StatType.HEALING_BONUS, 0.15);
    }
}

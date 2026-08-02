package model.artifact;

import java.util.Objects;

import model.entity.ArtifactSet;
import model.stats.StatsContainer;
import model.type.StatType;

/**
 * Bloodstained Chivalry at the simulator's enemy-defeat boundary.
 *
 * <p>The fixed two-piece bonus grants 25% Physical DMG. Enemy defeat, stamina,
 * and kill attribution are absent, so the four-piece Charged Attack window is
 * inactive.</p>
 */
public class BloodstainedChivalry extends ArtifactSet {
    /** Constructs Bloodstained Chivalry with a fresh stat container. */
    public BloodstainedChivalry() {
        this(new StatsContainer());
    }

    /**
     * Constructs Bloodstained while preserving supplied main and sub stats.
     *
     * @param stats non-null artifact main and sub stats
     */
    public BloodstainedChivalry(StatsContainer stats) {
        super("Bloodstained Chivalry", Objects.requireNonNull(stats, "stats"));
        getStats().add(StatType.PHYSICAL_DMG_BONUS, 0.25);
    }
}

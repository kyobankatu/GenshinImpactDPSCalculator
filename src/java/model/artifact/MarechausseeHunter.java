package model.artifact;

import java.util.Objects;

import model.entity.ArtifactSet;
import model.stats.StatsContainer;
import model.type.StatType;

/**
 * Marechaussee Hunter at the simulator's fixed-player-HP boundary.
 *
 * <p>The fixed two-piece bonus grants 15% Normal and Charged Attack DMG.
 * Player HP changes are not modeled, so the four-piece CRIT stacks remain
 * inactive.</p>
 */
public class MarechausseeHunter extends ArtifactSet {
    /** Constructs Marechaussee Hunter with a fresh stat container. */
    public MarechausseeHunter() {
        this(new StatsContainer());
    }

    /**
     * Constructs Marechaussee while preserving supplied main and sub stats.
     *
     * @param stats non-null artifact main and sub stats
     */
    public MarechausseeHunter(StatsContainer stats) {
        super("Marechaussee Hunter", Objects.requireNonNull(stats, "stats"));
        getStats().add(StatType.NORMAL_ATTACK_DMG_BONUS, 0.15);
        getStats().add(StatType.CHARGED_ATTACK_DMG_BONUS, 0.15);
    }
}

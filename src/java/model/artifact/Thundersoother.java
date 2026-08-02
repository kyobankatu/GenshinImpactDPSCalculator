package model.artifact;

import model.stats.StatsContainer;
import model.type.Element;

/**
 * Thundersoother with its live Electro-Aura outgoing damage condition.
 *
 * <p>The four-piece effect grants 35% all-DMG against an enemy currently
 * affected by Electro. The defensive two-piece Electro RES bonus is outside
 * the simulator's outgoing-damage stat model.</p>
 */
public class Thundersoother extends TargetAuraDamageArtifactSet {
    /** Constructs Thundersoother with fresh supplied-stat storage. */
    public Thundersoother() {
        this(new StatsContainer());
    }

    /**
     * Constructs Thundersoother while preserving supplied main and sub stats.
     *
     * @param stats non-null artifact main and sub stats
     */
    public Thundersoother(StatsContainer stats) {
        super("Thundersoother", stats, Element.ELECTRO, 0.35);
    }
}

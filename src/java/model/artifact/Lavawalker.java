package model.artifact;

import model.stats.StatsContainer;
import model.type.Element;

/**
 * Lavawalker with its live Pyro-Aura outgoing damage condition.
 *
 * <p>The four-piece effect grants 35% all-DMG against an enemy currently
 * affected by Pyro. The defensive two-piece Pyro RES bonus is outside the
 * simulator's outgoing-damage stat model.</p>
 */
public class Lavawalker extends TargetAuraDamageArtifactSet {
    /** Constructs Lavawalker with fresh supplied-stat storage. */
    public Lavawalker() {
        this(new StatsContainer());
    }

    /**
     * Constructs Lavawalker while preserving supplied main and sub stats.
     *
     * @param stats non-null artifact main and sub stats
     */
    public Lavawalker(StatsContainer stats) {
        super("Lavawalker", stats, Element.PYRO, 0.35);
    }
}

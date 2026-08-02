package model.artifact;

import java.util.Objects;

import model.entity.ArtifactSet;
import model.stats.StatsContainer;
import model.type.StatType;

/**
 * Gambler artifact set at the simulator's enemy-defeat boundary.
 *
 * <p>The fixed two-piece bonus grants 20% Elemental Skill DMG. Enemy defeat
 * and kill attribution are not modeled, so the four-piece Skill cooldown reset
 * remains inert instead of fabricating an activation.</p>
 */
public class Gambler extends ArtifactSet {
    /** Constructs Gambler with a fresh stat container. */
    public Gambler() {
        this(new StatsContainer());
    }

    /**
     * Constructs Gambler while preserving supplied main and sub stats.
     *
     * @param stats non-null artifact main and sub stats
     */
    public Gambler(StatsContainer stats) {
        super("Gambler", Objects.requireNonNull(stats, "stats"));
        getStats().add(StatType.SKILL_DMG_BONUS, 0.20);
    }
}

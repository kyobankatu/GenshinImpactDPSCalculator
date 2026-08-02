package model.artifact;

import java.util.Objects;

import model.entity.ArtifactSet;
import model.stats.StatsContainer;
import model.type.StatType;

/**
 * Vourukasha's Glow at the simulator's no-player-damage boundary.
 *
 * <p>The set grants 20% HP and the four-piece effect's unconditional 10%
 * Skill and Burst DMG. Incoming player damage is not modeled, so the five
 * conditional amplification stacks remain inactive.</p>
 */
public class VourukashasGlow extends ArtifactSet {
    /** Constructs Vourukasha's Glow with a fresh stat container. */
    public VourukashasGlow() {
        this(new StatsContainer());
    }

    /**
     * Constructs Vourukasha while preserving supplied main and sub stats.
     *
     * @param stats non-null artifact main and sub stats
     */
    public VourukashasGlow(StatsContainer stats) {
        super("Vourukasha's Glow", Objects.requireNonNull(stats, "stats"));
        getStats().add(StatType.HP_PERCENT, 0.20);
        getStats().add(StatType.SKILL_DMG_BONUS, 0.10);
        getStats().add(StatType.BURST_DMG_BONUS, 0.10);
    }
}

package model.artifact;

import java.util.Objects;

import model.entity.ArtifactSet;
import model.stats.StatsContainer;

/**
 * Obsidian Codex at the simulator's Nightsoul boundary.
 *
 * <p>Both set bonuses require Nightsoul Blessing or Nightsoul point
 * consumption, which the current simulator does not model. The set remains
 * canonical and loadable without fabricating unconditional outgoing stats.</p>
 */
public class ObsidianCodex extends ArtifactSet {
    /** Constructs Obsidian Codex with a fresh stat container. */
    public ObsidianCodex() {
        this(new StatsContainer());
    }

    /**
     * Constructs Obsidian Codex while preserving supplied artifact stats.
     *
     * @param stats non-null artifact main and sub stats
     */
    public ObsidianCodex(StatsContainer stats) {
        super("Obsidian Codex", Objects.requireNonNull(stats, "stats"));
    }
}

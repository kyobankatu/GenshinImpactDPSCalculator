package model.entity.state;

import model.stats.StatsContainer;

/**
 * Holds the latest captured stat snapshot for delayed damage resolution.
 *
 * <p>Genshin's snapshot mechanic freezes a character's stats at skill cast
 * time so that later off-field hits (e.g. Xingqiu's Rain Swords, Xiangling's
 * Pyronado) use those stored stats rather than the current ones. This class
 * is a thin holder that backs that behaviour at the {@code Character} level.
 */
public class SnapshotState {
    private StatsContainer snapshotStats;

    /**
     * Stores the supplied stat snapshot, overwriting any previous one.
     *
     * @param snapshotStats stats container captured at cast time
     */
    public void setSnapshot(StatsContainer snapshotStats) {
        this.snapshotStats = snapshotStats;
    }

    /**
     * Returns the stored snapshot, or an empty {@link StatsContainer} if no
     * snapshot has been captured yet (avoids {@code null} dereferences).
     *
     * @return stored snapshot or an empty container
     */
    public StatsContainer getSnapshot() {
        return snapshotStats != null ? snapshotStats : new StatsContainer();
    }
}

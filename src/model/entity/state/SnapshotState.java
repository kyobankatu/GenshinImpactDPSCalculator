package model.entity.state;

import model.stats.StatsContainer;

/**
 * Holds the latest captured stat snapshot for delayed damage resolution.
 */
public class SnapshotState {
    private StatsContainer snapshotStats;

    public void setSnapshot(StatsContainer snapshotStats) {
        this.snapshotStats = snapshotStats;
    }

    public StatsContainer getSnapshot() {
        return snapshotStats != null ? snapshotStats : new StatsContainer();
    }
}

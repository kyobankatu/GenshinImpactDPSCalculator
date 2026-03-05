package mechanics.analysis;

import model.type.StatType;
import java.util.List;
import java.util.Map;

/**
 * Immutable data record holding a complete snapshot of all party member stats
 * and active buff names at a specific point in simulation time.
 *
 * <p>Snapshots are produced by {@link StatsRecorder} and consumed by
 * {@link visualization.HtmlReportGenerator} to populate the interactive stats
 * table slider in the HTML report.
 */
// Record to hold stats at a specific point in time
public class StatsSnapshot {
    /** Simulation time (in seconds) at which this snapshot was taken. */
    public double time;
    // Map<CharacterName, Map<StatType, Double>>
    /** Effective stat values per character at {@link #time}, keyed by character name. */
    public Map<String, Map<StatType, Double>> characterStats;
    // Map<CharacterName, List<BuffName>>
    /** Names of active buffs per character at {@link #time}, keyed by character name. */
    public Map<String, List<String>> characterBuffs;

    /**
     * Constructs a new snapshot.
     *
     * @param time           simulation time in seconds
     * @param characterStats map from character name to their effective stat values
     * @param characterBuffs map from character name to their active buff name list
     */
    public StatsSnapshot(double time, Map<String, Map<StatType, Double>> characterStats,
            Map<String, List<String>> characterBuffs) {
        this.time = time;
        this.characterStats = characterStats;
        this.characterBuffs = characterBuffs;
    }
}

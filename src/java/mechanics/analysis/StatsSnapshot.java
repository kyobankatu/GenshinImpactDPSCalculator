package mechanics.analysis;

import model.type.CharacterId;
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
public class StatsSnapshot {
    /** Simulation time (in seconds) at which this snapshot was taken. */
    public double time;
    /** Effective stat values per character at {@link #time}, keyed by typed identity. */
    public Map<CharacterId, Map<StatType, Double>> characterStats;
    /** Display labels of active buffs per character at {@link #time}, keyed by typed identity. */
    public Map<CharacterId, List<String>> characterBuffs;
    /** Current energy percentage per character at {@link #time}, keyed by typed identity. */
    public Map<CharacterId, Double> characterEnergyPercent;

    /**
     * Constructs a new snapshot.
     *
     * @param time           simulation time in seconds
     * @param characterStats map from character id to their effective stat values
     * @param characterBuffs map from character id to their active buff display labels
     */
    public StatsSnapshot(double time, Map<CharacterId, Map<StatType, Double>> characterStats,
            Map<CharacterId, List<String>> characterBuffs) {
        this(time, characterStats, characterBuffs, new java.util.HashMap<>());
    }

    /**
     * Constructs a new snapshot.
     *
     * @param time                   simulation time in seconds
     * @param characterStats         map from character id to their effective stat values
     * @param characterBuffs         map from character id to their active buff display labels
     * @param characterEnergyPercent map from character id to current energy percentage
     */
    public StatsSnapshot(double time, Map<CharacterId, Map<StatType, Double>> characterStats,
            Map<CharacterId, List<String>> characterBuffs,
            Map<CharacterId, Double> characterEnergyPercent) {
        this.time = time;
        this.characterStats = characterStats;
        this.characterBuffs = characterBuffs;
        this.characterEnergyPercent = characterEnergyPercent;
    }
}

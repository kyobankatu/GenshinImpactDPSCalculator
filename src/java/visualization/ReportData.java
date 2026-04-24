package visualization;

import java.util.List;
import java.util.Map;

import model.type.StatType;

/**
 * Report-ready data assembled before HTML rendering.
 */
final class ReportData {
    final List<SimulationRecord> records;
    final List<ReportViewAdapter.ReportCharacterView> characters;
    final List<ReportViewAdapter.ReportStatsSnapshot> statsHistory;
    final List<ReportArtifactRollView> artifactRolls;
    final Map<String, Double> totalDamageByActor;
    final Map<String, List<String>> cumulativeDamageSeries;
    final List<String> chartNames;
    final String[] chartColors;
    final double totalDamage;
    final double rotationTime;
    final double dps;
    final double endTime;
    final boolean hasStatsHistory;

    ReportData(
            List<SimulationRecord> records,
            List<ReportViewAdapter.ReportCharacterView> characters,
            List<ReportViewAdapter.ReportStatsSnapshot> statsHistory,
            List<ReportArtifactRollView> artifactRolls,
            Map<String, Double> totalDamageByActor,
            Map<String, List<String>> cumulativeDamageSeries,
            List<String> chartNames,
            String[] chartColors,
            double totalDamage,
            double rotationTime,
            double dps,
            double endTime,
            boolean hasStatsHistory) {
        this.records = records;
        this.characters = characters;
        this.statsHistory = statsHistory;
        this.artifactRolls = artifactRolls;
        this.totalDamageByActor = totalDamageByActor;
        this.cumulativeDamageSeries = cumulativeDamageSeries;
        this.chartNames = chartNames;
        this.chartColors = chartColors;
        this.totalDamage = totalDamage;
        this.rotationTime = rotationTime;
        this.dps = dps;
        this.endTime = endTime;
        this.hasStatsHistory = hasStatsHistory;
    }

    static final class ReportArtifactRollView {
        final String displayName;
        final Map<StatType, Integer> rolls;

        ReportArtifactRollView(String displayName, Map<StatType, Integer> rolls) {
            this.displayName = displayName;
            this.rolls = rolls;
        }
    }
}

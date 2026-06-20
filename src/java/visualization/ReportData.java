package visualization;

import java.util.List;
import java.util.Map;

import model.type.Element;
import model.type.StatType;

/**
 * Report-ready data assembled before HTML rendering.
 */
final class ReportData {
    /** Raw simulation events used to render the timeline. */
    final List<SimulationRecord> records;
    /** Party member views (display name and DOM key). */
    final List<ReportViewAdapter.ReportCharacterView> characters;
    /** Optional history of party stat snapshots for the timeline slider. */
    final List<ReportViewAdapter.ReportStatsSnapshot> statsHistory;
    /** Artifact substat roll counts per character. */
    final List<ReportArtifactRollView> artifactRolls;
    /** Total damage attributed to each actor (display name keyed). */
    final Map<String, Double> totalDamageByActor;
    /** Cumulative damage series points (as JS object literals) per actor. */
    final Map<String, List<String>> cumulativeDamageSeries;
    /** Damage totals grouped by action display label. */
    final List<ReportMetricView> actionDamageTotals;
    /** Damage totals grouped by actor, then action display label. */
    final Map<String, List<ReportMetricView>> actionDamageTotalsByActor;
    /** Transformative reaction damage totals grouped by reaction display label. */
    final List<ReportMetricView> reactionDamageTotals;
    /** Direct-hit damage totals grouped by reaction labels when true reaction bonus is not separable. */
    final List<ReportMetricView> reactionLabeledDamageTotals;
    /** Rolling team DPS series as JS object literals. */
    final List<String> rollingDpsSeries;
    /** Enemy aura unit series per element as JS object literals. */
    final Map<Element, List<String>> auraSeries;
    /** Character energy percentage series as JS object literals, keyed by display name. */
    final Map<String, List<String>> energySeries;
    /** Buff uptime summaries grouped by character and buff display label. */
    final List<ReportBuffUptimeView> buffUptime;
    /** Ordered list of actor display names used for charts. */
    final List<String> chartNames;
    /** Chart colors aligned by index with {@link #chartNames}. */
    final String[] chartColors;
    /** Sum of damage across all records. */
    final double totalDamage;
    /** Effective rotation duration in seconds. */
    final double rotationTime;
    /** Damage-per-second derived from {@link #totalDamage} and {@link #rotationTime}. */
    final double dps;
    /** Timestamp of the final record in the rotation. */
    final double endTime;
    /** Whether {@link #statsHistory} contains usable data. */
    final boolean hasStatsHistory;

    /**
     * Creates a fully populated report data bundle.
     *
     * @param records                raw simulation records
     * @param characters             party views for the report
     * @param statsHistory           adapted stat snapshots (may be empty)
     * @param artifactRolls          per-character artifact roll views
     * @param totalDamageByActor     totals keyed by actor display name
     * @param cumulativeDamageSeries cumulative chart points keyed by actor
     * @param chartNames             ordered actor names for chart series
     * @param chartColors            chart colors aligned with {@code chartNames}
     * @param totalDamage            aggregate damage
     * @param rotationTime           effective rotation duration in seconds
     * @param dps                    damage per second
     * @param endTime                timestamp of the last record
     * @param hasStatsHistory        whether stats history should be rendered
     */
    ReportData(
            List<SimulationRecord> records,
            List<ReportViewAdapter.ReportCharacterView> characters,
            List<ReportViewAdapter.ReportStatsSnapshot> statsHistory,
            List<ReportArtifactRollView> artifactRolls,
            Map<String, Double> totalDamageByActor,
            Map<String, List<String>> cumulativeDamageSeries,
            List<ReportMetricView> actionDamageTotals,
            Map<String, List<ReportMetricView>> actionDamageTotalsByActor,
            List<ReportMetricView> reactionDamageTotals,
            List<ReportMetricView> reactionLabeledDamageTotals,
            List<String> rollingDpsSeries,
            Map<Element, List<String>> auraSeries,
            Map<String, List<String>> energySeries,
            List<ReportBuffUptimeView> buffUptime,
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
        this.actionDamageTotals = actionDamageTotals;
        this.actionDamageTotalsByActor = actionDamageTotalsByActor;
        this.reactionDamageTotals = reactionDamageTotals;
        this.reactionLabeledDamageTotals = reactionLabeledDamageTotals;
        this.rollingDpsSeries = rollingDpsSeries;
        this.auraSeries = auraSeries;
        this.energySeries = energySeries;
        this.buffUptime = buffUptime;
        this.chartNames = chartNames;
        this.chartColors = chartColors;
        this.totalDamage = totalDamage;
        this.rotationTime = rotationTime;
        this.dps = dps;
        this.endTime = endTime;
        this.hasStatsHistory = hasStatsHistory;
    }

    /**
     * Named numeric metric used by summary charts and tables.
     */
    static final class ReportMetricView {
        /** Display label. */
        final String label;
        /** Numeric value. */
        final double value;

        ReportMetricView(String label, double value) {
            this.label = label;
            this.value = value;
        }
    }

    /**
     * Report row describing one buff's observed uptime.
     */
    static final class ReportBuffUptimeView {
        /** Character display name. */
        final String characterName;
        /** Buff display label. */
        final String buffName;
        /** Uptime as percent of observed rotation duration. */
        final double uptimePercent;

        ReportBuffUptimeView(String characterName, String buffName, double uptimePercent) {
            this.characterName = characterName;
            this.buffName = buffName;
            this.uptimePercent = uptimePercent;
        }
    }

    /**
     * Report row describing a single character's artifact substat roll counts.
     */
    static final class ReportArtifactRollView {
        /** Character display name used as the column header. */
        final String displayName;
        /** Substat type to roll-count mapping. */
        final Map<StatType, Integer> rolls;

        /**
         * Creates a new artifact roll view.
         *
         * @param displayName character display name
         * @param rolls       substat roll counts
         */
        ReportArtifactRollView(String displayName, Map<StatType, Integer> rolls) {
            this.displayName = displayName;
            this.rolls = rolls;
        }
    }
}

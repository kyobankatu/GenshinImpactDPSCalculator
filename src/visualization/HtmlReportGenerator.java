package visualization;

import java.util.List;

import simulation.CombatSimulator;

/**
 * Public entry point for generating interactive HTML simulation reports.
 */
public class HtmlReportGenerator {

    /**
     * Generates the HTML report without stat history tracking.
     *
     * @param filePath destination file path for the generated HTML
     * @param records  list of simulation events/records to visualize
     * @param sim      the combat simulator that produced the records
     */
    public static void generate(String filePath, List<SimulationRecord> records, CombatSimulator sim) {
        generate(filePath, records, sim, null);
    }

    /**
     * Generates the complete HTML report, including the interactive stat tracker
     * if history snapshots are provided.
     *
     * @param filePath     destination file path for the generated HTML
     * @param records      list of simulation events/records to visualize in the
     *                     timeline
     * @param sim          the combat simulator that produced the records
     * @param statsHistory optional list of stat snapshots for the timeline slider;
     *                     if {@code null}, the interactive stat tracker is omitted
     */
    public static void generate(String filePath, List<SimulationRecord> records, CombatSimulator sim,
            List<mechanics.analysis.StatsSnapshot> statsHistory) {
        ReportData data = ReportDataBuilder.build(records, sim, statsHistory);
        ReportFileWriter.write(filePath, ReportHtmlRenderer.render(data));
    }
}

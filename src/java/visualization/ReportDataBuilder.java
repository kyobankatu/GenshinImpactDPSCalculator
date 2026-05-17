package visualization;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import model.entity.Character;
import simulation.CombatSimulator;

/**
 * Converts simulation records and runtime state into report-ready aggregates.
 */
final class ReportDataBuilder {
    private ReportDataBuilder() {
    }

    /**
     * Builds the {@link ReportData} bundle that drives HTML rendering.
     *
     * @param records      raw simulation records (may be {@code null})
     * @param sim          combat simulator that produced the records (may be {@code null})
     * @param statsHistory optional analysis stat snapshots; {@code null} disables
     *                     the timeline slider in the report
     * @return populated report data
     */
    static ReportData build(
            List<SimulationRecord> records,
            CombatSimulator sim,
            List<mechanics.analysis.StatsSnapshot> statsHistory) {
        List<SimulationRecord> safeRecords = records != null ? records : new ArrayList<>();
        List<ReportViewAdapter.ReportCharacterView> characters = ReportViewAdapter.partyCharacters(sim);
        List<ReportViewAdapter.ReportStatsSnapshot> reportStatsHistory = ReportViewAdapter.statsHistory(statsHistory);
        Map<String, Double> totalDamageByActor = totalDamageByActor(safeRecords);
        double endTime = safeRecords.isEmpty() ? 0 : safeRecords.get(safeRecords.size() - 1).time;
        Map<String, List<String>> cumulativeDamageSeries = cumulativeDamageSeries(safeRecords, totalDamageByActor,
                endTime);
        double totalDamage = safeRecords.stream().mapToDouble(r -> r.damage).sum();
        double rotationTime = (sim != null && sim.getRotationTime() > 0) ? sim.getRotationTime() : endTime;
        double dps = rotationTime > 0 ? totalDamage / rotationTime : 0;
        List<String> chartNames = new ArrayList<>(totalDamageByActor.keySet());

        return new ReportData(
                safeRecords,
                characters,
                reportStatsHistory,
                artifactRolls(sim),
                totalDamageByActor,
                cumulativeDamageSeries,
                chartNames,
                ElementColorPalette.colorsFor(chartNames, sim),
                totalDamage,
                rotationTime,
                dps,
                endTime,
                statsHistory != null);
    }

    /**
     * Aggregates total damage per actor across all records.
     *
     * @param records simulation records
     * @return total damage keyed by actor display name
     */
    private static Map<String, Double> totalDamageByActor(List<SimulationRecord> records) {
        Map<String, Double> totals = new HashMap<>();
        for (SimulationRecord record : records) {
            totals.put(record.actor, totals.getOrDefault(record.actor, 0.0) + record.damage);
        }
        return totals;
    }

    /**
     * Builds per-actor cumulative damage series as JS object literal strings.
     *
     * @param records            simulation records ordered by time
     * @param totalDamageByActor totals keyed by actor name (defines the series set)
     * @param endTime            timestamp appended as the final point of every series
     * @return series points keyed by actor display name
     */
    private static Map<String, List<String>> cumulativeDamageSeries(
            List<SimulationRecord> records,
            Map<String, Double> totalDamageByActor,
            double endTime) {
        Map<String, List<String>> series = new HashMap<>();
        Map<String, Double> currentSums = new HashMap<>();

        for (String actor : totalDamageByActor.keySet()) {
            series.put(actor, new ArrayList<>());
            currentSums.put(actor, 0.0);
            series.get(actor).add("{x: 0, y: 0}");
        }

        for (SimulationRecord record : records) {
            if (record.damage > 0) {
                double sum = currentSums.get(record.actor) + record.damage;
                currentSums.put(record.actor, sum);
                series.get(record.actor).add(String.format("{x: %.2f, y: %.0f}", record.time, sum));
            }
        }

        for (String actor : totalDamageByActor.keySet()) {
            series.get(actor).add(String.format("{x: %.2f, y: %.0f}", endTime, currentSums.get(actor)));
        }
        return series;
    }

    /**
     * Collects artifact substat roll views for every party member.
     *
     * @param sim combat simulator; may be {@code null}
     * @return per-character roll views, or an empty list when {@code sim} is null
     */
    private static List<ReportData.ReportArtifactRollView> artifactRolls(CombatSimulator sim) {
        List<ReportData.ReportArtifactRollView> artifactRolls = new ArrayList<>();
        if (sim == null) {
            return artifactRolls;
        }
        for (Character character : sim.getPartyMembers()) {
            artifactRolls.add(new ReportData.ReportArtifactRollView(character.getName(), character.getArtifactRolls()));
        }
        return artifactRolls;
    }
}

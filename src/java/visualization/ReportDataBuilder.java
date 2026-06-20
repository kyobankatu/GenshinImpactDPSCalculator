package visualization;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import model.entity.Character;
import model.type.Element;
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
        double chartEndTime = Math.max(rotationTime, endTime);

        return new ReportData(
                safeRecords,
                characters,
                reportStatsHistory,
                artifactRolls(sim),
                totalDamageByActor,
                cumulativeDamageSeries,
                metricTotalsByAction(safeRecords),
                metricTotalsByActorAction(safeRecords),
                transformativeReactionTotals(safeRecords),
                reactionLabeledDamageTotals(safeRecords),
                rollingDpsSeries(safeRecords, chartEndTime, 5.0, 0.5),
                auraSeries(safeRecords, chartEndTime),
                energySeries(reportStatsHistory, characters),
                buffUptime(reportStatsHistory, characters, chartEndTime),
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

    private static List<ReportData.ReportMetricView> metricTotalsByAction(List<SimulationRecord> records) {
        Map<String, Double> totals = new HashMap<>();
        for (SimulationRecord record : records) {
            if (record.damage <= 0.0) {
                continue;
            }
            totals.put(record.action, totals.getOrDefault(record.action, 0.0) + record.damage);
        }
        return sortedMetrics(totals, 16);
    }

    private static Map<String, List<ReportData.ReportMetricView>> metricTotalsByActorAction(
            List<SimulationRecord> records) {
        Map<String, Map<String, Double>> totalsByActor = new LinkedHashMap<>();
        for (SimulationRecord record : records) {
            if (record.damage <= 0.0) {
                continue;
            }
            totalsByActor.computeIfAbsent(record.actor, ignored -> new HashMap<>());
            Map<String, Double> totals = totalsByActor.get(record.actor);
            totals.put(record.action, totals.getOrDefault(record.action, 0.0) + record.damage);
        }

        Map<String, List<ReportData.ReportMetricView>> views = new LinkedHashMap<>();
        totalsByActor.entrySet().stream()
                .sorted((a, b) -> Double.compare(
                        b.getValue().values().stream().mapToDouble(Double::doubleValue).sum(),
                        a.getValue().values().stream().mapToDouble(Double::doubleValue).sum()))
                .forEach(entry -> views.put(entry.getKey(), sortedMetrics(entry.getValue(), 16)));
        return views;
    }

    private static List<ReportData.ReportMetricView> transformativeReactionTotals(List<SimulationRecord> records) {
        Map<String, Double> totals = new HashMap<>();
        for (SimulationRecord record : records) {
            if (!hasReactionLabel(record) || record.reactionDamage <= 0.0) {
                continue;
            }
            totals.put(record.reactionType, totals.getOrDefault(record.reactionType, 0.0) + record.reactionDamage);
        }
        return sortedMetrics(totals, 16);
    }

    private static List<ReportData.ReportMetricView> reactionLabeledDamageTotals(List<SimulationRecord> records) {
        Map<String, Double> totals = new HashMap<>();
        for (SimulationRecord record : records) {
            if (!hasReactionLabel(record) || record.reactionDamage > 0.0 || record.damage <= 0.0) {
                continue;
            }
            totals.put(record.reactionType, totals.getOrDefault(record.reactionType, 0.0) + record.damage);
        }
        return sortedMetrics(totals, 16);
    }

    private static boolean hasReactionLabel(SimulationRecord record) {
        return record.reactionType != null && !"None".equals(record.reactionType);
    }

    private static List<String> rollingDpsSeries(
            List<SimulationRecord> records,
            double endTime,
            double window,
            double interval) {
        List<String> series = new ArrayList<>();
        if (endTime <= 0.0) {
            series.add("{x: 0, y: 0}");
            return series;
        }

        for (double time = 0.0; time <= endTime + 1e-9; time += interval) {
            double start = Math.max(0.0, time - window);
            double sum = 0.0;
            for (SimulationRecord record : records) {
                if (record.time > time) {
                    break;
                }
                if (record.time >= start && record.damage > 0.0) {
                    sum += record.damage;
                }
            }
            double divisor = Math.max(0.1, time - start);
            series.add(String.format("{x: %.2f, y: %.0f}", time, time <= 1e-9 ? 0.0 : sum / divisor));
        }
        if (series.isEmpty() || !series.get(series.size() - 1).startsWith(String.format("{x: %.2f", endTime))) {
            double start = Math.max(0.0, endTime - window);
            double sum = 0.0;
            for (SimulationRecord record : records) {
                if (record.time >= start && record.time <= endTime && record.damage > 0.0) {
                    sum += record.damage;
                }
            }
            series.add(String.format("{x: %.2f, y: %.0f}", endTime, sum / Math.max(0.1, endTime - start)));
        }
        return downsample(series, 600);
    }

    private static Map<Element, List<String>> auraSeries(List<SimulationRecord> records, double endTime) {
        Map<Element, List<String>> series = new EnumMap<>(Element.class);
        for (Element element : Element.values()) {
            List<String> points = new ArrayList<>();
            boolean hasAura = false;
            points.add("{x: 0, y: 0}");
            for (SimulationRecord record : records) {
                double value = record.enemyAura != null ? record.enemyAura.getOrDefault(element, 0.0) : 0.0;
                if (value > 0.0) {
                    hasAura = true;
                }
                points.add(String.format("{x: %.2f, y: %.2f}", record.time, value));
            }
            if (hasAura) {
                if (endTime > 0.0) {
                    points.add(String.format("{x: %.2f, y: %.2f}", endTime,
                            lastAuraValue(records, element)));
                }
                series.put(element, downsample(points, 600));
            }
        }
        return series;
    }

    private static double lastAuraValue(List<SimulationRecord> records, Element element) {
        if (records.isEmpty()) {
            return 0.0;
        }
        SimulationRecord last = records.get(records.size() - 1);
        return last.enemyAura != null ? last.enemyAura.getOrDefault(element, 0.0) : 0.0;
    }

    private static Map<String, List<String>> energySeries(
            List<ReportViewAdapter.ReportStatsSnapshot> statsHistory,
            List<ReportViewAdapter.ReportCharacterView> characters) {
        Map<String, List<String>> series = new LinkedHashMap<>();
        for (ReportViewAdapter.ReportCharacterView character : characters) {
            List<String> points = new ArrayList<>();
            for (ReportViewAdapter.ReportStatsSnapshot snapshot : statsHistory) {
                ReportViewAdapter.ReportCharacterStats stats = snapshot.characters.get(character.domKey);
                double value = stats != null ? stats.energy : 0.0;
                points.add(String.format("{x: %.2f, y: %.1f}", snapshot.time, value));
            }
            series.put(character.displayName, downsample(points, 600));
        }
        return series;
    }

    private static List<ReportData.ReportBuffUptimeView> buffUptime(
            List<ReportViewAdapter.ReportStatsSnapshot> statsHistory,
            List<ReportViewAdapter.ReportCharacterView> characters,
            double rotationTime) {
        if (statsHistory.isEmpty() || rotationTime <= 0.0) {
            return new ArrayList<>();
        }

        Map<String, String> characterNamesByDomKey = new HashMap<>();
        for (ReportViewAdapter.ReportCharacterView character : characters) {
            characterNamesByDomKey.put(character.domKey, character.displayName);
        }

        Map<String, Double> uptimeSeconds = new HashMap<>();
        for (int i = 0; i < statsHistory.size(); i++) {
            ReportViewAdapter.ReportStatsSnapshot snapshot = statsHistory.get(i);
            double nextTime = i + 1 < statsHistory.size() ? statsHistory.get(i + 1).time : rotationTime;
            double duration = Math.max(0.0, nextTime - snapshot.time);
            if (duration <= 0.0) {
                continue;
            }
            for (Map.Entry<String, ReportViewAdapter.ReportCharacterStats> entry : snapshot.characters.entrySet()) {
                String characterName = characterNamesByDomKey.getOrDefault(entry.getKey(), entry.getKey());
                for (String buff : entry.getValue().buffs) {
                    String key = characterName + "\u0000" + buff;
                    uptimeSeconds.put(key, uptimeSeconds.getOrDefault(key, 0.0) + duration);
                }
            }
        }

        List<ReportData.ReportBuffUptimeView> views = new ArrayList<>();
        for (Map.Entry<String, Double> entry : uptimeSeconds.entrySet()) {
            String[] parts = entry.getKey().split("\u0000", 2);
            if (parts.length == 2) {
                views.add(new ReportData.ReportBuffUptimeView(parts[0], parts[1],
                        100.0 * entry.getValue() / rotationTime));
            }
        }
        views.sort(Comparator.comparingDouble((ReportData.ReportBuffUptimeView view) -> view.uptimePercent)
                .reversed());
        List<ReportData.ReportBuffUptimeView> variableViews = new ArrayList<>();
        for (ReportData.ReportBuffUptimeView view : views) {
            if (view.uptimePercent < 99.5 && view.uptimePercent > 0.0) {
                variableViews.add(view);
            }
        }
        List<ReportData.ReportBuffUptimeView> displayViews = variableViews.isEmpty() ? views : variableViews;
        return displayViews.size() > 16 ? new ArrayList<>(displayViews.subList(0, 16)) : displayViews;
    }

    private static List<ReportData.ReportMetricView> sortedMetrics(Map<String, Double> totals, int limit) {
        List<ReportData.ReportMetricView> metrics = new ArrayList<>();
        totals.entrySet().stream()
                .filter(entry -> entry.getValue() > 0.0)
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(limit)
                .forEach(entry -> metrics.add(new ReportData.ReportMetricView(entry.getKey(), entry.getValue())));
        return metrics;
    }

    private static List<String> downsample(List<String> points, int maxPoints) {
        if (points.size() <= maxPoints || maxPoints < 3) {
            return points;
        }
        List<String> sampled = new ArrayList<>();
        sampled.add(points.get(0));
        double step = (points.size() - 2) / (double) (maxPoints - 2);
        for (int i = 1; i < maxPoints - 1; i++) {
            int index = 1 + (int) Math.floor((i - 1) * step);
            sampled.add(points.get(Math.min(points.size() - 2, index)));
        }
        sampled.add(points.get(points.size() - 1));
        return sampled;
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

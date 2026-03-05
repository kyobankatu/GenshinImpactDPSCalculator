package visualization;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;
import java.util.stream.Collectors;
import model.type.Element;
import model.type.StatType;
import simulation.CombatSimulator;

/**
 * Generates an interactive HTML simulation report containing DPS metrics,
 * damage charts (pie and line), character stat snapshots over time,
 * artifact substat allocations, and a detailed combat timeline.
 *
 * <p>
 * The generated report uses Chart.js for data visualization and supports
 * a time-slider to view character stats and active buffs at any point during
 * the simulation.
 */
public class HtmlReportGenerator {

    /**
     * Generates the HTML report without stat history tracking.
     * This is a convenience method that delegates to
     * {@link #generate(String, List, CombatSimulator, List)} with a null history.
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
        StringBuilder sb = new StringBuilder();

        // --- Data Processing for Charts ---
        // 1. Total Damage per Character (Pie)
        Map<String, Double> totalDmgMap = new HashMap<>();
        for (SimulationRecord r : records) {
            String c = r.actor;
            totalDmgMap.put(c, totalDmgMap.getOrDefault(c, 0.0) + r.damage);
        }

        // 2. Cumulative Damage over Time (Line)
        // Structure: Map<CharacterName, List<{t, total_so_far}>>
        Map<String, List<String>> lineDataMap = new HashMap<>();
        Map<String, Double> currentSums = new HashMap<>();

        // Initialize
        for (String c : totalDmgMap.keySet()) {
            lineDataMap.put(c, new ArrayList<>());
            currentSums.put(c, 0.0);
            lineDataMap.get(c).add(String.format("{x: 0, y: 0}")); // Start at 0
        }

        // Iterate events
        for (SimulationRecord r : records) {
            if (r.damage > 0) {
                double s = currentSums.get(r.actor) + r.damage;
                currentSums.put(r.actor, s);
                lineDataMap.get(r.actor).add(String.format("{x: %.2f, y: %.0f}", r.time, s));
            }
        }
        // Add final point for all
        double endTime = records.isEmpty() ? 0 : records.get(records.size() - 1).time;
        for (String c : totalDmgMap.keySet()) {
            lineDataMap.get(c).add(String.format("{x: %.2f, y: %.0f}", endTime, currentSums.get(c)));
        }

        sb.append("<!DOCTYPE html>\n");
        sb.append("<html lang='en'>\n");
        sb.append("<head>\n");
        sb.append("<meta charset='UTF-8'>\n");
        sb.append("<title>Genshin Simulation Report</title>\n");
        // Chart.js CDN
        sb.append("<script src='https://cdn.jsdelivr.net/npm/chart.js'></script>\n");
        sb.append("<script src='https://cdn.jsdelivr.net/npm/chartjs-plugin-datalabels@2.0.0'></script>\n");
        // Chart.js Datalabels Plugin
        sb.append("<script src='https://cdn.jsdelivr.net/npm/chartjs-plugin-datalabels@2.0.0'></script>\n");
        sb.append("<style>\n");
        sb.append(
                "body { font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; background: #202020; color: #eee; padding: 20px; }\n");
        sb.append(
                ".container { max-width: 1200px; margin: 0 auto; display: flex; flex-direction: column; gap: 20px; }\n");
        sb.append(
                ".row { display: flex; gap: 20px; flex-wrap: wrap; }\n");
        sb.append(
                ".col { flex: 1; min-width: 400px; background: #333; border-radius: 8px; padding: 15px; box-shadow: 0 4px 6px rgba(0,0,0,0.3); }\n");
        sb.append(
                ".timeline { display: flex; flex-direction: column; gap: 5px; max-height: 800px; overflow-y: auto; }\n");
        sb.append(
                ".card { background: #444; border-radius: 4px; padding: 10px; display: flex; align-items: center; gap: 10px; font-size: 0.9em; position: relative; }\n");
        sb.append(".time { font-weight: bold; color: #aaa; width: 50px; }\n");
        sb.append(".actor { font-weight: bold; width: 100px; color: #fff; }\n");
        sb.append(".action { flex: 1; color: #ddd; }\n");
        sb.append(".damage { color: #ff6b6b; font-weight: bold; width: 70px; text-align: right; }\n");
        sb.append(
                ".reaction { font-size: 0.8em; padding: 2px 6px; border-radius: 4px; background: #555; color: #ccc; margin-left: 5px; }\n");
        sb.append(".aura-bar { position: absolute; bottom: 0; left: 0; right: 0; height: 3px; display: flex; }\n");
        sb.append(".aura-segment { height: 100%; }\n");
        sb.append(".card.swap { background: #3a3a2a; border-left: 3px solid #FFD700; opacity: 0.85; }\n");
        sb.append(".card.swap .action { color: #ddd; }\n");

        // Tooltip CSS
        sb.append(".damage { position: relative; cursor: help; }\n");
        sb.append(".damage:hover::after {\n");
        sb.append("  content: attr(title);\n");
        sb.append("  position: absolute;\n");
        sb.append("  bottom: 100%;\n");
        sb.append("  right: 0;\n");
        sb.append("  background: #222;\n");
        sb.append("  color: #fff;\n");
        sb.append("  padding: 8px;\n");
        sb.append("  border-radius: 4px;\n");
        sb.append("  white-space: nowrap;\n");
        sb.append("  z-index: 1000;\n");
        sb.append("  box-shadow: 0 4px 8px rgba(0,0,0,0.5);\n");
        sb.append("  border: 1px solid #555;\n");
        sb.append("  font-size: 0.85em;\n");
        sb.append("  pointer-events: none;\n");
        sb.append("}\n");

        // Stats Table
        sb.append("table.stats { width: 100%; border-collapse: collapse; font-size: 0.9em; }\n");
        sb.append("table.stats th, table.stats td { border: 1px solid #555; padding: 8px; text-align: center; }\n");
        sb.append("table.stats th { background: #444; }\n");

        // Buff Panel
        sb.append(".buff-panel { margin-top: 12px; }\n");
        sb.append(
                ".buff-row { display: flex; align-items: flex-start; gap: 8px; margin-bottom: 4px; font-size: 0.85em; }\n");
        sb.append(".buff-char { font-weight: bold; color: #ddd; min-width: 90px; flex-shrink: 0; }\n");
        sb.append(
                ".buff-tag { background: #555; color: #ccc; padding: 2px 7px; border-radius: 10px; margin: 2px; display: inline-block; }\n");

        // Element Colors
        sb.append(".PYRO { color: #FF9999; } .bg-PYRO { background-color: #FF5555; }\n");
        sb.append(".HYDRO { color: #80C0FF; } .bg-HYDRO { background-color: #3388FF; }\n");
        sb.append(".ELECTRO { color: #FFACFF; } .bg-ELECTRO { background-color: #AA55FF; }\n");
        sb.append(".CRYO { color: #99FFFF; } .bg-CRYO { background-color: #55FFFF; }\n");
        sb.append(".ANEMO { color: #80FFD7; } .bg-ANEMO { background-color: #33FF99; }\n");
        sb.append(".GEO { color: #FFE699; } .bg-GEO { background-color: #FFAA00; }\n");
        sb.append(".DENDRO { color: #A5C882; } .bg-DENDRO { background-color: #77EE44; }\n");
        sb.append(".PHYSICAL { color: #CCCCCC; } .bg-PHYSICAL { background-color: #CCCCCC; }\n"); // Fallback
        sb.append("</style>\n");
        sb.append("</head>\n");
        sb.append("<body>\n");

        sb.append("<div class='container'>\n");

        sb.append("<h1>Simulation Report</h1>\n");

        // --- Summary Section ---
        double totalSimDmg = records.stream().mapToDouble(r -> r.damage).sum();
        double rotationTime = (sim != null && sim.getRotationTime() > 0)
                ? sim.getRotationTime()
                : (records.isEmpty() ? 0 : records.get(records.size() - 1).time);
        double dps = rotationTime > 0 ? totalSimDmg / rotationTime : 0;

        sb.append("<div class='row'>\n");
        sb.append("<div class='col' style='background: #444; text-align: center;'>\n");
        sb.append("<h1>DPS: <span style='color: #ff6b6b;'>" + String.format("%,.0f", dps) + "</span></h1>\n");
        sb.append("<h3>Total Damage (DPR): <span style='color: #4db8ff;'>" + String.format("%,.0f", totalSimDmg)
                + "</span></h3>\n");
        sb.append("<p>Duration: " + String.format("%.1fs", rotationTime) + "</p>\n");
        sb.append("</div>\n");
        sb.append("</div>\n");

        // --- Row 1: Charts ---
        sb.append("<div class='row'>\n");

        // Pie Chart
        sb.append("<div class='col'>\n");
        sb.append("<h3>Damage Distribution</h3>\n");
        sb.append("<canvas id='dpsPie'></canvas>\n");
        sb.append("</div>\n");

        // Line Chart
        sb.append("<div class='col' style='flex: 2;'>\n");
        sb.append("<h3>Cumulative Damage Over Time</h3>\n");
        sb.append("<canvas id='dpsLine'></canvas>\n");
        sb.append("</div>\n");

        sb.append("</div>\n"); // End Row 1

        // --- Row 2: Character Stats (Interactive) ---
        sb.append("<div class='row'>\n");
        sb.append("<div class='col'>\n");
        sb.append("<div style='display:flex; justify-content:space-between; align-items:center;'>\n");
        sb.append("<h3>Character Stats Snapshot</h3>\n");
        sb.append("<div>\n");
        sb.append(
                "<label for='timeSlider' style='margin-right:10px;'>Time: <span id='timeDisplay' style='color:#ff6b6b; font-weight:bold;'>0.0s</span></label>\n");
        sb.append("<input type='range' id='timeSlider' min='0' max='" + String.format("%.1f", endTime)
                + "' step='0.1' value='0' style='width:200px;'>\n");
        sb.append("</div>\n");
        sb.append("</div>\n");

        sb.append("<table class='stats' id='statsTable'>\n");
        sb.append(
                "<thead><tr><th>Character</th><th>ATK</th><th>HP</th><th>DEF</th><th>CRIT Rate</th><th>CRIT DMG</th><th>ER</th><th>EM</th><th>Elem%</th></tr></thead>\n");
        sb.append("<tbody>\n");

        if (sim != null) {
            for (model.entity.Character c : sim.getPartyMembers()) {
                String safeName = c.getName().replaceAll("[^a-zA-Z0-9]", "");
                sb.append(String.format(
                        "<tr id='row-%s'><td>%s</td><td class='val-atk'>-</td><td class='val-hp'>-</td><td class='val-def'>-</td><td class='val-cr'>-</td><td class='val-cd'>-</td><td class='val-er'>-</td><td class='val-em'>-</td><td class='val-dmg'>-</td></tr>\n",
                        safeName, c.getName()));
            }
        }
        sb.append("</tbody></table>\n");
        sb.append(
                "<p style='font-size:0.8em; color:#aaa; margin-top:5px;'>* Use slider to view stats at different timestamps.</p>\n");

        if (statsHistory != null && sim != null) {
            sb.append("<div class='buff-panel'>\n");
            sb.append("<h4 style='margin-bottom:6px; color:#ccc;'>Active Buffs</h4>\n");
            sb.append("<div id='buffRows'>\n");
            for (model.entity.Character c : sim.getPartyMembers()) {
                String safeName = c.getName().replaceAll("[^a-zA-Z0-9]", "");
                sb.append(String.format(
                        "<div class='buff-row'><span class='buff-char'>%s</span><span class='buff-list' id='bl-%s'></span></div>\n",
                        c.getName(), safeName));
            }
            sb.append("</div>\n");
            sb.append("</div>\n");
        }

        sb.append("</div>\n");

        // --- Artifact Roll Breakdown Table ---
        sb.append("<div class='col'>\n");
        sb.append("<h3>Artifact Substat Rolls</h3>\n");
        sb.append("<table class='stats'>\n");
        // Header (Stats across top? Or Characters across top?)
        // Let's do Characters as Column Headers, Stats as Rows for better vertical
        // space
        // Get all unique StatTypes used
        Set<StatType> usedTypes = new HashSet<>();
        if (sim != null) {
            for (model.entity.Character c : sim.getPartyMembers()) {
                usedTypes.addAll(c.getArtifactRolls().keySet());
            }
        }
        // Ordered List of Stats (Standard Order)
        List<StatType> typeOrder = Arrays.asList(
                StatType.ENERGY_RECHARGE, StatType.CRIT_RATE, StatType.CRIT_DMG, StatType.ELEMENTAL_MASTERY,
                StatType.ATK_PERCENT, StatType.HP_PERCENT, StatType.DEF_PERCENT,
                StatType.ATK_FLAT, StatType.HP_FLAT, StatType.DEF_FLAT);

        sb.append("<thead><tr><th>Stat</th>");
        if (sim != null) {
            for (model.entity.Character c : sim.getPartyMembers()) {
                sb.append("<th>").append(c.getName()).append("</th>");
            }
        }
        sb.append("</tr></thead>\n");

        sb.append("<tbody>\n");
        for (StatType type : typeOrder) {
            sb.append("<tr>");
            sb.append("<td>").append(type.name()).append("</td>");

            if (sim != null) {
                for (model.entity.Character c : sim.getPartyMembers()) {
                    int rolls = c.getArtifactRolls().getOrDefault(type, 0);
                    if (rolls > 0) {
                        sb.append("<td>").append(rolls).append("</td>");
                    } else {
                        sb.append("<td style='color:#555;'>-</td>");
                    }
                }
            }
            sb.append("</tr>\n");
        }
        sb.append("</tbody></table>\n");
        sb.append("</div>\n");

        sb.append("</div>\n"); // End Row 2

        // --- Row 3: Timeline ---
        sb.append("<div class='row'>\n");
        sb.append("<div class='col'>\n");
        sb.append("<h3>Combat Timeline</h3>\n");
        sb.append("<div class='timeline'>\n");

        for (SimulationRecord r : records) {
            boolean isSwap = r.action != null && r.action.startsWith("Swap \u2192");
            sb.append(isSwap ? "<div class='card swap'>\n" : "<div class='card'>\n");
            sb.append(String.format("<div class='time'>%.1fs</div>\n", r.time));
            sb.append(String.format("<div class='actor'>%s</div>\n", r.actor));

            String reactionLabel = r.reactionType.equals("None") ? ""
                    : String.format("<span class='reaction'>%s</span>", r.reactionType);

            sb.append(String.format("<div class='action'>%s %s</div>\n", r.action, reactionLabel));

            if (r.damage > 0) {
                if (r.formula != null && !r.formula.isEmpty()) {
                    sb.append(String.format("<div class='damage' title='%s'>%,.0f</div>\n", r.formula, r.damage));
                } else {
                    sb.append(String.format("<div class='damage'>%,.0f</div>\n", r.damage));
                }
            } else {
                sb.append("<div class='damage'>-</div>\n");
            }

            // Aura Bar
            if (!r.enemyAura.isEmpty()) {
                sb.append("<div class='aura-bar'>\n");
                for (Map.Entry<Element, Double> entry : r.enemyAura.entrySet()) {
                    if (entry.getValue() > 0.0) {
                        String elem = entry.getKey().name();
                        sb.append(
                                String.format(
                                        "<div class='aura-segment bg-%s' style='flex: 1;' title='%s %.1fU'></div>\n",
                                        elem, elem, entry.getValue()));
                    }
                }
                sb.append("</div>\n");
            }

            sb.append("</div>\n"); // End Card
        }

        sb.append("</div>\n"); // End timeline container
        sb.append("</div>\n"); // End col
        sb.append("</div>\n"); // End Row 3

        sb.append("</div>\n"); // End Container

        // Javascript for Charts & Interactive Stats
        sb.append("<script>\n");

        // Prep Java Map keys to JS Arrays
        List<String> names = new ArrayList<>(totalDmgMap.keySet());
        String labelsJs = names.stream().map(n -> "'" + n + "'").collect(Collectors.joining(","));
        String pieDataJs = names.stream().map(n -> String.valueOf(totalDmgMap.get(n))).collect(Collectors.joining(","));

        // --- 1. Serialize Stats History to JSON ---
        sb.append("const statsHistory = [\n");
        if (statsHistory != null) {
            for (mechanics.analysis.StatsSnapshot snap : statsHistory) {
                sb.append(String.format("{ t: %.2f, chars: {\n", snap.time));

                for (Map.Entry<String, Map<StatType, Double>> entry : snap.characterStats.entrySet()) {
                    String cName = entry.getKey().replaceAll("[^a-zA-Z0-9]", "");
                    Map<StatType, Double> s = entry.getValue();

                    double atk = s.getOrDefault(StatType.BASE_ATK, 0.0)
                            * (1 + s.getOrDefault(StatType.ATK_PERCENT, 0.0)) + s.getOrDefault(StatType.ATK_FLAT, 0.0);
                    double hp = s.getOrDefault(StatType.BASE_HP, 0.0) * (1 + s.getOrDefault(StatType.HP_PERCENT, 0.0))
                            + s.getOrDefault(StatType.HP_FLAT, 0.0);
                    double def = s.getOrDefault(StatType.BASE_DEF, 0.0)
                            * (1 + s.getOrDefault(StatType.DEF_PERCENT, 0.0)) + s.getOrDefault(StatType.DEF_FLAT, 0.0);
                    double cr = s.getOrDefault(StatType.CRIT_RATE, 0.0) * 100;
                    double cd = s.getOrDefault(StatType.CRIT_DMG, 0.0) * 100;
                    double er = s.getOrDefault(StatType.ENERGY_RECHARGE, 0.0) * 100;
                    double em = s.getOrDefault(StatType.ELEMENTAL_MASTERY, 0.0);
                    double dmgBonus = Math.max(s.getOrDefault(StatType.PYRO_DMG_BONUS, 0.0),
                            Math.max(s.getOrDefault(StatType.HYDRO_DMG_BONUS, 0.0),
                                    Math.max(s.getOrDefault(StatType.ELECTRO_DMG_BONUS, 0.0),
                                            Math.max(s.getOrDefault(StatType.CRYO_DMG_BONUS, 0.0),
                                                    Math.max(s.getOrDefault(StatType.ANEMO_DMG_BONUS, 0.0),
                                                            Math.max(s.getOrDefault(StatType.GEO_DMG_BONUS, 0.0),
                                                                    s.getOrDefault(StatType.DENDRO_DMG_BONUS, 0.0)))))))
                            * 100;

                    List<String> buffNames = (snap.characterBuffs != null)
                            ? snap.characterBuffs.getOrDefault(entry.getKey(), Collections.emptyList())
                            : Collections.emptyList();
                    String buffsJs = buffNames.stream()
                            .map(b -> "'" + b.replace("\\", "\\\\").replace("'", "\\'") + "'")
                            .collect(Collectors.joining(","));
                    sb.append(String.format(
                            "'%s': { atk: %.0f, hp: %.0f, def: %.0f, cr: %.1f, cd: %.1f, er: %.1f, em: %.0f, dmg: %.1f, buffs: [%s] },\n",
                            cName, atk, hp, def, cr, cd, er, em, dmgBonus, buffsJs));
                }
                sb.append("}},\n");
            }
        }
        sb.append("];\n");

        sb.append("\n// --- 2. Slider Logic ---\n");
        sb.append("const timeSlider = document.getElementById('timeSlider');\n");
        sb.append("const timeDisplay = document.getElementById('timeDisplay');\n");
        sb.append("const statsTable = document.getElementById('statsTable');\n");

        sb.append("function updateStatsTable(time) {\n");
        sb.append("    // Binary search or Find closest snapshot\n");
        sb.append("    if (!statsHistory || statsHistory.length === 0) return;\n");
        sb.append("    let closest = statsHistory[0];\n");
        sb.append("    let minDiff = Math.abs(statsHistory[0].t - time);\n");
        sb.append("    for (let i = 1; i < statsHistory.length; i++) {\n");
        sb.append("        let diff = Math.abs(statsHistory[i].t - time);\n");
        sb.append("        if (diff < minDiff) {\n");
        sb.append("            minDiff = diff;\n");
        sb.append("            closest = statsHistory[i];\n");
        sb.append("        }\n");
        sb.append("    }\n");
        sb.append("    \n");
        sb.append("    // Update DOM\n");
        sb.append("    for (const [charId, stats] of Object.entries(closest.chars)) {\n");
        sb.append("        const row = document.getElementById('row-' + charId);\n");
        sb.append("        if (row) {\n");
        sb.append("            row.querySelector('.val-atk').innerText = stats.atk;\n");
        sb.append("            row.querySelector('.val-hp').innerText = stats.hp;\n");
        sb.append("            row.querySelector('.val-def').innerText = stats.def;\n");
        sb.append("            row.querySelector('.val-cr').innerText = stats.cr.toFixed(1) + '%';\n");
        sb.append("            row.querySelector('.val-cd').innerText = stats.cd.toFixed(1) + '%';\n");
        sb.append("            row.querySelector('.val-er').innerText = stats.er.toFixed(1) + '%';\n");
        sb.append("            row.querySelector('.val-em').innerText = stats.em;\n");
        sb.append("            row.querySelector('.val-dmg').innerText = stats.dmg.toFixed(1) + '%';\n");
        sb.append("        }\n");
        sb.append("        const bl = document.getElementById('bl-' + charId);\n");
        sb.append("        if (bl) {\n");
        sb.append("            const buffs = stats.buffs || [];\n");
        sb.append("            bl.innerHTML = buffs.length > 0\n");
        sb.append("                ? buffs.map(b => `<span class='buff-tag'>${b}</span>`).join('')\n");
        sb.append("                : '<span style=\"color:#666;\">-</span>';\n");
        sb.append("        }\n");
        sb.append("    }\n");
        sb.append("}\n");

        sb.append("timeSlider.addEventListener('input', (e) => {\n");
        sb.append("    const t = parseFloat(e.target.value);\n");
        sb.append("    timeDisplay.innerText = t.toFixed(1) + 's';\n");
        sb.append("    updateStatsTable(t);\n");
        sb.append("});\n");

        sb.append("// Initial Update\n");
        sb.append("updateStatsTable(0);\n");

        // Dynamic Element Colors with distinct variations
        Map<String, String> colorMap = new HashMap<>();
        Map<model.type.Element, Integer> elementCounts = new HashMap<>();

        if (sim != null) {
            for (String name : names) {
                model.entity.Character c = sim.getCharacter(name);
                if (c != null && c.getElement() != null) {
                    model.type.Element e = c.getElement();
                    int count = elementCounts.getOrDefault(e, 0);
                    colorMap.put(name, getElementColor(e, count));
                    elementCounts.put(e, count + 1);
                } else {
                    colorMap.put(name, "'#AAAAAA'");
                }
            }
        }

        // Colors Array (aligned with names list)
        String[] colors = names.stream()
                .map(n -> colorMap.getOrDefault(n, "'#AAAAAA'"))
                .toArray(String[]::new);

        // Pie Chart
        sb.append("Chart.register(ChartDataLabels);\n");
        sb.append("const ctxPie = document.getElementById('dpsPie').getContext('2d');\n");
        sb.append("new Chart(ctxPie, {\n");
        sb.append("    type: 'pie',\n");
        sb.append("    data: {\n");
        sb.append("        labels: [" + labelsJs + "],\n");
        sb.append("        datasets: [{\n");
        sb.append("            data: [" + pieDataJs + "],\n");
        sb.append("            backgroundColor: [" + String.join(",", colors) + "],\n");
        sb.append("            borderWidth: 0\n");
        sb.append("        }]\n");
        sb.append("    },\n");
        sb.append("    options: {\n");
        sb.append("        plugins: {\n");
        sb.append("             legend: { position: 'right', labels: { color: '#eee' } },\n");
        sb.append("             datalabels: {\n");
        sb.append("                 color: '#fff',\n");
        sb.append("                 formatter: (value, ctx) => {\n");
        sb.append("                     let sum = 0;\n");
        sb.append("                     let dataArr = ctx.chart.data.datasets[0].data;\n");
        sb.append("                     dataArr.map(data => { sum += data; });\n");
        sb.append("                     let percentage = (value*100 / sum).toFixed(1) + '%';\n");
        sb.append("                     return percentage;\n");
        sb.append("                 },\n");
        sb.append("                 font: { weight: 'bold', size: 12 }\n");
        sb.append("             }\n");
        sb.append("        }\n");
        sb.append("    }\n");
        sb.append("});\n");

        // Line Chart
        sb.append("const ctxLine = document.getElementById('dpsLine').getContext('2d');\n");
        sb.append("new Chart(ctxLine, {\n");
        sb.append("    type: 'line',\n");
        sb.append("    data: {\n");
        sb.append("        datasets: [\n");

        for (int i = 0; i < names.size(); i++) {
            String name = names.get(i);
            String dataPts = String.join(",", lineDataMap.get(name));
            String color = colors[i % colors.length];

            sb.append("        {\n");
            sb.append("            label: '" + name + "',\n");
            sb.append("            data: [" + dataPts + "],\n");
            sb.append("            borderColor: " + color + ",\n");
            sb.append("            backgroundColor: " + color + ",\n");
            sb.append("            fill: false,\n");
            sb.append("            tension: 0.1\n");
            sb.append("        },\n");
        }

        sb.append("        ]\n");
        sb.append("    },\n");
        sb.append("    options: { \n");
        sb.append("        scales: { \n");
        sb.append(
                "            x: { type: 'linear', position: 'bottom', title: {display: true, text: 'Time (s)', color: '#aaa'}, ticks: { color: '#aaa'} },\n");
        sb.append(
                "            y: { title: {display: true, text: 'Total Damage', color: '#aaa'}, ticks: { color: '#aaa'} }\n");
        sb.append("        },\n");
        sb.append("        plugins: {\n");
        sb.append("            legend: { labels: { color: '#eee' } },\n");
        sb.append("            datalabels: { display: false } \n"); // Disable datalabels for Line Chart
        sb.append("        }\n");
        sb.append("    }\n");
        sb.append("});\n");

        sb.append("</script>\n");
        sb.append("</body></html>");

        try (
                PrintWriter out = new PrintWriter(new FileWriter(filePath))) {
            out.write(sb.toString());
            System.out.println("Generated HTML Report: " + filePath);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Returns a CSS color code for the given element, cycling through variations
     * to ensure visual distinction between characters of the same element in
     * charts.
     *
     * @param e       the elemental type
     * @param variant an index (0-3) used to select a shade variation
     * @return a CSS color string (e.g., {@code "'#FF2222'"})
     */
    private static String getElementColor(Element e, int variant) {
        // Ensure variant is within 0-3 range (cycling if > 4, though max party is 4)
        int v = variant % 4;
        String color = "'#AAAAAA'";

        switch (e) {
            case PYRO:
                // High Contrast Red Spectrum
                if (v == 0)
                    color = "'#FF2222'"; // Vivid Red
                else if (v == 1)
                    color = "'#FFAAAA'"; // Pale Red
                else if (v == 2)
                    color = "'#990000'"; // Dark Red
                else
                    color = "'#FF5500'"; // Orange Red
                break;
            case HYDRO:
                // Bright Blue Spectrum
                if (v == 0)
                    color = "'#3388FF'"; // Azure Blue
                else if (v == 1)
                    color = "'#00CCFF'"; // Cyan/Sky Blue
                else if (v == 2)
                    color = "'#0055FF'"; // Royal Blue
                else
                    color = "'#66B2FF'"; // Lighter Azure
                break;
            case ELECTRO:
                // Bright Purple Spectrum
                if (v == 0)
                    color = "'#A066D3'"; // Amethyst
                else if (v == 1)
                    color = "'#D480FF'"; // Bright Lavender
                else if (v == 2)
                    color = "'#8800CC'"; // Deep Violet
                else
                    color = "'#CC99FF'"; // Pale Purple
                break;
            case CRYO:
                // Bright Ice/Teal Spectrum
                if (v == 0)
                    color = "'#99FFFF'"; // Cyan Ice
                else if (v == 1)
                    color = "'#00FFFF'"; // Bright Aqua
                else if (v == 2)
                    color = "'#66CCCC'"; // Teal
                else
                    color = "'#CCFFFF'"; // Very Pale Blue
                break;
            case ANEMO:
                // Bright Green/Teal Spectrum
                if (v == 0)
                    color = "'#33FF99'"; // Spring Green
                else if (v == 1)
                    color = "'#00FFCC'"; // Turquoise
                else if (v == 2)
                    color = "'#66FF66'"; // Lime Green
                else
                    color = "'#00CC99'"; // Sea Green
                break;
            case GEO:
                // Bright Yellow/Gold Spectrum
                if (v == 0)
                    color = "'#FFE699'"; // Light Gold
                else if (v == 1)
                    color = "'#FFD700'"; // Pure Gold
                else if (v == 2)
                    color = "'#FFAA00'"; // Orange Gold
                else
                    color = "'#E6C200'"; // Darker Gold
                break;
            case DENDRO:
                // Bright Green/Nature Spectrum
                if (v == 0)
                    color = "'#A5C882'"; // Soft Green
                else if (v == 1)
                    color = "'#77FF00'"; // Neon Chartreuse
                else if (v == 2)
                    color = "'#33CC33'"; // Kelly Green
                else
                    color = "'#66AA44'"; // Forest Green
                break;
            case PHYSICAL:
                color = "'#AAAAAA'";
                break;
            default:
                break;
        }
        return color;
    }
}

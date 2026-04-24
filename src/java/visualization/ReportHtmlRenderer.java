package visualization;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import model.type.Element;
import model.type.StatType;

/**
 * Renders report-ready data into a self-contained Chart.js HTML document.
 */
final class ReportHtmlRenderer {
    private static final List<StatType> ARTIFACT_STAT_ORDER = Arrays.asList(
            StatType.ENERGY_RECHARGE,
            StatType.CRIT_RATE,
            StatType.CRIT_DMG,
            StatType.ELEMENTAL_MASTERY,
            StatType.ATK_PERCENT,
            StatType.HP_PERCENT,
            StatType.DEF_PERCENT,
            StatType.ATK_FLAT,
            StatType.HP_FLAT,
            StatType.DEF_FLAT);

    private ReportHtmlRenderer() {
    }

    static String render(ReportData data) {
        StringBuilder sb = new StringBuilder();
        appendDocumentStart(sb);
        appendSummary(sb, data);
        appendCharts(sb);
        appendStatsAndArtifacts(sb, data);
        appendTimeline(sb, data);
        appendScript(sb, data);
        appendDocumentEnd(sb);
        return sb.toString();
    }

    private static void appendDocumentStart(StringBuilder sb) {
        sb.append("<!DOCTYPE html>\n");
        sb.append("<html lang='en'>\n");
        sb.append("<head>\n");
        sb.append("<meta charset='UTF-8'>\n");
        sb.append("<title>Genshin Simulation Report</title>\n");
        sb.append("<script src='https://cdn.jsdelivr.net/npm/chart.js'></script>\n");
        sb.append("<script src='https://cdn.jsdelivr.net/npm/chartjs-plugin-datalabels@2.0.0'></script>\n");
        appendStyles(sb);
        sb.append("</head>\n");
        sb.append("<body>\n");
        sb.append("<div class='container'>\n");
        sb.append("<h1>Simulation Report</h1>\n");
    }

    private static void appendStyles(StringBuilder sb) {
        sb.append("<style>\n");
        sb.append("body { font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; background: #202020; color: #eee; padding: 20px; }\n");
        sb.append(".container { max-width: 1200px; margin: 0 auto; display: flex; flex-direction: column; gap: 20px; }\n");
        sb.append(".row { display: flex; gap: 20px; flex-wrap: wrap; }\n");
        sb.append(".col { flex: 1; min-width: 400px; background: #333; border-radius: 8px; padding: 15px; box-shadow: 0 4px 6px rgba(0,0,0,0.3); }\n");
        sb.append(".timeline { display: flex; flex-direction: column; gap: 5px; max-height: 800px; overflow-y: auto; }\n");
        sb.append(".card { background: #444; border-radius: 4px; padding: 10px; display: flex; align-items: center; gap: 10px; font-size: 0.9em; position: relative; }\n");
        sb.append(".time { font-weight: bold; color: #aaa; width: 50px; }\n");
        sb.append(".actor { font-weight: bold; width: 100px; color: #fff; }\n");
        sb.append(".action { flex: 1; color: #ddd; }\n");
        sb.append(".damage { color: #ff6b6b; font-weight: bold; width: 70px; text-align: right; position: relative; cursor: help; }\n");
        sb.append(".reaction { font-size: 0.8em; padding: 2px 6px; border-radius: 4px; background: #555; color: #ccc; margin-left: 5px; }\n");
        sb.append(".aura-bar { position: absolute; bottom: 0; left: 0; right: 0; height: 3px; display: flex; }\n");
        sb.append(".aura-segment { height: 100%; }\n");
        sb.append(".card.swap { background: #3a3a2a; border-left: 3px solid #FFD700; opacity: 0.85; }\n");
        sb.append(".card.swap .action { color: #ddd; }\n");
        sb.append(".damage:hover::after { content: attr(title); position: absolute; bottom: 100%; right: 0; background: #222; color: #fff; padding: 8px; border-radius: 4px; white-space: nowrap; z-index: 1000; box-shadow: 0 4px 8px rgba(0,0,0,0.5); border: 1px solid #555; font-size: 0.85em; pointer-events: none; }\n");
        sb.append("table.stats { width: 100%; border-collapse: collapse; font-size: 0.9em; }\n");
        sb.append("table.stats th, table.stats td { border: 1px solid #555; padding: 8px; text-align: center; }\n");
        sb.append("table.stats th { background: #444; }\n");
        sb.append(".buff-panel { margin-top: 12px; }\n");
        sb.append(".buff-row { display: flex; align-items: flex-start; gap: 8px; margin-bottom: 4px; font-size: 0.85em; }\n");
        sb.append(".buff-char { font-weight: bold; color: #ddd; min-width: 90px; flex-shrink: 0; }\n");
        sb.append(".buff-tag { background: #555; color: #ccc; padding: 2px 7px; border-radius: 10px; margin: 2px; display: inline-block; }\n");
        sb.append(".PYRO { color: #FF9999; } .bg-PYRO { background-color: #FF5555; }\n");
        sb.append(".HYDRO { color: #80C0FF; } .bg-HYDRO { background-color: #3388FF; }\n");
        sb.append(".ELECTRO { color: #FFACFF; } .bg-ELECTRO { background-color: #AA55FF; }\n");
        sb.append(".CRYO { color: #99FFFF; } .bg-CRYO { background-color: #55FFFF; }\n");
        sb.append(".ANEMO { color: #80FFD7; } .bg-ANEMO { background-color: #33FF99; }\n");
        sb.append(".GEO { color: #FFE699; } .bg-GEO { background-color: #FFAA00; }\n");
        sb.append(".DENDRO { color: #A5C882; } .bg-DENDRO { background-color: #77EE44; }\n");
        sb.append(".PHYSICAL { color: #CCCCCC; } .bg-PHYSICAL { background-color: #CCCCCC; }\n");
        sb.append("</style>\n");
    }

    private static void appendSummary(StringBuilder sb, ReportData data) {
        sb.append("<div class='row'>\n");
        sb.append("<div class='col' style='background: #444; text-align: center;'>\n");
        sb.append("<h1>DPS: <span style='color: #ff6b6b;'>").append(String.format("%,.0f", data.dps))
                .append("</span></h1>\n");
        sb.append("<h3>Total Damage : <span style='color: #4db8ff;'>")
                .append(String.format("%,.0f", data.totalDamage)).append("</span></h3>\n");
        sb.append("<p>Duration: ").append(String.format("%.1fs", data.rotationTime)).append("</p>\n");
        sb.append("</div>\n");
        sb.append("</div>\n");
    }

    private static void appendCharts(StringBuilder sb) {
        sb.append("<div class='row'>\n");
        sb.append("<div class='col'>\n");
        sb.append("<h3>Damage Distribution</h3>\n");
        sb.append("<canvas id='dpsPie'></canvas>\n");
        sb.append("</div>\n");
        sb.append("<div class='col' style='flex: 2;'>\n");
        sb.append("<h3>Cumulative Damage Over Time</h3>\n");
        sb.append("<canvas id='dpsLine'></canvas>\n");
        sb.append("</div>\n");
        sb.append("</div>\n");
    }

    private static void appendStatsAndArtifacts(StringBuilder sb, ReportData data) {
        sb.append("<div class='row'>\n");
        appendStatsSection(sb, data);
        appendArtifactSection(sb, data);
        sb.append("</div>\n");
    }

    private static void appendStatsSection(StringBuilder sb, ReportData data) {
        sb.append("<div class='col'>\n");
        sb.append("<div style='display:flex; justify-content:space-between; align-items:center;'>\n");
        sb.append("<h3>Character Stats Snapshot</h3>\n");
        sb.append("<div>\n");
        sb.append("<label for='timeSlider' style='margin-right:10px;'>Time: <span id='timeDisplay' style='color:#ff6b6b; font-weight:bold;'>0.0s</span></label>\n");
        sb.append("<input type='range' id='timeSlider' min='0' max='").append(String.format("%.1f", data.endTime))
                .append("' step='0.1' value='0' style='width:200px;'>\n");
        sb.append("</div>\n");
        sb.append("</div>\n");
        sb.append("<table class='stats' id='statsTable'>\n");
        sb.append("<thead><tr><th>Character</th><th>ATK</th><th>HP</th><th>DEF</th><th>CRIT Rate</th><th>CRIT DMG</th><th>ER</th><th>EM</th><th>Elem%</th></tr></thead>\n");
        sb.append("<tbody>\n");
        for (ReportViewAdapter.ReportCharacterView character : data.characters) {
            sb.append(String.format(
                    "<tr id='row-%s'><td>%s</td><td class='val-atk'>-</td><td class='val-hp'>-</td><td class='val-def'>-</td><td class='val-cr'>-</td><td class='val-cd'>-</td><td class='val-er'>-</td><td class='val-em'>-</td><td class='val-dmg'>-</td></tr>\n",
                    character.domKey, character.displayName));
        }
        sb.append("</tbody></table>\n");
        sb.append("<p style='font-size:0.8em; color:#aaa; margin-top:5px;'>* Use slider to view stats at different timestamps.</p>\n");

        if (data.hasStatsHistory) {
            sb.append("<div class='buff-panel'>\n");
            sb.append("<h4 style='margin-bottom:6px; color:#ccc;'>Active Buffs</h4>\n");
            sb.append("<div id='buffRows'>\n");
            for (ReportViewAdapter.ReportCharacterView character : data.characters) {
                sb.append(String.format(
                        "<div class='buff-row'><span class='buff-char'>%s</span><span class='buff-list' id='bl-%s'></span></div>\n",
                        character.displayName, character.domKey));
            }
            sb.append("</div>\n");
            sb.append("</div>\n");
        }
        sb.append("</div>\n");
    }

    private static void appendArtifactSection(StringBuilder sb, ReportData data) {
        sb.append("<div class='col'>\n");
        sb.append("<h3>Artifact Substat Rolls</h3>\n");
        sb.append("<table class='stats'>\n");
        sb.append("<thead><tr><th>Stat</th>");
        for (ReportData.ReportArtifactRollView character : data.artifactRolls) {
            sb.append("<th>").append(character.displayName).append("</th>");
        }
        sb.append("</tr></thead>\n");
        sb.append("<tbody>\n");
        for (StatType type : ARTIFACT_STAT_ORDER) {
            sb.append("<tr>");
            sb.append("<td>").append(type.name()).append("</td>");
            for (ReportData.ReportArtifactRollView character : data.artifactRolls) {
                int rolls = character.rolls.getOrDefault(type, 0);
                if (rolls > 0) {
                    sb.append("<td>").append(rolls).append("</td>");
                } else {
                    sb.append("<td style='color:#555;'>-</td>");
                }
            }
            sb.append("</tr>\n");
        }
        sb.append("</tbody></table>\n");
        sb.append("</div>\n");
    }

    private static void appendTimeline(StringBuilder sb, ReportData data) {
        sb.append("<div class='row'>\n");
        sb.append("<div class='col'>\n");
        sb.append("<h3>Combat Timeline</h3>\n");
        sb.append("<div class='timeline'>\n");
        for (SimulationRecord record : data.records) {
            appendTimelineCard(sb, record);
        }
        sb.append("</div>\n");
        sb.append("</div>\n");
        sb.append("</div>\n");
    }

    private static void appendTimelineCard(StringBuilder sb, SimulationRecord record) {
        boolean isSwap = record.action != null && record.action.startsWith("Swap \u2192");
        sb.append(isSwap ? "<div class='card swap'>\n" : "<div class='card'>\n");
        sb.append(String.format("<div class='time'>%.1fs</div>\n", record.time));
        sb.append(String.format("<div class='actor'>%s</div>\n", record.actor));

        String reactionLabel = "None".equals(record.reactionType) ? ""
                : String.format("<span class='reaction'>%s</span>", record.reactionType);
        sb.append(String.format("<div class='action'>%s %s</div>\n", record.action, reactionLabel));

        if (record.damage > 0) {
            if (record.formula != null && !record.formula.isEmpty()) {
                sb.append(String.format("<div class='damage' title='%s'>%,.0f</div>\n", record.formula,
                        record.damage));
            } else {
                sb.append(String.format("<div class='damage'>%,.0f</div>\n", record.damage));
            }
        } else {
            sb.append("<div class='damage'>-</div>\n");
        }

        appendAuraBar(sb, record);
        sb.append("</div>\n");
    }

    private static void appendAuraBar(StringBuilder sb, SimulationRecord record) {
        if (record.enemyAura.isEmpty()) {
            return;
        }
        sb.append("<div class='aura-bar'>\n");
        for (Map.Entry<Element, Double> entry : record.enemyAura.entrySet()) {
            if (entry.getValue() > 0.0) {
                String elem = entry.getKey().name();
                sb.append(String.format(
                        "<div class='aura-segment bg-%s' style='flex: 1;' title='%s %.1fU'></div>\n",
                        elem, elem, entry.getValue()));
            }
        }
        sb.append("</div>\n");
    }

    private static void appendScript(StringBuilder sb, ReportData data) {
        sb.append("<script>\n");
        appendStatsHistoryScript(sb, data);
        appendChartScript(sb, data);
        sb.append("</script>\n");
    }

    private static void appendStatsHistoryScript(StringBuilder sb, ReportData data) {
        sb.append("const statsHistory = [\n");
        if (data.hasStatsHistory) {
            for (ReportViewAdapter.ReportStatsSnapshot snapshot : data.statsHistory) {
                sb.append(String.format("{ t: %.2f, chars: {\n", snapshot.time));
                for (Map.Entry<String, ReportViewAdapter.ReportCharacterStats> entry : snapshot.characters
                        .entrySet()) {
                    ReportViewAdapter.ReportCharacterStats stats = entry.getValue();
                    String buffsJs = stats.buffs.stream()
                            .map(buff -> "'" + buff.replace("\\", "\\\\").replace("'", "\\'") + "'")
                            .collect(Collectors.joining(","));
                    sb.append(String.format(
                            "'%s': { atk: %.0f, hp: %.0f, def: %.0f, cr: %.1f, cd: %.1f, er: %.1f, em: %.0f, dmg: %.1f, buffs: [%s] },\n",
                            entry.getKey(), stats.atk, stats.hp, stats.def, stats.cr, stats.cd, stats.er, stats.em,
                            stats.dmg, buffsJs));
                }
                sb.append("}},\n");
            }
        }
        sb.append("];\n");
        sb.append("const timeSlider = document.getElementById('timeSlider');\n");
        sb.append("const timeDisplay = document.getElementById('timeDisplay');\n");
        sb.append("function updateStatsTable(time) {\n");
        sb.append("    if (!statsHistory || statsHistory.length === 0) return;\n");
        sb.append("    let closest = statsHistory[0];\n");
        sb.append("    let minDiff = Math.abs(statsHistory[0].t - time);\n");
        sb.append("    for (let i = 1; i < statsHistory.length; i++) {\n");
        sb.append("        let diff = Math.abs(statsHistory[i].t - time);\n");
        sb.append("        if (diff < minDiff) { minDiff = diff; closest = statsHistory[i]; }\n");
        sb.append("    }\n");
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
        sb.append("            bl.innerHTML = buffs.length > 0 ? buffs.map(b => `<span class='buff-tag'>${b}</span>`).join('') : '<span style=\"color:#666;\">-</span>';\n");
        sb.append("        }\n");
        sb.append("    }\n");
        sb.append("}\n");
        sb.append("timeSlider.addEventListener('input', (e) => {\n");
        sb.append("    const t = parseFloat(e.target.value);\n");
        sb.append("    timeDisplay.innerText = t.toFixed(1) + 's';\n");
        sb.append("    updateStatsTable(t);\n");
        sb.append("});\n");
        sb.append("updateStatsTable(0);\n");
    }

    private static void appendChartScript(StringBuilder sb, ReportData data) {
        String labelsJs = data.chartNames.stream().map(name -> "'" + name + "'").collect(Collectors.joining(","));
        String pieDataJs = data.chartNames.stream()
                .map(name -> String.valueOf(data.totalDamageByActor.get(name)))
                .collect(Collectors.joining(","));

        sb.append("Chart.register(ChartDataLabels);\n");
        sb.append("const ctxPie = document.getElementById('dpsPie').getContext('2d');\n");
        sb.append("new Chart(ctxPie, {\n");
        sb.append("    type: 'pie', data: { labels: [").append(labelsJs).append("], datasets: [{ data: [")
                .append(pieDataJs).append("], backgroundColor: [").append(String.join(",", data.chartColors))
                .append("], borderWidth: 0 }] },\n");
        sb.append("    options: { plugins: { legend: { position: 'right', labels: { color: '#eee' } }, datalabels: { color: '#fff', formatter: (value, ctx) => { let sum = 0; let dataArr = ctx.chart.data.datasets[0].data; dataArr.map(data => { sum += data; }); return (value*100 / sum).toFixed(1) + '%'; }, font: { weight: 'bold', size: 12 } } } }\n");
        sb.append("});\n");
        sb.append("const ctxLine = document.getElementById('dpsLine').getContext('2d');\n");
        sb.append("new Chart(ctxLine, {\n");
        sb.append("    type: 'line', data: { datasets: [\n");

        for (int i = 0; i < data.chartNames.size(); i++) {
            String name = data.chartNames.get(i);
            String dataPoints = String.join(",", data.cumulativeDamageSeries.get(name));
            String color = data.chartColors[i % data.chartColors.length];
            sb.append("        { label: '").append(name).append("', data: [").append(dataPoints)
                    .append("], borderColor: ").append(color).append(", backgroundColor: ").append(color)
                    .append(", fill: false, tension: 0.1 },\n");
        }

        sb.append("    ] },\n");
        sb.append("    options: { scales: { x: { type: 'linear', position: 'bottom', title: {display: true, text: 'Time (s)', color: '#aaa'}, ticks: { color: '#aaa'} }, y: { title: {display: true, text: 'Total Damage', color: '#aaa'}, ticks: { color: '#aaa'} } }, plugins: { legend: { labels: { color: '#eee' } }, datalabels: { display: false } } }\n");
        sb.append("});\n");
    }

    private static void appendDocumentEnd(StringBuilder sb) {
        sb.append("</div>\n");
        sb.append("</body></html>");
    }
}

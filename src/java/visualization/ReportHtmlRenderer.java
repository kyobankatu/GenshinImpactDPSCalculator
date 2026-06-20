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

    /**
     * Renders a self-contained HTML document for the given report data.
     *
     * @param data fully populated report data
     * @return the complete HTML document as a string
     */
    static String render(ReportData data) {
        StringBuilder sb = new StringBuilder();
        appendDocumentStart(sb);
        appendSummary(sb, data);
        appendCharts(sb, data);
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
        sb.append("<meta name='viewport' content='width=device-width, initial-scale=1.0'>\n");
        sb.append("<title>Genshin Simulation Report</title>\n");
        sb.append("<script src='https://cdn.jsdelivr.net/npm/chart.js'></script>\n");
        sb.append("<script src='https://cdn.jsdelivr.net/npm/chartjs-plugin-datalabels@2.0.0'></script>\n");
        appendStyles(sb);
        sb.append("</head>\n");
        sb.append("<body>\n");
        sb.append("<div class='container'>\n");
        sb.append("<header class='page-header'>\n");
        sb.append("<h1>Simulation Report</h1>\n");
        sb.append("<p>Damage, reaction, aura, energy, buff, and timeline analysis.</p>\n");
        sb.append("</header>\n");
    }

    private static void appendStyles(StringBuilder sb) {
        sb.append("<style>\n");
        sb.append(":root { color-scheme: dark; --bg:#17191c; --panel:#24282e; --panel2:#2e333a; --line:#3d4652; --text:#eef2f6; --muted:#9aa6b2; --accent:#64b5f6; --danger:#ff7b7b; --good:#7bd88f; }\n");
        sb.append("* { box-sizing: border-box; }\n");
        sb.append("body { font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; background: var(--bg); color: var(--text); margin: 0; padding: 24px; }\n");
        sb.append(".container { max-width: 1440px; margin: 0 auto; display: flex; flex-direction: column; gap: 18px; }\n");
        sb.append(".page-header h1 { margin: 0 0 4px; font-size: 1.9rem; }\n");
        sb.append(".page-header p { margin: 0; color: var(--muted); }\n");
        sb.append("h2, h3, h4 { margin: 0 0 10px; letter-spacing: 0; }\n");
        sb.append(".row { display: grid; grid-template-columns: repeat(auto-fit, minmax(360px, 1fr)); gap: 16px; align-items: stretch; }\n");
        sb.append(".row.wide { grid-template-columns: minmax(360px, 1.1fr) minmax(460px, 1.9fr); }\n");
        sb.append(".col { min-width: 0; background: var(--panel); border: 1px solid var(--line); border-radius: 8px; padding: 16px; box-shadow: 0 10px 28px rgba(0,0,0,0.22); }\n");
        sb.append(".summary-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(160px, 1fr)); gap: 12px; }\n");
        sb.append(".metric { background: var(--panel2); border: 1px solid var(--line); border-radius: 8px; padding: 12px; min-width: 0; }\n");
        sb.append(".metric-label { color: var(--muted); font-size: 0.78rem; text-transform: uppercase; letter-spacing: 0.04em; }\n");
        sb.append(".metric-value { margin-top: 4px; font-size: 1.35rem; font-weight: 700; color: var(--text); overflow-wrap: anywhere; }\n");
        sb.append(".metric-value.accent { color: var(--accent); } .metric-value.danger { color: var(--danger); }\n");
        sb.append(".chart-box { min-height: 0; }\n");
        sb.append(".chart-box canvas { display: block; width: 100% !important; height: 260px !important; max-height: 260px; }\n");
        sb.append(".chart-box.tall canvas { height: 360px !important; max-height: 360px; }\n");
        sb.append(".chart-note { color: var(--muted); font-size: 0.82rem; margin: 0 0 8px; }\n");
        sb.append(".timeline-toolbar { display: grid; grid-template-columns: repeat(auto-fit, minmax(150px, 1fr)); gap: 10px; margin-bottom: 12px; align-items: end; }\n");
        sb.append(".control label { display: block; color: var(--muted); font-size: 0.78rem; margin-bottom: 4px; }\n");
        sb.append(".control input, .control select { width: 100%; background: #1d2228; border: 1px solid var(--line); color: var(--text); border-radius: 6px; padding: 7px 8px; }\n");
        sb.append(".control.check { display: flex; align-items: center; gap: 8px; padding-bottom: 7px; color: var(--muted); }\n");
        sb.append(".control.check input { width: auto; }\n");
        sb.append(".filter-count { color: var(--muted); font-size: 0.86rem; margin-bottom: 8px; }\n");
        sb.append(".timeline { display: flex; flex-direction: column; gap: 4px; max-height: 850px; overflow-y: auto; padding-right: 4px; }\n");
        sb.append(".card { background: var(--panel2); border: 1px solid transparent; border-radius: 6px; padding: 8px 10px; display: grid; grid-template-columns: 58px minmax(88px, 120px) minmax(180px, 1fr) 96px; align-items: center; gap: 10px; font-size: 0.88em; position: relative; }\n");
        sb.append(".card:hover { border-color: var(--line); background: #343a42; }\n");
        sb.append(".card.hidden { display: none; }\n");
        sb.append(".time { font-weight: 700; color: var(--muted); }\n");
        sb.append(".actor { font-weight: 700; color: #fff; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }\n");
        sb.append(".action { color: #dbe1e8; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }\n");
        sb.append(".damage { color: var(--danger); font-weight: 700; text-align: right; font-variant-numeric: tabular-nums; }\n");
        sb.append(".reaction { font-size: 0.75em; padding: 2px 6px; border-radius: 999px; background: #41505f; color: #d8e7f7; margin-left: 5px; white-space: nowrap; }\n");
        sb.append(".formula-details { grid-column: 3 / 5; color: #d8e0e8; }\n");
        sb.append(".formula-details summary { cursor: pointer; color: var(--accent); font-size: 0.82rem; }\n");
        sb.append(".formula-details pre { white-space: pre-wrap; overflow-wrap: anywhere; background: #15191e; border: 1px solid var(--line); border-radius: 6px; padding: 10px; margin: 6px 0 0; }\n");
        sb.append(".aura-bar { position: absolute; bottom: 0; left: 0; right: 0; height: 3px; display: flex; }\n");
        sb.append(".aura-segment { height: 100%; }\n");
        sb.append(".card.swap { background: #393522; border-left: 3px solid #FFD54F; opacity: 0.9; }\n");
        sb.append(".card.swap .action { color: #ddd; }\n");
        sb.append("table.stats { width: 100%; border-collapse: collapse; font-size: 0.86em; }\n");
        sb.append("table.stats th, table.stats td { border-bottom: 1px solid var(--line); padding: 8px; text-align: right; font-variant-numeric: tabular-nums; }\n");
        sb.append("table.stats th:first-child, table.stats td:first-child { text-align: left; }\n");
        sb.append("table.artifact-rolls th, table.artifact-rolls td { text-align: center; }\n");
        sb.append("table.artifact-rolls th:first-child, table.artifact-rolls td:first-child { text-align: left; }\n");
        sb.append("table.stats th { color: var(--muted); font-weight: 700; background: #29303a; position: sticky; top: 0; }\n");
        sb.append(".buff-panel { margin-top: 12px; }\n");
        sb.append(".buff-row { display: flex; align-items: flex-start; gap: 8px; margin-bottom: 4px; font-size: 0.85em; }\n");
        sb.append(".buff-char { font-weight: bold; color: #ddd; min-width: 90px; flex-shrink: 0; }\n");
        sb.append(".buff-tag { background: #3c4652; color: #d9e2ec; padding: 2px 7px; border-radius: 10px; margin: 2px; display: inline-block; }\n");
        sb.append(".PYRO { color: #FF9999; } .bg-PYRO { background-color: #FF5555; }\n");
        sb.append(".HYDRO { color: #80C0FF; } .bg-HYDRO { background-color: #3388FF; }\n");
        sb.append(".ELECTRO { color: #FFACFF; } .bg-ELECTRO { background-color: #AA55FF; }\n");
        sb.append(".CRYO { color: #99FFFF; } .bg-CRYO { background-color: #55FFFF; }\n");
        sb.append(".ANEMO { color: #80FFD7; } .bg-ANEMO { background-color: #33FF99; }\n");
        sb.append(".GEO { color: #FFE699; } .bg-GEO { background-color: #FFAA00; }\n");
        sb.append(".DENDRO { color: #A5C882; } .bg-DENDRO { background-color: #77EE44; }\n");
        sb.append(".PHYSICAL { color: #CCCCCC; } .bg-PHYSICAL { background-color: #CCCCCC; }\n");
        sb.append("@media (max-width: 760px) { body { padding: 12px; } .row.wide, .row { grid-template-columns: 1fr; } .card { grid-template-columns: 52px 92px 1fr; } .damage { grid-column: 3; } .formula-details { grid-column: 1 / 4; } }\n");
        sb.append("</style>\n");
    }

    private static void appendSummary(StringBuilder sb, ReportData data) {
        String topActor = data.chartNames.isEmpty() ? "-"
                : data.chartNames.stream()
                        .max((left, right) -> Double.compare(
                                data.totalDamageByActor.getOrDefault(left, 0.0),
                                data.totalDamageByActor.getOrDefault(right, 0.0)))
                        .orElse("-");
        String topReaction = !data.reactionDamageTotals.isEmpty()
                ? data.reactionDamageTotals.get(0).label
                : (!data.reactionLabeledDamageTotals.isEmpty() ? data.reactionLabeledDamageTotals.get(0).label : "-");
        double maxHit = data.records.stream().mapToDouble(record -> record.damage).max().orElse(0.0);

        sb.append("<section class='summary-grid'>\n");
        appendMetricCard(sb, "DPS", String.format("%,.0f", data.dps), "danger");
        appendMetricCard(sb, "Total Damage", String.format("%,.0f", data.totalDamage), "accent");
        appendMetricCard(sb, "Duration", String.format("%.1fs", data.rotationTime), "");
        appendMetricCard(sb, "Top Dealer", topActor, "");
        appendMetricCard(sb, "Top Reaction", topReaction, "");
        appendMetricCard(sb, "Max Hit", String.format("%,.0f", maxHit), "");
        sb.append("</section>\n");
    }

    private static void appendMetricCard(StringBuilder sb, String label, String value, String valueClass) {
        sb.append("<div class='metric'>\n");
        sb.append("<div class='metric-label'>").append(escapeHtml(label)).append("</div>\n");
        sb.append("<div class='metric-value");
        if (valueClass != null && !valueClass.isEmpty()) {
            sb.append(" ").append(valueClass);
        }
        sb.append("'>").append(escapeHtml(value)).append("</div>\n");
        sb.append("</div>\n");
    }

    private static void appendCharts(StringBuilder sb, ReportData data) {
        sb.append("<div class='row wide'>\n");
        appendChartPanel(sb, "Damage Distribution", "dpsPie", null, false);
        appendChartPanel(sb, "Character Cumulative Damage", "dpsLine", null, false);
        sb.append("</div>\n");

        sb.append("<div class='row'>\n");
        appendChartPanel(sb, "Reaction Damage", "reactionPie",
                "Transformative reaction damage only. Additive/direct labels are separated below when exact bonus damage is unavailable.",
                false);
        appendChartPanel(sb, "Action Damage", "actionBar", "Top action labels by recorded damage.", false);
        sb.append("</div>\n");

        sb.append("<div class='row'>\n");
        appendChartPanel(sb, "Rolling DPS", "rollingDps", "0.5s samples over the previous 5.0s window.", false);
        appendChartPanel(sb, "Aura Timeline", "auraTimeline", "Step-style enemy aura units by element.", false);
        sb.append("</div>\n");

        sb.append("<div class='row'>\n");
        appendChartPanel(sb, "Energy Timeline", "energyTimeline", "Current energy as percent of burst cost.", false);
        appendChartPanel(sb, "Buff Uptime", "buffUptime",
                "Variable sampled active-buff labels are prioritized; always-on labels are hidden when variable entries exist.",
                true);
        sb.append("</div>\n");

        if (!data.reactionLabeledDamageTotals.isEmpty()) {
            sb.append("<div class='row'>\n");
            appendChartPanel(sb, "Reaction-labeled Direct Damage", "reactionLabeledBar",
                    "Full hit damage for reaction-labeled events where the additive bonus is not separately available.",
                    false);
            sb.append("</div>\n");
        }
    }

    private static void appendChartPanel(StringBuilder sb, String title, String canvasId, String note, boolean tall) {
        sb.append("<div class='col chart-box");
        if (tall) {
            sb.append(" tall");
        }
        sb.append("'>\n");
        sb.append("<h3>").append(escapeHtml(title)).append("</h3>\n");
        if (note != null && !note.isEmpty()) {
            sb.append("<p class='chart-note'>").append(escapeHtml(note)).append("</p>\n");
        }
        if ("actionBar".equals(canvasId)) {
            sb.append("<div class='control' style='max-width:220px; margin-bottom:10px;'>\n");
            sb.append("<label for='actionActorFilter'>Actor</label>\n");
            sb.append("<select id='actionActorFilter'><option value='__all__'>All actors</option></select>\n");
            sb.append("</div>\n");
        }
        sb.append("<canvas id='").append(escapeAttr(canvasId)).append("'></canvas>\n");
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
        sb.append("<div style='display:flex; justify-content:space-between; align-items:center; gap:12px; flex-wrap:wrap;'>\n");
        sb.append("<h3>Character Stats Snapshot</h3>\n");
        sb.append("<div>\n");
        sb.append("<label for='timeSlider' style='margin-right:10px;'>Time: <span id='timeDisplay' style='color:#ff7b7b; font-weight:bold;'>0.0s</span></label>\n");
        sb.append("<input type='range' id='timeSlider' min='0' max='").append(String.format("%.1f", data.endTime))
                .append("' step='0.1' value='0' style='width:200px;'>\n");
        sb.append("</div>\n");
        sb.append("</div>\n");
        sb.append("<table class='stats' id='statsTable'>\n");
        sb.append("<thead><tr><th>Character</th><th>ATK</th><th>HP</th><th>DEF</th><th>CRIT Rate</th><th>CRIT DMG</th><th>ER</th><th>Energy</th><th>EM</th><th>Elem%</th></tr></thead>\n");
        sb.append("<tbody>\n");
        for (ReportViewAdapter.ReportCharacterView character : data.characters) {
            sb.append("<tr id='row-").append(escapeAttr(character.domKey)).append("'><td>")
                    .append(escapeHtml(character.displayName))
                    .append("</td><td class='val-atk'>-</td><td class='val-hp'>-</td><td class='val-def'>-</td><td class='val-cr'>-</td><td class='val-cd'>-</td><td class='val-er'>-</td><td class='val-energy'>-</td><td class='val-em'>-</td><td class='val-dmg'>-</td></tr>\n");
        }
        sb.append("</tbody></table>\n");
        sb.append("<p class='chart-note'>Use slider to inspect sampled stats and active buffs.</p>\n");

        if (data.hasStatsHistory) {
            sb.append("<div class='buff-panel'>\n");
            sb.append("<h4>Active Buffs</h4>\n");
            sb.append("<div id='buffRows'>\n");
            for (ReportViewAdapter.ReportCharacterView character : data.characters) {
                sb.append("<div class='buff-row'><span class='buff-char'>")
                        .append(escapeHtml(character.displayName))
                        .append("</span><span class='buff-list' id='bl-")
                        .append(escapeAttr(character.domKey))
                        .append("'></span></div>\n");
            }
            sb.append("</div>\n");
            sb.append("</div>\n");
        }
        sb.append("</div>\n");
    }

    private static void appendArtifactSection(StringBuilder sb, ReportData data) {
        sb.append("<div class='col'>\n");
        sb.append("<h3>Artifact Substat Rolls</h3>\n");
        sb.append("<table class='stats artifact-rolls'>\n");
        sb.append("<thead><tr><th>Stat</th>");
        for (ReportData.ReportArtifactRollView character : data.artifactRolls) {
            sb.append("<th>").append(escapeHtml(character.displayName)).append("</th>");
        }
        sb.append("</tr></thead>\n");
        sb.append("<tbody>\n");
        for (StatType type : ARTIFACT_STAT_ORDER) {
            sb.append("<tr><td>").append(escapeHtml(type.name())).append("</td>");
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
        appendTimelineFilters(sb, data);
        sb.append("<div id='timelineCount' class='filter-count'></div>\n");
        sb.append("<div class='timeline' id='timeline'>\n");
        for (SimulationRecord record : data.records) {
            appendTimelineCard(sb, record);
        }
        sb.append("</div>\n");
        sb.append("</div>\n");
        sb.append("</div>\n");
    }

    private static void appendTimelineFilters(StringBuilder sb, ReportData data) {
        sb.append("<div class='timeline-toolbar'>\n");
        sb.append("<div class='control'><label for='actorFilter'>Actor</label><select id='actorFilter'><option value=''>All</option>");
        for (String name : data.chartNames) {
            sb.append("<option value='").append(escapeAttr(name)).append("'>").append(escapeHtml(name)).append("</option>");
        }
        sb.append("</select></div>\n");

        sb.append("<div class='control'><label for='reactionFilter'>Reaction</label><select id='reactionFilter'><option value=''>All</option>");
        List<String> reactions = data.records.stream()
                .map(record -> record.reactionType)
                .filter(label -> label != null && !"None".equals(label))
                .distinct()
                .sorted()
                .collect(Collectors.toList());
        for (String reaction : reactions) {
            sb.append("<option value='").append(escapeAttr(reaction)).append("'>")
                    .append(escapeHtml(reaction)).append("</option>");
        }
        sb.append("</select></div>\n");
        sb.append("<div class='control'><label for='searchFilter'>Search action</label><input id='searchFilter' type='search' placeholder='e.g. Burst'></div>\n");
        sb.append("<div class='control'><label for='minDamageFilter'>Min damage</label><input id='minDamageFilter' type='number' min='0' step='1000' value='0'></div>\n");
        sb.append("<label class='control check'><input id='damageOnlyFilter' type='checkbox'> Damage only</label>\n");
        sb.append("</div>\n");
    }

    private static void appendTimelineCard(StringBuilder sb, SimulationRecord record) {
        boolean isSwap = record.action != null && record.action.startsWith("Swap ->");
        double damage = Math.max(0.0, record.damage);
        String reaction = record.reactionType != null ? record.reactionType : "None";
        String action = record.action != null ? record.action : "";
        String actor = record.actor != null ? record.actor : "";
        sb.append(isSwap ? "<div class='card swap timeline-card'" : "<div class='card timeline-card'");
        sb.append(" data-actor='").append(escapeAttr(actor)).append("'");
        sb.append(" data-reaction='").append(escapeAttr(reaction)).append("'");
        sb.append(" data-damage='").append(String.format("%.0f", damage)).append("'");
        sb.append(" data-text='").append(escapeAttr((actor + " " + action + " " + reaction).toLowerCase())).append("'>\n");
        sb.append("<div class='time'>").append(String.format("%.1fs", record.time)).append("</div>\n");
        sb.append("<div class='actor'>").append(escapeHtml(actor)).append("</div>\n");

        String reactionLabel = "None".equals(reaction) ? ""
                : "<span class='reaction'>" + escapeHtml(reaction) + "</span>";
        sb.append("<div class='action'>").append(escapeHtml(action)).append(" ").append(reactionLabel).append("</div>\n");
        sb.append("<div class='damage'>").append(damage > 0.0 ? String.format("%,.0f", damage) : "-").append("</div>\n");

        if (record.formula != null && !record.formula.isEmpty()) {
            sb.append("<details class='formula-details'><summary>Formula</summary><pre>")
                    .append(escapeHtml(record.formula))
                    .append("</pre></details>\n");
        }

        appendAuraBar(sb, record);
        sb.append("</div>\n");
    }

    private static void appendAuraBar(StringBuilder sb, SimulationRecord record) {
        if (record.enemyAura == null || record.enemyAura.isEmpty()) {
            return;
        }
        sb.append("<div class='aura-bar'>\n");
        for (Map.Entry<Element, Double> entry : record.enemyAura.entrySet()) {
            if (entry.getValue() > 0.0) {
                String elem = entry.getKey().name();
                sb.append("<div class='aura-segment bg-").append(escapeAttr(elem))
                        .append("' style='flex: 1;' title='")
                        .append(escapeAttr(elem + " " + String.format("%.1fU", entry.getValue())))
                        .append("'></div>\n");
            }
        }
        sb.append("</div>\n");
    }

    private static void appendScript(StringBuilder sb, ReportData data) {
        sb.append("<script>\n");
        appendStatsHistoryScript(sb, data);
        appendChartScript(sb, data);
        appendTimelineFilterScript(sb);
        sb.append("</script>\n");
    }

    private static void appendStatsHistoryScript(StringBuilder sb, ReportData data) {
        sb.append("const statsHistory = [\n");
        if (data.hasStatsHistory) {
            for (ReportViewAdapter.ReportStatsSnapshot snapshot : data.statsHistory) {
                sb.append("{ t: ").append(String.format("%.2f", snapshot.time)).append(", chars: {\n");
                for (Map.Entry<String, ReportViewAdapter.ReportCharacterStats> entry : snapshot.characters.entrySet()) {
                    ReportViewAdapter.ReportCharacterStats stats = entry.getValue();
                    String buffsJs = stats.buffs.stream()
                            .map(ReportHtmlRenderer::jsString)
                            .collect(Collectors.joining(","));
                    sb.append(jsString(entry.getKey())).append(": { atk: ").append(String.format("%.0f", stats.atk))
                            .append(", hp: ").append(String.format("%.0f", stats.hp))
                            .append(", def: ").append(String.format("%.0f", stats.def))
                            .append(", cr: ").append(String.format("%.1f", stats.cr))
                            .append(", cd: ").append(String.format("%.1f", stats.cd))
                            .append(", er: ").append(String.format("%.1f", stats.er))
                            .append(", energy: ").append(String.format("%.1f", stats.energy))
                            .append(", em: ").append(String.format("%.0f", stats.em))
                            .append(", dmg: ").append(String.format("%.1f", stats.dmg))
                            .append(", buffs: [").append(buffsJs).append("] },\n");
                }
                sb.append("}},\n");
            }
        }
        sb.append("];\n");
        sb.append("const timeSlider = document.getElementById('timeSlider');\n");
        sb.append("const timeDisplay = document.getElementById('timeDisplay');\n");
        sb.append("function setText(selectorRoot, selector, value) { const el = selectorRoot.querySelector(selector); if (el) el.textContent = value; }\n");
        sb.append("function updateStatsTable(time) {\n");
        sb.append("  if (!statsHistory || statsHistory.length === 0) return;\n");
        sb.append("  let closest = statsHistory[0]; let minDiff = Math.abs(statsHistory[0].t - time);\n");
        sb.append("  for (let i = 1; i < statsHistory.length; i++) { const diff = Math.abs(statsHistory[i].t - time); if (diff < minDiff) { minDiff = diff; closest = statsHistory[i]; } }\n");
        sb.append("  for (const [charId, stats] of Object.entries(closest.chars)) {\n");
        sb.append("    const row = document.getElementById('row-' + charId); if (row) {\n");
        sb.append("      setText(row, '.val-atk', stats.atk); setText(row, '.val-hp', stats.hp); setText(row, '.val-def', stats.def);\n");
        sb.append("      setText(row, '.val-cr', stats.cr.toFixed(1) + '%'); setText(row, '.val-cd', stats.cd.toFixed(1) + '%'); setText(row, '.val-er', stats.er.toFixed(1) + '%');\n");
        sb.append("      setText(row, '.val-energy', stats.energy.toFixed(1) + '%'); setText(row, '.val-em', stats.em); setText(row, '.val-dmg', stats.dmg.toFixed(1) + '%'); }\n");
        sb.append("    const bl = document.getElementById('bl-' + charId); if (bl) { bl.textContent = ''; const buffs = stats.buffs || []; if (buffs.length === 0) { const empty = document.createElement('span'); empty.style.color = '#666'; empty.textContent = '-'; bl.appendChild(empty); } else { buffs.forEach(b => { const tag = document.createElement('span'); tag.className = 'buff-tag'; tag.textContent = b; bl.appendChild(tag); }); } }\n");
        sb.append("  }\n");
        sb.append("}\n");
        sb.append("if (timeSlider) { timeSlider.addEventListener('input', (e) => { const t = parseFloat(e.target.value); timeDisplay.textContent = t.toFixed(1) + 's'; updateStatsTable(t); }); updateStatsTable(0); }\n");
    }

    private static void appendChartScript(StringBuilder sb, ReportData data) {
        sb.append("Chart.register(ChartDataLabels);\n");
        appendPieChart(sb, "dpsPie", data.chartNames, data.chartNames.stream()
                .map(name -> data.totalDamageByActor.getOrDefault(name, 0.0)).collect(Collectors.toList()),
                Arrays.asList(data.chartColors), true);
        appendLineChart(sb, "dpsLine", characterDatasets(data), "Total Damage", false);
        appendPieChart(sb, "reactionPie",
                metricLabels(data.reactionDamageTotals),
                metricValues(data.reactionDamageTotals),
                fallbackColors(data.reactionDamageTotals.size()), true);
        sb.append("const actionBarChart = ");
        appendBarChart(sb, "actionBar", metricLabels(data.actionDamageTotals), metricValues(data.actionDamageTotals),
                fallbackColors(data.actionDamageTotals.size()), "Damage");
        appendActionDamageFilterScript(sb, data);
        appendLineChart(sb, "rollingDps", List.of("{ label: 'Rolling DPS', data: ["
                + String.join(",", data.rollingDpsSeries)
                + "], borderColor: '#ff7b7b', backgroundColor: '#ff7b7b', fill: false, tension: 0.15 }"),
                "DPS", false);
        appendLineChart(sb, "auraTimeline", auraDatasets(data), "Aura Units", true);
        appendLineChart(sb, "energyTimeline", energyDatasets(data), "Energy %", false);
        appendBarChart(sb, "buffUptime", buffLabels(data), buffValues(data), fallbackColors(data.buffUptime.size()),
                "Uptime %");
        if (!data.reactionLabeledDamageTotals.isEmpty()) {
            appendBarChart(sb, "reactionLabeledBar", metricLabels(data.reactionLabeledDamageTotals),
                    metricValues(data.reactionLabeledDamageTotals),
                    fallbackColors(data.reactionLabeledDamageTotals.size()), "Reaction-labeled Damage");
        }
    }

    private static void appendPieChart(
            StringBuilder sb,
            String canvasId,
            List<String> labels,
            List<Double> values,
            List<String> colors,
            boolean showPercent) {
        sb.append("createPieChart(").append(jsString(canvasId)).append(", [")
                .append(labels.stream().map(ReportHtmlRenderer::jsString).collect(Collectors.joining(",")))
                .append("], [").append(values.stream().map(value -> String.format("%.3f", value)).collect(Collectors.joining(",")))
                .append("], [").append(String.join(",", colors)).append("], ").append(showPercent).append(");\n");
    }

    private static void appendBarChart(
            StringBuilder sb,
            String canvasId,
            List<String> labels,
            List<Double> values,
            List<String> colors,
            String xTitle) {
        sb.append("createBarChart(").append(jsString(canvasId)).append(", [")
                .append(labels.stream().map(ReportHtmlRenderer::jsString).collect(Collectors.joining(",")))
                .append("], [").append(values.stream().map(value -> String.format("%.3f", value)).collect(Collectors.joining(",")))
                .append("], [").append(String.join(",", colors)).append("], ").append(jsString(xTitle)).append(");\n");
    }

    private static void appendActionDamageFilterScript(StringBuilder sb, ReportData data) {
        sb.append("const actionDamageByActor = {\n");
        for (Map.Entry<String, List<ReportData.ReportMetricView>> entry : data.actionDamageTotalsByActor.entrySet()) {
            sb.append("  ").append(jsString(entry.getKey())).append(": { labels: [")
                    .append(metricLabels(entry.getValue()).stream()
                            .map(ReportHtmlRenderer::jsString)
                            .collect(Collectors.joining(",")))
                    .append("], values: [")
                    .append(metricValues(entry.getValue()).stream()
                            .map(value -> String.format("%.3f", value))
                            .collect(Collectors.joining(",")))
                    .append("] },\n");
        }
        sb.append("};\n");
        sb.append("const actionActorFilter = document.getElementById('actionActorFilter');\n");
        sb.append("if (actionActorFilter && actionBarChart) {\n");
        sb.append("  Object.keys(actionDamageByActor).forEach(actor => { const option = document.createElement('option'); option.value = actor; option.textContent = actor; actionActorFilter.appendChild(option); });\n");
        sb.append("  actionActorFilter.addEventListener('change', () => { const selected = actionActorFilter.value; const labels = selected === '__all__' ? [")
                .append(metricLabels(data.actionDamageTotals).stream()
                        .map(ReportHtmlRenderer::jsString)
                        .collect(Collectors.joining(",")))
                .append("] : actionDamageByActor[selected].labels; const values = selected === '__all__' ? [")
                .append(metricValues(data.actionDamageTotals).stream()
                        .map(value -> String.format("%.3f", value))
                        .collect(Collectors.joining(",")))
                .append("] : actionDamageByActor[selected].values; actionBarChart.data.labels = labels; actionBarChart.data.datasets[0].data = values; actionBarChart.data.datasets[0].backgroundColor = chartPalette(values.length); actionBarChart.update(); });\n");
        sb.append("}\n");
    }

    private static void appendLineChart(StringBuilder sb, String canvasId, List<String> datasets, String yTitle,
            boolean stepped) {
        sb.append("createLineChart(").append(jsString(canvasId)).append(", [")
                .append(String.join(",", datasets)).append("], ").append(jsString(yTitle)).append(", ")
                .append(stepped).append(");\n");
    }

    private static List<String> characterDatasets(ReportData data) {
        java.util.ArrayList<String> datasets = new java.util.ArrayList<>();
        for (int i = 0; i < data.chartNames.size(); i++) {
            String name = data.chartNames.get(i);
            String color = data.chartColors[i % data.chartColors.length];
            datasets.add("{ label: " + jsString(name) + ", data: ["
                    + String.join(",", data.cumulativeDamageSeries.getOrDefault(name, List.of()))
                    + "], borderColor: " + color + ", backgroundColor: " + color
                    + ", fill: false, tension: 0.1 }");
        }
        return datasets;
    }

    private static List<String> auraDatasets(ReportData data) {
        java.util.ArrayList<String> datasets = new java.util.ArrayList<>();
        for (Map.Entry<Element, List<String>> entry : data.auraSeries.entrySet()) {
            String color = elementColor(entry.getKey());
            datasets.add("{ label: " + jsString(entry.getKey().name()) + ", data: ["
                    + String.join(",", entry.getValue()) + "], borderColor: " + color
                    + ", backgroundColor: " + color + ", fill: false, stepped: true }");
        }
        return datasets;
    }

    private static List<String> energyDatasets(ReportData data) {
        java.util.ArrayList<String> datasets = new java.util.ArrayList<>();
        int index = 0;
        for (Map.Entry<String, List<String>> entry : data.energySeries.entrySet()) {
            String color = data.chartColors.length > 0 ? data.chartColors[index % data.chartColors.length] : "'#AAAAAA'";
            datasets.add("{ label: " + jsString(entry.getKey()) + ", data: ["
                    + String.join(",", entry.getValue()) + "], borderColor: " + color
                    + ", backgroundColor: " + color + ", fill: false, tension: 0.1 }");
            index++;
        }
        return datasets;
    }

    private static void appendTimelineFilterScript(StringBuilder sb) {
        sb.append("function createPieChart(id, labels, values, colors, showPercent) { const el = document.getElementById(id); if (!el) return; new Chart(el.getContext('2d'), { type: 'pie', data: { labels, datasets: [{ data: values, backgroundColor: colors, borderWidth: 0 }] }, options: { responsive: true, maintainAspectRatio: false, plugins: { legend: { position: 'right', labels: { color: '#eef2f6' } }, datalabels: { color: '#fff', display: showPercent && values.length > 0, formatter: (value, ctx) => { const sum = ctx.chart.data.datasets[0].data.reduce((a,b)=>a+b,0); return sum > 0 ? (value * 100 / sum).toFixed(1) + '%' : ''; }, font: { weight: 'bold', size: 11 } } } } }); }\n");
        sb.append("function createLineChart(id, datasets, yTitle, stepped) { const el = document.getElementById(id); if (!el) return; new Chart(el.getContext('2d'), { type: 'line', data: { datasets }, options: { responsive: true, maintainAspectRatio: false, parsing: false, scales: { x: { type: 'linear', title: { display: true, text: 'Time (s)', color: '#9aa6b2' }, ticks: { color: '#9aa6b2' }, grid: { color: '#2f3742' } }, y: { title: { display: true, text: yTitle, color: '#9aa6b2' }, ticks: { color: '#9aa6b2' }, grid: { color: '#2f3742' } } }, elements: { line: { stepped } }, plugins: { legend: { labels: { color: '#eef2f6' } }, datalabels: { display: false } } } }); }\n");
        sb.append("function chartPalette(size) { const palette = ['#64b5f6','#ff7b7b','#7bd88f','#ffd166','#c792ea','#4dd0e1','#f78c6c','#a5c882']; return Array.from({length: size}, (_, i) => palette[i % palette.length]); }\n");
        sb.append("function createBarChart(id, labels, values, colors, xTitle) { const el = document.getElementById(id); if (!el) return null; return new Chart(el.getContext('2d'), { type: 'bar', data: { labels, datasets: [{ data: values, backgroundColor: colors, borderWidth: 0 }] }, options: { indexAxis: 'y', responsive: true, maintainAspectRatio: false, scales: { x: { title: { display: true, text: xTitle, color: '#9aa6b2' }, ticks: { color: '#9aa6b2' }, grid: { color: '#2f3742' } }, y: { ticks: { color: '#9aa6b2' }, grid: { display: false } } }, plugins: { legend: { display: false }, datalabels: { display: false } } } }); }\n");
        sb.append("const filterInputs = ['actorFilter','reactionFilter','searchFilter','minDamageFilter','damageOnlyFilter'].map(id => document.getElementById(id)).filter(Boolean);\n");
        sb.append("function applyTimelineFilters() { const actor = document.getElementById('actorFilter')?.value || ''; const reaction = document.getElementById('reactionFilter')?.value || ''; const search = (document.getElementById('searchFilter')?.value || '').toLowerCase(); const minDamage = parseFloat(document.getElementById('minDamageFilter')?.value || '0'); const damageOnly = document.getElementById('damageOnlyFilter')?.checked || false; let visible = 0; let total = 0; document.querySelectorAll('.timeline-card').forEach(row => { total++; const dmg = parseFloat(row.dataset.damage || '0'); const ok = (!actor || row.dataset.actor === actor) && (!reaction || row.dataset.reaction === reaction) && (!search || (row.dataset.text || '').includes(search)) && (!damageOnly || dmg > 0) && (isNaN(minDamage) || dmg >= minDamage); row.classList.toggle('hidden', !ok); if (ok) visible++; }); const count = document.getElementById('timelineCount'); if (count) count.textContent = visible + ' / ' + total + ' events shown'; }\n");
        sb.append("filterInputs.forEach(input => input.addEventListener('input', applyTimelineFilters)); applyTimelineFilters();\n");
    }

    private static List<String> metricLabels(List<ReportData.ReportMetricView> metrics) {
        return metrics.stream().map(metric -> metric.label).collect(Collectors.toList());
    }

    private static List<Double> metricValues(List<ReportData.ReportMetricView> metrics) {
        return metrics.stream().map(metric -> metric.value).collect(Collectors.toList());
    }

    private static List<String> buffLabels(ReportData data) {
        return data.buffUptime.stream()
                .map(view -> view.characterName + ": " + view.buffName)
                .collect(Collectors.toList());
    }

    private static List<Double> buffValues(ReportData data) {
        return data.buffUptime.stream().map(view -> view.uptimePercent).collect(Collectors.toList());
    }

    private static List<String> fallbackColors(int size) {
        List<String> palette = List.of("'#64b5f6'", "'#ff7b7b'", "'#7bd88f'", "'#ffd166'", "'#c792ea'",
                "'#4dd0e1'", "'#f78c6c'", "'#a5c882'");
        java.util.ArrayList<String> colors = new java.util.ArrayList<>();
        for (int i = 0; i < size; i++) {
            colors.add(palette.get(i % palette.size()));
        }
        return colors;
    }

    private static String elementColor(Element element) {
        switch (element) {
            case PYRO:
                return "'#FF5555'";
            case HYDRO:
                return "'#3388FF'";
            case ELECTRO:
                return "'#AA55FF'";
            case CRYO:
                return "'#55FFFF'";
            case ANEMO:
                return "'#33FF99'";
            case GEO:
                return "'#FFAA00'";
            case DENDRO:
                return "'#77EE44'";
            case PHYSICAL:
            default:
                return "'#CCCCCC'";
        }
    }

    static String escapeHtml(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    static String escapeAttr(String value) {
        return escapeHtml(value).replace("\n", "&#10;").replace("\r", "");
    }

    static String jsString(String value) {
        if (value == null) {
            return "''";
        }
        String escaped = value.replace("\\", "\\\\")
                .replace("'", "\\'")
                .replace("<", "\\u003C")
                .replace(">", "\\u003E")
                .replace("&", "\\u0026")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\u2028", "\\u2028")
                .replace("\u2029", "\\u2029");
        return "'" + escaped + "'";
    }

    private static void appendDocumentEnd(StringBuilder sb) {
        sb.append("</div>\n");
        sb.append("</body></html>");
    }
}

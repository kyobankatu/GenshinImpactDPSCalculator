package sample;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import mechanics.analysis.StatsSnapshot;
import model.type.CharacterId;
import model.type.Element;
import model.type.StatType;
import visualization.HtmlReportGenerator;
import visualization.SimulationRecord;

/**
 * Lightweight checks for generated HTML report structure and escaping.
 */
public class ReportRegressionTest {
    public static void main(String[] args) throws Exception {
        testReportChartsAndEscaping();
        testEmptyReportRenders();
        System.out.println("ReportRegressionTest passed");
    }

    private static void testReportChartsAndEscaping() throws Exception {
        Map<Element, Double> aura = new EnumMap<>(Element.class);
        aura.put(Element.PYRO, 1.0);
        aura.put(Element.HYDRO, 0.5);

        List<SimulationRecord> records = new ArrayList<>();
        records.add(new SimulationRecord(0.5, "Tester <Actor>", "Attack '</script><script>alert(1)</script>",
                1234.0, "None", 0.0, aura, "formula <tag> & '</script>'"));
        records.add(new SimulationRecord(1.0, "Tester <Actor>", "Reaction Hit", 2500.0,
                "Aggravate", 0.0, aura, null));
        records.add(new SimulationRecord(1.5, "Thundercloud", "Lunar-Charged Tick", 3000.0,
                "Lunar-Charged", 3000.0, aura, null));

        Map<CharacterId, Map<StatType, Double>> statMap = new HashMap<>();
        Map<StatType, Double> sucroseStats = new EnumMap<>(StatType.class);
        sucroseStats.put(StatType.BASE_ATK, 800.0);
        sucroseStats.put(StatType.ATK_PERCENT, 0.5);
        sucroseStats.put(StatType.CRIT_RATE, 0.5);
        sucroseStats.put(StatType.CRIT_DMG, 1.0);
        sucroseStats.put(StatType.ENERGY_RECHARGE, 1.5);
        sucroseStats.put(StatType.ELEMENTAL_MASTERY, 900.0);
        statMap.put(CharacterId.SUCROSE, sucroseStats);

        Map<CharacterId, List<String>> initialBuffMap = new HashMap<>();
        initialBuffMap.put(CharacterId.SUCROSE,
                List.of("Always-on Buff <unsafe> '</script>'", "Dynamic Buff <unsafe>"));
        Map<CharacterId, List<String>> laterBuffMap = new HashMap<>();
        laterBuffMap.put(CharacterId.SUCROSE, List.of("Always-on Buff <unsafe> '</script>'"));

        Map<CharacterId, Double> energy = new HashMap<>();
        energy.put(CharacterId.SUCROSE, 75.0);

        HtmlReportGenerator.generate("report_regression.html", records, null,
                List.of(
                        new StatsSnapshot(0.0, statMap, initialBuffMap, energy),
                        new StatsSnapshot(1.0, statMap, laterBuffMap, energy)));

        String html = Files.readString(Paths.get("output/report_regression.html"), StandardCharsets.UTF_8);
        assertContains(html, "id='reactionPie'", "reaction chart container");
        assertContains(html, "id='actionBar'", "action chart container");
        assertContains(html, "id='actionActorFilter'", "action actor filter");
        assertContains(html, "const actionDamageByActor", "action damage by actor data");
        assertContains(html, "id='rollingDps'", "rolling DPS chart container");
        assertContains(html, "id='auraTimeline'", "aura timeline chart container");
        assertContains(html, "id='energyTimeline'", "energy timeline chart container");
        assertContains(html, "id='buffUptime'", "buff uptime chart container");
        assertContains(html, "artifact-rolls", "artifact rolls table class");
        assertContains(html, "Dynamic Buff", "variable buff uptime label");
        assertContains(html, "Reaction-labeled Direct Damage", "reaction-labeled direct damage section");
        assertContains(html, "&lt;Actor&gt;", "HTML-escaped actor label");
        assertContains(html, "\\u003C/script\\u003E", "JS-escaped script close tag");
        assertNotContains(html, "Attack '</script><script>", "raw script-breaking action label");
        assertNotContains(html, "formula <tag>", "raw formula text");
    }

    private static void testEmptyReportRenders() throws Exception {
        HtmlReportGenerator.generate("report_empty_regression.html", List.of(), null, null);
        String html = Files.readString(Paths.get("output/report_empty_regression.html"), StandardCharsets.UTF_8);
        assertContains(html, "Simulation Report", "empty report title");
        assertContains(html, "id='dpsPie'", "empty report chart container");
        assertContains(html, "const statsHistory", "empty report script");
    }

    private static void assertContains(String text, String expected, String message) {
        if (!text.contains(expected)) {
            throw new AssertionError("Expected " + message + " to contain: " + expected);
        }
    }

    private static void assertNotContains(String text, String unexpected, String message) {
        if (text.contains(unexpected)) {
            throw new AssertionError("Expected " + message + " not to contain: " + unexpected);
        }
    }
}

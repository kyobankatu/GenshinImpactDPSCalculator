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
import model.entity.ArtifactSet;
import model.entity.Weapon;
import model.stats.StatsContainer;
import model.type.CharacterId;
import model.type.Element;
import model.type.StatType;
import simulation.CombatSimulator;
import visualization.HtmlReportGenerator;
import visualization.SimulationRecord;

/**
 * Lightweight checks for generated HTML report structure and escaping.
 */
public class ReportRegressionTest {
    public static void main(String[] args) throws Exception {
        testReportChartsAndEscaping();
        testCharacterFaceAssetsAndFallbackEscaping();
        testEmptyReportRenders();
        testAuraTimelineContinuousDecay();
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
        records.add(new SimulationRecord(1.2, "Tester <Actor>", "Electro-Charged Tick", 999.0,
                "Electro-Charged Tick", 999.0, aura, null));
        records.add(new SimulationRecord(1.5, "Thundercloud", "Lunar-Charged Tick", 3000.0,
                "Lunar-Charged", 3000.0, aura, null));
        records.add(new SimulationRecord(1.7, "Polestar Field", "Stellar-Conduct", 4000.0,
                "Stellar-Conduct", 4000.0, aura, null));
        records.add(new SimulationRecord(1.8, "Tester <Actor>", "Stellar-Swirl", 3500.0,
                "Stellar-Swirl", 3500.0, aura, null));
        records.add(new SimulationRecord(1.9, "Tester <Actor>", "Direct Stellar-Swirl", 777.0,
                "None", 0.0, aura, null));

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
        assertContains(html, "Elemental Reaction Damage", "unified reaction chart title");
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
        assertContains(html, "Only separately recorded elemental reaction damage is included",
                "reaction chart scope note");
        assertContains(html, "Lunar-Charged", "explicit reaction damage label");
        assertContains(html,
                "createPieChart('reactionPie', ['Stellar-Conduct','Stellar-Swirl','Lunar-Charged','Electro-Charged']",
                "unified reaction chart labels");
        assertContains(html, "Direct Stellar-Swirl", "direct Stellar action remains in action damage");
        assertNotContains(html,
                "createPieChart('reactionPie', ['Stellar-Conduct','Stellar-Swirl','Lunar-Charged','Direct Stellar-Swirl'",
                "direct Stellar action excluded from reaction chart");
        assertNotContains(html, "Reaction-labeled Direct Damage", "removed reaction-labeled direct damage section");
        assertNotContains(html, "Reaction-labeled Damage", "removed reaction-labeled direct damage chart script");
        assertNotContains(html, "createPieChart('reactionPie', ['Lunar-Charged','Electro-Charged','Aggravate'",
                "reaction-labeled direct hit excluded from reaction chart");
        assertContains(html, "&lt;Actor&gt;", "HTML-escaped actor label");
        assertContains(html, "\\u003C/script\\u003E", "JS-escaped script close tag");
        assertNotContains(html, "Attack '</script><script>", "raw script-breaking action label");
        assertNotContains(html, "formula <tag>", "raw formula text");
    }

    private static void testCharacterFaceAssetsAndFallbackEscaping() throws Exception {
        String unsafeName = "Tester <Icon> '</script>'";
        CombatSimulator sim = new CombatSimulator();
        sim.addCharacter(new ReportTestCharacter(CharacterId.SUCROSE, "Sucrose"));
        sim.addCharacter(new ReportTestCharacter(CharacterId.UNKNOWN, unsafeName));

        List<SimulationRecord> records = List.of(
                new SimulationRecord(0.1, "Sucrose", "Safe Action", 100.0, "None", 0.0,
                        new EnumMap<>(Element.class), null),
                new SimulationRecord(0.2, unsafeName, "Unsafe Action </script>", 200.0, "None", 0.0,
                        new EnumMap<>(Element.class), null));

        Map<CharacterId, Map<StatType, Double>> statMap = new HashMap<>();
        Map<StatType, Double> sucroseStats = new EnumMap<>(StatType.class);
        sucroseStats.put(StatType.BASE_ATK, 800.0);
        sucroseStats.put(StatType.BASE_HP, 10000.0);
        sucroseStats.put(StatType.BASE_DEF, 700.0);
        sucroseStats.put(StatType.ENERGY_RECHARGE, 1.5);
        statMap.put(CharacterId.SUCROSE, sucroseStats);
        Map<CharacterId, List<String>> buffMap = new HashMap<>();
        buffMap.put(CharacterId.SUCROSE, List.of("Character Buff <safe>"));
        Map<CharacterId, Double> energyMap = new HashMap<>();
        energyMap.put(CharacterId.SUCROSE, 42.0);

        HtmlReportGenerator.generate("report_face_asset_regression.html", records, sim,
                List.of(new StatsSnapshot(0.0, statMap, buffMap, energyMap),
                        new StatsSnapshot(1.0, statMap, buffMap, energyMap)));

        String html = Files.readString(Paths.get("output/report_face_asset_regression.html"),
                StandardCharsets.UTF_8);
        assertContains(html, "id='characterAssetTemplate'", "character asset template");
        assertContains(html, "const characterAssets", "character asset script data");
        assertContains(html, "../config/characters/Sucrose/face.png", "known character face path");
        assertContains(html, "alt='Sucrose face icon'", "known character face alt text");
        assertContains(html, "data-character-key='sucrose'", "known character data key");
        assertContains(html, "hasIcon: true", "known character icon availability");
        assertContains(html, "id='characterDetails'", "character details section");
        assertContains(html, "role='tablist'", "character tablist");
        assertContains(html, "data-character-tab='sucrose'", "known character tab button");
        assertContains(html, "data-character-panel='sucrose'", "known character tab panel");
        assertContains(html, "aria-selected='true'", "default selected character tab");
        assertContains(html, "id='character-panel-unknown' aria-labelledby='character-tab-unknown' data-character-panel='unknown' style='--char-color:#AAAAAA' hidden",
                "inactive character panel hidden by default");
        assertContains(html, "class='character-loadout'", "character loadout block");
        assertContains(html, "Lv. 90", "character level display");
        assertContains(html, "C0", "character constellation display");
        assertContains(html, "../config/weapons/PrimordialJadeWingedSpear/icon.png", "character weapon icon path");
        assertContains(html, "Primordial Jade Winged-Spear", "character weapon display");
        assertContains(html, "../config/artifacts/ViridescentVenerer/flower.png", "character artifact icon path");
        assertContains(html, "Viridescent Venerer", "character artifact set display");
        assertContains(html, "class='character-widget character-action-damage'", "character action damage widget");
        assertContains(html, "Safe Action", "per-character action damage label");
        assertContains(html, "class='character-widget character-artifact-rolls'", "character artifact rolls widget");
        assertContains(html, "No artifact roll data.", "empty artifact rolls state");
        assertContains(html, "class='character-widget character-energy'", "character energy widget");
        assertContains(html, "Latest energy 42.0%", "character latest energy");
        assertContains(html, "label: 'Sucrose', data: [{x: 0.00, y: 42.0},{x: 1.00, y: 42.0}], borderColor: '#33FF99'",
                "character energy chart uses element color");
        assertContains(html, "class='character-widget character-buff-uptime'", "character buff uptime widget");
        assertContains(html, "Character Buff &lt;safe&gt;", "character buff label escaping");
        assertContains(html, "class='character-widget character-top-events'", "character top events widget");
        assertContains(html, "class='character-widget character-recent-events'", "character recent events widget");
        assertContains(html, "No sampled active buffs.", "empty character buff state");
        assertContains(html, "function activateCharacterTab", "character tab switching script");
        assertContains(html, "class='face-fallback'", "missing icon fallback markup");
        assertContains(html, "Tester &lt;Icon&gt;", "HTML-escaped unsafe character name");
        assertContains(html, "\\u003CIcon\\u003E", "JS-escaped unsafe character name");
        assertContains(html, "../config/characters/TesterIconScript/face.png",
                "normalized missing face path");
        assertNotContains(html, "Unsafe Action </script>", "raw unsafe action label");
    }

    private static void testEmptyReportRenders() throws Exception {
        HtmlReportGenerator.generate("report_empty_regression.html", List.of(), null, null);
        String html = Files.readString(Paths.get("output/report_empty_regression.html"), StandardCharsets.UTF_8);
        assertContains(html, "Simulation Report", "empty report title");
        assertContains(html, "id='dpsPie'", "empty report chart container");
        assertContains(html, "const statsHistory", "empty report script");
    }

    private static void testAuraTimelineContinuousDecay() throws Exception {
        // Records carry the decayed current aura units at each event time. A 1U Pyro
        // aura decays from 1.0 at t=0 to 0.5 at the midpoint and to 0.0 at expiry.
        List<SimulationRecord> records = new ArrayList<>();
        Map<Element, Double> full = new EnumMap<>(Element.class);
        full.put(Element.PYRO, 1.0);
        Map<Element, Double> mid = new EnumMap<>(Element.class);
        mid.put(Element.PYRO, 0.5);
        Map<Element, Double> expired = new EnumMap<>(Element.class);

        records.add(new SimulationRecord(0.0, "Tester", "Pyro Application", 100.0, "None", 0.0, full, null));
        records.add(new SimulationRecord(5.5, "Tester", "Idle Tick", 100.0, "None", 0.0, mid, null));
        records.add(new SimulationRecord(11.0, "Tester", "Idle Tick", 100.0, "None", 0.0, expired, null));

        HtmlReportGenerator.generate("report_aura_decay_regression.html", records, null, null);
        String html = Files.readString(Paths.get("output/report_aura_decay_regression.html"), StandardCharsets.UTF_8);

        // Renderer test: Aura Timeline uses continuous (non-stepped) rendering.
        assertContains(html, "Continuous enemy aura units", "continuous aura timeline description");
        assertContains(html, "stepped: false, tension: 0", "non-stepped aura dataset rendering");
        // Normal path: a midpoint aura value below the initial units is present.
        assertContains(html, "{x: 5.50, y: 0.50}", "midpoint decayed aura value below initial units");
        // Boundary value: aura reaches zero at expiry.
        assertContains(html, "{x: 11.00, y: 0.00}", "zero aura value at expiry");
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

    private static final class ReportTestCharacter extends model.entity.Character {
        private ReportTestCharacter(CharacterId id, String displayName) {
            this.characterId = id;
            this.name = displayName;
            this.element = id == CharacterId.SUCROSE ? Element.ANEMO : null;
            this.weapon = new Weapon("Primordial Jade Winged-Spear", new StatsContainer());
            this.artifacts = new ArtifactSet[] { new ArtifactSet("Viridescent Venerer", new StatsContainer()) };
            this.baseStats.set(StatType.BASE_HP, 10000.0);
            this.baseStats.set(StatType.BASE_ATK, 1000.0);
            this.baseStats.set(StatType.BASE_DEF, 700.0);
        }

        @Override
        public void applyPassive(StatsContainer currentStats) {
        }

        @Override
        public double getEnergyCost() {
            return 60.0;
        }
    }
}

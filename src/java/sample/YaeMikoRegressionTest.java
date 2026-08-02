package sample;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import mechanics.buff.Buff;
import mechanics.buff.BuffId;
import mechanics.buff.SimpleBuff;
import mechanics.data.TalentDataSource;
import model.character.YaeMiko;
import model.entity.Character;
import model.entity.Enemy;
import model.stats.StatsContainer;
import model.type.ActionType;
import model.type.CharacterId;
import model.type.Element;
import model.type.ICDTag;
import model.type.ICDType;
import model.type.StatType;
import simulation.CombatSimulator;
import simulation.action.AttackAction;
import simulation.action.CharacterActionKey;
import simulation.action.CharacterActionRequest;

/** Focused regression checks for Yae Miko's old-base-kit offensive slice. */
public final class YaeMikoRegressionTest {
    private static final double EPS = 1e-8;

    private YaeMikoRegressionTest() {
    }

    /** Runs data, action, summon, Burst, constellation, and isolation checks. */
    public static void main(String[] args) throws Exception {
        testIdentityStatsAndCsvShape();
        testNormalChargedAndPlungeActions();
        testSakuraTimingLevelsParticlesAndDuration();
        testSkillChargeBoundaryReplacementAndSwitch();
        testBurstConsumptionTimingCooldownAndC1();
        testA4UsesLiveElementalMastery();
        testConstellationsTwoThroughSix();
        testStaleTimersIndependentInstancesAndBinding();
        testInvalidConstellationAndAction();
        System.out.println("YaeMikoRegressionTest passed");
    }

    private static void testIdentityStatsAndCsvShape() throws IOException {
        YaeMiko yae = yaeAtConstellation(6);
        assertEquals(CharacterId.YAE_MIKO, yae.getCharacterId(),
                "Yae typed id");
        assertEquals(CharacterId.YAE_MIKO, CharacterId.fromName("Yae Miko"),
                "Yae name lookup");
        assertEquals(CharacterId.YAE_MIKO, CharacterId.fromNumericId(16),
                "Yae numeric lookup");
        assertEquals(Element.ELECTRO, yae.getElement(), "Yae element");
        assertClose(10372.0,
                yae.getBaseStats().get(StatType.BASE_HP), EPS,
                "Yae base HP");
        assertClose(340.0,
                yae.getBaseStats().get(StatType.BASE_ATK), EPS,
                "Yae base ATK");
        assertClose(569.0,
                yae.getBaseStats().get(StatType.BASE_DEF), EPS,
                "Yae base DEF");
        assertClose(0.242,
                yae.getBaseStats().get(StatType.CRIT_RATE), EPS,
                "Yae final base CRIT Rate");
        assertClose(90.0, yae.getEnergyCost(), EPS, "Yae Energy cost");
        assertClose(0.0015,
                yae.getEffectiveStats(0.0).get(
                        StatType.ELEMENTAL_MASTERY_TO_SKILL_DMG_BONUS_RATIO),
                EPS,
                "Yae A4 typed ratio");

        assertCsvShape(
                Paths.get("config/characters/YaeMiko/YaeMiko_Status.csv"),
                10);
        assertCsvShape(
                Paths.get(
                        "config/characters/YaeMiko/YaeMiko_Multipliers.csv"),
                21);
    }

    private static void testNormalChargedAndPlungeActions() {
        YaeMiko yae = yaeAtConstellation(0);
        CombatSimulator sim = simulatorWith(yae);
        List<ActionRecord> actions = captureYaeActions(sim);
        double[] multipliers = { 0.674193, 0.654826, 0.967110 };
        double[] durations = { 16.0 / 60.0, 36.0 / 60.0, 79.0 / 60.0 };
        for (int i = 0; i < multipliers.length; i++) {
            perform(sim, CharacterActionKey.NORMAL);
            AttackAction action = actions.get(i).action;
            assertEquals("Spiritfox Sin-Eater N" + (i + 1),
                    action.getName(), "Yae Normal name");
            assertClose(multipliers[i], action.getDamagePercent(), EPS,
                    "Yae Normal multiplier");
            assertClose(durations[i], action.getAnimationDuration(), EPS,
                    "Yae Normal duration");
            assertEquals(ActionType.NORMAL, action.getActionType(),
                    "Yae Normal type");
            assertEquals(Element.ELECTRO, action.getElement(),
                    "Yae Normal element");
            assertEquals(ICDType.Standard, action.getICDType(),
                    "Yae Normal ICD");
            assertClose(1.0, action.getGaugeUnits(), EPS,
                    "Yae Normal gauge");
        }
        actions.clear();
        perform(sim, CharacterActionKey.NORMAL);
        assertEquals("Spiritfox Sin-Eater N1", actions.get(0).action.getName(),
                "Yae Normal chain wraps");

        actions.clear();
        perform(sim, CharacterActionKey.CHARGE);
        AttackAction charged = actions.get(0).action;
        assertClose(2.429212, charged.getDamagePercent(), EPS,
                "Yae Charged multiplier");
        assertEquals(ActionType.CHARGE, charged.getActionType(),
                "Yae Charged type");
        assertEquals(ICDType.None, charged.getICDType(),
                "Yae Charged no ICD");
        assertClose(1.0, charged.getGaugeUnits(), EPS,
                "Yae Charged gauge");

        actions.clear();
        perform(sim, CharacterActionKey.PLUNGE);
        AttackAction plunge = actions.get(0).action;
        assertClose(2.607632, plunge.getDamagePercent(), EPS,
                "Yae high Plunge multiplier");
        assertEquals(ActionType.PLUNGE, plunge.getActionType(),
                "Yae high Plunge type");
        assertEquals(Element.ELECTRO, plunge.getElement(),
                "Yae high Plunge element");
        assertEquals(ICDType.None, plunge.getICDType(),
                "Yae high Plunge no ICD");
    }

    private static void testSakuraTimingLevelsParticlesAndDuration() {
        YaeMiko yae = yaeAtConstellation(0);
        CombatSimulator sim = simulatorWith(yae);
        List<ActionRecord> sakura = captureNamedActions(sim, "Sesshou Sakura");

        perform(sim, CharacterActionKey.SKILL);
        assertClose(37.0 / 60.0, sim.getCurrentTime(), EPS,
                "Yae Skill action duration");
        assertEquals(1, yae.getSakuraCount(sim.getCurrentTime()),
                "Yae first Sakura placement at frame 34");
        assertEquals(0, sakura.size(), "Yae Sakura waits for first strike");
        advanceTo(sim, 120.0 / 60.0 - 0.001);
        assertEquals(0, sakura.size(), "Yae pre-first-strike boundary");
        sim.advanceTime(0.002);
        assertEquals(1, sakura.size(), "Yae first Sakura strike");
        assertClose(120.0 / 60.0, sakura.get(0).time, EPS,
                "Yae first Sakura strike frame");
        assertClose(1.031424, sakura.get(0).action.getDamagePercent(), EPS,
                "Yae C0 level-one Sakura multiplier");
        assertEquals(ICDType.Standard, sakura.get(0).action.getICDType(),
                "Yae Sakura standard ICD");
        assertEquals(ICDTag.ElementalSkill,
                sakura.get(0).action.getICDTag(), "Yae Sakura ICD tag");
        assertClose(1.0, sakura.get(0).action.getGaugeUnits(), EPS,
                "Yae Sakura gauge");
        assertTrue(!sakura.get(0).action.isUseSnapshot(),
                "Yae Sakura evaluates dynamically");
        assertClose(3.0, yae.getTotalParticleEnergy(), EPS,
                "Yae first Sakura hit generates one Electro particle");

        advanceTo(sim, 296.0 / 60.0 - 0.001);
        assertEquals(1, sakura.size(), "Yae pre-second-strike boundary");
        sim.advanceTime(0.002);
        assertEquals(2, sakura.size(), "Yae 176-frame Sakura interval");
        assertClose(6.0, yae.getTotalParticleEnergy(), EPS,
                "Yae particle ICD permits next 176-frame strike");

        double expiry = (34.0 + 840.0) / 60.0;
        advanceTo(sim, expiry - 0.001);
        assertEquals(1, yae.getSakuraCount(sim.getCurrentTime()),
                "Yae Sakura active before 14-second expiry");
        sim.advanceTime(0.002);
        assertEquals(0, yae.getSakuraCount(sim.getCurrentTime()),
                "Yae Sakura expires at half-open boundary");
    }

    private static void testSkillChargeBoundaryReplacementAndSwitch() {
        YaeMiko yae = yaeAtConstellation(0);
        CombatSimulator sim = simulatorWith(yae);
        TestCharacter teammate = new TestCharacter(
                CharacterId.NOELLE, Element.GEO);
        sim.addCharacter(teammate);
        List<ActionRecord> sakura = captureNamedActions(sim, "Sesshou Sakura");

        perform(sim, CharacterActionKey.SKILL);
        perform(sim, CharacterActionKey.SKILL);
        perform(sim, CharacterActionKey.SKILL);
        assertClose(111.0 / 60.0, sim.getCurrentTime(), EPS,
                "Yae consumes three Skill charges without waiting");
        assertEquals(3, yae.getSakuraCount(sim.getCurrentTime()),
                "Yae three-Sakura cap");
        assertEquals(3, yae.getCurrentSakuraLevel(sim.getCurrentTime()),
                "Yae linked C0 Sakura level");
        assertClose(145.0 / 60.0,
                yae.getSkillCDRemaining(sim.getCurrentTime()), EPS,
                "Yae fourth Skill waits for delayed first charge restore");

        sim.setActiveCharacter(CharacterId.NOELLE);
        advanceTo(sim, 120.0 / 60.0);
        assertEquals(1, sakura.size(), "Yae Sakura persists off field");
        assertEquals("Sesshou Sakura Level 3", sakura.get(0).action.getName(),
                "Yae three-Sakura strike level");

        sim.setActiveCharacter(CharacterId.YAE_MIKO);
        perform(sim, CharacterActionKey.SKILL);
        assertClose(293.0 / 60.0, sim.getCurrentTime(), EPS,
                "Yae fourth Skill waits then completes");
        assertEquals(3, yae.getSakuraCount(sim.getCurrentTime()),
                "Yae fourth placement replaces oldest Sakura");
        sakura.clear();
        advanceTo(sim, 320.0 / 60.0);
        assertEquals(0, sakura.size(),
                "Yae replaced Sakura frame-296 timer is stale");
    }

    private static void testBurstConsumptionTimingCooldownAndC1() {
        YaeMiko yae = yaeAtConstellation(1);
        CombatSimulator sim = simulatorWith(yae);
        List<ActionRecord> sakura = captureNamedActions(sim, "Sesshou Sakura");
        List<ActionRecord> initial = captureNamedActions(sim, "Tenko Kenshin");
        List<ActionRecord> tenko = captureNamedActions(
                sim, "Tenko Thunderbolt");

        perform(sim, CharacterActionKey.SKILL);
        perform(sim, CharacterActionKey.SKILL);
        perform(sim, CharacterActionKey.SKILL);
        double burstCast = sim.getCurrentTime();
        perform(sim, CharacterActionKey.BURST);
        assertClose(burstCast + 114.0 / 60.0,
                sim.getCurrentTime(), EPS, "Yae Burst action duration");
        assertEquals(0, yae.getSakuraCount(sim.getCurrentTime()),
                "Yae Burst consumes Sakura immediately");
        assertEquals(0, sakura.size(),
                "Yae consumed Sakura pending ticks are stale");
        assertEquals(1, initial.size(), "Yae Burst initial hit count");
        assertClose(burstCast + 100.0 / 60.0, initial.get(0).time, EPS,
                "Yae Burst initial hitmark");
        assertEquals(ICDType.None, initial.get(0).action.getICDType(),
                "Yae Burst has no ICD");
        assertClose(0.0, yae.getCurrentEnergy(), EPS,
                "Yae Burst spends 90 Energy");
        assertClose(burstCast + 22.0,
                yae.getBurstCooldownEndTime(), EPS,
                "Yae Burst cooldown");
        assertClose(0.0, yae.getSkillCDRemaining(sim.getCurrentTime()), EPS,
                "Yae A1 restores one charge per consumed Sakura");

        advanceTo(sim, burstCast + 202.0 / 60.0);
        assertEquals(3, tenko.size(), "Yae creates one Tenko per Sakura");
        assertClose(burstCast + 154.0 / 60.0, tenko.get(0).time, EPS,
                "Yae first Tenko hitmark");
        assertClose(24.0 / 60.0,
                tenko.get(1).time - tenko.get(0).time, EPS,
                "Yae Tenko interval");
        assertClose(24.0, yae.getCurrentEnergy(), EPS,
                "Yae C1 restores eight flat Energy per Tenko");
        assertClose(24.0, yae.getTotalFlatEnergy(), EPS,
                "Yae C1 records flat Energy");
    }

    private static void testA4UsesLiveElementalMastery() {
        YaeMiko activeA4 = yaeAtConstellation(0);
        CombatSimulator activeSim = simulatorWith(activeA4);
        List<ActionRecord> activeHits = captureNamedActions(
                activeSim, "Sesshou Sakura");
        perform(activeSim, CharacterActionKey.SKILL);
        activeA4.addBuff(new SimpleBuff(
                "Late EM", 10.0, activeSim.getCurrentTime(),
                stats -> stats.add(StatType.ELEMENTAL_MASTERY, 100.0)));
        advanceTo(activeSim, 120.0 / 60.0);

        TalentDataSource noA4Data = (character, key, defaultValue) ->
                "A4 EM Skill DMG Ratio".equals(key) ? 0.0 : defaultValue;
        YaeMiko noA4 = new YaeMiko(null, null, noA4Data, 0);
        CombatSimulator noA4Sim = simulatorWith(noA4);
        List<ActionRecord> noA4Hits = captureNamedActions(
                noA4Sim, "Sesshou Sakura");
        perform(noA4Sim, CharacterActionKey.SKILL);
        noA4.addBuff(new SimpleBuff(
                "Late EM", 10.0, noA4Sim.getCurrentTime(),
                stats -> stats.add(StatType.ELEMENTAL_MASTERY, 100.0)));
        advanceTo(noA4Sim, 120.0 / 60.0);

        assertClose(1.15,
                activeHits.get(0).damage / noA4Hits.get(0).damage,
                EPS,
                "Yae A4 reads final EM at Sakura impact");
    }

    private static void testConstellationsTwoThroughSix() {
        YaeMiko c2 = yaeAtConstellation(2);
        CombatSimulator c2Sim = simulatorWith(c2);
        List<ActionRecord> c2Hits = captureNamedActions(c2Sim, "Sesshou Sakura");
        perform(c2Sim, CharacterActionKey.SKILL);
        assertEquals(2, c2.getCurrentSakuraLevel(c2Sim.getCurrentTime()),
                "Yae C2 starts the first Sakura at level two");
        perform(c2Sim, CharacterActionKey.SKILL);
        perform(c2Sim, CharacterActionKey.SKILL);
        advanceTo(c2Sim, 120.0 / 60.0);
        assertEquals(4, c2.getCurrentSakuraLevel(c2Sim.getCurrentTime()),
                "Yae C2 raises three-Sakura level to four");
        assertClose(2.0145, c2Hits.get(0).action.getDamagePercent(), EPS,
                "Yae C2 uses talent-nine level-four multiplier");

        YaeMiko c3 = yaeAtConstellation(3);
        CombatSimulator c3Sim = simulatorWith(c3);
        List<ActionRecord> c3Hits = captureNamedActions(c3Sim, "Sesshou Sakura");
        perform(c3Sim, CharacterActionKey.SKILL);
        perform(c3Sim, CharacterActionKey.SKILL);
        perform(c3Sim, CharacterActionKey.SKILL);
        advanceTo(c3Sim, 120.0 / 60.0);
        assertClose(2.37, c3Hits.get(0).action.getDamagePercent(), EPS,
                "Yae C3 raises Sakura talent by three");

        YaeMiko c4 = yaeAtConstellation(4);
        CombatSimulator c4Sim = simulatorWith(c4);
        TestCharacter electro = new TestCharacter(
                CharacterId.FISCHL, Element.ELECTRO);
        TestCharacter geo = new TestCharacter(
                CharacterId.NOELLE, Element.GEO);
        c4Sim.addCharacter(electro);
        c4Sim.addCharacter(geo);
        perform(c4Sim, CharacterActionKey.SKILL);
        advanceTo(c4Sim, 120.0 / 60.0);
        assertTrue(hasApplicableBuff(
                c4Sim, electro, BuffId.YAE_MIKO_C4_ELECTRO_DMG_BONUS),
                "Yae C4 emits typed team buff");
        assertClose(0.20,
                applicableStats(c4Sim, electro).get(
                        StatType.ELECTRO_DMG_BONUS),
                EPS,
                "Yae C4 buffs Electro teammate");
        assertClose(0.0,
                applicableStats(c4Sim, geo).get(StatType.ELECTRO_DMG_BONUS),
                EPS,
                "Yae C4 excludes non-Electro teammate");
        double c4Start = c4Sim.getCurrentTime();
        perform(c4Sim, CharacterActionKey.BURST);
        advanceTo(c4Sim, c4Start + 5.0 - 0.001);
        assertTrue(hasApplicableBuff(
                c4Sim, electro, BuffId.YAE_MIKO_C4_ELECTRO_DMG_BONUS),
                "Yae C4 remains active before five seconds");
        c4Sim.advanceTime(0.002);
        assertTrue(!hasApplicableBuff(
                c4Sim, electro, BuffId.YAE_MIKO_C4_ELECTRO_DMG_BONUS),
                "Yae C4 expires at five-second half-open boundary");

        YaeMiko c5 = yaeAtConstellation(5);
        CombatSimulator c5Sim = simulatorWith(c5);
        List<ActionRecord> c5Initial = captureNamedActions(
                c5Sim, "Tenko Kenshin");
        List<ActionRecord> c5Tenko = captureNamedActions(
                c5Sim, "Tenko Thunderbolt");
        perform(c5Sim, CharacterActionKey.SKILL);
        perform(c5Sim, CharacterActionKey.BURST);
        advanceTo(c5Sim, 37.0 / 60.0 + 154.0 / 60.0);
        assertClose(5.20, c5Initial.get(0).action.getDamagePercent(), EPS,
                "Yae C5 raises Burst initial talent");
        assertClose(6.67632, c5Tenko.get(0).action.getDamagePercent(), EPS,
                "Yae C5 raises Tenko talent");

        YaeMiko c6 = yaeAtConstellation(6);
        CombatSimulator c6Sim = simulatorWith(c6);
        List<ActionRecord> c6Hits = captureNamedActions(c6Sim, "Sesshou Sakura");
        perform(c6Sim, CharacterActionKey.SKILL);
        advanceTo(c6Sim, 120.0 / 60.0);
        assertClose(0.60, c6Hits.get(0).action.getDefenseIgnore(), EPS,
                "Yae C6 Sakura DEF ignore");
    }

    private static void testStaleTimersIndependentInstancesAndBinding() {
        YaeMiko first = yaeAtConstellation(0);
        YaeMiko second = yaeAtConstellation(0);
        CombatSimulator firstSim = simulatorWith(first);
        CombatSimulator secondSim = simulatorWith(second);
        List<ActionRecord> firstHits = captureNamedActions(
                firstSim, "Sesshou Sakura");
        List<ActionRecord> secondHits = captureNamedActions(
                secondSim, "Sesshou Sakura");
        perform(firstSim, CharacterActionKey.SKILL);
        perform(secondSim, CharacterActionKey.SKILL);
        advanceTo(firstSim, 120.0 / 60.0);
        assertEquals(1, firstHits.size(), "Yae first instance ticks");
        assertEquals(0, secondHits.size(),
                "Yae second instance does not share timer state");
        advanceTo(secondSim, 120.0 / 60.0);
        assertEquals(1, secondHits.size(), "Yae second instance ticks");

        CombatSimulator rejected = new CombatSimulator();
        rejected.setEnemy(new Enemy(90));
        assertThrows(
                IllegalStateException.class,
                () -> rejected.addCharacter(first),
                "Yae instance rejects cross-simulator reuse");
    }

    private static void testInvalidConstellationAndAction() {
        assertThrows(
                IllegalArgumentException.class,
                () -> yaeAtConstellation(-1),
                "Yae rejects negative constellation");
        assertThrows(
                IllegalArgumentException.class,
                () -> yaeAtConstellation(7),
                "Yae rejects constellation above six");
        YaeMiko yae = yaeAtConstellation(0);
        CombatSimulator sim = simulatorWith(yae);
        assertThrows(
                IllegalArgumentException.class,
                () -> perform(sim, CharacterActionKey.DASH),
                "Yae rejects unsupported Dash action");
    }

    private static YaeMiko yaeAtConstellation(int constellation) {
        TalentDataSource data = (character, key, defaultValue) -> defaultValue;
        return new YaeMiko(null, null, data, constellation);
    }

    private static CombatSimulator simulatorWith(YaeMiko yae) {
        CombatSimulator sim = new CombatSimulator();
        sim.setLoggingEnabled(false);
        sim.setEnemy(new Enemy(90));
        sim.addCharacter(yae);
        return sim;
    }

    private static void perform(
            CombatSimulator sim,
            CharacterActionKey key) {
        sim.performAction(
                CharacterId.YAE_MIKO,
                CharacterActionRequest.of(key));
    }

    private static void advanceTo(CombatSimulator sim, double targetTime) {
        double delta = targetTime - sim.getCurrentTime();
        if (delta > 0.0) {
            sim.advanceTime(delta);
        }
    }

    private static List<ActionRecord> captureYaeActions(
            CombatSimulator sim) {
        List<ActionRecord> records = new ArrayList<>();
        sim.addDamageListener((actor, action, damage, time) -> {
            if (actor.getCharacterId() == CharacterId.YAE_MIKO) {
                records.add(new ActionRecord(action, damage, time));
            }
        });
        return records;
    }

    private static List<ActionRecord> captureNamedActions(
            CombatSimulator sim,
            String namePrefix) {
        List<ActionRecord> records = new ArrayList<>();
        sim.addDamageListener((actor, action, damage, time) -> {
            if (actor.getCharacterId() == CharacterId.YAE_MIKO
                    && action.getName().startsWith(namePrefix)) {
                records.add(new ActionRecord(action, damage, time));
            }
        });
        return records;
    }

    private static boolean hasApplicableBuff(
            CombatSimulator sim,
            Character target,
            BuffId id) {
        for (Buff buff : sim.getApplicableBuffs(target)) {
            if (buff.getId() == id && !buff.isExpired(sim.getCurrentTime())) {
                return true;
            }
        }
        return false;
    }

    private static StatsContainer applicableStats(
            CombatSimulator sim,
            Character target) {
        StatsContainer stats = target.getEffectiveStats(sim.getCurrentTime());
        for (Buff buff : sim.getApplicableBuffs(target)) {
            buff.apply(stats, sim.getCurrentTime());
        }
        return stats;
    }

    private static void assertCsvShape(Path path, int expectedRows)
            throws IOException {
        List<String> lines = Files.readAllLines(path);
        assertEquals(
                "Character,AbilityType,Key,Level,Value1,Value2",
                lines.get(0), path + " header");
        assertEquals(expectedRows + 1, lines.size(), path + " row count");
        for (int i = 1; i < lines.size(); i++) {
            String[] columns = lines.get(i).split(",", -1);
            assertEquals(6, columns.length,
                    path + " column count at line " + (i + 1));
            assertEquals("Yae Miko", columns[0],
                    path + " character alignment at line " + (i + 1));
            Double.parseDouble(columns[3]);
            Double.parseDouble(columns[4]);
            Double.parseDouble(columns[5]);
        }
    }

    private static void assertClose(
            double expected,
            double actual,
            double tolerance,
            String message) {
        if (Math.abs(expected - actual) > tolerance) {
            throw new AssertionError(message + ": expected " + expected
                    + " but got " + actual);
        }
    }

    private static void assertEquals(
            Object expected,
            Object actual,
            String message) {
        if (!java.util.Objects.equals(expected, actual)) {
            throw new AssertionError(message + ": expected " + expected
                    + " but got " + actual);
        }
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static void assertThrows(
            Class<? extends Throwable> expectedType,
            Runnable action,
            String message) {
        try {
            action.run();
        } catch (Throwable thrown) {
            if (expectedType.isInstance(thrown)) {
                return;
            }
            throw new AssertionError(message + ": expected "
                    + expectedType.getSimpleName() + " but got "
                    + thrown.getClass().getSimpleName(), thrown);
        }
        throw new AssertionError(message + ": expected "
                + expectedType.getSimpleName());
    }

    private static final class ActionRecord {
        private final AttackAction action;
        private final double damage;
        private final double time;

        private ActionRecord(AttackAction action, double damage, double time) {
            this.action = action;
            this.damage = damage;
            this.time = time;
        }
    }

    /** Minimal party member for switch and typed team-buff checks. */
    private static final class TestCharacter extends Character {
        private TestCharacter(CharacterId id, Element element) {
            this.name = id.getDisplayName();
            this.characterId = id;
            this.element = element;
            baseStats.set(StatType.BASE_HP, 1.0);
            baseStats.set(StatType.BASE_ATK, 1.0);
            baseStats.set(StatType.BASE_DEF, 1.0);
        }

        @Override
        public void applyPassive(StatsContainer stats) {
        }

        @Override
        public double getEnergyCost() {
            return 60.0;
        }
    }
}

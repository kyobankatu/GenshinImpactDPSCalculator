package sample;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import mechanics.data.TalentDataManager;
import model.character.Chiori;
import model.character.Noelle;
import model.entity.Character;
import model.entity.Enemy;
import model.type.CharacterId;
import model.type.CharacterRegion;
import model.type.Element;
import model.type.StatType;
import simulation.CombatSimulator;
import simulation.SimulatorSnapshot;
import simulation.action.AttackAction;
import simulation.action.CharacterActionKey;
import simulation.action.CharacterActionRequest;
import simulation.action.SkillActionMode;

/** Focused checks for Chiori's fixed-target Tamoto offensive slice. */
public final class ChioriRegressionTest {
    private static final double FRAME = 1.0 / 60.0;
    private static final double EPSILON = 1e-8;

    private ChioriRegressionTest() {
    }

    /** Runs identity, basics, Tamoto, constellation, and rollback checks. */
    public static void main(String[] args) throws Exception {
        testIdentityDataAndGuards();
        testBasicsTailoringAndC6();
        testTamotoCadenceParticlesAndC1();
        testBurstAndKinuConstellations();
        testSnapshotRestoreAndReuseGuards();
        System.out.println("ChioriRegressionTest passed");
    }

    private static void testIdentityDataAndGuards() throws IOException {
        Chiori chiori = chiori(0, 0.9);
        assertEquals(CharacterId.CHIORI, chiori.getCharacterId(),
                "Chiori typed identity");
        assertEquals(CharacterId.CHIORI, CharacterId.fromNumericId(80),
                "Chiori numeric identity");
        assertEquals(CharacterId.CHIORI, CharacterId.fromName("Chiori"),
                "Chiori name identity");
        assertEquals(CharacterRegion.INAZUMA,
                CharacterId.CHIORI.getRegion(), "Chiori region");
        assertEquals(Element.GEO, chiori.getElement(), "Chiori element");
        assertClose(11438.0,
                chiori.getBaseStats().get(StatType.BASE_HP),
                "Chiori base HP");
        assertClose(323.0,
                chiori.getBaseStats().get(StatType.BASE_ATK),
                "Chiori base ATK");
        assertClose(953.0,
                chiori.getBaseStats().get(StatType.BASE_DEF),
                "Chiori base DEF");
        assertClose(0.242,
                chiori.getBaseStats().get(StatType.CRIT_RATE),
                "Chiori total base CRIT Rate");
        assertClose(50.0, chiori.getEnergyCost(), "Chiori Energy cost");
        assertClose(16.0, chiori.getSkillCD(), "Chiori Skill cooldown");
        assertClose(13.5, chiori.getBurstCD(), "Chiori Burst cooldown");
        for (int constellation = 0; constellation <= 6;
                constellation++) {
            assertEquals(constellation,
                    chiori(constellation, 0.9).getConstellation(),
                    "Chiori constellation " + constellation);
        }
        assertCsvShape(Path.of(
                "config/characters/Chiori/Chiori_Status.csv"), 20);
        assertCsvShape(Path.of(
                "config/characters/Chiori/Chiori_Multipliers.csv"), 20);
        assertThrows(IllegalArgumentException.class,
                () -> chiori(-1, 0.9),
                "Chiori rejects negative constellation");
        assertThrows(IllegalArgumentException.class,
                () -> chiori(7, 0.9),
                "Chiori rejects constellation above C6");
        assertThrows(IllegalArgumentException.class,
                () -> new Chiori(null, null,
                        TalentDataManager.getInstance(), 0, null),
                "Chiori rejects null random source");

        CombatSimulator simulator = simulatorWith(chiori);
        assertThrows(IllegalArgumentException.class,
                () -> chiori.onAction(null, simulator),
                "Chiori rejects null action");
        assertThrows(IllegalArgumentException.class,
                () -> simulator.performAction(
                        CharacterId.CHIORI,
                        CharacterActionRequest.skill(SkillActionMode.HOLD)),
                "Chiori rejects Hold Skill movement");
        assertThrows(IllegalArgumentException.class,
                () -> perform(simulator, CharacterActionKey.DASH),
                "Chiori rejects unsupported Dash");
    }

    private static void testBasicsTailoringAndC6() {
        Chiori c0 = chiori(0, 0.9);
        CombatSimulator simulator = simulatorWith(c0);
        List<ActionRecord> records = captureActions(simulator);
        for (int step = 0; step < 4; step++) {
            perform(simulator, CharacterActionKey.NORMAL);
        }
        assertEquals(5, startingWith(records, "Weaving Blade N").size(),
                "Chiori four-step Normal string has five hits");
        assertClose(0.558814,
                startingWith(records, "Weaving Blade N3").get(1)
                        .action.getDamagePercent(),
                "Chiori N3 second multiplier");

        records.clear();
        perform(simulator, CharacterActionKey.CHARGE);
        assertEquals(2,
                startingWith(records, "Weaving Blade Charged").size(),
                "Chiori Charged two-hit count");
        assertClose(0.997770,
                startingWith(records, "Weaving Blade Charged").get(0)
                        .action.getDamagePercent(),
                "Chiori Charged multiplier");
        records.clear();
        perform(simulator, CharacterActionKey.PLUNGE);
        assertClose(2.933586,
                named(records, "Weaving Blade High Plunge").get(0)
                        .action.getDamagePercent(),
                "Chiori high Plunge multiplier");

        Chiori c6 = chiori(6, 0.9);
        CombatSimulator c6Simulator = simulatorWith(c6);
        List<ActionRecord> c6Records = captureActions(c6Simulator);
        perform(c6Simulator, CharacterActionKey.SKILL);
        perform(c6Simulator, CharacterActionKey.NORMAL);
        ActionRecord infused = startingWith(
                c6Records, "Weaving Blade N1").get(0);
        assertEquals(Element.GEO, infused.action.getElement(),
                "Chiori A1 Tailoring infuses Normal with Geo");
        assertClose(953.0 * 2.35,
                infused.action.getAdditiveBaseDmgBonus(),
                "Chiori C6 adds DEF to Normal base damage");
        assertTrue(c6.isGeoInfusionActive(c6Simulator.getCurrentTime()),
                "Chiori Tailoring remains active after trigger Normal");
        assertClose(4.0,
                c6.getSkillCooldownEndTime(),
                "Chiori C6 reduces current Skill cooldown by twelve seconds");
    }

    private static void testTamotoCadenceParticlesAndC1() {
        Chiori c0 = chiori(0, 0.1);
        CombatSimulator simulator = simulatorWith(c0);
        List<ActionRecord> records = captureActions(simulator);
        List<Double> particles = captureGeoParticles(simulator);
        perform(simulator, CharacterActionKey.SKILL);
        ActionRecord sweep = named(
                records, "Fluttering Hasode (Upward Sweep)").get(0);
        assertClose(21.0 * FRAME, sweep.time,
                "Chiori upward sweep hit timing");
        assertClose(2.537760, sweep.action.getDamagePercent(),
                "Chiori C0 upward sweep ATK ratio");
        assertClose(953.0 * 3.172200,
                sweep.action.getAdditiveBaseDmgBonus(),
                "Chiori upward sweep DEF portion");
        advanceTo(simulator, 18.0);
        List<ActionRecord> tamoto = named(
                records, "Fluttering Hasode (Tamoto)");
        assertEquals(5, tamoto.size(),
                "Chiori Tamoto attacks five times in 17.5 seconds");
        assertClose(3.6, tamoto.get(1).time - tamoto.get(0).time,
                "Chiori Tamoto interval");
        assertEquals(5, particles.size(),
                "Chiori Tamoto particle gate admits each 3.6-second hit");
        for (double particleCount : particles) {
            assertClose(2.0, particleCount,
                    "Chiori low draw emits two particles");
        }
        assertTrue(!c0.isTamotoActive(18.0),
                "Chiori Tamoto expires at the sourced boundary");

        Chiori c1 = chiori(1, 0.9);
        Noelle noelle = new Noelle(null, null);
        CombatSimulator c1Simulator = simulatorWith(c1, noelle);
        List<ActionRecord> c1Records = captureActions(c1Simulator);
        perform(c1Simulator, CharacterActionKey.SKILL);
        advanceTo(c1Simulator, 18.0);
        assertEquals(5,
                named(c1Records, "Fluttering Hasode (Tamoto C1)").size(),
                "Chiori C1 adds a second Tamoto with another Geo member");

        Chiori invalidRandom = chiori(0, 1.0);
        CombatSimulator invalidSimulator = simulatorWith(invalidRandom);
        perform(invalidSimulator, CharacterActionKey.SKILL);
        assertThrows(IllegalStateException.class,
                () -> advanceTo(invalidSimulator, 1.0),
                "Chiori rejects out-of-range particle random draw");
    }

    private static void testBurstAndKinuConstellations() {
        Chiori c2 = chiori(2, 0.9);
        CombatSimulator simulator = simulatorWith(c2);
        List<ActionRecord> records = captureActions(simulator);
        c2.restoreCurrentEnergy(50.0);
        perform(simulator, CharacterActionKey.BURST);
        assertClose(0.0, c2.getCurrentEnergy(),
                "Chiori Burst spends fifty Energy");
        ActionRecord burst = named(records, "Hiyoku: Twin Blades").get(0);
        assertClose(92.0 * FRAME, burst.time,
                "Chiori Burst hit timing");
        assertClose(4.357440, burst.action.getDamagePercent(),
                "Chiori C2 Burst ATK ratio");
        assertClose(953.0 * 5.446800,
                burst.action.getAdditiveBaseDmgBonus(),
                "Chiori C2 Burst DEF ratio");
        advanceTo(simulator, 12.0);
        assertEquals(3, startingWith(
                records, "Fluttering Hasode (Kinu C2)").size(),
                "Chiori C2 creates three Kinu attacks over ten seconds");

        Chiori c5 = chiori(5, 0.9);
        CombatSimulator c5Simulator = simulatorWith(c5);
        List<ActionRecord> c5Records = captureActions(c5Simulator);
        perform(c5Simulator, CharacterActionKey.SKILL);
        assertClose(2.985600,
                named(c5Records, "Fluttering Hasode (Upward Sweep)")
                        .get(0).action.getDamagePercent(),
                "Chiori C3 raises Skill talent");
        c5.restoreCurrentEnergy(50.0);
        perform(c5Simulator, CharacterActionKey.BURST);
        assertClose(5.126400,
                named(c5Records, "Hiyoku: Twin Blades")
                        .get(0).action.getDamagePercent(),
                "Chiori C5 raises Burst talent");
    }

    private static void testSnapshotRestoreAndReuseGuards() {
        Chiori chiori = chiori(1, 0.9);
        Noelle noelle = new Noelle(null, null);
        CombatSimulator simulator = simulatorWith(chiori, noelle);
        List<ActionRecord> records = captureActions(simulator);
        perform(simulator, CharacterActionKey.SKILL);
        SimulatorSnapshot snapshot = simulator.saveSnapshot();
        advanceTo(simulator, 18.0);
        int expectedHits = startingWith(
                records, "Fluttering Hasode (Tamoto").size();
        double expectedDamage = simulator.getTotalDamage();
        assertEquals(10, expectedHits,
                "Chiori original branch resolves both Tamoto sequences");

        simulator.restoreSnapshot(snapshot);
        records.clear();
        advanceTo(simulator, 18.0);
        assertEquals(expectedHits, startingWith(
                records, "Fluttering Hasode (Tamoto").size(),
                "Chiori restore resolves each future Tamoto hit once");
        assertClose(expectedDamage, simulator.getTotalDamage(),
                "Chiori restore preserves total damage");

        simulator.restoreSnapshot(snapshot);
        simulator.restoreSnapshot(snapshot);
        records.clear();
        advanceTo(simulator, 18.0);
        assertEquals(expectedHits, startingWith(
                records, "Fluttering Hasode (Tamoto").size(),
                "Chiori repeated restore keeps one future sequence");

        Chiori foreign = chiori(0, 0.9);
        assertTrue(!chiori.acceptsCharacterState(
                foreign.captureCharacterState()),
                "Chiori rejects foreign state");
        assertThrows(IllegalArgumentException.class,
                () -> chiori.restoreCharacterState(null, simulator),
                "Chiori rejects null state");
        assertThrows(IllegalStateException.class,
                () -> simulatorWith(chiori),
                "Chiori rejects cross-simulator reuse");
    }

    private static Chiori chiori(
            int constellation,
            double randomDraw) {
        return new Chiori(
                null,
                null,
                TalentDataManager.getInstance(),
                constellation,
                () -> randomDraw);
    }

    private static CombatSimulator simulatorWith(Character... characters) {
        CombatSimulator simulator = new CombatSimulator();
        simulator.setLoggingEnabled(false);
        Enemy enemy = new Enemy(90);
        enemy.setRes(StatType.PHYSICAL_DMG_BONUS, 0.0);
        enemy.setRes(StatType.GEO_DMG_BONUS, 0.0);
        simulator.setEnemy(enemy);
        for (Character character : characters) {
            simulator.addCharacter(character);
        }
        return simulator;
    }

    private static void perform(
            CombatSimulator simulator,
            CharacterActionKey key) {
        simulator.performAction(
                CharacterId.CHIORI, CharacterActionRequest.of(key));
    }

    private static List<ActionRecord> captureActions(
            CombatSimulator simulator) {
        List<ActionRecord> records = new ArrayList<>();
        simulator.addDamageListener((actor, action, damage, time) -> {
            if (actor.getCharacterId() == CharacterId.CHIORI) {
                records.add(new ActionRecord(action, time));
            }
        });
        return records;
    }

    private static List<Double> captureGeoParticles(
            CombatSimulator simulator) {
        List<Double> records = new ArrayList<>();
        simulator.addParticleListener((element, count, time) -> {
            if (element == Element.GEO) {
                records.add(count);
            }
        });
        return records;
    }

    private static List<ActionRecord> named(
            List<ActionRecord> records,
            String name) {
        List<ActionRecord> matches = new ArrayList<>();
        for (ActionRecord record : records) {
            if (name.equals(record.action.getName())) {
                matches.add(record);
            }
        }
        return matches;
    }

    private static List<ActionRecord> startingWith(
            List<ActionRecord> records,
            String prefix) {
        List<ActionRecord> matches = new ArrayList<>();
        for (ActionRecord record : records) {
            if (record.action.getName().startsWith(prefix)) {
                matches.add(record);
            }
        }
        return matches;
    }

    private static void advanceTo(
            CombatSimulator simulator,
            double targetTime) {
        simulator.advanceTime(targetTime - simulator.getCurrentTime());
    }

    private static void assertCsvShape(Path path, int expectedRows)
            throws IOException {
        List<String> lines = Files.readAllLines(path);
        assertEquals("Character,AbilityType,Key,Level,Value1,Value2",
                lines.get(0), path + " header");
        assertEquals(expectedRows + 1, lines.size(), path + " row count");
        for (int index = 1; index < lines.size(); index++) {
            assertEquals(6, lines.get(index).split(",", -1).length,
                    path + " columns at line " + (index + 1));
            assertTrue(lines.get(index).startsWith("Chiori,"),
                    path + " identity at line " + (index + 1));
        }
    }

    private static void assertClose(
            double expected,
            double actual,
            String message) {
        if (Math.abs(expected - actual) > EPSILON) {
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
            Class<? extends Throwable> expected,
            ThrowingRunnable action,
            String message) {
        try {
            action.run();
        } catch (Throwable throwable) {
            if (expected.isInstance(throwable)) {
                return;
            }
            throw new AssertionError(message + ": expected "
                    + expected.getSimpleName() + " but got "
                    + throwable.getClass().getSimpleName(), throwable);
        }
        throw new AssertionError(message + ": expected "
                + expected.getSimpleName());
    }

    private static final class ActionRecord {
        private final AttackAction action;
        private final double time;

        private ActionRecord(AttackAction action, double time) {
            this.action = action;
            this.time = time;
        }
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }
}

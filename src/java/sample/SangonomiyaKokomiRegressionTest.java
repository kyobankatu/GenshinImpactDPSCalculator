package sample;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import mechanics.data.TalentDataManager;
import model.character.SangonomiyaKokomi;
import model.entity.Character;
import model.entity.Enemy;
import model.stats.StatsContainer;
import model.type.ActionType;
import model.type.CharacterId;
import model.type.CharacterRegion;
import model.type.Element;
import model.type.StatType;
import simulation.CombatSimulator;
import simulation.SimulatorSnapshot;
import simulation.action.AttackAction;
import simulation.action.CharacterActionKey;
import simulation.action.CharacterActionRequest;

/** Focused checks for Kokomi's represented Bake-Kurage offensive slice. */
public final class SangonomiyaKokomiRegressionTest {
    private static final double FRAME = 1.0 / 60.0;
    private static final double EPSILON = 1e-8;

    private SangonomiyaKokomiRegressionTest() {
    }

    /** Runs identity, action, deployable, Burst, and rollback checks. */
    public static void main(String[] args) throws Exception {
        testIdentityStatsDataAndGuards();
        testNormalsAndCharged();
        testBakeKurageCadenceParticlesAndC5();
        testBurstBonusesA1AndConstellations();
        testSnapshotRestoreAndReuseGuards();
        System.out.println("SangonomiyaKokomiRegressionTest passed");
    }

    private static void testIdentityStatsDataAndGuards()
            throws IOException {
        SangonomiyaKokomi kokomi = kokomi(0, 0.25);
        assertEquals(CharacterId.SANGONOMIYA_KOKOMI,
                kokomi.getCharacterId(), "Kokomi typed identity");
        assertEquals(CharacterId.SANGONOMIYA_KOKOMI,
                CharacterId.fromNumericId(68),
                "Kokomi numeric identity");
        assertEquals(CharacterId.SANGONOMIYA_KOKOMI,
                CharacterId.fromName("Sangonomiya Kokomi"),
                "Kokomi display-name identity");
        assertEquals(CharacterRegion.INAZUMA,
                CharacterId.SANGONOMIYA_KOKOMI.getRegion(),
                "Kokomi region");
        assertEquals(Element.HYDRO, kokomi.getElement(), "Kokomi element");
        assertClose(13471.0,
                kokomi.getBaseStats().get(StatType.BASE_HP),
                "Kokomi base HP");
        assertClose(234.0,
                kokomi.getBaseStats().get(StatType.BASE_ATK),
                "Kokomi base ATK");
        assertClose(657.0,
                kokomi.getBaseStats().get(StatType.BASE_DEF),
                "Kokomi base DEF");
        assertClose(0.288,
                kokomi.getBaseStats().get(StatType.HYDRO_DMG_BONUS),
                "Kokomi ascension Hydro bonus");
        StatsContainer passive = kokomi.getEffectiveStats(0.0);
        assertClose(-0.95, passive.get(StatType.CRIT_RATE),
                "Flawless Strategy subtracts 100% CRIT Rate");
        assertClose(0.25, passive.get(StatType.HEALING_BONUS),
                "Flawless Strategy grants Healing Bonus");
        assertClose(70.0, kokomi.getEnergyCost(), "Kokomi Energy cost");
        assertClose(20.0, kokomi.getSkillCD(), "Kokomi Skill cooldown");
        assertClose(18.0, kokomi.getBurstCD(), "Kokomi Burst cooldown");
        for (int constellation = 0; constellation <= 6;
                constellation++) {
            assertEquals(constellation,
                    kokomi(constellation, 0.25).getConstellation(),
                    "Kokomi constellation " + constellation);
        }
        assertCsvShape(Path.of(
                "config/characters/SangonomiyaKokomi/"
                        + "SangonomiyaKokomi_Status.csv"), 28);
        assertCsvShape(Path.of(
                "config/characters/SangonomiyaKokomi/"
                        + "SangonomiyaKokomi_Multipliers.csv"), 14);
        assertThrows(IllegalArgumentException.class,
                () -> kokomi(-1, 0.25),
                "Kokomi rejects negative constellation");
        assertThrows(IllegalArgumentException.class,
                () -> kokomi(7, 0.25),
                "Kokomi rejects constellation above C6");
        assertThrows(IllegalArgumentException.class,
                () -> new SangonomiyaKokomi(
                        null,
                        null,
                        TalentDataManager.getInstance(),
                        0,
                        null),
                "Kokomi rejects null particle randomness");

        CombatSimulator simulator = simulatorWith(kokomi);
        assertThrows(IllegalArgumentException.class,
                () -> kokomi.onAction(null, simulator),
                "Kokomi rejects null action");
        assertThrows(IllegalArgumentException.class,
                () -> perform(simulator, CharacterActionKey.PLUNGE),
                "Kokomi rejects unpinned Plunge data");
    }

    private static void testNormalsAndCharged() {
        SangonomiyaKokomi kokomi = kokomi(0, 0.75);
        CombatSimulator simulator = simulatorWith(kokomi);
        List<ActionRecord> records = captureActions(simulator);
        double[] expected = { 1.162392, 1.046153, 1.603195 };
        for (int step = 0; step < 3; step++) {
            perform(simulator, CharacterActionKey.NORMAL);
        }
        assertEquals(3, records.size(), "Kokomi Normal hit count");
        for (int index = 0; index < expected.length; index++) {
            assertClose(expected[index],
                    records.get(index).action.getDamagePercent(),
                    "Kokomi Normal multiplier " + index);
            assertEquals(Element.HYDRO,
                    records.get(index).action.getElement(),
                    "Kokomi Normal element " + index);
            assertEquals(ActionType.NORMAL,
                    records.get(index).action.getActionType(),
                    "Kokomi Normal category " + index);
            assertClose(0.0,
                    records.get(index).action.getAdditiveBaseDmgBonus(),
                    "Kokomi non-Burst Normal has no HP addition " + index);
        }

        records.clear();
        perform(simulator, CharacterActionKey.CHARGE);
        assertEquals(1, records.size(), "Kokomi Charged hit count");
        assertClose(2.521440, records.get(0).action.getDamagePercent(),
                "Kokomi Charged multiplier");
        assertClose(0.0,
                records.get(0).action.getAdditiveBaseDmgBonus(),
                "Kokomi non-Burst Charged has no HP addition");
    }

    private static void testBakeKurageCadenceParticlesAndC5() {
        SangonomiyaKokomi c5 = kokomi(5, 0.25);
        CombatSimulator simulator = simulatorWith(c5);
        List<ActionRecord> records = captureActions(simulator);
        List<Double> particles = captureHydroParticles(simulator);
        perform(simulator, CharacterActionKey.SKILL);
        assertTrue(c5.isBakeKurageActive(simulator.getCurrentTime()),
                "Kokomi Skill creates Bake-Kurage");
        assertClose(20.0 * FRAME, c5.getLastSkillTime(),
                "Kokomi Skill cooldown starts at frame twenty");
        assertClose(751.0 * FRAME,
                c5.getBakeKurageExpirationTime(),
                "Bake-Kurage initial exact duration");
        advanceTo(simulator, 15.0);
        List<ActionRecord> ticks = named(records, "Bake-Kurage");
        assertEquals(7, ticks.size(),
                "Bake-Kurage resolves seven source-defined ticks");
        assertClose(2.183808, ticks.get(0).action.getDamagePercent(),
                "C5 raises Bake-Kurage multiplier");
        assertClose(150.0 * FRAME,
                ticks.get(1).time,
                "Bake-Kurage second tick uses 150-frame timing");
        for (int index = 2; index < ticks.size(); index++) {
            assertClose(2.0,
                    ticks.get(index).time - ticks.get(index - 1).time,
                    "Bake-Kurage repeating interval " + index);
        }
        assertEquals(7, particles.size(),
                "Each successful Bake-Kurage draw creates one packet");
        for (double count : particles) {
            assertClose(1.0, count,
                    "Bake-Kurage packet contains one particle");
        }
        assertTrue(!c5.isBakeKurageActive(751.0 * FRAME),
                "Bake-Kurage expires at half-open boundary");
    }

    private static void testBurstBonusesA1AndConstellations() {
        SangonomiyaKokomi c5 = kokomi(5, 0.75);
        CombatSimulator simulator = simulatorWith(c5);
        List<ActionRecord> records = captureActions(simulator);
        perform(simulator, CharacterActionKey.BURST);
        AttackAction burst = named(records, "Nereid's Ascension")
                .get(0).action;
        assertClose(0.208320, burst.getDamagePercent(),
                "C3 raises Nereid's Ascension multiplier");
        assertEquals(StatType.BASE_HP, burst.getScalingStat(),
                "Nereid's Ascension scales from Max HP");
        assertClose(0.0, c5.getCurrentEnergy(),
                "Nereid's Ascension spends Energy at frame 57");
        assertTrue(c5.isBurstActive(simulator.getCurrentTime()),
                "Ceremonial Garment remains active after animation");
        assertClose(0.10,
                c5.getEffectiveStats(simulator.getCurrentTime())
                        .get(StatType.NORMAL_ATTACK_SPD),
                "C4 grants Normal Attack speed during Burst");

        double normalStart = simulator.getCurrentTime();
        perform(simulator, CharacterActionKey.NORMAL);
        assertClose(30.0 * FRAME / 1.10,
                simulator.getCurrentTime() - normalStart,
                "C4 accelerates Normal action timing");
        AttackAction normal = named(records, "The Shape of Water N1")
                .get(0).action;
        assertClose(1809.1553, normal.getAdditiveBaseDmgBonus(),
                "Burst Normal includes C3 and A4 Max-HP additions");
        assertClose(0.8, c5.getCurrentEnergy(),
                "C4 Normal hit restores 0.8 flat Energy");

        perform(simulator, CharacterActionKey.NORMAL);
        perform(simulator, CharacterActionKey.NORMAL);
        AttackAction c1 = named(records, "At Water's Edge C1")
                .get(0).action;
        assertClose(4041.3, c1.getAdditiveBaseDmgBonus(),
                "C1 adds thirty percent Max HP after N3");

        SangonomiyaKokomi chargedOwner = kokomi(3, 0.75);
        CombatSimulator chargedSimulator = simulatorWith(chargedOwner);
        List<ActionRecord> chargedRecords = captureActions(chargedSimulator);
        perform(chargedSimulator, CharacterActionKey.BURST);
        perform(chargedSimulator, CharacterActionKey.CHARGE);
        assertClose(2330.75242,
                named(chargedRecords, "The Shape of Water Charged")
                        .get(0).action.getAdditiveBaseDmgBonus(),
                "Burst Charged includes C3 and A4 Max-HP additions");

        SangonomiyaKokomi refreshOwner = kokomi(0, 0.75);
        TestCharacter ally = new TestCharacter(
                CharacterId.NOELLE, Element.GEO);
        CombatSimulator refreshSimulator = simulatorWith(
                refreshOwner, ally);
        perform(refreshSimulator, CharacterActionKey.SKILL);
        double originalExpiry = refreshOwner.getBakeKurageExpirationTime();
        perform(refreshSimulator, CharacterActionKey.BURST);
        assertTrue(refreshOwner.getBakeKurageExpirationTime()
                        > originalExpiry,
                "A1 refreshes active Bake-Kurage at frame 46");
        refreshSimulator.switchCharacter(CharacterId.NOELLE);
        assertTrue(!refreshOwner.isBurstActive(
                refreshSimulator.getCurrentTime()),
                "Switch-out ends Ceremonial Garment");
        assertTrue(refreshOwner.isBakeKurageActive(
                refreshSimulator.getCurrentTime()),
                "Switch-out preserves Bake-Kurage");
    }

    private static void testSnapshotRestoreAndReuseGuards() {
        SangonomiyaKokomi kokomi = kokomi(5, 0.25);
        CombatSimulator simulator = simulatorWith(kokomi);
        perform(simulator, CharacterActionKey.SKILL);
        SimulatorSnapshot snapshot = simulator.saveSnapshot();
        simulator.advanceTime(2.0);
        simulator.restoreSnapshot(snapshot);
        List<ActionRecord> records = captureActions(simulator);
        simulator.advanceTime(2.0);
        assertEquals(1, named(records, "Bake-Kurage").size(),
                "Restored second Bake-Kurage tick resolves once");

        SangonomiyaKokomi foreign = kokomi(0, 0.25);
        assertTrue(!kokomi.acceptsCharacterState(
                foreign.captureCharacterState()),
                "Kokomi rejects foreign snapshot payload");
        assertThrows(IllegalArgumentException.class,
                () -> kokomi.restoreCharacterState(null, simulator),
                "Kokomi rejects null snapshot payload");
        assertThrows(IllegalStateException.class,
                () -> simulatorWith(kokomi),
                "Kokomi rejects cross-simulator reuse");

        SangonomiyaKokomi invalidRandom = kokomi(0, 1.0);
        CombatSimulator invalidSimulator = simulatorWith(invalidRandom);
        assertThrows(IllegalStateException.class,
                () -> perform(invalidSimulator, CharacterActionKey.SKILL),
                "Kokomi rejects out-of-range particle random draw");
    }

    private static SangonomiyaKokomi kokomi(
            int constellation,
            double particleDraw) {
        return new SangonomiyaKokomi(
                null,
                null,
                TalentDataManager.getInstance(),
                constellation,
                () -> particleDraw);
    }

    private static CombatSimulator simulatorWith(Character... characters) {
        CombatSimulator simulator = new CombatSimulator();
        simulator.setLoggingEnabled(false);
        Enemy enemy = new Enemy(90);
        enemy.setRes(StatType.HYDRO_DMG_BONUS, 0.0);
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
                CharacterId.SANGONOMIYA_KOKOMI,
                CharacterActionRequest.of(key));
    }

    private static List<ActionRecord> captureActions(
            CombatSimulator simulator) {
        List<ActionRecord> records = new ArrayList<>();
        simulator.addDamageListener((actor, action, damage, time) -> {
            if (actor.getCharacterId()
                    == CharacterId.SANGONOMIYA_KOKOMI) {
                records.add(new ActionRecord(action, time));
            }
        });
        return records;
    }

    private static List<Double> captureHydroParticles(
            CombatSimulator simulator) {
        List<Double> records = new ArrayList<>();
        simulator.addParticleListener((element, count, time) -> {
            if (element == Element.HYDRO) {
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
            assertTrue(lines.get(index).startsWith("Sangonomiya Kokomi,"),
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

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }

    private static final class ActionRecord {
        private final AttackAction action;
        private final double time;

        private ActionRecord(AttackAction action, double time) {
            this.action = action;
            this.time = time;
        }
    }

    private static final class TestCharacter extends Character {
        private TestCharacter(CharacterId id, Element element) {
            name = id.getDisplayName();
            characterId = id;
            this.element = element;
            baseStats.set(StatType.BASE_HP, 10000.0);
            baseStats.set(StatType.BASE_ATK, 100.0);
            baseStats.set(StatType.BASE_DEF, 100.0);
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

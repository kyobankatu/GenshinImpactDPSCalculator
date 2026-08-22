package sample;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import model.character.Dori;
import model.entity.ArtifactSet;
import model.entity.Character;
import model.entity.Enemy;
import model.entity.SnapshotAwareCharacterEffect;
import model.entity.Weapon;
import model.stats.StatsContainer;
import model.type.CharacterId;
import model.type.CharacterRegion;
import model.type.Element;
import model.type.StatType;
import simulation.CombatSimulator;
import simulation.SimulatorSnapshot;
import simulation.action.AttackAction;
import simulation.action.CharacterActionKey;
import simulation.action.CharacterActionRequest;

/** Focused regression checks for Dori's represented fixed-target slice. */
public final class DoriRegressionTest {
    private static final double FRAME = 1.0 / 60.0;
    private static final double EPSILON = 1e-8;

    private DoriRegressionTest() {
    }

    /** Runs identity, action, Energy, infusion, restore, and guard checks. */
    public static void main(String[] args) throws Exception {
        testIdentityStatsAndCsv();
        testNormalStringAndReset();
        testSkillRoundsParticlesAndA4();
        testBurstTicksEnergyAndC3();
        testC6InfusionBoundary();
        testSnapshotRestore();
        testGuards();
        System.out.println("DoriRegressionTest passed");
    }

    private static void testIdentityStatsAndCsv() throws IOException {
        assertEquals(CharacterId.DORI, CharacterId.fromNumericId(55),
                "Dori numeric identity");
        assertEquals(CharacterId.DORI, CharacterId.fromName("Dori"),
                "Dori exact-name identity");
        assertEquals(CharacterRegion.SUMERU, CharacterId.DORI.getRegion(),
                "Dori region");
        assertEquals(CharacterId.KAVEH, CharacterId.fromNumericId(56),
                "Dori next numeric identity");
        assertEquals(CharacterId.UNKNOWN, CharacterId.fromName("dori"),
                "Dori lookup remains case-sensitive");

        Dori dori = new Dori(null, null, 0);
        assertEquals(CharacterId.DORI, dori.getCharacterId(),
                "Dori runtime identity");
        assertEquals(Element.ELECTRO, dori.getElement(),
                "Dori element");
        assertClose(12397.0,
                dori.getBaseStats().get(StatType.BASE_HP),
                "Dori base HP");
        assertClose(223.0,
                dori.getBaseStats().get(StatType.BASE_ATK),
                "Dori base ATK");
        assertClose(723.0,
                dori.getBaseStats().get(StatType.BASE_DEF),
                "Dori base DEF");
        assertClose(0.24,
                dori.getBaseStats().get(StatType.HP_PERCENT),
                "Dori ascension HP");
        assertClose(80.0, dori.getEnergyCost(),
                "Dori Burst cost");
        assertCsvShape(Path.of(
                "config/characters/Dori/Dori_Status.csv"), 14);
        assertCsvShape(Path.of(
                "config/characters/Dori/Dori_Multipliers.csv"), 12);
        assertCsvValue("N2 Hit 2", 0.792212);
        assertCsvValue("Connector DMG C3", 0.317648);
    }

    private static void testNormalStringAndReset() {
        Dori dori = new Dori(null, null, 0);
        TestCharacter ally = new TestCharacter(
                CharacterId.NOELLE, Element.GEO);
        CombatSimulator simulator = simulatorWith(dori, ally);
        List<ActionRecord> records = captureActions(simulator);
        perform(simulator, CharacterActionKey.NORMAL);
        perform(simulator, CharacterActionKey.NORMAL);
        perform(simulator, CharacterActionKey.NORMAL);
        List<ActionRecord> normals = named(records,
                "Marvelous Sword-Dance");
        assertEquals(4, normals.size(),
                "Dori three-Normal string contains four hits");
        double[] expected = {
            1.657420, 0.754608, 0.792212, 2.358940
        };
        for (int index = 0; index < expected.length; index++) {
            assertClose(expected[index],
                    normals.get(index).action.getDamagePercent(),
                    "Dori Normal multiplier " + index);
            assertEquals(Element.PHYSICAL,
                    normals.get(index).action.getElement(),
                    "Dori C0 Normal element " + index);
        }
        assertClose(27.0 * FRAME, normals.get(0).time,
                "Dori N1 impact frame");
        assertClose((44.0 + 10.0 + 19.0) * FRAME,
                normals.get(1).time,
                "Dori N2 first impact frame");
        assertClose((44.0 + 10.0 + 33.0) * FRAME,
                normals.get(2).time,
                "Dori N2 second impact frame");

        simulator.switchCharacter(CharacterId.NOELLE);
        simulator.switchCharacter(CharacterId.DORI);
        perform(simulator, CharacterActionKey.NORMAL);
        List<ActionRecord> reset = named(records,
                "Marvelous Sword-Dance N1");
        assertEquals(2, reset.size(),
                "Dori switch-out resets Normal string");
    }

    private static void testSkillRoundsParticlesAndA4() {
        Dori c0 = new Dori(null, null, 0);
        CombatSimulator simulator = simulatorWith(c0);
        List<ActionRecord> records = captureActions(simulator);
        List<Double> particles = captureElectroParticles(simulator);
        c0.spendEnergy(40.0);
        perform(simulator, CharacterActionKey.SKILL);
        advanceTo(simulator, 2.2);
        assertEquals(1, named(records, "Troubleshooter Shot").size(),
                "Dori Skill creates one Troubleshooter hit");
        assertEquals(2, named(records, "After-Sales").size(),
                "Dori C0 Skill creates two After-Sales rounds");
        assertClose(5.0, c0.getTotalFlatEnergy(),
                "Dori A4 restores five Energy at base ER");
        assertEquals(1, particles.size(),
                "Dori Skill emits one particle packet");
        assertClose(2.0, particles.get(0),
                "Dori Skill emits two Electro particles");

        Dori c1 = new Dori(null, null, 1);
        CombatSimulator c1Simulator = simulatorWith(c1);
        List<ActionRecord> c1Records = captureActions(c1Simulator);
        perform(c1Simulator, CharacterActionKey.SKILL);
        advanceTo(c1Simulator, 1.6);
        assertEquals(3, named(c1Records, "After-Sales").size(),
                "Dori C1 creates a third After-Sales round");

        Dori c5 = new Dori(null, null, 5);
        CombatSimulator c5Simulator = simulatorWith(c5);
        List<ActionRecord> c5Records = captureActions(c5Simulator);
        perform(c5Simulator, CharacterActionKey.SKILL);
        advanceTo(c5Simulator, 1.6);
        assertClose(2.945600,
                named(c5Records, "Troubleshooter Shot")
                        .get(0).action.getDamagePercent(),
                "Dori C5 Skill uses Talent 12");
        assertClose(0.631200,
                named(c5Records, "After-Sales")
                        .get(0).action.getDamagePercent(),
                "Dori C5 rounds use Talent 12");
    }

    private static void testBurstTicksEnergyAndC3() {
        Dori c0 = new Dori(null, null, 0);
        CombatSimulator simulator = simulatorWith(c0);
        List<ActionRecord> records = captureActions(simulator);
        perform(simulator, CharacterActionKey.BURST);
        advanceTo(simulator, 13.0);
        List<ActionRecord> connectors = named(records,
                "Alcazarzaray's Exactitude Connector");
        assertEquals(32, connectors.size(),
                "Dori Burst creates thirty-two connector ticks");
        assertClose(28.0 * FRAME, connectors.get(0).time,
                "Dori first connector frame");
        assertClose((28.0 + 31.0 * 24.0) * FRAME,
                connectors.get(31).time,
                "Dori final connector frame");
        assertClose(0.270001,
                connectors.get(0).action.getDamagePercent(),
                "Dori C0 Burst Talent 9");
        assertClose(14.4, c0.getTotalFlatEnergy(),
                "Dori C0 Burst restores six Energy pulses");

        Dori c3 = new Dori(null, null, 3);
        CombatSimulator c3Simulator = simulatorWith(c3);
        List<ActionRecord> c3Records = captureActions(c3Simulator);
        perform(c3Simulator, CharacterActionKey.BURST);
        advanceTo(c3Simulator, 13.0);
        assertClose(0.317648,
                named(c3Records, "Alcazarzaray's Exactitude Connector")
                        .get(0).action.getDamagePercent(),
                "Dori C3 Burst uses Talent 12");
        assertClose(15.0, c3.getTotalFlatEnergy(),
                "Dori C3 Burst restores six Talent 12 pulses");
    }

    private static void testC6InfusionBoundary() {
        Dori dori = new Dori(null, null, 6);
        CombatSimulator simulator = simulatorWith(dori);
        List<ActionRecord> records = captureActions(simulator);
        perform(simulator, CharacterActionKey.SKILL);
        perform(simulator, CharacterActionKey.NORMAL);
        List<ActionRecord> normals = named(records,
                "Marvelous Sword-Dance");
        assertEquals(Element.ELECTRO, normals.get(0).action.getElement(),
                "Dori C6 infuses Normal attacks after Skill");
        advanceTo(simulator, 3.8);
        assertTrue(!dori.isC6InfusionActive(3.8),
                "Dori C6 infusion expires at exact boundary");
        perform(simulator, CharacterActionKey.NORMAL);
        normals = named(records, "Marvelous Sword-Dance");
        assertEquals(Element.PHYSICAL,
                normals.get(normals.size() - 1).action.getElement(),
                "Dori Normal returns to Physical after C6 expiry");
    }

    private static void testSnapshotRestore() {
        Dori dori = new Dori(null, null, 0);
        CombatSimulator simulator = simulatorWith(dori);
        List<ActionRecord> records = captureActions(simulator);
        perform(simulator, CharacterActionKey.BURST);
        SimulatorSnapshot snapshot = simulator.saveSnapshot();
        int resolvedAtSnapshot = named(records,
                "Alcazarzaray's Exactitude Connector").size();
        advanceTo(simulator, 13.0);
        assertEquals(32, named(records,
                "Alcazarzaray's Exactitude Connector").size(),
                "Dori live branch resolves all connector ticks");
        simulator.restoreSnapshot(snapshot);
        advanceTo(simulator, 13.0);
        assertEquals(64 - resolvedAtSnapshot, named(records,
                "Alcazarzaray's Exactitude Connector").size(),
                "Dori restore replays only pending connector ticks once");
        assertEquals(0, dori.getPendingHitCount(),
                "Dori has no pending hits after restored Burst ends");
    }

    private static void testGuards() {
        assertThrows(IllegalArgumentException.class,
                () -> new Dori(null, null, -1),
                "Dori rejects negative constellation");
        assertThrows(IllegalArgumentException.class,
                () -> new Dori(null, null, 7),
                "Dori rejects constellation above C6");
        Dori dori = new Dori(null, null, 0);
        CombatSimulator simulator = simulatorWith(dori);
        assertThrows(IllegalArgumentException.class,
                () -> dori.onAction(null, simulator),
                "Dori rejects null action");
        assertThrows(IllegalArgumentException.class,
                () -> perform(simulator, CharacterActionKey.CHARGE),
                "Dori rejects deferred Charged attacks");
        Dori external = new Dori(null, null, 0);
        assertThrows(IllegalArgumentException.class,
                () -> external.initializeForSimulator(new CombatSimulator()),
                "Dori rejects binding outside simulator party");
        Dori reused = new Dori(null, null, 0);
        simulatorWith(reused);
        assertThrows(IllegalStateException.class,
                () -> simulatorWith(reused),
                "Dori rejects cross-simulator reuse");
        SnapshotAwareCharacterEffect.State foreignState =
                external.captureCharacterState();
        assertTrue(!dori.acceptsCharacterState(foreignState),
                "Dori rejects another instance snapshot payload");
    }

    private static CombatSimulator simulatorWith(Character... characters) {
        CombatSimulator simulator = new CombatSimulator();
        simulator.setLoggingEnabled(false);
        Enemy enemy = new Enemy(90);
        enemy.setRes(StatType.PHYSICAL_DMG_BONUS, 0.0);
        enemy.setRes(StatType.ELECTRO_DMG_BONUS, 0.0);
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
                CharacterId.DORI, CharacterActionRequest.of(key));
    }

    private static List<ActionRecord> captureActions(
            CombatSimulator simulator) {
        List<ActionRecord> records = new ArrayList<>();
        simulator.addDamageListener((actor, action, damage, time) -> {
            if (actor.getCharacterId() == CharacterId.DORI) {
                records.add(new ActionRecord(action, time));
            }
        });
        return records;
    }

    private static List<Double> captureElectroParticles(
            CombatSimulator simulator) {
        List<Double> records = new ArrayList<>();
        simulator.addParticleListener((element, count, time) -> {
            if (element == Element.ELECTRO) {
                records.add(count);
            }
        });
        return records;
    }

    private static List<ActionRecord> named(
            List<ActionRecord> records,
            String prefix) {
        List<ActionRecord> selected = new ArrayList<>();
        for (ActionRecord record : records) {
            if (record.action.getName().startsWith(prefix)) {
                selected.add(record);
            }
        }
        return selected;
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
            assertTrue(lines.get(index).startsWith("Dori,"),
                    path + " identity at line " + (index + 1));
        }
    }

    private static void assertCsvValue(String key, double expected)
            throws IOException {
        for (String path : new String[] {
                "config/characters/Dori/Dori_Status.csv",
                "config/characters/Dori/Dori_Multipliers.csv"
        }) {
            for (String line : Files.readAllLines(Path.of(path))) {
                String[] columns = line.split(",", -1);
                if (columns.length == 6 && columns[2].equals(key)) {
                    assertClose(expected, Double.parseDouble(columns[4]),
                            path + " value for " + key);
                    return;
                }
            }
        }
        throw new AssertionError("Dori CSVs missing key " + key);
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
        if (!expected.equals(actual)) {
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
            baseStats.set(StatType.BASE_DEF, 500.0);
        }

        @Override
        public void applyPassive(StatsContainer stats) {
        }

        @Override
        public double getEnergyCost() {
            return 40.0;
        }

        @Override
        public Weapon getWeapon() {
            return null;
        }

        @Override
        public ArtifactSet[] getArtifacts() {
            return new ArtifactSet[0];
        }
    }
}

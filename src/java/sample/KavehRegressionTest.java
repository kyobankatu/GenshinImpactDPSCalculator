package sample;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import mechanics.buff.Buff;
import model.character.Kaveh;
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

/** Focused regression checks for Kaveh's represented fixed-target slice. */
public final class KavehRegressionTest {
    private static final double FRAME = 1.0 / 60.0;
    private static final double EPSILON = 1e-8;

    private KavehRegressionTest() {
    }

    /** Runs identity, action, form, constellation, restore, and guard checks. */
    public static void main(String[] args) throws Exception {
        testIdentityStatsAndCsv();
        testNormalStringAndReset();
        testSkillParticlesC1AndC5();
        testBurstInfusionBloomBonusAndC3();
        testA4C2C4AndC6();
        testSnapshotRestore();
        testGuards();
        System.out.println("KavehRegressionTest passed");
    }

    private static void testIdentityStatsAndCsv() throws IOException {
        assertEquals(CharacterId.KAVEH, CharacterId.fromNumericId(56),
                "Kaveh numeric identity");
        assertEquals(CharacterId.KAVEH, CharacterId.fromName("Kaveh"),
                "Kaveh exact-name identity");
        assertEquals(CharacterRegion.SUMERU, CharacterId.KAVEH.getRegion(),
                "Kaveh region");
        assertEquals(CharacterId.CHEVREUSE, CharacterId.fromNumericId(57),
                "Kaveh next numeric identity");
        assertEquals(CharacterId.UNKNOWN, CharacterId.fromName("kaveh"),
                "Kaveh lookup remains case-sensitive");

        Kaveh kaveh = new Kaveh(null, null, 0);
        assertEquals(CharacterId.KAVEH, kaveh.getCharacterId(),
                "Kaveh runtime identity");
        assertEquals(Element.DENDRO, kaveh.getElement(),
                "Kaveh element");
        assertClose(11962.0,
                kaveh.getBaseStats().get(StatType.BASE_HP),
                "Kaveh base HP");
        assertClose(234.0,
                kaveh.getBaseStats().get(StatType.BASE_ATK),
                "Kaveh base ATK");
        assertClose(751.0,
                kaveh.getBaseStats().get(StatType.BASE_DEF),
                "Kaveh base DEF");
        assertClose(96.0,
                kaveh.getBaseStats().get(StatType.ELEMENTAL_MASTERY),
                "Kaveh ascension EM");
        assertClose(80.0, kaveh.getEnergyCost(),
                "Kaveh Burst cost");
        assertCsvShape(Path.of(
                "config/characters/Kaveh/Kaveh_Status.csv"), 24);
        assertCsvShape(Path.of(
                "config/characters/Kaveh/Kaveh_Multipliers.csv"), 10);
        assertCsvValue("N4", 1.886599);
        assertCsvValue("Bloom DMG Bonus C3", 0.549760);
    }

    private static void testNormalStringAndReset() {
        Kaveh kaveh = new Kaveh(null, null, 0);
        TestCharacter ally = new TestCharacter(
                CharacterId.NOELLE, Element.GEO);
        CombatSimulator simulator = simulatorWith(kaveh, ally);
        List<ActionRecord> records = captureActions(simulator);
        for (int index = 0; index < 4; index++) {
            perform(simulator, CharacterActionKey.NORMAL);
        }
        List<ActionRecord> normals = named(records, "Schematic Setup");
        assertEquals(4, normals.size(),
                "Kaveh four-step Normal string");
        double[] expected = {
            1.399690, 1.279405, 1.548052, 1.886599
        };
        for (int index = 0; index < expected.length; index++) {
            assertClose(expected[index],
                    normals.get(index).action.getDamagePercent(),
                    "Kaveh Normal multiplier " + index);
            assertEquals(Element.PHYSICAL,
                    normals.get(index).action.getElement(),
                    "Kaveh C0 Normal element " + index);
        }
        assertClose(27.0 * FRAME, normals.get(0).time,
                "Kaveh N1 impact frame");
        assertClose((44.0 + 10.0 + 22.0) * FRAME, normals.get(1).time,
                "Kaveh N2 impact frame");

        simulator.switchCharacter(CharacterId.NOELLE);
        simulator.switchCharacter(CharacterId.KAVEH);
        perform(simulator, CharacterActionKey.NORMAL);
        assertEquals(2, named(records, "Schematic Setup N1").size(),
                "Kaveh switch-out resets Normal string");
    }

    private static void testSkillParticlesC1AndC5() {
        Kaveh c0 = new Kaveh(null, null, 0);
        CombatSimulator simulator = simulatorWith(c0);
        List<ActionRecord> records = captureActions(simulator);
        List<Double> particles = captureDendroParticles(simulator);
        perform(simulator, CharacterActionKey.SKILL);
        advanceTo(simulator, 2.5);
        assertEquals(1, named(records, "Artistic Ingenuity").size(),
                "Kaveh Skill creates one hit");
        assertClose(3.468,
                named(records, "Artistic Ingenuity")
                        .get(0).action.getDamagePercent(),
                "Kaveh C0 Skill uses Talent 9");
        assertEquals(1, particles.size(),
                "Kaveh Skill emits one particle packet");
        assertClose(2.0, particles.get(0),
                "Kaveh Skill emits two Dendro particles");

        Kaveh c1 = new Kaveh(null, null, 1);
        CombatSimulator c1Simulator = simulatorWith(c1);
        perform(c1Simulator, CharacterActionKey.SKILL);
        assertClose(0.25,
                c1.getEffectiveStats(c1Simulator.getCurrentTime())
                        .get(StatType.HEALING_BONUS),
                "Kaveh C1 Healing Bonus begins during Skill");
        advanceTo(c1Simulator, 33.0 * FRAME + 3.0);
        assertClose(0.0,
                c1.getEffectiveStats(c1Simulator.getCurrentTime())
                        .get(StatType.HEALING_BONUS),
                "Kaveh C1 expires at exact boundary");

        Kaveh c5 = new Kaveh(null, null, 5);
        CombatSimulator c5Simulator = simulatorWith(c5);
        List<ActionRecord> c5Records = captureActions(c5Simulator);
        perform(c5Simulator, CharacterActionKey.SKILL);
        assertClose(4.08,
                named(c5Records, "Artistic Ingenuity")
                        .get(0).action.getDamagePercent(),
                "Kaveh C5 Skill uses Talent 12");
    }

    private static void testBurstInfusionBloomBonusAndC3() {
        Kaveh c0 = new Kaveh(null, null, 0);
        TestCharacter ally = new TestCharacter(
                CharacterId.BARBARA, Element.HYDRO);
        CombatSimulator simulator = simulatorWith(c0, ally);
        List<ActionRecord> records = captureActions(simulator);
        perform(simulator, CharacterActionKey.BURST);
        assertEquals(1, named(records, "Painted Dome").size(),
                "Kaveh Burst creates one cast hit");
        assertClose(2.72,
                named(records, "Painted Dome")
                        .get(0).action.getDamagePercent(),
                "Kaveh C0 Burst uses Talent 9");
        assertTrue(c0.isBurstActive(simulator.getCurrentTime()),
                "Kaveh form starts at Burst hit");
        assertClose(0.467296,
                effectiveStats(ally, simulator)
                        .get(StatType.BLOOM_DMG_BONUS),
                "Kaveh Burst grants party Bloom DMG");
        perform(simulator, CharacterActionKey.NORMAL);
        assertEquals(Element.DENDRO,
                named(records, "Schematic Setup")
                        .get(0).action.getElement(),
                "Kaveh Burst infuses Normal attacks");
        simulator.switchCharacter(CharacterId.BARBARA);
        assertTrue(!c0.isBurstActive(simulator.getCurrentTime()),
                "Kaveh Burst ends on switch-out");
        assertClose(0.0,
                effectiveStats(ally, simulator)
                        .get(StatType.BLOOM_DMG_BONUS),
                "Kaveh switch-out removes party Bloom DMG");

        Kaveh c3 = new Kaveh(null, null, 3);
        CombatSimulator c3Simulator = simulatorWith(c3);
        List<ActionRecord> c3Records = captureActions(c3Simulator);
        perform(c3Simulator, CharacterActionKey.BURST);
        assertClose(3.20,
                named(c3Records, "Painted Dome")
                        .get(0).action.getDamagePercent(),
                "Kaveh C3 Burst uses Talent 12");
        assertClose(0.549760,
                effectiveStats(c3, c3Simulator)
                        .get(StatType.BLOOM_DMG_BONUS),
                "Kaveh C3 upgrades Painted Dome Bloom bonus");
    }

    private static void testA4C2C4AndC6() {
        Kaveh kaveh = new Kaveh(null, null, 6);
        CombatSimulator simulator = simulatorWith(kaveh);
        List<ActionRecord> records = captureActions(simulator);
        perform(simulator, CharacterActionKey.BURST);
        double beforeNormal = simulator.getCurrentTime();
        for (int index = 0; index < 4; index++) {
            perform(simulator, CharacterActionKey.NORMAL);
        }
        assertEquals(4, kaveh.getA4Stacks(simulator.getCurrentTime()),
                "Kaveh A4 reaches four EM stacks");
        assertClose(196.0,
                kaveh.getEffectiveStats(simulator.getCurrentTime())
                        .get(StatType.ELEMENTAL_MASTERY),
                "Kaveh A4 adds one hundred EM");
        double expectedC2String = (44.0 + 45.0 + 56.0 + 81.0)
                * FRAME / 1.15 + 44.0 * FRAME;
        assertClose(expectedC2String,
                simulator.getCurrentTime() - beforeNormal,
                "Kaveh C2 accelerates the Normal string");
        assertClose(0.60 + 0.549760,
                effectiveStats(kaveh, simulator)
                        .get(StatType.BLOOM_DMG_BONUS),
                "Kaveh C4 stacks with C3 Painted Dome Bloom bonus");
        advanceTo(simulator, simulator.getCurrentTime() + 0.4);
        assertEquals(1, named(records, "Pairidaeza's Dreams").size(),
                "Kaveh C6 respects its three-second trigger cooldown");
        assertClose(0.618,
                named(records, "Pairidaeza's Dreams")
                        .get(0).action.getDamagePercent(),
                "Kaveh C6 uses the source-backed multiplier");
    }

    private static void testSnapshotRestore() {
        Kaveh kaveh = new Kaveh(null, null, 6);
        CombatSimulator simulator = simulatorWith(kaveh);
        List<ActionRecord> records = captureActions(simulator);
        SimulatorSnapshot[] captured = {null};
        simulator.addDamageListener((actor, action, damage, time) -> {
            if (captured[0] == null
                    && actor.getCharacterId() == CharacterId.KAVEH
                    && "Schematic Setup N1".equals(action.getName())) {
                captured[0] = simulator.saveSnapshot();
            }
        });
        perform(simulator, CharacterActionKey.BURST);
        perform(simulator, CharacterActionKey.NORMAL);
        assertTrue(captured[0] != null,
                "Kaveh should capture the queued C6 hit at N1 impact");
        assertEquals(1, named(records, "Pairidaeza's Dreams").size(),
                "Kaveh live branch resolves pending C6 hit");
        simulator.restoreSnapshot(captured[0]);
        advanceTo(simulator, simulator.getCurrentTime() + 0.4);
        assertEquals(2, named(records, "Pairidaeza's Dreams").size(),
                "Kaveh restore replays the pending C6 hit once");
        assertEquals(0, kaveh.getPendingHitCount(),
                "Kaveh has no pending hits after restored C6 resolves");
    }

    private static void testGuards() {
        assertThrows(IllegalArgumentException.class,
                () -> new Kaveh(null, null, -1),
                "Kaveh rejects negative constellation");
        assertThrows(IllegalArgumentException.class,
                () -> new Kaveh(null, null, 7),
                "Kaveh rejects constellation above C6");
        Kaveh kaveh = new Kaveh(null, null, 0);
        CombatSimulator simulator = simulatorWith(kaveh);
        assertThrows(IllegalArgumentException.class,
                () -> kaveh.onAction(null, simulator),
                "Kaveh rejects null action");
        assertThrows(IllegalArgumentException.class,
                () -> perform(simulator, CharacterActionKey.CHARGE),
                "Kaveh rejects deferred Charged attacks");
        Kaveh external = new Kaveh(null, null, 0);
        assertThrows(IllegalArgumentException.class,
                () -> external.initializeForSimulator(
                        new CombatSimulator()),
                "Kaveh rejects binding outside simulator party");
        Kaveh reused = new Kaveh(null, null, 0);
        simulatorWith(reused);
        assertThrows(IllegalStateException.class,
                () -> simulatorWith(reused),
                "Kaveh rejects cross-simulator reuse");
        SnapshotAwareCharacterEffect.State foreign =
                external.captureCharacterState();
        assertTrue(!kaveh.acceptsCharacterState(foreign),
                "Kaveh rejects another instance snapshot payload");
    }

    private static CombatSimulator simulatorWith(Character... characters) {
        CombatSimulator simulator = new CombatSimulator();
        simulator.setLoggingEnabled(false);
        Enemy enemy = new Enemy(90);
        enemy.setRes(StatType.PHYSICAL_DMG_BONUS, 0.0);
        enemy.setRes(StatType.DENDRO_DMG_BONUS, 0.0);
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
                CharacterId.KAVEH, CharacterActionRequest.of(key));
    }

    private static List<ActionRecord> captureActions(
            CombatSimulator simulator) {
        List<ActionRecord> records = new ArrayList<>();
        simulator.addDamageListener((actor, action, damage, time) -> {
            if (actor.getCharacterId() == CharacterId.KAVEH) {
                records.add(new ActionRecord(action, time));
            }
        });
        return records;
    }

    private static List<Double> captureDendroParticles(
            CombatSimulator simulator) {
        List<Double> records = new ArrayList<>();
        simulator.addParticleListener((element, count, time) -> {
            if (element == Element.DENDRO) {
                records.add(count);
            }
        });
        return records;
    }

    private static StatsContainer effectiveStats(
            Character character,
            CombatSimulator simulator) {
        StatsContainer stats = character.getEffectiveStats(
                simulator.getCurrentTime());
        for (Buff buff : simulator.getApplicableBuffs(character)) {
            if (!buff.isExpired(simulator.getCurrentTime())) {
                buff.apply(stats, simulator.getCurrentTime());
            }
        }
        return stats;
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
            assertTrue(lines.get(index).startsWith("Kaveh,"),
                    path + " identity at line " + (index + 1));
        }
    }

    private static void assertCsvValue(String key, double expected)
            throws IOException {
        for (String path : new String[] {
                "config/characters/Kaveh/Kaveh_Status.csv",
                "config/characters/Kaveh/Kaveh_Multipliers.csv"
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
        throw new AssertionError("Kaveh CSVs missing key " + key);
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

package sample;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import mechanics.buff.Buff;
import model.character.Charlotte;
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
import simulation.action.SkillActionMode;

/** Focused regression checks for Charlotte's fixed-target Kamera kit. */
public final class CharlotteRegressionTest {
    private static final double FRAME = 1.0 / 60.0;
    private static final double EPSILON = 1e-8;

    private CharlotteRegressionTest() {
    }

    /** Runs identity, action, mark, Burst, restore, and guard checks. */
    public static void main(String[] args) throws Exception {
        testIdentityDataAndConstruction();
        testBasicsSwitchResetAndArkhe();
        testArkheBoundary();
        testPressMarksParticlesAndRestore();
        testMinimumHoldC2AndC5();
        testBurstC3();
        testGuards();
        System.out.println("CharlotteRegressionTest passed");
    }

    private static void testIdentityDataAndConstruction()
            throws IOException {
        Charlotte charlotte = new Charlotte(null, null, 6);
        assertEquals(CharacterId.CHARLOTTE, charlotte.getCharacterId(),
                "Charlotte typed identity");
        assertEquals(CharacterId.CHARLOTTE,
                CharacterId.fromName("Charlotte"),
                "Charlotte name lookup");
        assertEquals(CharacterId.CHARLOTTE,
                CharacterId.fromNumericId(54),
                "Charlotte numeric lookup");
        assertEquals(CharacterRegion.FONTAINE,
                CharacterId.CHARLOTTE.getRegion(), "Charlotte region");
        assertEquals(Element.CRYO, charlotte.getElement(),
                "Charlotte element");
        assertClose(10766.0,
                charlotte.getBaseStats().get(StatType.BASE_HP),
                "Charlotte base HP");
        assertClose(173.0,
                charlotte.getBaseStats().get(StatType.BASE_ATK),
                "Charlotte base ATK");
        assertClose(546.0,
                charlotte.getBaseStats().get(StatType.BASE_DEF),
                "Charlotte base DEF");
        assertClose(0.24,
                charlotte.getBaseStats().get(StatType.ATK_PERCENT),
                "Charlotte ascension ATK");
        assertClose(80.0, charlotte.getEnergyCost(),
                "Charlotte Energy cost");
        assertClose(12.0, charlotte.getSkillCD(),
                "Charlotte default Skill cooldown");
        assertClose(20.0, charlotte.getBurstCD(),
                "Charlotte Burst cooldown");
        for (int constellation = 0; constellation <= 6;
                constellation++) {
            assertEquals(CharacterId.CHARLOTTE,
                    new Charlotte(null, null, constellation)
                            .getCharacterId(),
                    "Charlotte explicit C" + constellation
                            + " construction");
        }
        assertCsvShape(Path.of(
                "config/characters/Charlotte/Charlotte_Status.csv"), 14);
        assertCsvShape(Path.of(
                "config/characters/Charlotte/Charlotte_Multipliers.csv"),
                17);
        assertCsvValue("N3", 1.098214);
        assertCsvValue("Hold Photo C5", 2.784000);
        assertCsvValue("Kamera Tick C3", 0.129360);
    }

    private static void testBasicsSwitchResetAndArkhe() {
        Charlotte charlotte = new Charlotte(null, null, 0);
        TestCharacter ally = new TestCharacter(
                CharacterId.NOELLE, Element.GEO);
        CombatSimulator simulator = simulatorWith(charlotte, ally);
        List<ActionRecord> records = captureActions(simulator);
        double[] expected = { 0.847375, 0.737378, 1.098214 };
        for (int step = 0; step < expected.length; step++) {
            double castTime = simulator.getCurrentTime();
            perform(simulator, CharacterActionRequest.of(
                    CharacterActionKey.NORMAL));
            ActionRecord record = records.get(step);
            assertClose(castTime
                            + new int[] { 13, 25, 31 }[step] * FRAME,
                    record.time, "Charlotte Normal hitmark");
            assertClose(expected[step], record.action.getDamagePercent(),
                    "Charlotte Normal multiplier");
            assertEquals(Element.CRYO, record.action.getElement(),
                    "Charlotte catalyst Normal element");
        }

        perform(simulator, CharacterActionRequest.of(
                CharacterActionKey.NORMAL));
        simulator.switchCharacter(CharacterId.NOELLE);
        simulator.switchCharacter(CharacterId.CHARLOTTE);
        perform(simulator, CharacterActionRequest.of(
                CharacterActionKey.NORMAL));
        assertEquals(3, named(records, "Cool-Color Capture N1").size(),
                "Charlotte switch-out resets the Normal string");

        double chargedCast = simulator.getCurrentTime();
        perform(simulator, CharacterActionRequest.of(
                CharacterActionKey.CHARGE));
        ActionRecord charged = named(records,
                "Cool-Color Capture Charged").get(0);
        assertClose(chargedCast + 67.0 * FRAME, charged.time,
                "Charlotte Charged hitmark");
        assertClose(1.708704, charged.action.getDamagePercent(),
                "Charlotte Charged multiplier");
        advanceTo(simulator, chargedCast + 97.0 * FRAME);
        ActionRecord arkhe = named(records,
                "Spiritbreath Thorn (Charlotte)").get(0);
        assertClose(chargedCast + 97.0 * FRAME, arkhe.time,
                "Charlotte Arkhe hitmark");
        assertClose(0.189856, arkhe.action.getDamagePercent(),
                "Charlotte Arkhe multiplier");
        assertClose(0.0, arkhe.action.getGaugeUnits(),
                "Charlotte Arkhe applies no elemental gauge");
    }

    private static void testArkheBoundary() {
        Charlotte before = new Charlotte(null, null, 0);
        CombatSimulator beforeSimulator = simulatorWith(before);
        List<ActionRecord> beforeRecords = captureActions(beforeSimulator);
        perform(beforeSimulator, CharacterActionRequest.of(
                CharacterActionKey.CHARGE));
        advanceTo(beforeSimulator, 6.0 - 2.0 * EPSILON);
        perform(beforeSimulator, CharacterActionRequest.of(
                CharacterActionKey.CHARGE));
        advanceTo(beforeSimulator, 8.0);
        assertEquals(1, named(beforeRecords,
                "Spiritbreath Thorn (Charlotte)").size(),
                "Charlotte Arkhe remains gated before six seconds");

        Charlotte boundary = new Charlotte(null, null, 0);
        CombatSimulator boundarySimulator = simulatorWith(boundary);
        List<ActionRecord> boundaryRecords = captureActions(
                boundarySimulator);
        perform(boundarySimulator, CharacterActionRequest.of(
                CharacterActionKey.CHARGE));
        advanceTo(boundarySimulator, 6.0);
        perform(boundarySimulator, CharacterActionRequest.of(
                CharacterActionKey.CHARGE));
        advanceTo(boundarySimulator, 8.0);
        assertEquals(2, named(boundaryRecords,
                "Spiritbreath Thorn (Charlotte)").size(),
                "Charlotte Arkhe reopens at the exact six-second boundary");
    }

    private static void testPressMarksParticlesAndRestore() {
        Charlotte charlotte = new Charlotte(null, null, 0);
        CombatSimulator simulator = simulatorWith(charlotte);
        List<ActionRecord> records = captureActions(simulator);
        List<Double> particles = captureCryoParticles(simulator);
        perform(simulator, CharacterActionRequest.skill(
                SkillActionMode.PRESS));
        ActionRecord photo = named(records,
                "Framing: Freezing Point Composition").get(0);
        assertClose(31.0 * FRAME, photo.time,
                "Charlotte Press hitmark");
        assertClose(1.142400, photo.action.getDamagePercent(),
                "Charlotte C0 Press Talent 9");
        assertTrue(charlotte.getSkillCDRemaining(
                simulator.getCurrentTime()) > 11.0,
                "Charlotte Press cooldown begins at frame twenty-nine");

        SimulatorSnapshot pending = simulator.saveSnapshot();
        advanceTo(simulator, 7.0);
        assertMarkSequence(records, "Snappy Silhouette Mark",
                4, 121, 0.666400);
        assertEquals(1, particles.size(),
                "Charlotte Press emits one particle packet");
        assertClose(3.0, particles.get(0),
                "Charlotte Press emits three particles");

        simulator.restoreSnapshot(pending);
        advanceTo(simulator, 7.0);
        assertEquals(8, named(records,
                "Snappy Silhouette Mark").size(),
                "Charlotte restore replays four pending marks once");
        assertEquals(2, particles.size(),
                "Charlotte restore replays the pending particles once");
    }

    private static void testMinimumHoldC2AndC5() {
        Charlotte charlotte = new Charlotte(null, null, 5);
        CombatSimulator simulator = simulatorWith(charlotte);
        List<ActionRecord> records = captureActions(simulator);
        List<Double> particles = captureCryoParticles(simulator);
        perform(simulator, CharacterActionRequest.skill(
                SkillActionMode.HOLD));
        ActionRecord photo = named(records,
                "Framing: Freezing Point Composition (Hold)").get(0);
        assertClose(112.0 * FRAME, photo.time,
                "Charlotte minimum-Hold hitmark");
        assertClose(2.784000, photo.action.getDamagePercent(),
                "Charlotte C5 Hold Talent 12");
        assertClose(0.10,
                effectiveStats(simulator, charlotte).get(
                        StatType.ATK_PERCENT) - 0.24,
                "Charlotte C2 grants ten percent ATK");
        assertTrue(charlotte.getSkillCDRemaining(
                simulator.getCurrentTime()) > 17.0,
                "Charlotte Hold cooldown begins at frame one-ten");
        advanceTo(simulator, 14.0);
        assertMarkSequence(records, "Focused Impression Mark",
                8, 202, 0.812000);
        assertEquals(1, particles.size(),
                "Charlotte Hold emits one particle packet");
        assertClose(5.0, particles.get(0),
                "Charlotte Hold emits five particles");
    }

    private static void testBurstC3() {
        Charlotte charlotte = new Charlotte(null, null, 3);
        CombatSimulator simulator = simulatorWith(charlotte);
        List<ActionRecord> records = captureActions(simulator);
        perform(simulator, CharacterActionRequest.of(
                CharacterActionKey.BURST));
        ActionRecord initial = named(records,
                "Still Photo: Comprehensive Confirmation").get(0);
        assertClose(53.0 * FRAME, initial.time,
                "Charlotte Burst initial hitmark");
        assertClose(1.552320, initial.action.getDamagePercent(),
                "Charlotte C3 Burst Talent 12");
        assertClose(0.0, charlotte.getCurrentEnergy(),
                "Charlotte Burst spends Energy at frame seven");
        assertTrue(charlotte.getBurstCDRemaining(
                simulator.getCurrentTime()) > 18.0,
                "Charlotte Burst cooldown starts at cast");
        advanceTo(simulator, 5.0);
        int[] frames = { 95, 119, 143, 166, 179, 203, 226, 249 };
        List<ActionRecord> ticks = named(records,
                "Still Photo: Kamera Tick");
        assertEquals(frames.length, ticks.size(),
                "Charlotte Burst queues eight Kamera ticks");
        for (int index = 0; index < frames.length; index++) {
            assertClose(frames[index] * FRAME, ticks.get(index).time,
                    "Charlotte Burst tick hitmark");
            assertClose(0.129360,
                    ticks.get(index).action.getDamagePercent(),
                    "Charlotte C3 Kamera Talent 12");
        }
    }

    private static void testGuards() {
        assertThrows(IllegalArgumentException.class,
                () -> new Charlotte(null, null, -1),
                "Charlotte rejects negative constellation");
        assertThrows(IllegalArgumentException.class,
                () -> new Charlotte(null, null, 7),
                "Charlotte rejects constellation above C6");
        Charlotte charlotte = new Charlotte(null, null, 0);
        CombatSimulator simulator = simulatorWith(charlotte);
        assertThrows(IllegalArgumentException.class,
                () -> charlotte.onAction(null, simulator),
                "Charlotte rejects null action");
        Charlotte external = new Charlotte(null, null, 0);
        assertThrows(IllegalArgumentException.class,
                () -> external.initializeForSimulator(
                        new CombatSimulator()),
                "Charlotte rejects binding outside simulator party");
        Charlotte reused = new Charlotte(null, null, 0);
        simulatorWith(reused);
        assertThrows(IllegalStateException.class,
                () -> simulatorWith(reused),
                "Charlotte rejects cross-simulator reuse");
        SnapshotAwareCharacterEffect.State foreignState =
                external.captureCharacterState();
        assertTrue(!charlotte.acceptsCharacterState(foreignState),
                "Charlotte rejects another instance snapshot payload");
        assertThrows(IllegalArgumentException.class,
                () -> charlotte.restoreCharacterState(
                        foreignState, simulator),
                "Charlotte rejects restoring another instance state");
    }

    private static void assertMarkSequence(
            List<ActionRecord> records,
            String name,
            int expectedCount,
            int firstFrame,
            double expectedMultiplier) {
        List<ActionRecord> marks = named(records, name);
        assertEquals(expectedCount, marks.size(),
                name + " count");
        for (int index = 0; index < marks.size(); index++) {
            assertClose((firstFrame + 90 * index) * FRAME,
                    marks.get(index).time, name + " hitmark");
            assertClose(expectedMultiplier,
                    marks.get(index).action.getDamagePercent(),
                    name + " multiplier");
        }
    }

    private static CombatSimulator simulatorWith(
            Character... characters) {
        CombatSimulator simulator = new CombatSimulator();
        simulator.setLoggingEnabled(false);
        Enemy enemy = new Enemy(90);
        enemy.setRes(StatType.PHYSICAL_DMG_BONUS, 0.0);
        enemy.setRes(StatType.CRYO_DMG_BONUS, 0.0);
        simulator.setEnemy(enemy);
        for (Character character : characters) {
            simulator.addCharacter(character);
        }
        return simulator;
    }

    private static void perform(
            CombatSimulator simulator,
            CharacterActionRequest request) {
        simulator.performAction(CharacterId.CHARLOTTE, request);
    }

    private static List<ActionRecord> captureActions(
            CombatSimulator simulator) {
        List<ActionRecord> records = new ArrayList<>();
        simulator.addDamageListener((actor, action, damage, time) -> {
            if (actor.getCharacterId() == CharacterId.CHARLOTTE) {
                records.add(new ActionRecord(action, time));
            }
        });
        return records;
    }

    private static List<Double> captureCryoParticles(
            CombatSimulator simulator) {
        List<Double> records = new ArrayList<>();
        simulator.addParticleListener((element, count, time) -> {
            if (element == Element.CRYO) {
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

    private static StatsContainer effectiveStats(
            CombatSimulator simulator,
            Character character) {
        double currentTime = simulator.getCurrentTime();
        StatsContainer stats = character.getEffectiveStats(currentTime);
        for (Buff buff : simulator.getApplicableBuffs(character)) {
            if (!buff.isExpired(currentTime)) {
                buff.apply(stats, currentTime);
            }
        }
        return stats;
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
            assertTrue(lines.get(index).startsWith("Charlotte,"),
                    path + " identity at line " + (index + 1));
        }
    }

    private static void assertCsvValue(String key, double expected)
            throws IOException {
        for (String path : new String[] {
                "config/characters/Charlotte/Charlotte_Status.csv",
                "config/characters/Charlotte/Charlotte_Multipliers.csv"
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
        throw new AssertionError("Charlotte CSVs missing key " + key);
    }

    private static void assertClose(
            double expected,
            double actual,
            String message) {
        if (Double.doubleToLongBits(expected)
                == Double.doubleToLongBits(actual)) {
            return;
        }
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

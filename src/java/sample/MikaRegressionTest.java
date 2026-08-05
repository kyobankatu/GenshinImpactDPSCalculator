package sample;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import mechanics.buff.Buff;
import model.character.Mika;
import model.entity.ArtifactSet;
import model.entity.Character;
import model.entity.Enemy;
import model.entity.SnapshotAwareCharacterEffect;
import model.entity.Weapon;
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
import simulation.action.SkillActionMode;

/** Focused regression checks for Mika's fixed-target Soulwind kit. */
public final class MikaRegressionTest {
    private static final double FRAME = 1.0 / 60.0;
    private static final double EPSILON = 1e-8;

    private MikaRegressionTest() {
    }

    /** Runs identity, action, support, boundary, restore, and guard checks. */
    public static void main(String[] args) throws Exception {
        testIdentityDataAndConstruction();
        testPhysicalBasicsAndSwitchReset();
        testPressSoulwindParticlesAndRestore();
        testHoldC2C5C6AndIsolation();
        testSoulwindExpirationBoundary();
        testGuards();
        System.out.println("MikaRegressionTest passed");
    }

    private static void testIdentityDataAndConstruction() throws IOException {
        Mika mika = new Mika(null, null, 6);
        assertEquals(CharacterId.MIKA, mika.getCharacterId(),
                "Mika typed identity");
        assertEquals(CharacterId.MIKA, CharacterId.fromName("Mika"),
                "Mika name lookup");
        assertEquals(CharacterId.MIKA, CharacterId.fromNumericId(53),
                "Mika numeric lookup");
        assertEquals(CharacterRegion.MONDSTADT,
                CharacterId.MIKA.getRegion(), "Mika region");
        assertEquals(Element.CRYO, mika.getElement(), "Mika element");
        assertClose(12506.0,
                mika.getBaseStats().get(StatType.BASE_HP), "Mika base HP");
        assertClose(223.0,
                mika.getBaseStats().get(StatType.BASE_ATK), "Mika base ATK");
        assertClose(713.0,
                mika.getBaseStats().get(StatType.BASE_DEF), "Mika base DEF");
        assertClose(0.24,
                mika.getBaseStats().get(StatType.HP_PERCENT),
                "Mika ascension HP");
        assertClose(70.0, mika.getEnergyCost(), "Mika Energy cost");
        assertClose(15.0, mika.getSkillCD(), "Mika Skill cooldown");
        for (int constellation = 0; constellation <= 6; constellation++) {
            assertEquals(CharacterId.MIKA,
                    new Mika(null, null, constellation).getCharacterId(),
                    "Mika explicit C" + constellation + " construction");
        }
        assertCsvShape(Path.of(
                "config/characters/Mika/Mika_Status.csv"), 13);
        assertCsvShape(Path.of(
                "config/characters/Mika/Mika_Multipliers.csv"), 15);
        assertCsvValue("N4 Hit 2", 0.507338);
        assertCsvValue("Rimestar Flare C5", 1.680000);
        assertCsvValue("ATK Speed C5", 0.240000);
    }

    private static void testPhysicalBasicsAndSwitchReset() {
        Mika mika = new Mika(null, null, 0);
        TestCharacter ally = new TestCharacter(
                CharacterId.NOELLE, Element.GEO);
        CombatSimulator simulator = simulatorWith(mika, ally);
        List<ActionRecord> records = captureActions(simulator);
        double[] expected = {
            0.794835, 0.762476, 1.001341,
            0.507338, 0.507338, 1.302110
        };
        int record = 0;
        for (int step = 0; step < 5; step++) {
            perform(simulator, CharacterActionRequest.of(
                    CharacterActionKey.NORMAL));
            int hits = step == 3 ? 2 : 1;
            for (int hit = 0; hit < hits; hit++) {
                assertClose(expected[record],
                        records.get(record).action.getDamagePercent(),
                        "Mika Normal multiplier");
                assertEquals(Element.PHYSICAL,
                        records.get(record).action.getElement(),
                        "Mika physical Normal element");
                record++;
            }
        }
        perform(simulator, CharacterActionRequest.of(
                CharacterActionKey.CHARGE));
        assertClose(2.071380,
                named(records, "Spear of Favonius Charged").get(0)
                        .action.getDamagePercent(),
                "Mika Charged multiplier");

        perform(simulator, CharacterActionRequest.of(
                CharacterActionKey.NORMAL));
        simulator.switchCharacter(CharacterId.NOELLE);
        simulator.switchCharacter(CharacterId.MIKA);
        perform(simulator, CharacterActionRequest.of(
                CharacterActionKey.NORMAL));
        List<ActionRecord> n1 = named(records, "Spear of Favonius N1");
        assertTrue(n1.size() >= 2,
                "Mika switch-out resets the Normal string");
    }

    private static void testPressSoulwindParticlesAndRestore() {
        Mika mika = new Mika(null, null, 0);
        TestCharacter ally = new TestCharacter(
                CharacterId.NOELLE, Element.GEO);
        CombatSimulator simulator = simulatorWith(mika, ally);
        List<ActionRecord> records = captureActions(simulator);
        List<Double> particles = captureCryoParticles(simulator);
        perform(simulator, CharacterActionRequest.skill(
                SkillActionMode.PRESS));
        ActionRecord press = named(records, "Flowfrost Arrow").get(0);
        assertClose(18.0 * FRAME, press.time,
                "Mika Press impact frame");
        assertClose(1.142400, press.action.getDamagePercent(),
                "Mika C0 Press Talent 9");
        assertClose(0.21,
                effectiveStats(simulator, ally).get(StatType.ATK_SPD),
                "Mika Talent 9 Soulwind team speed");
        assertTrue(mika.getSkillCDRemaining(
                simulator.getCurrentTime()) > 14.0,
                "Mika cooldown begins at frame sixteen");

        SimulatorSnapshot pendingParticle = simulator.saveSnapshot();
        advanceTo(simulator, 3.0);
        assertEquals(1, particles.size(),
                "Mika Press emits one particle packet");
        assertClose(4.0, particles.get(0),
                "Mika Skill emits four particles");
        simulator.restoreSnapshot(pendingParticle);
        advanceTo(simulator, 3.0);
        assertEquals(2, particles.size(),
                "Mika restores the pending particle packet once");
    }

    private static void testHoldC2C5C6AndIsolation() {
        Mika mika = new Mika(null, null, 6);
        TestCharacter ally = new TestCharacter(
                CharacterId.NOELLE, Element.GEO);
        CombatSimulator simulator = simulatorWith(mika, ally);
        List<ActionRecord> records = captureActions(simulator);
        perform(simulator, CharacterActionRequest.skill(
                SkillActionMode.HOLD));
        ActionRecord hold = named(records, "Rimestar Flare").get(0);
        assertClose(15.0 * FRAME, hold.time,
                "Mika Hold impact frame");
        assertClose(1.680000, hold.action.getDamagePercent(),
                "Mika C5 Hold Talent 12");
        assertClose(0.24,
                effectiveStats(simulator, mika).get(StatType.ATK_SPD),
                "Mika C5 Soulwind speed");
        assertEquals(1, mika.getDetectorStacks(
                simulator.getCurrentTime()),
                "Mika C2 fixed-target hit grants one Detector stack");

        AttackAction physical = testAction(Element.PHYSICAL);
        StatsContainer physicalStats = new StatsContainer();
        mika.applyTargetDependentTeamStats(
                physicalStats,
                mika,
                simulator.getEnemy(),
                physical,
                simulator.getCurrentTime());
        assertClose(0.10,
                physicalStats.get(StatType.PHYSICAL_DMG_BONUS),
                "Mika Detector grants Physical damage");
        assertClose(0.60,
                physicalStats.get(StatType.PHYSICAL_CRIT_DMG),
                "Mika C6 grants Physical-only CRIT DMG");

        StatsContainer cryoStats = new StatsContainer();
        mika.applyTargetDependentTeamStats(
                cryoStats,
                mika,
                simulator.getEnemy(),
                testAction(Element.CRYO),
                simulator.getCurrentTime());
        assertClose(0.0, cryoStats.get(StatType.PHYSICAL_DMG_BONUS),
                "Mika Detector excludes Cryo hits");
        assertClose(0.0, cryoStats.get(StatType.PHYSICAL_CRIT_DMG),
                "Mika C6 excludes Cryo hits");

        simulator.switchCharacter(CharacterId.NOELLE);
        StatsContainer activeAllyStats = new StatsContainer();
        mika.applyTargetDependentTeamStats(
                activeAllyStats,
                ally,
                simulator.getEnemy(),
                physical,
                simulator.getCurrentTime());
        assertClose(0.10,
                activeAllyStats.get(StatType.PHYSICAL_DMG_BONUS),
                "Mika Detector follows the active teammate");
        assertClose(0.60,
                activeAllyStats.get(StatType.PHYSICAL_CRIT_DMG),
                "Mika C6 follows the active teammate");
        StatsContainer offFieldMikaStats = new StatsContainer();
        mika.applyTargetDependentTeamStats(
                offFieldMikaStats,
                mika,
                simulator.getEnemy(),
                physical,
                simulator.getCurrentTime());
        assertClose(0.0,
                offFieldMikaStats.get(StatType.PHYSICAL_DMG_BONUS),
                "Mika Detector excludes off-field attacks");
    }

    private static void testSoulwindExpirationBoundary() {
        Mika mika = new Mika(null, null, 0);
        CombatSimulator simulator = simulatorWith(mika);
        perform(simulator, CharacterActionRequest.skill(
                SkillActionMode.PRESS));
        double expiration = 12.0 + 16.0 * FRAME;
        advanceTo(simulator, expiration - 2.0 * EPSILON);
        assertTrue(mika.isSoulwindActive(simulator.getCurrentTime()),
                "Mika Soulwind is active before expiry");
        advanceTo(simulator, expiration);
        assertTrue(!mika.isSoulwindActive(simulator.getCurrentTime()),
                "Mika Soulwind expires at the exact boundary");
        assertClose(0.0,
                effectiveStats(simulator, mika).get(StatType.ATK_SPD),
                "Mika Soulwind speed expires at the exact boundary");
    }

    private static void testGuards() {
        assertThrows(IllegalArgumentException.class,
                () -> new Mika(null, null, -1),
                "Mika rejects negative constellation");
        assertThrows(IllegalArgumentException.class,
                () -> new Mika(null, null, 7),
                "Mika rejects constellation above C6");
        Mika mika = new Mika(null, null, 0);
        CombatSimulator simulator = simulatorWith(mika);
        assertThrows(IllegalArgumentException.class,
                () -> mika.onAction(null, simulator),
                "Mika rejects null action");
        assertThrows(IllegalArgumentException.class,
                () -> perform(simulator, CharacterActionRequest.of(
                        CharacterActionKey.BURST)),
                "Mika rejects deferred Burst");
        Mika external = new Mika(null, null, 0);
        assertThrows(IllegalArgumentException.class,
                () -> external.initializeForSimulator(new CombatSimulator()),
                "Mika rejects binding outside simulator party");
        Mika reused = new Mika(null, null, 0);
        simulatorWith(reused);
        assertThrows(IllegalStateException.class,
                () -> simulatorWith(reused),
                "Mika rejects cross-simulator reuse");
        SnapshotAwareCharacterEffect.State foreignState =
                external.captureCharacterState();
        assertTrue(!mika.acceptsCharacterState(foreignState),
                "Mika rejects another instance snapshot payload");
    }

    private static AttackAction testAction(Element element) {
        return new AttackAction(
                "Mika support test",
                1.0,
                element,
                StatType.BASE_ATK,
                StatType.NORMAL_ATTACK_DMG_BONUS,
                0.0,
                ActionType.NORMAL);
    }

    private static CombatSimulator simulatorWith(Character... characters) {
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
        simulator.performAction(CharacterId.MIKA, request);
    }

    private static List<ActionRecord> captureActions(
            CombatSimulator simulator) {
        List<ActionRecord> records = new ArrayList<>();
        simulator.addDamageListener((actor, action, damage, time) -> {
            if (actor.getCharacterId() == CharacterId.MIKA) {
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
            assertTrue(lines.get(index).startsWith("Mika,"),
                    path + " identity at line " + (index + 1));
        }
    }

    private static void assertCsvValue(String key, double expected)
            throws IOException {
        for (String path : new String[] {
                "config/characters/Mika/Mika_Status.csv",
                "config/characters/Mika/Mika_Multipliers.csv"
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
        throw new AssertionError("Mika CSVs missing key " + key);
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

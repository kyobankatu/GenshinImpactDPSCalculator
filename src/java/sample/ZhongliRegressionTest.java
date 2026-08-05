package sample;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import mechanics.data.TalentDataManager;
import model.character.Zhongli;
import model.entity.Character;
import model.entity.Enemy;
import model.stats.StatsContainer;
import model.type.ActionType;
import model.type.CharacterId;
import model.type.CharacterRegion;
import model.type.Element;
import model.type.ICDType;
import model.type.StatType;
import simulation.CombatSimulator;
import simulation.SimulatorSnapshot;
import simulation.action.AttackAction;
import simulation.action.CharacterActionKey;
import simulation.action.CharacterActionRequest;
import simulation.action.SkillActionMode;

/** Focused checks for Zhongli's represented Stone Stele offensive slice. */
public final class ZhongliRegressionTest {
    private static final double FRAME = 1.0 / 60.0;
    private static final double EPSILON = 1e-8;

    private ZhongliRegressionTest() {
    }

    /** Runs identity, action, construct, constellation, and rollback checks. */
    public static void main(String[] args) throws Exception {
        testIdentityStatsDataAndGuards();
        testNormalsChargePlungeAndA4();
        testPressStelePulsesParticlesAndReplacement();
        testHoldAndBurstConstellations();
        testSnapshotRestoreAndReuseGuards();
        System.out.println("ZhongliRegressionTest passed");
    }

    private static void testIdentityStatsDataAndGuards()
            throws IOException {
        Zhongli zhongli = zhongli(0, 0.25);
        assertEquals(CharacterId.ZHONGLI, zhongli.getCharacterId(),
                "Zhongli typed identity");
        assertEquals(CharacterId.ZHONGLI, CharacterId.fromNumericId(66),
                "Zhongli numeric identity");
        assertEquals(CharacterId.ZHONGLI,
                CharacterId.fromName("Zhongli"),
                "Zhongli display-name identity");
        assertEquals(CharacterRegion.LIYUE,
                CharacterId.ZHONGLI.getRegion(), "Zhongli region");
        assertEquals(Element.GEO, zhongli.getElement(), "Zhongli element");
        assertClose(14695.0,
                zhongli.getBaseStats().get(StatType.BASE_HP),
                "Zhongli base HP");
        assertClose(251.0,
                zhongli.getBaseStats().get(StatType.BASE_ATK),
                "Zhongli base ATK");
        assertClose(738.0,
                zhongli.getBaseStats().get(StatType.BASE_DEF),
                "Zhongli base DEF");
        assertClose(0.288,
                zhongli.getBaseStats().get(StatType.GEO_DMG_BONUS),
                "Zhongli ascension Geo bonus");
        assertClose(40.0, zhongli.getEnergyCost(),
                "Zhongli Energy cost");
        assertClose(4.0, zhongli.getSkillCD(),
                "Zhongli default Press cooldown");
        assertClose(12.0, zhongli.getBurstCD(),
                "Zhongli Burst cooldown");
        for (int constellation = 0; constellation <= 6;
                constellation++) {
            assertEquals(constellation,
                    zhongli(constellation, 0.25).getConstellation(),
                    "Zhongli constellation " + constellation);
        }
        assertCsvShape(Path.of(
                "config/characters/Zhongli/Zhongli_Status.csv"), 22);
        assertCsvShape(Path.of(
                "config/characters/Zhongli/Zhongli_Multipliers.csv"), 16);
        assertThrows(IllegalArgumentException.class,
                () -> zhongli(-1, 0.25),
                "Zhongli rejects negative constellation");
        assertThrows(IllegalArgumentException.class,
                () -> zhongli(7, 0.25),
                "Zhongli rejects constellation above C6");
        assertThrows(IllegalArgumentException.class,
                () -> new Zhongli(
                        null,
                        null,
                        TalentDataManager.getInstance(),
                        0,
                        null),
                "Zhongli rejects null particle randomness");

        CombatSimulator simulator = simulatorWith(zhongli);
        assertThrows(IllegalArgumentException.class,
                () -> zhongli.onAction(null, simulator),
                "Zhongli rejects null action");
    }

    private static void testNormalsChargePlungeAndA4() {
        Zhongli zhongli = zhongli(0, 0.75);
        CombatSimulator simulator = simulatorWith(zhongli);
        List<ActionRecord> records = captureActions(simulator);
        double[] expected = {
            0.565292, 0.572323, 0.708725, 0.788878,
            0.197500, 0.197500, 0.197500, 0.197500, 1.001214
        };
        for (int step = 0; step < 6; step++) {
            perform(simulator, CharacterActionKey.NORMAL);
        }
        assertEquals(9, records.size(), "Zhongli Normal hit count");
        for (int index = 0; index < records.size(); index++) {
            assertClose(expected[index],
                    records.get(index).action.getDamagePercent(),
                    "Zhongli Normal multiplier " + index);
            assertClose(204.2605,
                    records.get(index).action.getAdditiveBaseDmgBonus(),
                    "Zhongli A4 Normal flat addition " + index);
            assertEquals(Element.PHYSICAL,
                    records.get(index).action.getElement(),
                    "Zhongli Normal element " + index);
        }

        records.clear();
        perform(simulator, CharacterActionKey.CHARGE);
        assertEquals(1, records.size(), "Zhongli Charged hit count");
        assertClose(2.039780, records.get(0).action.getDamagePercent(),
                "Zhongli Charged multiplier");
        assertClose(204.2605,
                records.get(0).action.getAdditiveBaseDmgBonus(),
                "Zhongli A4 Charged addition");

        records.clear();
        perform(simulator, CharacterActionKey.PLUNGE);
        assertEquals(1, records.size(), "Zhongli high Plunge hit count");
        assertClose(2.747916, records.get(0).action.getDamagePercent(),
                "Zhongli high Plunge multiplier");
        assertEquals(ICDType.None, records.get(0).action.getICDType(),
                "Zhongli high Plunge has no ICD");
    }

    private static void testPressStelePulsesParticlesAndReplacement() {
        Zhongli zhongli = zhongli(0, 0.25);
        CombatSimulator simulator = simulatorWith(zhongli);
        List<ActionRecord> records = captureActions(simulator);
        List<Double> particles = captureGeoParticles(simulator);
        perform(simulator, CharacterActionKey.SKILL);
        assertTrue(zhongli.isStoneSteleActive(
                simulator.getCurrentTime()),
                "Press creates Stone Stele at frame 24");
        assertClose(22.0 * FRAME, zhongli.getLastSkillTime(),
                "Press cooldown starts at frame 22");
        ActionRecord initial = named(records, "Stone Stele Initial").get(0);
        assertClose(0.272000, initial.action.getDamagePercent(),
                "C0 Stone Stele initial multiplier");
        assertClose(279.205,
                initial.action.getAdditiveBaseDmgBonus(),
                "A4 Stele Max-HP addition");
        assertClose(initial.time + 31.0,
                zhongli.getStoneSteleExpirationTime(),
                "Stone Stele lasts 31 seconds");

        simulator.advanceTime(4.0);
        List<ActionRecord> pulses = named(records, "Stone Stele Pulse");
        assertEquals(2, pulses.size(),
                "Stone Stele pulses every two seconds");
        assertClose(2.0, pulses.get(1).time - pulses.get(0).time,
                "Stone Stele exact pulse interval");
        assertClose(0.544000,
                pulses.get(0).action.getDamagePercent(),
                "C0 Stone Stele pulse multiplier");
        assertTrue(particles.size() >= 2,
                "Successful particle draws create Geo packets");
        for (double count : particles) {
            assertClose(1.0, count,
                    "Stone Stele packet contains one particle");
        }

        double priorExpiry = zhongli.getStoneSteleExpirationTime();
        perform(simulator, CharacterActionKey.SKILL);
        assertTrue(zhongli.getStoneSteleExpirationTime() > priorExpiry,
                "Press recast replaces and refreshes the Stone Stele");
        int pulseCount = named(records, "Stone Stele Pulse").size();
        simulator.advanceTime(2.1);
        assertEquals(pulseCount + 1,
                named(records, "Stone Stele Pulse").size(),
                "Only replacement generation continues pulsing");
    }

    private static void testHoldAndBurstConstellations() {
        Zhongli c3 = zhongli(3, 0.75);
        CombatSimulator simulator = simulatorWith(c3);
        List<ActionRecord> records = captureActions(simulator);
        performSkill(simulator, SkillActionMode.HOLD);
        assertClose(47.0 * FRAME, c3.getLastSkillTime(),
                "Hold cooldown starts at frame 47");
        assertClose(12.0 - (96.0 - 47.0) * FRAME,
                c3.getSkillCDRemaining(simulator.getCurrentTime()),
                "Hold stores its 12-second cooldown");
        ActionRecord hold = named(records, "Dominus Lapidis Hold").get(0);
        assertClose(1.600000, hold.action.getDamagePercent(),
                "C3 raises Hold damage");
        ActionRecord initial = named(records, "Stone Stele Initial").get(0);
        assertClose(0.320000, initial.action.getDamagePercent(),
                "C3 raises Stone Stele initial damage");
        double expiry = c3.getStoneSteleExpirationTime();

        advanceTo(simulator, c3.getLastSkillTime() + 12.0);
        performSkill(simulator, SkillActionMode.HOLD);
        assertClose(expiry, c3.getStoneSteleExpirationTime(),
                "Hold does not replace an existing Stone Stele");

        Zhongli c5 = zhongli(5, 0.75);
        CombatSimulator burstSimulator = simulatorWith(c5);
        List<ActionRecord> burstRecords = captureActions(burstSimulator);
        perform(burstSimulator, CharacterActionKey.BURST);
        AttackAction burst = named(burstRecords, "Planet Befall")
                .get(0).action;
        assertClose(10.298000, burst.getDamagePercent(),
                "C5 raises Planet Befall damage");
        assertClose(4849.35, burst.getAdditiveBaseDmgBonus(),
                "A4 Planet Befall Max-HP addition");
        assertEquals(Element.GEO, burst.getElement(),
                "Planet Befall element");
        assertEquals(ActionType.BURST, burst.getActionType(),
                "Planet Befall category");
        assertClose(0.0, c5.getCurrentEnergy(),
                "Planet Befall spends Energy at frame seven");
    }

    private static void testSnapshotRestoreAndReuseGuards() {
        Zhongli zhongli = zhongli(3, 0.25);
        CombatSimulator simulator = simulatorWith(zhongli);
        perform(simulator, CharacterActionKey.SKILL);
        SimulatorSnapshot snapshot = simulator.saveSnapshot();
        simulator.advanceTime(2.1);
        assertTrue(zhongli.getPendingHitCount() >= 1,
                "Stone Stele pulse schedules its successor");
        simulator.restoreSnapshot(snapshot);
        List<ActionRecord> records = captureActions(simulator);
        simulator.advanceTime(2.1);
        assertEquals(1, named(records, "Stone Stele Pulse").size(),
                "Restored Stone Stele pulse resolves once");

        Zhongli foreign = zhongli(0, 0.25);
        assertTrue(!zhongli.acceptsCharacterState(
                foreign.captureCharacterState()),
                "Zhongli rejects foreign snapshot payload");
        assertThrows(IllegalArgumentException.class,
                () -> zhongli.restoreCharacterState(null, simulator),
                "Zhongli rejects null snapshot payload");
        assertThrows(IllegalStateException.class,
                () -> simulatorWith(zhongli),
                "Zhongli rejects cross-simulator reuse");

        Zhongli invalidRandom = zhongli(0, 1.0);
        CombatSimulator invalidSimulator = simulatorWith(invalidRandom);
        assertThrows(IllegalStateException.class,
                () -> perform(invalidSimulator, CharacterActionKey.SKILL),
                "Zhongli rejects out-of-range particle random draw");
    }

    private static Zhongli zhongli(int constellation, double particleDraw) {
        return new Zhongli(
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
                CharacterId.ZHONGLI, CharacterActionRequest.of(key));
    }

    private static void performSkill(
            CombatSimulator simulator,
            SkillActionMode mode) {
        simulator.performAction(
                CharacterId.ZHONGLI,
                CharacterActionRequest.skill(mode));
    }

    private static List<ActionRecord> captureActions(
            CombatSimulator simulator) {
        List<ActionRecord> records = new ArrayList<>();
        simulator.addDamageListener((actor, action, damage, time) -> {
            if (actor.getCharacterId() == CharacterId.ZHONGLI) {
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
            assertTrue(lines.get(index).startsWith("Zhongli,"),
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
}

package sample;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import mechanics.data.TalentDataManager;
import model.character.HuTao;
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

/** Focused checks for Hu Tao's represented Paramita offensive slice. */
public final class HuTaoRegressionTest {
    private static final double FRAME = 1.0 / 60.0;
    private static final double EPSILON = 1e-8;

    private HuTaoRegressionTest() {
    }

    /** Runs identity, action, state-window, constellation, and rollback checks. */
    public static void main(String[] args) throws Exception {
        testIdentityStatsDataAndGuards();
        testNormalChargeAndPlunge();
        testParamitaConversionInfusionParticlesAndA1();
        testBloodBlossomConstellationsAndBurst();
        testSnapshotRestoreAndReuseGuards();
        System.out.println("HuTaoRegressionTest passed");
    }

    private static void testIdentityStatsDataAndGuards()
            throws IOException {
        HuTao huTao = huTao(0, 0.75);
        assertEquals(CharacterId.HU_TAO, huTao.getCharacterId(),
                "Hu Tao typed identity");
        assertEquals(CharacterId.HU_TAO, CharacterId.fromNumericId(63),
                "Hu Tao numeric identity");
        assertEquals(CharacterId.HU_TAO, CharacterId.fromName("Hu Tao"),
                "Hu Tao display-name identity");
        assertEquals(CharacterRegion.LIYUE,
                CharacterId.HU_TAO.getRegion(), "Hu Tao region");
        assertEquals(Element.PYRO, huTao.getElement(), "Hu Tao element");
        assertClose(15552.0,
                huTao.getBaseStats().get(StatType.BASE_HP),
                "Hu Tao base HP");
        assertClose(106.0,
                huTao.getBaseStats().get(StatType.BASE_ATK),
                "Hu Tao base ATK");
        assertClose(876.0,
                huTao.getBaseStats().get(StatType.BASE_DEF),
                "Hu Tao base DEF");
        assertClose(0.884,
                huTao.getBaseStats().get(StatType.CRIT_DMG),
                "Hu Tao total base CRIT DMG");
        assertClose(60.0, huTao.getEnergyCost(), "Hu Tao Energy cost");
        assertClose(16.0, huTao.getSkillCD(), "Hu Tao Skill cooldown");
        assertClose(15.0, huTao.getBurstCD(), "Hu Tao Burst cooldown");
        for (int constellation = 0; constellation <= 6;
                constellation++) {
            assertEquals(constellation,
                    huTao(constellation, 0.75).getConstellation(),
                    "Hu Tao constellation " + constellation);
        }
        assertCsvShape(Path.of(
                "config/characters/HuTao/HuTao_Status.csv"), 23);
        assertCsvShape(Path.of(
                "config/characters/HuTao/HuTao_Multipliers.csv"), 15);
        assertThrows(IllegalArgumentException.class,
                () -> huTao(-1, 0.75),
                "Hu Tao rejects negative constellation");
        assertThrows(IllegalArgumentException.class,
                () -> huTao(7, 0.75),
                "Hu Tao rejects constellation above C6");
        assertThrows(IllegalArgumentException.class,
                () -> new HuTao(
                        null,
                        null,
                        TalentDataManager.getInstance(),
                        0,
                        null),
                "Hu Tao rejects null particle randomness");

        CombatSimulator simulator = simulatorWith(huTao);
        assertThrows(IllegalArgumentException.class,
                () -> huTao.onAction(null, simulator),
                "Hu Tao rejects null action");
    }

    private static void testNormalChargeAndPlunge() {
        HuTao huTao = huTao(0, 0.75);
        CombatSimulator simulator = simulatorWith(huTao);
        List<ActionRecord> records = captureActions(simulator);
        double[] expected = {
            0.788544, 0.811543, 1.026750, 1.103962,
            0.559603, 0.592000, 1.445664
        };
        for (int step = 0; step < 6; step++) {
            perform(simulator, CharacterActionKey.NORMAL);
        }
        assertEquals(7, records.size(), "Hu Tao Normal hit count");
        for (int index = 0; index < expected.length; index++) {
            assertClose(expected[index],
                    records.get(index).action.getDamagePercent(),
                    "Hu Tao Normal multiplier " + index);
            assertEquals(Element.PHYSICAL,
                    records.get(index).action.getElement(),
                    "Hu Tao uninfused Normal element " + index);
            assertEquals(ActionType.NORMAL,
                    records.get(index).action.getActionType(),
                    "Hu Tao Normal category " + index);
        }

        records.clear();
        perform(simulator, CharacterActionKey.CHARGE);
        assertEquals(1, records.size(), "Hu Tao Charged hit count");
        assertClose(2.286600, records.get(0).action.getDamagePercent(),
                "Hu Tao Charged multiplier");
        assertEquals(Element.PHYSICAL,
                records.get(0).action.getElement(),
                "Hu Tao uninfused Charged element");
        assertClose(62.0 * FRAME,
                simulator.getCurrentTime() - records.get(0).time
                        + 19.0 * FRAME,
                "Hu Tao uninfused Charged duration");

        records.clear();
        perform(simulator, CharacterActionKey.PLUNGE);
        assertEquals(1, records.size(), "Hu Tao high Plunge hit count");
        assertClose(2.747916, records.get(0).action.getDamagePercent(),
                "Hu Tao high Plunge multiplier");
        assertEquals(ICDType.None, records.get(0).action.getICDType(),
                "Hu Tao high Plunge has no ICD");
    }

    private static void testParamitaConversionInfusionParticlesAndA1() {
        HuTao huTao = huTao(0, 0.25);
        TestCharacter ally = new TestCharacter(
                CharacterId.NOELLE, Element.GEO);
        CombatSimulator simulator = simulatorWith(huTao, ally);
        List<ActionRecord> records = captureActions(simulator);
        List<Double> particles = capturePyroParticles(simulator);

        perform(simulator, CharacterActionKey.SKILL);
        assertTrue(huTao.isParamitaActive(simulator.getCurrentTime()),
                "Hu Tao Skill activates Paramita");
        assertClose(14.0 * FRAME, huTao.getLastSkillTime(),
                "Hu Tao Skill cooldown starts at frame fourteen");
        perform(simulator, CharacterActionKey.NORMAL);
        AttackAction normal = records.get(0).action;
        assertEquals(Element.PYRO, normal.getElement(),
                "Paramita infuses Normal with Pyro");
        assertClose(424.0,
                normal.getStatSnapshot().get(StatType.ATK_FLAT),
                "Paramita conversion obeys four-times-base-ATK cap");
        simulator.advanceTime(2.0);
        assertEquals(1, particles.size(),
                "First infused hit creates one particle packet");
        assertClose(3.0, particles.get(0),
                "Low random draw selects three particles");

        double expiry = 554.0 * FRAME;
        assertTrue(huTao.isParamitaActive(expiry - EPSILON),
                "Paramita remains active before exact expiry");
        advanceTo(simulator, expiry);
        assertTrue(!huTao.isParamitaActive(expiry),
                "Paramita expires at the half-open boundary");
        assertClose(0.17,
                ally.getEffectiveStats(expiry).get(StatType.CRIT_RATE),
                "A1 grants non-owner party CRIT Rate");
        assertClose(0.05,
                huTao.getEffectiveStats(expiry).get(StatType.CRIT_RATE),
                "A1 excludes Hu Tao");
        assertClose(0.05,
                ally.getEffectiveStats(expiry + 8.0)
                        .get(StatType.CRIT_RATE),
                "A1 expires at eight seconds");

        HuTao switched = huTao(0, 0.75);
        TestCharacter switchAlly = new TestCharacter(
                CharacterId.NOELLE, Element.GEO);
        CombatSimulator switchSimulator = simulatorWith(
                switched, switchAlly);
        perform(switchSimulator, CharacterActionKey.SKILL);
        switchSimulator.switchCharacter(CharacterId.NOELLE);
        assertTrue(!switched.isParamitaActive(
                switchSimulator.getCurrentTime()),
                "Switch-out ends Paramita immediately");
        assertClose(0.17,
                switchAlly.getEffectiveStats(
                        switchSimulator.getCurrentTime())
                        .get(StatType.CRIT_RATE),
                "Switch-out grants A1 immediately");
    }

    private static void testBloodBlossomConstellationsAndBurst() {
        HuTao c0 = huTao(0, 0.75);
        CombatSimulator c0Simulator = simulatorWith(c0);
        List<ActionRecord> c0Records = captureActions(c0Simulator);
        perform(c0Simulator, CharacterActionKey.SKILL);
        perform(c0Simulator, CharacterActionKey.CHARGE);
        assertClose(9.5,
                c0.getBloodBlossomExpirationTime()
                        - c0Records.get(0).time,
                "Charged hit applies exact Blood Blossom duration");
        c0Simulator.advanceTime(8.5);
        List<ActionRecord> c0Blossoms = named(c0Records, "Blood Blossom");
        assertEquals(2, c0Blossoms.size(),
                "Blood Blossom ticks twice at four-second cadence");
        assertClose(1.088000,
                c0Blossoms.get(0).action.getDamagePercent(),
                "C0 Blood Blossom multiplier");
        assertClose(0.0,
                c0Blossoms.get(0).action.getAdditiveBaseDmgBonus(),
                "C0 Blood Blossom has no C2 flat damage");

        HuTao c3 = huTao(3, 0.75);
        CombatSimulator c3Simulator = simulatorWith(c3);
        List<ActionRecord> c3Records = captureActions(c3Simulator);
        perform(c3Simulator, CharacterActionKey.SKILL);
        perform(c3Simulator, CharacterActionKey.CHARGE);
        c3Simulator.advanceTime(4.1);
        AttackAction c3Blossom = named(
                c3Records, "Blood Blossom").get(0).action;
        assertClose(1.280000, c3Blossom.getDamagePercent(),
                "C3 raises Blood Blossom talent value");
        assertClose(1555.2, c3Blossom.getAdditiveBaseDmgBonus(),
                "C2 adds ten percent Max HP to Blood Blossom");

        HuTao c5 = huTao(5, 0.75);
        CombatSimulator c5Simulator = simulatorWith(c5);
        List<ActionRecord> c5Records = captureActions(c5Simulator);
        perform(c5Simulator, CharacterActionKey.BURST);
        AttackAction burst = named(c5Records, "Spirit Soother")
                .get(0).action;
        assertClose(5.411680, burst.getDamagePercent(),
                "C5 raises Spirit Soother talent value");
        assertEquals(Element.PYRO, burst.getElement(),
                "Spirit Soother is Pyro");
        assertEquals(ActionType.BURST, burst.getActionType(),
                "Spirit Soother category");
        assertClose(0.0, c5.getCurrentEnergy(),
                "Spirit Soother spends Energy at frame 68");
        assertTrue(c5.getBloodBlossomExpirationTime()
                        > c5Simulator.getCurrentTime(),
                "C2 Burst hit applies Blood Blossom");
    }

    private static void testSnapshotRestoreAndReuseGuards() {
        HuTao huTao = huTao(3, 0.75);
        CombatSimulator simulator = simulatorWith(huTao);
        perform(simulator, CharacterActionKey.SKILL);
        perform(simulator, CharacterActionKey.CHARGE);
        SimulatorSnapshot snapshot = simulator.saveSnapshot();
        simulator.advanceTime(4.1);
        assertEquals(1, huTao.getPendingHitCount(),
                "First Blood Blossom tick schedules the second");
        simulator.restoreSnapshot(snapshot);
        List<ActionRecord> records = captureActions(simulator);
        simulator.advanceTime(4.1);
        assertEquals(1, named(records, "Blood Blossom").size(),
                "Restored Blood Blossom tick resolves once");

        HuTao foreign = huTao(0, 0.75);
        assertTrue(!huTao.acceptsCharacterState(
                foreign.captureCharacterState()),
                "Hu Tao rejects foreign snapshot payload");
        assertThrows(IllegalArgumentException.class,
                () -> huTao.restoreCharacterState(null, simulator),
                "Hu Tao rejects null snapshot payload");
        assertThrows(IllegalStateException.class,
                () -> simulatorWith(huTao),
                "Hu Tao rejects cross-simulator reuse");

        HuTao invalidRandom = huTao(0, 1.0);
        CombatSimulator invalidSimulator = simulatorWith(invalidRandom);
        perform(invalidSimulator, CharacterActionKey.SKILL);
        assertThrows(IllegalStateException.class,
                () -> perform(invalidSimulator, CharacterActionKey.NORMAL),
                "Hu Tao rejects out-of-range particle random draw");
    }

    private static HuTao huTao(int constellation, double particleDraw) {
        return new HuTao(
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
        enemy.setRes(StatType.PYRO_DMG_BONUS, 0.0);
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
                CharacterId.HU_TAO, CharacterActionRequest.of(key));
    }

    private static List<ActionRecord> captureActions(
            CombatSimulator simulator) {
        List<ActionRecord> records = new ArrayList<>();
        simulator.addDamageListener((actor, action, damage, time) -> {
            if (actor.getCharacterId() == CharacterId.HU_TAO) {
                records.add(new ActionRecord(action, time));
            }
        });
        return records;
    }

    private static List<Double> capturePyroParticles(
            CombatSimulator simulator) {
        List<Double> records = new ArrayList<>();
        simulator.addParticleListener((element, count, time) -> {
            if (element == Element.PYRO) {
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
            assertTrue(lines.get(index).startsWith("Hu Tao,"),
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

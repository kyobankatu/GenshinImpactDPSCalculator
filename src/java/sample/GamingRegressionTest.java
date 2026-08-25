package sample;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import model.character.Gaming;
import model.entity.Character;
import model.entity.Enemy;
import model.stats.StatsContainer;
import model.type.ActionType;
import model.type.CharacterId;
import model.type.Element;
import model.type.ICDType;
import model.type.StatType;
import simulation.CombatSimulator;
import simulation.SimulatorSnapshot;
import simulation.action.AttackAction;
import simulation.action.CharacterActionKey;
import simulation.action.CharacterActionRequest;

/** Focused checks for Gaming's represented Charmed Cloudstrider slice. */
public final class GamingRegressionTest {
    private static final double FRAME = 1.0 / 60.0;
    private static final double EPSILON = 1e-8;

    private GamingRegressionTest() {
    }

    /** Runs identity, action, Burst, constellation, and rollback checks. */
    public static void main(String[] args) throws Exception {
        testIdentityStatsDataAndGuards();
        testNormalStringAndHighPlunge();
        testSpecialPlungeParticlesAndConstellations();
        testBurstWindowManChaiAndSwitchBoundary();
        testSnapshotRestoreAndReuseGuards();
        System.out.println("GamingRegressionTest passed");
    }

    private static void testIdentityStatsDataAndGuards() throws IOException {
        Gaming gaming = new Gaming(null, null, 0);
        assertEquals(CharacterId.GAMING, gaming.getCharacterId(),
                "Gaming typed identity");
        assertEquals(Element.PYRO, gaming.getElement(), "Gaming element");
        assertClose(11419.0,
                gaming.getBaseStats().get(StatType.BASE_HP),
                "Gaming base HP");
        assertClose(302.0,
                gaming.getBaseStats().get(StatType.BASE_ATK),
                "Gaming base ATK");
        assertClose(703.0,
                gaming.getBaseStats().get(StatType.BASE_DEF),
                "Gaming base DEF");
        assertClose(0.24,
                gaming.getBaseStats().get(StatType.ATK_PERCENT),
                "Gaming ascension ATK");
        assertClose(60.0, gaming.getEnergyCost(), "Gaming Energy cost");
        assertClose(6.0, gaming.getSkillCD(), "Gaming Skill cooldown");
        assertClose(15.0, gaming.getBurstCD(), "Gaming Burst cooldown");
        for (int constellation = 0; constellation <= 6; constellation++) {
            assertEquals(CharacterId.GAMING,
                    new Gaming(null, null, constellation).getCharacterId(),
                    "Gaming constellation " + constellation);
        }
        assertCsvShape(Path.of(
                "config/characters/Gaming/Gaming_Status.csv"), 18);
        assertCsvShape(Path.of(
                "config/characters/Gaming/Gaming_Multipliers.csv"), 9);
        assertThrows(IllegalArgumentException.class,
                () -> new Gaming(null, null, -1),
                "Gaming rejects negative constellation");
        assertThrows(IllegalArgumentException.class,
                () -> new Gaming(null, null, 7),
                "Gaming rejects constellation above C6");

        CombatSimulator simulator = simulatorWith(gaming);
        assertThrows(IllegalArgumentException.class,
                () -> gaming.onAction(null, simulator),
                "Gaming rejects null action");
        assertThrows(IllegalArgumentException.class,
                () -> perform(simulator, CharacterActionKey.CHARGE),
                "Gaming rejects excluded Charged movement");
    }

    private static void testNormalStringAndHighPlunge() {
        Gaming gaming = new Gaming(null, null, 0);
        CombatSimulator simulator = simulatorWith(gaming);
        List<ActionRecord> records = captureActions(simulator);
        double[] expectedMultipliers = {
            1.540611, 1.452210, 1.959311, 2.350692
        };
        for (int index = 0; index < 4; index++) {
            perform(simulator, CharacterActionKey.NORMAL);
        }
        assertEquals(4, records.size(), "Gaming Normal hit count");
        for (int index = 0; index < records.size(); index++) {
            assertClose(expectedMultipliers[index],
                    records.get(index).action.getDamagePercent(),
                    "Gaming N" + (index + 1) + " multiplier");
            assertEquals(ActionType.NORMAL,
                    records.get(index).action.getActionType(),
                    "Gaming Normal category");
            assertEquals(Element.PHYSICAL,
                    records.get(index).action.getElement(),
                    "Gaming Normal element");
        }
        records.clear();
        perform(simulator, CharacterActionKey.PLUNGE);
        assertEquals(1, records.size(), "Gaming high Plunge hit count");
        assertClose(2.943366, records.get(0).action.getDamagePercent(),
                "Gaming high Plunge multiplier");
        assertEquals(ActionType.PLUNGE,
                records.get(0).action.getActionType(),
                "Gaming high Plunge category");
        assertEquals(Element.PHYSICAL,
                records.get(0).action.getElement(),
                "Gaming high Plunge element");
    }

    private static void testSpecialPlungeParticlesAndConstellations() {
        Gaming c6 = new Gaming(null, null, 6);
        CombatSimulator simulator = simulatorWith(c6);
        List<ActionRecord> records = captureActions(simulator);
        List<Double> particles = capturePyroParticles(simulator);
        c6.spendEnergy(60.0);
        perform(simulator, CharacterActionKey.SKILL);
        assertTrue(c6.isSpecialPlungeReady(),
                "Gaming Skill arms Charmed Cloudstrider");
        assertClose(4.0 * FRAME, c6.getLastSkillTime(),
                "Gaming Skill cooldown starts at frame four");
        perform(simulator, CharacterActionKey.PLUNGE);
        assertTrue(!c6.isSpecialPlungeReady(),
                "Gaming special Plunge consumes readiness");
        assertEquals(1, records.size(),
                "Gaming Charmed Cloudstrider hit count");
        AttackAction special = records.get(0).action;
        assertEquals("Charmed Cloudstrider", special.getName(),
                "Gaming special Plunge name");
        assertClose(4.608000, special.getDamagePercent(),
                "Gaming C3 special Plunge multiplier");
        assertEquals(Element.PYRO, special.getElement(),
                "Gaming special Plunge element");
        assertEquals(ActionType.PLUNGE, special.getActionType(),
                "Gaming special Plunge category");
        assertEquals(ICDType.None, special.getICDType(),
                "Gaming special Plunge has no ICD");
        assertClose(0.25,
                special.getStatSnapshot().get(StatType.CRIT_RATE),
                "Gaming C6 special Plunge CRIT Rate");
        assertClose(0.90,
                special.getStatSnapshot().get(StatType.CRIT_DMG),
                "Gaming C6 special Plunge CRIT DMG");
        assertClose(2.0, c6.getCurrentEnergy(),
                "Gaming C4 restores two flat Energy");
        simulator.advanceTime(2.0);
        assertEquals(1, particles.size(),
                "Gaming special Plunge produces one packet");
        assertClose(2.0, particles.get(0),
                "Gaming special Plunge particle count");

        Gaming c2 = new Gaming(null, null, 2);
        CombatSimulator c2Simulator = simulatorWith(c2);
        List<ActionRecord> c2Records = captureActions(c2Simulator);
        perform(c2Simulator, CharacterActionKey.SKILL);
        perform(c2Simulator, CharacterActionKey.PLUNGE);
        AttackAction c2Special = c2Records.get(0).action;
        assertClose(3.916800, c2Special.getDamagePercent(),
                "Gaming pre-C3 special Plunge multiplier");
        assertClose(0.05,
                c2Special.getStatSnapshot().get(StatType.CRIT_RATE),
                "Gaming C6 CRIT does not leak into C2");
        StatsContainer passive = c2.getEffectiveStats(
                c2Simulator.getCurrentTime());
        assertClose(0.24, passive.get(StatType.ATK_PERCENT),
                "Gaming excluded overheal C2 does not leak ATK");
    }

    private static void testBurstWindowManChaiAndSwitchBoundary() {
        Gaming gaming = new Gaming(null, null, 5);
        TestCharacter ally = new TestCharacter(
                CharacterId.NOELLE, Element.GEO);
        CombatSimulator simulator = simulatorWith(gaming, ally);
        List<ActionRecord> records = captureActions(simulator);
        perform(simulator, CharacterActionKey.BURST);
        assertEquals(1, records.size(), "Gaming Burst initial hit count");
        assertClose(7.408000, records.get(0).action.getDamagePercent(),
                "Gaming C5 Burst multiplier");
        assertEquals(Element.PYRO, records.get(0).action.getElement(),
                "Gaming Burst element");
        assertClose(0.0, gaming.getCurrentEnergy(),
                "Gaming Burst spends Energy at frame seven");
        assertTrue(gaming.isBurstActive(simulator.getCurrentTime()),
                "Gaming Burst active after animation");

        perform(simulator, CharacterActionKey.SKILL);
        assertTrue(!gaming.canSkill(simulator.getCurrentTime()),
                "Gaming Skill enters cooldown before Man Chai returns");
        advanceTo(simulator, 161.0 * FRAME + EPSILON);
        assertTrue(gaming.canSkill(simulator.getCurrentTime()),
                "Gaming initial Man Chai return resets Skill cooldown");

        perform(simulator, CharacterActionKey.SKILL);
        double secondPlungeStart = simulator.getCurrentTime();
        perform(simulator, CharacterActionKey.PLUNGE);
        double repeatedReturn = secondPlungeStart + 125.0 * FRAME;
        advanceTo(simulator, repeatedReturn - EPSILON);
        assertTrue(!gaming.canSkill(simulator.getCurrentTime()),
                "Gaming repeated Man Chai return respects 125-frame timing");
        advanceTo(simulator, repeatedReturn + EPSILON);
        assertTrue(gaming.canSkill(simulator.getCurrentTime()),
                "Gaming repeated Man Chai return resets Skill cooldown");
        assertTrue(gaming.isBurstActive(36.0 * FRAME + 12.0 - EPSILON),
                "Gaming Burst remains active before exact expiry");
        assertTrue(!gaming.isBurstActive(36.0 * FRAME + 12.0),
                "Gaming Burst expires at the half-open boundary");

        simulator.switchCharacter(CharacterId.NOELLE);
        assertTrue(!gaming.isBurstActive(simulator.getCurrentTime()),
                "Gaming switch-out cancels Burst immediately");
    }

    private static void testSnapshotRestoreAndReuseGuards() {
        Gaming gaming = new Gaming(null, null, 6);
        CombatSimulator simulator = simulatorWith(gaming);
        perform(simulator, CharacterActionKey.SKILL);
        SimulatorSnapshot snapshot = simulator.saveSnapshot();
        perform(simulator, CharacterActionKey.PLUNGE);
        assertTrue(!gaming.isSpecialPlungeReady(),
                "Gaming mutates special readiness after snapshot");
        simulator.restoreSnapshot(snapshot);
        assertTrue(gaming.isSpecialPlungeReady(),
                "Gaming restores special readiness");
        List<ActionRecord> records = captureActions(simulator);
        perform(simulator, CharacterActionKey.PLUNGE);
        assertEquals(1, records.size(),
                "Gaming restored special hit resolves once");

        Gaming foreign = new Gaming(null, null, 0);
        assertTrue(!gaming.acceptsCharacterState(
                foreign.captureCharacterState()),
                "Gaming rejects foreign snapshot payload");
        assertThrows(IllegalArgumentException.class,
                () -> gaming.restoreCharacterState(null, simulator),
                "Gaming rejects null snapshot payload");
        assertThrows(IllegalStateException.class,
                () -> simulatorWith(gaming),
                "Gaming rejects cross-simulator reuse");
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
                CharacterId.GAMING, CharacterActionRequest.of(key));
    }

    private static List<ActionRecord> captureActions(
            CombatSimulator simulator) {
        List<ActionRecord> records = new ArrayList<>();
        simulator.addDamageListener((actor, action, damage, time) -> {
            if (actor.getCharacterId() == CharacterId.GAMING) {
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
            assertTrue(lines.get(index).startsWith("Gaming,"),
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

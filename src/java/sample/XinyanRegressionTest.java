package sample;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import mechanics.buff.Buff;
import model.character.Xinyan;
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

/** Focused checks for Xinyan's direct Skill and Riff Revolution slice. */
public final class XinyanRegressionTest {
    private static final double EPSILON = 1e-8;

    private XinyanRegressionTest() {
    }

    /** Runs identity, action, constellation, window, and rollback checks. */
    public static void main(String[] args) throws Exception {
        testIdentityStatsDataAndGuards();
        testNormalAndPlungeActions();
        testSkillParticlesAndC4();
        testBurstSequenceAndC2();
        testSnapshotRestoreAndIsolation();
        System.out.println("XinyanRegressionTest passed");
    }

    private static void testIdentityStatsDataAndGuards() throws IOException {
        Xinyan xinyan = new Xinyan(null, null, 0);
        assertEquals(CharacterId.XINYAN, xinyan.getCharacterId(),
                "Xinyan typed identity");
        assertEquals(Element.PYRO, xinyan.getElement(), "Xinyan element");
        assertClose(11201.0,
                xinyan.getBaseStats().get(StatType.BASE_HP),
                "Xinyan base HP");
        assertClose(249.0,
                xinyan.getBaseStats().get(StatType.BASE_ATK),
                "Xinyan base ATK");
        assertClose(799.0,
                xinyan.getBaseStats().get(StatType.BASE_DEF),
                "Xinyan base DEF");
        assertClose(0.24,
                xinyan.getBaseStats().get(StatType.ATK_PERCENT),
                "Xinyan ascension ATK");
        assertClose(60.0, xinyan.getEnergyCost(), "Xinyan Energy cost");
        assertClose(18.0, xinyan.getSkillCD(), "Xinyan Skill cooldown");
        assertClose(15.0, xinyan.getBurstCD(), "Xinyan Burst cooldown");
        assertCsvShape(Path.of(
                "config/characters/Xinyan/Xinyan_Status.csv"), 17);
        assertCsvShape(Path.of(
                "config/characters/Xinyan/Xinyan_Multipliers.csv"), 11);
        assertThrows(IllegalArgumentException.class,
                () -> new Xinyan(null, null, -1),
                "Xinyan rejects negative constellation");
        assertThrows(IllegalArgumentException.class,
                () -> new Xinyan(null, null, 7),
                "Xinyan rejects constellation above C6");
        CombatSimulator simulator = simulatorWith(xinyan);
        assertThrows(IllegalArgumentException.class,
                () -> xinyan.onAction(null, simulator),
                "Xinyan rejects null action");
        assertThrows(IllegalArgumentException.class,
                () -> perform(simulator, CharacterActionKey.CHARGE),
                "Xinyan rejects excluded Charged Attack");
    }

    private static void testNormalAndPlungeActions() {
        Xinyan xinyan = new Xinyan(null, null, 0);
        CombatSimulator simulator = simulatorWith(xinyan);
        List<ActionRecord> records = captureActions(simulator);
        double[] multipliers = {
            1.406200, 1.358800, 1.753800, 2.128260
        };
        for (int index = 0; index < 4; index++) {
            perform(simulator, CharacterActionKey.NORMAL);
        }
        assertEquals(4, records.size(), "Xinyan Normal hit count");
        for (int index = 0; index < records.size(); index++) {
            assertClose(multipliers[index],
                    records.get(index).action.getDamagePercent(),
                    "Xinyan N" + (index + 1) + " multiplier");
            assertEquals(ActionType.NORMAL,
                    records.get(index).action.getActionType(),
                    "Xinyan Normal category");
            assertEquals(Element.PHYSICAL,
                    records.get(index).action.getElement(),
                    "Xinyan Normal element");
        }
        records.clear();
        perform(simulator, CharacterActionKey.PLUNGE);
        assertEquals(1, records.size(), "Xinyan high Plunge count");
        assertClose(3.422517, records.get(0).action.getDamagePercent(),
                "Xinyan high Plunge multiplier");
        assertEquals(ActionType.PLUNGE,
                records.get(0).action.getActionType(),
                "Xinyan high Plunge category");
    }

    private static void testSkillParticlesAndC4() {
        Xinyan c4 = new Xinyan(null, null, 4);
        TestCharacter ally = new TestCharacter(
                CharacterId.NOELLE, Element.GEO);
        CombatSimulator simulator = simulatorWith(c4, ally);
        List<ActionRecord> records = captureActions(simulator);
        List<Double> particles = capturePyroParticles(simulator);
        perform(simulator, CharacterActionKey.SKILL);
        assertEquals(1, records.size(), "Xinyan Skill hit count");
        AttackAction skill = records.get(0).action;
        assertClose(3.392000, skill.getDamagePercent(),
                "Xinyan C3 Skill multiplier");
        assertEquals(Element.PYRO, skill.getElement(),
                "Xinyan Skill element");
        assertEquals(ICDType.None, skill.getICDType(),
                "Xinyan Skill has no ICD");
        assertClose(13.0 / 60.0, c4.getLastSkillTime(),
                "Xinyan Skill cooldown start");
        StatsContainer allyStats = effectiveStats(ally, simulator);
        assertClose(0.15, allyStats.get(StatType.PHYS_RES_SHRED),
                "Xinyan C4 applies party-visible Physical shred");
        simulator.advanceTime(2.0);
        assertEquals(1, particles.size(),
                "Xinyan Skill particle packet count");
        assertClose(4.0, particles.get(0),
                "Xinyan Skill particle amount");

        double expiry = 15.0 / 60.0 + 12.0;
        assertClose(0.15,
                effectiveStats(ally, simulator, expiry - EPSILON)
                        .get(StatType.PHYS_RES_SHRED),
                "Xinyan C4 remains before expiry");
        assertClose(0.0,
                effectiveStats(ally, simulator, expiry)
                        .get(StatType.PHYS_RES_SHRED),
                "Xinyan C4 expires at exact boundary");

        Xinyan c3 = new Xinyan(null, null, 3);
        CombatSimulator c3Simulator = simulatorWith(c3);
        perform(c3Simulator, CharacterActionKey.SKILL);
        assertClose(0.0,
                effectiveStats(c3, c3Simulator)
                        .get(StatType.PHYS_RES_SHRED),
                "Xinyan C4 does not leak into C3");
    }

    private static void testBurstSequenceAndC2() {
        Xinyan c5 = new Xinyan(null, null, 5);
        CombatSimulator simulator = simulatorWith(c5);
        List<ActionRecord> records = captureActions(simulator);
        perform(simulator, CharacterActionKey.BURST);
        simulator.advanceTime(2.0);
        assertEquals(8, records.size(),
                "Xinyan Burst has one initial and seven Pyro hits");
        ActionRecord initial = records.get(0);
        assertEquals(Element.PHYSICAL, initial.action.getElement(),
                "Xinyan Burst initial is Physical");
        assertClose(6.816000, initial.action.getDamagePercent(),
                "Xinyan C5 initial multiplier");
        assertClose(1.05,
                initial.action.getStatSnapshot().get(StatType.CRIT_RATE),
                "Xinyan C2 adds 100% CRIT Rate to Physical initial only");
        for (int index = 1; index < records.size(); index++) {
            AttackAction dot = records.get(index).action;
            assertEquals(Element.PYRO, dot.getElement(),
                    "Xinyan Burst DoT element " + index);
            assertClose(0.800000, dot.getDamagePercent(),
                    "Xinyan C5 DoT multiplier " + index);
            assertClose(0.05,
                    dot.getStatSnapshot().get(StatType.CRIT_RATE),
                    "Xinyan C2 CRIT does not leak into Pyro DoT " + index);
        }
        assertClose(0.0, c5.getCurrentEnergy(),
                "Xinyan Burst spends Energy at frame five");

        Xinyan c1 = new Xinyan(null, null, 1);
        CombatSimulator c1Simulator = simulatorWith(c1);
        List<ActionRecord> c1Records = captureActions(c1Simulator);
        perform(c1Simulator, CharacterActionKey.BURST);
        assertClose(5.793600,
                c1Records.get(0).action.getDamagePercent(),
                "Xinyan pre-C5 Burst multiplier");
        assertClose(0.05,
                c1Records.get(0).action.getStatSnapshot()
                        .get(StatType.CRIT_RATE),
                "Xinyan C2 CRIT does not leak into C1");
    }

    private static void testSnapshotRestoreAndIsolation() {
        Xinyan xinyan = new Xinyan(null, null, 5);
        CombatSimulator simulator = simulatorWith(xinyan);
        List<ActionRecord> records = captureActions(simulator);
        perform(simulator, CharacterActionKey.BURST);
        SimulatorSnapshot snapshot = simulator.saveSnapshot();
        int resolvedAtSnapshot = records.size();
        simulator.advanceTime(2.0);
        int futureHitCount = records.size() - resolvedAtSnapshot;
        assertTrue(futureHitCount > 0,
                "Xinyan Burst retains future DoT hits at snapshot");
        simulator.restoreSnapshot(snapshot);
        records.clear();
        simulator.advanceTime(2.0);
        assertEquals(futureHitCount, records.size(),
                "Xinyan restored DoT sequence resolves once");

        Xinyan foreign = new Xinyan(null, null, 0);
        assertTrue(!xinyan.acceptsCharacterState(
                foreign.captureCharacterState()),
                "Xinyan rejects foreign state");
        assertThrows(IllegalArgumentException.class,
                () -> xinyan.restoreCharacterState(null, simulator),
                "Xinyan rejects null state");
        assertThrows(IllegalStateException.class,
                () -> simulatorWith(xinyan),
                "Xinyan rejects cross-simulator reuse");
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
                CharacterId.XINYAN, CharacterActionRequest.of(key));
    }

    private static List<ActionRecord> captureActions(
            CombatSimulator simulator) {
        List<ActionRecord> records = new ArrayList<>();
        simulator.addDamageListener((actor, action, damage, time) -> {
            if (actor.getCharacterId() == CharacterId.XINYAN) {
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

    private static StatsContainer effectiveStats(
            Character character,
            CombatSimulator simulator) {
        return effectiveStats(
                character, simulator, simulator.getCurrentTime());
    }

    private static StatsContainer effectiveStats(
            Character character,
            CombatSimulator simulator,
            double time) {
        StatsContainer stats = character.getEffectiveStats(time);
        for (Buff buff : simulator.getApplicableBuffs(character)) {
            if (!buff.isExpired(time)) {
                buff.apply(stats, time);
            }
        }
        return stats;
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
            assertTrue(lines.get(index).startsWith("Xinyan,"),
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

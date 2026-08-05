package sample;

import model.entity.Character;
import model.entity.Enemy;
import model.entity.SnapshotAwareWeaponEffect;
import model.entity.Weapon;
import model.stats.StatsContainer;
import model.type.CharacterId;
import model.type.Element;
import model.type.StatType;
import model.type.WeaponType;
import model.weapon.PrototypeAmber;
import simulation.CombatSimulator;
import simulation.SimulatorSnapshot;
import simulation.action.CharacterActionKey;
import simulation.action.CharacterActionRequest;

/** Focused regression for Prototype Amber's owner-Energy branch. */
public final class PrototypeAmberRegressionTest {
    private static final double EPSILON = 1e-8;

    private PrototypeAmberRegressionTest() {
    }

    /** Runs metadata, pulse, rollback, exclusion, and binding checks. */
    public static void main(String[] args) {
        testMetadataAndRefinement();
        testPulseTimingAndEnergyCap();
        testOffFieldPersistence();
        testSnapshotRestoreAndIndependentInstances();
        testTriggerBindingAndStateGuards();
        System.out.println("PrototypeAmberRegressionTest passed");
    }

    private static void testMetadataAndRefinement() {
        PrototypeAmber defaultWeapon = new PrototypeAmber();
        assertEquals(5, defaultWeapon.getRefinement(),
                "Prototype Amber default refinement");
        assertEquals("Prototype Amber", defaultWeapon.getName(),
                "Prototype Amber display name");
        assertEquals(WeaponType.CATALYST, defaultWeapon.getWeaponType(),
                "Prototype Amber weapon type");
        assertClose(510.0,
                defaultWeapon.getStats().get(StatType.BASE_ATK),
                "Prototype Amber base ATK");
        assertClose(0.413,
                defaultWeapon.getStats().get(StatType.HP_PERCENT),
                "Prototype Amber HP percent");
        for (int refinement = 1; refinement <= 5; refinement++) {
            PrototypeAmber weapon = new PrototypeAmber(refinement);
            assertClose(3.5 + 0.5 * refinement,
                    weapon.getEnergyPerPulse(),
                    "Prototype Amber Energy R" + refinement);
        }
        assertThrows(IllegalArgumentException.class,
                () -> new PrototypeAmber(0),
                "Prototype Amber rejects R0");
        assertThrows(IllegalArgumentException.class,
                () -> new PrototypeAmber(6),
                "Prototype Amber rejects R6");
    }

    private static void testPulseTimingAndEnergyCap() {
        PrototypeAmber weapon = new PrototypeAmber(1);
        TestCharacter owner = character(
                CharacterId.BARBARA, Element.HYDRO, weapon);
        CombatSimulator simulator = simulatorWith(owner);
        owner.restoreCurrentEnergy(0.0);
        trigger(weapon, owner, simulator, CharacterActionKey.BURST);
        assertEquals(3, weapon.getPendingPulseCount(),
                "Prototype Amber queues exactly three pulses");
        simulator.advanceTime(2.0 - EPSILON);
        assertClose(0.0, owner.getCurrentEnergy(),
                "No Energy arrives before two seconds");
        simulator.advanceTime(EPSILON);
        assertClose(4.0, owner.getCurrentEnergy(),
                "First pulse arrives at exactly two seconds");
        simulator.advanceTime(2.0);
        assertClose(8.0, owner.getCurrentEnergy(),
                "Second pulse arrives at exactly four seconds");
        simulator.advanceTime(2.0);
        assertClose(12.0, owner.getCurrentEnergy(),
                "Third pulse arrives at exactly six seconds");
        assertEquals(0, weapon.getPendingPulseCount(),
                "All three pulses resolve once");

        PrototypeAmber cappedWeapon = new PrototypeAmber(5);
        TestCharacter cappedOwner = character(
                CharacterId.BARBARA, Element.HYDRO, cappedWeapon);
        CombatSimulator cappedSimulator = simulatorWith(cappedOwner);
        cappedOwner.restoreCurrentEnergy(39.0);
        trigger(cappedWeapon, cappedOwner, cappedSimulator,
                CharacterActionKey.BURST);
        cappedSimulator.advanceTime(6.0);
        assertClose(40.0, cappedOwner.getCurrentEnergy(),
                "Prototype Amber respects the owner Energy cap");
    }

    private static void testOffFieldPersistence() {
        PrototypeAmber weapon = new PrototypeAmber(1);
        TestCharacter owner = character(
                CharacterId.BARBARA, Element.HYDRO, weapon);
        TestCharacter ally = character(
                CharacterId.NOELLE, Element.GEO, null);
        CombatSimulator simulator = simulatorWith(owner, ally);
        owner.restoreCurrentEnergy(0.0);
        trigger(weapon, owner, simulator, CharacterActionKey.BURST);
        simulator.switchCharacter(CharacterId.NOELLE);
        simulator.advanceTime(6.0);
        assertClose(12.0, owner.getCurrentEnergy(),
                "Prototype Amber pulses persist after owner switch-out");
    }

    private static void testSnapshotRestoreAndIndependentInstances() {
        PrototypeAmber weapon = new PrototypeAmber(1);
        TestCharacter owner = character(
                CharacterId.BARBARA, Element.HYDRO, weapon);
        CombatSimulator simulator = simulatorWith(owner);
        owner.restoreCurrentEnergy(0.0);
        trigger(weapon, owner, simulator, CharacterActionKey.BURST);
        simulator.advanceTime(2.5);
        assertClose(4.0, owner.getCurrentEnergy(),
                "First pulse resolves before snapshot");
        SimulatorSnapshot snapshot = simulator.saveSnapshot();
        simulator.advanceTime(3.5);
        assertClose(12.0, owner.getCurrentEnergy(),
                "Remaining pulses resolve after snapshot");
        simulator.restoreSnapshot(snapshot);
        assertClose(4.0, owner.getCurrentEnergy(),
                "Restore returns owner Energy");
        assertEquals(2, weapon.getPendingPulseCount(),
                "Restore returns unresolved pulse count");
        simulator.advanceTime(3.5);
        assertClose(12.0, owner.getCurrentEnergy(),
                "Restored pulses resolve once");
        assertEquals(0, weapon.getPendingPulseCount(),
                "Restored pulses leave no duplicate work");

        PrototypeAmber independent = new PrototypeAmber(1);
        assertEquals(0, independent.getPendingPulseCount(),
                "Independent Prototype Amber has isolated state");
    }

    private static void testTriggerBindingAndStateGuards() {
        PrototypeAmber weapon = new PrototypeAmber(1);
        TestCharacter owner = character(
                CharacterId.BARBARA, Element.HYDRO, weapon);
        TestCharacter ally = character(
                CharacterId.NOELLE, Element.GEO, null);
        CombatSimulator simulator = simulatorWith(owner, ally);
        trigger(weapon, owner, simulator, CharacterActionKey.SKILL);
        trigger(weapon, ally, simulator, CharacterActionKey.BURST);
        simulator.switchCharacter(CharacterId.NOELLE);
        trigger(weapon, owner, simulator, CharacterActionKey.BURST);
        assertEquals(0, weapon.getPendingPulseCount(),
                "Non-Burst, foreign, and off-field triggers are rejected");

        PrototypeAmber unequippedWeapon = new PrototypeAmber(1);
        TestCharacter unequipped = character(
                CharacterId.BARBARA, Element.HYDRO, null);
        CombatSimulator unequippedSimulator = simulatorWith(unequipped);
        assertThrows(IllegalArgumentException.class,
                () -> unequippedWeapon.initializeForSimulator(
                        unequipped, unequippedSimulator),
                "Prototype Amber rejects unequipped binding");
        assertThrows(IllegalStateException.class,
                () -> weapon.initializeForSimulator(
                        owner, new CombatSimulator()),
                "Prototype Amber rejects cross-simulator reuse");

        PrototypeAmber foreign = new PrototypeAmber(1);
        SnapshotAwareWeaponEffect.State foreignState =
                foreign.captureWeaponState();
        assertThrows(IllegalArgumentException.class,
                () -> weapon.restoreWeaponState(foreignState),
                "Prototype Amber rejects foreign state");
        assertThrows(IllegalArgumentException.class,
                () -> weapon.restoreWeaponState(null),
                "Prototype Amber rejects null state");
    }

    private static void trigger(
            PrototypeAmber weapon,
            Character owner,
            CombatSimulator simulator,
            CharacterActionKey key) {
        weapon.onAction(owner, CharacterActionRequest.of(key), simulator);
    }

    private static TestCharacter character(
            CharacterId id,
            Element element,
            Weapon weapon) {
        return new TestCharacter(id, element, weapon);
    }

    private static CombatSimulator simulatorWith(Character... characters) {
        CombatSimulator simulator = new CombatSimulator();
        simulator.setLoggingEnabled(false);
        simulator.setEnemy(new Enemy(90));
        for (Character character : characters) {
            simulator.addCharacter(character);
        }
        return simulator;
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

    private static final class TestCharacter extends Character {
        private TestCharacter(
                CharacterId id,
                Element element,
                Weapon weapon) {
            name = id.getDisplayName();
            characterId = id;
            this.element = element;
            this.weapon = weapon;
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
    }
}

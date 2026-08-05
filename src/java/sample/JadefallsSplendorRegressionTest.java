package sample;

import mechanics.buff.SimpleBuff;
import model.entity.Character;
import model.entity.Enemy;
import model.entity.SnapshotAwareWeaponEffect;
import model.entity.Weapon;
import model.stats.StatsContainer;
import model.type.CharacterId;
import model.type.Element;
import model.type.StatType;
import model.type.WeaponType;
import model.weapon.JadefallsSplendor;
import simulation.CombatSimulator;
import simulation.SimulatorSnapshot;
import simulation.action.CharacterActionKey;
import simulation.action.CharacterActionRequest;

/** Focused regression for Jadefall's Splendor's represented Burst branch. */
public final class JadefallsSplendorRegressionTest {
    private static final double EPSILON = 1e-8;
    private static final double ENERGY_DELAY = 142.0 / 60.0;

    private JadefallsSplendorRegressionTest() {
    }

    /** Runs metadata, refinement, timing, rollback, isolation, and guard checks. */
    public static void main(String[] args) {
        testMetadataAndRefinement();
        testMaxHpSnapshotAndElementIsolation();
        testDurationEnergyTimingAndReplacement();
        testOffFieldPersistenceAndTriggerGuards();
        testSnapshotRollbackAndIndependentInstances();
        testBindingAndStateGuards();
        System.out.println("JadefallsSplendorRegressionTest passed");
    }

    private static void testMetadataAndRefinement() {
        JadefallsSplendor defaultWeapon = new JadefallsSplendor();
        assertEquals("Jadefall's Splendor", defaultWeapon.getName(),
                "Jadefall display name");
        assertEquals(WeaponType.CATALYST, defaultWeapon.getWeaponType(),
                "Jadefall weapon type");
        assertEquals(5, defaultWeapon.getRefinement(),
                "Jadefall default refinement");
        assertClose(608.0,
                defaultWeapon.getStats().get(StatType.BASE_ATK),
                "Jadefall base ATK");
        assertClose(0.496,
                defaultWeapon.getStats().get(StatType.HP_PERCENT),
                "Jadefall HP percent");

        for (int refinement = 1; refinement <= 5; refinement++) {
            JadefallsSplendor weapon =
                    new JadefallsSplendor(refinement);
            assertClose(4.0 + 0.5 * refinement,
                    weapon.getEnergyRestoration(),
                    "Jadefall Energy R" + refinement);
            assertClose(0.001 + 0.002 * refinement,
                    weapon.getDamageBonusPerThousandHp(),
                    "Jadefall damage ratio R" + refinement);
            assertClose(0.04 + 0.08 * refinement,
                    weapon.getMaximumDamageBonus(),
                    "Jadefall damage cap R" + refinement);
        }
        assertThrows(IllegalArgumentException.class,
                () -> new JadefallsSplendor(0),
                "Jadefall rejects R0");
        assertThrows(IllegalArgumentException.class,
                () -> new JadefallsSplendor(6),
                "Jadefall rejects R6");
    }

    private static void testMaxHpSnapshotAndElementIsolation() {
        JadefallsSplendor weapon = new JadefallsSplendor(1);
        TestCharacter owner = character(
                CharacterId.TIGHNARI, Element.DENDRO, weapon, 10000.0);
        CombatSimulator simulator = simulatorWith(owner);
        simulator.applyTeamBuff(new SimpleBuff(
                "Jadefall regression HP",
                10.0,
                0.0,
                stats -> stats.add(StatType.HP_PERCENT, 0.20)));
        trigger(weapon, owner, simulator, CharacterActionKey.BURST);

        double expectedBonus = 16.96 * 0.003;
        assertClose(expectedBonus, weapon.getElementalDamageBonus(),
                "Jadefall snapshots continuous final Max HP with team buffs");
        StatsContainer stats = seededStats();
        weapon.applyPassive(stats, 0.0);
        assertClose(0.20 + expectedBonus,
                stats.get(StatType.DENDRO_DMG_BONUS),
                "Jadefall adds corresponding-element damage");
        assertClose(0.30, stats.get(StatType.HYDRO_DMG_BONUS),
                "Jadefall preserves unrelated element damage");

        JadefallsSplendor cappedWeapon = new JadefallsSplendor(1);
        TestCharacter cappedOwner = character(
                CharacterId.TIGHNARI,
                Element.DENDRO,
                cappedWeapon,
                100000.0);
        CombatSimulator cappedSimulator = simulatorWith(cappedOwner);
        trigger(cappedWeapon, cappedOwner, cappedSimulator,
                CharacterActionKey.BURST);
        assertClose(0.12, cappedWeapon.getElementalDamageBonus(),
                "Jadefall caps the Max-HP-derived bonus");
    }

    private static void testDurationEnergyTimingAndReplacement() {
        JadefallsSplendor weapon = new JadefallsSplendor(1);
        TestCharacter owner = character(
                CharacterId.TIGHNARI, Element.DENDRO, weapon, 10000.0);
        CombatSimulator simulator = simulatorWith(owner);
        owner.restoreCurrentEnergy(0.0);
        trigger(weapon, owner, simulator, CharacterActionKey.BURST);
        assertTrue(weapon.isRegaliaActive(0.0),
                "Jadefall starts Regalia immediately");
        assertTrue(weapon.isRegaliaActive(3.0 - EPSILON),
                "Jadefall remains active before three seconds");
        assertTrue(!weapon.isRegaliaActive(3.0),
                "Jadefall expires at exactly three seconds");
        assertEquals(1, weapon.getPendingEnergyCount(),
                "Jadefall queues one Energy restoration");

        simulator.advanceTime(ENERGY_DELAY - EPSILON);
        assertClose(0.0, owner.getCurrentEnergy(),
                "Jadefall restores no Energy before frame 142");
        simulator.advanceTime(EPSILON);
        assertClose(4.5, owner.getCurrentEnergy(),
                "Jadefall restores Energy at exactly frame 142");
        assertEquals(0, weapon.getPendingEnergyCount(),
                "Jadefall resolves its Energy task once");

        JadefallsSplendor replacingWeapon = new JadefallsSplendor(1);
        TestCharacter replacingOwner = character(
                CharacterId.TIGHNARI,
                Element.DENDRO,
                replacingWeapon,
                10000.0);
        CombatSimulator replacingSimulator = simulatorWith(replacingOwner);
        replacingOwner.restoreCurrentEnergy(0.0);
        trigger(replacingWeapon, replacingOwner, replacingSimulator,
                CharacterActionKey.BURST);
        replacingSimulator.advanceTime(1.0);
        trigger(replacingWeapon, replacingOwner, replacingSimulator,
                CharacterActionKey.BURST);
        replacingSimulator.advanceTime(ENERGY_DELAY - 1.0);
        assertClose(0.0, replacingOwner.getCurrentEnergy(),
                "Jadefall invalidates the replaced Energy task");
        replacingSimulator.advanceTime(1.0);
        assertClose(4.5, replacingOwner.getCurrentEnergy(),
                "Jadefall resolves only the replacement task");
    }

    private static void testOffFieldPersistenceAndTriggerGuards() {
        JadefallsSplendor weapon = new JadefallsSplendor(1);
        TestCharacter owner = character(
                CharacterId.TIGHNARI, Element.DENDRO, weapon, 10000.0);
        TestCharacter ally = character(
                CharacterId.AMBER, Element.PYRO, null, 10000.0);
        CombatSimulator simulator = simulatorWith(owner, ally);
        owner.restoreCurrentEnergy(0.0);
        trigger(weapon, owner, simulator, CharacterActionKey.BURST);
        simulator.switchCharacter(CharacterId.AMBER);
        simulator.advanceTime(ENERGY_DELAY);
        assertClose(4.5, owner.getCurrentEnergy(),
                "Jadefall restores Energy after owner switch-out");
        assertTrue(weapon.isRegaliaActive(simulator.getCurrentTime()),
                "Jadefall Regalia persists off field");

        JadefallsSplendor guardedWeapon = new JadefallsSplendor(1);
        TestCharacter guardedOwner = character(
                CharacterId.TIGHNARI,
                Element.DENDRO,
                guardedWeapon,
                10000.0);
        TestCharacter guardedAlly = character(
                CharacterId.AMBER, Element.PYRO, null, 10000.0);
        CombatSimulator guardedSimulator = simulatorWith(
                guardedOwner, guardedAlly);
        trigger(guardedWeapon, guardedOwner, guardedSimulator,
                CharacterActionKey.SKILL);
        trigger(guardedWeapon, guardedAlly, guardedSimulator,
                CharacterActionKey.BURST);
        guardedSimulator.switchCharacter(CharacterId.AMBER);
        trigger(guardedWeapon, guardedOwner, guardedSimulator,
                CharacterActionKey.BURST);
        guardedWeapon.onAction(
                guardedOwner, null, guardedSimulator);
        guardedWeapon.onAction(
                guardedOwner,
                CharacterActionRequest.of(CharacterActionKey.BURST),
                new CombatSimulator());
        assertEquals(0, guardedWeapon.getPendingEnergyCount(),
                "Jadefall rejects wrong action, owner, field, simulator, and null");
    }

    private static void testSnapshotRollbackAndIndependentInstances() {
        JadefallsSplendor weapon = new JadefallsSplendor(1);
        TestCharacter owner = character(
                CharacterId.TIGHNARI, Element.DENDRO, weapon, 10000.0);
        CombatSimulator simulator = simulatorWith(owner);
        owner.restoreCurrentEnergy(0.0);
        trigger(weapon, owner, simulator, CharacterActionKey.BURST);
        simulator.advanceTime(1.0);
        SimulatorSnapshot snapshot = simulator.saveSnapshot();
        simulator.advanceTime(ENERGY_DELAY - 1.0);
        assertClose(4.5, owner.getCurrentEnergy(),
                "Jadefall resolves Energy before rollback");
        assertEquals(0, weapon.getPendingEnergyCount(),
                "Jadefall clears resolved task before rollback");

        simulator.restoreSnapshot(snapshot);
        assertClose(0.0, owner.getCurrentEnergy(),
                "Jadefall rollback restores owner Energy");
        assertEquals(1, weapon.getPendingEnergyCount(),
                "Jadefall rollback restores pending task");
        assertClose(3.0, weapon.getRegaliaUntil(),
                "Jadefall rollback restores Regalia expiry");
        simulator.advanceTime(ENERGY_DELAY - 1.0);
        assertClose(4.5, owner.getCurrentEnergy(),
                "Jadefall restored task resolves exactly once");

        JadefallsSplendor independent = new JadefallsSplendor(1);
        assertTrue(!independent.isRegaliaActive(0.0),
                "Jadefall instances keep independent windows");
        assertEquals(0, independent.getPendingEnergyCount(),
                "Jadefall instances keep independent Energy tasks");
    }

    private static void testBindingAndStateGuards() {
        JadefallsSplendor weapon = new JadefallsSplendor(1);
        TestCharacter owner = character(
                CharacterId.TIGHNARI, Element.DENDRO, weapon, 10000.0);
        CombatSimulator simulator = simulatorWith(owner);
        weapon.initializeForSimulator(owner, simulator);

        JadefallsSplendor unequipped = new JadefallsSplendor(1);
        TestCharacter bare = character(
                CharacterId.TIGHNARI, Element.DENDRO, null, 10000.0);
        CombatSimulator bareSimulator = simulatorWith(bare);
        assertThrows(IllegalArgumentException.class,
                () -> unequipped.initializeForSimulator(
                        bare, bareSimulator),
                "Jadefall rejects an unequipped owner");
        assertThrows(IllegalStateException.class,
                () -> weapon.initializeForSimulator(
                        owner, new CombatSimulator()),
                "Jadefall rejects cross-simulator rebinding");
        assertThrows(IllegalArgumentException.class,
                () -> new JadefallsSplendor(1)
                        .initializeForSimulator(null, simulator),
                "Jadefall rejects a null owner");

        JadefallsSplendor foreign = new JadefallsSplendor(1);
        SnapshotAwareWeaponEffect.State foreignState =
                foreign.captureWeaponState();
        assertThrows(IllegalArgumentException.class,
                () -> weapon.restoreWeaponState(foreignState),
                "Jadefall rejects another instance state");
        assertThrows(IllegalArgumentException.class,
                () -> weapon.restoreWeaponState(new ForeignState()),
                "Jadefall rejects a foreign state type");
        assertThrows(IllegalArgumentException.class,
                () -> weapon.restoreWeaponState(null),
                "Jadefall rejects null state");
    }

    private static void trigger(
            JadefallsSplendor weapon,
            Character owner,
            CombatSimulator simulator,
            CharacterActionKey key) {
        weapon.onAction(
                owner, CharacterActionRequest.of(key), simulator);
    }

    private static StatsContainer seededStats() {
        StatsContainer stats = new StatsContainer();
        stats.set(StatType.DENDRO_DMG_BONUS, 0.20);
        stats.set(StatType.HYDRO_DMG_BONUS, 0.30);
        return stats;
    }

    private static TestCharacter character(
            CharacterId id,
            Element element,
            Weapon weapon,
            double baseHp) {
        return new TestCharacter(id, element, weapon, baseHp);
    }

    private static CombatSimulator simulatorWith(
            Character... characters) {
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

    private static final class ForeignState
            implements SnapshotAwareWeaponEffect.State {
    }

    private static final class TestCharacter extends Character {
        private TestCharacter(
                CharacterId id,
                Element element,
                Weapon weapon,
                double baseHp) {
            name = id.getDisplayName();
            characterId = id;
            this.element = element;
            this.weapon = weapon;
            baseStats.set(StatType.BASE_HP, baseHp);
            baseStats.set(StatType.BASE_ATK, 100.0);
            baseStats.set(StatType.BASE_DEF, 500.0);
        }

        @Override
        public void applyPassive(StatsContainer stats) {
        }

        @Override
        public double getEnergyCost() {
            return 80.0;
        }
    }
}

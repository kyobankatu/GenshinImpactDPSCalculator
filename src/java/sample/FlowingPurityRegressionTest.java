package sample;

import model.entity.ArtifactSet;
import model.entity.Character;
import model.entity.Enemy;
import model.entity.SnapshotAwareWeaponEffect;
import model.entity.Weapon;
import model.stats.StatsContainer;
import model.type.CharacterId;
import model.type.Element;
import model.type.StatType;
import model.type.WeaponType;
import model.weapon.FlowingPurity;
import simulation.CombatSimulator;
import simulation.SimulatorSnapshot;
import simulation.action.CharacterActionKey;
import simulation.action.CharacterActionRequest;

/** Focused regression checks for Flowing Purity's represented branch. */
public final class FlowingPurityRegressionTest {
    private static final double EPSILON = 1e-8;
    private static final StatType[] ELEMENTAL_BONUSES = {
        StatType.PYRO_DMG_BONUS,
        StatType.HYDRO_DMG_BONUS,
        StatType.ANEMO_DMG_BONUS,
        StatType.ELECTRO_DMG_BONUS,
        StatType.DENDRO_DMG_BONUS,
        StatType.CRYO_DMG_BONUS,
        StatType.GEO_DMG_BONUS
    };

    private FlowingPurityRegressionTest() {
    }

    /** Runs metadata, window, isolation, restore, and guard checks. */
    public static void main(String[] args) {
        testMetadataAndRefinement();
        testWindowCooldownAndIsolation();
        testSnapshotRestoreAndIndependentInstances();
        testBindingAndTriggerGuards();
        System.out.println("FlowingPurityRegressionTest passed");
    }

    private static void testMetadataAndRefinement() {
        FlowingPurity defaultWeapon = new FlowingPurity();
        assertEquals("Flowing Purity", defaultWeapon.getName(),
                "Flowing Purity display name");
        assertEquals(WeaponType.CATALYST, defaultWeapon.getWeaponType(),
                "Flowing Purity type");
        assertEquals(5, defaultWeapon.getRefinement(),
                "Flowing Purity default refinement");
        assertClose(565.0,
                defaultWeapon.getStats().get(StatType.BASE_ATK),
                "Flowing Purity base ATK");
        assertClose(0.276,
                defaultWeapon.getStats().get(StatType.ATK_PERCENT),
                "Flowing Purity ATK substat");
        for (int refinement = 1; refinement <= 5; refinement++) {
            FlowingPurity weapon = new FlowingPurity(refinement);
            assertClose(0.06 + 0.02 * refinement,
                    weapon.getElementalDamageBonus(),
                    "Flowing Purity elemental bonus R" + refinement);
        }
        assertThrows(IllegalArgumentException.class,
                () -> new FlowingPurity(0),
                "Flowing Purity rejects R0");
        assertThrows(IllegalArgumentException.class,
                () -> new FlowingPurity(6),
                "Flowing Purity rejects R6");
    }

    private static void testWindowCooldownAndIsolation() {
        FlowingPurity weapon = new FlowingPurity(1);
        TestCharacter owner = character(
                CharacterId.SUCROSE, Element.ANEMO, weapon);
        CombatSimulator simulator = simulatorWith(owner);
        trigger(weapon, owner, simulator, CharacterActionKey.SKILL);
        assertTrue(weapon.isWindowActive(0.0),
                "Flowing Purity activates at Skill input");
        StatsContainer active = owner.getEffectiveStats(0.0);
        for (StatType stat : ELEMENTAL_BONUSES) {
            assertClose(0.08, active.get(stat),
                    "Flowing Purity applies each elemental bonus");
        }
        assertClose(0.0, active.get(StatType.PHYSICAL_DMG_BONUS),
                "Flowing Purity excludes Physical damage");
        assertClose(0.0, active.get(StatType.DMG_BONUS_ALL),
                "Flowing Purity excludes generic damage");
        assertTrue(weapon.isWindowActive(15.0 - EPSILON),
                "Flowing Purity remains active before fifteen seconds");
        assertTrue(!weapon.isWindowActive(15.0),
                "Flowing Purity expires at exactly fifteen seconds");

        simulator.advanceTime(10.0 - EPSILON);
        trigger(weapon, owner, simulator, CharacterActionKey.SKILL);
        assertClose(15.0, weapon.getActiveUntil(),
                "Flowing Purity rejects refresh before ten seconds");
        simulator.advanceTime(EPSILON);
        trigger(weapon, owner, simulator, CharacterActionKey.SKILL);
        assertClose(25.0, weapon.getActiveUntil(),
                "Flowing Purity refreshes at exactly ten seconds");
        trigger(weapon, owner, simulator, CharacterActionKey.BURST);
        assertClose(25.0, weapon.getActiveUntil(),
                "Flowing Purity excludes Burst input");
    }

    private static void testSnapshotRestoreAndIndependentInstances() {
        FlowingPurity weapon = new FlowingPurity(1);
        TestCharacter owner = character(
                CharacterId.SUCROSE, Element.ANEMO, weapon);
        CombatSimulator simulator = simulatorWith(owner);
        trigger(weapon, owner, simulator, CharacterActionKey.SKILL);
        SimulatorSnapshot snapshot = simulator.saveSnapshot();
        simulator.advanceTime(10.0);
        trigger(weapon, owner, simulator, CharacterActionKey.SKILL);
        assertClose(25.0, weapon.getActiveUntil(),
                "Flowing Purity live refresh mutates state");
        simulator.restoreSnapshot(snapshot);
        assertClose(15.0, weapon.getActiveUntil(),
                "Flowing Purity restore returns expiration");
        assertTrue(!new FlowingPurity(1).isWindowActive(0.0),
                "Flowing Purity instances are independent");
    }

    private static void testBindingAndTriggerGuards() {
        FlowingPurity weapon = new FlowingPurity(1);
        TestCharacter owner = character(
                CharacterId.SUCROSE, Element.ANEMO, weapon);
        TestCharacter ally = character(
                CharacterId.BENNETT, Element.PYRO, null);
        CombatSimulator simulator = simulatorWith(owner, ally);
        trigger(weapon, ally, simulator, CharacterActionKey.SKILL);
        assertTrue(!weapon.isWindowActive(0.0),
                "Flowing Purity rejects foreign user");
        simulator.switchCharacter(CharacterId.BENNETT);
        trigger(weapon, owner, simulator, CharacterActionKey.SKILL);
        assertTrue(!weapon.isWindowActive(0.0),
                "Flowing Purity rejects off-field owner input");

        FlowingPurity unequipped = new FlowingPurity(1);
        TestCharacter bare = character(
                CharacterId.SUCROSE, Element.ANEMO, null);
        CombatSimulator bareSimulator = simulatorWith(bare);
        assertThrows(IllegalArgumentException.class,
                () -> unequipped.initializeForSimulator(
                        bare, bareSimulator),
                "Flowing Purity rejects unequipped binding");
        assertThrows(IllegalStateException.class,
                () -> weapon.initializeForSimulator(
                        owner, new CombatSimulator()),
                "Flowing Purity rejects cross-simulator reuse");
        SnapshotAwareWeaponEffect.State foreign =
                new FlowingPurity(1).captureWeaponState();
        assertThrows(IllegalArgumentException.class,
                () -> weapon.restoreWeaponState(foreign),
                "Flowing Purity rejects foreign state");
        assertThrows(IllegalArgumentException.class,
                () -> weapon.restoreWeaponState(null),
                "Flowing Purity rejects null state");
    }

    private static void trigger(
            FlowingPurity weapon,
            Character user,
            CombatSimulator simulator,
            CharacterActionKey key) {
        weapon.onAction(user, CharacterActionRequest.of(key), simulator);
    }

    private static TestCharacter character(
            CharacterId id,
            Element element,
            Weapon weapon) {
        return new TestCharacter(id, element, weapon);
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

        @Override
        public Weapon getWeapon() {
            return weapon;
        }

        @Override
        public ArtifactSet[] getArtifacts() {
            return new ArtifactSet[0];
        }
    }
}

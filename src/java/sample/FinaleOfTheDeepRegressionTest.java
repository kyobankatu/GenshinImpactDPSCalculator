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
import model.weapon.FinaleOfTheDeep;
import simulation.CombatSimulator;
import simulation.SimulatorSnapshot;
import simulation.action.CharacterActionKey;
import simulation.action.CharacterActionRequest;

/** Focused regression checks for Finale of the Deep's represented branch. */
public final class FinaleOfTheDeepRegressionTest {
    private static final double EPSILON = 1e-8;

    private FinaleOfTheDeepRegressionTest() {
    }

    /** Runs metadata, window, isolation, restore, and guard checks. */
    public static void main(String[] args) {
        testMetadataAndRefinement();
        testWindowCooldownAndIsolation();
        testSnapshotRestoreAndIndependentInstances();
        testBindingAndTriggerGuards();
        System.out.println("FinaleOfTheDeepRegressionTest passed");
    }

    private static void testMetadataAndRefinement() {
        FinaleOfTheDeep defaultWeapon = new FinaleOfTheDeep();
        assertEquals("Finale of the Deep", defaultWeapon.getName(),
                "Finale of the Deep display name");
        assertEquals(WeaponType.SWORD, defaultWeapon.getWeaponType(),
                "Finale of the Deep type");
        assertEquals(5, defaultWeapon.getRefinement(),
                "Finale of the Deep default refinement");
        assertClose(565.0,
                defaultWeapon.getStats().get(StatType.BASE_ATK),
                "Finale of the Deep base ATK");
        assertClose(0.276,
                defaultWeapon.getStats().get(StatType.ATK_PERCENT),
                "Finale of the Deep ATK substat");
        for (int refinement = 1; refinement <= 5; refinement++) {
            FinaleOfTheDeep weapon = new FinaleOfTheDeep(refinement);
            assertClose(0.09 + 0.03 * refinement,
                    weapon.getAttackBonus(),
                    "Finale of the Deep ATK bonus R" + refinement);
        }
        assertThrows(IllegalArgumentException.class,
                () -> new FinaleOfTheDeep(0),
                "Finale of the Deep rejects R0");
        assertThrows(IllegalArgumentException.class,
                () -> new FinaleOfTheDeep(6),
                "Finale of the Deep rejects R6");
    }

    private static void testWindowCooldownAndIsolation() {
        FinaleOfTheDeep weapon = new FinaleOfTheDeep(1);
        TestCharacter owner = character(
                CharacterId.BENNETT, Element.PYRO, weapon);
        CombatSimulator simulator = simulatorWith(owner);
        trigger(weapon, owner, simulator, CharacterActionKey.SKILL);
        assertTrue(weapon.isWindowActive(0.0),
                "Finale of the Deep activates at Skill input");
        StatsContainer active = owner.getEffectiveStats(0.0);
        assertClose(0.276 + 0.12, active.get(StatType.ATK_PERCENT),
                "Finale of the Deep applies percentage ATK");
        assertClose(0.0, active.get(StatType.ATK_FLAT),
                "Finale of the Deep excludes debt-derived flat ATK");
        assertClose(0.0, active.get(StatType.DMG_BONUS_ALL),
                "Finale of the Deep excludes generic damage");
        assertTrue(weapon.isWindowActive(15.0 - EPSILON),
                "Finale of the Deep remains active before fifteen seconds");
        assertTrue(!weapon.isWindowActive(15.0),
                "Finale of the Deep expires at exactly fifteen seconds");

        simulator.advanceTime(10.0 - EPSILON);
        trigger(weapon, owner, simulator, CharacterActionKey.SKILL);
        assertClose(15.0, weapon.getActiveUntil(),
                "Finale of the Deep rejects refresh before ten seconds");
        simulator.advanceTime(EPSILON);
        trigger(weapon, owner, simulator, CharacterActionKey.SKILL);
        assertClose(25.0, weapon.getActiveUntil(),
                "Finale of the Deep refreshes at exactly ten seconds");
        trigger(weapon, owner, simulator, CharacterActionKey.BURST);
        assertClose(25.0, weapon.getActiveUntil(),
                "Finale of the Deep excludes Burst input");
    }

    private static void testSnapshotRestoreAndIndependentInstances() {
        FinaleOfTheDeep weapon = new FinaleOfTheDeep(1);
        TestCharacter owner = character(
                CharacterId.BENNETT, Element.PYRO, weapon);
        CombatSimulator simulator = simulatorWith(owner);
        trigger(weapon, owner, simulator, CharacterActionKey.SKILL);
        SimulatorSnapshot snapshot = simulator.saveSnapshot();
        simulator.advanceTime(10.0);
        trigger(weapon, owner, simulator, CharacterActionKey.SKILL);
        assertClose(25.0, weapon.getActiveUntil(),
                "Finale of the Deep live refresh mutates state");
        simulator.restoreSnapshot(snapshot);
        assertClose(15.0, weapon.getActiveUntil(),
                "Finale of the Deep restore returns expiration");
        assertTrue(!new FinaleOfTheDeep(1).isWindowActive(0.0),
                "Finale of the Deep instances are independent");
    }

    private static void testBindingAndTriggerGuards() {
        FinaleOfTheDeep weapon = new FinaleOfTheDeep(1);
        TestCharacter owner = character(
                CharacterId.BENNETT, Element.PYRO, weapon);
        TestCharacter ally = character(
                CharacterId.XINGQIU, Element.HYDRO, null);
        CombatSimulator simulator = simulatorWith(owner, ally);
        trigger(weapon, ally, simulator, CharacterActionKey.SKILL);
        assertTrue(!weapon.isWindowActive(0.0),
                "Finale of the Deep rejects foreign user");
        simulator.switchCharacter(CharacterId.XINGQIU);
        trigger(weapon, owner, simulator, CharacterActionKey.SKILL);
        assertTrue(!weapon.isWindowActive(0.0),
                "Finale of the Deep rejects off-field owner input");

        FinaleOfTheDeep unequipped = new FinaleOfTheDeep(1);
        TestCharacter bare = character(
                CharacterId.BENNETT, Element.PYRO, null);
        CombatSimulator bareSimulator = simulatorWith(bare);
        assertThrows(IllegalArgumentException.class,
                () -> unequipped.initializeForSimulator(
                        bare, bareSimulator),
                "Finale of the Deep rejects unequipped binding");
        assertThrows(IllegalStateException.class,
                () -> weapon.initializeForSimulator(
                        owner, new CombatSimulator()),
                "Finale of the Deep rejects cross-simulator reuse");
        SnapshotAwareWeaponEffect.State foreign =
                new FinaleOfTheDeep(1).captureWeaponState();
        assertThrows(IllegalArgumentException.class,
                () -> weapon.restoreWeaponState(foreign),
                "Finale of the Deep rejects foreign state");
        assertThrows(IllegalArgumentException.class,
                () -> weapon.restoreWeaponState(null),
                "Finale of the Deep rejects null state");
    }

    private static void trigger(
            FinaleOfTheDeep weapon,
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

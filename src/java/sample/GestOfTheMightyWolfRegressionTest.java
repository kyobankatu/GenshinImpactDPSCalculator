package sample;

import model.entity.ArtifactSet;
import model.entity.Character;
import model.entity.Enemy;
import model.entity.SnapshotAwareWeaponEffect;
import model.entity.Weapon;
import model.stats.StatsContainer;
import model.type.ActionType;
import model.type.CharacterId;
import model.type.Element;
import model.type.StatType;
import model.type.WeaponType;
import model.weapon.GestOfTheMightyWolf;
import simulation.CombatSimulator;
import simulation.SimulatorSnapshot;
import simulation.action.AttackAction;
import simulation.action.CharacterActionKey;
import simulation.action.CharacterActionRequest;

/** Focused regression checks for Gest of the Mighty Wolf. */
public final class GestOfTheMightyWolfRegressionTest {
    private static final double EPSILON = 1e-8;

    private GestOfTheMightyWolfRegressionTest() {
    }

    /** Runs metadata, trigger, boundary, restore, and guard checks. */
    public static void main(String[] args) {
        testMetadataAndPermanentSpeed();
        testTriggerRoutesCapAndRefresh();
        testExactExpiryAndReset();
        testExclusionsAndBinding();
        testSnapshotRestoreAndStateGuards();
        System.out.println("GestOfTheMightyWolfRegressionTest passed");
    }

    private static void testMetadataAndPermanentSpeed() {
        GestOfTheMightyWolf defaultWeapon =
                new GestOfTheMightyWolf();
        assertEquals("Gest of the Mighty Wolf", defaultWeapon.getName(),
                "Gest display name");
        assertEquals(WeaponType.CLAYMORE,
                defaultWeapon.getWeaponType(), "Gest weapon type");
        assertEquals(5, defaultWeapon.getRefinement(),
                "Gest default refinement");
        assertClose(608.0,
                defaultWeapon.getStats().get(StatType.BASE_ATK),
                "Gest base ATK");
        assertClose(0.331,
                defaultWeapon.getStats().get(StatType.CRIT_RATE),
                "Gest CRIT Rate");
        for (int refinement = 1; refinement <= 5; refinement++) {
            GestOfTheMightyWolf weapon =
                    new GestOfTheMightyWolf(refinement);
            assertClose(0.055 + 0.020 * refinement,
                    weapon.getDamagePerStack(),
                    "Gest damage per stack R" + refinement);
            StatsContainer stats = new StatsContainer();
            weapon.applyPassive(stats, -100.0);
            assertClose(0.10, stats.get(StatType.ATK_SPD),
                    "Gest permanent ATK SPD R" + refinement);
            assertClose(0.0, stats.get(StatType.DMG_BONUS_ALL),
                    "Gest begins without Hymn stacks");
        }
        assertThrows(IllegalArgumentException.class,
                () -> new GestOfTheMightyWolf(0),
                "Gest rejects R0");
        assertThrows(IllegalArgumentException.class,
                () -> new GestOfTheMightyWolf(6),
                "Gest rejects R6");
    }

    private static void testTriggerRoutesCapAndRefresh() {
        GestOfTheMightyWolf weapon = new GestOfTheMightyWolf(1);
        TestCharacter owner = character(
                CharacterId.NOELLE, Element.GEO, weapon);
        CombatSimulator simulator = simulatorWith(owner);

        trigger(weapon, owner, simulator, CharacterActionKey.CHARGE);
        assertEquals(2, weapon.getStacks(0.0),
                "Gest Charged input grants two stacks");
        assertClose(0.15,
                owner.getEffectiveStats(0.0).get(StatType.DMG_BONUS_ALL),
                "Gest applies two R1 damage stacks");
        deal(simulator, owner, ActionType.NORMAL, 1.0);
        assertEquals(3, weapon.getStacks(0.0),
                "Gest Normal hit grants one stack");
        trigger(weapon, owner, simulator, CharacterActionKey.SKILL);
        assertEquals(4, weapon.getStacks(0.0),
                "Gest Skill input grants two stacks up to the cap");
        assertClose(0.30,
                owner.getEffectiveStats(0.0).get(StatType.DMG_BONUS_ALL),
                "Gest applies four R1 damage stacks");

        simulator.advanceTime(3.5);
        deal(simulator, owner, ActionType.NORMAL, 1.0);
        assertEquals(4, weapon.getStacks(3.5),
                "Gest capped trigger preserves four stacks");
        assertClose(7.5, weapon.getExpiresAt(),
                "Gest capped trigger refreshes the shared window");
        trigger(weapon, owner, simulator, CharacterActionKey.BURST);
        assertClose(7.5, weapon.getExpiresAt(),
                "Gest Burst input does not refresh stacks");
    }

    private static void testExactExpiryAndReset() {
        GestOfTheMightyWolf weapon = new GestOfTheMightyWolf(1);
        TestCharacter owner = character(
                CharacterId.NOELLE, Element.GEO, weapon);
        CombatSimulator simulator = simulatorWith(owner);
        trigger(weapon, owner, simulator, CharacterActionKey.SKILL);
        assertEquals(2, weapon.getStacks(4.0 - EPSILON),
                "Gest stacks remain active before four seconds");
        assertEquals(0, weapon.getStacks(4.0),
                "Gest stacks expire at exactly four seconds");
        simulator.advanceTime(4.0);
        deal(simulator, owner, ActionType.NORMAL, 1.0);
        assertEquals(1, weapon.getStacks(4.0),
                "Gest first post-expiry hit starts from one stack");
        assertClose(8.0, weapon.getExpiresAt(),
                "Gest post-expiry hit opens a fresh window");
    }

    private static void testExclusionsAndBinding() {
        GestOfTheMightyWolf weapon = new GestOfTheMightyWolf(1);
        TestCharacter owner = character(
                CharacterId.NOELLE, Element.GEO, weapon);
        TestCharacter ally = character(
                CharacterId.BENNETT, Element.PYRO, null);
        CombatSimulator simulator = simulatorWith(owner, ally);
        deal(simulator, owner, ActionType.SKILL, 1.0);
        deal(simulator, ally, ActionType.NORMAL, 1.0);
        assertEquals(0, weapon.getStacks(0.0),
                "Gest rejects non-Normal and foreign damage");
        deal(simulator, owner, ActionType.NORMAL, 0.0);
        assertEquals(1, weapon.getStacks(0.0),
                "Gest uses Normal hit identity rather than damage value");
        simulator.switchCharacter(CharacterId.BENNETT);
        deal(simulator, owner, ActionType.NORMAL, 1.0);
        assertEquals(2, weapon.getStacks(simulator.getCurrentTime()),
                "Gest accepts an in-flight owner Normal after switch");
        trigger(weapon, owner, simulator, CharacterActionKey.SKILL);
        assertEquals(2, weapon.getStacks(simulator.getCurrentTime()),
                "Gest rejects off-field owner action input");

        GestOfTheMightyWolf unequipped = new GestOfTheMightyWolf(1);
        TestCharacter bare = character(
                CharacterId.NOELLE, Element.GEO, null);
        CombatSimulator bareSimulator = simulatorWith(bare);
        assertThrows(IllegalArgumentException.class,
                () -> unequipped.initializeForSimulator(
                        bare, bareSimulator),
                "Gest rejects unequipped binding");
        assertThrows(IllegalArgumentException.class,
                () -> unequipped.initializeForSimulator(null, bareSimulator),
                "Gest rejects null owner");
    }

    private static void testSnapshotRestoreAndStateGuards() {
        GestOfTheMightyWolf weapon = new GestOfTheMightyWolf(1);
        TestCharacter owner = character(
                CharacterId.NOELLE, Element.GEO, weapon);
        CombatSimulator simulator = simulatorWith(owner);
        trigger(weapon, owner, simulator, CharacterActionKey.CHARGE);
        SimulatorSnapshot snapshot = simulator.saveSnapshot();
        deal(simulator, owner, ActionType.NORMAL, 1.0);
        assertEquals(3, weapon.getStacks(0.0),
                "Gest live state changes after snapshot");
        simulator.restoreSnapshot(snapshot);
        assertEquals(2, weapon.getStacks(0.0),
                "Gest restore returns the stack count");
        assertClose(4.0, weapon.getExpiresAt(),
                "Gest restore returns the exact expiry");

        GestOfTheMightyWolf foreign = new GestOfTheMightyWolf(1);
        SnapshotAwareWeaponEffect.State foreignState =
                foreign.captureWeaponState();
        assertThrows(IllegalArgumentException.class,
                () -> weapon.restoreWeaponState(foreignState),
                "Gest rejects foreign snapshot state");
        assertThrows(IllegalArgumentException.class,
                () -> weapon.restoreWeaponState(null),
                "Gest rejects null snapshot state");
        assertThrows(IllegalStateException.class,
                () -> weapon.initializeForSimulator(
                        owner, new CombatSimulator()),
                "Gest rejects cross-simulator reuse");
    }

    private static void trigger(
            GestOfTheMightyWolf weapon,
            Character owner,
            CombatSimulator simulator,
            CharacterActionKey key) {
        weapon.onAction(owner, CharacterActionRequest.of(key), simulator);
    }

    private static void deal(
            CombatSimulator simulator,
            Character actor,
            ActionType actionType,
            double multiplier) {
        AttackAction action = new AttackAction(
                "Gest test hit",
                multiplier,
                Element.PHYSICAL,
                StatType.BASE_ATK,
                actionType == ActionType.SKILL
                        ? StatType.SKILL_DMG_BONUS
                        : StatType.NORMAL_ATTACK_DMG_BONUS,
                0.0,
                actionType);
        simulator.performActionWithoutTimeAdvance(
                actor.getCharacterId(), action);
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
        Enemy enemy = new Enemy(90);
        enemy.setRes(StatType.PHYSICAL_DMG_BONUS, 0.0);
        for (Character character : characters) {
            simulator.addCharacter(character);
        }
        simulator.setEnemy(enemy);
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

package sample;

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
import model.weapon.AThousandBlazingSuns;
import simulation.CombatSimulator;
import simulation.SimulatorSnapshot;
import simulation.action.AttackAction;
import simulation.action.CharacterActionKey;
import simulation.action.CharacterActionRequest;

/** Focused regression for A Thousand Blazing Suns. */
public final class AThousandBlazingSunsRegressionTest {
    private static final double EPSILON = 1e-8;

    private AThousandBlazingSunsRegressionTest() {
    }

    /** Runs metadata, trigger, extension, restore, and guard checks. */
    public static void main(String[] args) {
        testMetadataAndRefinement();
        testTriggerWindowsAndCooldown();
        testElementalNormalChargedExtensions();
        testExtensionGatePersistsAcrossReactivation();
        testExtensionExclusions();
        testSnapshotRestoreAndIndependentInstances();
        testBindingAndStateGuards();
        System.out.println("AThousandBlazingSunsRegressionTest passed");
    }

    private static void testMetadataAndRefinement() {
        AThousandBlazingSuns defaultWeapon =
                new AThousandBlazingSuns();
        assertEquals(5, defaultWeapon.getRefinement(),
                "Blazing Suns default refinement");
        assertEquals("A Thousand Blazing Suns", defaultWeapon.getName(),
                "Blazing Suns display name");
        assertEquals(WeaponType.CLAYMORE, defaultWeapon.getWeaponType(),
                "Blazing Suns weapon type");
        assertClose(741.0,
                defaultWeapon.getStats().get(StatType.BASE_ATK),
                "Blazing Suns base ATK");
        assertClose(0.110,
                defaultWeapon.getStats().get(StatType.CRIT_RATE),
                "Blazing Suns CRIT Rate");
        for (int refinement = 1; refinement <= 5; refinement++) {
            AThousandBlazingSuns weapon =
                    new AThousandBlazingSuns(refinement);
            assertClose(0.21 + 0.07 * refinement,
                    weapon.getAttackBonus(),
                    "Blazing Suns ATK bonus R" + refinement);
            assertClose(0.15 + 0.05 * refinement,
                    weapon.getCritDamageBonus(),
                    "Blazing Suns CRIT DMG bonus R" + refinement);
        }
        assertThrows(IllegalArgumentException.class,
                () -> new AThousandBlazingSuns(0),
                "Blazing Suns rejects R0");
        assertThrows(IllegalArgumentException.class,
                () -> new AThousandBlazingSuns(6),
                "Blazing Suns rejects R6");
    }

    private static void testTriggerWindowsAndCooldown() {
        AThousandBlazingSuns weapon = new AThousandBlazingSuns(1);
        TestCharacter owner = character(
                CharacterId.NOELLE, Element.GEO, weapon);
        CombatSimulator simulator = simulatorWith(owner);
        trigger(weapon, owner, simulator, CharacterActionKey.SKILL);
        assertTrue(weapon.isWindowActive(0.0),
                "Skill opens Scorching Brilliance at cast time");
        assertClose(0.28,
                owner.getEffectiveStats(0.0).get(StatType.ATK_PERCENT),
                "R1 window grants ATK");
        assertClose(0.70,
                owner.getEffectiveStats(0.0).get(StatType.CRIT_DMG),
                "R1 window adds CRIT DMG to the character baseline");
        assertTrue(weapon.isWindowActive(6.0 - EPSILON),
                "Window remains active immediately before six seconds");
        assertTrue(!weapon.isWindowActive(6.0),
                "Window expires at exactly six seconds");

        simulator.advanceTime(5.0);
        trigger(weapon, owner, simulator, CharacterActionKey.BURST);
        assertClose(6.0, weapon.getActiveUntil(),
                "Activation cooldown rejects a Burst before ten seconds");
        simulator.advanceTime(5.0);
        trigger(weapon, owner, simulator, CharacterActionKey.BURST);
        assertClose(16.0, weapon.getActiveUntil(),
                "Activation reopens at exactly ten seconds");
        trigger(weapon, owner, simulator, CharacterActionKey.NORMAL);
        assertClose(16.0, weapon.getActiveUntil(),
                "Normal use does not activate the weapon");
    }

    private static void testElementalNormalChargedExtensions() {
        AThousandBlazingSuns weapon = new AThousandBlazingSuns(1);
        TestCharacter owner = character(
                CharacterId.NOELLE, Element.GEO, weapon);
        CombatSimulator simulator = simulatorWith(owner);
        trigger(weapon, owner, simulator, CharacterActionKey.SKILL);

        deal(simulator, owner, ActionType.NORMAL, Element.GEO);
        assertEquals(1, weapon.getExtensionCount(),
                "First elemental Normal extends the window");
        assertClose(8.0, weapon.getActiveUntil(),
                "First extension adds two seconds");
        simulator.advanceTime(1.0 - EPSILON);
        deal(simulator, owner, ActionType.CHARGE, Element.GEO);
        assertEquals(1, weapon.getExtensionCount(),
                "Extension gate rejects a hit before one second");
        simulator.advanceTime(EPSILON);
        deal(simulator, owner, ActionType.CHARGE, Element.GEO);
        assertEquals(2, weapon.getExtensionCount(),
                "Extension gate reopens at exactly one second");
        assertClose(10.0, weapon.getActiveUntil(),
                "Second extension adds two seconds");
        simulator.advanceTime(1.0);
        deal(simulator, owner, ActionType.NORMAL, Element.GEO);
        assertEquals(3, weapon.getExtensionCount(),
                "Third elemental hit reaches the cap");
        assertClose(12.0, weapon.getActiveUntil(),
                "Three extensions add at most six seconds");
        simulator.advanceTime(1.0);
        deal(simulator, owner, ActionType.NORMAL, Element.GEO);
        assertEquals(3, weapon.getExtensionCount(),
                "Further elemental hits cannot exceed the cap");
        assertClose(12.0, weapon.getActiveUntil(),
                "Extension cap preserves the twelve-second expiry");
    }

    private static void testExtensionExclusions() {
        AThousandBlazingSuns weapon = new AThousandBlazingSuns(1);
        TestCharacter owner = character(
                CharacterId.NOELLE, Element.GEO, weapon);
        TestCharacter ally = character(
                CharacterId.BENNETT, Element.PYRO, null);
        CombatSimulator simulator = simulatorWith(owner, ally);
        trigger(weapon, owner, simulator, CharacterActionKey.SKILL);

        deal(simulator, owner, ActionType.NORMAL, Element.PHYSICAL);
        deal(simulator, owner, ActionType.SKILL, Element.GEO);
        deal(simulator, ally, ActionType.NORMAL, Element.PYRO);
        assertEquals(0, weapon.getExtensionCount(),
                "Physical, Skill, and foreign hits do not extend");
        simulator.switchCharacter(CharacterId.BENNETT);
        deal(simulator, owner, ActionType.NORMAL, Element.GEO);
        assertEquals(0, weapon.getExtensionCount(),
                "Off-field owner damage does not extend");
        trigger(weapon, owner, simulator, CharacterActionKey.BURST);
        assertClose(6.0, weapon.getActiveUntil(),
                "Off-field owner action cannot refresh the window");
    }

    private static void testExtensionGatePersistsAcrossReactivation() {
        AThousandBlazingSuns weapon = new AThousandBlazingSuns(1);
        TestCharacter owner = character(
                CharacterId.NOELLE, Element.GEO, weapon);
        CombatSimulator simulator = simulatorWith(owner);
        trigger(weapon, owner, simulator, CharacterActionKey.SKILL);
        deal(simulator, owner, ActionType.NORMAL, Element.GEO);
        simulator.advanceTime(1.0);
        deal(simulator, owner, ActionType.NORMAL, Element.GEO);
        simulator.advanceTime(8.5);
        deal(simulator, owner, ActionType.NORMAL, Element.GEO);
        assertEquals(3, weapon.getExtensionCount(),
                "Late pre-refresh hit starts the extension gate");

        simulator.advanceTime(0.5);
        trigger(weapon, owner, simulator, CharacterActionKey.BURST);
        assertEquals(0, weapon.getExtensionCount(),
                "Reactivation resets only the extension count");
        deal(simulator, owner, ActionType.NORMAL, Element.GEO);
        assertEquals(0, weapon.getExtensionCount(),
                "Reactivation preserves the prior one-second gate");
        simulator.advanceTime(0.5);
        deal(simulator, owner, ActionType.NORMAL, Element.GEO);
        assertEquals(1, weapon.getExtensionCount(),
                "Preserved extension gate reopens at its exact boundary");
    }

    private static void testSnapshotRestoreAndIndependentInstances() {
        AThousandBlazingSuns weapon = new AThousandBlazingSuns(1);
        TestCharacter owner = character(
                CharacterId.NOELLE, Element.GEO, weapon);
        CombatSimulator simulator = simulatorWith(owner);
        trigger(weapon, owner, simulator, CharacterActionKey.SKILL);
        deal(simulator, owner, ActionType.NORMAL, Element.GEO);
        SimulatorSnapshot snapshot = simulator.saveSnapshot();
        simulator.advanceTime(1.0);
        deal(simulator, owner, ActionType.NORMAL, Element.GEO);
        assertEquals(2, weapon.getExtensionCount(),
                "Post-snapshot extension mutates live state");
        simulator.restoreSnapshot(snapshot);
        assertEquals(1, weapon.getExtensionCount(),
                "Restore returns the extension counter");
        assertClose(8.0, weapon.getActiveUntil(),
                "Restore returns the extended expiry");

        AThousandBlazingSuns independent =
                new AThousandBlazingSuns(1);
        assertTrue(!independent.isWindowActive(0.0),
                "Independent weapon instances do not share state");
    }

    private static void testBindingAndStateGuards() {
        AThousandBlazingSuns weapon = new AThousandBlazingSuns(1);
        TestCharacter unequipped = character(
                CharacterId.NOELLE, Element.GEO, null);
        CombatSimulator unequippedSimulator = simulatorWith(unequipped);
        assertThrows(IllegalArgumentException.class,
                () -> weapon.initializeForSimulator(
                        unequipped, unequippedSimulator),
                "Blazing Suns rejects unequipped binding");

        AThousandBlazingSuns bound = new AThousandBlazingSuns(1);
        TestCharacter owner = character(
                CharacterId.NOELLE, Element.GEO, bound);
        CombatSimulator simulator = simulatorWith(owner);
        assertThrows(IllegalStateException.class,
                () -> bound.initializeForSimulator(
                        owner, new CombatSimulator()),
                "Blazing Suns rejects cross-simulator reuse");
        AThousandBlazingSuns foreign = new AThousandBlazingSuns(1);
        SnapshotAwareWeaponEffect.State foreignState =
                foreign.captureWeaponState();
        assertThrows(IllegalArgumentException.class,
                () -> bound.restoreWeaponState(foreignState),
                "Blazing Suns rejects foreign state");
        assertThrows(IllegalArgumentException.class,
                () -> bound.restoreWeaponState(null),
                "Blazing Suns rejects null state");
    }

    private static void trigger(
            AThousandBlazingSuns weapon,
            Character owner,
            CombatSimulator simulator,
            CharacterActionKey key) {
        weapon.onAction(owner, CharacterActionRequest.of(key), simulator);
    }

    private static void deal(
            CombatSimulator simulator,
            Character actor,
            ActionType actionType,
            Element element) {
        AttackAction action = new AttackAction(
                "Blazing Suns Test Hit",
                1.0,
                element,
                StatType.BASE_ATK,
                actionType == ActionType.CHARGE
                        ? StatType.CHARGED_ATTACK_DMG_BONUS
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

    private static CombatSimulator simulatorWith(Character... characters) {
        CombatSimulator simulator = new CombatSimulator();
        simulator.setLoggingEnabled(false);
        Enemy enemy = new Enemy(90);
        enemy.setRes(StatType.PHYSICAL_DMG_BONUS, 0.0);
        enemy.setRes(StatType.GEO_DMG_BONUS, 0.0);
        enemy.setRes(StatType.PYRO_DMG_BONUS, 0.0);
        simulator.setEnemy(enemy);
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
    }
}

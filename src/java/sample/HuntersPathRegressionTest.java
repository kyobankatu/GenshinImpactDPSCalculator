package sample;

import java.util.List;

import mechanics.formula.DamageCalculator;
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
import model.weapon.HuntersPath;
import simulation.CombatSimulator;
import simulation.SimulatorSnapshot;
import simulation.action.AttackAction;

/** Regression checks for Hunter's Path and Tireless Hunt state. */
public final class HuntersPathRegressionTest {
    private static final double EPS = 1e-8;
    private static final double R1_ELEMENTAL_BASE_DAMAGE = 323.568;
    private static final double R1_PASSIVE_DAMAGE = 404.208;

    private HuntersPathRegressionTest() {
    }

    /** Runs metadata, ordering, lifecycle, snapshot, and isolation checks. */
    public static void main(String[] args) {
        testMetadataAndRefinementValues();
        testPostHitOrderingAndLiveElementalMastery();
        testInstanceDurationAndCooldownBoundaries();
        testActionRoutingAndOffFieldDamage();
        testSnapshotRollbackAndStrictRestore();
        testBindingValidation();
        System.out.println("HuntersPathRegressionTest passed");
    }

    private static void testMetadataAndRefinementValues() {
        assertEquals(5, new HuntersPath().getRefinement(),
                "Hunter default refinement");
        for (int refinement = 1; refinement <= 5; refinement++) {
            HuntersPath weapon = new HuntersPath(refinement);
            assertEquals("Hunter's Path", weapon.getName(), "Hunter name");
            assertEquals(WeaponType.BOW, weapon.getWeaponType(), "Hunter type");
            assertClose(542.0, weapon.getStats().get(StatType.BASE_ATK),
                    "Hunter base ATK");
            assertClose(0.441, weapon.getStats().get(StatType.CRIT_RATE),
                    "Hunter CRIT Rate");
            assertClose(0.09 + 0.03 * refinement,
                    weapon.getAllElementalDamageBonus(),
                    "Hunter elemental bonus R" + refinement);
            assertClose(1.20 + 0.40 * refinement,
                    weapon.getElementalMasteryConversionRatio(),
                    "Hunter conversion R" + refinement);
            for (Element element : Element.values()) {
                double expected = element == Element.PHYSICAL
                        ? 0.0 : weapon.getAllElementalDamageBonus();
                assertClose(expected,
                        weapon.getStats().get(element.getBonusStatType()),
                        "Hunter bonus routing " + element);
            }
        }
        assertThrows(IllegalArgumentException.class,
                () -> new HuntersPath(0), "Hunter refinement zero");
        assertThrows(IllegalArgumentException.class,
                () -> new HuntersPath(6), "Hunter refinement six");
    }

    private static void testPostHitOrderingAndLiveElementalMastery() {
        HuntersPath weapon = new HuntersPath(1);
        TestCharacter owner = character(CharacterId.AMBER, weapon);
        CombatSimulator sim = simulatorWith(owner);
        AttackAction charged = hit("Triggering Charge", ActionType.CHARGE,
                Element.PYRO);

        assertClose(R1_ELEMENTAL_BASE_DAMAGE, calculate(owner, charged, sim),
                "Hunter triggering Charge resolves before acquisition");
        assertEquals(12, weapon.getRemainingInstances(0.0),
                "Hunter opens with 12 instances");
        assertClose(R1_PASSIVE_DAMAGE, calculate(owner, charged, sim),
                "Hunter following Charge receives EM conversion");
        assertEquals(11, weapon.getRemainingInstances(0.0),
                "Hunter following Charge consumes one instance");

        owner.getBaseStats().set(StatType.ELEMENTAL_MASTERY, 200.0);
        assertClose(484.848, calculate(owner, charged, sim),
                "Hunter reads final EM at each Charged instance");
    }

    private static void testInstanceDurationAndCooldownBoundaries() {
        HuntersPath weapon = new HuntersPath(1);
        TestCharacter owner = character(CharacterId.AMBER, weapon);
        CombatSimulator sim = simulatorWith(owner);
        AttackAction charged = hit("Instance Charge", ActionType.CHARGE,
                Element.PYRO);
        calculate(owner, charged, sim);

        for (int instance = 12; instance > 0; instance--) {
            assertClose(R1_PASSIVE_DAMAGE, calculate(owner, charged, sim),
                    "Hunter instance " + instance + " receives conversion");
            assertEquals(instance - 1,
                    weapon.getRemainingInstances(sim.getCurrentTime()),
                    "Hunter instance " + instance + " decrements exactly once");
        }
        assertClose(R1_ELEMENTAL_BASE_DAMAGE, calculate(owner, charged, sim),
                "Hunter expires immediately after the 12th instance");

        HuntersPath durationWeapon = new HuntersPath(1);
        TestCharacter durationOwner = character(CharacterId.AMBER,
                durationWeapon);
        CombatSimulator durationSim = simulatorWith(durationOwner);
        calculate(durationOwner, charged, durationSim);
        durationSim.advanceTime(9.999);
        assertEquals(12, durationWeapon.getRemainingInstances(9.999),
                "Hunter survives before ten-second boundary");
        durationSim.advanceTime(0.001);
        assertEquals(0, durationWeapon.getRemainingInstances(10.0),
                "Hunter expires at exact ten-second boundary");
        assertClose(R1_ELEMENTAL_BASE_DAMAGE,
                calculate(durationOwner, charged, durationSim),
                "Hunter cannot reacquire before 12-second cooldown");
        durationSim.advanceTime(2.0);
        assertClose(R1_ELEMENTAL_BASE_DAMAGE,
                calculate(durationOwner, charged, durationSim),
                "Hunter exact cooldown acquisition hit has no conversion");
        assertEquals(12, durationWeapon.getRemainingInstances(12.0),
                "Hunter reacquires at exact cooldown boundary");
    }

    private static void testActionRoutingAndOffFieldDamage() {
        HuntersPath weapon = new HuntersPath(1);
        TestCharacter owner = character(CharacterId.AMBER, weapon);
        TestCharacter ally = character(CharacterId.SUCROSE, null);
        CombatSimulator sim = simulatorWith(owner, ally);
        AttackAction charged = hit("Eligible Charge", ActionType.CHARGE,
                Element.PYRO);

        assertClose(288.9, calculate(owner,
                        hit("Physical Charge", ActionType.CHARGE,
                                Element.PHYSICAL), sim),
                "Hunter all-element bonus excludes Physical");
        AttackAction skill = hit("Skill", ActionType.SKILL, Element.PYRO);
        assertClose(R1_ELEMENTAL_BASE_DAMAGE, calculate(owner, skill, sim),
                "Hunter Skill excludes Charged conversion");
        assertEquals(12, weapon.getRemainingInstances(0.0),
                "Hunter non-Charged damage does not consume");

        sim.setActiveCharacter(CharacterId.SUCROSE);
        assertClose(R1_PASSIVE_DAMAGE, calculate(owner, charged, sim),
                "Hunter delayed off-field Charge remains eligible");
        assertEquals(11, weapon.getRemainingInstances(0.0),
                "Hunter off-field Charge consumes one instance");

        AttackAction dummy = hit("Dummy Charge", ActionType.CHARGE,
                Element.PYRO);
        dummy.setHitEffectTrigger(false);
        assertClose(0.0, calculate(owner, dummy, sim),
                "Hunter dummy cast deals no derived damage");
        assertEquals(11, weapon.getRemainingInstances(0.0),
                "Hunter dummy cast leaves state unchanged");
        weapon.onDamage(ally, charged, 0.0, sim);
        weapon.onDamage(owner, null, 0.0, sim);
        weapon.onDamage(owner, charged, 0.0, new CombatSimulator());
    }

    private static void testSnapshotRollbackAndStrictRestore() {
        HuntersPath weapon = new HuntersPath(1);
        TestCharacter owner = character(CharacterId.AMBER, weapon);
        CombatSimulator sim = simulatorWith(owner);
        AttackAction charged = hit("Snapshot Charge", ActionType.CHARGE,
                Element.PYRO);
        calculate(owner, charged, sim);
        calculate(owner, charged, sim);
        SimulatorSnapshot snapshot = sim.saveSnapshot();
        calculate(owner, charged, sim);
        calculate(owner, charged, sim);
        assertEquals(9, weapon.getRemainingInstances(0.0),
                "Hunter state changes before rollback");
        sim.restoreSnapshot(snapshot);
        assertEquals(11, weapon.getRemainingInstances(0.0),
                "Hunter rollback restores instance count");

        SnapshotAwareWeaponEffect.State state = weapon.captureWeaponState();
        HuntersPath other = new HuntersPath(1);
        assertThrows(IllegalArgumentException.class,
                () -> other.restoreWeaponState(state),
                "Hunter rejects another instance state");
        assertThrows(IllegalArgumentException.class,
                () -> weapon.restoreWeaponState(new ForeignState()),
                "Hunter rejects foreign state type");
    }

    private static void testBindingValidation() {
        HuntersPath weapon = new HuntersPath(1);
        TestCharacter owner = character(CharacterId.AMBER, weapon);
        CombatSimulator sim = simulatorWith(owner);
        weapon.initializeForSimulator(owner, sim);
        assertThrows(IllegalStateException.class,
                () -> weapon.initializeForSimulator(owner, new CombatSimulator()),
                "Hunter rejects cross-simulator binding");
        assertThrows(IllegalArgumentException.class,
                () -> new HuntersPath(1).initializeForSimulator(owner, sim),
                "Hunter rejects owner without this instance equipped");
    }

    private static double calculate(
            TestCharacter owner,
            AttackAction action,
            CombatSimulator sim) {
        return DamageCalculator.calculateDamage(
                owner,
                sim.getEnemy(),
                action,
                List.of(),
                sim.getCurrentTime(),
                1.0,
                sim);
    }

    private static AttackAction hit(
            String name,
            ActionType actionType,
            Element element) {
        AttackAction action = new AttackAction(
                name,
                1.0,
                element,
                StatType.BASE_ATK,
                null,
                0.0,
                actionType);
        action.setHitEffectTrigger(true);
        return action;
    }

    private static TestCharacter character(CharacterId id, Weapon weapon) {
        return new TestCharacter(id, weapon);
    }

    private static CombatSimulator simulatorWith(TestCharacter... characters) {
        CombatSimulator sim = new CombatSimulator();
        sim.setLoggingEnabled(false);
        sim.setEnemy(new Enemy(90));
        for (TestCharacter character : characters) {
            sim.addCharacter(character);
        }
        return sim;
    }

    private static void assertClose(
            double expected,
            double actual,
            String message) {
        if (Math.abs(expected - actual) > EPS) {
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

    private static void assertThrows(
            Class<? extends Throwable> expectedType,
            Runnable action,
            String message) {
        try {
            action.run();
        } catch (Throwable thrown) {
            if (expectedType.isInstance(thrown)) {
                return;
            }
            throw new AssertionError(message + ": expected "
                    + expectedType.getSimpleName() + " but got "
                    + thrown.getClass().getSimpleName(), thrown);
        }
        throw new AssertionError(message + ": expected "
                + expectedType.getSimpleName());
    }

    /** Minimal owner with deterministic final ATK, EM, and crit values. */
    private static final class TestCharacter extends Character {
        private TestCharacter(CharacterId id, Weapon weapon) {
            name = id.getDisplayName();
            characterId = id;
            element = Element.PYRO;
            this.weapon = weapon;
            baseStats.set(StatType.BASE_ATK, 100.0);
            baseStats.set(StatType.ELEMENTAL_MASTERY, 100.0);
            baseStats.set(StatType.CRIT_RATE, weapon == null
                    ? 0.0 : -weapon.getStats().get(StatType.CRIT_RATE));
            baseStats.set(StatType.CRIT_DMG, 0.0);
        }

        @Override
        public void applyPassive(StatsContainer stats) {
        }

        @Override
        public double getEnergyCost() {
            return 0.0;
        }
    }

    private static final class ForeignState
            implements SnapshotAwareWeaponEffect.State {
    }
}

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
import model.weapon.CinnabarSpindle;
import simulation.CombatSimulator;
import simulation.SimulatorSnapshot;
import simulation.action.AttackAction;

/** Regression checks for Cinnabar Spindle's transient DEF conversion. */
public final class CinnabarSpindleRegressionTest {
    private static final double EPS = 1e-8;
    private static final double BASE_DAMAGE = 249.3;
    private static final double R1_PASSIVE_DAMAGE = 553.5;

    private CinnabarSpindleRegressionTest() {
    }

    /** Runs metadata, timing, trigger, snapshot, and binding checks. */
    public static void main(String[] args) {
        testMetadataAndRefinementValues();
        testClearAndReadinessBoundaries();
        testTriggerRoutingAndOffFieldDamage();
        testSnapshotRollbackAndStrictRestore();
        testBindingValidation();
        System.out.println("CinnabarSpindleRegressionTest passed");
    }

    private static void testMetadataAndRefinementValues() {
        assertEquals(5, new CinnabarSpindle().getRefinement(),
                "Cinnabar default refinement");
        for (int refinement = 1; refinement <= 5; refinement++) {
            CinnabarSpindle weapon = new CinnabarSpindle(refinement);
            assertEquals("Cinnabar Spindle", weapon.getName(), "Cinnabar name");
            assertEquals(WeaponType.SWORD, weapon.getWeaponType(),
                    "Cinnabar type");
            assertClose(454.0, weapon.getStats().get(StatType.BASE_ATK),
                    "Cinnabar base ATK");
            assertClose(0.690, weapon.getStats().get(StatType.DEF_PERCENT),
                    "Cinnabar DEF");
            assertClose(0.30 + 0.10 * refinement,
                    weapon.getDefenseConversionRatio(),
                    "Cinnabar conversion R" + refinement);
        }
        assertThrows(IllegalArgumentException.class,
                () -> new CinnabarSpindle(0), "Cinnabar refinement zero");
        assertThrows(IllegalArgumentException.class,
                () -> new CinnabarSpindle(6), "Cinnabar refinement six");
    }

    private static void testClearAndReadinessBoundaries() {
        CinnabarSpindle weapon = new CinnabarSpindle(1);
        TestCharacter owner = character(CharacterId.SUCROSE, weapon);
        CombatSimulator sim = simulatorWith(owner);
        AttackAction skill = hit("Cinnabar Skill", ActionType.SKILL);

        assertClose(R1_PASSIVE_DAMAGE, calculate(owner, skill, sim),
                "Cinnabar first ready hit receives DEF conversion");
        sim.advanceTime(0.099);
        assertClose(R1_PASSIVE_DAMAGE, calculate(owner, skill, sim),
                "Cinnabar retained effect applies before clear boundary");
        sim.advanceTime(0.001);
        assertClose(BASE_DAMAGE, calculate(owner, skill, sim),
                "Cinnabar clears at exact 0.1 boundary");
        sim.advanceTime(1.499);
        assertClose(BASE_DAMAGE, calculate(owner, skill, sim),
                "Cinnabar remains unavailable before post-clear cooldown");
        sim.advanceTime(0.001);
        assertClose(R1_PASSIVE_DAMAGE, calculate(owner, skill, sim),
                "Cinnabar becomes ready at exact post-clear boundary");
    }

    private static void testTriggerRoutingAndOffFieldDamage() {
        CinnabarSpindle weapon = new CinnabarSpindle(1);
        TestCharacter owner = character(CharacterId.SUCROSE, weapon);
        TestCharacter ally = character(CharacterId.AMBER, null);
        CombatSimulator sim = simulatorWith(owner, ally);

        assertClose(BASE_DAMAGE, calculate(owner,
                        hit("Normal", ActionType.NORMAL), sim),
                "Cinnabar Normal does not receive DEF conversion");
        AttackAction dummy = hit("Dummy Skill", ActionType.SKILL);
        dummy.setHitEffectTrigger(false);
        assertClose(0.0, calculate(owner, dummy, sim),
                "Cinnabar dummy cast deals no derived damage");

        AttackAction classified = hit("Skill Follow-Up", ActionType.OTHER);
        classified.setCountsAsSkillDmg(true);
        assertClose(R1_PASSIVE_DAMAGE, calculate(owner, classified, sim),
                "Cinnabar accepts Skill-classified follow-up");
        sim.advanceTime(1.6);
        sim.setActiveCharacter(CharacterId.AMBER);
        assertClose(R1_PASSIVE_DAMAGE, calculate(owner, classified, sim),
                "Cinnabar delayed off-field Skill remains eligible");

        weapon.onDamage(ally, classified, sim.getCurrentTime(), sim);
        weapon.onDamage(owner, null, sim.getCurrentTime(), sim);
        weapon.onDamage(owner, classified, sim.getCurrentTime(),
                new CombatSimulator());
    }

    private static void testSnapshotRollbackAndStrictRestore() {
        CinnabarSpindle weapon = new CinnabarSpindle(1);
        TestCharacter owner = character(CharacterId.SUCROSE, weapon);
        CombatSimulator sim = simulatorWith(owner);
        AttackAction skill = hit("Snapshot Skill", ActionType.SKILL);
        calculate(owner, skill, sim);
        SimulatorSnapshot snapshot = sim.saveSnapshot();

        sim.advanceTime(1.6);
        calculate(owner, skill, sim);
        sim.restoreSnapshot(snapshot);
        sim.advanceTime(0.1);
        assertClose(BASE_DAMAGE, calculate(owner, skill, sim),
                "Cinnabar rollback restores original cooldown state");
        sim.advanceTime(1.5);
        assertClose(R1_PASSIVE_DAMAGE, calculate(owner, skill, sim),
                "Cinnabar rollback restores original readiness boundary");

        SnapshotAwareWeaponEffect.State state = weapon.captureWeaponState();
        CinnabarSpindle other = new CinnabarSpindle(1);
        assertThrows(IllegalArgumentException.class,
                () -> other.restoreWeaponState(state),
                "Cinnabar rejects another instance state");
        assertThrows(IllegalArgumentException.class,
                () -> weapon.restoreWeaponState(new ForeignState()),
                "Cinnabar rejects foreign state type");
    }

    private static void testBindingValidation() {
        CinnabarSpindle weapon = new CinnabarSpindle(1);
        TestCharacter owner = character(CharacterId.SUCROSE, weapon);
        CombatSimulator sim = simulatorWith(owner);
        weapon.initializeForSimulator(owner, sim);
        assertThrows(IllegalStateException.class,
                () -> weapon.initializeForSimulator(owner, new CombatSimulator()),
                "Cinnabar rejects cross-simulator binding");
        assertThrows(IllegalArgumentException.class,
                () -> new CinnabarSpindle(1).initializeForSimulator(owner, sim),
                "Cinnabar rejects owner without this instance equipped");
        assertThrows(IllegalArgumentException.class,
                () -> new CinnabarSpindle(1).initializeForSimulator(null, sim),
                "Cinnabar rejects null owner");
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

    private static AttackAction hit(String name, ActionType actionType) {
        AttackAction action = new AttackAction(
                name,
                1.0,
                Element.PHYSICAL,
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

    /** Minimal owner with deterministic final ATK, DEF, and crit values. */
    private static final class TestCharacter extends Character {
        private TestCharacter(CharacterId id, Weapon weapon) {
            name = id.getDisplayName();
            characterId = id;
            element = Element.GEO;
            this.weapon = weapon;
            baseStats.set(StatType.BASE_ATK, 100.0);
            baseStats.set(StatType.BASE_DEF, 1000.0);
            baseStats.set(StatType.CRIT_RATE, 0.0);
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

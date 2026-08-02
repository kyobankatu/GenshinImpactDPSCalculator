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
import model.weapon.LightOfFoliarIncision;
import simulation.CombatSimulator;
import simulation.SimulatorSnapshot;
import simulation.action.AttackAction;

/** Regression checks for Light of Foliar Incision's instance-limited effect. */
public final class LightOfFoliarIncisionRegressionTest {
    private static final double EPS = 1e-8;
    private static final double BASE_DAMAGE = 288.9;
    private static final double R1_PASSIVE_DAMAGE = 342.9;

    private LightOfFoliarIncisionRegressionTest() {
    }

    /** Runs metadata, ordering, lifecycle, snapshot, and isolation checks. */
    public static void main(String[] args) {
        testMetadataAndRefinementValues();
        testPostHitOrderingAndLiveElementalMastery();
        testInstanceAndDurationBoundaries();
        testOnFieldAndTriggerRequirements();
        testSnapshotRollbackAndStrictRestore();
        testBindingValidation();
        System.out.println("LightOfFoliarIncisionRegressionTest passed");
    }

    private static void testMetadataAndRefinementValues() {
        assertEquals(5, new LightOfFoliarIncision().getRefinement(),
                "Foliar default refinement");
        for (int refinement = 1; refinement <= 5; refinement++) {
            LightOfFoliarIncision weapon =
                    new LightOfFoliarIncision(refinement);
            assertEquals("Light of Foliar Incision", weapon.getName(),
                    "Foliar name");
            assertEquals(WeaponType.SWORD, weapon.getWeaponType(),
                    "Foliar type");
            assertClose(542.0, weapon.getStats().get(StatType.BASE_ATK),
                    "Foliar base ATK");
            assertClose(0.882, weapon.getStats().get(StatType.CRIT_DMG),
                    "Foliar CRIT DMG");
            assertClose(0.03 + 0.01 * refinement,
                    weapon.getStats().get(StatType.CRIT_RATE),
                    "Foliar CRIT Rate R" + refinement);
            assertClose(0.90 + 0.30 * refinement,
                    weapon.getElementalMasteryConversionRatio(),
                    "Foliar conversion R" + refinement);
        }
        assertThrows(IllegalArgumentException.class,
                () -> new LightOfFoliarIncision(0),
                "Foliar refinement zero");
        assertThrows(IllegalArgumentException.class,
                () -> new LightOfFoliarIncision(6),
                "Foliar refinement six");
    }

    private static void testPostHitOrderingAndLiveElementalMastery() {
        LightOfFoliarIncision weapon = new LightOfFoliarIncision(1);
        TestCharacter owner = character(CharacterId.SUCROSE, weapon);
        CombatSimulator sim = simulatorWith(owner);

        assertClose(BASE_DAMAGE, calculate(owner,
                        hit("Elemental Normal", ActionType.NORMAL, Element.PYRO),
                        sim),
                "Foliar triggering hit resolves before acquisition");
        assertEquals(28, weapon.getRemainingInstances(0.0),
                "Foliar opens with 28 instances");
        assertClose(R1_PASSIVE_DAMAGE, calculate(owner,
                        hit("Following Skill", ActionType.SKILL, Element.PHYSICAL),
                        sim),
                "Foliar following Skill receives EM conversion");
        assertEquals(27, weapon.getRemainingInstances(0.0),
                "Foliar eligible Skill consumes one instance");

        owner.getBaseStats().set(StatType.ELEMENTAL_MASTERY, 200.0);
        assertClose(396.9, calculate(owner,
                        hit("Live EM Normal", ActionType.NORMAL, Element.PHYSICAL),
                        sim),
                "Foliar reads final EM at each damage instance");
    }

    private static void testInstanceAndDurationBoundaries() {
        LightOfFoliarIncision weapon = new LightOfFoliarIncision(1);
        TestCharacter owner = character(CharacterId.SUCROSE, weapon);
        CombatSimulator sim = simulatorWith(owner);
        calculate(owner,
                hit("Elemental Normal", ActionType.NORMAL, Element.PYRO), sim);
        AttackAction skill = hit("Instance Skill", ActionType.SKILL,
                Element.PHYSICAL);

        for (int instance = 28; instance > 0; instance--) {
            assertClose(R1_PASSIVE_DAMAGE, calculate(owner, skill, sim),
                    "Foliar instance " + instance + " receives conversion");
            assertEquals(instance - 1,
                    weapon.getRemainingInstances(sim.getCurrentTime()),
                    "Foliar instance " + instance + " decrements exactly once");
        }
        assertClose(BASE_DAMAGE, calculate(owner, skill, sim),
                "Foliar expires immediately after the 28th instance");

        LightOfFoliarIncision durationWeapon =
                new LightOfFoliarIncision(1);
        TestCharacter durationOwner =
                character(CharacterId.SUCROSE, durationWeapon);
        CombatSimulator durationSim = simulatorWith(durationOwner);
        AttackAction elementalNormal = hit(
                "Boundary Normal", ActionType.NORMAL, Element.PYRO);
        calculate(durationOwner, elementalNormal, durationSim);
        durationSim.advanceTime(11.999);
        assertEquals(28, durationWeapon.getRemainingInstances(11.999),
                "Foliar survives before 12-second boundary");
        durationSim.advanceTime(0.001);
        assertEquals(0, durationWeapon.getRemainingInstances(12.0),
                "Foliar expires at exact 12-second boundary");
        assertClose(BASE_DAMAGE,
                calculate(durationOwner, elementalNormal, durationSim),
                "Foliar exact cooldown reacquisition hit has no conversion");
        assertEquals(28, durationWeapon.getRemainingInstances(12.0),
                "Foliar reacquires at exact cooldown boundary");
    }

    private static void testOnFieldAndTriggerRequirements() {
        LightOfFoliarIncision weapon = new LightOfFoliarIncision(1);
        TestCharacter owner = character(CharacterId.SUCROSE, weapon);
        TestCharacter ally = character(CharacterId.AMBER, null);
        CombatSimulator sim = simulatorWith(owner, ally);

        calculate(owner,
                hit("Physical Normal", ActionType.NORMAL, Element.PHYSICAL), sim);
        assertEquals(0, weapon.getRemainingInstances(0.0),
                "Foliar physical Normal cannot acquire");
        AttackAction dummy = hit("Dummy Normal", ActionType.NORMAL, Element.PYRO);
        dummy.setHitEffectTrigger(false);
        assertClose(0.0, calculate(owner, dummy, sim),
                "Foliar dummy cast cannot acquire");
        assertEquals(0, weapon.getRemainingInstances(0.0),
                "Foliar dummy cast leaves state unchanged");

        calculate(owner,
                hit("Elemental Normal", ActionType.NORMAL, Element.PYRO), sim);
        sim.setActiveCharacter(CharacterId.AMBER);
        AttackAction skill = hit("Off-Field Skill", ActionType.SKILL,
                Element.PHYSICAL);
        assertClose(BASE_DAMAGE, calculate(owner, skill, sim),
                "Foliar does not apply while owner is off-field");
        assertEquals(28, weapon.getRemainingInstances(0.0),
                "Foliar off-field damage does not consume instances");

        sim.setActiveCharacter(CharacterId.SUCROSE);
        AttackAction classified = hit(
                "Skill-Classified Follow-Up", ActionType.OTHER,
                Element.PHYSICAL);
        classified.setCountsAsSkillDmg(true);
        assertClose(R1_PASSIVE_DAMAGE, calculate(owner, classified, sim),
                "Foliar accepts Skill-classified damage");
        weapon.onDamage(ally, skill, 0.0, sim);
        weapon.onDamage(owner, null, 0.0, sim);
        weapon.onDamage(owner, skill, 0.0, new CombatSimulator());
    }

    private static void testSnapshotRollbackAndStrictRestore() {
        LightOfFoliarIncision weapon = new LightOfFoliarIncision(1);
        TestCharacter owner = character(CharacterId.SUCROSE, weapon);
        CombatSimulator sim = simulatorWith(owner);
        calculate(owner,
                hit("Elemental Normal", ActionType.NORMAL, Element.PYRO), sim);
        AttackAction skill = hit("Snapshot Skill", ActionType.SKILL,
                Element.PHYSICAL);
        calculate(owner, skill, sim);
        SimulatorSnapshot snapshot = sim.saveSnapshot();
        calculate(owner, skill, sim);
        calculate(owner, skill, sim);
        assertEquals(25, weapon.getRemainingInstances(0.0),
                "Foliar state changes before rollback");
        sim.restoreSnapshot(snapshot);
        assertEquals(27, weapon.getRemainingInstances(0.0),
                "Foliar rollback restores instance count");

        SnapshotAwareWeaponEffect.State state = weapon.captureWeaponState();
        LightOfFoliarIncision other = new LightOfFoliarIncision(1);
        assertThrows(IllegalArgumentException.class,
                () -> other.restoreWeaponState(state),
                "Foliar rejects another instance state");
        assertThrows(IllegalArgumentException.class,
                () -> weapon.restoreWeaponState(new ForeignState()),
                "Foliar rejects foreign state type");
    }

    private static void testBindingValidation() {
        LightOfFoliarIncision weapon = new LightOfFoliarIncision(1);
        TestCharacter owner = character(CharacterId.SUCROSE, weapon);
        CombatSimulator sim = simulatorWith(owner);
        weapon.initializeForSimulator(owner, sim);
        assertThrows(IllegalStateException.class,
                () -> weapon.initializeForSimulator(owner, new CombatSimulator()),
                "Foliar rejects cross-simulator binding");
        assertThrows(IllegalArgumentException.class,
                () -> new LightOfFoliarIncision(1)
                        .initializeForSimulator(owner, sim),
                "Foliar rejects owner without this instance equipped");
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
            element = Element.DENDRO;
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

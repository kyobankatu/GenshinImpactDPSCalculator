package sample;

import java.util.List;

import mechanics.formula.DamageCalculator;
import model.entity.Character;
import model.entity.Enemy;
import model.stats.StatsContainer;
import model.type.ActionType;
import model.type.CharacterId;
import model.type.Element;
import model.type.StatType;
import model.type.WeaponType;
import model.weapon.RedhornStonethresher;
import simulation.CombatSimulator;
import simulation.action.AttackAction;

/** Regression checks for Redhorn Stonethresher's offensive passive. */
public final class RedhornStonethresherRegressionTest {
    private static final double EPS = 1e-9;

    private RedhornStonethresherRegressionTest() {
    }

    /** Runs metadata, refinement, formula-routing, and isolation checks. */
    public static void main(String[] args) {
        testMetadataAndRefinementValues();
        testLateMergedFinalDefense();
        testActionRoutingAndUnrelatedStats();
        testZeroMultiplierHitBoundary();
        testIndependentInstancesAndValidation();
        System.out.println("RedhornStonethresherRegressionTest passed");
    }

    private static void testMetadataAndRefinementValues() {
        assertEquals(5, new RedhornStonethresher().getRefinement(),
                "Redhorn default refinement");
        for (int refinement = 1; refinement <= 5; refinement++) {
            RedhornStonethresher weapon =
                    new RedhornStonethresher(refinement);
            assertEquals("Redhorn Stonethresher", weapon.getName(),
                    "Redhorn name");
            assertEquals(WeaponType.CLAYMORE, weapon.getWeaponType(),
                    "Redhorn weapon type");
            assertEquals(refinement, weapon.getRefinement(),
                    "Redhorn selected refinement");
            assertClose(542.0,
                    weapon.getStats().get(StatType.BASE_ATK),
                    "Redhorn base ATK");
            assertClose(0.882,
                    weapon.getStats().get(StatType.CRIT_DMG),
                    "Redhorn CRIT DMG");
            assertClose(0.21 + 0.07 * refinement,
                    weapon.getStats().get(StatType.DEF_PERCENT),
                    "Redhorn DEF R" + refinement);
            assertClose(0.30 + 0.10 * refinement,
                    weapon.getStats().get(
                            StatType.DEF_TO_NORMAL_CHARGED_FLAT_DMG_RATIO),
                    "Redhorn additive ratio R" + refinement);
        }
    }

    private static void testLateMergedFinalDefense() {
        StatsContainer stats = assembledStats(new RedhornStonethresher(1));
        double finalDefense = 1000.0 * (1.0 + 0.28 + 0.30) + 100.0;
        double expected = (642.0 + finalDefense * 0.40) * 0.45;

        assertClose(finalDefense, stats.getTotalDef(),
                "Redhorn final DEF includes late sources");
        assertClose(expected, calculate(stats, ActionType.NORMAL),
                "Redhorn Normal additive damage");
        assertClose(expected, calculate(stats, ActionType.CHARGE),
                "Redhorn Charged additive damage");
    }

    private static void testActionRoutingAndUnrelatedStats() {
        StatsContainer stats = assembledStats(new RedhornStonethresher(5));
        double expectedWithoutPassive = 642.0 * 0.45;

        assertClose(expectedWithoutPassive, calculate(stats, ActionType.SKILL),
                "Redhorn Skill exclusion");
        assertClose(expectedWithoutPassive, calculate(stats, ActionType.BURST),
                "Redhorn Burst exclusion");
        assertClose(expectedWithoutPassive, calculate(stats, ActionType.PLUNGE),
                "Redhorn Plunge exclusion");
        assertClose(77.0, stats.get(StatType.ELEMENTAL_MASTERY),
                "Redhorn unrelated Elemental Mastery");
        assertClose(0.25, stats.get(StatType.GEO_DMG_BONUS),
                "Redhorn unrelated Geo DMG");
    }

    private static void testZeroMultiplierHitBoundary() {
        StatsContainer stats = assembledStats(new RedhornStonethresher(1));
        double expectedHitDamage = stats.getTotalDef() * 0.40 * 0.45;

        assertClose(0.0,
                calculate(stats, ActionType.NORMAL, 0.0, false),
                "Redhorn excludes zero-multiplier non-hit Normal casts");
        assertClose(0.0,
                calculate(stats, ActionType.CHARGE, 0.0, false),
                "Redhorn excludes zero-multiplier non-hit Charged casts");
        assertClose(expectedHitDamage,
                calculate(stats, ActionType.NORMAL, 0.0, true),
                "Redhorn includes explicit zero-multiplier Normal hits");
    }

    private static void testIndependentInstancesAndValidation() {
        RedhornStonethresher r1 = new RedhornStonethresher(1);
        RedhornStonethresher r5 = new RedhornStonethresher(5);
        assertClose(0.28, r1.getStats().get(StatType.DEF_PERCENT),
                "Redhorn R1 independent DEF");
        assertClose(0.56, r5.getStats().get(StatType.DEF_PERCENT),
                "Redhorn R5 independent DEF");
        assertClose(0.40, r1.getStats().get(
                StatType.DEF_TO_NORMAL_CHARGED_FLAT_DMG_RATIO),
                "Redhorn R1 independent ratio");
        assertClose(0.80, r5.getStats().get(
                StatType.DEF_TO_NORMAL_CHARGED_FLAT_DMG_RATIO),
                "Redhorn R5 independent ratio");
        assertThrows(() -> new RedhornStonethresher(0),
                "Redhorn refinement zero");
        assertThrows(() -> new RedhornStonethresher(6),
                "Redhorn refinement six");
    }

    private static StatsContainer assembledStats(
            RedhornStonethresher weapon) {
        StatsContainer character = new StatsContainer();
        character.set(StatType.BASE_ATK, 100.0);
        character.set(StatType.BASE_DEF, 1000.0);
        character.set(StatType.ELEMENTAL_MASTERY, 77.0);
        StatsContainer late = new StatsContainer();
        late.set(StatType.DEF_PERCENT, 0.30);
        late.set(StatType.DEF_FLAT, 100.0);
        late.set(StatType.GEO_DMG_BONUS, 0.25);
        return character.merge(weapon.getStats()).merge(late);
    }

    private static double calculate(
            StatsContainer stats,
            ActionType actionType) {
        return calculate(stats, actionType, 1.0, true);
    }

    private static double calculate(
            StatsContainer stats,
            ActionType actionType,
            double damagePercent,
            boolean hitEffectTrigger) {
        TestCharacter attacker = new TestCharacter();
        Enemy enemy = new Enemy(90);
        CombatSimulator sim = new CombatSimulator();
        sim.setLoggingEnabled(false);
        sim.setEnemy(enemy);
        AttackAction action = new AttackAction(
                "Redhorn Test Hit",
                damagePercent,
                Element.PHYSICAL,
                StatType.BASE_ATK,
                null,
                0.0,
                actionType);
        action.setHitEffectTrigger(hitEffectTrigger);
        return DamageCalculator.calculateDamage(
                attacker,
                enemy,
                action,
                List.of(),
                stats,
                0.0,
                1.0,
                sim);
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

    private static void assertThrows(Runnable action, String message) {
        try {
            action.run();
        } catch (IllegalArgumentException expected) {
            return;
        }
        throw new AssertionError(message + ": expected IllegalArgumentException");
    }

    /** Minimal attacker because tests pass a complete pre-resolved stat view. */
    private static final class TestCharacter extends Character {
        private TestCharacter() {
            name = "Redhorn Tester";
            characterId = CharacterId.UNKNOWN;
            element = Element.PHYSICAL;
        }

        @Override
        public void applyPassive(StatsContainer stats) {
        }

        @Override
        public double getEnergyCost() {
            return 0.0;
        }
    }
}

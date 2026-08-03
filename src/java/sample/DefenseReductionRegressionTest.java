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
import simulation.CombatSimulator;
import simulation.action.AttackAction;

/** Regression checks for standard enemy DEF reduction and DEF ignore. */
public final class DefenseReductionRegressionTest {
    private static final double EPS = 1e-9;
    private static final double BASE_DAMAGE = 100.0;

    private DefenseReductionRegressionTest() {
    }

    /** Runs normal, cap, invalid-input, interaction, and Lunar isolation checks. */
    public static void main(String[] args) {
        testBaselineAndExplicitZeroCompatibility();
        testEnemyDefenseReduction();
        testEnemyDefenseReductionBounds();
        testDefenseReductionAndIgnoreMultiply();
        testDefenseIgnoreBounds();
        testLunarDamageIgnoresStandardDefenseStats();
        System.out.println("DefenseReductionRegressionTest passed");
    }

    private static void testBaselineAndExplicitZeroCompatibility() {
        double baseline = calculateStandard(new StatsContainer(), 0.0);
        StatsContainer explicitZero = new StatsContainer();
        explicitZero.set(StatType.ENEMY_DEF_REDUCTION, 0.0);
        assertClose(50.0, baseline, "baseline equal-level DEF multiplier");
        assertSameBits(baseline, calculateStandard(explicitZero, 0.0),
                "explicit zero reduction compatibility");
    }

    private static void testEnemyDefenseReduction() {
        StatsContainer stats = new StatsContainer();
        stats.add(StatType.ENEMY_DEF_REDUCTION, 0.10);
        stats.add(StatType.ENEMY_DEF_REDUCTION, 0.133);
        assertClose(expectedStandardDamage(0.233, 0.0),
                calculateStandard(stats, 0.0),
                "23.3% additive enemy DEF reduction");
    }

    private static void testEnemyDefenseReductionBounds() {
        StatsContainer capped = new StatsContainer();
        capped.set(StatType.ENEMY_DEF_REDUCTION, 0.90);
        assertClose(expectedStandardDamage(0.90, 0.0),
                calculateStandard(capped, 0.0),
                "90% enemy DEF reduction boundary");

        StatsContainer negative = new StatsContainer();
        negative.set(StatType.ENEMY_DEF_REDUCTION, -0.25);
        assertClose(expectedStandardDamage(0.0, 0.0),
                calculateStandard(negative, 0.0),
                "negative enemy DEF reduction clamps to zero");

        StatsContainer excessive = new StatsContainer();
        excessive.set(StatType.ENEMY_DEF_REDUCTION, 2.0);
        assertClose(expectedStandardDamage(0.90, 0.0),
                calculateStandard(excessive, 0.0),
                "excessive enemy DEF reduction clamps to 90%");
    }

    private static void testDefenseReductionAndIgnoreMultiply() {
        StatsContainer stats = new StatsContainer();
        stats.set(StatType.ENEMY_DEF_REDUCTION, 0.233);
        assertClose(expectedStandardDamage(0.233, 0.60),
                calculateStandard(stats, 0.60),
                "23.3% reduction and 60% ignore use independent factors");
    }

    private static void testDefenseIgnoreBounds() {
        assertClose(expectedStandardDamage(0.0, 0.0),
                calculateStandard(new StatsContainer(), -0.40),
                "negative DEF ignore clamps to zero");
        assertClose(BASE_DAMAGE,
                calculateStandard(new StatsContainer(), 1.40),
                "excessive DEF ignore clamps to 100%");
    }

    private static void testLunarDamageIgnoresStandardDefenseStats() {
        StatsContainer baseline = new StatsContainer();
        StatsContainer defenseStats = new StatsContainer();
        defenseStats.set(StatType.ENEMY_DEF_REDUCTION, 0.90);
        defenseStats.set(StatType.DEF_IGNORE, 1.0);
        assertSameBits(calculateLunar(baseline), calculateLunar(defenseStats),
                "standard DEF stats must not affect Lunar damage");
    }

    private static double calculateStandard(
            StatsContainer defenseStats,
            double actionDefIgnore) {
        StatsContainer stats = baseStats().merge(defenseStats);
        AttackAction action = testAction(false);
        action.setDefenseIgnore(actionDefIgnore);
        return calculate(stats, action);
    }

    private static double calculateLunar(StatsContainer defenseStats) {
        StatsContainer stats = baseStats().merge(defenseStats);
        AttackAction action = testAction(true);
        return calculate(stats, action);
    }

    private static StatsContainer baseStats() {
        StatsContainer stats = new StatsContainer();
        stats.set(StatType.BASE_ATK, BASE_DAMAGE);
        return stats;
    }

    private static AttackAction testAction(boolean lunar) {
        AttackAction action = new AttackAction(
                "Defense Formula Test",
                1.0,
                Element.PHYSICAL,
                StatType.BASE_ATK,
                null,
                0.0,
                ActionType.NORMAL);
        action.setLunarConsidered(lunar);
        return action;
    }

    private static double calculate(StatsContainer stats, AttackAction action) {
        TestCharacter attacker = new TestCharacter();
        Enemy enemy = new Enemy(90);
        enemy.setRes(StatType.PHYSICAL_DMG_BONUS, 0.0);
        CombatSimulator sim = new CombatSimulator();
        sim.setLoggingEnabled(false);
        sim.setEnemy(enemy);
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

    private static double expectedStandardDamage(
            double defReduction,
            double defIgnore) {
        double attackerFactor = 90.0 + 100.0;
        double enemyFactor = (90.0 + 100.0)
                * (1.0 - defReduction)
                * (1.0 - defIgnore);
        return BASE_DAMAGE * attackerFactor / (attackerFactor + enemyFactor);
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

    private static void assertSameBits(
            double expected,
            double actual,
            String message) {
        if (Double.doubleToLongBits(expected) != Double.doubleToLongBits(actual)) {
            throw new AssertionError(message + ": expected " + expected
                    + " but got " + actual);
        }
    }

    /** Minimal attacker because every formula call receives pre-resolved stats. */
    private static final class TestCharacter extends Character {
        private TestCharacter() {
            name = "Defense Formula Tester";
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

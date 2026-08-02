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

/** Regression checks for reusable final-EM and final-DEF conversions. */
public final class DerivedOffensiveStatRegressionTest {
    private static final double EPS = 1e-9;

    private DerivedOffensiveStatRegressionTest() {
    }

    /** Runs conversion order, action routing, and isolation checks. */
    public static void main(String[] args) {
        testElementalMasteryToFlatAttack();
        testLateMergedElementalMasteryAndAdditiveRatios();
        testFinalDefenseNormalAndChargedDamage();
        testActionExclusionAndSourceImmutability();
        System.out.println("DerivedOffensiveStatRegressionTest passed");
    }

    private static void testElementalMasteryToFlatAttack() {
        StatsContainer stats = new StatsContainer();
        stats.set(StatType.BASE_ATK, 500.0);
        stats.set(StatType.ATK_PERCENT, 0.20);
        stats.set(StatType.ATK_FLAT, 100.0);
        stats.set(StatType.ELEMENTAL_MASTERY, 250.0);
        stats.set(StatType.ELEMENTAL_MASTERY_TO_ATK_FLAT_RATIO, 0.52);

        assertClose(830.0, stats.getTotalAtk(),
                "final Elemental Mastery conversion");
        assertClose(100.0, stats.get(StatType.ATK_FLAT),
                "derived conversion must not mutate flat ATK");
    }

    private static void testLateMergedElementalMasteryAndAdditiveRatios() {
        StatsContainer base = new StatsContainer();
        base.set(StatType.BASE_ATK, 300.0);
        base.set(StatType.ELEMENTAL_MASTERY, 80.0);
        base.set(StatType.ELEMENTAL_MASTERY_TO_ATK_FLAT_RATIO, 0.28);

        StatsContainer lateBuff = new StatsContainer();
        lateBuff.set(StatType.ELEMENTAL_MASTERY, 120.0);
        lateBuff.set(StatType.ELEMENTAL_MASTERY_TO_ATK_FLAT_RATIO, 0.24);
        StatsContainer resolved = base.merge(lateBuff);

        assertClose(404.0, resolved.getTotalAtk(),
                "late Elemental Mastery and additive conversion ratios");
        assertClose(300.0 + 80.0 * 0.28, base.getTotalAtk(),
                "merge must preserve source container");

        StatsContainer zero = new StatsContainer();
        zero.set(StatType.BASE_ATK, 300.0);
        zero.set(StatType.ELEMENTAL_MASTERY, -10.0);
        assertClose(300.0, zero.getTotalAtk(), "zero conversion ratio");
    }

    private static void testFinalDefenseNormalAndChargedDamage() {
        StatsContainer stats = standardDamageStats();
        assertClose(85.5, calculate(stats, ActionType.NORMAL),
                "Normal final-DEF additive damage");
        assertClose(85.5, calculate(stats, ActionType.CHARGE),
                "Charged final-DEF additive damage");

        StatsContainer secondRatio = new StatsContainer();
        secondRatio.set(
                StatType.DEF_TO_NORMAL_CHARGED_FLAT_DMG_RATIO, 0.10);
        assertClose(94.5,
                calculate(stats.merge(secondRatio), ActionType.NORMAL),
                "additive final-DEF ratios");
    }

    private static void testActionExclusionAndSourceImmutability() {
        StatsContainer stats = standardDamageStats();
        assertClose(49.5, calculate(stats, ActionType.SKILL),
                "Skill excludes final-DEF addition");
        assertClose(49.5, calculate(stats, ActionType.BURST),
                "Burst excludes final-DEF addition");
        assertClose(49.5, calculate(stats, ActionType.PLUNGE),
                "Plunge excludes final-DEF addition");
        assertClose(0.40, stats.get(
                StatType.DEF_TO_NORMAL_CHARGED_FLAT_DMG_RATIO),
                "damage calculation preserves source ratio");
        assertClose(10.0, stats.get(StatType.FLAT_DMG_BONUS),
                "damage calculation preserves ordinary flat damage");
    }

    private static StatsContainer standardDamageStats() {
        StatsContainer stats = new StatsContainer();
        stats.set(StatType.BASE_ATK, 100.0);
        stats.set(StatType.BASE_DEF, 100.0);
        stats.set(StatType.DEF_PERCENT, 0.50);
        stats.set(StatType.DEF_FLAT, 50.0);
        stats.set(StatType.FLAT_DMG_BONUS, 10.0);
        stats.set(
                StatType.DEF_TO_NORMAL_CHARGED_FLAT_DMG_RATIO, 0.40);
        return stats;
    }

    private static double calculate(
            StatsContainer stats,
            ActionType actionType) {
        TestCharacter attacker = new TestCharacter();
        Enemy enemy = new Enemy(90);
        CombatSimulator sim = new CombatSimulator();
        sim.setLoggingEnabled(false);
        sim.setEnemy(enemy);
        AttackAction action = new AttackAction(
                "Derived Stat Test",
                1.0,
                Element.PHYSICAL,
                StatType.BASE_ATK,
                null,
                0.0,
                actionType);
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

    /** Minimal attacker because the formula receives a pre-resolved stat view. */
    private static final class TestCharacter extends Character {
        private TestCharacter() {
            name = "Derived Stat Tester";
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

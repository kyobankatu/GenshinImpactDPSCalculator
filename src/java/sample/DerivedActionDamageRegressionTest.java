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

/** Regression checks for reusable final-DEF/EM action damage conversions. */
public final class DerivedActionDamageRegressionTest {
    private static final double EPS = 1e-9;

    private DerivedActionDamageRegressionTest() {
    }

    /** Runs formula ordering, routing, hit-boundary, and identity checks. */
    public static void main(String[] args) {
        testActionRoutingAndFormulaOrder();
        testMaxHpNormalRouting();
        testLateMergedStatsAndAdditiveRatios();
        testTrueHitAndNegativeBoundaries();
        testReservedCharacterIdentities();
        System.out.println("DerivedActionDamageRegressionTest passed");
    }

    private static void testActionRoutingAndFormulaOrder() {
        StatsContainer stats = standardStats();

        assertClose(94.5, calculate(stats, ActionType.NORMAL, 1.0, true, false),
                "final EM Normal additive damage");
        assertClose(128.7, calculate(stats, ActionType.SKILL, 1.0, true, false),
                "final DEF/EM Skill additive and DMG Bonus order");
        assertClose(128.7, calculate(stats, ActionType.OTHER, 1.0, true, true),
                "Skill-classified follow-up routing");
        assertClose(117.0, calculate(stats, ActionType.CHARGE, 1.0, true, false),
                "final EM Charged additive damage");
        assertClose(49.5, calculate(stats, ActionType.BURST, 1.0, true, false),
                "Burst excludes action-derived branches");
        assertClose(49.5, calculate(stats, ActionType.PLUNGE, 1.0, true, false),
                "Plunge excludes action-derived branches");
    }

    private static void testLateMergedStatsAndAdditiveRatios() {
        StatsContainer base = new StatsContainer();
        base.set(StatType.BASE_ATK, 100.0);
        base.set(StatType.BASE_DEF, 100.0);
        base.set(StatType.DEF_PERCENT, 0.50);
        base.set(StatType.DEF_FLAT, 50.0);
        base.set(StatType.ELEMENTAL_MASTERY, 20.0);
        base.set(StatType.DEF_TO_SKILL_FLAT_DMG_RATIO, 0.10);
        base.set(StatType.ELEMENTAL_MASTERY_TO_NORMAL_SKILL_FLAT_DMG_RATIO, 0.50);
        base.set(StatType.ELEMENTAL_MASTERY_TO_CHARGED_FLAT_DMG_RATIO, 1.0);
        base.set(StatType.ELEMENTAL_MASTERY_TO_SKILL_DMG_BONUS_RATIO, 0.001);

        StatsContainer late = new StatsContainer();
        late.set(StatType.ELEMENTAL_MASTERY, 30.0);
        late.set(StatType.DEF_TO_SKILL_FLAT_DMG_RATIO, 0.20);
        late.set(StatType.ELEMENTAL_MASTERY_TO_NORMAL_SKILL_FLAT_DMG_RATIO, 0.50);
        late.set(StatType.ELEMENTAL_MASTERY_TO_CHARGED_FLAT_DMG_RATIO, 1.0);
        late.set(StatType.ELEMENTAL_MASTERY_TO_SKILL_DMG_BONUS_RATIO, 0.002);
        StatsContainer resolved = base.merge(late);

        assertClose(108.675,
                calculate(resolved, ActionType.SKILL, 1.0, true, false),
                "late DEF/EM and additive ratios");
        assertClose(20.0, base.get(StatType.ELEMENTAL_MASTERY),
                "merge preserves source EM");
        assertClose(0.10, base.get(StatType.DEF_TO_SKILL_FLAT_DMG_RATIO),
                "merge preserves source ratio");
    }

    private static void testMaxHpNormalRouting() {
        StatsContainer stats = new StatsContainer();
        stats.set(StatType.BASE_ATK, 100.0);
        stats.set(StatType.BASE_HP, 100.0);
        stats.set(StatType.HP_PERCENT, 0.50);
        stats.set(StatType.HP_FLAT, 50.0);
        stats.set(StatType.MAX_HP_TO_NORMAL_FLAT_DMG_RATIO, 0.10);
        assertClose(54.0,
                calculate(stats, ActionType.NORMAL, 1.0, true, false),
                "final Max HP Normal additive damage");
        assertClose(45.0,
                calculate(stats, ActionType.CHARGE, 1.0, true, false),
                "Max HP ratio excludes Charged damage");
        assertClose(45.0,
                calculate(stats, ActionType.SKILL, 1.0, true, false),
                "Max HP ratio excludes Skill damage");
    }

    private static void testTrueHitAndNegativeBoundaries() {
        StatsContainer stats = standardStats();
        assertClose(0.0,
                calculate(stats, ActionType.SKILL, 0.0, false, false),
                "animation-only dummy excludes every additive branch");
        assertClose(79.2,
                calculate(stats, ActionType.SKILL, 0.0, true, false),
                "explicit zero-multiplier hit keeps derived damage");

        StatsContainer negative = new StatsContainer();
        negative.set(StatType.BASE_ATK, 100.0);
        negative.set(StatType.ELEMENTAL_MASTERY, -10.0);
        negative.set(
                StatType.ELEMENTAL_MASTERY_TO_CHARGED_FLAT_DMG_RATIO,
                2.0);
        assertClose(36.0,
                calculate(negative, ActionType.CHARGE, 1.0, true, false),
                "negative EM remains exact rather than clamped");
    }

    private static void testReservedCharacterIdentities() {
        assertEquals(CharacterId.YAE_MIKO, CharacterId.fromName("Yae Miko"),
                "Yae Miko name lookup");
        assertEquals(CharacterId.YAE_MIKO, CharacterId.fromNumericId(16),
                "Yae Miko numeric lookup");
        assertEquals(CharacterId.ALBEDO, CharacterId.fromName("Albedo"),
                "Albedo name lookup");
        assertEquals(CharacterId.ALBEDO, CharacterId.fromNumericId(17),
                "Albedo numeric lookup");
        assertEquals(CharacterId.FISCHL, CharacterId.fromNumericId(15),
                "existing identity remains stable");
        assertEquals(CharacterId.ROSARIA, CharacterId.fromNumericId(21),
                "extended identity remains stable");
    }

    private static StatsContainer standardStats() {
        StatsContainer stats = new StatsContainer();
        stats.set(StatType.BASE_ATK, 100.0);
        stats.set(StatType.BASE_DEF, 100.0);
        stats.set(StatType.DEF_PERCENT, 0.50);
        stats.set(StatType.DEF_FLAT, 50.0);
        stats.set(StatType.ELEMENTAL_MASTERY, 50.0);
        stats.set(StatType.FLAT_DMG_BONUS, 10.0);
        stats.set(StatType.DEF_TO_SKILL_FLAT_DMG_RATIO, 0.25);
        stats.set(
                StatType.ELEMENTAL_MASTERY_TO_NORMAL_SKILL_FLAT_DMG_RATIO,
                2.0);
        stats.set(
                StatType.ELEMENTAL_MASTERY_TO_CHARGED_FLAT_DMG_RATIO,
                3.0);
        stats.set(
                StatType.ELEMENTAL_MASTERY_TO_SKILL_DMG_BONUS_RATIO,
                0.002);
        return stats;
    }

    private static double calculate(
            StatsContainer stats,
            ActionType actionType,
            double damagePercent,
            boolean hitEffectTrigger,
            boolean countsAsSkillDamage) {
        TestCharacter attacker = new TestCharacter();
        Enemy enemy = new Enemy(90);
        CombatSimulator sim = new CombatSimulator();
        sim.setLoggingEnabled(false);
        sim.setEnemy(enemy);
        AttackAction action = new AttackAction(
                "Derived Action Test",
                damagePercent,
                Element.PHYSICAL,
                StatType.BASE_ATK,
                null,
                0.0,
                actionType);
        action.setHitEffectTrigger(hitEffectTrigger);
        action.setCountsAsSkillDmg(countsAsSkillDamage);
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

    /** Minimal attacker because the formula receives a pre-resolved stat view. */
    private static final class TestCharacter extends Character {
        private TestCharacter() {
            name = "Derived Action Tester";
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

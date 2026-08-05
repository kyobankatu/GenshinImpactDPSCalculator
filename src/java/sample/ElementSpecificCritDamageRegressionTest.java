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

/** Regression checks for element-specific expected CRIT DMG. */
public final class ElementSpecificCritDamageRegressionTest {
    private static final double EPS = 1e-9;

    private ElementSpecificCritDamageRegressionTest() {
    }

    /** Runs element inclusion, isolation, composition, and Lunar checks. */
    public static void main(String[] args) {
        StatsContainer stats = baseStats();
        assertClose(62.5, calculate(stats, Element.PHYSICAL, false),
                "baseline expected crit damage");

        stats.set(StatType.GEO_CRIT_DMG, 0.40);
        assertClose(72.5, calculate(stats, Element.GEO, false),
                "Geo damage receives Geo-only CRIT DMG");
        assertClose(62.5, calculate(stats, Element.PHYSICAL, false),
                "Physical damage ignores Geo-only CRIT DMG");
        assertClose(62.5, calculate(stats, Element.HYDRO, false),
                "non-Geo elemental damage ignores Geo-only CRIT DMG");

        stats.set(StatType.ELECTRO_CRIT_DMG, 0.60);
        assertClose(77.5, calculate(stats, Element.ELECTRO, false),
                "Electro damage receives Electro-only CRIT DMG");
        assertClose(72.5, calculate(stats, Element.GEO, false),
                "Geo damage ignores Electro-only CRIT DMG");
        assertClose(62.5, calculate(stats, Element.HYDRO, false),
                "other elements ignore Electro-only CRIT DMG");

        stats.set(StatType.ANEMO_CRIT_DMG, 0.40);
        assertClose(72.5, calculate(stats, Element.ANEMO, false),
                "Anemo damage receives Anemo-only CRIT DMG");
        assertClose(77.5, calculate(stats, Element.ELECTRO, false),
                "Electro damage ignores Anemo-only CRIT DMG");
        assertClose(62.5, calculate(stats, Element.PHYSICAL, false),
                "Physical damage ignores Anemo-only CRIT DMG");

        stats.set(StatType.CRYO_CRIT_DMG, 0.30);
        assertClose(70.0, calculate(stats, Element.CRYO, false),
                "Cryo damage receives Cryo-only CRIT DMG");
        assertClose(77.5, calculate(stats, Element.ELECTRO, false),
                "Electro damage ignores Cryo-only CRIT DMG");
        assertClose(62.5, calculate(stats, Element.PHYSICAL, false),
                "Physical damage ignores Cryo-only CRIT DMG");

        stats.set(StatType.PHYSICAL_CRIT_DMG, 0.60);
        assertClose(77.5, calculate(stats, Element.PHYSICAL, false),
                "Physical damage receives Physical-only CRIT DMG");
        assertClose(70.0, calculate(stats, Element.CRYO, false),
                "Cryo damage ignores Physical-only CRIT DMG");

        stats.set(StatType.CRIT_RATE, 1.25);
        assertClose(105.0, calculate(stats, Element.ELECTRO, false),
                "Electro and generic CRIT DMG compose at the CRIT Rate cap");

        StatsContainer lunarBaseline = baseStats();
        StatsContainer lunarGeoCrit = baseStats();
        lunarGeoCrit.set(StatType.GEO_CRIT_DMG, 5.0);
        assertSameBits(
                calculate(lunarBaseline, Element.GEO, true),
                calculate(lunarGeoCrit, Element.GEO, true),
                "standard Geo CRIT DMG does not affect Lunar damage");
        StatsContainer lunarElectroCrit = baseStats();
        lunarElectroCrit.set(StatType.ELECTRO_CRIT_DMG, 5.0);
        assertSameBits(
                calculate(lunarBaseline, Element.ELECTRO, true),
                calculate(lunarElectroCrit, Element.ELECTRO, true),
                "standard Electro CRIT DMG does not affect Lunar damage");
        StatsContainer lunarAnemoCrit = baseStats();
        lunarAnemoCrit.set(StatType.ANEMO_CRIT_DMG, 5.0);
        assertSameBits(
                calculate(lunarBaseline, Element.ANEMO, true),
                calculate(lunarAnemoCrit, Element.ANEMO, true),
                "standard Anemo CRIT DMG does not affect Lunar damage");
        StatsContainer lunarCryoCrit = baseStats();
        lunarCryoCrit.set(StatType.CRYO_CRIT_DMG, 5.0);
        assertSameBits(
                calculate(lunarBaseline, Element.CRYO, true),
                calculate(lunarCryoCrit, Element.CRYO, true),
                "standard Cryo CRIT DMG does not affect Lunar damage");
        StatsContainer lunarPhysicalCrit = baseStats();
        lunarPhysicalCrit.set(StatType.PHYSICAL_CRIT_DMG, 5.0);
        assertSameBits(
                calculate(lunarBaseline, Element.PHYSICAL, true),
                calculate(lunarPhysicalCrit, Element.PHYSICAL, true),
                "standard Physical CRIT DMG does not affect Lunar damage");
        System.out.println("ElementSpecificCritDamageRegressionTest passed");
    }

    private static StatsContainer baseStats() {
        StatsContainer stats = new StatsContainer();
        stats.set(StatType.BASE_ATK, 100.0);
        stats.set(StatType.CRIT_RATE, 0.50);
        stats.set(StatType.CRIT_DMG, 0.50);
        return stats;
    }

    private static double calculate(
            StatsContainer stats,
            Element element,
            boolean lunar) {
        AttackAction action = new AttackAction(
                "Element-specific CRIT Test",
                1.0,
                element,
                StatType.BASE_ATK,
                null,
                0.0,
                ActionType.NORMAL);
        action.setLunarConsidered(lunar);
        Enemy enemy = new Enemy(90);
        enemy.setRes(element.getBonusStatType(), 0.0);
        CombatSimulator simulator = new CombatSimulator();
        simulator.setLoggingEnabled(false);
        simulator.setEnemy(enemy);
        return DamageCalculator.calculateDamage(
                new TestCharacter(),
                enemy,
                action,
                List.of(),
                stats,
                0.0,
                1.0,
                simulator);
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
        if (Double.doubleToLongBits(expected)
                != Double.doubleToLongBits(actual)) {
            throw new AssertionError(message + ": expected " + expected
                    + " but got " + actual);
        }
    }

    /** Minimal attacker because formula calls use pre-resolved stats. */
    private static final class TestCharacter extends Character {
        private TestCharacter() {
            name = "Element-specific CRIT Tester";
            characterId = CharacterId.GOROU;
            element = Element.GEO;
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

package sample;

import java.util.Arrays;
import java.util.List;
import java.util.function.IntFunction;

import model.stats.StatsContainer;
import model.type.StatType;
import model.type.WeaponType;
import model.weapon.MaxHpScalingWeapon;
import model.weapon.PrimordialJadeCutter;
import model.weapon.StaffOfHoma;

/** Regression checks for the Max-HP-scaling five-star weapon batch. */
public class MaxHpScalingWeaponRegressionTest {
    private static final double EPS = 1e-9;

    /** Runs metadata, refinement, conversion, isolation, and boundary checks. */
    public static void main(String[] args) {
        testLv90Metadata();
        testDefaultRefinement();
        testRefinementValuesAndValidation();
        testSuppliedHpConversion();
        testLateMergedHpConversion();
        testArbitraryTimeAndUnrelatedStats();
        testIndependentInstances();
        testStaffOfHomaLowHpBoundary();
        System.out.println("MaxHpScalingWeaponRegressionTest passed");
    }

    /** Verifies names, categories, and exact Lv. 90 stat profiles. */
    private static void testLv90Metadata() {
        assertMetadata(new PrimordialJadeCutter(1),
                "Primordial Jade Cutter", WeaponType.SWORD,
                542.0, StatType.CRIT_RATE, 0.441);
        assertMetadata(new StaffOfHoma(1),
                "Staff of Homa", WeaponType.POLEARM,
                608.0, StatType.CRIT_DMG, 0.662);
    }

    /** Verifies the repository convention that no-argument weapons are R5. */
    private static void testDefaultRefinement() {
        assertEquals(5, new PrimordialJadeCutter().getRefinement(),
                "Primordial Jade Cutter default refinement");
        assertEquals(5, new StaffOfHoma().getRefinement(),
                "Staff of Homa default refinement");
    }

    /** Verifies every canonical R1-R5 coefficient and invalid-rank rejection. */
    private static void testRefinementValuesAndValidation() {
        List<IntFunction<MaxHpScalingWeapon>> factories = Arrays.asList(
                PrimordialJadeCutter::new,
                StaffOfHoma::new);
        for (IntFunction<MaxHpScalingWeapon> factory : factories) {
            for (int refinement = 1; refinement <= 5; refinement++) {
                MaxHpScalingWeapon weapon = factory.apply(refinement);
                assertEquals(refinement, weapon.getRefinement(),
                        weapon.getName() + " selected refinement");
                assertClose(0.15 + 0.05 * refinement,
                        weapon.getStats().get(StatType.HP_PERCENT),
                        weapon.getName() + " R" + refinement + " HP bonus");
            }
            assertThrows(() -> factory.apply(0), "refinement zero");
            assertThrows(() -> factory.apply(6), "refinement six");
        }

        for (int refinement = 1; refinement <= 5; refinement++) {
            PrimordialJadeCutter jade = new PrimordialJadeCutter(refinement);
            StaffOfHoma homa = new StaffOfHoma(refinement);
            assertClose(0.009 + 0.003 * refinement,
                    jade.getMaxHpAttackConversion(),
                    "Primordial Jade Cutter conversion at R" + refinement);
            assertClose(0.006 + 0.002 * refinement,
                    homa.getMaxHpAttackConversion(),
                    "Staff of Homa conversion at R" + refinement);
            assertClose(0.008 + 0.002 * refinement,
                    homa.getCanonicalBelowHalfHpAttackConversion(),
                    "Staff of Homa low-HP conversion at R" + refinement);
        }
    }

    /** Verifies base, percentage, flat, and weapon HP all enter conversion. */
    private static void testSuppliedHpConversion() {
        StatsContainer supplied = new StatsContainer();
        supplied.set(StatType.BASE_HP, 10000.0);
        supplied.set(StatType.HP_PERCENT, 0.25);
        supplied.set(StatType.HP_FLAT, 4780.0);
        supplied.set(StatType.ATK_FLAT, 311.0);

        PrimordialJadeCutter weapon = new PrimordialJadeCutter(3);
        StatsContainer assembled = supplied.merge(weapon.getStats());

        double expectedMaxHp = 10000.0 * (1.0 + 0.25 + 0.30) + 4780.0;
        assertClose(expectedMaxHp, assembled.getTotalHp(),
                "supplied Max HP assembly");
        assertClose(542.0 + 311.0 + expectedMaxHp * 0.018,
                assembled.getTotalAtk(),
                "supplied HP conversion to flat ATK");
        assertClose(311.0, assembled.get(StatType.ATK_FLAT),
                "derived conversion should not mutate ordinary flat ATK");
    }

    /** Verifies artifact and team HP merged after weapon stats still convert. */
    private static void testLateMergedHpConversion() {
        PrimordialJadeCutter weapon = new PrimordialJadeCutter(1);
        StatsContainer base = new StatsContainer();
        base.set(StatType.BASE_HP, 10000.0);
        StatsContainer artifact = new StatsContainer();
        artifact.set(StatType.HP_PERCENT, 0.20);
        artifact.set(StatType.HP_FLAT, 1000.0);
        StatsContainer team = new StatsContainer();
        team.set(StatType.HP_PERCENT, 0.25);

        StatsContainer finalView = base.merge(weapon.getStats())
                .merge(artifact)
                .merge(team);
        double expectedMaxHp = 10000.0 * (1.0 + 0.20 + 0.20 + 0.25)
                + 1000.0;
        assertClose(expectedMaxHp, finalView.getTotalHp(),
                "late-merged artifact and team Max HP");
        assertClose(542.0 + expectedMaxHp * 0.012,
                finalView.getTotalAtk(),
                "late-merged HP conversion");
    }

    /** Verifies static timing and preservation of stats outside the passive. */
    private static void testArbitraryTimeAndUnrelatedStats() {
        StaffOfHoma weapon = new StaffOfHoma(2);
        StatsContainer early = assembledStats(weapon, -123.5);
        StatsContainer late = assembledStats(weapon, 9876.25);

        assertClose(early.getTotalAtk(),
                late.getTotalAtk(),
                "arbitrary-time conversion stability");
        assertClose(37.0, early.get(StatType.ELEMENTAL_MASTERY),
                "unrelated Elemental Mastery preservation");
        assertClose(0.40, early.get(StatType.PYRO_DMG_BONUS),
                "unrelated damage bonus preservation");
        assertClose(0.0, early.get(StatType.ATK_PERCENT),
                "conversion must be flat ATK, not ATK percent");
    }

    /** Verifies one refinement instance cannot affect another instance. */
    private static void testIndependentInstances() {
        PrimordialJadeCutter r1 = new PrimordialJadeCutter(1);
        PrimordialJadeCutter r5 = new PrimordialJadeCutter(5);
        StatsContainer r1Stats = assembledStats(r1, 0.0);
        StatsContainer r5Stats = assembledStats(r5, 0.0);

        double r1Hp = 12000.0 * (1.0 + 0.10 + 0.20) + 800.0;
        double r5Hp = 12000.0 * (1.0 + 0.10 + 0.40) + 800.0;
        assertClose(542.0 + 50.0 + r1Hp * 0.012,
                r1Stats.getTotalAtk(),
                "R1 independent conversion");
        assertClose(542.0 + 50.0 + r5Hp * 0.024,
                r5Stats.getTotalAtk(),
                "R5 independent conversion");
        assertClose(0.20, r1.getStats().get(StatType.HP_PERCENT),
                "R1 HP after R5 assembly");
    }

    /** Verifies Homa does not fabricate the unavailable current-HP branch. */
    private static void testStaffOfHomaLowHpBoundary() {
        StaffOfHoma weapon = new StaffOfHoma(5);
        StatsContainer stats = assembledStats(weapon, 42.0);
        double maximumHp = 12000.0 * (1.0 + 0.10 + 0.40) + 800.0;

        assertClose(608.0 + 50.0 + maximumHp * 0.016,
                stats.getTotalAtk(),
                "Staff of Homa unconditional conversion only");
        if (weapon.isBelowHalfHpBonusActive()) {
            throw new AssertionError(
                    "Staff of Homa low-HP bonus must remain inactive");
        }
    }

    /** Creates one fresh assembled stat view and applies the weapon passive. */
    private static StatsContainer assembledStats(
            MaxHpScalingWeapon weapon,
            double currentTime) {
        StatsContainer supplied = new StatsContainer();
        supplied.set(StatType.BASE_HP, 12000.0);
        supplied.set(StatType.HP_PERCENT, 0.10);
        supplied.set(StatType.HP_FLAT, 800.0);
        supplied.set(StatType.ATK_FLAT, 50.0);
        supplied.set(StatType.ELEMENTAL_MASTERY, 37.0);
        supplied.set(StatType.PYRO_DMG_BONUS, 0.40);
        return supplied.merge(weapon.getStats());
    }

    /** Checks one weapon's immutable Lv. 90 metadata. */
    private static void assertMetadata(
            MaxHpScalingWeapon weapon,
            String expectedName,
            WeaponType expectedType,
            double expectedBaseAtk,
            StatType expectedSubstat,
            double expectedSubstatValue) {
        assertEquals(expectedName, weapon.getName(), expectedName + " name");
        assertEquals(expectedType, weapon.getWeaponType(), expectedName + " type");
        assertClose(expectedBaseAtk, weapon.getStats().get(StatType.BASE_ATK),
                expectedName + " base ATK");
        assertClose(expectedSubstatValue,
                weapon.getStats().get(expectedSubstat),
                expectedName + " secondary stat");
    }

    /** Asserts numeric equality within the test tolerance. */
    private static void assertClose(double expected, double actual, String message) {
        if (Math.abs(expected - actual) > EPS) {
            throw new AssertionError(
                    message + ": expected " + expected + " but got " + actual);
        }
    }

    /** Asserts object equality. */
    private static void assertEquals(Object expected, Object actual, String message) {
        if (!expected.equals(actual)) {
            throw new AssertionError(
                    message + ": expected " + expected + " but got " + actual);
        }
    }

    /** Asserts that an invalid refinement throws {@link IllegalArgumentException}. */
    private static void assertThrows(Runnable action, String message) {
        try {
            action.run();
            throw new AssertionError(
                    "Expected IllegalArgumentException for " + message);
        } catch (IllegalArgumentException expected) {
            // Expected.
        }
    }
}

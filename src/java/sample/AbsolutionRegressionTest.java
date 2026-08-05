package sample;

import model.stats.StatsContainer;
import model.type.StatType;
import model.type.WeaponType;
import model.weapon.Absolution;

/** Focused regression checks for Absolution's represented branch. */
public final class AbsolutionRegressionTest {
    private static final double EPSILON = 1e-8;

    private AbsolutionRegressionTest() {
    }

    /** Runs metadata, permanent-passive, isolation, and guard checks. */
    public static void main(String[] args) {
        testMetadataAndRefinement();
        testPermanentCritDamageAndIsolation();
        testIndependentInstancesAndGuards();
        System.out.println("AbsolutionRegressionTest passed");
    }

    private static void testMetadataAndRefinement() {
        Absolution defaultWeapon = new Absolution();
        assertEquals("Absolution", defaultWeapon.getName(),
                "Absolution display name");
        assertEquals(WeaponType.SWORD, defaultWeapon.getWeaponType(),
                "Absolution type");
        assertEquals(5, defaultWeapon.getRefinement(),
                "Absolution default refinement");
        assertClose(674.0,
                defaultWeapon.getStats().get(StatType.BASE_ATK),
                "Absolution base ATK");
        assertClose(0.441,
                defaultWeapon.getStats().get(StatType.CRIT_DMG),
                "Absolution CRIT DMG substat");
        for (int refinement = 1; refinement <= 5; refinement++) {
            Absolution weapon = new Absolution(refinement);
            assertClose(0.15 + 0.05 * refinement,
                    weapon.getPassiveCritDamage(),
                    "Absolution passive CRIT DMG R" + refinement);
        }
    }

    private static void testPermanentCritDamageAndIsolation() {
        Absolution weapon = new Absolution(1);
        for (double time : new double[] { -10.0, 0.0, 100.0 }) {
            StatsContainer stats = new StatsContainer();
            stats.set(StatType.CRIT_DMG, 0.50);
            stats.set(StatType.ATK_PERCENT, 0.20);
            stats.set(StatType.DMG_BONUS_ALL, 0.30);
            weapon.applyPassive(stats, time);
            assertClose(0.70, stats.get(StatType.CRIT_DMG),
                    "Absolution applies permanent CRIT DMG");
            assertClose(0.20, stats.get(StatType.ATK_PERCENT),
                    "Absolution preserves ATK");
            assertClose(0.30, stats.get(StatType.DMG_BONUS_ALL),
                    "Absolution excludes Bond-derived damage");
        }
    }

    private static void testIndependentInstancesAndGuards() {
        Absolution first = new Absolution(1);
        Absolution second = new Absolution(5);
        assertClose(0.20, first.getPassiveCritDamage(),
                "Absolution R1 remains independent");
        assertClose(0.40, second.getPassiveCritDamage(),
                "Absolution R5 remains independent");
        assertThrows(IllegalArgumentException.class,
                () -> new Absolution(0),
                "Absolution rejects R0");
        assertThrows(IllegalArgumentException.class,
                () -> new Absolution(6),
                "Absolution rejects R6");
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
}

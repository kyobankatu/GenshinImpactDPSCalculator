package sample;

import model.stats.StatsContainer;
import model.type.StatType;
import model.type.WeaponType;
import model.weapon.StarcallersWatch;

/** Focused metadata, refinement, and unavailable-shield boundary checks. */
public final class StarcallersWatchRegressionTest {
    private static final double EPSILON = 1e-9;

    private StarcallersWatchRegressionTest() {
    }

    /** Runs exact metadata, stat-isolation, refinement, and guard cases. */
    public static void main(String[] args) {
        testMetadataAndRefinement();
        testPermanentEmAndInactiveMirrorIsolation();
        testInvalidRefinement();
        System.out.println("StarcallersWatchRegressionTest passed");
    }

    private static void testMetadataAndRefinement() {
        StarcallersWatch defaultWeapon = new StarcallersWatch();
        assertEquals("Starcaller's Watch", defaultWeapon.getName(),
                "Starcaller's Watch display name");
        assertEquals(WeaponType.CATALYST,
                defaultWeapon.getWeaponType(),
                "Starcaller's Watch weapon type");
        assertEquals(5, defaultWeapon.getRefinement(),
                "Starcaller's Watch default refinement");
        assertClose(542.0,
                defaultWeapon.getStats().get(StatType.BASE_ATK),
                "Starcaller's Watch base ATK");
        assertClose(265.0,
                defaultWeapon.getStats().get(StatType.ELEMENTAL_MASTERY),
                "Starcaller's Watch EM substat");

        for (int refinement = 1; refinement <= 5; refinement++) {
            StarcallersWatch weapon = new StarcallersWatch(refinement);
            assertClose(75.0 + 25.0 * refinement,
                    weapon.getPermanentElementalMastery(),
                    "Starcaller's Watch permanent EM R" + refinement);
            assertClose(0.21 + 0.07 * refinement,
                    weapon.getMirrorOfNightDamageBonus(),
                    "Starcaller's Watch Mirror DMG R" + refinement);
        }
    }

    private static void testPermanentEmAndInactiveMirrorIsolation() {
        for (int refinement = 1; refinement <= 5; refinement++) {
            StarcallersWatch weapon = new StarcallersWatch(refinement);
            StatsContainer stats = seededStats();
            weapon.applyPassive(stats, -123.0);
            assertClose(10.0 + 75.0 + 25.0 * refinement,
                    stats.get(StatType.ELEMENTAL_MASTERY),
                    "Starcaller's Watch permanent EM R" + refinement);
            assertClose(0.20, stats.get(StatType.DMG_BONUS_ALL),
                    "Starcaller's Watch does not synthesize Mirror DMG R"
                            + refinement);
            assertClose(0.30, stats.get(StatType.ATK_PERCENT),
                    "Starcaller's Watch preserves unrelated ATK R"
                            + refinement);

            StatsContainer late = seededStats();
            weapon.applyPassive(late, 1_000_000.0);
            assertClose(stats.get(StatType.ELEMENTAL_MASTERY),
                    late.get(StatType.ELEMENTAL_MASTERY),
                    "Starcaller's Watch permanent branch is time-invariant R"
                            + refinement);
        }
    }

    private static void testInvalidRefinement() {
        assertThrows(IllegalArgumentException.class,
                () -> new StarcallersWatch(0),
                "Starcaller's Watch rejects R0");
        assertThrows(IllegalArgumentException.class,
                () -> new StarcallersWatch(6),
                "Starcaller's Watch rejects R6");
    }

    private static StatsContainer seededStats() {
        StatsContainer stats = new StatsContainer();
        stats.set(StatType.ELEMENTAL_MASTERY, 10.0);
        stats.set(StatType.DMG_BONUS_ALL, 0.20);
        stats.set(StatType.ATK_PERCENT, 0.30);
        return stats;
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
        if (!java.util.Objects.equals(expected, actual)) {
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

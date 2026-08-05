package sample;

import model.stats.StatsContainer;
import model.type.StatType;
import model.type.WeaponType;
import model.weapon.BoundaryInactiveWeapon;
import model.weapon.DialoguesOfTheDesertSages;
import model.weapon.PortablePowerSaw;
import model.weapon.RangeGauge;
import model.weapon.SongOfStillness;
import model.weapon.TidalShadow;

/** Table-driven regression checks for healing-boundary weapons. */
public final class HealingBoundaryWeaponRegressionTest {
    private static final double EPSILON = 1e-8;

    private HealingBoundaryWeaponRegressionTest() {
    }

    /** Runs metadata, no-op boundary, and refinement guard checks. */
    public static void main(String[] args) {
        testMetadataAndRefinements();
        testArbitraryTimeNoOpBoundary();
        testRefinementGuards();
        System.out.println("HealingBoundaryWeaponRegressionTest passed");
    }

    private static void testMetadataAndRefinements() {
        WeaponCase[] cases = cases();
        for (WeaponCase weaponCase : cases) {
            BoundaryInactiveWeapon defaultWeapon = weaponCase.create(5);
            assertEquals(weaponCase.name, defaultWeapon.getName(),
                    weaponCase.name + " display name");
            assertEquals(weaponCase.type, defaultWeapon.getWeaponType(),
                    weaponCase.name + " type");
            assertClose(weaponCase.baseAtk,
                    defaultWeapon.getStats().get(StatType.BASE_ATK),
                    weaponCase.name + " base ATK");
            assertClose(weaponCase.substatValue,
                    defaultWeapon.getStats().get(weaponCase.substat),
                    weaponCase.name + " substat");
            for (int refinement = 1; refinement <= 5; refinement++) {
                assertEquals(refinement,
                        weaponCase.create(refinement).getRefinement(),
                        weaponCase.name + " selected refinement");
            }
        }
    }

    private static void testArbitraryTimeNoOpBoundary() {
        for (WeaponCase weaponCase : cases()) {
            for (int refinement : new int[] { 1, 5 }) {
                BoundaryInactiveWeapon weapon =
                        weaponCase.create(refinement);
                for (double time : new double[] { -10.0, 0.0, 100.0 }) {
                    StatsContainer stats = sentinelStats();
                    weapon.applyPassive(stats, time);
                    assertClose(0.25, stats.get(StatType.HP_PERCENT),
                            weaponCase.name + " preserves HP");
                    assertClose(0.20, stats.get(StatType.ATK_PERCENT),
                            weaponCase.name + " preserves ATK");
                    assertClose(80.0,
                            stats.get(StatType.ELEMENTAL_MASTERY),
                            weaponCase.name + " preserves EM");
                    assertClose(0.30,
                            stats.get(StatType.DMG_BONUS_ALL),
                            weaponCase.name + " preserves generic damage");
                }
            }
        }
    }

    private static void testRefinementGuards() {
        for (WeaponCase weaponCase : cases()) {
            assertThrows(IllegalArgumentException.class,
                    () -> weaponCase.create(0),
                    weaponCase.name + " rejects R0");
            assertThrows(IllegalArgumentException.class,
                    () -> weaponCase.create(6),
                    weaponCase.name + " rejects R6");
        }
    }

    private static WeaponCase[] cases() {
        return new WeaponCase[] {
            new WeaponCase(
                    "Dialogues of the Desert Sages",
                    WeaponType.POLEARM,
                    510.0,
                    StatType.HP_PERCENT,
                    0.413,
                    DialoguesOfTheDesertSages::new),
            new WeaponCase(
                    "Range Gauge",
                    WeaponType.BOW,
                    565.0,
                    StatType.ATK_PERCENT,
                    0.276,
                    RangeGauge::new),
            new WeaponCase(
                    "Portable Power Saw",
                    WeaponType.CLAYMORE,
                    454.0,
                    StatType.HP_PERCENT,
                    0.551,
                    PortablePowerSaw::new),
            new WeaponCase(
                    "Song of Stillness",
                    WeaponType.BOW,
                    510.0,
                    StatType.ATK_PERCENT,
                    0.413,
                    SongOfStillness::new),
            new WeaponCase(
                    "Tidal Shadow",
                    WeaponType.CLAYMORE,
                    510.0,
                    StatType.ATK_PERCENT,
                    0.413,
                    TidalShadow::new)
        };
    }

    private static StatsContainer sentinelStats() {
        StatsContainer stats = new StatsContainer();
        stats.set(StatType.HP_PERCENT, 0.25);
        stats.set(StatType.ATK_PERCENT, 0.20);
        stats.set(StatType.ELEMENTAL_MASTERY, 80.0);
        stats.set(StatType.DMG_BONUS_ALL, 0.30);
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
    private interface WeaponFactory {
        BoundaryInactiveWeapon create(int refinement);
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }

    private static final class WeaponCase {
        private final String name;
        private final WeaponType type;
        private final double baseAtk;
        private final StatType substat;
        private final double substatValue;
        private final WeaponFactory factory;

        private WeaponCase(
                String name,
                WeaponType type,
                double baseAtk,
                StatType substat,
                double substatValue,
                WeaponFactory factory) {
            this.name = name;
            this.type = type;
            this.baseAtk = baseAtk;
            this.substat = substat;
            this.substatValue = substatValue;
            this.factory = factory;
        }

        private BoundaryInactiveWeapon create(int refinement) {
            return factory.create(refinement);
        }
    }
}

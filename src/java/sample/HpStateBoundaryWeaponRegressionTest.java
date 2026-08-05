package sample;

import java.util.function.IntFunction;

import model.entity.Weapon;
import model.stats.StatsContainer;
import model.type.StatType;
import model.type.WeaponType;
import model.weapon.BoundaryInactiveWeapon;
import model.weapon.RightfulReward;
import model.weapon.TheBell;
import model.weapon.TomeOfTheEternalFlow;

/** Regression checks for HP-state boundary weapons. */
public final class HpStateBoundaryWeaponRegressionTest {
    private static final double EPSILON = 1e-8;

    private HpStateBoundaryWeaponRegressionTest() {
    }

    /** Runs metadata, refinement, static-passive, and guard checks. */
    public static void main(String[] args) {
        testInactiveWeaponMetadata();
        testInactiveBoundaries();
        testTomeMetadataAndStaticHp();
        testRefinementGuards();
        System.out.println("HpStateBoundaryWeaponRegressionTest passed");
    }

    private static void testInactiveWeaponMetadata() {
        assertMetadata(new TheBell(), "The Bell", WeaponType.CLAYMORE,
                510.0, StatType.HP_PERCENT, 0.413, 5);
        assertMetadata(new RightfulReward(), "Rightful Reward",
                WeaponType.POLEARM, 565.0,
                StatType.HP_PERCENT, 0.276, 5);
        for (int refinement = 1; refinement <= 5; refinement++) {
            assertEquals(refinement, new TheBell(refinement).getRefinement(),
                    "The Bell selected refinement");
            assertEquals(refinement,
                    new RightfulReward(refinement).getRefinement(),
                    "Rightful Reward selected refinement");
        }
    }

    private static void testInactiveBoundaries() {
        for (BoundaryInactiveWeapon weapon : new BoundaryInactiveWeapon[] {
                new TheBell(1),
                new TheBell(5),
                new RightfulReward(1),
                new RightfulReward(5)
        }) {
            for (double time : new double[] { -10.0, 0.0, 100.0 }) {
                StatsContainer stats = sentinelStats();
                weapon.applyPassive(stats, time);
                assertSentinel(stats,
                        weapon.getName() + " inactive at " + time);
            }
        }
    }

    private static void testTomeMetadataAndStaticHp() {
        TomeOfTheEternalFlow defaultTome =
                new TomeOfTheEternalFlow();
        assertMetadata(defaultTome, "Tome of the Eternal Flow",
                WeaponType.CATALYST, 542.0,
                StatType.CRIT_DMG, 0.882, 5);
        for (int refinement = 1; refinement <= 5; refinement++) {
            TomeOfTheEternalFlow tome =
                    new TomeOfTheEternalFlow(refinement);
            double expected = 0.12 + 0.04 * refinement;
            assertEquals(refinement, tome.getRefinement(),
                    "Tome selected refinement");
            assertClose(expected, tome.getHpBonus(),
                    "Tome HP bonus R" + refinement);
            for (double time : new double[] { -10.0, 0.0, 100.0 }) {
                StatsContainer stats = sentinelStats();
                tome.applyPassive(stats, time);
                assertClose(0.25 + expected,
                        stats.get(StatType.HP_PERCENT),
                        "Tome HP bonus at arbitrary time");
                assertClose(0.20, stats.get(StatType.CHARGED_ATTACK_DMG_BONUS),
                        "Tome does not fabricate HP-change stacks");
                assertClose(17.0, stats.get(StatType.ENERGY_RECHARGE),
                        "Tome preserves unrelated Energy Recharge");
            }
        }
    }

    private static void testRefinementGuards() {
        for (IntFunction<? extends Weapon> factory : weaponFactories()) {
            assertThrows(IllegalArgumentException.class,
                    () -> factory.apply(0),
                    "Boundary weapon rejects R0");
            assertThrows(IllegalArgumentException.class,
                    () -> factory.apply(6),
                    "Boundary weapon rejects R6");
        }
    }

    @SuppressWarnings("unchecked")
    private static IntFunction<? extends Weapon>[] weaponFactories() {
        return new IntFunction[] {
            (IntFunction<Weapon>) TheBell::new,
            (IntFunction<Weapon>) RightfulReward::new,
            (IntFunction<Weapon>) TomeOfTheEternalFlow::new
        };
    }

    private static void assertMetadata(
            Weapon weapon,
            String name,
            WeaponType type,
            double baseAtk,
            StatType substat,
            double substatValue,
            int expectedRefinement) {
        assertEquals(name, weapon.getName(), name + " display name");
        assertEquals(type, weapon.getWeaponType(), name + " weapon type");
        assertClose(baseAtk, weapon.getStats().get(StatType.BASE_ATK),
                name + " base ATK");
        assertClose(substatValue, weapon.getStats().get(substat),
                name + " substat");
        int refinement = weapon instanceof BoundaryInactiveWeapon
                ? ((BoundaryInactiveWeapon) weapon).getRefinement()
                : ((TomeOfTheEternalFlow) weapon).getRefinement();
        assertEquals(expectedRefinement, refinement,
                name + " default refinement");
    }

    private static StatsContainer sentinelStats() {
        StatsContainer stats = new StatsContainer();
        stats.set(StatType.HP_PERCENT, 0.25);
        stats.set(StatType.CHARGED_ATTACK_DMG_BONUS, 0.20);
        stats.set(StatType.ENERGY_RECHARGE, 17.0);
        return stats;
    }

    private static void assertSentinel(
            StatsContainer stats,
            String message) {
        assertClose(0.25, stats.get(StatType.HP_PERCENT),
                message + " HP");
        assertClose(0.20, stats.get(StatType.CHARGED_ATTACK_DMG_BONUS),
                message + " Charged");
        assertClose(17.0, stats.get(StatType.ENERGY_RECHARGE),
                message + " ER");
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

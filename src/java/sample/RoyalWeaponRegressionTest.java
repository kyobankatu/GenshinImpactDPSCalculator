package sample;

import java.util.Arrays;
import java.util.List;
import java.util.function.IntFunction;

import model.entity.DamageTriggeredWeaponEffect;
import model.stats.StatsContainer;
import model.type.StatType;
import model.type.WeaponType;
import model.weapon.RoyalBow;
import model.weapon.RoyalGreatsword;
import model.weapon.RoyalGrimoire;
import model.weapon.RoyalLongsword;
import model.weapon.RoyalSpear;
import model.weapon.RoyalWeapon;

/** Regression checks for Royal weapon metadata and the inactive Focus boundary. */
public class RoyalWeaponRegressionTest {
    private static final double EPS = 1e-9;

    /** Runs all Royal family regression checks. */
    public static void main(String[] args) {
        testLv90Metadata();
        testDefaultRefinement();
        testRefinementMetadataAndValidation();
        testFocusRuntimeBoundary();
        System.out.println("RoyalWeaponRegressionTest passed");
    }

    /** Verifies names, categories, and exact Lv. 90 stat profiles. */
    private static void testLv90Metadata() {
        assertMetadata(new RoyalLongsword(1),
                "Royal Longsword", WeaponType.SWORD, 510.0, 0.413);
        assertMetadata(new RoyalGreatsword(1),
                "Royal Greatsword", WeaponType.CLAYMORE, 565.0, 0.276);
        assertMetadata(new RoyalSpear(1),
                "Royal Spear", WeaponType.POLEARM, 565.0, 0.276);
        assertMetadata(new RoyalGrimoire(1),
                "Royal Grimoire", WeaponType.CATALYST, 565.0, 0.276);
        assertMetadata(new RoyalBow(1),
                "Royal Bow", WeaponType.BOW, 510.0, 0.413);
    }

    /** Verifies the repository convention that no-argument weapons are R5. */
    private static void testDefaultRefinement() {
        List<RoyalWeapon> weapons = Arrays.asList(
                new RoyalLongsword(),
                new RoyalGreatsword(),
                new RoyalSpear(),
                new RoyalGrimoire(),
                new RoyalBow());
        for (RoyalWeapon weapon : weapons) {
            assertEquals(5, weapon.getRefinement(), weapon.getName() + " default refinement");
        }
    }

    /** Verifies all canonical Focus refinement values and rejects invalid ranks. */
    private static void testRefinementMetadataAndValidation() {
        List<IntFunction<RoyalWeapon>> factories = Arrays.asList(
                RoyalLongsword::new,
                RoyalGreatsword::new,
                RoyalSpear::new,
                RoyalGrimoire::new,
                RoyalBow::new);
        for (IntFunction<RoyalWeapon> factory : factories) {
            for (int refinement = 1; refinement <= 5; refinement++) {
                RoyalWeapon weapon = factory.apply(refinement);
                assertEquals(refinement, weapon.getRefinement(),
                        weapon.getName() + " selected refinement");
                assertClose(0.06 + 0.02 * refinement,
                        weapon.getCanonicalFocusCritRatePerStack(),
                        weapon.getName() + " R" + refinement + " Focus CRIT Rate");
                assertEquals(5, weapon.getCanonicalFocusMaxStacks(),
                        weapon.getName() + " Focus stack cap");
            }
            assertThrows(() -> factory.apply(0), "refinement zero");
            assertThrows(() -> factory.apply(6), "refinement six");
        }
    }

    /**
     * Verifies Focus remains an explicit no-op without a realized-CRIT callback.
     */
    private static void testFocusRuntimeBoundary() {
        RoyalWeapon weapon = new RoyalLongsword(5);
        StatsContainer stats = new StatsContainer();
        stats.set(StatType.CRIT_RATE, 0.25);

        weapon.applyPassive(stats, 10.0);

        assertClose(0.25, stats.get(StatType.CRIT_RATE),
                "inactive Focus must not add unresettable CRIT Rate");
        if (weapon.isFocusRuntimeActive()) {
            throw new AssertionError("Focus must report inactive in the average-CRIT runtime");
        }
        if (weapon instanceof DamageTriggeredWeaponEffect) {
            throw new AssertionError("Inactive Focus must not register a partial damage hook");
        }
    }

    /** Checks one weapon's immutable Lv. 90 metadata. */
    private static void assertMetadata(
            RoyalWeapon weapon,
            String expectedName,
            WeaponType expectedType,
            double expectedBaseAtk,
            double expectedAttackPercent) {
        assertEquals(expectedName, weapon.getName(), expectedName + " name");
        assertEquals(expectedType, weapon.getWeaponType(), expectedName + " type");
        assertClose(expectedBaseAtk, weapon.getStats().get(StatType.BASE_ATK),
                expectedName + " base ATK");
        assertClose(expectedAttackPercent, weapon.getStats().get(StatType.ATK_PERCENT),
                expectedName + " ATK substat");
    }

    /** Asserts a numeric equality within the test tolerance. */
    private static void assertClose(double expected, double actual, String message) {
        if (Math.abs(expected - actual) > EPS) {
            throw new AssertionError(
                    "Expected " + message + " to be " + expected + " but was " + actual);
        }
    }

    /** Asserts object equality. */
    private static void assertEquals(Object expected, Object actual, String message) {
        if (!expected.equals(actual)) {
            throw new AssertionError(
                    "Expected " + message + " to be " + expected + " but was " + actual);
        }
    }

    /** Asserts that an invalid refinement throws {@link IllegalArgumentException}. */
    private static void assertThrows(Runnable action, String message) {
        try {
            action.run();
            throw new AssertionError("Expected IllegalArgumentException for " + message);
        } catch (IllegalArgumentException expected) {
            // Expected.
        }
    }
}

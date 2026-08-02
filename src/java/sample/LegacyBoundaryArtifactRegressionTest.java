package sample;

import java.util.function.Function;
import java.util.function.Supplier;

import model.artifact.PrayersForDestiny;
import model.artifact.PrayersForIllumination;
import model.artifact.PrayersForWisdom;
import model.artifact.PrayersToSpringtime;
import model.artifact.TinyMiracle;
import model.artifact.TravelingDoctor;
import model.entity.ArtifactSet;
import model.entity.Character;
import model.entity.Weapon;
import model.stats.StatsContainer;
import model.type.CharacterId;
import model.type.Element;
import model.type.StatType;

/** Regression checks for legacy artifact sets outside the combat-state model. */
public final class LegacyBoundaryArtifactRegressionTest {
    private static final double EPS = 1e-9;
    private static final double[] TEST_TIMES = { -100.0, 0.0, 10000.0 };

    private LegacyBoundaryArtifactRegressionTest() {
    }

    /** Runs metadata, stat preservation, time stability, and boundary checks. */
    public static void main(String[] args) {
        testSet("Prayers for Destiny", PrayersForDestiny::new,
                PrayersForDestiny::new);
        testSet("Prayers for Illumination", PrayersForIllumination::new,
                PrayersForIllumination::new);
        testSet("Prayers to Springtime", PrayersToSpringtime::new,
                PrayersToSpringtime::new);
        testSet("Prayers for Wisdom", PrayersForWisdom::new,
                PrayersForWisdom::new);
        testSet("Tiny Miracle", TinyMiracle::new, TinyMiracle::new);
        testSet("Traveling Doctor", TravelingDoctor::new,
                TravelingDoctor::new);
        testNullStats();
        System.out.println("LegacyBoundaryArtifactRegressionTest passed");
    }

    /**
     * Verifies one boundary set without coupling the fixture to its class.
     *
     * @param expectedName canonical set display name
     * @param freshFactory constructor using an independent fresh container
     * @param suppliedFactory constructor preserving a supplied container
     */
    private static void testSet(
            String expectedName,
            Supplier<ArtifactSet> freshFactory,
            Function<StatsContainer, ArtifactSet> suppliedFactory) {
        ArtifactSet fresh = freshFactory.get();
        assertEquals(expectedName, fresh.getName(), expectedName + " name");
        assertAllZero(fresh.getStats(), expectedName + " fresh stats");

        StatsContainer supplied = new StatsContainer();
        supplied.set(StatType.ELEMENTAL_MASTERY, 37.0);
        ArtifactSet preserved = suppliedFactory.apply(supplied);
        assertTrue(preserved.getStats() == supplied,
                expectedName + " should retain the supplied container");
        assertClose(37.0, supplied.get(StatType.ELEMENTAL_MASTERY),
                expectedName + " supplied stat preservation");
        assertOnlyMarkerStat(supplied, expectedName + " unsupported effects");

        StatsContainer passiveTarget = new StatsContainer();
        passiveTarget.set(StatType.ELEMENTAL_MASTERY, 11.0);
        preserved.applyPassive(passiveTarget);
        assertOnlyStat(passiveTarget, StatType.ELEMENTAL_MASTERY, 11.0,
                expectedName + " passive boundary");

        ArtifactSet independent = freshFactory.get();
        fresh.getStats().set(StatType.ATK_PERCENT, 0.75);
        assertAllZero(independent.getStats(),
                expectedName + " independent fresh instances");

        TestCharacter wearer = new TestCharacter(independent);
        for (double time : TEST_TIMES) {
            StatsContainer effective = wearer.getEffectiveStats(time);
            assertClose(0.0, effective.get(StatType.ATK_PERCENT),
                    expectedName + " ATK at time " + time);
            assertClose(0.0, effective.get(StatType.DMG_BONUS_ALL),
                    expectedName + " outgoing damage at time " + time);
            assertClose(0.0, effective.get(StatType.RES_SHRED),
                    expectedName + " enemy resistance at time " + time);
            assertClose(0.0, effective.get(StatType.HEALING_BONUS),
                    expectedName + " outgoing healing at time " + time);
            assertClose(0.0, effective.get(StatType.HP_FLAT),
                    expectedName + " fabricated HP restoration at time " + time);
        }
    }

    /** Verifies that every supplied-stat constructor rejects null explicitly. */
    private static void testNullStats() {
        assertNullRejected(() -> new PrayersForDestiny(null),
                "Prayers for Destiny null stats");
        assertNullRejected(() -> new PrayersForIllumination(null),
                "Prayers for Illumination null stats");
        assertNullRejected(() -> new PrayersToSpringtime(null),
                "Prayers to Springtime null stats");
        assertNullRejected(() -> new PrayersForWisdom(null),
                "Prayers for Wisdom null stats");
        assertNullRejected(() -> new TinyMiracle(null),
                "Tiny Miracle null stats");
        assertNullRejected(() -> new TravelingDoctor(null),
                "Traveling Doctor null stats");
    }

    /** Asserts that every stat in a container is zero. */
    private static void assertAllZero(StatsContainer stats, String message) {
        for (StatType type : StatType.values()) {
            assertClose(0.0, stats.get(type), message + " " + type);
        }
    }

    /** Asserts that the supplied marker is the container's only non-zero stat. */
    private static void assertOnlyMarkerStat(StatsContainer stats, String message) {
        assertOnlyStat(stats, StatType.ELEMENTAL_MASTERY, 37.0, message);
    }

    /** Asserts that one expected stat is the container's only non-zero stat. */
    private static void assertOnlyStat(
            StatsContainer stats,
            StatType expectedType,
            double expectedValue,
            String message) {
        for (StatType type : StatType.values()) {
            double expected = type == expectedType ? expectedValue : 0.0;
            assertClose(expected, stats.get(type), message + " " + type);
        }
    }

    /** Asserts that a constructor rejects null. */
    private static void assertNullRejected(Runnable constructor, String message) {
        try {
            constructor.run();
            throw new AssertionError(message + ": expected NullPointerException");
        } catch (NullPointerException expected) {
            // Expected.
        }
    }

    /** Asserts two doubles are equal within the test tolerance. */
    private static void assertClose(double expected, double actual, String message) {
        if (Math.abs(expected - actual) > EPS) {
            throw new AssertionError(
                    message + ": expected " + expected + " but got " + actual);
        }
    }

    /** Asserts object equality. */
    private static void assertEquals(Object expected, Object actual, String message) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError(
                    message + ": expected " + expected + " but got " + actual);
        }
    }

    /** Asserts a boolean condition. */
    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    /** Minimal wearer used to evaluate artifact stats at arbitrary times. */
    private static final class TestCharacter extends Character {
        private TestCharacter(ArtifactSet artifact) {
            name = "Legacy Artifact Tester";
            characterId = CharacterId.SUCROSE;
            element = Element.ANEMO;
            weapon = new Weapon("Test Weapon", new StatsContainer());
            artifacts = new ArtifactSet[] { artifact };
            baseStats.set(StatType.BASE_HP, 10000.0);
            baseStats.set(StatType.BASE_ATK, 1000.0);
            baseStats.set(StatType.BASE_DEF, 700.0);
        }

        /** No character-specific passive. */
        @Override
        public void applyPassive(StatsContainer currentStats) {
        }

        /** Returns an unused Burst cost for the fixture. */
        @Override
        public double getEnergyCost() {
            return 60.0;
        }
    }
}

package sample;

import java.util.ArrayList;
import java.util.List;

import model.entity.SnapshotAwareWeaponEffect;
import model.stats.StatsContainer;
import model.type.ActionType;
import model.type.CharacterId;
import model.type.StatType;
import model.type.WeaponType;
import model.weapon.SwordOfDescension;
import simulation.CombatSimulator;
import simulation.action.AttackAction;

/** Focused Sword of Descension metadata, proc, affinity, and state regressions. */
public final class SwordOfDescensionRegressionTest {
    private static final double EPSILON = 1e-8;
    private static final String PROC_NAME = "Sword of Descension Proc";

    private SwordOfDescensionRegressionTest() {
    }

    /** Runs normal, boundary, and abnormal Sword of Descension cases. */
    public static void main(String[] args) {
        testMetadataAndTypedTravelerAffinity();
        testProcChanceCategoriesAndCooldown();
        testSnapshotAndBindingGuards();
        System.out.println("SwordOfDescensionRegressionTest passed");
    }

    private static void testMetadataAndTypedTravelerAffinity() {
        SwordOfDescension travelerWeapon = new SwordOfDescension(() -> 1.0);
        StatefulWeaponRegressionSupport.TestCharacter traveler =
                character(CharacterId.TRAVELER, travelerWeapon);
        CombatSimulator travelerSimulator = simulator(traveler);

        assertEquals("Sword of Descension", travelerWeapon.getName(),
                "Sword of Descension display name");
        assertEquals(WeaponType.SWORD, travelerWeapon.getWeaponType(),
                "Sword of Descension weapon type");
        assertEquals(1, travelerWeapon.getRefinement(),
                "Sword of Descension fixed refinement");
        assertTrue(travelerWeapon.isPlatformPassiveEnabled(),
                "Sword of Descension explicit platform boundary");
        assertClose(440.0,
                travelerWeapon.getStats().get(StatType.BASE_ATK),
                "Sword of Descension base ATK");
        assertClose(0.352,
                travelerWeapon.getStats().get(StatType.ATK_PERCENT),
                "Sword of Descension ATK percent");
        StatsContainer travelerStats = stats(traveler, travelerSimulator);
        assertClose(66.0, travelerStats.get(StatType.ATK_FLAT),
                "typed Traveler receives flat ATK affinity");

        SwordOfDescension foreignWeapon = new SwordOfDescension(() -> 1.0);
        StatefulWeaponRegressionSupport.TestCharacter foreign =
                character(CharacterId.BENNETT, foreignWeapon);
        CombatSimulator foreignSimulator = simulator(foreign);
        assertClose(0.0,
                stats(foreign, foreignSimulator).get(StatType.ATK_FLAT),
                "non-Traveler does not receive affinity");
        assertEquals(CharacterId.UNKNOWN, CharacterId.fromName("Aether"),
                "display alias cannot bypass canonical typed identity");
    }

    private static void testProcChanceCategoriesAndCooldown() {
        SwordOfDescension weapon = new SwordOfDescension(() -> 0.0);
        StatefulWeaponRegressionSupport.TestCharacter owner =
                character(CharacterId.TRAVELER, weapon);
        CombatSimulator simulator = simulator(owner);
        List<AttackAction> procs = captureProcs(simulator);

        calculate(owner, hit("Skill", ActionType.SKILL), simulator);
        calculate(owner, hit("Normal", ActionType.NORMAL), simulator);
        assertEquals(1, procs.size(),
                "eligible successful Normal triggers once");
        assertClose(2.0, procs.get(0).getDamagePercent(),
                "proc motion value");

        calculate(owner, hit("Charged", ActionType.CHARGE), simulator);
        assertEquals(1, procs.size(), "cooldown blocks immediate Charged proc");
        simulator.advanceTime(10.0 - EPSILON);
        calculate(owner, hit("Normal before boundary", ActionType.NORMAL),
                simulator);
        assertEquals(1, procs.size(), "cooldown is active before ten seconds");
        simulator.advanceTime(EPSILON);
        calculate(owner, hit("Charged at boundary", ActionType.CHARGE),
                simulator);
        assertEquals(2, procs.size(), "cooldown accepts exact boundary");

        SwordOfDescension failedWeapon = new SwordOfDescension(() -> 0.5);
        StatefulWeaponRegressionSupport.TestCharacter failedOwner =
                character(CharacterId.TRAVELER, failedWeapon);
        CombatSimulator failedSimulator = simulator(failedOwner);
        List<AttackAction> failedProcs = captureProcs(failedSimulator);
        calculate(failedOwner, hit("Failed Normal", ActionType.NORMAL),
                failedSimulator);
        assertEquals(0, failedProcs.size(),
                "draw equal to 0.5 fails the half-open chance gate");

        AttackAction nonHit = hit("Non-hit Normal", ActionType.NORMAL);
        nonHit.setHitEffectTrigger(false);
        failedWeapon.onDamage(failedOwner, nonHit, 0.0, failedSimulator);
        failedWeapon.onDamage(failedOwner, null, 0.0, failedSimulator);
        assertEquals(0, failedProcs.size(),
                "null and non-hit actions fail closed");
    }

    private static void testSnapshotAndBindingGuards() {
        SwordOfDescension weapon = new SwordOfDescension(() -> 0.0);
        StatefulWeaponRegressionSupport.TestCharacter owner =
                character(CharacterId.TRAVELER, weapon);
        CombatSimulator simulator = simulator(owner);
        List<AttackAction> procs = captureProcs(simulator);
        calculate(owner, hit("Initial Normal", ActionType.NORMAL), simulator);
        SnapshotAwareWeaponEffect.State state = weapon.captureWeaponState();
        simulator.advanceTime(10.0);
        calculate(owner, hit("Divergent Normal", ActionType.NORMAL), simulator);
        assertEquals(2, procs.size(), "divergent state procs after cooldown");
        weapon.restoreWeaponState(state);
        weapon.onDamage(owner,
                hit("Restored before-boundary Normal", ActionType.NORMAL),
                10.0 - EPSILON, simulator);
        assertEquals(2, procs.size(),
                "restored cooldown blocks the instant before its boundary");
        weapon.onDamage(owner,
                hit("Restored boundary Normal", ActionType.NORMAL),
                10.0, simulator);
        assertEquals(3, procs.size(),
                "restored cooldown accepts its exact boundary");

        SwordOfDescension other = new SwordOfDescension(() -> 0.0);
        assertThrows(IllegalArgumentException.class,
                () -> other.restoreWeaponState(state),
                "another weapon instance rejects captured state");
        assertThrows(IllegalArgumentException.class,
                () -> weapon.restoreWeaponState(
                        new SnapshotAwareWeaponEffect.State() { }),
                "foreign state type is rejected");
        assertThrows(IllegalArgumentException.class,
                () -> weapon.initializeForSimulator(null, simulator),
                "null owner is rejected");
        assertThrows(IllegalStateException.class,
                () -> weapon.initializeForSimulator(owner,
                        new CombatSimulator()),
                "cross-simulator rebinding is rejected");
        assertThrows(NullPointerException.class,
                () -> new SwordOfDescension(null),
                "null proc source is rejected");
    }

    private static List<AttackAction> captureProcs(CombatSimulator simulator) {
        List<AttackAction> procs = new ArrayList<>();
        simulator.addDamageListener((actor, action, damage, time) -> {
            if (PROC_NAME.equals(action.getName())) {
                procs.add(action);
            }
        });
        return procs;
    }

    private static StatefulWeaponRegressionSupport.TestCharacter character(
            CharacterId id,
            SwordOfDescension weapon) {
        return StatefulWeaponRegressionSupport.character(id, weapon);
    }

    private static CombatSimulator simulator(
            StatefulWeaponRegressionSupport.TestCharacter owner) {
        return StatefulWeaponRegressionSupport.simulatorWith(owner);
    }

    private static AttackAction hit(String name, ActionType type) {
        return StatefulWeaponRegressionSupport.hit(name, type);
    }

    private static double calculate(
            StatefulWeaponRegressionSupport.TestCharacter owner,
            AttackAction action,
            CombatSimulator simulator) {
        return StatefulWeaponRegressionSupport.calculate(
                owner, action, simulator);
    }

    private static StatsContainer stats(
            StatefulWeaponRegressionSupport.TestCharacter owner,
            CombatSimulator simulator) {
        return StatefulWeaponRegressionSupport.stats(owner, simulator);
    }

    private static void assertClose(
            double expected,
            double actual,
            String message) {
        StatefulWeaponRegressionSupport.assertClose(expected, actual, message);
    }

    private static void assertEquals(
            Object expected,
            Object actual,
            String message) {
        StatefulWeaponRegressionSupport.assertEquals(expected, actual, message);
    }

    private static void assertTrue(boolean condition, String message) {
        StatefulWeaponRegressionSupport.assertTrue(condition, message);
    }

    private static void assertThrows(
            Class<? extends Throwable> expected,
            Runnable action,
            String message) {
        StatefulWeaponRegressionSupport.assertThrows(expected, action, message);
    }
}

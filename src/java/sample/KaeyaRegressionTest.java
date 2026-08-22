package sample;

import java.util.ArrayList;
import java.util.List;

import model.character.Kaeya;
import model.entity.Enemy;
import model.type.CharacterId;
import simulation.CombatSimulator;
import simulation.action.AttackAction;
import simulation.action.CharacterActionKey;
import simulation.action.CharacterActionRequest;
import simulation.action.HitlagProfile;

/** Focused regression checks for Kaeya's source-backed Normal hitlag. */
public final class KaeyaRegressionTest {
    private static final double FRAME = 1.0 / 60.0;
    private static final double EPSILON = 1e-8;

    private KaeyaRegressionTest() {
    }

    /** Runs Kaeya's Normal timing, metadata, and combo-wrap checks. */
    public static void main(String[] args) {
        Kaeya kaeya = new Kaeya(null, null);
        CombatSimulator simulator = new CombatSimulator();
        simulator.setLoggingEnabled(false);
        simulator.addCharacter(kaeya);
        simulator.setEnemy(new Enemy(90));
        List<AttackAction> normals = new ArrayList<>();
        simulator.addDamageListener((actor, action, damage, time) -> {
            if (actor.getCharacterId() == CharacterId.KAEYA
                    && action.getName().startsWith("Kaeya N")) {
                normals.add(action);
            }
        });

        int[] durationFrames = { 27, 27, 47, 46, 74 };
        int[] hitlagFrames = { 6, 6, 8, 8, 10 };
        double[] haltTimes = { 0.03, 0.03, 0.06, 0.06, 0.10 };
        for (int step = 0; step < durationFrames.length; step++) {
            double castTime = simulator.getCurrentTime();
            performNormal(simulator);
            assertClose(castTime
                            + (durationFrames[step] + hitlagFrames[step])
                                    * FRAME,
                    simulator.getCurrentTime(),
                    "Kaeya N" + (step + 1) + " recovery");
            assertHitlagProfile(
                    normals.get(step).getHitlagProfile(),
                    haltTimes[step],
                    "Kaeya N" + (step + 1));
        }

        performNormal(simulator);
        assertEquals("Kaeya N1", normals.get(5).getName(),
                "Kaeya Normal string wraps after N5");
        System.out.println("KaeyaRegressionTest passed");
    }

    private static void performNormal(CombatSimulator simulator) {
        simulator.performAction(
                CharacterId.KAEYA,
                CharacterActionRequest.of(CharacterActionKey.NORMAL));
    }

    private static void assertHitlagProfile(
            HitlagProfile profile,
            double haltTime,
            String message) {
        assertClose(haltTime, profile.getHaltTimeSeconds(),
                message + " hitlag halt time");
        assertClose(0.01, profile.getFactor(),
                message + " hitlag factor");
        assertTrue(profile.canDefenseHalt(),
                message + " enables Defense Halt");
        assertTrue(!profile.isDeployable(),
                message + " is owner-bound");
        assertTrue(!profile.isHeadshotOnly(),
                message + " is not headshot-only");
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

    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}

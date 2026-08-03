package sample;

import simulation.action.CharacterActionKey;
import simulation.action.CharacterActionRequest;
import simulation.action.SkillActionMode;

/** Regression checks for typed Press and Hold Skill action requests. */
public final class CharacterActionRequestRegressionTest {
    private CharacterActionRequestRegressionTest() {
    }

    /** Runs compatibility, explicit-mode, label, and null-input checks. */
    public static void main(String[] args) {
        CharacterActionRequest legacySkill = CharacterActionRequest.of(
                CharacterActionKey.SKILL);
        assertEquals(CharacterActionKey.SKILL, legacySkill.getKey(),
                "legacy Skill key");
        assertEquals(SkillActionMode.PRESS, legacySkill.getSkillMode(),
                "legacy Skill defaults to Press");
        assertEquals("skill", legacySkill.getLogLabel(),
                "legacy Skill label");

        CharacterActionRequest heldSkill = CharacterActionRequest.skill(
                SkillActionMode.HOLD);
        assertEquals(CharacterActionKey.SKILL, heldSkill.getKey(),
                "Hold Skill key");
        assertEquals(SkillActionMode.HOLD, heldSkill.getSkillMode(),
                "Hold Skill mode");
        assertEquals("skill hold", heldSkill.getLogLabel(),
                "Hold Skill label");

        CharacterActionRequest normal = CharacterActionRequest.of(
                CharacterActionKey.NORMAL);
        assertEquals(CharacterActionKey.NORMAL, normal.getKey(),
                "Normal key remains unchanged");
        assertEquals("attack", normal.getLogLabel(),
                "Normal label remains unchanged");
        assertEquals(SkillActionMode.PRESS, normal.getSkillMode(),
                "non-Skill request uses inert Press default");

        CharacterActionRequest burst = CharacterActionRequest.of(
                CharacterActionKey.BURST);
        assertEquals("burst", burst.getLogLabel(),
                "Burst label remains unchanged");

        assertThrows(NullPointerException.class,
                () -> CharacterActionRequest.of(null),
                "null action key");
        assertThrows(NullPointerException.class,
                () -> CharacterActionRequest.skill(null),
                "null Skill mode");
        System.out.println("CharacterActionRequestRegressionTest passed");
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
            Runnable action,
            String message) {
        try {
            action.run();
        } catch (Throwable throwable) {
            if (expected.isInstance(throwable)) {
                return;
            }
            throw new AssertionError(message + ": unexpected " + throwable,
                    throwable);
        }
        throw new AssertionError(message + ": expected "
                + expected.getSimpleName());
    }
}

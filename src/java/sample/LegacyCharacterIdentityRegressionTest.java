package sample;

import java.util.EnumSet;

import mechanics.buff.BuffId;
import model.type.CharacterId;

/** Regression checks for the B-161 character and buff identity baseline. */
public final class LegacyCharacterIdentityRegressionTest {
    private LegacyCharacterIdentityRegressionTest() {
    }

    /** Runs character round-trip, prior-ID, fallback, and buff checks. */
    public static void main(String[] args) {
        assertIdentity(CharacterId.VENTI, 18, "Venti");
        assertIdentity(CharacterId.YOIMIYA, 19, "Yoimiya");
        assertIdentity(CharacterId.YANFEI, 20, "Yanfei");
        assertEquals(CharacterId.ALBEDO, CharacterId.fromNumericId(17),
                "prior Albedo numeric ID");
        assertEquals(CharacterId.UNKNOWN, CharacterId.fromNumericId(21),
                "unknown numeric fallback");
        assertEquals(CharacterId.UNKNOWN, CharacterId.fromName(null),
                "null name fallback");

        EnumSet<BuffId> reserved = EnumSet.of(
                BuffId.VENTI_C2_RES_SHRED,
                BuffId.VENTI_C6_RES_SHRED,
                BuffId.YOIMIYA_A1_PYRO_DMG_BONUS,
                BuffId.YOIMIYA_A4_TEAM_ATK,
                BuffId.YANFEI_A1_PYRO_DMG_BONUS,
                BuffId.YANFEI_BRILLIANCE_CHARGED_DMG_BONUS);
        assertEquals(6, reserved.size(), "reserved typed buff identities");
        System.out.println("LegacyCharacterIdentityRegressionTest passed");
    }

    private static void assertIdentity(
            CharacterId expected,
            int numericId,
            String displayName) {
        assertEquals(expected, CharacterId.fromNumericId(numericId),
                displayName + " numeric lookup");
        assertEquals(expected, CharacterId.fromName(displayName),
                displayName + " name lookup");
        assertEquals(numericId, expected.getNumericId(),
                displayName + " stable numeric ID");
        assertEquals(displayName, expected.getDisplayName(),
                displayName + " display name");
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
}

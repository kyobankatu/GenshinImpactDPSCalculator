package sample;

import java.util.EnumSet;

import mechanics.buff.BuffId;
import model.type.CharacterId;
import model.type.CharacterRegion;

/** Regression checks for the legacy character and buff identity baseline. */
public final class LegacyCharacterIdentityRegressionTest {
    private LegacyCharacterIdentityRegressionTest() {
    }

    /** Runs character round-trip, prior-ID, fallback, and buff checks. */
    public static void main(String[] args) {
        assertIdentity(CharacterId.VENTI, 18, "Venti");
        assertIdentity(CharacterId.YOIMIYA, 19, "Yoimiya");
        assertIdentity(CharacterId.YANFEI, 20, "Yanfei");
        assertIdentity(CharacterId.ROSARIA, 21, "Rosaria");
        assertIdentity(CharacterId.DILUC, 22, "Diluc");
        assertIdentity(CharacterId.KEQING, 23, "Keqing");
        assertIdentity(CharacterId.NINGGUANG, 24, "Ningguang");
        assertIdentity(CharacterId.GANYU, 25, "Ganyu");
        assertIdentity(CharacterId.JEAN, 26, "Jean");
        assertIdentity(CharacterId.CHONGYUN, 27, "Chongyun");
        assertIdentity(CharacterId.DIONA, 28, "Diona");
        assertEquals(CharacterId.BENNETT, CharacterId.fromNumericId(1),
                "first prior numeric ID");
        assertEquals(CharacterId.YANFEI, CharacterId.fromNumericId(20),
                "last prior numeric ID");
        assertEquals(CharacterId.UNKNOWN, CharacterId.fromNumericId(-1),
                "negative numeric fallback");
        assertEquals(CharacterId.UNKNOWN, CharacterId.fromNumericId(0),
                "zero numeric fallback");
        assertEquals(CharacterId.UNKNOWN, CharacterId.fromNumericId(29),
                "high numeric fallback");
        assertEquals(CharacterId.UNKNOWN, CharacterId.fromName(null),
                "null name fallback");
        assertEquals(CharacterId.UNKNOWN, CharacterId.fromName("rosaria"),
                "case-sensitive name fallback");
        assertEquals(CharacterId.UNKNOWN, CharacterId.fromName("Not A Character"),
                "unmatched name fallback");
        assertEquals(CharacterRegion.LIYUE, CharacterId.GANYU.getRegion(),
                "Liyue region lookup");
        assertEquals(CharacterRegion.LIYUE, CharacterId.CHONGYUN.getRegion(),
                "Chongyun Liyue region lookup");
        assertEquals(CharacterRegion.INAZUMA,
                CharacterId.RAIDEN_SHOGUN.getRegion(),
                "Inazuma region lookup");
        assertEquals(CharacterRegion.MONDSTADT,
                CharacterId.DILUC.getRegion(),
                "Mondstadt region lookup");
        assertEquals(CharacterRegion.MONDSTADT, CharacterId.JEAN.getRegion(),
                "Jean Mondstadt region lookup");
        assertEquals(CharacterRegion.MONDSTADT, CharacterId.DIONA.getRegion(),
                "Diona Mondstadt region lookup");
        assertEquals(CharacterRegion.UNKNOWN,
                CharacterId.COLUMBINA.getRegion(),
                "unverified custom region fails closed");
        assertEquals(CharacterRegion.UNKNOWN,
                CharacterId.UNKNOWN.getRegion(),
                "unknown identity region fails closed");

        EnumSet<BuffId> reserved = EnumSet.of(
                BuffId.VENTI_C2_RES_SHRED,
                BuffId.VENTI_C6_RES_SHRED,
                BuffId.YOIMIYA_A1_PYRO_DMG_BONUS,
                BuffId.YOIMIYA_A4_TEAM_ATK,
                BuffId.YANFEI_A1_PYRO_DMG_BONUS,
                BuffId.YANFEI_BRILLIANCE_CHARGED_DMG_BONUS,
                BuffId.ROSARIA_A4_TEAM_CRIT_RATE,
                BuffId.ROSARIA_C6_PHYSICAL_RES_SHRED,
                BuffId.DILUC_A4_PYRO_DMG_BONUS,
                BuffId.DILUC_C4_SKILL_DMG_BONUS,
                BuffId.KEQING_A4_CRIT_RATE_AND_ER,
                BuffId.KEQING_C4_ATK_BONUS,
                BuffId.KEQING_C6_ELECTRO_DMG_BONUS,
                BuffId.GANYU_A1_FROSTFLAKE_CRIT_RATE,
                BuffId.GANYU_A4_CRYO_DMG_BONUS,
                BuffId.GANYU_C1_CRYO_RES_SHRED,
                BuffId.GANYU_C4_CELESTIAL_SHOWER_DMG_BONUS);
        assertEquals(17, reserved.size(), "reserved typed buff identities");
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

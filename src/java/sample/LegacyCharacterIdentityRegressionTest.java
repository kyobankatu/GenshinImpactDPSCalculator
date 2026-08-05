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
        assertIdentity(CharacterId.QIQI, 29, "Qiqi");
        assertIdentity(CharacterId.MONA, 30, "Mona");
        assertIdentity(CharacterId.BEIDOU, 31, "Beidou");
        assertIdentity(CharacterId.COLLEI, 32, "Collei");
        assertIdentity(CharacterId.KLEE, 33, "Klee");
        assertIdentity(CharacterId.EULA, 34, "Eula");
        assertIdentity(CharacterId.GOROU, 35, "Gorou");
        assertIdentity(CharacterId.YELAN, 36, "Yelan");
        assertIdentity(CharacterId.SAYU, 37, "Sayu");
        assertIdentity(CharacterId.KUJOU_SARA, 38, "Kujou Sara");
        assertIdentity(CharacterId.YUN_JIN, 39, "Yun Jin");
        assertIdentity(CharacterId.FARUZAN, 40, "Faruzan");
        assertIdentity(CharacterId.SHENHE, 41, "Shenhe");
        assertIdentity(CharacterId.TIGHNARI, 42, "Tighnari");
        assertIdentity(CharacterId.KAEDEHARA_KAZUHA, 43,
                "Kaedehara Kazuha");
        assertIdentity(CharacterId.ALOY, 44, "Aloy");
        assertIdentity(CharacterId.KUKI_SHINOBU, 45, "Kuki Shinobu");
        assertIdentity(CharacterId.CYNO, 46, "Cyno");
        assertIdentity(CharacterId.ALHAITHAM, 47, "Alhaitham");
        assertIdentity(CharacterId.KAMISATO_AYATO, 48, "Kamisato Ayato");
        assertIdentity(CharacterId.SHIKANOIN_HEIZOU, 49,
                "Shikanoin Heizou");
        assertIdentity(CharacterId.FREMINET, 50, "Freminet");
        assertIdentity(CharacterId.CANDACE, 51, "Candace");
        assertIdentity(CharacterId.LYNETTE, 52, "Lynette");
        assertIdentity(CharacterId.MIKA, 53, "Mika");
        assertIdentity(CharacterId.CHARLOTTE, 54, "Charlotte");
        assertIdentity(CharacterId.DORI, 55, "Dori");
        assertIdentity(CharacterId.KAVEH, 56, "Kaveh");
        assertIdentity(CharacterId.CHEVREUSE, 57, "Chevreuse");
        assertIdentity(CharacterId.THOMA, 58, "Thoma");
        assertIdentity(CharacterId.YAOYAO, 59, "Yaoyao");
        assertIdentity(CharacterId.XIAO, 60, "Xiao");
        assertIdentity(CharacterId.GAMING, 61, "Gaming");
        assertIdentity(CharacterId.XINYAN, 62, "Xinyan");
        assertEquals(CharacterId.BENNETT, CharacterId.fromNumericId(1),
                "first prior numeric ID");
        assertEquals(CharacterId.YANFEI, CharacterId.fromNumericId(20),
                "last prior numeric ID");
        assertEquals(CharacterId.UNKNOWN, CharacterId.fromNumericId(-1),
                "negative numeric fallback");
        assertEquals(CharacterId.UNKNOWN, CharacterId.fromNumericId(0),
                "zero numeric fallback");
        assertEquals(CharacterId.UNKNOWN,
                CharacterId.fromNumericId(Integer.MAX_VALUE),
                "high numeric fallback");
        assertEquals(CharacterId.UNKNOWN, CharacterId.fromName(null),
                "null name fallback");
        assertEquals(CharacterId.UNKNOWN, CharacterId.fromName("rosaria"),
                "case-sensitive name fallback");
        assertEquals(CharacterId.UNKNOWN, CharacterId.fromName("mona"),
                "new identity case-sensitive fallback");
        assertEquals(CharacterId.UNKNOWN, CharacterId.fromName("collei"),
                "latest identity case-sensitive fallback");
        assertEquals(CharacterId.UNKNOWN, CharacterId.fromName("klee"),
                "Klee identity case-sensitive fallback");
        assertEquals(CharacterId.UNKNOWN, CharacterId.fromName("eula"),
                "Eula identity case-sensitive fallback");
        assertEquals(CharacterId.UNKNOWN, CharacterId.fromName("gorou"),
                "Gorou identity case-sensitive fallback");
        assertEquals(CharacterId.UNKNOWN, CharacterId.fromName("yelan"),
                "Yelan identity case-sensitive fallback");
        assertEquals(CharacterId.UNKNOWN, CharacterId.fromName("sayu"),
                "Sayu identity case-sensitive fallback");
        assertEquals(CharacterId.UNKNOWN, CharacterId.fromName("kujou sara"),
                "Kujou Sara identity case-sensitive fallback");
        assertEquals(CharacterId.UNKNOWN, CharacterId.fromName("yun jin"),
                "Yun Jin identity case-sensitive fallback");
        assertEquals(CharacterId.UNKNOWN, CharacterId.fromName("faruzan"),
                "Faruzan identity case-sensitive fallback");
        assertEquals(CharacterId.UNKNOWN, CharacterId.fromName("shenhe"),
                "Shenhe identity case-sensitive fallback");
        assertEquals(CharacterId.UNKNOWN, CharacterId.fromName("tighnari"),
                "Tighnari identity case-sensitive fallback");
        assertEquals(CharacterId.UNKNOWN,
                CharacterId.fromName("kaedehara kazuha"),
                "Kazuha identity case-sensitive fallback");
        assertEquals(CharacterId.UNKNOWN, CharacterId.fromName("aloy"),
                "Aloy identity case-sensitive fallback");
        assertEquals(CharacterId.UNKNOWN, CharacterId.fromName("kuki shinobu"),
                "Kuki Shinobu identity case-sensitive fallback");
        assertEquals(CharacterId.UNKNOWN, CharacterId.fromName("cyno"),
                "Cyno identity case-sensitive fallback");
        assertEquals(CharacterId.UNKNOWN, CharacterId.fromName("alhaitham"),
                "Alhaitham identity case-sensitive fallback");
        assertEquals(CharacterId.UNKNOWN,
                CharacterId.fromName("kamisato ayato"),
                "Ayato identity case-sensitive fallback");
        assertEquals(CharacterId.UNKNOWN,
                CharacterId.fromName("shikanoin heizou"),
                "Heizou identity case-sensitive fallback");
        assertEquals(CharacterId.UNKNOWN, CharacterId.fromName("freminet"),
                "Freminet identity case-sensitive fallback");
        assertEquals(CharacterId.UNKNOWN, CharacterId.fromName("candace"),
                "Candace identity case-sensitive fallback");
        assertEquals(CharacterId.UNKNOWN, CharacterId.fromName("lynette"),
                "Lynette identity case-sensitive fallback");
        assertEquals(CharacterId.UNKNOWN, CharacterId.fromName("dori"),
                "Dori identity case-sensitive fallback");
        assertEquals(CharacterId.UNKNOWN, CharacterId.fromName("kaveh"),
                "Kaveh identity case-sensitive fallback");
        assertEquals(CharacterId.UNKNOWN, CharacterId.fromName("chevreuse"),
                "Chevreuse identity case-sensitive fallback");
        assertEquals(CharacterId.UNKNOWN, CharacterId.fromName("thoma"),
                "Thoma identity case-sensitive fallback");
        assertEquals(CharacterId.UNKNOWN, CharacterId.fromName("yaoyao"),
                "Yaoyao identity case-sensitive fallback");
        assertEquals(CharacterId.UNKNOWN, CharacterId.fromName("xiao"),
                "Xiao identity case-sensitive fallback");
        assertEquals(CharacterId.UNKNOWN, CharacterId.fromName("gaming"),
                "Gaming identity case-sensitive fallback");
        assertEquals(CharacterId.UNKNOWN, CharacterId.fromName("xinyan"),
                "Xinyan identity case-sensitive fallback");
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
        assertEquals(CharacterRegion.MONDSTADT, CharacterId.MONA.getRegion(),
                "Mona Mondstadt region lookup");
        assertEquals(CharacterRegion.LIYUE, CharacterId.BEIDOU.getRegion(),
                "Beidou Liyue region lookup");
        assertEquals(CharacterRegion.SUMERU, CharacterId.COLLEI.getRegion(),
                "Collei Sumeru region lookup");
        assertEquals(CharacterRegion.MONDSTADT, CharacterId.KLEE.getRegion(),
                "Klee Mondstadt region lookup");
        assertEquals(CharacterRegion.MONDSTADT, CharacterId.EULA.getRegion(),
                "Eula Mondstadt region lookup");
        assertEquals(CharacterRegion.INAZUMA, CharacterId.GOROU.getRegion(),
                "Gorou Inazuma region lookup");
        assertEquals(CharacterRegion.LIYUE, CharacterId.YELAN.getRegion(),
                "Yelan Liyue region lookup");
        assertEquals(CharacterRegion.INAZUMA, CharacterId.SAYU.getRegion(),
                "Sayu Inazuma region lookup");
        assertEquals(CharacterRegion.INAZUMA,
                CharacterId.KUJOU_SARA.getRegion(),
                "Kujou Sara Inazuma region lookup");
        assertEquals(CharacterRegion.LIYUE, CharacterId.YUN_JIN.getRegion(),
                "Yun Jin Liyue region lookup");
        assertEquals(CharacterRegion.SUMERU, CharacterId.FARUZAN.getRegion(),
                "Faruzan Sumeru region lookup");
        assertEquals(CharacterRegion.LIYUE, CharacterId.SHENHE.getRegion(),
                "Shenhe Liyue region lookup");
        assertEquals(CharacterRegion.SUMERU,
                CharacterId.TIGHNARI.getRegion(),
                "Tighnari Sumeru region lookup");
        assertEquals(CharacterRegion.INAZUMA,
                CharacterId.KAEDEHARA_KAZUHA.getRegion(),
                "Kazuha Inazuma region lookup");
        assertEquals(CharacterRegion.UNKNOWN, CharacterId.ALOY.getRegion(),
                "Aloy crossover region fails closed");
        assertEquals(CharacterRegion.INAZUMA,
                CharacterId.KUKI_SHINOBU.getRegion(),
                "Kuki Shinobu Inazuma region lookup");
        assertEquals(CharacterRegion.SUMERU, CharacterId.CYNO.getRegion(),
                "Cyno Sumeru region lookup");
        assertEquals(CharacterRegion.SUMERU,
                CharacterId.ALHAITHAM.getRegion(),
                "Alhaitham Sumeru region lookup");
        assertEquals(CharacterRegion.INAZUMA,
                CharacterId.KAMISATO_AYATO.getRegion(),
                "Ayato Inazuma region lookup");
        assertEquals(CharacterRegion.INAZUMA,
                CharacterId.SHIKANOIN_HEIZOU.getRegion(),
                "Heizou Inazuma region lookup");
        assertEquals(CharacterRegion.FONTAINE, CharacterId.FREMINET.getRegion(),
                "Freminet Fontaine region lookup");
        assertEquals(CharacterRegion.SUMERU, CharacterId.CANDACE.getRegion(),
                "Candace Sumeru region lookup");
        assertEquals(CharacterRegion.FONTAINE, CharacterId.LYNETTE.getRegion(),
                "Lynette Fontaine region lookup");
        assertEquals(CharacterRegion.SUMERU, CharacterId.DORI.getRegion(),
                "Dori Sumeru region lookup");
        assertEquals(CharacterRegion.SUMERU, CharacterId.KAVEH.getRegion(),
                "Kaveh Sumeru region lookup");
        assertEquals(CharacterRegion.FONTAINE,
                CharacterId.CHEVREUSE.getRegion(),
                "Chevreuse Fontaine region lookup");
        assertEquals(CharacterRegion.INAZUMA, CharacterId.THOMA.getRegion(),
                "Thoma Inazuma region lookup");
        assertEquals(CharacterRegion.LIYUE, CharacterId.YAOYAO.getRegion(),
                "Yaoyao Liyue region lookup");
        assertEquals(CharacterRegion.LIYUE, CharacterId.XIAO.getRegion(),
                "Xiao Liyue region lookup");
        assertEquals(CharacterRegion.LIYUE, CharacterId.GAMING.getRegion(),
                "Gaming Liyue region lookup");
        assertEquals(CharacterRegion.LIYUE, CharacterId.XINYAN.getRegion(),
                "Xinyan Liyue region lookup");
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
                BuffId.GANYU_C4_CELESTIAL_SHOWER_DMG_BONUS,
                BuffId.GOROU_GENERAL_WAR_BANNER,
                BuffId.GOROU_A1_DEF_BONUS,
                BuffId.GOROU_C6_GEO_CRIT_DMG,
                BuffId.YELAN_ADAPT_WITH_EASE,
                BuffId.YELAN_C4_MAX_HP,
                BuffId.KUJOU_SARA_CROWFEATHER_COVER,
                BuffId.KUJOU_SARA_TENGU_JUURAI,
                BuffId.YUN_JIN_FLYING_CLOUD_FORMATION,
                BuffId.YUN_JIN_C2_NORMAL_DMG,
                BuffId.YUN_JIN_C4_DEF,
                BuffId.YUN_JIN_C6_NORMAL_SPEED,
                BuffId.FARUZAN_PRAYERFUL_WIND,
                BuffId.FARUZAN_PERFIDIOUS_WIND,
                BuffId.SHENHE_A4_SKILL_BURST_DMG,
                BuffId.SHENHE_A4_NORMAL_CHARGED_PLUNGE_DMG,
                BuffId.SHENHE_BURST_RES_SHRED,
                BuffId.SHENHE_BURST_ACTIVE_BONUS,
                BuffId.TIGHNARI_A1_ELEMENTAL_MASTERY,
                BuffId.TIGHNARI_C2_DENDRO_DMG_BONUS,
                BuffId.TIGHNARI_C4_PARTY_ELEMENTAL_MASTERY,
                BuffId.ALHAITHAM_C4_PARTY_ELEMENTAL_MASTERY,
                BuffId.ALHAITHAM_C4_DENDRO_DMG_BONUS,
                BuffId.AYATO_BURST_NORMAL_DMG,
                BuffId.AYATO_C4_NORMAL_SPEED,
                BuffId.KAZUHA_A4_PYRO_DMG_BONUS,
                BuffId.KAZUHA_A4_HYDRO_DMG_BONUS,
                BuffId.KAZUHA_A4_ELECTRO_DMG_BONUS,
                BuffId.KAZUHA_A4_CRYO_DMG_BONUS,
                BuffId.KAZUHA_C2_OWNER_ELEMENTAL_MASTERY,
                BuffId.KAZUHA_C2_ACTIVE_ELEMENTAL_MASTERY,
                BuffId.KAZUHA_C6_INFUSION,
                BuffId.ALOY_A1_OWNER_ATK,
                BuffId.ALOY_A1_TEAM_ATK,
                BuffId.ALOY_A4_CRYO_DMG_BONUS,
                BuffId.CANDACE_C2_MAX_HP,
                BuffId.CANDACE_CRIMSON_CROWN_NORMAL_DMG,
                BuffId.LYNETTE_A1_PARTY_ATK,
                BuffId.LYNETTE_C6_ANEMO_DMG,
                BuffId.MIKA_SOULWIND_ATTACK_SPEED,
                BuffId.CHARLOTTE_C2_ATK,
                BuffId.CHEVREUSE_A1_COORDINATED_TACTICS,
                BuffId.CHEVREUSE_A4_VERTICAL_FORCE_COORDINATION,
                BuffId.XINYAN_C4_PHYSICAL_RES_SHRED);
        assertEquals(60, reserved.size(), "reserved typed buff identities");
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

package model.type;

/**
 * Identifier for each playable character supported by the simulator.
 *
 * <p>Each constant carries a human-readable display name, used both for
 * console/HTML report output and for matching against the {@code Character}
 * column of the per-character CSV configuration files in
 * {@code config/characters/}.
 *
 * <p>{@link #UNKNOWN} acts as a safe fallback returned by
 * {@link #fromName(String)} when the supplied display name does not match any
 * registered character, avoiding {@code null} return values at call sites.
 */
public enum CharacterId {
    /** Bennett (Pyro sword support). */
    BENNETT(1, "Bennett", CharacterRegion.MONDSTADT),
    /** Columbina (custom Lunar character; carries {@code LUNAR_MULTIPLIER}). */
    COLUMBINA(2, "Columbina", CharacterRegion.UNKNOWN),
    /** Flins (custom Lunar character). */
    FLINS(3, "Flins", CharacterRegion.UNKNOWN),
    /** Ineffa (custom Lunar character; carries {@code LUNAR_BASE_BONUS}). */
    INEFFA(4, "Ineffa", CharacterRegion.UNKNOWN),
    /** Raiden Shogun (Electro polearm DPS / battery). */
    RAIDEN_SHOGUN(5, "Raiden Shogun", CharacterRegion.INAZUMA),
    /** Sucrose (Anemo catalyst Moonsign / Ascendant Blessing source). */
    SUCROSE(6, "Sucrose", CharacterRegion.MONDSTADT),
    /** Xiangling (Pyro polearm sub-DPS). */
    XIANGLING(7, "Xiangling", CharacterRegion.LIYUE),
    /** Xingqiu (Hydro sword off-field reaction enabler). */
    XINGQIU(8, "Xingqiu", CharacterRegion.LIYUE),
    /** Kaeya (Cryo sword DPS and off-field Burst source). */
    KAEYA(9, "Kaeya", CharacterRegion.MONDSTADT),
    /** Amber (Pyro bow DPS with delayed Skill and fixed-area Burst). */
    AMBER(10, "Amber", CharacterRegion.MONDSTADT),
    /** Lisa (Electro catalyst DPS with Conductive stacks and a periodic Burst). */
    LISA(11, "Lisa", CharacterRegion.MONDSTADT),
    /** Barbara (Hydro catalyst healer and driver). */
    BARBARA(12, "Barbara", CharacterRegion.MONDSTADT),
    /** Noelle (Geo claymore on-field DPS). */
    NOELLE(13, "Noelle", CharacterRegion.MONDSTADT),
    /** Razor (Electro claymore on-field DPS). */
    RAZOR(14, "Razor", CharacterRegion.MONDSTADT),
    /** Fischl (Electro bow off-field DPS and battery). */
    FISCHL(15, "Fischl", CharacterRegion.MONDSTADT),
    /** Yae Miko (Electro catalyst off-field Skill DPS). */
    YAE_MIKO(16, "Yae Miko", CharacterRegion.INAZUMA),
    /** Albedo (Geo sword off-field Skill DPS). */
    ALBEDO(17, "Albedo", CharacterRegion.MONDSTADT),
    /** Venti (Anemo bow Burst support). */
    VENTI(18, "Venti", CharacterRegion.MONDSTADT),
    /** Yoimiya (Pyro bow Normal Attack DPS). */
    YOIMIYA(19, "Yoimiya", CharacterRegion.INAZUMA),
    /** Yanfei (Pyro catalyst Charged Attack DPS). */
    YANFEI(20, "Yanfei", CharacterRegion.LIYUE),
    /** Rosaria (Cryo polearm DPS and team CRIT support). */
    ROSARIA(21, "Rosaria", CharacterRegion.MONDSTADT),
    /** Diluc (Pyro claymore on-field DPS). */
    DILUC(22, "Diluc", CharacterRegion.MONDSTADT),
    /** Keqing (Electro sword on-field DPS). */
    KEQING(23, "Keqing", CharacterRegion.LIYUE),
    /** Ningguang (Geo catalyst on-field DPS). */
    NINGGUANG(24, "Ningguang", CharacterRegion.LIYUE),
    /** Ganyu (Cryo bow Charged Attack DPS). */
    GANYU(25, "Ganyu", CharacterRegion.LIYUE),
    /** Fallback value returned by {@link #fromName(String)} for unmatched names. */
    UNKNOWN(0, "Unknown", CharacterRegion.UNKNOWN);

    private final int numericId;
    private final String displayName;
    private final CharacterRegion region;

    CharacterId(
            int numericId,
            String displayName,
            CharacterRegion region) {
        this.numericId = numericId;
        this.displayName = displayName;
        this.region = region;
    }

    /**
     * Returns the stable numeric identifier for serialization, arrays, and
     * non-display bookkeeping. Do not derive persisted values from enum ordinal.
     *
     * @return stable numeric id
     */
    public int getNumericId() {
        return numericId;
    }

    /**
     * Returns the human-readable display name associated with this identifier.
     * The same string is used as the {@code Character} column value in CSV
     * configuration files.
     *
     * @return display name (never {@code null})
     */
    public String getDisplayName() {
        return displayName;
    }

    /** Returns the typed region used by composition-based equipment. */
    public CharacterRegion getRegion() {
        return region;
    }

    /**
     * Resolves a display name back to its {@link CharacterId} constant.
     *
     * @param name human-readable display name as written in CSV files
     *             (case-sensitive); may be {@code null}
     * @return the matching {@link CharacterId}, or {@link #UNKNOWN} if
     *         {@code name} is {@code null} or no constant matches
     */
    public static CharacterId fromName(String name) {
        if (name == null) {
            return UNKNOWN;
        }
        for (CharacterId id : values()) {
            if (id.displayName.equals(name)) {
                return id;
            }
        }
        return UNKNOWN;
    }

    /**
     * Resolves a stable numeric id back to a {@link CharacterId}.
     *
     * @param numericId stable numeric id
     * @return matching id, or {@link #UNKNOWN} if no id matches
     */
    public static CharacterId fromNumericId(int numericId) {
        for (CharacterId id : values()) {
            if (id.numericId == numericId) {
                return id;
            }
        }
        return UNKNOWN;
    }
}

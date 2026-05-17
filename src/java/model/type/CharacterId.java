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
    BENNETT("Bennett"),
    /** Columbina (custom Lunar character; carries {@code LUNAR_MULTIPLIER}). */
    COLUMBINA("Columbina"),
    /** Flins (custom Lunar character). */
    FLINS("Flins"),
    /** Ineffa (custom Lunar character; carries {@code LUNAR_BASE_BONUS}). */
    INEFFA("Ineffa"),
    /** Raiden Shogun (Electro polearm DPS / battery). */
    RAIDEN_SHOGUN("Raiden Shogun"),
    /** Sucrose (Anemo catalyst Moonsign / Ascendant Blessing source). */
    SUCROSE("Sucrose"),
    /** Xiangling (Pyro polearm sub-DPS). */
    XIANGLING("Xiangling"),
    /** Xingqiu (Hydro sword off-field reaction enabler). */
    XINGQIU("Xingqiu"),
    /** Fallback value returned by {@link #fromName(String)} for unmatched names. */
    UNKNOWN("Unknown");

    private final String displayName;

    CharacterId(String displayName) {
        this.displayName = displayName;
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
}

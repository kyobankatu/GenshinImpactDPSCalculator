package model.type;

/**
 * Enumeration of the seven playable elements plus Physical damage in Genshin
 * Impact.
 *
 * <p>Each constant carries a reference to the corresponding
 * {@link StatType} DMG_BONUS key so that damage calculators and resistance
 * lookups can retrieve the correct stat without string-based dispatch.
 *
 * <p>The same {@link StatType} key is used both for the character's elemental
 * DMG Bonus% and for the enemy's resistance entry in
 * {@link model.entity.Enemy#getRes(StatType)}.
 */
public enum Element {
    /** Pyro element; maps to {@link StatType#PYRO_DMG_BONUS}. */
    PYRO(StatType.PYRO_DMG_BONUS),
    /** Hydro element; maps to {@link StatType#HYDRO_DMG_BONUS}. */
    HYDRO(StatType.HYDRO_DMG_BONUS),
    /** Anemo element; maps to {@link StatType#ANEMO_DMG_BONUS}. */
    ANEMO(StatType.ANEMO_DMG_BONUS),
    /** Electro element; maps to {@link StatType#ELECTRO_DMG_BONUS}. */
    ELECTRO(StatType.ELECTRO_DMG_BONUS),
    /** Dendro element; maps to {@link StatType#DENDRO_DMG_BONUS}. */
    DENDRO(StatType.DENDRO_DMG_BONUS),
    /** Cryo element; maps to {@link StatType#CRYO_DMG_BONUS}. */
    CRYO(StatType.CRYO_DMG_BONUS),
    /** Geo element; maps to {@link StatType#GEO_DMG_BONUS}. */
    GEO(StatType.GEO_DMG_BONUS),
    /** Physical damage type; maps to {@link StatType#PHYSICAL_DMG_BONUS}. */
    PHYSICAL(StatType.PHYSICAL_DMG_BONUS);

    private final StatType bonusStatType;

    Element(StatType bonusStatType) {
        this.bonusStatType = bonusStatType;
    }

    /**
     * Returns the {@link StatType} DMG_BONUS key associated with this element.
     * Used to look up both the attacker's elemental DMG Bonus% and the enemy's
     * corresponding resistance value.
     *
     * @return associated DMG Bonus stat type
     */
    public StatType getBonusStatType() {
        return bonusStatType;
    }
}

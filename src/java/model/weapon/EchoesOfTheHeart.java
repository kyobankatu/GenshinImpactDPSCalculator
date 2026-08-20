package model.weapon;

import model.type.StatType;
import model.type.WeaponType;

/** Echoes of the Heart with EM and Stellar damage windows. */
public final class EchoesOfTheHeart extends VersionSevenReactionWindowWeapon {
    /** Constructs Echoes of the Heart at refinement rank five. */
    public EchoesOfTheHeart() {
        this(5);
    }

    /**
     * Constructs Echoes of the Heart at the selected refinement rank.
     *
     * @param refinement refinement rank in the inclusive range 1-5
     */
    public EchoesOfTheHeart(int refinement) {
        super(
                "Echoes of the Heart",
                WeaponType.CATALYST,
                565.0,
                StatType.ATK_PERCENT,
                0.276,
                refinement,
                StatType.ELEMENTAL_MASTERY,
                45.0 + 15.0 * refinement,
                0.12 + 0.04 * refinement,
                0.0,
                0.0,
                StatType.STELLAR_CONDUCT_DMG_BONUS,
                StatType.STELLAR_SWIRL_DMG_BONUS);
    }
}

package model.weapon;

import model.type.StatType;
import model.type.WeaponType;

/** Emberwell with independent ordinary-reaction and Stellar damage windows. */
public final class Emberwell extends VersionSevenReactionWindowWeapon {
    /** Constructs Emberwell at refinement rank five. */
    public Emberwell() {
        this(5);
    }

    /**
     * Constructs Emberwell at the selected refinement rank.
     *
     * @param refinement refinement rank in the inclusive range 1-5
     */
    public Emberwell(int refinement) {
        super(
                "Emberwell",
                WeaponType.SWORD,
                510.0,
                StatType.ELEMENTAL_MASTERY,
                165.0,
                refinement,
                StatType.ATK_PERCENT,
                0.12 + 0.04 * refinement,
                0.12 + 0.04 * refinement,
                0.0,
                0.0,
                StatType.STELLAR_CONDUCT_DMG_BONUS,
                StatType.STELLAR_SWIRL_DMG_BONUS);
    }
}

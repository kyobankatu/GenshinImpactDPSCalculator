package model.weapon;

import model.type.StatType;
import model.type.WeaponType;

/** Blade of Atonement with EM and Stellar-triggered ATK windows. */
public final class BladeOfAtonement extends VersionSevenReactionWindowWeapon {
    /** Constructs Blade of Atonement at refinement rank five. */
    public BladeOfAtonement() {
        this(5);
    }

    /**
     * Constructs Blade of Atonement at the selected refinement rank.
     *
     * @param refinement refinement rank in the inclusive range 1-5
     */
    public BladeOfAtonement(int refinement) {
        super(
                "Blade of Atonement",
                WeaponType.CLAYMORE,
                565.0,
                StatType.ATK_PERCENT,
                0.276,
                refinement,
                StatType.ELEMENTAL_MASTERY,
                48.0 + 16.0 * refinement,
                0.12 + 0.04 * refinement,
                0.0,
                0.0,
                StatType.ATK_PERCENT);
    }
}

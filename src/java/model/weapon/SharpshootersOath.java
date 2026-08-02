package model.weapon;

import model.type.StatType;
import model.type.WeaponType;

/**
 * Sharpshooter's Oath with Lv. 90 metadata and inactive Precise.
 *
 * <p>Precise increases damage against weak points by
 * {@code 24/30/36/42/48%} at R1-R5. Attack and enemy models expose no
 * weak-point metadata, so the generic damage path must remain unchanged.</p>
 */
public class SharpshootersOath extends BoundaryInactiveWeapon {
    /** Constructs an R5 Sharpshooter's Oath. */
    public SharpshootersOath() {
        this(5);
    }

    /**
     * Constructs Sharpshooter's Oath at a selected refinement.
     *
     * @param refinement refinement rank in the inclusive range 1-5
     */
    public SharpshootersOath(int refinement) {
        super("Sharpshooter's Oath", WeaponType.BOW, 401.0,
                StatType.CRIT_DMG, 0.469, refinement);
    }
}

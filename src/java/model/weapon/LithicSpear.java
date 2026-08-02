package model.weapon;

import model.type.WeaponType;

/** Lithic Spear with its Liyue party-composition passive. */
public final class LithicSpear extends LithicWeapon {
    /** Constructs Lithic Spear at refinement rank five. */
    public LithicSpear() {
        this(5);
    }

    /** Constructs Lithic Spear at the selected refinement rank. */
    public LithicSpear(int refinement) {
        super("Lithic Spear", WeaponType.POLEARM,
                565.0, 0.276, refinement);
    }
}

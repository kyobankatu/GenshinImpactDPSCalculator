package model.weapon;

import model.type.WeaponType;

/** Lithic Blade with its Liyue party-composition passive. */
public final class LithicBlade extends LithicWeapon {
    /** Constructs Lithic Blade at refinement rank five. */
    public LithicBlade() {
        this(5);
    }

    /** Constructs Lithic Blade at the selected refinement rank. */
    public LithicBlade(int refinement) {
        super("Lithic Blade", WeaponType.CLAYMORE,
                510.0, 0.413, refinement);
    }
}

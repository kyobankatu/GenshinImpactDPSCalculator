package model.weapon;

import model.type.WeaponType;

/** Memory of Dust catalyst with the shared Golden Majesty passive. */
public class MemoryOfDust extends GoldenMajestyWeapon {
    /** Constructs Memory of Dust at refinement rank five. */
    public MemoryOfDust() {
        this(5);
    }

    /** Constructs Memory of Dust at a selected refinement rank. */
    public MemoryOfDust(int refinement) {
        super("Memory of Dust", WeaponType.CATALYST, refinement);
    }
}

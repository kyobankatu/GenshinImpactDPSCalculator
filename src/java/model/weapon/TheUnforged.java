package model.weapon;

import model.type.WeaponType;

/** The Unforged claymore with the shared Golden Majesty passive. */
public class TheUnforged extends GoldenMajestyWeapon {
    /** Constructs The Unforged at refinement rank five. */
    public TheUnforged() {
        this(5);
    }

    /** Constructs The Unforged at a selected refinement rank. */
    public TheUnforged(int refinement) {
        super("The Unforged", WeaponType.CLAYMORE, refinement);
    }
}

package model.weapon;

import model.type.WeaponType;

/** Summit Shaper sword with the shared Golden Majesty passive. */
public class SummitShaper extends GoldenMajestyWeapon {
    /** Constructs Summit Shaper at refinement rank five. */
    public SummitShaper() {
        this(5);
    }

    /** Constructs Summit Shaper at a selected refinement rank. */
    public SummitShaper(int refinement) {
        super("Summit Shaper", WeaponType.SWORD, refinement);
    }
}

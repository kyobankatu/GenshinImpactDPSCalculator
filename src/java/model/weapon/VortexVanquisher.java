package model.weapon;

import model.type.WeaponType;

/** Vortex Vanquisher polearm with the shared Golden Majesty passive. */
public class VortexVanquisher extends GoldenMajestyWeapon {
    /** Constructs Vortex Vanquisher at refinement rank five. */
    public VortexVanquisher() {
        this(5);
    }

    /** Constructs Vortex Vanquisher at a selected refinement rank. */
    public VortexVanquisher(int refinement) {
        super("Vortex Vanquisher", WeaponType.POLEARM, refinement);
    }
}

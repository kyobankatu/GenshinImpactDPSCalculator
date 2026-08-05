package model.weapon;

import model.type.StatType;
import model.type.WeaponType;

/** Prototype Crescent with an explicit Charged weak-point boundary. */
public final class PrototypeCrescent extends BoundaryInactiveWeapon {
    /** Constructs the weapon at refinement rank five. */
    public PrototypeCrescent() {
        this(5);
    }

    /** Constructs the weapon at the selected refinement rank. */
    public PrototypeCrescent(int refinement) {
        super("Prototype Crescent", WeaponType.BOW, 510.0,
                StatType.ATK_PERCENT, 0.413, refinement);
    }
}

package model.weapon;

import model.type.StatType;
import model.type.WeaponType;

/** Portable Power Saw with an explicit healing-derived Symbol boundary. */
public final class PortablePowerSaw extends BoundaryInactiveWeapon {
    /** Constructs the weapon at refinement rank five. */
    public PortablePowerSaw() {
        this(5);
    }

    /** Constructs the weapon at the selected refinement rank. */
    public PortablePowerSaw(int refinement) {
        super("Portable Power Saw", WeaponType.CLAYMORE, 454.0,
                StatType.HP_PERCENT, 0.551, refinement);
    }
}

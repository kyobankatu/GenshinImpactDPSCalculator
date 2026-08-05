package model.weapon;

import model.type.StatType;
import model.type.WeaponType;

/** Sapwood Blade with an explicit world-pickup boundary. */
public final class SapwoodBlade extends BoundaryInactiveWeapon {
    /** Constructs the weapon at refinement rank five. */
    public SapwoodBlade() {
        this(5);
    }

    /** Constructs the weapon at the selected refinement rank. */
    public SapwoodBlade(int refinement) {
        super("Sapwood Blade", WeaponType.SWORD, 565.0,
                StatType.ENERGY_RECHARGE, 0.306, refinement);
    }
}

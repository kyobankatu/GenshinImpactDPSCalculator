package model.weapon;

import model.type.StatType;
import model.type.WeaponType;

/** Moonpiercer with an explicit world-pickup boundary. */
public final class Moonpiercer extends BoundaryInactiveWeapon {
    /** Constructs the weapon at refinement rank five. */
    public Moonpiercer() {
        this(5);
    }

    /** Constructs the weapon at the selected refinement rank. */
    public Moonpiercer(int refinement) {
        super("Moonpiercer", WeaponType.POLEARM, 565.0,
                StatType.ELEMENTAL_MASTERY, 110.0, refinement);
    }
}

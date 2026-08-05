package model.weapon;

import model.type.StatType;
import model.type.WeaponType;

/** Forest Regalia with an explicit world-pickup boundary. */
public final class ForestRegalia extends BoundaryInactiveWeapon {
    /** Constructs the weapon at refinement rank five. */
    public ForestRegalia() {
        this(5);
    }

    /** Constructs the weapon at the selected refinement rank. */
    public ForestRegalia(int refinement) {
        super("Forest Regalia", WeaponType.CLAYMORE, 565.0,
                StatType.ENERGY_RECHARGE, 0.306, refinement);
    }
}

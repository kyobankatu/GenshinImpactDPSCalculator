package model.weapon;

import model.type.StatType;
import model.type.WeaponType;

/** Tidal Shadow with an explicit post-healing ATK boundary. */
public final class TidalShadow extends BoundaryInactiveWeapon {
    /** Constructs the weapon at refinement rank five. */
    public TidalShadow() {
        this(5);
    }

    /** Constructs the weapon at the selected refinement rank. */
    public TidalShadow(int refinement) {
        super("Tidal Shadow", WeaponType.CLAYMORE, 510.0,
                StatType.ATK_PERCENT, 0.413, refinement);
    }
}

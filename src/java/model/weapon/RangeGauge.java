package model.weapon;

import model.type.StatType;
import model.type.WeaponType;

/** Range Gauge with an explicit healing-derived Symbol boundary. */
public final class RangeGauge extends BoundaryInactiveWeapon {
    /** Constructs the weapon at refinement rank five. */
    public RangeGauge() {
        this(5);
    }

    /** Constructs the weapon at the selected refinement rank. */
    public RangeGauge(int refinement) {
        super("Range Gauge", WeaponType.BOW, 565.0,
                StatType.ATK_PERCENT, 0.276, refinement);
    }
}

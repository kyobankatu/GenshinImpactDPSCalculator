package model.weapon;

import model.type.StatType;
import model.type.WeaponType;

/** Crimson Moon's Semblance with an explicit Bond-of-Life boundary. */
public final class CrimsonMoonsSemblance extends BoundaryInactiveWeapon {
    /** Constructs the weapon at refinement rank five. */
    public CrimsonMoonsSemblance() {
        this(5);
    }

    /** Constructs the weapon at the selected refinement rank. */
    public CrimsonMoonsSemblance(int refinement) {
        super("Crimson Moon's Semblance", WeaponType.POLEARM, 674.0,
                StatType.CRIT_RATE, 0.221, refinement);
    }

    /** Returns the first inactive Bond generic-damage tier. */
    public double getFirstBondDamageBonus() {
        return 0.08 + 0.04 * getRefinement();
    }

    /** Returns the second inactive 30%-Bond damage tier. */
    public double getThresholdBondDamageBonus() {
        return 0.16 + 0.08 * getRefinement();
    }

    /** Returns the unavailable Charged-hit Bond increase ratio. */
    public double getBondIncreaseRatio() {
        return 0.25;
    }
}

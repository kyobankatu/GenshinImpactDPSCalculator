package model.weapon;

import model.type.StatType;
import model.type.WeaponType;

/** Flower-Wreathed Feathers with explicit aim and exploration boundaries. */
public final class FlowerWreathedFeathers extends BoundaryInactiveWeapon {
    /** Constructs the weapon at refinement rank five. */
    public FlowerWreathedFeathers() {
        this(5);
    }

    /** Constructs the weapon at the selected refinement rank. */
    public FlowerWreathedFeathers(int refinement) {
        super("Flower-Wreathed Feathers", WeaponType.BOW, 510.0,
                StatType.ATK_PERCENT, 0.413, refinement);
    }

    /** Returns inactive aimed Charged DMG per half-second stack. */
    public double getChargedDamagePerStack() {
        return 0.045 + 0.015 * getRefinement();
    }

    /** Returns the unavailable exploration stamina reduction. */
    public double getGlidingStaminaReduction() {
        return 0.15;
    }
}

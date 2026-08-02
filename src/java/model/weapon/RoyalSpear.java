package model.weapon;

import model.type.WeaponType;

/** Royal Spear with Lv. 90 stats and the explicit inactive Focus boundary. */
public class RoyalSpear extends RoyalWeapon {
    /** Constructs an R5 Royal Spear. */
    public RoyalSpear() {
        this(5);
    }

    /**
     * Constructs Royal Spear at a selected refinement.
     *
     * @param refinement refinement rank in the inclusive range 1-5
     */
    public RoyalSpear(int refinement) {
        super("Royal Spear", WeaponType.POLEARM, 565.0, 0.276, refinement);
    }
}

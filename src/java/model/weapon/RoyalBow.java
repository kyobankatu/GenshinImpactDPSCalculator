package model.weapon;

import model.type.WeaponType;

/** Royal Bow with Lv. 90 stats and the explicit inactive Focus boundary. */
public class RoyalBow extends RoyalWeapon {
    /** Constructs an R5 Royal Bow. */
    public RoyalBow() {
        this(5);
    }

    /**
     * Constructs Royal Bow at a selected refinement.
     *
     * @param refinement refinement rank in the inclusive range 1-5
     */
    public RoyalBow(int refinement) {
        super("Royal Bow", WeaponType.BOW, 510.0, 0.413, refinement);
    }
}

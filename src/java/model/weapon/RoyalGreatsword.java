package model.weapon;

import model.type.WeaponType;

/** Royal Greatsword with Lv. 90 stats and the explicit inactive Focus boundary. */
public class RoyalGreatsword extends RoyalWeapon {
    /** Constructs an R5 Royal Greatsword. */
    public RoyalGreatsword() {
        this(5);
    }

    /**
     * Constructs Royal Greatsword at a selected refinement.
     *
     * @param refinement refinement rank in the inclusive range 1-5
     */
    public RoyalGreatsword(int refinement) {
        super("Royal Greatsword", WeaponType.CLAYMORE, 565.0, 0.276, refinement);
    }
}

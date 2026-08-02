package model.weapon;

import model.type.WeaponType;

/** Royal Longsword with Lv. 90 stats and the explicit inactive Focus boundary. */
public class RoyalLongsword extends RoyalWeapon {
    /** Constructs an R5 Royal Longsword. */
    public RoyalLongsword() {
        this(5);
    }

    /**
     * Constructs Royal Longsword at a selected refinement.
     *
     * @param refinement refinement rank in the inclusive range 1-5
     */
    public RoyalLongsword(int refinement) {
        super("Royal Longsword", WeaponType.SWORD, 510.0, 0.413, refinement);
    }
}

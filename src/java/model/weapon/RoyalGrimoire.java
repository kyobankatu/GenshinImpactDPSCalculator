package model.weapon;

import model.type.WeaponType;

/** Royal Grimoire with Lv. 90 stats and the explicit inactive Focus boundary. */
public class RoyalGrimoire extends RoyalWeapon {
    /** Constructs an R5 Royal Grimoire. */
    public RoyalGrimoire() {
        this(5);
    }

    /**
     * Constructs Royal Grimoire at a selected refinement.
     *
     * @param refinement refinement rank in the inclusive range 1-5
     */
    public RoyalGrimoire(int refinement) {
        super("Royal Grimoire", WeaponType.CATALYST, 565.0, 0.276, refinement);
    }
}

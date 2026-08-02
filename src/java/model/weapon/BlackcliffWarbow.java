package model.weapon;

import model.type.WeaponType;

/** Blackcliff Warbow with its Lv. 90 stats and modeled passive boundary. */
public class BlackcliffWarbow extends BlackcliffWeapon {
    /** Constructs an R5 Blackcliff Warbow. */
    public BlackcliffWarbow() {
        this(5);
    }

    /**
     * Constructs Blackcliff Warbow at a selected refinement.
     *
     * @param refinement refinement rank in the inclusive range 1-5
     */
    public BlackcliffWarbow(int refinement) {
        super("Blackcliff Warbow", WeaponType.BOW, 565.0, 0.368, refinement);
    }
}

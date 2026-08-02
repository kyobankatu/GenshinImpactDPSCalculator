package model.weapon;

import model.type.WeaponType;

/** Blackcliff Agate with its Lv. 90 stats and modeled passive boundary. */
public class BlackcliffAgate extends BlackcliffWeapon {
    /** Constructs an R5 Blackcliff Agate. */
    public BlackcliffAgate() {
        this(5);
    }

    /**
     * Constructs Blackcliff Agate at a selected refinement.
     *
     * @param refinement refinement rank in the inclusive range 1-5
     */
    public BlackcliffAgate(int refinement) {
        super("Blackcliff Agate", WeaponType.CATALYST, 510.0, 0.551, refinement);
    }
}

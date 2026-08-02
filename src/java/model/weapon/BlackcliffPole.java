package model.weapon;

import model.type.WeaponType;

/** Blackcliff Pole with its Lv. 90 stats and modeled passive boundary. */
public class BlackcliffPole extends BlackcliffWeapon {
    /** Constructs an R5 Blackcliff Pole. */
    public BlackcliffPole() {
        this(5);
    }

    /**
     * Constructs Blackcliff Pole at a selected refinement.
     *
     * @param refinement refinement rank in the inclusive range 1-5
     */
    public BlackcliffPole(int refinement) {
        super("Blackcliff Pole", WeaponType.POLEARM, 510.0, 0.551, refinement);
    }
}

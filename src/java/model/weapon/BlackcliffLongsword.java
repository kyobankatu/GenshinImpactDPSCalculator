package model.weapon;

import model.type.WeaponType;

/** Blackcliff Longsword with its Lv. 90 stats and modeled passive boundary. */
public class BlackcliffLongsword extends BlackcliffWeapon {
    /** Constructs an R5 Blackcliff Longsword. */
    public BlackcliffLongsword() {
        this(5);
    }

    /**
     * Constructs Blackcliff Longsword at a selected refinement.
     *
     * @param refinement refinement rank in the inclusive range 1-5
     */
    public BlackcliffLongsword(int refinement) {
        super("Blackcliff Longsword", WeaponType.SWORD, 565.0, 0.368, refinement);
    }
}

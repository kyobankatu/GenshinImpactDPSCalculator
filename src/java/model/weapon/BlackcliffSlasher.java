package model.weapon;

import model.type.WeaponType;

/** Blackcliff Slasher with its Lv. 90 stats and modeled passive boundary. */
public class BlackcliffSlasher extends BlackcliffWeapon {
    /** Constructs an R5 Blackcliff Slasher. */
    public BlackcliffSlasher() {
        this(5);
    }

    /**
     * Constructs Blackcliff Slasher at a selected refinement.
     *
     * @param refinement refinement rank in the inclusive range 1-5
     */
    public BlackcliffSlasher(int refinement) {
        super("Blackcliff Slasher", WeaponType.CLAYMORE, 510.0, 0.551, refinement);
    }
}

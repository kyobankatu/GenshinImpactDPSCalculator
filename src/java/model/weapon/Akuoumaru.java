package model.weapon;

import model.type.WeaponType;

/**
 * Akuoumaru with Lv. 90 stats and refinement-aware Watatsumi Wavewalker.
 */
public class Akuoumaru extends PartyEnergyBurstWeapon {
    /**
     * Constructs an R5 Akuoumaru.
     */
    public Akuoumaru() {
        this(5);
    }

    /**
     * Constructs Akuoumaru at a selected refinement.
     *
     * @param refinement refinement rank in the inclusive range 1-5
     */
    public Akuoumaru(int refinement) {
        super("Akuoumaru", WeaponType.CLAYMORE, 510.0, 0.413, refinement);
    }
}

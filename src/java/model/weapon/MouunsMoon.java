package model.weapon;

import model.type.WeaponType;

/**
 * Mouun's Moon with Lv. 90 stats and refinement-aware Watatsumi Wavewalker.
 */
public class MouunsMoon extends PartyEnergyBurstWeapon {
    /**
     * Constructs an R5 Mouun's Moon.
     */
    public MouunsMoon() {
        this(5);
    }

    /**
     * Constructs Mouun's Moon at a selected refinement.
     *
     * @param refinement refinement rank in the inclusive range 1-5
     */
    public MouunsMoon(int refinement) {
        super("Mouun's Moon", WeaponType.BOW, 565.0, 0.276, refinement);
    }
}

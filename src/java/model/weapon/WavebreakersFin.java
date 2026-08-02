package model.weapon;

import model.type.WeaponType;

/**
 * Wavebreaker's Fin with Lv. 90 stats and refinement-aware Watatsumi Wavewalker.
 */
public class WavebreakersFin extends PartyEnergyBurstWeapon {
    /**
     * Constructs an R5 Wavebreaker's Fin.
     */
    public WavebreakersFin() {
        this(5);
    }

    /**
     * Constructs Wavebreaker's Fin at a selected refinement.
     *
     * @param refinement refinement rank in the inclusive range 1-5
     */
    public WavebreakersFin(int refinement) {
        super("Wavebreaker's Fin", WeaponType.POLEARM, 620.0, 0.138, refinement);
    }
}

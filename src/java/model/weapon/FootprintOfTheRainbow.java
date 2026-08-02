package model.weapon;

import model.type.StatType;
import model.type.WeaponType;

/**
 * Footprint of the Rainbow with Lv. 90 stats and refinement-aware DEF window.
 */
public class FootprintOfTheRainbow extends SkillUseStatWeapon {
    /** Constructs an R5 Footprint of the Rainbow. */
    public FootprintOfTheRainbow() {
        this(5);
    }

    /**
     * Constructs Footprint of the Rainbow at a selected refinement.
     *
     * @param refinement refinement rank in the inclusive range 1-5
     */
    public FootprintOfTheRainbow(int refinement) {
        super("Footprint of the Rainbow", WeaponType.POLEARM, 510.0,
                StatType.DEF_PERCENT, 0.517, refinement,
                StatType.DEF_PERCENT, 0.12, 0.04, 15.0);
    }
}

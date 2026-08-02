package model.weapon;

import model.type.StatType;
import model.type.WeaponType;

/**
 * Flute of Ezpitzal with Lv. 90 stats and refinement-aware DEF window.
 */
public class FluteOfEzpitzal extends SkillUseStatWeapon {
    /** Constructs an R5 Flute of Ezpitzal. */
    public FluteOfEzpitzal() {
        this(5);
    }

    /**
     * Constructs Flute of Ezpitzal at a selected refinement.
     *
     * @param refinement refinement rank in the inclusive range 1-5
     */
    public FluteOfEzpitzal(int refinement) {
        super("Flute of Ezpitzal", WeaponType.SWORD, 454.0,
                StatType.DEF_PERCENT, 0.690, refinement,
                StatType.DEF_PERCENT, 0.12, 0.04, 15.0);
    }
}

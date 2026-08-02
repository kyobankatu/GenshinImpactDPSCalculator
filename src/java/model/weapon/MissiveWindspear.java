package model.weapon;

import model.type.StatType;
import model.type.WeaponType;

/**
 * Missive Windspear with Lv. 90 stats and refinement-aware reaction window.
 */
public class MissiveWindspear extends SkillHitOrReactionWindowWeapon {
    /** Constructs an R5 Missive Windspear. */
    public MissiveWindspear() {
        this(5);
    }

    /**
     * Constructs Missive Windspear at a selected refinement.
     *
     * @param refinement refinement rank in the inclusive range 1-5
     */
    public MissiveWindspear(int refinement) {
        super("Missive Windspear", WeaponType.POLEARM, 510.0,
                StatType.ATK_PERCENT, 0.413, refinement, false, 10.0);
    }
}

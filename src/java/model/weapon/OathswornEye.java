package model.weapon;

import model.type.StatType;
import model.type.WeaponType;

/**
 * Oathsworn Eye with Lv. 90 stats and a refreshable Skill-use ER window.
 */
public class OathswornEye extends SkillUseStatWeapon {
    /**
     * Constructs an R5 Oathsworn Eye.
     */
    public OathswornEye() {
        this(5);
    }

    /**
     * Constructs Oathsworn Eye at a selected refinement.
     *
     * @param refinement refinement rank in the inclusive range 1-5
     */
    public OathswornEye(int refinement) {
        super("Oathsworn Eye", WeaponType.CATALYST, 565.0,
                StatType.ATK_PERCENT, 0.276, refinement,
                StatType.ENERGY_RECHARGE, 0.18, 0.06, 10.0);
    }
}

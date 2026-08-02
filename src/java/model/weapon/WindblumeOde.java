package model.weapon;

import model.type.StatType;
import model.type.WeaponType;

/**
 * Windblume Ode with Lv. 90 stats and a refreshable Skill-use ATK window.
 */
public class WindblumeOde extends SkillUseStatWeapon {
    /**
     * Constructs an R5 Windblume Ode.
     */
    public WindblumeOde() {
        this(5);
    }

    /**
     * Constructs Windblume Ode at a selected refinement.
     *
     * @param refinement refinement rank in the inclusive range 1-5
     */
    public WindblumeOde(int refinement) {
        super("Windblume Ode", WeaponType.BOW, 510.0,
                StatType.ELEMENTAL_MASTERY, 165.0, refinement,
                StatType.ATK_PERCENT, 0.12, 0.04, 6.0);
    }
}

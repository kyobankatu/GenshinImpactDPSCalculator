package model.weapon;

import model.type.StatType;
import model.type.WeaponType;

/**
 * Kitain Cross Spear with Lv. 90 stats and refinement-aware Samurai Conduct.
 */
public class KitainCrossSpear extends SkillHitEnergyWeapon {
    /** Constructs an R5 Kitain Cross Spear. */
    public KitainCrossSpear() {
        this(5);
    }

    /**
     * Constructs Kitain Cross Spear at a selected refinement.
     *
     * @param refinement refinement rank in the inclusive range 1-5
     */
    public KitainCrossSpear(int refinement) {
        super("Kitain Cross Spear", WeaponType.POLEARM, 565.0,
                StatType.ELEMENTAL_MASTERY, 110.0, refinement);
    }
}

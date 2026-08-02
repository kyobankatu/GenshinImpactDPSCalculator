package model.weapon;

import model.type.StatType;
import model.type.WeaponType;

/**
 * Lightbearing Moonshard sword with DEF and a post-Skill Lunar-Crystallize window.
 */
public class LightbearingMoonshard extends SkillUseStatWeapon {
    /** Constructs Lightbearing Moonshard at refinement rank five. */
    public LightbearingMoonshard() {
        this(5);
    }

    /**
     * Constructs Lightbearing Moonshard at a selected refinement.
     *
     * @param refinement refinement rank in the inclusive range 1-5
     */
    public LightbearingMoonshard(int refinement) {
        super("Lightbearing Moonshard", WeaponType.SWORD, 542.0,
                StatType.CRIT_DMG, 0.882, refinement,
                StatType.LUNAR_CRYSTALLIZE_DMG_BONUS,
                0.48, 0.16, 5.0);
        getStats().set(StatType.DEF_PERCENT, 0.15 + 0.05 * refinement);
    }
}

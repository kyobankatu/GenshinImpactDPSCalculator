package model.weapon;

import model.type.StatType;
import model.type.WeaponType;

/**
 * Katsuragikiri Nagamasa with Lv. 90 stats and refinement-aware Samurai Conduct.
 */
public class KatsuragikiriNagamasa extends SkillHitEnergyWeapon {
    /** Constructs an R5 Katsuragikiri Nagamasa. */
    public KatsuragikiriNagamasa() {
        this(5);
    }

    /**
     * Constructs Katsuragikiri Nagamasa at a selected refinement.
     *
     * @param refinement refinement rank in the inclusive range 1-5
     */
    public KatsuragikiriNagamasa(int refinement) {
        super("Katsuragikiri Nagamasa", WeaponType.CLAYMORE, 510.0,
                StatType.ENERGY_RECHARGE, 0.459, refinement);
    }
}

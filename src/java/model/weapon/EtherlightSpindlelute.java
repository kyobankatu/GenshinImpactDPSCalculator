package model.weapon;

import model.type.StatType;
import model.type.WeaponType;

/**
 * Etherlight Spindlelute with Lv. 90 stats and refinement-aware Last Singer.
 */
public class EtherlightSpindlelute extends SkillUseStatWeapon {
    /**
     * Constructs an R5 Etherlight Spindlelute.
     */
    public EtherlightSpindlelute() {
        this(5);
    }

    /**
     * Constructs Etherlight Spindlelute at a selected refinement.
     *
     * @param refinement refinement rank in the inclusive range 1-5
     */
    public EtherlightSpindlelute(int refinement) {
        super("Etherlight Spindlelute", WeaponType.CATALYST, 510.0,
                StatType.ENERGY_RECHARGE, 0.459, refinement,
                StatType.ELEMENTAL_MASTERY, 75.0, 25.0, 20.0);
    }
}

package model.weapon;

import java.util.EnumSet;

import model.type.ActionType;
import model.type.StatType;
import model.type.WeaponType;

/**
 * Sacrificer's Staff with Lv. 90 stats and refinement-aware Untainted Desire.
 */
public class SacrificersStaff extends HitStackStatWeapon {
    /** Constructs an R5 Sacrificer's Staff. */
    public SacrificersStaff() {
        this(5);
    }

    /**
     * Constructs Sacrificer's Staff at a selected refinement.
     *
     * @param refinement refinement rank in the inclusive range 1-5
     */
    public SacrificersStaff(int refinement) {
        super("Sacrificer's Staff", WeaponType.POLEARM, 620.0,
                StatType.CRIT_RATE, 0.092, refinement,
                EnumSet.of(ActionType.SKILL),
                0.0,
                6.0,
                3,
                new StatType[] { StatType.ATK_PERCENT, StatType.ENERGY_RECHARGE },
                new double[] {
                        0.06 + 0.02 * refinement,
                        0.045 + 0.015 * refinement
                });
    }
}

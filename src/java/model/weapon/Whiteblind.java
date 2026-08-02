package model.weapon;

import java.util.EnumSet;

import model.type.ActionType;
import model.type.StatType;
import model.type.WeaponType;

/**
 * Whiteblind with Lv. 90 stats and refinement-aware Infusion Blade stacks.
 */
public class Whiteblind extends HitStackStatWeapon {
    /**
     * Constructs an R5 Whiteblind.
     */
    public Whiteblind() {
        this(5);
    }

    /**
     * Constructs Whiteblind at a selected refinement.
     *
     * @param refinement refinement rank in the inclusive range 1-5
     */
    public Whiteblind(int refinement) {
        super("Whiteblind", WeaponType.CLAYMORE, 510.0,
                StatType.DEF_PERCENT, 0.517, refinement,
                EnumSet.of(ActionType.NORMAL, ActionType.CHARGE),
                0.5,
                6.0,
                4,
                new StatType[] { StatType.ATK_PERCENT, StatType.DEF_PERCENT },
                new double[] {
                        0.045 + 0.015 * refinement,
                        0.045 + 0.015 * refinement
                });
    }
}

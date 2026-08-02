package model.weapon;

import java.util.EnumSet;

import model.type.ActionType;
import model.type.StatType;
import model.type.WeaponType;

/**
 * Dodoco Tales with Lv. 90 stats and refinement-aware Dodoventure windows.
 */
public class DodocoTales extends ReciprocalHitStatWeapon {
    /**
     * Constructs an R5 Dodoco Tales.
     */
    public DodocoTales() {
        this(5);
    }

    /**
     * Constructs Dodoco Tales at a selected refinement.
     *
     * @param refinement refinement rank in the inclusive range 1-5
     */
    public DodocoTales(int refinement) {
        super("Dodoco Tales", WeaponType.CATALYST, 454.0,
                StatType.ATK_PERCENT, 0.551, refinement,
                window(
                        EnumSet.of(ActionType.NORMAL),
                        6.0,
                        0.12 + 0.04 * refinement,
                        StatType.CHARGED_ATTACK_DMG_BONUS),
                window(
                        EnumSet.of(ActionType.CHARGE),
                        6.0,
                        0.06 + 0.02 * refinement,
                        StatType.ATK_PERCENT));
    }
}

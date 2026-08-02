package model.weapon;

import java.util.EnumSet;

import model.type.ActionType;
import model.type.StatType;
import model.type.WeaponType;

/**
 * Prototype Rancour with Lv. 90 stats and refinement-aware Smashed Stone stacks.
 */
public class PrototypeRancour extends HitStackStatWeapon {
    /** Constructs an R5 Prototype Rancour. */
    public PrototypeRancour() {
        this(5);
    }

    /**
     * Constructs Prototype Rancour at a selected refinement.
     *
     * @param refinement refinement rank in the inclusive range 1-5
     */
    public PrototypeRancour(int refinement) {
        super("Prototype Rancour", WeaponType.SWORD, 565.0,
                StatType.PHYSICAL_DMG_BONUS, 0.345, refinement,
                EnumSet.of(ActionType.NORMAL, ActionType.CHARGE),
                0.3,
                6.0,
                4,
                new StatType[] { StatType.ATK_PERCENT, StatType.DEF_PERCENT },
                new double[] {
                        0.03 + 0.01 * refinement,
                        0.03 + 0.01 * refinement
                });
    }
}

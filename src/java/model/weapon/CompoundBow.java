package model.weapon;

import java.util.EnumSet;

import model.type.ActionType;
import model.type.StatType;
import model.type.WeaponType;

/**
 * Compound Bow with Lv. 90 stats and refinement-aware Infusion Arrow stacks.
 */
public class CompoundBow extends HitStackStatWeapon {
    /**
     * Constructs an R5 Compound Bow.
     */
    public CompoundBow() {
        this(5);
    }

    /**
     * Constructs Compound Bow at a selected refinement.
     *
     * @param refinement refinement rank in the inclusive range 1-5
     */
    public CompoundBow(int refinement) {
        super("Compound Bow", WeaponType.BOW, 454.0,
                StatType.PHYSICAL_DMG_BONUS, 0.690, refinement,
                EnumSet.of(ActionType.NORMAL, ActionType.CHARGE),
                0.3,
                6.0,
                4,
                new StatType[] { StatType.ATK_PERCENT, StatType.ATK_SPD },
                new double[] {
                        0.03 + 0.01 * refinement,
                        0.009 + 0.003 * refinement
                });
    }
}

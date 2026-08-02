package model.weapon;

import java.util.EnumSet;

import model.type.ActionType;
import model.type.StatType;
import model.type.WeaponType;

/**
 * Skyrider Greatsword with Lv. 90 stats and refinement-aware Courage stacks.
 */
public class SkyriderGreatsword extends HitStackStatWeapon {
    /**
     * Constructs an R5 Skyrider Greatsword.
     */
    public SkyriderGreatsword() {
        this(5);
    }

    /**
     * Constructs Skyrider Greatsword at a selected refinement.
     *
     * @param refinement refinement rank in the inclusive range 1-5
     */
    public SkyriderGreatsword(int refinement) {
        super("Skyrider Greatsword", WeaponType.CLAYMORE, 401.0,
                StatType.PHYSICAL_DMG_BONUS, 0.439, refinement,
                EnumSet.of(ActionType.NORMAL, ActionType.CHARGE),
                0.5,
                6.0,
                4,
                new StatType[] { StatType.ATK_PERCENT },
                new double[] { 0.05 + 0.01 * refinement });
    }
}

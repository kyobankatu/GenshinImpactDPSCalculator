package model.weapon;

import java.util.EnumSet;

import model.type.ActionType;
import model.type.StatType;
import model.type.WeaponType;

/**
 * Ibis Piercer with Lv. 90 stats and refinement-aware Secret Wisdom stacks.
 */
public class IbisPiercer extends HitStackStatWeapon {
    /**
     * Constructs an R5 Ibis Piercer.
     */
    public IbisPiercer() {
        this(5);
    }

    /**
     * Constructs Ibis Piercer at a selected refinement.
     *
     * @param refinement refinement rank in the inclusive range 1-5
     */
    public IbisPiercer(int refinement) {
        super("Ibis Piercer", WeaponType.BOW, 565.0,
                StatType.ATK_PERCENT, 0.276, refinement,
                EnumSet.of(ActionType.CHARGE),
                0.5,
                6.0,
                2,
                new StatType[] { StatType.ELEMENTAL_MASTERY },
                new double[] { 30.0 + 10.0 * refinement });
    }
}

package model.weapon;

import java.util.EnumSet;

import model.type.ActionType;
import model.type.StatType;
import model.type.WeaponType;

/**
 * Ballad of the Boundless Blue with refinement-aware Azure Skies stacks.
 */
public class BalladOfTheBoundlessBlue extends HitStackStatWeapon {
    /**
     * Constructs an R5 Ballad of the Boundless Blue.
     */
    public BalladOfTheBoundlessBlue() {
        this(5);
    }

    /**
     * Constructs Ballad of the Boundless Blue at a selected refinement.
     *
     * @param refinement refinement rank in the inclusive range 1-5
     */
    public BalladOfTheBoundlessBlue(int refinement) {
        super("Ballad of the Boundless Blue", WeaponType.CATALYST, 565.0,
                StatType.ENERGY_RECHARGE, 0.306, refinement,
                EnumSet.of(ActionType.NORMAL, ActionType.CHARGE),
                0.3,
                6.0,
                3,
                new StatType[] {
                        StatType.NORMAL_ATTACK_DMG_BONUS,
                        StatType.CHARGED_ATTACK_DMG_BONUS
                },
                new double[] {
                        0.06 + 0.02 * refinement,
                        0.045 + 0.015 * refinement
                });
    }
}

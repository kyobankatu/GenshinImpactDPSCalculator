package model.weapon;

import java.util.EnumSet;

import model.type.ActionType;
import model.type.StatType;
import model.type.WeaponType;

/**
 * Solar Pearl with Lv. 90 stats and refinement-aware Solar Shine windows.
 */
public class SolarPearl extends ReciprocalHitStatWeapon {
    /**
     * Constructs an R5 Solar Pearl.
     */
    public SolarPearl() {
        this(5);
    }

    /**
     * Constructs Solar Pearl at a selected refinement.
     *
     * @param refinement refinement rank in the inclusive range 1-5
     */
    public SolarPearl(int refinement) {
        super("Solar Pearl", WeaponType.CATALYST, 510.0,
                StatType.CRIT_RATE, 0.276, refinement,
                window(
                        EnumSet.of(ActionType.NORMAL),
                        6.0,
                        0.15 + 0.05 * refinement,
                        StatType.SKILL_DMG_BONUS,
                        StatType.BURST_DMG_BONUS),
                window(
                        EnumSet.of(ActionType.SKILL, ActionType.BURST),
                        6.0,
                        0.15 + 0.05 * refinement,
                        StatType.NORMAL_ATTACK_DMG_BONUS));
    }
}

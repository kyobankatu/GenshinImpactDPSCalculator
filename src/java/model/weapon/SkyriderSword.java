package model.weapon;

import java.util.EnumSet;

import model.type.StatType;
import model.type.WeaponType;
import simulation.action.CharacterActionKey;

/**
 * Skyrider Sword with Lv. 90 stats and refinement-aware Burst-use ATK window.
 */
public class SkyriderSword extends ActionUseStatWeapon {
    /**
     * Constructs an R5 Skyrider Sword.
     */
    public SkyriderSword() {
        this(5);
    }

    /**
     * Constructs Skyrider Sword at a selected refinement.
     *
     * @param refinement refinement rank in the inclusive range 1-5
     */
    public SkyriderSword(int refinement) {
        super("Skyrider Sword", WeaponType.SWORD, 354.0,
                StatType.ENERGY_RECHARGE, 0.521, refinement,
                EnumSet.of(CharacterActionKey.BURST),
                StatType.ATK_PERCENT, 0.09, 0.03, 15.0);
    }
}

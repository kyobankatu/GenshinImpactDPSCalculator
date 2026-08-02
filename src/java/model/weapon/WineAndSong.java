package model.weapon;

import java.util.EnumSet;

import model.type.StatType;
import model.type.WeaponType;
import simulation.action.CharacterActionKey;

/**
 * Wine and Song with Lv. 90 stats and refinement-aware Dash-use ATK window.
 */
public class WineAndSong extends ActionUseStatWeapon {
    /**
     * Constructs an R5 Wine and Song.
     */
    public WineAndSong() {
        this(5);
    }

    /**
     * Constructs Wine and Song at a selected refinement.
     *
     * @param refinement refinement rank in the inclusive range 1-5
     */
    public WineAndSong(int refinement) {
        super("Wine and Song", WeaponType.CATALYST, 565.0,
                StatType.ENERGY_RECHARGE, 0.306, refinement,
                EnumSet.of(CharacterActionKey.DASH),
                StatType.ATK_PERCENT, 0.15, 0.05, 5.0);
    }
}

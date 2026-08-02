package model.weapon;

import model.type.Element;
import model.type.StatType;
import model.type.WeaponType;

/**
 * Cool Steel with Lv. 90 stats and a live Hydro/Cryo target passive.
 */
public class CoolSteel extends TargetAuraDamageWeapon {
    /**
     * Constructs an R5 Cool Steel.
     */
    public CoolSteel() {
        this(5);
    }

    /**
     * Constructs Cool Steel at a selected refinement.
     *
     * @param refinement refinement rank in the inclusive range 1-5
     */
    public CoolSteel(int refinement) {
        super("Cool Steel", WeaponType.SWORD, 401.0,
                StatType.ATK_PERCENT, 0.352, refinement,
                0.09, 0.03, Element.HYDRO, Element.CRYO);
    }
}

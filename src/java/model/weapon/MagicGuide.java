package model.weapon;

import model.type.Element;
import model.type.StatType;
import model.type.WeaponType;

/**
 * Magic Guide with Lv. 90 stats and a live Hydro/Electro target passive.
 */
public class MagicGuide extends TargetAuraDamageWeapon {
    /**
     * Constructs an R5 Magic Guide.
     */
    public MagicGuide() {
        this(5);
    }

    /**
     * Constructs Magic Guide at a selected refinement.
     *
     * @param refinement refinement rank in the inclusive range 1-5
     */
    public MagicGuide(int refinement) {
        super("Magic Guide", WeaponType.CATALYST, 354.0,
                StatType.ELEMENTAL_MASTERY, 187.0, refinement,
                0.09, 0.03, Element.HYDRO, Element.ELECTRO);
    }
}

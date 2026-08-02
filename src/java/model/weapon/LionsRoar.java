package model.weapon;

import model.type.Element;
import model.type.StatType;
import model.type.WeaponType;

/**
 * Lion's Roar with Lv. 90 stats and a live Pyro/Electro target passive.
 */
public class LionsRoar extends TargetAuraDamageWeapon {
    /**
     * Constructs an R5 Lion's Roar.
     */
    public LionsRoar() {
        this(5);
    }

    /**
     * Constructs Lion's Roar at a selected refinement.
     *
     * @param refinement refinement rank in the inclusive range 1-5
     */
    public LionsRoar(int refinement) {
        super("Lion's Roar", WeaponType.SWORD, 510.0,
                StatType.ATK_PERCENT, 0.413, refinement,
                0.16, 0.04, Element.PYRO, Element.ELECTRO);
    }
}

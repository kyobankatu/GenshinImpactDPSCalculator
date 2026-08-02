package model.weapon;

import model.type.Element;
import model.type.StatType;
import model.type.WeaponType;

/**
 * Dragon's Bane polearm with an aura-conditional damage bonus passive.
 */
public class DragonsBane extends TargetAuraDamageWeapon {

    /**
     * Constructs Dragon's Bane with Lv 90 base stats.
     */
    public DragonsBane() {
        this(5);
    }

    /**
     * Constructs Dragon's Bane at a selected refinement.
     *
     * @param refinement refinement rank in the inclusive range 1-5
     */
    public DragonsBane(int refinement) {
        super("Dragon's Bane", WeaponType.POLEARM, 454.0,
                StatType.ELEMENTAL_MASTERY, 221.0, refinement,
                0.16, 0.04, Element.HYDRO, Element.PYRO);
    }
}

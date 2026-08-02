package model.weapon;

import model.type.Element;
import model.type.StatType;
import model.type.WeaponType;

/**
 * Rainslasher with Lv. 90 stats and a live Hydro/Electro target passive.
 */
public class Rainslasher extends TargetAuraDamageWeapon {
    /**
     * Constructs an R5 Rainslasher.
     */
    public Rainslasher() {
        this(5);
    }

    /**
     * Constructs Rainslasher at a selected refinement.
     *
     * @param refinement refinement rank in the inclusive range 1-5
     */
    public Rainslasher(int refinement) {
        super("Rainslasher", WeaponType.CLAYMORE, 510.0,
                StatType.ELEMENTAL_MASTERY, 165.0, refinement,
                0.16, 0.04, Element.HYDRO, Element.ELECTRO);
    }
}

package model.weapon;

import model.type.Element;
import model.type.StatType;
import model.type.WeaponType;

/**
 * Raven Bow with Lv. 90 stats and a live Hydro/Pyro target passive.
 */
public class RavenBow extends TargetAuraDamageWeapon {
    /**
     * Constructs an R5 Raven Bow.
     */
    public RavenBow() {
        this(5);
    }

    /**
     * Constructs Raven Bow at a selected refinement.
     *
     * @param refinement refinement rank in the inclusive range 1-5
     */
    public RavenBow(int refinement) {
        super("Raven Bow", WeaponType.BOW, 448.0,
                StatType.ELEMENTAL_MASTERY, 94.0, refinement,
                0.09, 0.03, Element.HYDRO, Element.PYRO);
    }
}

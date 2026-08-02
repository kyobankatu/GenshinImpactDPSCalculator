package model.weapon;

import model.type.StatType;
import model.type.WeaponType;

/**
 * Haran Geppaku Futsu with Lv. 90 stats and its static elemental damage bonus.
 *
 * <p>The Wavespike Normal Attack branch is unavailable because the simulator
 * has no cross-party callback for Elemental Skill use by equipped weapons.</p>
 */
public class HaranGeppakuFutsu extends StaticElementalDamageWeapon {
    /** Constructs Haran Geppaku Futsu at refinement rank five. */
    public HaranGeppakuFutsu() {
        this(5);
    }

    /**
     * Constructs Haran Geppaku Futsu at a selected refinement.
     *
     * @param refinement refinement rank in the inclusive range 1-5
     */
    public HaranGeppakuFutsu(int refinement) {
        super(
                "Haran Geppaku Futsu",
                WeaponType.SWORD,
                608.0,
                StatType.CRIT_RATE,
                0.331,
                refinement);
    }
}

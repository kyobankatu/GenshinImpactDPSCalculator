package model.weapon;

import model.type.Element;
import model.type.StatType;
import model.type.WeaponType;

/**
 * Bloodtainted Greatsword with Lv. 90 stats and a live Pyro/Electro target passive.
 */
public class BloodtaintedGreatsword extends TargetAuraDamageWeapon {
    /**
     * Constructs an R5 Bloodtainted Greatsword.
     */
    public BloodtaintedGreatsword() {
        this(5);
    }

    /**
     * Constructs Bloodtainted Greatsword at a selected refinement.
     *
     * @param refinement refinement rank in the inclusive range 1-5
     */
    public BloodtaintedGreatsword(int refinement) {
        super("Bloodtainted Greatsword", WeaponType.CLAYMORE, 354.0,
                StatType.ELEMENTAL_MASTERY, 187.0, refinement,
                0.09, 0.03, Element.PYRO, Element.ELECTRO);
    }
}

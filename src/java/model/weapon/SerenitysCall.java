package model.weapon;

import model.type.StatType;
import model.type.WeaponType;

/**
 * Serenity's Call sword with an off-field Moonsign-sensitive reaction HP window.
 */
public class SerenitysCall extends MoonsignReactionWindowWeapon {
    /** Constructs Serenity's Call at refinement rank five. */
    public SerenitysCall() {
        this(5);
    }

    /**
     * Constructs Serenity's Call at a selected refinement.
     *
     * @param refinement refinement rank in the inclusive range 1-5
     */
    public SerenitysCall(int refinement) {
        super("Serenity's Call", WeaponType.SWORD, 454.0,
                StatType.ENERGY_RECHARGE, 0.613, refinement,
                StatType.HP_PERCENT, 0.12 + 0.04 * refinement, 12.0);
    }
}

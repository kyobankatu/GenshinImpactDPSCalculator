package model.weapon;

import model.type.StatType;
import model.type.WeaponType;

/** Snare Hook bow with an off-field Moonsign-sensitive reaction EM window. */
public class SnareHook extends MoonsignReactionWindowWeapon {
    /** Constructs Snare Hook at refinement rank five. */
    public SnareHook() {
        this(5);
    }

    /**
     * Constructs Snare Hook at a selected refinement.
     *
     * @param refinement refinement rank in the inclusive range 1-5
     */
    public SnareHook(int refinement) {
        super("Snare Hook", WeaponType.BOW, 454.0,
                StatType.ENERGY_RECHARGE, 0.613, refinement,
                StatType.ELEMENTAL_MASTERY, 45.0 + 15.0 * refinement, 12.0);
    }
}

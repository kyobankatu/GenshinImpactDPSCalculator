package model.weapon;

import model.type.StatType;
import model.type.WeaponType;

/**
 * Master Key claymore with an off-field Moonsign-sensitive reaction EM window.
 */
public class MasterKey extends MoonsignReactionWindowWeapon {
    /** Constructs Master Key at refinement rank five. */
    public MasterKey() {
        this(5);
    }

    /**
     * Constructs Master Key at a selected refinement.
     *
     * @param refinement refinement rank in the inclusive range 1-5
     */
    public MasterKey(int refinement) {
        super("Master Key", WeaponType.CLAYMORE, 454.0,
                StatType.ENERGY_RECHARGE, 0.613, refinement,
                StatType.ELEMENTAL_MASTERY, 45.0 + 15.0 * refinement, 12.0);
    }
}

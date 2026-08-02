package model.weapon;

import model.type.StatType;
import model.type.WeaponType;

/**
 * Primordial Jade Cutter with Lv. 90 stats and Protector's Virtue.
 */
public class PrimordialJadeCutter extends MaxHpScalingWeapon {
    /** Constructs Primordial Jade Cutter at refinement rank five. */
    public PrimordialJadeCutter() {
        this(5);
    }

    /**
     * Constructs Primordial Jade Cutter at a selected refinement.
     *
     * @param refinement refinement rank in the inclusive range 1-5
     */
    public PrimordialJadeCutter(int refinement) {
        super("Primordial Jade Cutter", WeaponType.SWORD, 542.0,
                StatType.CRIT_RATE, 0.441, refinement,
                0.15 + 0.05 * refinement,
                0.009 + 0.003 * refinement);
    }
}

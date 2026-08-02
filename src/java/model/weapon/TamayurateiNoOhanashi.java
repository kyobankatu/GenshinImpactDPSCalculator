package model.weapon;

import model.type.StatType;
import model.type.WeaponType;

/**
 * Tamayuratei no Ohanashi with Lv. 90 stats and its combat-relevant ATK window.
 */
public class TamayurateiNoOhanashi extends SkillUseStatWeapon {
    /** Constructs an R5 Tamayuratei no Ohanashi. */
    public TamayurateiNoOhanashi() {
        this(5);
    }

    /**
     * Constructs Tamayuratei no Ohanashi at a selected refinement.
     *
     * @param refinement refinement rank in the inclusive range 1-5
     */
    public TamayurateiNoOhanashi(int refinement) {
        super("Tamayuratei no Ohanashi", WeaponType.POLEARM, 565.0,
                StatType.ENERGY_RECHARGE, 0.306, refinement,
                StatType.ATK_PERCENT, 0.15, 0.05, 10.0);
    }
}

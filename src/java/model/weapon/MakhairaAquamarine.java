package model.weapon;

import mechanics.buff.BuffId;
import model.type.StatType;
import model.type.WeaponType;

/**
 * Makhaira Aquamarine claymore with periodic EM-to-ATK team conversion.
 */
public class MakhairaAquamarine extends TimedElementalMasteryTeamStatWeapon {
    /** Constructs Makhaira Aquamarine at refinement rank five. */
    public MakhairaAquamarine() {
        this(5);
    }

    /**
     * Constructs Makhaira Aquamarine at a selected refinement.
     *
     * @param refinement refinement rank in the inclusive range 1-5
     */
    public MakhairaAquamarine(int refinement) {
        super("Makhaira Aquamarine", WeaponType.CLAYMORE, refinement,
                "Desert Pavilion",
                BuffId.MAKHAIRA_AQUAMARINE_DESERT_PAVILION,
                StatType.ATK_FLAT,
                0.18 + 0.06 * refinement);
    }
}

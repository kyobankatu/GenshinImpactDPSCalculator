package model.weapon;

import mechanics.buff.BuffId;
import model.type.StatType;
import model.type.WeaponType;

/**
 * Wandering Evenstar catalyst with periodic EM-to-ATK team conversion.
 */
public class WanderingEvenstar extends TimedElementalMasteryTeamStatWeapon {
    /** Constructs Wandering Evenstar at refinement rank five. */
    public WanderingEvenstar() {
        this(5);
    }

    /**
     * Constructs Wandering Evenstar at a selected refinement.
     *
     * @param refinement refinement rank in the inclusive range 1-5
     */
    public WanderingEvenstar(int refinement) {
        super("Wandering Evenstar", WeaponType.CATALYST, refinement,
                "Wildling Nightstar",
                BuffId.WANDERING_EVENSTAR_WILDLING_NIGHTSTAR,
                StatType.ATK_FLAT,
                0.18 + 0.06 * refinement);
    }
}

package model.weapon;

import model.type.StatType;
import model.type.WeaponType;

/**
 * Twin Nephrite with Lv. 90 metadata and inactive Guerilla Tactics.
 *
 * <p>After defeating an opponent, Guerilla Tactics increases ATK and movement
 * speed by {@code 12/14/16/18/20%} at R1-R5 for 15 seconds. Enemy defeat and
 * movement speed are not represented, so neither bonus can be activated.</p>
 */
public class TwinNephrite extends BoundaryInactiveWeapon {
    /** Constructs an R5 Twin Nephrite. */
    public TwinNephrite() {
        this(5);
    }

    /**
     * Constructs Twin Nephrite at a selected refinement.
     *
     * @param refinement refinement rank in the inclusive range 1-5
     */
    public TwinNephrite(int refinement) {
        super("Twin Nephrite", WeaponType.CATALYST, 448.0,
                StatType.CRIT_RATE, 0.156, refinement);
    }
}

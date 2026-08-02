package model.weapon;

import model.type.StatType;
import model.type.WeaponType;

/**
 * Otherworldly Story with Lv. 90 metadata and an inactive Energy Shower passive.
 *
 * <p>Energy Shower heals {@code 1/1.25/1.5/1.75/2%} HP at R1-R5 when the
 * wielder obtains an Elemental Orb or Particle. The simulator has no current-HP
 * or healing state, so applying that recovery would fabricate combat state.</p>
 */
public class OtherworldlyStory extends BoundaryInactiveWeapon {
    /** Constructs an R5 Otherworldly Story. */
    public OtherworldlyStory() {
        this(5);
    }

    /**
     * Constructs Otherworldly Story at a selected refinement.
     *
     * @param refinement refinement rank in the inclusive range 1-5
     */
    public OtherworldlyStory(int refinement) {
        super("Otherworldly Story", WeaponType.CATALYST, 401.0,
                StatType.ENERGY_RECHARGE, 0.390, refinement);
    }
}

package model.weapon;

import model.type.StatType;
import model.type.WeaponType;

/**
 * Traveler's Handy Sword with Lv. 90 metadata and an inactive Journey passive.
 *
 * <p>Journey heals {@code 1/1.25/1.5/1.75/2%} HP at R1-R5 when the wielder
 * obtains an Elemental Orb or Particle. The simulator has no current-HP or
 * healing state, so applying that recovery would fabricate combat state.</p>
 */
public class TravelersHandySword extends BoundaryInactiveWeapon {
    /** Constructs an R5 Traveler's Handy Sword. */
    public TravelersHandySword() {
        this(5);
    }

    /**
     * Constructs Traveler's Handy Sword at a selected refinement.
     *
     * @param refinement refinement rank in the inclusive range 1-5
     */
    public TravelersHandySword(int refinement) {
        super("Traveler's Handy Sword", WeaponType.SWORD, 448.0,
                StatType.DEF_PERCENT, 0.293, refinement);
    }
}

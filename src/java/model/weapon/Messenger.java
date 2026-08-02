package model.weapon;

import model.type.StatType;
import model.type.WeaponType;

/**
 * Messenger with Lv. 90 metadata and an inactive Archer's Message passive.
 *
 * <p>Archer's Message deals {@code 100/125/150/175/200% ATK} at R1-R5 when
 * a Charged Attack hits a weak point, with a 10-second cooldown. Attack and
 * enemy models expose no weak-point metadata, so the proc cannot be selected.</p>
 */
public class Messenger extends BoundaryInactiveWeapon {
    /** Constructs an R5 Messenger. */
    public Messenger() {
        this(5);
    }

    /**
     * Constructs Messenger at a selected refinement.
     *
     * @param refinement refinement rank in the inclusive range 1-5
     */
    public Messenger(int refinement) {
        super("Messenger", WeaponType.BOW, 448.0,
                StatType.CRIT_DMG, 0.312, refinement);
    }
}

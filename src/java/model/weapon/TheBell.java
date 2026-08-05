package model.weapon;

import model.type.StatType;
import model.type.WeaponType;

/**
 * The Bell with exact metadata and an explicit incoming-damage boundary.
 *
 * <p>Rebellious Guardian requires player damage and shield state. Those
 * systems are absent, so shield generation and its R1-R5 12-24% damage bonus
 * remain inactive instead of being approximated.</p>
 */
public final class TheBell extends BoundaryInactiveWeapon {
    /** Constructs the weapon at refinement rank five. */
    public TheBell() {
        this(5);
    }

    /** Constructs the weapon at the selected refinement rank. */
    public TheBell(int refinement) {
        super("The Bell", WeaponType.CLAYMORE, 510.0,
                StatType.HP_PERCENT, 0.413, refinement);
    }
}

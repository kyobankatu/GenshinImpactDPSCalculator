package model.weapon;

import model.type.StatType;
import model.type.WeaponType;

/**
 * Recurve Bow with Lv. 90 metadata and inactive Cull the Weak.
 *
 * <p>Cull the Weak heals {@code 8/10/12/14/16%} HP at R1-R5 after defeating
 * an opponent. The simulator models neither enemy defeat nor current HP, so
 * the passive cannot trigger within the supported combat boundary.</p>
 */
public class RecurveBow extends BoundaryInactiveWeapon {
    /** Constructs an R5 Recurve Bow. */
    public RecurveBow() {
        this(5);
    }

    /**
     * Constructs Recurve Bow at a selected refinement.
     *
     * @param refinement refinement rank in the inclusive range 1-5
     */
    public RecurveBow(int refinement) {
        super("Recurve Bow", WeaponType.BOW, 354.0,
                StatType.HP_PERCENT, 0.469, refinement);
    }
}

package model.weapon;

import model.type.StatType;
import model.type.WeaponType;

/**
 * White Iron Greatsword with Lv. 90 metadata and inactive Cull the Weak.
 *
 * <p>Cull the Weak heals {@code 8/10/12/14/16%} HP at R1-R5 after defeating
 * an opponent. The simulator models neither enemy defeat nor current HP, so
 * the passive cannot trigger within the supported combat boundary.</p>
 */
public class WhiteIronGreatsword extends BoundaryInactiveWeapon {
    /** Constructs an R5 White Iron Greatsword. */
    public WhiteIronGreatsword() {
        this(5);
    }

    /**
     * Constructs White Iron Greatsword at a selected refinement.
     *
     * @param refinement refinement rank in the inclusive range 1-5
     */
    public WhiteIronGreatsword(int refinement) {
        super("White Iron Greatsword", WeaponType.CLAYMORE, 401.0,
                StatType.DEF_PERCENT, 0.439, refinement);
    }
}

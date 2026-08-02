package model.weapon;

import model.type.StatType;
import model.type.WeaponType;

/**
 * Black Tassel with Lv. 90 metadata and inactive Bane of the Soft.
 *
 * <p>Bane of the Soft increases damage against slimes by
 * {@code 40/50/60/70/80%} at R1-R5. Enemy type is not represented, and the
 * simulator's generic enemy must not be presumed to be a slime.</p>
 */
public class BlackTassel extends BoundaryInactiveWeapon {
    /** Constructs an R5 Black Tassel. */
    public BlackTassel() {
        this(5);
    }

    /**
     * Constructs Black Tassel at a selected refinement.
     *
     * @param refinement refinement rank in the inclusive range 1-5
     */
    public BlackTassel(int refinement) {
        super("Black Tassel", WeaponType.POLEARM, 354.0,
                StatType.HP_PERCENT, 0.469, refinement);
    }
}

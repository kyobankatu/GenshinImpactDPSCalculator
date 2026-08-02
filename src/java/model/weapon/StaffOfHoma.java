package model.weapon;

import model.type.StatType;
import model.type.WeaponType;

/**
 * Staff of Homa with Lv. 90 stats and the unconditional Reckless Cinnabar ATK.
 *
 * <p>The additional Max-HP-to-ATK conversion below 50% current HP is
 * intentionally inactive because the simulator does not model player current
 * HP. Its canonical refinement value remains available as metadata.</p>
 */
public class StaffOfHoma extends MaxHpScalingWeapon {
    private final double canonicalBelowHalfHpAttackConversion;

    /** Constructs Staff of Homa at refinement rank five. */
    public StaffOfHoma() {
        this(5);
    }

    /**
     * Constructs Staff of Homa at a selected refinement.
     *
     * @param refinement refinement rank in the inclusive range 1-5
     */
    public StaffOfHoma(int refinement) {
        super("Staff of Homa", WeaponType.POLEARM, 608.0,
                StatType.CRIT_DMG, 0.662, refinement,
                0.15 + 0.05 * refinement,
                0.006 + 0.002 * refinement);
        this.canonicalBelowHalfHpAttackConversion =
                0.008 + 0.002 * refinement;
    }

    /**
     * Returns the canonical additional conversion below 50% current HP.
     *
     * @return inactive low-HP conversion ratio as a decimal
     */
    public double getCanonicalBelowHalfHpAttackConversion() {
        return canonicalBelowHalfHpAttackConversion;
    }

    /**
     * Reports whether the below-50%-HP branch is represented at runtime.
     *
     * @return {@code false} because player current HP is not modeled
     */
    public boolean isBelowHalfHpBonusActive() {
        return false;
    }
}

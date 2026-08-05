package model.weapon;

import model.type.StatType;
import model.type.WeaponType;

/** Splendor of Tranquil Waters with explicit player-HP-change boundaries. */
public final class SplendorOfTranquilWaters extends BoundaryInactiveWeapon {
    /** Constructs the weapon at refinement rank five. */
    public SplendorOfTranquilWaters() {
        this(5);
    }

    /** Constructs the weapon at the selected refinement rank. */
    public SplendorOfTranquilWaters(int refinement) {
        super("Splendor of Tranquil Waters", WeaponType.SWORD, 542.0,
                StatType.CRIT_DMG, 0.882, refinement);
    }

    /** Returns inactive owner-HP-change Skill DMG per stack. */
    public double getSkillDamagePerStack() {
        return 0.06 + 0.02 * getRefinement();
    }

    /** Returns inactive other-character-HP-change HP per stack. */
    public double getHpBonusPerStack() {
        return 0.105 + 0.035 * getRefinement();
    }
}

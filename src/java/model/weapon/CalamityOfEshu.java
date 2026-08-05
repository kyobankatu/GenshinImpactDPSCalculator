package model.weapon;

import model.type.StatType;
import model.type.WeaponType;

/** Calamity of Eshu with an explicit player-shield boundary. */
public final class CalamityOfEshu extends BoundaryInactiveWeapon {
    /** Constructs the weapon at refinement rank five. */
    public CalamityOfEshu() {
        this(5);
    }

    /** Constructs the weapon at the selected refinement rank. */
    public CalamityOfEshu(int refinement) {
        super("Calamity of Eshu", WeaponType.SWORD, 565.0,
                StatType.ATK_PERCENT, 0.276, refinement);
    }

    /** Returns inactive shielded Normal/Charged DMG. */
    public double getShieldedDamageBonus() {
        return 0.15 + 0.05 * getRefinement();
    }

    /** Returns inactive shielded Normal/Charged CRIT Rate. */
    public double getShieldedCriticalRate() {
        return 0.06 + 0.02 * getRefinement();
    }
}

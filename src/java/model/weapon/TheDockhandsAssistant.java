package model.weapon;

import model.type.StatType;
import model.type.WeaponType;

/** The Dockhand's Assistant with an explicit healing-Symbol boundary. */
public final class TheDockhandsAssistant extends BoundaryInactiveWeapon {
    /** Constructs the weapon at refinement rank five. */
    public TheDockhandsAssistant() {
        this(5);
    }

    /** Constructs the weapon at the selected refinement rank. */
    public TheDockhandsAssistant(int refinement) {
        super("The Dockhand's Assistant", WeaponType.SWORD, 510.0,
                StatType.HP_PERCENT, 0.413, refinement);
    }
}

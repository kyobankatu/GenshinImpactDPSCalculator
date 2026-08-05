package model.weapon;

import model.type.StatType;
import model.type.WeaponType;

/** Prospector's Drill with an explicit healing-Symbol boundary. */
public final class ProspectorsDrill extends BoundaryInactiveWeapon {
    /** Constructs the weapon at refinement rank five. */
    public ProspectorsDrill() {
        this(5);
    }

    /** Constructs the weapon at the selected refinement rank. */
    public ProspectorsDrill(int refinement) {
        super("Prospector's Drill", WeaponType.POLEARM, 565.0,
                StatType.ATK_PERCENT, 0.276, refinement);
    }
}

package model.weapon;

import model.type.StatType;
import model.type.WeaponType;

/** Dialogues of the Desert Sages with an explicit healing-event boundary. */
public final class DialoguesOfTheDesertSages
        extends BoundaryInactiveWeapon {
    /** Constructs the weapon at refinement rank five. */
    public DialoguesOfTheDesertSages() {
        this(5);
    }

    /** Constructs the weapon at the selected refinement rank. */
    public DialoguesOfTheDesertSages(int refinement) {
        super("Dialogues of the Desert Sages", WeaponType.POLEARM, 510.0,
                StatType.HP_PERCENT, 0.413, refinement);
    }
}

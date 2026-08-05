package model.weapon;

import model.type.StatType;
import model.type.WeaponType;

/** Song of Stillness with an explicit post-healing damage boundary. */
public final class SongOfStillness extends BoundaryInactiveWeapon {
    /** Constructs the weapon at refinement rank five. */
    public SongOfStillness() {
        this(5);
    }

    /** Constructs the weapon at the selected refinement rank. */
    public SongOfStillness(int refinement) {
        super("Song of Stillness", WeaponType.BOW, 510.0,
                StatType.ATK_PERCENT, 0.413, refinement);
    }
}

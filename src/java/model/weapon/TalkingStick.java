package model.weapon;

import model.type.StatType;
import model.type.WeaponType;

/** Talking Stick with an explicit player-elemental-status boundary. */
public final class TalkingStick extends BoundaryInactiveWeapon {
    /** Constructs the weapon at refinement rank five. */
    public TalkingStick() {
        this(5);
    }

    /** Constructs the weapon at the selected refinement rank. */
    public TalkingStick(int refinement) {
        super("Talking Stick", WeaponType.CLAYMORE, 565.0,
                StatType.CRIT_RATE, 0.184, refinement);
    }
}

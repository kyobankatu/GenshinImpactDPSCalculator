package model.weapon;

import model.type.StatType;
import model.type.WeaponType;

/**
 * Rightful Reward with exact metadata and an explicit healing boundary.
 *
 * <p>Tip of the Spear restores 8-16 Energy after the wielder is healed. The
 * simulator has no resolved player-heal event, so the trigger remains inactive
 * rather than restoring Energy on unrelated actions.</p>
 */
public final class RightfulReward extends BoundaryInactiveWeapon {
    /** Constructs the weapon at refinement rank five. */
    public RightfulReward() {
        this(5);
    }

    /** Constructs the weapon at the selected refinement rank. */
    public RightfulReward(int refinement) {
        super("Rightful Reward", WeaponType.POLEARM, 565.0,
                StatType.HP_PERCENT, 0.276, refinement);
    }
}

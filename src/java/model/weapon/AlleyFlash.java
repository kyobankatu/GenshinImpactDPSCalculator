package model.weapon;

import model.entity.Weapon;
import model.stats.StatsContainer;
import model.type.StatType;
import model.type.WeaponType;

/**
 * The Alley Flash sword with its fixed base stats and passive damage bonus.
 */
public class AlleyFlash extends Weapon {
    private final int refinement;

    /**
     * Constructs an R1 The Alley Flash, preserving the existing default.
     */
    public AlleyFlash() {
        this(1);
    }

    /**
     * Constructs The Alley Flash at a selected refinement.
     *
     * <p>The simulator has no incoming-damage state, so Itinerant Hero remains
     * active throughout the modeled no-incoming-damage combat boundary.
     *
     * @param refinement refinement rank in the inclusive range 1-5
     */
    public AlleyFlash(int refinement) {
        super("The Alley Flash", new StatsContainer());
        if (refinement < 1 || refinement > 5) {
            throw new IllegalArgumentException("Weapon refinement must be between 1 and 5");
        }
        this.refinement = refinement;
        StatsContainer s = this.getStats();
        s.add(StatType.BASE_ATK, 620);
        s.add(StatType.ELEMENTAL_MASTERY, 55.0);
        s.add(StatType.DMG_BONUS_ALL, 0.09 + 0.03 * refinement);
        this.weaponType = WeaponType.SWORD;
    }

    /**
     * Returns this weapon's refinement rank.
     *
     * @return refinement in the inclusive range 1-5
     */
    public int getRefinement() {
        return refinement;
    }
}

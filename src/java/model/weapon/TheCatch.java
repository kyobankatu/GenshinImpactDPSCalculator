package model.weapon;

import model.entity.Weapon;
import model.stats.StatsContainer;
import model.type.StatType;
import model.type.WeaponType;

/**
 * The Catch polearm with its Lv 90 stats and burst-focused passive.
 */
public class TheCatch extends Weapon {
    private final int refinement;

    /**
     * Constructs an R5 The Catch, preserving the existing default.
     */
    public TheCatch() {
        this(5);
    }

    /**
     * Constructs The Catch at a selected refinement.
     *
     * @param refinement refinement rank in the inclusive range 1-5
     */
    public TheCatch(int refinement) {
        super("The Catch", new StatsContainer());
        if (refinement < 1 || refinement > 5) {
            throw new IllegalArgumentException("Weapon refinement must be between 1 and 5");
        }
        this.refinement = refinement;
        // Lv90 Base ATK 510, ER 45.9%
        getStats().set(StatType.BASE_ATK, 510);
        getStats().set(StatType.ENERGY_RECHARGE, 0.459);
        this.weaponType = WeaponType.POLEARM;
    }

    /**
     * Returns this weapon's refinement rank.
     *
     * @return refinement in the inclusive range 1-5
     */
    public int getRefinement() {
        return refinement;
    }

    /**
     * Applies the weapon's burst damage and burst CRIT Rate bonuses.
     *
     * @param stats the stats container to mutate in-place
     * @param currentTime simulation time in seconds
     */
    @Override
    public void applyPassive(StatsContainer stats, double currentTime) {
        stats.add(StatType.BURST_DMG_BONUS, 0.12 + 0.04 * refinement);
        stats.add(StatType.BURST_CRIT_RATE, 0.045 + 0.015 * refinement);
    }
}

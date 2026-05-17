package model.weapon;

import model.entity.Weapon;
import model.stats.StatsContainer;
import model.type.StatType;
import model.type.WeaponType;

/**
 * The Catch polearm with its Lv 90 stats and burst-focused passive.
 */
public class TheCatch extends Weapon {
    /**
     * Constructs The Catch with Lv 90 base stats.
     */
    public TheCatch() {
        super("The Catch", new StatsContainer());
        // Lv90 Base ATK 510, ER 45.9%
        getStats().set(StatType.BASE_ATK, 510);
        getStats().set(StatType.ENERGY_RECHARGE, 0.459);
        this.weaponType = WeaponType.POLEARM;
    }

    /**
     * Applies the weapon's burst damage and burst CRIT Rate bonuses.
     *
     * @param stats the stats container to mutate in-place
     * @param currentTime simulation time in seconds
     */
    @Override
    public void applyPassive(StatsContainer stats, double currentTime) {
        // R5: Burst DMG +32%, Burst CR +12%
        stats.add(StatType.BURST_DMG_BONUS, 0.32);
        stats.add(StatType.BURST_CRIT_RATE, 0.12);
    }
}

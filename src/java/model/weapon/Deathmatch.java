package model.weapon;

import model.entity.Weapon;
import model.stats.StatsContainer;
import model.type.StatType;
import model.type.WeaponType;

/**
 * Deathmatch polearm with passives that depend on enemy count.
 */
public class Deathmatch extends Weapon {
    private boolean singleTarget = true; // Default to single target context (Boss)

    /**
     * Constructs Deathmatch with Lv 90 base stats.
     */
    public Deathmatch() {
        super("Deathmatch", new StatsContainer());
        StatsContainer s = this.getStats();
        s.add(StatType.BASE_ATK, 454);
        s.add(StatType.CRIT_RATE, 0.368);
        this.weaponType = WeaponType.POLEARM;
    }

    /**
     * Set the battlefield context.
     * 
     * @param isSingleTarget true if &lt; 2 enemies (default), false if &gt;= 2
     *                       enemies.
     */
    public void setSingleTarget(boolean isSingleTarget) {
        this.singleTarget = isSingleTarget;
    }

    /**
     * Applies the weapon's conditional ATK and DEF bonuses for the current
     * enemy-count context.
     *
     * @param stats the stats container to mutate in-place
     * @param currentTime simulation time in seconds
     */
    @Override
    public void applyPassive(StatsContainer stats, double currentTime) {
        // Refinement 1
        // < 2 opponents: ATK +24%
        // >= 2 opponents: ATK +16%, DEF +16%

        if (singleTarget) {
            stats.add(StatType.ATK_PERCENT, 0.24);
        } else {
            stats.add(StatType.ATK_PERCENT, 0.16);
            stats.add(StatType.DEF_PERCENT, 0.16);
        }
    }
}

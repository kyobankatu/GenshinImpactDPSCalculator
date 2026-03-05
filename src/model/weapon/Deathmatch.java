package model.weapon;

import model.entity.Weapon;
import model.stats.StatsContainer;
import model.type.StatType;

public class Deathmatch extends Weapon {
    private boolean singleTarget = true; // Default to single target context (Boss)

    public Deathmatch() {
        super("Deathmatch", new StatsContainer());
        StatsContainer s = this.getStats();
        s.add(StatType.BASE_ATK, 454);
        s.add(StatType.CRIT_RATE, 0.368);
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

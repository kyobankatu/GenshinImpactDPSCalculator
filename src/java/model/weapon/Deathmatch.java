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
    private final int refinement;

    /**
     * Constructs an R1 Deathmatch, preserving the existing default.
     */
    public Deathmatch() {
        this(1);
    }

    /**
     * Constructs Deathmatch at a selected refinement.
     *
     * @param refinement refinement rank in the inclusive range 1-5
     */
    public Deathmatch(int refinement) {
        super("Deathmatch", new StatsContainer());
        if (refinement < 1 || refinement > 5) {
            throw new IllegalArgumentException("Weapon refinement must be between 1 and 5");
        }
        this.refinement = refinement;
        StatsContainer s = this.getStats();
        s.add(StatType.BASE_ATK, 454);
        s.add(StatType.CRIT_RATE, 0.368);
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
        if (singleTarget) {
            stats.add(StatType.ATK_PERCENT, 0.18 + 0.06 * refinement);
        } else {
            double multiTargetBonus = 0.12 + 0.04 * refinement;
            stats.add(StatType.ATK_PERCENT, multiTargetBonus);
            stats.add(StatType.DEF_PERCENT, multiTargetBonus);
        }
    }
}

package model.weapon;

import model.entity.Weapon;
import model.stats.StatsContainer;
import model.type.StatType;
import model.type.WeaponType;

/**
 * Festering Desire with Lv. 90 stats and refinement-aware Skill damage/CRIT.
 */
public class FesteringDesire extends Weapon {
    private final int refinement;
    private final double skillDamageBonus;
    private final double skillCritRate;

    /**
     * Constructs an R5 Festering Desire.
     */
    public FesteringDesire() {
        this(5);
    }

    /**
     * Constructs Festering Desire at a selected refinement.
     *
     * @param refinement refinement rank in the inclusive range 1-5
     */
    public FesteringDesire(int refinement) {
        super("Festering Desire", new StatsContainer());
        if (refinement < 1 || refinement > 5) {
            throw new IllegalArgumentException("Weapon refinement must be between 1 and 5");
        }
        this.refinement = refinement;
        this.skillDamageBonus = 0.12 + 0.04 * refinement;
        this.skillCritRate = 0.045 + 0.015 * refinement;
        this.weaponType = WeaponType.SWORD;
        getStats().set(StatType.BASE_ATK, 510.0);
        getStats().set(StatType.ENERGY_RECHARGE, 0.459);
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
     * Applies Undying Admiration to Skill damage and Skill CRIT only.
     *
     * @param stats stats container to mutate
     * @param currentTime simulation time in seconds
     */
    @Override
    public void applyPassive(StatsContainer stats, double currentTime) {
        stats.add(StatType.SKILL_DMG_BONUS, skillDamageBonus);
        stats.add(StatType.SKILL_CRIT_RATE, skillCritRate);
    }
}

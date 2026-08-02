package model.weapon;

import model.entity.Weapon;
import model.stats.StatsContainer;
import model.type.StatType;
import model.type.WeaponType;

/**
 * Amos' Bow with Lv. 90 stats and refinement-aware Strong-Willed damage bonuses.
 *
 * <p>The additional Normal and Charged Attack bonus based on projectile flight
 * time is unavailable because attack actions do not expose projectile travel time.</p>
 */
public class AmosBow extends Weapon {
    private final int refinement;
    private final double actionDamageBonus;

    /**
     * Constructs an R5 Amos' Bow.
     */
    public AmosBow() {
        this(5);
    }

    /**
     * Constructs Amos' Bow at a selected refinement.
     *
     * @param refinement refinement rank in the inclusive range 1-5
     */
    public AmosBow(int refinement) {
        super("Amos' Bow", new StatsContainer());
        if (refinement < 1 || refinement > 5) {
            throw new IllegalArgumentException(
                    "Weapon refinement must be between 1 and 5");
        }
        this.refinement = refinement;
        this.actionDamageBonus = 0.09 + 0.03 * refinement;
        this.weaponType = WeaponType.BOW;
        getStats().set(StatType.BASE_ATK, 608.0);
        getStats().set(StatType.ATK_PERCENT, 0.496);
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
     * Applies Strong-Willed's unconditional Normal and Charged Attack bonuses.
     *
     * @param stats stats container to mutate
     * @param currentTime simulation time in seconds
     */
    @Override
    public void applyPassive(StatsContainer stats, double currentTime) {
        stats.add(StatType.NORMAL_ATTACK_DMG_BONUS, actionDamageBonus);
        stats.add(StatType.CHARGED_ATTACK_DMG_BONUS, actionDamageBonus);
    }
}

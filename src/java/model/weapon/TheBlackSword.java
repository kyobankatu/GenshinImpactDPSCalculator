package model.weapon;

import model.entity.Weapon;
import model.stats.StatsContainer;
import model.type.StatType;
import model.type.WeaponType;

/**
 * The Black Sword with Lv. 90 stats and refinement-aware Justice damage bonuses.
 *
 * <p>The healing branch is outside the simulator's current player-HP model.</p>
 */
public class TheBlackSword extends Weapon {
    private final int refinement;
    private final double actionDamageBonus;

    /** Constructs an R5 The Black Sword. */
    public TheBlackSword() {
        this(5);
    }

    /**
     * Constructs The Black Sword at a selected refinement.
     *
     * @param refinement refinement rank in the inclusive range 1-5
     */
    public TheBlackSword(int refinement) {
        super("The Black Sword", new StatsContainer());
        if (refinement < 1 || refinement > 5) {
            throw new IllegalArgumentException(
                    "Weapon refinement must be between 1 and 5");
        }
        this.refinement = refinement;
        this.actionDamageBonus = 0.15 + 0.05 * refinement;
        this.weaponType = WeaponType.SWORD;
        getStats().set(StatType.BASE_ATK, 510.0);
        getStats().set(StatType.CRIT_RATE, 0.276);
    }

    /**
     * Returns this weapon's refinement rank.
     *
     * @return refinement in the inclusive range 1-5
     */
    public int getRefinement() {
        return refinement;
    }

    /** Applies Justice to Normal and Charged Attack damage. */
    @Override
    public void applyPassive(StatsContainer stats, double currentTime) {
        stats.add(StatType.NORMAL_ATTACK_DMG_BONUS, actionDamageBonus);
        stats.add(StatType.CHARGED_ATTACK_DMG_BONUS, actionDamageBonus);
    }
}

package model.weapon;

import model.entity.Weapon;
import model.stats.StatsContainer;
import model.type.StatType;
import model.type.WeaponType;

/**
 * Harbinger of Dawn with Lv. 90 stats and refinement-aware Vigorous CRIT Rate.
 *
 * <p>
 * Vigorous is active while the wielder has more than 90% HP. The simulator does
 * not model player damage or current HP, so characters remain at full HP and the
 * condition is continuously satisfied within the supported combat boundary.
 */
public class HarbingerOfDawn extends Weapon {
    private final int refinement;
    private final double critRateBonus;

    /** Constructs an R5 Harbinger of Dawn. */
    public HarbingerOfDawn() {
        this(5);
    }

    /**
     * Constructs Harbinger of Dawn at a selected refinement.
     *
     * @param refinement refinement rank in the inclusive range 1-5
     * @throws IllegalArgumentException when refinement is outside 1-5
     */
    public HarbingerOfDawn(int refinement) {
        super("Harbinger of Dawn", new StatsContainer());
        if (refinement < 1 || refinement > 5) {
            throw new IllegalArgumentException(
                    "Weapon refinement must be between 1 and 5");
        }
        this.refinement = refinement;
        this.critRateBonus = 0.105 + 0.035 * refinement;
        this.weaponType = WeaponType.SWORD;
        getStats().set(StatType.BASE_ATK, 401.0);
        getStats().set(StatType.CRIT_DMG, 0.469);
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
     * Applies Vigorous continuously because modeled characters remain at full HP.
     *
     * @param stats stats container to mutate
     * @param currentTime simulation time in seconds
     */
    @Override
    public void applyPassive(StatsContainer stats, double currentTime) {
        stats.add(StatType.CRIT_RATE, critRateBonus);
    }
}

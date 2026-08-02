package model.weapon;

import model.entity.Weapon;
import model.stats.StatsContainer;
import model.type.StatType;
import model.type.WeaponType;

/**
 * White Tassel with Lv. 90 stats and refinement-aware Normal Attack damage.
 */
public class WhiteTassel extends Weapon {
    private final int refinement;
    private final double normalDamageBonus;

    /**
     * Constructs an R5 White Tassel.
     */
    public WhiteTassel() {
        this(5);
    }

    /**
     * Constructs White Tassel at a selected refinement.
     *
     * @param refinement refinement rank in the inclusive range 1-5
     */
    public WhiteTassel(int refinement) {
        super("White Tassel", new StatsContainer());
        if (refinement < 1 || refinement > 5) {
            throw new IllegalArgumentException("Weapon refinement must be between 1 and 5");
        }
        this.refinement = refinement;
        this.normalDamageBonus = 0.18 + 0.06 * refinement;
        this.weaponType = WeaponType.POLEARM;
        getStats().set(StatType.BASE_ATK, 401.0);
        getStats().set(StatType.CRIT_RATE, 0.234);
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
     * Applies Sharp to Normal Attack damage only.
     *
     * @param stats stats container to mutate
     * @param currentTime simulation time in seconds
     */
    @Override
    public void applyPassive(StatsContainer stats, double currentTime) {
        stats.add(StatType.NORMAL_ATTACK_DMG_BONUS, normalDamageBonus);
    }
}

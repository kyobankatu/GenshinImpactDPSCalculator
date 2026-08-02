package model.weapon;

import model.entity.Weapon;
import model.stats.StatsContainer;
import model.type.StatType;
import model.type.WeaponType;

/**
 * Aquila Favonia with Lv. 90 stats and refinement-aware Falcon's Defiance ATK.
 *
 * <p>The incoming-damage trigger, healing, and retaliatory damage branch is
 * unreachable because the simulator does not model damage received by players.</p>
 */
public class AquilaFavonia extends Weapon {
    private final int refinement;
    private final double attackBonus;

    /**
     * Constructs an R5 Aquila Favonia.
     */
    public AquilaFavonia() {
        this(5);
    }

    /**
     * Constructs Aquila Favonia at a selected refinement.
     *
     * @param refinement refinement rank in the inclusive range 1-5
     */
    public AquilaFavonia(int refinement) {
        super("Aquila Favonia", new StatsContainer());
        if (refinement < 1 || refinement > 5) {
            throw new IllegalArgumentException(
                    "Weapon refinement must be between 1 and 5");
        }
        this.refinement = refinement;
        this.attackBonus = 0.15 + 0.05 * refinement;
        this.weaponType = WeaponType.SWORD;
        getStats().set(StatType.BASE_ATK, 674.0);
        getStats().set(StatType.PHYSICAL_DMG_BONUS, 0.413);
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
     * Applies Falcon's Defiance's unconditional ATK bonus.
     *
     * @param stats stats container to mutate
     * @param currentTime simulation time in seconds
     */
    @Override
    public void applyPassive(StatsContainer stats, double currentTime) {
        stats.add(StatType.ATK_PERCENT, attackBonus);
    }
}

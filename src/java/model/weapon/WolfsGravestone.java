package model.weapon;

import model.entity.Weapon;
import model.stats.StatsContainer;
import model.type.StatType;
import model.type.WeaponType;

/**
 * Wolf's Gravestone with Lv. 90 stats and refinement-aware Wolfish Tracker ATK.
 *
 * <p>The team ATK branch triggered by damaging an enemy below 30% HP is
 * unreachable because the simulator does not model enemy current HP.</p>
 */
public class WolfsGravestone extends Weapon {
    private final int refinement;
    private final double attackBonus;

    /**
     * Constructs an R5 Wolf's Gravestone.
     */
    public WolfsGravestone() {
        this(5);
    }

    /**
     * Constructs Wolf's Gravestone at a selected refinement.
     *
     * @param refinement refinement rank in the inclusive range 1-5
     */
    public WolfsGravestone(int refinement) {
        super("Wolf's Gravestone", new StatsContainer());
        if (refinement < 1 || refinement > 5) {
            throw new IllegalArgumentException(
                    "Weapon refinement must be between 1 and 5");
        }
        this.refinement = refinement;
        this.attackBonus = 0.15 + 0.05 * refinement;
        this.weaponType = WeaponType.CLAYMORE;
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
     * Applies Wolfish Tracker's unconditional ATK bonus.
     *
     * @param stats stats container to mutate
     * @param currentTime simulation time in seconds
     */
    @Override
    public void applyPassive(StatsContainer stats, double currentTime) {
        stats.add(StatType.ATK_PERCENT, attackBonus);
    }
}

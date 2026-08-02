package model.weapon;

import model.entity.Weapon;
import model.stats.StatsContainer;
import model.type.StatType;
import model.type.WeaponType;

/**
 * Ferrous Shadow with Lv. 90 stats and refinement metadata for Unbending.
 *
 * <p>
 * Unbending activates below an HP threshold of 70/75/80/85/90%, granting
 * 30/35/40/45/50% Charged Attack DMG and increased interruption resistance.
 * The simulator has no player current-HP, incoming-damage, or interruption
 * state, and modeled characters remain at full HP. The condition is therefore
 * deterministically inactive and {@link #applyPassive} is an explicit no-op.
 */
public class FerrousShadow extends Weapon {
    private final int refinement;

    /** Constructs an R5 Ferrous Shadow. */
    public FerrousShadow() {
        this(5);
    }

    /**
     * Constructs Ferrous Shadow at a selected refinement.
     *
     * @param refinement refinement rank in the inclusive range 1-5
     * @throws IllegalArgumentException when refinement is outside 1-5
     */
    public FerrousShadow(int refinement) {
        super("Ferrous Shadow", new StatsContainer());
        if (refinement < 1 || refinement > 5) {
            throw new IllegalArgumentException(
                    "Weapon refinement must be between 1 and 5");
        }
        this.refinement = refinement;
        this.weaponType = WeaponType.CLAYMORE;
        getStats().set(StatType.BASE_ATK, 401.0);
        getStats().set(StatType.HP_PERCENT, 0.352);
    }

    /**
     * Returns this weapon's refinement rank for metadata and future HP-state support.
     *
     * @return refinement in the inclusive range 1-5
     */
    public int getRefinement() {
        return refinement;
    }

    /**
     * Leaves stats unchanged because modeled characters cannot enter Unbending's
     * below-threshold HP state.
     *
     * @param stats stats container that remains unchanged
     * @param currentTime simulation time in seconds
     */
    @Override
    public void applyPassive(StatsContainer stats, double currentTime) {
        // Unbending requires player current-HP and interruption state.
    }
}

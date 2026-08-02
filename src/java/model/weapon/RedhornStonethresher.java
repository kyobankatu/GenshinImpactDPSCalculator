package model.weapon;

import model.entity.Weapon;
import model.stats.StatsContainer;
import model.type.StatType;
import model.type.WeaponType;

/** Redhorn Stonethresher with final-DEF Normal and Charged damage conversion. */
public class RedhornStonethresher extends Weapon {
    private final int refinement;

    /** Constructs Redhorn Stonethresher at refinement rank five. */
    public RedhornStonethresher() {
        this(5);
    }

    /**
     * Constructs Redhorn Stonethresher at a selected refinement.
     *
     * @param refinement refinement rank in the inclusive range 1-5
     */
    public RedhornStonethresher(int refinement) {
        super("Redhorn Stonethresher", new StatsContainer());
        if (refinement < 1 || refinement > 5) {
            throw new IllegalArgumentException(
                    "Weapon refinement must be between 1 and 5");
        }
        this.refinement = refinement;
        this.weaponType = WeaponType.CLAYMORE;
        getStats().set(StatType.BASE_ATK, 542.0);
        getStats().set(StatType.CRIT_DMG, 0.882);
        getStats().set(
                StatType.DEF_PERCENT,
                0.21 + 0.07 * refinement);
        getStats().set(
                StatType.DEF_TO_NORMAL_CHARGED_FLAT_DMG_RATIO,
                0.30 + 0.10 * refinement);
    }

    /** Returns the selected refinement rank. */
    public int getRefinement() {
        return refinement;
    }
}

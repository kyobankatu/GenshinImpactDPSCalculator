package model.weapon;

import model.entity.Weapon;
import model.stats.StatsContainer;
import model.type.StatType;
import model.type.WeaponType;

/**
 * Base class for canonical weapons whose passive trigger cannot be represented.
 *
 * <p>The class preserves Lv. 90 metadata and refinement identity while making
 * the unsupported passive an explicit no-op. Concrete classes document the
 * missing runtime state and the canonical R1-R5 values needed when that state
 * becomes available.</p>
 */
public abstract class BoundaryInactiveWeapon extends Weapon {
    private final int refinement;

    /**
     * Constructs an inactive weapon with fixed metadata and refinement.
     *
     * @param name weapon display name
     * @param weaponType weapon category
     * @param baseAtk Lv. 90 base ATK
     * @param substatType Lv. 90 secondary stat type
     * @param substatValue Lv. 90 secondary stat value as a decimal
     * @param refinement refinement rank in the inclusive range 1-5
     */
    protected BoundaryInactiveWeapon(
            String name,
            WeaponType weaponType,
            double baseAtk,
            StatType substatType,
            double substatValue,
            int refinement) {
        super(name, new StatsContainer());
        if (refinement < 1 || refinement > 5) {
            throw new IllegalArgumentException(
                    "Weapon refinement must be between 1 and 5");
        }
        this.refinement = refinement;
        this.weaponType = weaponType;
        getStats().set(StatType.BASE_ATK, baseAtk);
        getStats().set(substatType, substatValue);
    }

    /**
     * Returns this weapon's refinement rank.
     *
     * @return refinement in the inclusive range 1-5
     */
    public final int getRefinement() {
        return refinement;
    }

    /**
     * Leaves stats unchanged because the concrete passive requires absent state.
     *
     * @param stats stats container intentionally left unchanged
     * @param currentTime simulation time in seconds
     */
    @Override
    public final void applyPassive(StatsContainer stats, double currentTime) {
        // The concrete class documents the runtime state required to activate it.
    }
}

package model.weapon;

import model.entity.Weapon;
import model.stats.StatsContainer;
import model.type.StatType;
import model.type.WeaponType;

/**
 * Shared Lv. 90 metadata and refinement handling for the Blackcliff weapon family.
 *
 * <p>
 * Press the Advantage grants ATK after defeating an enemy. The current simulator
 * models one immortal enemy and exposes no enemy-defeat callback, so the passive
 * cannot activate within the supported combat boundary. Refinement is retained
 * for API completeness while {@link #applyPassive} intentionally remains a no-op.
 */
public abstract class BlackcliffWeapon extends Weapon {
    private final int refinement;

    /**
     * Constructs one Lv. 90 Blackcliff family member.
     *
     * @param name weapon display name
     * @param weaponType weapon category
     * @param baseAtk Lv. 90 base ATK
     * @param critDamage Lv. 90 CRIT DMG substat as a decimal
     * @param refinement refinement rank in the inclusive range 1-5
     * @throws IllegalArgumentException when refinement is outside 1-5
     */
    protected BlackcliffWeapon(
            String name,
            WeaponType weaponType,
            double baseAtk,
            double critDamage,
            int refinement) {
        super(name, new StatsContainer());
        if (refinement < 1 || refinement > 5) {
            throw new IllegalArgumentException("Blackcliff refinement must be between 1 and 5");
        }
        this.refinement = refinement;
        this.weaponType = weaponType;
        getStats().set(StatType.BASE_ATK, baseAtk);
        getStats().set(StatType.CRIT_DMG, critDamage);
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
     * Leaves stats unchanged because the modeled immortal enemy cannot trigger
     * Press the Advantage.
     *
     * @param stats stats container that remains unchanged
     * @param currentTime simulation time in seconds
     */
    @Override
    public final void applyPassive(StatsContainer stats, double currentTime) {
        // Press the Advantage requires an enemy-defeat event, which is not modeled.
    }
}

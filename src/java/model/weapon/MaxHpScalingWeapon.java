package model.weapon;

import model.entity.Weapon;
import model.stats.StatsContainer;
import model.type.StatType;
import model.type.WeaponType;

/**
 * Shared implementation for weapons that convert Max HP into flat ATK.
 *
 * <p>The conversion ratio is stored as a typed derived stat. Therefore
 * {@link StatsContainer#getTotalAtk()} resolves it from final Max HP after
 * artifact and team HP sources have also been merged.</p>
 */
public abstract class MaxHpScalingWeapon extends Weapon {
    private final int refinement;
    private final double maxHpAttackConversion;

    /**
     * Constructs a Max-HP-scaling weapon with exact Lv. 90 metadata.
     *
     * @param name weapon display name
     * @param weaponType weapon category
     * @param baseAtk Lv. 90 base ATK
     * @param substatType Lv. 90 secondary stat type
     * @param substatValue Lv. 90 secondary stat value as a decimal
     * @param refinement refinement rank in the inclusive range 1-5
     * @param hpBonus unconditional HP bonus as a decimal
     * @param maxHpAttackConversion Max-HP-to-flat-ATK ratio as a decimal
     */
    protected MaxHpScalingWeapon(
            String name,
            WeaponType weaponType,
            double baseAtk,
            StatType substatType,
            double substatValue,
            int refinement,
            double hpBonus,
            double maxHpAttackConversion) {
        super(name, new StatsContainer());
        if (refinement < 1 || refinement > 5) {
            throw new IllegalArgumentException(
                    "Weapon refinement must be between 1 and 5");
        }
        this.refinement = refinement;
        this.maxHpAttackConversion = maxHpAttackConversion;
        this.weaponType = weaponType;
        getStats().set(StatType.BASE_ATK, baseAtk);
        getStats().set(substatType, substatValue);
        getStats().set(StatType.HP_PERCENT, hpBonus);
        getStats().set(
                StatType.MAX_HP_TO_ATK_FLAT_RATIO,
                maxHpAttackConversion);
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
     * Returns the unconditional Max-HP-to-flat-ATK conversion ratio.
     *
     * @return conversion ratio as a decimal
     */
    public final double getMaxHpAttackConversion() {
        return maxHpAttackConversion;
    }

}

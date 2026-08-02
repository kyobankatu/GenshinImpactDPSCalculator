package model.weapon;

import model.entity.Weapon;
import model.stats.StatsContainer;
import model.type.StatType;
import model.type.WeaponType;

/**
 * Shared implementation for weapons that convert Max HP into flat ATK.
 *
 * <p>The weapon's HP bonus is part of its ordinary stat container, so
 * {@link StatsContainer#getTotalHp()} resolves the conversion from base HP,
 * every supplied HP percentage, and supplied flat HP. The conversion is
 * unconditional and independent of simulation time.</p>
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

    /**
     * Applies flat ATK derived from the Max HP in the supplied stat view.
     *
     * @param stats assembled stats containing all HP sources to convert
     * @param currentTime simulation time in seconds; does not affect this passive
     */
    @Override
    public final void applyPassive(StatsContainer stats, double currentTime) {
        stats.add(StatType.ATK_FLAT,
                stats.getTotalHp() * maxHpAttackConversion);
    }
}

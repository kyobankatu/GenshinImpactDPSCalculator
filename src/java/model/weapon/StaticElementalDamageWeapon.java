package model.weapon;

import model.entity.Weapon;
import model.stats.StatsContainer;
import model.type.Element;
import model.type.StatType;
import model.type.WeaponType;

/**
 * Shared Lv. 90 metadata and refinement-aware elemental damage bonus.
 *
 * <p>The passive grants {@code 0.09 + 0.03 * refinement} to each of the
 * seven elemental damage stats. Physical damage and the generic all-damage
 * stat are intentionally excluded.</p>
 */
abstract class StaticElementalDamageWeapon extends Weapon {
    private final int refinement;
    private final double elementalDamageBonus;

    /**
     * Constructs a static elemental-damage weapon with exact Lv. 90 metadata.
     *
     * @param name weapon display name
     * @param weaponType weapon category
     * @param baseAtk Lv. 90 base ATK
     * @param substatType Lv. 90 secondary stat type
     * @param substatValue Lv. 90 secondary stat value as a decimal
     * @param refinement refinement rank in the inclusive range 1-5
     */
    protected StaticElementalDamageWeapon(
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
        this.elementalDamageBonus = 0.09 + 0.03 * refinement;
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
     * Applies the weapon's unconditional bonus to every elemental damage stat.
     *
     * @param stats stats container to mutate
     * @param currentTime simulation time in seconds
     */
    @Override
    public final void applyPassive(StatsContainer stats, double currentTime) {
        for (Element element : Element.values()) {
            if (element != Element.PHYSICAL) {
                stats.add(element.getBonusStatType(), elementalDamageBonus);
            }
        }
    }
}

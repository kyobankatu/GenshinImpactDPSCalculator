package model.weapon;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

import model.entity.Enemy;
import model.entity.TargetDependentWeaponEffect;
import model.entity.Weapon;
import model.stats.StatsContainer;
import model.type.Element;
import model.type.StatType;
import model.type.WeaponType;

/**
 * Shared live-Aura damage passive for weapons such as Dragon's Bane.
 *
 * <p>
 * Eligibility is evaluated for every impact from the target's current Aura.
 * The resulting all-DMG bonus mutates only the formula's per-hit stats copy and
 * therefore never enters structural, effective, or snapshotted owner stats.
 */
public abstract class TargetAuraDamageWeapon extends Weapon
        implements TargetDependentWeaponEffect {
    private final int refinement;
    private final double targetDamageBonus;
    private final Set<Element> eligibleElements;

    /**
     * Constructs one Lv. 90 target-Aura weapon.
     *
     * @param name weapon display name
     * @param weaponType weapon category
     * @param baseAtk Lv. 90 base ATK
     * @param secondaryStat secondary stat type
     * @param secondaryValue Lv. 90 secondary stat value
     * @param refinement refinement rank in the inclusive range 1-5
     * @param baseBonus value below R1, before per-refinement progression
     * @param bonusPerRefinement bonus added for each refinement rank
     * @param eligibleElements target Aura elements that activate the passive
     * @throws IllegalArgumentException for refinement outside 1-5 or no elements
     */
    protected TargetAuraDamageWeapon(
            String name,
            WeaponType weaponType,
            double baseAtk,
            StatType secondaryStat,
            double secondaryValue,
            int refinement,
            double baseBonus,
            double bonusPerRefinement,
            Element... eligibleElements) {
        super(name, new StatsContainer());
        if (refinement < 1 || refinement > 5) {
            throw new IllegalArgumentException("Weapon refinement must be between 1 and 5");
        }
        Objects.requireNonNull(eligibleElements, "eligibleElements");
        if (eligibleElements.length == 0) {
            throw new IllegalArgumentException("At least one eligible Aura element is required");
        }

        EnumSet<Element> elements = EnumSet.noneOf(Element.class);
        Collections.addAll(elements, eligibleElements);
        this.eligibleElements = Collections.unmodifiableSet(elements);
        this.refinement = refinement;
        this.targetDamageBonus = baseBonus + bonusPerRefinement * refinement;
        this.weaponType = weaponType;
        getStats().set(StatType.BASE_ATK, baseAtk);
        getStats().set(secondaryStat, secondaryValue);
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
     * Applies the damage bonus when any eligible target Aura is live at impact.
     *
     * @param stats per-hit stats container to mutate
     * @param target enemy being hit
     * @param currentTime simulation time in seconds used for Aura decay
     */
    @Override
    public final void applyTargetDependentStats(
            StatsContainer stats,
            Enemy target,
            double currentTime) {
        for (Element element : eligibleElements) {
            if (target.getAuraUnits(element, currentTime) > 0.0) {
                stats.add(StatType.DMG_BONUS_ALL, targetDamageBonus);
                return;
            }
        }
    }
}

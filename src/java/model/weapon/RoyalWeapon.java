package model.weapon;

import model.entity.Weapon;
import model.stats.StatsContainer;
import model.type.StatType;
import model.type.WeaponType;

/**
 * Shared Lv. 90 metadata and Focus refinement values for the Royal weapon family.
 *
 * <p>
 * Canonical Focus gains one CRIT Rate stack after dealing damage, up to five,
 * and removes every stack when the wielder deals a CRIT Hit. The simulator
 * resolves damage with average CRIT ({@code 1 + CR * CD}) and its post-damage
 * weapon callback does not expose whether a hit crit. Focus is therefore
 * intentionally inactive: accumulating stacks without observable resets would
 * overstate the passive, while a weapon-local expected-value approximation
 * would run before artifact and character CRIT Rate is assembled.
 */
public abstract class RoyalWeapon extends Weapon {
    private static final int FOCUS_MAX_STACKS = 5;

    private final int refinement;
    private final double focusCritRatePerStack;

    /**
     * Constructs one Lv. 90 Royal family member.
     *
     * @param name weapon display name
     * @param weaponType weapon category
     * @param baseAtk Lv. 90 base ATK
     * @param attackPercent Lv. 90 ATK substat as a decimal
     * @param refinement refinement rank in the inclusive range 1-5
     * @throws IllegalArgumentException when refinement is outside 1-5
     */
    protected RoyalWeapon(
            String name,
            WeaponType weaponType,
            double baseAtk,
            double attackPercent,
            int refinement) {
        super(name, new StatsContainer());
        if (refinement < 1 || refinement > 5) {
            throw new IllegalArgumentException(
                    "Royal weapon refinement must be between 1 and 5");
        }
        this.refinement = refinement;
        this.focusCritRatePerStack = 0.06 + 0.02 * refinement;
        this.weaponType = weaponType;
        getStats().set(StatType.BASE_ATK, baseAtk);
        getStats().set(StatType.ATK_PERCENT, attackPercent);
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
     * Returns the canonical Focus CRIT Rate granted by one stack.
     *
     * <p>
     * This value is metadata only while exact CRIT outcomes are absent from the
     * runtime; {@link #applyPassive} does not add it to simulated stats.
     *
     * @return per-stack CRIT Rate as a decimal, from 0.08 at R1 to 0.16 at R5
     */
    public final double getCanonicalFocusCritRatePerStack() {
        return focusCritRatePerStack;
    }

    /**
     * Returns the canonical Focus stack cap.
     *
     * @return maximum of five Focus stacks
     */
    public final int getCanonicalFocusMaxStacks() {
        return FOCUS_MAX_STACKS;
    }

    /**
     * Reports whether Focus participates in runtime stat assembly.
     *
     * @return {@code false} until damage resolution exposes exact CRIT outcomes
     */
    public final boolean isFocusRuntimeActive() {
        return false;
    }

    /**
     * Leaves stats unchanged because average-CRIT damage cannot drive exact
     * Focus stack resets.
     *
     * @param stats stats container intentionally left unchanged
     * @param currentTime simulation time in seconds
     */
    @Override
    public final void applyPassive(StatsContainer stats, double currentTime) {
        // Focus remains inactive until the runtime emits realized CRIT outcomes.
    }
}

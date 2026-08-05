package model.weapon;

import model.entity.Weapon;
import model.stats.StatsContainer;
import model.type.StatType;
import model.type.WeaponType;

/** Absolution's representable permanent CRIT DMG branch. */
public final class Absolution extends Weapon {
    private final int refinement;
    private final double passiveCritDamage;

    /** Constructs the weapon at refinement rank five. */
    public Absolution() {
        this(5);
    }

    /** Constructs the weapon at the selected refinement rank. */
    public Absolution(int refinement) {
        super("Absolution", new StatsContainer());
        if (refinement < 1 || refinement > 5) {
            throw new IllegalArgumentException(
                    "Absolution refinement must be between 1 and 5");
        }
        this.refinement = refinement;
        passiveCritDamage = 0.15 + 0.05 * refinement;
        weaponType = WeaponType.SWORD;
        getStats().set(StatType.BASE_ATK, 674.0);
        getStats().set(StatType.CRIT_DMG, 0.441);
    }

    /** Returns the selected refinement rank. */
    public int getRefinement() {
        return refinement;
    }

    /** Returns the permanent passive CRIT DMG bonus. */
    public double getPassiveCritDamage() {
        return passiveCritDamage;
    }

    /** Applies the permanent CRIT DMG branch. */
    @Override
    public void applyPassive(StatsContainer stats, double currentTime) {
        stats.add(StatType.CRIT_DMG, passiveCritDamage);
    }
}

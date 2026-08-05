package model.weapon;

import model.entity.Weapon;
import model.stats.StatsContainer;
import model.type.StatType;
import model.type.WeaponType;

/**
 * Tome of the Eternal Flow's representable static Aeon Wave branch.
 *
 * <p>The unconditional R1-R5 Max HP increase is applied. Charged Attack stacks
 * and Energy restoration require actual player HP changes, which the simulator
 * does not expose and therefore remain inactive.</p>
 */
public final class TomeOfTheEternalFlow extends Weapon {
    private final int refinement;
    private final double hpBonus;

    /** Constructs the weapon at refinement rank five. */
    public TomeOfTheEternalFlow() {
        this(5);
    }

    /** Constructs the weapon at the selected refinement rank. */
    public TomeOfTheEternalFlow(int refinement) {
        super("Tome of the Eternal Flow", new StatsContainer());
        if (refinement < 1 || refinement > 5) {
            throw new IllegalArgumentException(
                    "Tome refinement must be between 1 and 5");
        }
        this.refinement = refinement;
        hpBonus = 0.12 + 0.04 * refinement;
        weaponType = WeaponType.CATALYST;
        getStats().set(StatType.BASE_ATK, 542.0);
        getStats().set(StatType.CRIT_DMG, 0.882);
    }

    /** Returns the selected refinement rank. */
    public int getRefinement() {
        return refinement;
    }

    /** Returns the unconditional Aeon Wave HP bonus. */
    public double getHpBonus() {
        return hpBonus;
    }

    /** Applies only the unconditional R1-R5 Max HP increase. */
    @Override
    public void applyPassive(StatsContainer stats, double currentTime) {
        stats.add(StatType.HP_PERCENT, hpBonus);
    }
}

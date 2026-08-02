package model.weapon;

import model.entity.Character;
import model.entity.SimulatorInitializedWeaponEffect;
import model.entity.Weapon;
import model.stats.StatsContainer;
import model.type.StatType;
import model.type.WeaponType;
import simulation.CombatSimulator;

/**
 * Moonweaver's Dawn sword with maximum-Energy-tiered Burst damage.
 */
public class MoonweaversDawn extends Weapon
        implements SimulatorInitializedWeaponEffect {
    private final int refinement;
    private final double baseBurstBonus;
    private final double sixtyEnergyBonus;
    private final double fortyEnergyBonus;
    private Character owner;
    private CombatSimulator simulator;

    /** Constructs Moonweaver's Dawn at refinement rank five. */
    public MoonweaversDawn() {
        this(5);
    }

    /**
     * Constructs Moonweaver's Dawn at a selected refinement.
     *
     * @param refinement refinement rank in the inclusive range 1-5
     */
    public MoonweaversDawn(int refinement) {
        super("Moonweaver's Dawn", new StatsContainer());
        if (refinement < 1 || refinement > 5) {
            throw new IllegalArgumentException("Weapon refinement must be between 1 and 5");
        }
        this.refinement = refinement;
        this.baseBurstBonus = 0.15 + 0.05 * refinement;
        this.sixtyEnergyBonus = 0.12 + 0.04 * refinement;
        this.fortyEnergyBonus = 0.21 + 0.07 * refinement;
        this.weaponType = WeaponType.SWORD;
        getStats().set(StatType.BASE_ATK, 565.0);
        getStats().set(StatType.ATK_PERCENT, 0.276);
    }

    /**
     * Returns this weapon's refinement rank.
     *
     * @return refinement in the inclusive range 1-5
     */
    public int getRefinement() {
        return refinement;
    }

    /** Binds the owner whose maximum Energy selects Secret Silver's tier. */
    @Override
    public void initializeForSimulator(Character owner, CombatSimulator sim) {
        if (simulator != null && (this.owner != owner || simulator != sim)) {
            throw new IllegalStateException(
                    "Moonweaver's Dawn is already bound to another simulator");
        }
        this.owner = owner;
        this.simulator = sim;
    }

    /** Applies the base Burst bonus and at most one maximum-Energy tier. */
    @Override
    public void applyPassive(StatsContainer stats, double currentTime) {
        double bonus = baseBurstBonus;
        if (owner != null && owner.getMaxEnergy() <= 40.0) {
            bonus += fortyEnergyBonus;
        } else if (owner != null && owner.getMaxEnergy() <= 60.0) {
            bonus += sixtyEnergyBonus;
        }
        stats.add(StatType.BURST_DMG_BONUS, bonus);
    }
}

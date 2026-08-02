package model.weapon;

import model.entity.Character;
import model.entity.SimulatorInitializedWeaponEffect;
import model.entity.Weapon;
import model.stats.StatsContainer;
import model.type.StatType;
import model.type.WeaponType;
import simulation.CombatSimulator;

/**
 * Hamayumi bow with live full-Energy Normal and Charged Attack bonuses.
 */
public class Hamayumi extends Weapon implements SimulatorInitializedWeaponEffect {
    private final int refinement;
    private final double normalBonus;
    private final double chargedBonus;
    private Character owner;
    private CombatSimulator simulator;

    /** Constructs Hamayumi at refinement rank five. */
    public Hamayumi() {
        this(5);
    }

    /**
     * Constructs Hamayumi at a selected refinement.
     *
     * @param refinement refinement rank in the inclusive range 1-5
     */
    public Hamayumi(int refinement) {
        super("Hamayumi", new StatsContainer());
        if (refinement < 1 || refinement > 5) {
            throw new IllegalArgumentException("Weapon refinement must be between 1 and 5");
        }
        this.refinement = refinement;
        this.normalBonus = 0.12 + 0.04 * refinement;
        this.chargedBonus = 0.09 + 0.03 * refinement;
        this.weaponType = WeaponType.BOW;
        getStats().set(StatType.BASE_ATK, 454.0);
        getStats().set(StatType.ATK_PERCENT, 0.551);
    }

    /**
     * Returns this weapon's refinement rank.
     *
     * @return refinement in the inclusive range 1-5
     */
    public int getRefinement() {
        return refinement;
    }

    /** Binds the owner whose live Energy controls Full Draw. */
    @Override
    public void initializeForSimulator(Character owner, CombatSimulator sim) {
        if (simulator != null && (this.owner != owner || simulator != sim)) {
            throw new IllegalStateException("Hamayumi is already bound to another simulator");
        }
        this.owner = owner;
        this.simulator = sim;
    }

    /** Applies base bonuses and doubles them while the owner's Energy is full. */
    @Override
    public void applyPassive(StatsContainer stats, double currentTime) {
        double multiplier = owner != null
                && owner.getCurrentEnergy() >= owner.getMaxEnergy() ? 2.0 : 1.0;
        stats.add(StatType.NORMAL_ATTACK_DMG_BONUS, normalBonus * multiplier);
        stats.add(StatType.CHARGED_ATTACK_DMG_BONUS, chargedBonus * multiplier);
    }
}

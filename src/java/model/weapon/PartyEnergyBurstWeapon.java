package model.weapon;

import model.entity.Character;
import model.entity.SimulatorInitializedWeaponEffect;
import model.entity.Weapon;
import model.stats.StatsContainer;
import model.type.StatType;
import model.type.WeaponType;
import simulation.CombatSimulator;

/**
 * Shared Watatsumi Wavewalker passive based on combined party maximum Energy.
 */
public abstract class PartyEnergyBurstWeapon extends Weapon
        implements SimulatorInitializedWeaponEffect {
    private static final double BASE_BONUS_PER_ENERGY = 0.0009;
    private static final double BONUS_PER_ENERGY_PER_REFINEMENT = 0.0003;
    private static final double BASE_BONUS_CAP = 0.30;
    private static final double BONUS_CAP_PER_REFINEMENT = 0.10;

    private final int refinement;
    private final double bonusPerEnergy;
    private final double bonusCap;
    private CombatSimulator simulator;

    /**
     * Constructs one Lv. 90 Watatsumi Wavewalker family member.
     *
     * @param name weapon display name
     * @param weaponType weapon category
     * @param baseAtk Lv. 90 base ATK
     * @param atkPercent Lv. 90 ATK percentage substat
     * @param refinement refinement rank in the inclusive range 1-5
     * @throws IllegalArgumentException when refinement is outside 1-5
     */
    protected PartyEnergyBurstWeapon(
            String name,
            WeaponType weaponType,
            double baseAtk,
            double atkPercent,
            int refinement) {
        super(name, new StatsContainer());
        if (refinement < 1 || refinement > 5) {
            throw new IllegalArgumentException(
                    "Watatsumi Wavewalker refinement must be between 1 and 5");
        }
        this.refinement = refinement;
        this.bonusPerEnergy = BASE_BONUS_PER_ENERGY
                + BONUS_PER_ENERGY_PER_REFINEMENT * refinement;
        this.bonusCap = BASE_BONUS_CAP + BONUS_CAP_PER_REFINEMENT * refinement;
        this.weaponType = weaponType;
        getStats().set(StatType.BASE_ATK, baseAtk);
        getStats().set(StatType.ATK_PERCENT, atkPercent);
    }

    /**
     * Binds the weapon to the party whose maximum Energy it reads.
     *
     * @param owner owner equipped with this weapon
     * @param sim simulator containing the owner
     */
    @Override
    public final void initializeForSimulator(Character owner, CombatSimulator sim) {
        this.simulator = sim;
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
     * Applies the capped Burst damage bonus from combined party maximum Energy.
     *
     * @param stats stats container to mutate
     * @param currentTime simulation time in seconds
     */
    @Override
    public final void applyPassive(StatsContainer stats, double currentTime) {
        if (simulator == null) {
            return;
        }
        double partyMaxEnergy = simulator.getPartyMembers().stream()
                .mapToDouble(Character::getMaxEnergy)
                .sum();
        stats.add(StatType.BURST_DMG_BONUS,
                Math.min(bonusCap, partyMaxEnergy * bonusPerEnergy));
    }
}

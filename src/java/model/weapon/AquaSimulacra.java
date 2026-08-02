package model.weapon;

import model.entity.Weapon;
import model.stats.StatsContainer;
import model.type.StatType;
import model.type.WeaponType;

/**
 * Aqua Simulacra bow under the simulator's single-nearby-enemy combat model.
 */
public class AquaSimulacra extends Weapon {
    private final int refinement;

    /** Constructs Aqua Simulacra at refinement rank five. */
    public AquaSimulacra() {
        this(5);
    }

    /**
     * Constructs Aqua Simulacra at a selected refinement.
     *
     * @param refinement refinement rank in the inclusive range 1-5
     */
    public AquaSimulacra(int refinement) {
        super("Aqua Simulacra", new StatsContainer());
        if (refinement < 1 || refinement > 5) {
            throw new IllegalArgumentException("Weapon refinement must be between 1 and 5");
        }
        this.refinement = refinement;
        this.weaponType = WeaponType.BOW;
        getStats().set(StatType.BASE_ATK, 542.0);
        getStats().set(StatType.CRIT_DMG, 0.882);
        getStats().set(StatType.HP_PERCENT, 0.12 + 0.04 * refinement);
        getStats().set(StatType.DMG_BONUS_ALL, 0.15 + 0.05 * refinement);
    }

    /** Returns this weapon's refinement rank. */
    public int getRefinement() {
        return refinement;
    }
}

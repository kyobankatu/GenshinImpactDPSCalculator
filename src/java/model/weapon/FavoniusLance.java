package model.weapon;

import java.util.function.DoubleSupplier;

import model.type.WeaponType;

/**
 * Favonius Lance with Lv. 90 stats and the shared Windfall passive.
 */
public class FavoniusLance extends FavoniusWeapon {
    /**
     * Constructs an R5 Favonius Lance with stochastic Windfall draws.
     */
    public FavoniusLance() {
        this(5, Math::random);
    }

    /**
     * Constructs an R5 Favonius Lance with an explicit draw source.
     *
     * @param procDraw source used for joint CRIT and Windfall proc sampling
     */
    public FavoniusLance(DoubleSupplier procDraw) {
        this(5, procDraw);
    }

    /**
     * Constructs Favonius Lance at a selected refinement with stochastic draws.
     *
     * @param refinement refinement rank in the inclusive range 1-5
     */
    public FavoniusLance(int refinement) {
        this(refinement, Math::random);
    }

    /**
     * Constructs Favonius Lance with selected refinement and draw source.
     *
     * @param refinement refinement rank in the inclusive range 1-5
     * @param procDraw source used for joint CRIT and Windfall proc sampling
     */
    public FavoniusLance(int refinement, DoubleSupplier procDraw) {
        super("Favonius Lance", WeaponType.POLEARM, 565.0, 0.306, refinement, procDraw);
    }
}

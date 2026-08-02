package model.weapon;

import java.util.function.DoubleSupplier;

import model.type.WeaponType;

/**
 * Favonius Sword with Lv. 90 stats and the shared Windfall passive.
 */
public class FavoniusSword extends FavoniusWeapon {
    /**
     * Constructs an R5 Favonius Sword with stochastic Windfall draws.
     */
    public FavoniusSword() {
        this(5, Math::random);
    }

    /**
     * Constructs an R5 Favonius Sword with an explicit draw source.
     *
     * @param procDraw source used for joint CRIT and Windfall proc sampling
     */
    public FavoniusSword(DoubleSupplier procDraw) {
        this(5, procDraw);
    }

    /**
     * Constructs Favonius Sword at a selected refinement with stochastic draws.
     *
     * @param refinement refinement rank in the inclusive range 1-5
     */
    public FavoniusSword(int refinement) {
        this(refinement, Math::random);
    }

    /**
     * Constructs Favonius Sword with selected refinement and draw source.
     *
     * @param refinement refinement rank in the inclusive range 1-5
     * @param procDraw source used for joint CRIT and Windfall proc sampling
     */
    public FavoniusSword(int refinement, DoubleSupplier procDraw) {
        super("Favonius Sword", WeaponType.SWORD, 454.0, 0.613, refinement, procDraw);
    }
}

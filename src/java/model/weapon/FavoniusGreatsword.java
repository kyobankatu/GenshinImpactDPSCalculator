package model.weapon;

import java.util.function.DoubleSupplier;

import model.type.WeaponType;

/**
 * Favonius Greatsword with Lv. 90 stats and the shared Windfall passive.
 */
public class FavoniusGreatsword extends FavoniusWeapon {
    /**
     * Constructs an R5 Favonius Greatsword with stochastic Windfall draws.
     */
    public FavoniusGreatsword() {
        this(5, Math::random);
    }

    /**
     * Constructs an R5 Favonius Greatsword with an explicit draw source.
     *
     * @param procDraw source used for joint CRIT and Windfall proc sampling
     */
    public FavoniusGreatsword(DoubleSupplier procDraw) {
        this(5, procDraw);
    }

    /**
     * Constructs Favonius Greatsword at a selected refinement with stochastic draws.
     *
     * @param refinement refinement rank in the inclusive range 1-5
     */
    public FavoniusGreatsword(int refinement) {
        this(refinement, Math::random);
    }

    /**
     * Constructs Favonius Greatsword with selected refinement and draw source.
     *
     * @param refinement refinement rank in the inclusive range 1-5
     * @param procDraw source used for joint CRIT and Windfall proc sampling
     */
    public FavoniusGreatsword(int refinement, DoubleSupplier procDraw) {
        super("Favonius Greatsword", WeaponType.CLAYMORE, 454.0, 0.613, refinement, procDraw);
    }
}

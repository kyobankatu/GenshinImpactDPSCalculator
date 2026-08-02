package model.weapon;

import java.util.function.DoubleSupplier;

import model.type.WeaponType;

/**
 * Favonius Warbow with Lv. 90 stats and the shared Windfall passive.
 */
public class FavoniusWarbow extends FavoniusWeapon {
    /**
     * Constructs an R5 Favonius Warbow with stochastic Windfall draws.
     */
    public FavoniusWarbow() {
        this(5, Math::random);
    }

    /**
     * Constructs an R5 Favonius Warbow with an explicit draw source.
     *
     * @param procDraw source used for joint CRIT and Windfall proc sampling
     */
    public FavoniusWarbow(DoubleSupplier procDraw) {
        this(5, procDraw);
    }

    /**
     * Constructs Favonius Warbow at a selected refinement with stochastic draws.
     *
     * @param refinement refinement rank in the inclusive range 1-5
     */
    public FavoniusWarbow(int refinement) {
        this(refinement, Math::random);
    }

    /**
     * Constructs Favonius Warbow with selected refinement and draw source.
     *
     * @param refinement refinement rank in the inclusive range 1-5
     * @param procDraw source used for joint CRIT and Windfall proc sampling
     */
    public FavoniusWarbow(int refinement, DoubleSupplier procDraw) {
        super("Favonius Warbow", WeaponType.BOW, 454.0, 0.613, refinement, procDraw);
    }
}

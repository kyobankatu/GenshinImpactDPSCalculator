package model.weapon;

import java.util.function.DoubleSupplier;

import model.type.WeaponType;

/**
 * Favonius Codex catalyst with a CRIT-triggered particle generation passive.
 */
public class FavoniusCodex extends FavoniusWeapon {

    /**
     * Constructs Favonius Codex with Lv 90 base stats and stochastic proc draws.
     */
    public FavoniusCodex() {
        this(5, Math::random);
    }

    /**
     * Constructs Favonius Codex with an explicit Windfall draw source.
     *
     * @param procDraw source of chance values, where values below the wielder's
     *                 CRIT Rate trigger Windfall
     * @throws NullPointerException if {@code procDraw} is null
     */
    public FavoniusCodex(DoubleSupplier procDraw) {
        this(5, procDraw);
    }

    /**
     * Constructs Favonius Codex at a selected refinement with stochastic draws.
     *
     * @param refinement refinement rank in the inclusive range 1-5
     */
    public FavoniusCodex(int refinement) {
        this(refinement, Math::random);
    }

    /**
     * Constructs Favonius Codex with selected refinement and draw source.
     *
     * @param refinement refinement rank in the inclusive range 1-5
     * @param procDraw source used for joint CRIT and Windfall proc sampling
     */
    public FavoniusCodex(int refinement, DoubleSupplier procDraw) {
        super("Favonius Codex", WeaponType.CATALYST, 510.0, 0.459, refinement, procDraw);
    }
}

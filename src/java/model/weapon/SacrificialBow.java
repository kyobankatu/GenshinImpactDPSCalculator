package model.weapon;

import java.util.function.DoubleSupplier;

import model.type.StatType;
import model.type.WeaponType;

/**
 * Sacrificial Bow with Lv. 90 stats and the shared Composed passive.
 */
public class SacrificialBow extends SacrificialWeapon {
    /**
     * Constructs an R5 Sacrificial Bow with stochastic Composed draws.
     */
    public SacrificialBow() {
        this(5, Math::random);
    }

    /**
     * Constructs an R5 Sacrificial Bow with an explicit draw source.
     *
     * @param procDraw source used for Composed proc sampling
     */
    public SacrificialBow(DoubleSupplier procDraw) {
        this(5, procDraw);
    }

    /**
     * Constructs Sacrificial Bow at a selected refinement.
     *
     * @param refinement refinement rank in the inclusive range 1-5
     */
    public SacrificialBow(int refinement) {
        this(refinement, Math::random);
    }

    /**
     * Constructs Sacrificial Bow with selected refinement and draw source.
     *
     * @param refinement refinement rank in the inclusive range 1-5
     * @param procDraw source used for Composed proc sampling
     */
    public SacrificialBow(int refinement, DoubleSupplier procDraw) {
        super("Sacrificial Bow", WeaponType.BOW, 565.0,
                StatType.ENERGY_RECHARGE, 0.306, refinement, procDraw);
    }
}

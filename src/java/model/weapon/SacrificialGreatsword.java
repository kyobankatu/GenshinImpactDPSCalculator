package model.weapon;

import java.util.function.DoubleSupplier;

import model.type.StatType;
import model.type.WeaponType;

/**
 * Sacrificial Greatsword with Lv. 90 stats and the shared Composed passive.
 */
public class SacrificialGreatsword extends SacrificialWeapon {
    /**
     * Constructs an R5 Sacrificial Greatsword with stochastic Composed draws.
     */
    public SacrificialGreatsword() {
        this(5, Math::random);
    }

    /**
     * Constructs an R5 Sacrificial Greatsword with an explicit draw source.
     *
     * @param procDraw source used for Composed proc sampling
     */
    public SacrificialGreatsword(DoubleSupplier procDraw) {
        this(5, procDraw);
    }

    /**
     * Constructs Sacrificial Greatsword at a selected refinement.
     *
     * @param refinement refinement rank in the inclusive range 1-5
     */
    public SacrificialGreatsword(int refinement) {
        this(refinement, Math::random);
    }

    /**
     * Constructs Sacrificial Greatsword with selected refinement and draw source.
     *
     * @param refinement refinement rank in the inclusive range 1-5
     * @param procDraw source used for Composed proc sampling
     */
    public SacrificialGreatsword(int refinement, DoubleSupplier procDraw) {
        super("Sacrificial Greatsword", WeaponType.CLAYMORE, 565.0,
                StatType.ENERGY_RECHARGE, 0.306, refinement, procDraw);
    }
}

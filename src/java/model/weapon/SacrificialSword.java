package model.weapon;

import java.util.function.DoubleSupplier;

import model.type.StatType;
import model.type.WeaponType;

/**
 * Sacrificial Sword with Lv. 90 stats and the shared Composed passive.
 */
public class SacrificialSword extends SacrificialWeapon {

    /**
     * Constructs R5 Sacrificial Sword with stochastic Composed draws.
     */
    public SacrificialSword() {
        this(5, Math::random);
    }

    /**
     * Constructs R5 Sacrificial Sword with an explicit Composed draw source.
     *
     * @param procDraw source of chance values; values below 0.8 trigger Composed
     * @throws NullPointerException if {@code procDraw} is null
     */
    public SacrificialSword(DoubleSupplier procDraw) {
        this(5, procDraw);
    }

    /**
     * Constructs Sacrificial Sword at a selected refinement with stochastic draws.
     *
     * @param refinement refinement rank in the inclusive range 1-5
     */
    public SacrificialSword(int refinement) {
        this(refinement, Math::random);
    }

    /**
     * Constructs Sacrificial Sword with selected refinement and draw source.
     *
     * @param refinement refinement rank in the inclusive range 1-5
     * @param procDraw source used for Composed proc sampling
     */
    public SacrificialSword(int refinement, DoubleSupplier procDraw) {
        super("Sacrificial Sword", WeaponType.SWORD, 454.0,
                StatType.ENERGY_RECHARGE, 0.613, refinement, procDraw);
    }
}

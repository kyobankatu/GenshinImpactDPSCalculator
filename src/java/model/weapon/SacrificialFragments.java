package model.weapon;

import java.util.function.DoubleSupplier;

import model.type.StatType;
import model.type.WeaponType;

/**
 * Sacrificial Fragments with Lv. 90 stats and the shared Composed passive.
 */
public class SacrificialFragments extends SacrificialWeapon {
    /**
     * Constructs R5 Sacrificial Fragments with stochastic Composed draws.
     */
    public SacrificialFragments() {
        this(5, Math::random);
    }

    /**
     * Constructs R5 Sacrificial Fragments with an explicit draw source.
     *
     * @param procDraw source used for Composed proc sampling
     */
    public SacrificialFragments(DoubleSupplier procDraw) {
        this(5, procDraw);
    }

    /**
     * Constructs Sacrificial Fragments at a selected refinement.
     *
     * @param refinement refinement rank in the inclusive range 1-5
     */
    public SacrificialFragments(int refinement) {
        this(refinement, Math::random);
    }

    /**
     * Constructs Sacrificial Fragments with selected refinement and draw source.
     *
     * @param refinement refinement rank in the inclusive range 1-5
     * @param procDraw source used for Composed proc sampling
     */
    public SacrificialFragments(int refinement, DoubleSupplier procDraw) {
        super("Sacrificial Fragments", WeaponType.CATALYST, 454.0,
                StatType.ELEMENTAL_MASTERY, 221.0, refinement, procDraw);
    }
}

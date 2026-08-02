package model.weapon;

import java.util.function.DoubleSupplier;

import model.type.StatType;
import model.type.WeaponType;

/** Frostbearer catalyst with Lv. 90 stats and refinement-aware Frost Burial. */
public class Frostbearer extends FrostBurialWeapon {
    /** Constructs an R5 Frostbearer with stochastic proc draws. */
    public Frostbearer() {
        this(5, Math::random);
    }

    /** Constructs an R5 Frostbearer with an explicit proc source. */
    public Frostbearer(DoubleSupplier procDraw) {
        this(5, procDraw);
    }

    /** Constructs Frostbearer at a selected refinement. */
    public Frostbearer(int refinement) {
        this(refinement, Math::random);
    }

    /** Constructs Frostbearer at a selected refinement and proc source. */
    public Frostbearer(int refinement, DoubleSupplier procDraw) {
        super("Frostbearer", WeaponType.CATALYST, 510.0,
                StatType.ATK_PERCENT, 0.413, refinement, procDraw);
    }
}

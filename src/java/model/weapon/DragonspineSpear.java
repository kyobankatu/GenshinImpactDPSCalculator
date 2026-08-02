package model.weapon;

import java.util.function.DoubleSupplier;

import model.type.StatType;
import model.type.WeaponType;

/** Dragonspine Spear with Lv. 90 stats and refinement-aware Frost Burial. */
public class DragonspineSpear extends FrostBurialWeapon {
    /** Constructs an R5 Dragonspine Spear with stochastic proc draws. */
    public DragonspineSpear() {
        this(5, Math::random);
    }

    /** Constructs an R5 Dragonspine Spear with an explicit proc source. */
    public DragonspineSpear(DoubleSupplier procDraw) {
        this(5, procDraw);
    }

    /** Constructs Dragonspine Spear at a selected refinement. */
    public DragonspineSpear(int refinement) {
        this(refinement, Math::random);
    }

    /** Constructs Dragonspine Spear at a selected refinement and proc source. */
    public DragonspineSpear(int refinement, DoubleSupplier procDraw) {
        super("Dragonspine Spear", WeaponType.POLEARM, 454.0,
                StatType.PHYSICAL_DMG_BONUS, 0.690, refinement, procDraw);
    }
}

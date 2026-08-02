package model.weapon;

import java.util.function.DoubleSupplier;

import model.type.StatType;
import model.type.WeaponType;

/** Snow-Tombed Starsilver with Lv. 90 stats and Frost Burial. */
public class SnowTombedStarsilver extends FrostBurialWeapon {
    /** Constructs an R5 Snow-Tombed Starsilver with stochastic proc draws. */
    public SnowTombedStarsilver() {
        this(5, Math::random);
    }

    /** Constructs an R5 Snow-Tombed Starsilver with an explicit proc source. */
    public SnowTombedStarsilver(DoubleSupplier procDraw) {
        this(5, procDraw);
    }

    /** Constructs Snow-Tombed Starsilver at a selected refinement. */
    public SnowTombedStarsilver(int refinement) {
        this(refinement, Math::random);
    }

    /** Constructs Snow-Tombed Starsilver at a refinement and proc source. */
    public SnowTombedStarsilver(int refinement, DoubleSupplier procDraw) {
        super("Snow-Tombed Starsilver", WeaponType.CLAYMORE, 565.0,
                StatType.PHYSICAL_DMG_BONUS, 0.345, refinement, procDraw);
    }
}

package model.weapon;

import java.util.EnumSet;
import java.util.function.DoubleSupplier;

import model.type.ActionType;
import model.type.StatType;
import model.type.WeaponType;

/**
 * Fillet Blade with Lv. 90 stats and refinement-aware Gash procs.
 */
public class FilletBlade extends DirectDamageProcWeapon {
    /** Constructs an R5 Fillet Blade with stochastic proc draws. */
    public FilletBlade() {
        this(5, Math::random);
    }

    /**
     * Constructs an R5 Fillet Blade with an explicit proc source.
     *
     * @param procDraw source used for Gash sampling
     */
    public FilletBlade(DoubleSupplier procDraw) {
        this(5, procDraw);
    }

    /**
     * Constructs Fillet Blade at a selected refinement with stochastic draws.
     *
     * @param refinement refinement rank in the inclusive range 1-5
     */
    public FilletBlade(int refinement) {
        this(refinement, Math::random);
    }

    /**
     * Constructs Fillet Blade with a selected refinement and proc source.
     *
     * @param refinement refinement rank in the inclusive range 1-5
     * @param procDraw source used for Gash sampling
     */
    public FilletBlade(int refinement, DoubleSupplier procDraw) {
        super("Fillet Blade", WeaponType.SWORD, 401.0,
                StatType.ATK_PERCENT, 0.352, refinement,
                EnumSet.allOf(ActionType.class),
                0.5,
                16.0 - refinement,
                2.0 + 0.4 * refinement,
                procDraw);
    }
}

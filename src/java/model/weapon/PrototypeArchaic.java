package model.weapon;

import java.util.EnumSet;
import java.util.function.DoubleSupplier;

import model.type.ActionType;
import model.type.StatType;
import model.type.WeaponType;

/**
 * Prototype Archaic with Lv. 90 stats and refinement-aware Crush procs.
 */
public class PrototypeArchaic extends DirectDamageProcWeapon {
    /** Constructs an R5 Prototype Archaic with stochastic proc draws. */
    public PrototypeArchaic() {
        this(5, Math::random);
    }

    /**
     * Constructs an R5 Prototype Archaic with an explicit proc source.
     *
     * @param procDraw source used for Crush sampling
     */
    public PrototypeArchaic(DoubleSupplier procDraw) {
        this(5, procDraw);
    }

    /**
     * Constructs Prototype Archaic at a selected refinement with stochastic draws.
     *
     * @param refinement refinement rank in the inclusive range 1-5
     */
    public PrototypeArchaic(int refinement) {
        this(refinement, Math::random);
    }

    /**
     * Constructs Prototype Archaic with a selected refinement and proc source.
     *
     * @param refinement refinement rank in the inclusive range 1-5
     * @param procDraw source used for Crush sampling
     */
    public PrototypeArchaic(int refinement, DoubleSupplier procDraw) {
        super("Prototype Archaic", WeaponType.CLAYMORE, 565.0,
                StatType.ATK_PERCENT, 0.276, refinement,
                EnumSet.of(ActionType.NORMAL, ActionType.CHARGE),
                0.5,
                15.0,
                1.8 + 0.6 * refinement,
                procDraw);
    }
}

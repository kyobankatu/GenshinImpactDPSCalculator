package model.weapon;

import java.util.EnumSet;
import java.util.function.DoubleSupplier;

import model.type.ActionType;
import model.type.StatType;
import model.type.WeaponType;

/** Eye of Perception catalyst with an injected single-target Physical Bolt. */
public class EyeOfPerception extends DirectDamageProcWeapon {
    /** Constructs Eye of Perception at refinement rank five. */
    public EyeOfPerception() {
        this(5, Math::random);
    }

    /**
     * Constructs Eye of Perception at a selected refinement.
     *
     * @param refinement refinement rank in the inclusive range 1-5
     */
    public EyeOfPerception(int refinement) {
        this(refinement, Math::random);
    }

    /**
     * Constructs R5 Eye of Perception with an explicit proc draw source.
     *
     * @param procDraw source of values in the usual {@code [0, 1)} range
     */
    public EyeOfPerception(DoubleSupplier procDraw) {
        this(5, procDraw);
    }

    /**
     * Constructs Eye of Perception with selected refinement and proc draws.
     *
     * @param refinement refinement rank in the inclusive range 1-5
     * @param procDraw source of values in the usual {@code [0, 1)} range
     */
    public EyeOfPerception(int refinement, DoubleSupplier procDraw) {
        super(
                "Eye of Perception",
                WeaponType.CATALYST,
                454.0,
                StatType.ATK_PERCENT,
                0.551,
                refinement,
                EnumSet.of(ActionType.NORMAL, ActionType.CHARGE),
                0.50,
                13.0 - refinement,
                2.10 + 0.30 * refinement,
                procDraw);
    }
}

package model.weapon;

import java.util.EnumSet;

import model.type.ActionType;
import model.type.StatType;
import model.type.WeaponType;

/**
 * Halberd with Lv. 90 stats and refinement-aware Heavy procs.
 */
public class Halberd extends DirectDamageProcWeapon {
    /** Constructs an R5 Halberd. */
    public Halberd() {
        this(5);
    }

    /**
     * Constructs Halberd at a selected refinement.
     *
     * @param refinement refinement rank in the inclusive range 1-5
     */
    public Halberd(int refinement) {
        super("Halberd", WeaponType.POLEARM, 448.0,
                StatType.ATK_PERCENT, 0.235, refinement,
                EnumSet.of(ActionType.NORMAL),
                1.0,
                10.0,
                1.2 + 0.4 * refinement,
                () -> 0.0);
    }
}

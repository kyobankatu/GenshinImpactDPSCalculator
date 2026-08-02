package model.weapon;

import java.util.EnumSet;

import model.type.ActionType;
import model.type.StatType;
import model.type.WeaponType;

/**
 * Mitternachts Waltz with Lv. 90 stats and refinement-aware Evernight Duet.
 */
public class MitternachtsWaltz extends ReciprocalHitStatWeapon {
    /**
     * Constructs an R5 Mitternachts Waltz.
     */
    public MitternachtsWaltz() {
        this(5);
    }

    /**
     * Constructs Mitternachts Waltz at a selected refinement.
     *
     * @param refinement refinement rank in the inclusive range 1-5
     */
    public MitternachtsWaltz(int refinement) {
        super("Mitternachts Waltz", WeaponType.BOW, 510.0,
                StatType.PHYSICAL_DMG_BONUS, 0.517, refinement,
                window(
                        EnumSet.of(ActionType.NORMAL),
                        5.0,
                        0.15 + 0.05 * refinement,
                        StatType.SKILL_DMG_BONUS),
                window(
                        EnumSet.of(ActionType.SKILL),
                        5.0,
                        0.15 + 0.05 * refinement,
                        StatType.NORMAL_ATTACK_DMG_BONUS));
    }
}

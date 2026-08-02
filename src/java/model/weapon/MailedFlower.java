package model.weapon;

import model.type.StatType;
import model.type.WeaponType;

/**
 * Mailed Flower with Lv. 90 stats and refinement-aware Skill/reaction window.
 */
public class MailedFlower extends SkillHitOrReactionWindowWeapon {
    /** Constructs an R5 Mailed Flower. */
    public MailedFlower() {
        this(5);
    }

    /**
     * Constructs Mailed Flower at a selected refinement.
     *
     * @param refinement refinement rank in the inclusive range 1-5
     */
    public MailedFlower(int refinement) {
        super("Mailed Flower", WeaponType.CLAYMORE, 565.0,
                StatType.ELEMENTAL_MASTERY, 110.0, refinement, true, 8.0);
    }
}

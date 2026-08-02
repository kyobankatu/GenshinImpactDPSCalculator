package model.weapon;

import model.type.StatType;
import model.type.WeaponType;

/**
 * Fleuve Cendre Ferryman with static Skill CRIT and a Skill-use ER window.
 */
public class FleuveCendreFerryman extends SkillUseStatWeapon {
    /** Constructs Fleuve Cendre Ferryman at refinement rank five. */
    public FleuveCendreFerryman() {
        this(5);
    }

    /**
     * Constructs Fleuve Cendre Ferryman at a selected refinement.
     *
     * @param refinement refinement rank in the inclusive range 1-5
     */
    public FleuveCendreFerryman(int refinement) {
        super("Fleuve Cendre Ferryman", WeaponType.SWORD, 510.0,
                StatType.ENERGY_RECHARGE, 0.459, refinement,
                StatType.ENERGY_RECHARGE, 0.12, 0.04, 5.0);
        getStats().set(StatType.SKILL_CRIT_RATE, 0.06 + 0.02 * refinement);
    }
}

package model.weapon;

import java.util.EnumSet;

import model.type.StatType;
import model.type.WeaponType;
import simulation.action.CharacterActionKey;

/**
 * Shared refinement-aware owner stat window activated by Elemental Skill use.
 *
 * <p>
 * A later Skill use replaces the expiry boundary rather than stacking another
 * bonus. The effect is active in the half-open interval from cast time through
 * immediately before {@code cast time + duration}.
 */
public abstract class SkillUseStatWeapon extends ActionUseStatWeapon {

    /**
     * Constructs a Skill-use stat weapon.
     *
     * @param name weapon display name
     * @param weaponType weapon category
     * @param baseAtk Lv. 90 base ATK
     * @param secondaryStat secondary stat type
     * @param secondaryValue Lv. 90 secondary stat value
     * @param refinement refinement rank in the inclusive range 1-5
     * @param passiveStat stat granted during the active window
     * @param baseBonus value below R1 before refinement progression
     * @param bonusPerRefinement bonus added for each refinement rank
     * @param duration active window duration in seconds
     */
    protected SkillUseStatWeapon(
            String name,
            WeaponType weaponType,
            double baseAtk,
            StatType secondaryStat,
            double secondaryValue,
            int refinement,
            StatType passiveStat,
            double baseBonus,
            double bonusPerRefinement,
            double duration) {
        super(name, weaponType, baseAtk, secondaryStat, secondaryValue,
                refinement, EnumSet.of(CharacterActionKey.SKILL),
                passiveStat, baseBonus, bonusPerRefinement, duration);
    }
}

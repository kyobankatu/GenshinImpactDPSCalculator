package model.weapon;

import model.entity.ActionTriggeredWeaponEffect;
import model.entity.Character;
import model.entity.Weapon;
import model.stats.StatsContainer;
import model.type.StatType;
import model.type.WeaponType;
import simulation.CombatSimulator;
import simulation.action.CharacterActionKey;
import simulation.action.CharacterActionRequest;

/**
 * Shared refinement-aware owner stat window activated by Elemental Skill use.
 *
 * <p>
 * A later Skill use replaces the expiry boundary rather than stacking another
 * bonus. The effect is active in the half-open interval from cast time through
 * immediately before {@code cast time + duration}.
 */
public abstract class SkillUseStatWeapon extends Weapon
        implements ActionTriggeredWeaponEffect {
    private final int refinement;
    private final StatType passiveStat;
    private final double passiveValue;
    private final double duration;
    private double activeUntil = Double.NEGATIVE_INFINITY;

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
        super(name, new StatsContainer());
        if (refinement < 1 || refinement > 5) {
            throw new IllegalArgumentException("Weapon refinement must be between 1 and 5");
        }
        this.refinement = refinement;
        this.passiveStat = passiveStat;
        this.passiveValue = baseBonus + bonusPerRefinement * refinement;
        this.duration = duration;
        this.weaponType = weaponType;
        getStats().set(StatType.BASE_ATK, baseAtk);
        getStats().set(secondaryStat, secondaryValue);
    }

    /**
     * Returns this weapon's refinement rank.
     *
     * @return refinement in the inclusive range 1-5
     */
    public final int getRefinement() {
        return refinement;
    }

    /**
     * Refreshes the owner stat window on typed Elemental Skill use.
     *
     * @param user weapon owner
     * @param request requested action
     * @param sim active combat simulator
     */
    @Override
    public final void onAction(
            Character user,
            CharacterActionRequest request,
            CombatSimulator sim) {
        if (request.getKey() == CharacterActionKey.SKILL) {
            activeUntil = sim.getCurrentTime() + duration;
        }
    }

    /**
     * Applies the active Skill-use bonus before its exact expiry.
     *
     * @param stats stats container to mutate
     * @param currentTime simulation time in seconds
     */
    @Override
    public final void applyPassive(StatsContainer stats, double currentTime) {
        if (currentTime < activeUntil) {
            stats.add(passiveStat, passiveValue);
        }
    }
}

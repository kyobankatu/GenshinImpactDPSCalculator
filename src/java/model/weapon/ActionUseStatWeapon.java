package model.weapon;

import java.util.EnumSet;

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
 * Shared refinement-aware stat window activated by configured typed actions.
 */
public abstract class ActionUseStatWeapon extends Weapon
        implements ActionTriggeredWeaponEffect {
    private final int refinement;
    private final EnumSet<CharacterActionKey> triggerKeys;
    private final StatType passiveStat;
    private final double passiveValue;
    private final double duration;
    private double activeUntil = Double.NEGATIVE_INFINITY;

    /**
     * Constructs one typed action-use stat weapon.
     *
     * @param name weapon display name
     * @param weaponType weapon category
     * @param baseAtk Lv. 90 base ATK
     * @param secondaryStat secondary stat type
     * @param secondaryValue Lv. 90 secondary stat value
     * @param refinement refinement rank in the inclusive range 1-5
     * @param triggerKeys action keys that activate the window
     * @param passiveStat stat granted during the active window
     * @param baseBonus value below R1 before refinement progression
     * @param bonusPerRefinement bonus added for each refinement rank
     * @param duration active window duration in seconds
     */
    protected ActionUseStatWeapon(
            String name,
            WeaponType weaponType,
            double baseAtk,
            StatType secondaryStat,
            double secondaryValue,
            int refinement,
            EnumSet<CharacterActionKey> triggerKeys,
            StatType passiveStat,
            double baseBonus,
            double bonusPerRefinement,
            double duration) {
        super(name, new StatsContainer());
        if (refinement < 1 || refinement > 5) {
            throw new IllegalArgumentException("Weapon refinement must be between 1 and 5");
        }
        if (triggerKeys.isEmpty() || duration <= 0.0) {
            throw new IllegalArgumentException("Action-use window definition is invalid");
        }
        this.refinement = refinement;
        this.triggerKeys = EnumSet.copyOf(triggerKeys);
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
     * Refreshes the stat window on a configured typed action.
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
        if (triggerKeys.contains(request.getKey())) {
            activeUntil = sim.getCurrentTime() + duration;
        }
    }

    /**
     * Applies the active action-use bonus before its exact expiry.
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

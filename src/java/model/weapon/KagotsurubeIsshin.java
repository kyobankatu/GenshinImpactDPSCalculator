package model.weapon;

import java.util.EnumSet;

import model.entity.Character;
import model.entity.DamageTriggeredWeaponEffect;
import model.entity.Weapon;
import model.stats.StatsContainer;
import model.type.ActionType;
import model.type.Element;
import model.type.StatType;
import model.type.WeaponType;
import simulation.CombatSimulator;
import simulation.action.AttackAction;

/**
 * Kagotsurube Isshin sword with Hewing Gale and an ATK window.
 */
public class KagotsurubeIsshin extends Weapon
        implements DamageTriggeredWeaponEffect {
    private static final String PROC_NAME = "Kagotsurube Isshin Hewing Gale";
    private static final double DURATION = 8.0;
    private static final EnumSet<ActionType> ELIGIBLE_ACTIONS = EnumSet.of(
            ActionType.NORMAL, ActionType.CHARGE, ActionType.PLUNGE);

    private double activeUntil = Double.NEGATIVE_INFINITY;
    private double nextProcTime = Double.NEGATIVE_INFINITY;

    /** Constructs the fixed-refinement Kagotsurube Isshin. */
    public KagotsurubeIsshin() {
        super("Kagotsurube Isshin", new StatsContainer());
        this.weaponType = WeaponType.SWORD;
        getStats().set(StatType.BASE_ATK, 510.0);
        getStats().set(StatType.ATK_PERCENT, 0.413);
    }

    /** Returns the weapon's fixed refinement rank. */
    public int getRefinement() {
        return 1;
    }

    /** Triggers Hewing Gale and the ATK window at exact eight-second CT. */
    @Override
    public void onDamage(
            Character user,
            AttackAction action,
            double currentTime,
            CombatSimulator sim) {
        if (sim.getActiveCharacter() != user
                || action.getDamagePercent() <= 0.0
                || action.getName().equals(PROC_NAME)
                || !ELIGIBLE_ACTIONS.contains(action.getActionType())
                || currentTime < nextProcTime) {
            return;
        }
        activeUntil = currentTime + DURATION;
        nextProcTime = currentTime + DURATION;
        AttackAction proc = new AttackAction(
                PROC_NAME,
                1.8,
                Element.PHYSICAL,
                StatType.BASE_ATK,
                StatType.PHYSICAL_DMG_BONUS,
                0.0,
                ActionType.OTHER);
        sim.performActionWithoutTimeAdvance(user.getCharacterId(), proc);
    }

    /** Applies the 15% ATK bonus before exact expiry. */
    @Override
    public void applyPassive(StatsContainer stats, double currentTime) {
        if (currentTime < activeUntil) {
            stats.add(StatType.ATK_PERCENT, 0.15);
        }
    }
}

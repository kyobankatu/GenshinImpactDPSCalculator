package model.weapon;

import java.util.EnumSet;

import model.entity.Character;
import model.entity.DamageTriggeredWeaponEffect;
import model.entity.Weapon;
import model.stats.StatsContainer;
import model.type.ActionType;
import model.type.StatType;
import model.type.WeaponType;
import simulation.CombatSimulator;
import simulation.action.AttackAction;

/**
 * Shared positive-hit stacks with typed actions, CT, cap, and shared duration.
 */
public abstract class HitStackStatWeapon extends Weapon
        implements DamageTriggeredWeaponEffect {
    private final int refinement;
    private final EnumSet<ActionType> eligibleActions;
    private final double stackCooldown;
    private final double stackDuration;
    private final int maxStacks;
    private final StatType[] bonusStats;
    private final double[] bonusesPerStack;

    private int stackCount;
    private double expiration = Double.NEGATIVE_INFINITY;
    private double nextStackTime = Double.NEGATIVE_INFINITY;

    /**
     * Constructs one hit-stack weapon.
     *
     * @param name weapon display name
     * @param weaponType weapon category
     * @param baseAtk Lv. 90 base ATK
     * @param secondaryStat secondary stat type
     * @param secondaryValue Lv. 90 secondary stat value
     * @param refinement refinement rank in the inclusive range 1-5
     * @param eligibleActions direct action types that gain stacks
     * @param stackCooldown minimum seconds between stack gains
     * @param stackDuration shared stack duration in seconds
     * @param maxStacks maximum active stacks
     * @param bonusStats stats modified by every stack
     * @param bonusesPerStack values corresponding to {@code bonusStats}
     */
    protected HitStackStatWeapon(
            String name,
            WeaponType weaponType,
            double baseAtk,
            StatType secondaryStat,
            double secondaryValue,
            int refinement,
            EnumSet<ActionType> eligibleActions,
            double stackCooldown,
            double stackDuration,
            int maxStacks,
            StatType[] bonusStats,
            double[] bonusesPerStack) {
        super(name, new StatsContainer());
        if (refinement < 1 || refinement > 5) {
            throw new IllegalArgumentException(
                    "Hit-stack weapon refinement must be between 1 and 5");
        }
        if (eligibleActions.isEmpty()
                || stackCooldown < 0.0
                || stackDuration <= 0.0
                || maxStacks < 1
                || bonusStats.length == 0
                || bonusStats.length != bonusesPerStack.length) {
            throw new IllegalArgumentException("Hit-stack definition is invalid");
        }
        this.refinement = refinement;
        this.eligibleActions = EnumSet.copyOf(eligibleActions);
        this.stackCooldown = stackCooldown;
        this.stackDuration = stackDuration;
        this.maxStacks = maxStacks;
        this.bonusStats = bonusStats.clone();
        this.bonusesPerStack = bonusesPerStack.clone();
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
     * Applies every active shared-duration stack.
     *
     * @param stats stats container to mutate
     * @param currentTime simulation time in seconds
     */
    @Override
    public final void applyPassive(StatsContainer stats, double currentTime) {
        if (currentTime >= expiration) {
            return;
        }
        for (int i = 0; i < bonusStats.length; i++) {
            stats.add(bonusStats[i], bonusesPerStack[i] * stackCount);
        }
    }

    /**
     * Gains one stack after an eligible positive hit at or after exact CT.
     *
     * @param user weapon owner who dealt the hit
     * @param action resolved direct damage action
     * @param currentTime hit time in simulation seconds
     * @param sim active simulator
     */
    @Override
    public final void onDamage(
            Character user,
            AttackAction action,
            double currentTime,
            CombatSimulator sim) {
        if (action.getDamagePercent() <= 0.0
                || !eligibleActions.contains(action.getActionType())
                || currentTime < nextStackTime) {
            return;
        }
        if (currentTime >= expiration) {
            stackCount = 0;
        }
        stackCount = Math.min(maxStacks, stackCount + 1);
        expiration = currentTime + stackDuration;
        nextStackTime = currentTime + stackCooldown;
    }
}

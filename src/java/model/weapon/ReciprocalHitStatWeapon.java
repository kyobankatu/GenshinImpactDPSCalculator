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
 * Shared two-direction stat windows activated by configured direct-hit types.
 */
public abstract class ReciprocalHitStatWeapon extends Weapon
        implements DamageTriggeredWeaponEffect {
    private final int refinement;
    private final WindowSpec firstWindow;
    private final WindowSpec secondWindow;
    private double firstExpiration = Double.NEGATIVE_INFINITY;
    private double secondExpiration = Double.NEGATIVE_INFINITY;

    /**
     * Constructs a weapon with two independent hit-triggered stat windows.
     *
     * @param name weapon display name
     * @param weaponType weapon category
     * @param baseAtk Lv. 90 base ATK
     * @param secondaryStat secondary stat type
     * @param secondaryValue Lv. 90 secondary stat value
     * @param refinement refinement rank in the inclusive range 1-5
     * @param firstWindow first trigger and bonus definition
     * @param secondWindow second trigger and bonus definition
     * @throws IllegalArgumentException when refinement is outside 1-5
     */
    protected ReciprocalHitStatWeapon(
            String name,
            WeaponType weaponType,
            double baseAtk,
            StatType secondaryStat,
            double secondaryValue,
            int refinement,
            WindowSpec firstWindow,
            WindowSpec secondWindow) {
        super(name, new StatsContainer());
        if (refinement < 1 || refinement > 5) {
            throw new IllegalArgumentException(
                    "Reciprocal hit weapon refinement must be between 1 and 5");
        }
        this.refinement = refinement;
        this.firstWindow = firstWindow;
        this.secondWindow = secondWindow;
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
     * Creates one immutable trigger-window definition for a concrete weapon.
     *
     * @param triggers direct action types that activate the window
     * @param duration window duration in seconds
     * @param bonus stat value added while active
     * @param stats stats receiving the same bonus
     * @return immutable window definition
     */
    protected static WindowSpec window(
            EnumSet<ActionType> triggers,
            double duration,
            double bonus,
            StatType... stats) {
        return new WindowSpec(triggers, duration, bonus, stats);
    }

    /**
     * Applies both independently active stat windows.
     *
     * @param stats stats container to mutate
     * @param currentTime simulation time in seconds
     */
    @Override
    public final void applyPassive(StatsContainer stats, double currentTime) {
        if (currentTime < firstExpiration) {
            firstWindow.apply(stats);
        }
        if (currentTime < secondExpiration) {
            secondWindow.apply(stats);
        }
    }

    /**
     * Refreshes each matching window after a positive direct hit resolves.
     *
     * @param user weapon owner who dealt the hit
     * @param action resolved direct damage action
     * @param currentTime hit time in simulation seconds
     * @param sim active combat simulator
     */
    @Override
    public final void onDamage(
            Character user,
            AttackAction action,
            double currentTime,
            CombatSimulator sim) {
        if (action.getDamagePercent() <= 0.0) {
            return;
        }
        if (firstWindow.matches(action.getActionType())) {
            firstExpiration = currentTime + firstWindow.duration;
        }
        if (secondWindow.matches(action.getActionType())) {
            secondExpiration = currentTime + secondWindow.duration;
        }
    }

    /** Immutable trigger, duration, and equal-valued stat bundle. */
    protected static final class WindowSpec {
        private final EnumSet<ActionType> triggers;
        private final double duration;
        private final double bonus;
        private final StatType[] stats;

        private WindowSpec(
                EnumSet<ActionType> triggers,
                double duration,
                double bonus,
                StatType[] stats) {
            this.triggers = EnumSet.copyOf(triggers);
            this.duration = duration;
            this.bonus = bonus;
            this.stats = stats.clone();
        }

        private boolean matches(ActionType actionType) {
            return actionType != null && triggers.contains(actionType);
        }

        private void apply(StatsContainer target) {
            for (StatType stat : stats) {
                target.add(stat, bonus);
            }
        }
    }
}

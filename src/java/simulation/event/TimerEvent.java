package simulation.event;

import simulation.CombatSimulator;

/**
 * Contract for all time-driven events managed by the {@link CombatSimulator} event queue.
 * Events are stored in a priority queue ordered by {@link #getNextTickTime()} and are
 * fired by {@link CombatSimulator#advanceTime} as simulation time reaches each event's
 * scheduled tick. After ticking, an event is re-queued unless {@link #isFinished} returns
 * {@code true}.
 *
 * <p>Typical use-cases include periodic DoT (e.g. Electro-Charged ticks), buff expiry
 * notifications, and cooldown timers.
 */
public interface TimerEvent {
    /**
     * Returns the simulation time at which this event's next {@link #tick} should fire.
     *
     * @return next tick time in seconds
     */
    double getNextTickTime();

    /**
     * Executes the event's logic at the current simulation time.
     * Implementations may call {@link CombatSimulator#performActionWithoutTimeAdvance},
     * {@link CombatSimulator#recordDamage}, or modify game state as required.
     *
     * @param sim the {@link CombatSimulator} managing this event
     */
    void tick(CombatSimulator sim);

    /**
     * Returns whether this event has completed and should be removed from the queue.
     * Called by {@link CombatSimulator#advanceTime} after each tick.
     *
     * @param currentTime the current simulation time in seconds
     * @return {@code true} if the event is finished and should not be re-queued
     */
    boolean isFinished(double currentTime);
}

package simulation.event;

import simulation.CombatSimulator;

/**
 * Convenience base class for recurring {@link TimerEvent} implementations with a fixed interval.
 * Subclasses only need to implement {@link #onTick} and optionally call {@link #finish()} to
 * stop the event early. The next tick time is automatically advanced by {@link #interval}
 * after each tick unless the event is already finished.
 *
 * <p>Example use: a periodic buff refresh that fires every {@code 2.0} seconds until
 * {@link #finish()} is called.
 */
public abstract class SimpleTimerEvent implements TimerEvent {
    /** The simulation time at which the next tick will fire. */
    protected double nextTickTime;
    /** The fixed interval in seconds between successive ticks. */
    protected double interval;
    /** Whether the event has been marked complete via {@link #finish()}. */
    protected boolean finished = false;

    /**
     * Constructs a new recurring event.
     *
     * @param startTime the simulation time of the first tick
     * @param interval  the interval in seconds between consecutive ticks
     */
    public SimpleTimerEvent(double startTime, double interval) {
        this.nextTickTime = startTime;
        this.interval = interval;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public double getNextTickTime() {
        return nextTickTime;
    }

    /**
     * Returns {@code true} once {@link #finish()} has been called.
     *
     * @param currentTime current simulation time (unused by this implementation)
     * @return {@code true} if the event is finished
     */
    @Override
    public boolean isFinished(double currentTime) {
        return finished;
    }

    /**
     * Marks this event as complete. After this call, {@link #isFinished} will return
     * {@code true} and the event will not be re-queued after its next tick.
     */
    public void finish() {
        this.finished = true;
    }

    /**
     * Delegates to {@link #onTick} and then advances {@link #nextTickTime} by
     * {@link #interval} if the event has not been finished.
     *
     * @param sim the {@link CombatSimulator} managing this event
     */
    @Override
    public void tick(CombatSimulator sim) {
        onTick(sim);
        if (!finished) {
            nextTickTime += interval;
        }
    }

    /**
     * Subclass hook called on every tick. Implement this to define the event's periodic behavior.
     *
     * @param sim the {@link CombatSimulator} managing this event
     */
    public abstract void onTick(CombatSimulator sim);
}

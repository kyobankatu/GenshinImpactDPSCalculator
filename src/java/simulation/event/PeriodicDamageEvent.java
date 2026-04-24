package simulation.event;

import simulation.CombatSimulator;
import simulation.action.AttackAction;

/**
 * A {@link TimerEvent} that fires a repeating {@link AttackAction} at a fixed interval
 * for a defined duration, optionally running an additional callback on each tick.
 *
 * <p>Typical use-cases: field DoT effects such as Beidou's Stormbreaker lightning,
 * Fischl's Oz shots, Xiangling's Pyronado hits, or any ability that deals repeated
 * damage ticks while another character is on-field.
 *
 * <p>The event completes when {@code currentTime >= startTime + duration}.
 */
public class PeriodicDamageEvent implements TimerEvent {
    private String sourceName;
    private AttackAction tickAction;
    private double startTime;
    private double nextTickTime;
    private double interval;
    private double duration;

    private java.util.function.Consumer<CombatSimulator> onTick;

    /**
     * Constructs a periodic damage event without an additional callback.
     *
     * @param sourceName the name of the character owning this event (used for damage attribution)
     * @param tickAction the {@link AttackAction} to execute on each tick; may be {@code null}
     *                   to skip damage but still run the callback
     * @param startTime  the simulation time of the first tick
     * @param interval   the time in seconds between consecutive ticks
     * @param duration   the total duration in seconds; the event finishes when
     *                   {@code currentTime >= startTime + duration}
     */
    public PeriodicDamageEvent(String sourceName, AttackAction tickAction, double startTime, double interval,
            double duration) {
        this(sourceName, tickAction, startTime, interval, duration, null);
    }

    /**
     * Constructs a periodic damage event with an additional per-tick callback.
     *
     * @param sourceName the name of the character owning this event
     * @param tickAction the {@link AttackAction} to execute on each tick; may be {@code null}
     * @param startTime  the simulation time of the first tick
     * @param interval   the time in seconds between consecutive ticks
     * @param duration   the total duration in seconds
     * @param onTick     optional callback invoked after damage on each tick
     *                   (e.g. applying a DEF shred); may be {@code null}
     */
    public PeriodicDamageEvent(String sourceName, AttackAction tickAction, double startTime, double interval,
            double duration, java.util.function.Consumer<CombatSimulator> onTick) {
        this.sourceName = sourceName;
        this.tickAction = tickAction;
        this.startTime = startTime;
        this.interval = interval;
        this.duration = duration;
        this.onTick = onTick;
        this.nextTickTime = startTime;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public double getNextTickTime() {
        return nextTickTime;
    }

    /**
     * Executes the tick action via
     * {@link CombatSimulator#performActionWithoutTimeAdvance(String, AttackAction)},
     * then invokes the optional callback, and advances {@link #nextTickTime} by the interval.
     *
     * @param sim the {@link CombatSimulator} managing this event
     */
    @Override
    public void tick(CombatSimulator sim) {
        // Perform the action
        if (tickAction != null) {
            sim.performActionWithoutTimeAdvance(sourceName, tickAction);
        }

        // Run Callback (e.g. C1 Shred)
        if (onTick != null) {
            onTick.accept(sim);
        }

        // Update next tick
        nextTickTime += interval;
    }

    /**
     * Returns {@code true} when the current simulation time has reached or passed the
     * end of this event's duration ({@code startTime + duration}).
     *
     * @param currentTime the current simulation time in seconds
     * @return {@code true} if the event has expired
     */
    @Override
    public boolean isFinished(double currentTime) {
        return currentTime >= (startTime + duration);
    }
}

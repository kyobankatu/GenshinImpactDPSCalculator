package simulation.event;

import java.util.function.Consumer;

import simulation.CombatSimulator;
import simulation.action.AttackAction;

/**
 * A {@link TimerEvent} that fires a repeating {@link AttackAction} at a fixed interval
 * for a defined duration, optionally running callbacks before and after each tick.
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
    private boolean cancelled;

    private Consumer<CombatSimulator> preTick;
    private Consumer<CombatSimulator> onTick;

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
            double duration, Consumer<CombatSimulator> onTick) {
        this(sourceName, tickAction, startTime, interval, duration, null, onTick);
    }

    /**
     * Constructs a periodic damage event with optional callbacks around each tick action.
     *
     * @param sourceName the name of the character owning this event
     * @param tickAction the {@link AttackAction} to execute on each tick; may be {@code null}
     * @param startTime  the simulation time of the first tick
     * @param interval   the time in seconds between consecutive ticks
     * @param duration   the total duration in seconds
     * @param preTick    optional callback invoked immediately before the tick action; may be {@code null}
     * @param onTick     optional callback invoked after the tick action; may be {@code null}
     */
    public PeriodicDamageEvent(String sourceName, AttackAction tickAction, double startTime, double interval,
            double duration, Consumer<CombatSimulator> preTick, Consumer<CombatSimulator> onTick) {
        this.sourceName = sourceName;
        this.tickAction = tickAction;
        this.startTime = startTime;
        this.interval = interval;
        this.duration = duration;
        this.preTick = preTick;
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
     * invoking the optional callbacks immediately before and after it, then advances
     * {@link #nextTickTime} by the interval.
     *
     * @param sim the {@link CombatSimulator} managing this event
     */
    @Override
    public void tick(CombatSimulator sim) {
        if (cancelled) {
            return;
        }

        if (preTick != null) {
            preTick.accept(sim);
        }

        if (tickAction != null) {
            sim.performActionWithoutTimeAdvance(sourceName, tickAction);
        }

        if (onTick != null) {
            onTick.accept(sim);
        }

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
        return cancelled || currentTime >= (startTime + duration);
    }

    /**
     * Cancels all future damage and callbacks from this event.
     *
     * <p>Cancellation is idempotent. The simulation clock removes the event
     * when it next reaches the queue head.
     */
    public void cancel() {
        cancelled = true;
    }
}

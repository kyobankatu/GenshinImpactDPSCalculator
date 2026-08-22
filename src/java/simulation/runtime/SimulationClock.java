package simulation.runtime;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.PriorityQueue;

import simulation.CombatSimulator;
import simulation.event.TimerEvent;

/**
 * Owns simulation time progression and timer-event execution.
 *
 * <p>This extracts the event queue and current-time bookkeeping from
 * {@link CombatSimulator} so the simulator can delegate timeline management.
 */
public class SimulationClock {
    private final CombatSimulator sim;
    private final PriorityQueue<TimerEvent> events = new PriorityQueue<>(
            Comparator.comparingDouble(TimerEvent::getNextTickTime));
    private double currentTime = 0.0;
    private double rotationTime = 0.0;
    private final Deque<ExecutingEvent> executingEvents = new ArrayDeque<>();

    /** One re-entrant timer callback and its deferred target-local pause. */
    private static final class ExecutingEvent {
        private final TimerEvent event;
        private double targetHitlagDuration;

        private ExecutingEvent(TimerEvent event) {
            this.event = event;
        }
    }

    /**
     * Creates a clock bound to the given simulator.
     *
     * @param sim active simulator passed through to scheduled events
     */
    public SimulationClock(CombatSimulator sim) {
        this.sim = sim;
    }

    /**
     * Returns the current simulation time.
     *
     * @return current time in seconds
     */
    public double getCurrentTime() {
        return currentTime;
    }

    /**
     * Returns the end time of the latest completed action or timeline advance.
     *
     * @return rotation time in seconds
     */
    public double getRotationTime() {
        return rotationTime;
    }

    /**
     * Explicitly records the latest rotation time marker.
     *
     * @param rotationTime time in seconds
     */
    public void setRotationTime(double rotationTime) {
        this.rotationTime = rotationTime;
    }

    /**
     * Registers a timer event in chronological order.
     *
     * @param event event to add
     */
    public void registerEvent(TimerEvent event) {
        events.add(event);
    }

    /**
     * Shifts only timer events that declare target-local hitlag behavior.
     *
     * <p>The queue is rebuilt because mutating an event's next tick invalidates
     * the priority heap ordering.
     *
     * @param duration effective target freeze duration in seconds
     */
    public void applyTargetHitlag(double duration) {
        if (!Double.isFinite(duration) || duration <= 0.0) {
            return;
        }
        for (ExecutingEvent executing : executingEvents) {
            executing.targetHitlagDuration += duration;
        }
        List<TimerEvent> queuedEvents = new ArrayList<>(events);
        events.clear();
        for (TimerEvent event : queuedEvents) {
            event.applyTargetHitlag(duration);
            events.add(event);
        }
    }

    /**
     * Restores the clock to a previously captured time pair and drops all pending events.
     *
     * @param currentTime  time to restore
     * @param rotationTime rotation time to restore
     */
    public void restoreTime(double currentTime, double rotationTime) {
        this.currentTime = currentTime;
        this.rotationTime = rotationTime;
        executingEvents.clear();
        events.clear();
    }

    /**
     * Advances time and executes all due timer events in order.
     *
     * @param duration duration to advance in seconds
     */
    public void advanceTime(double duration) {
        double targetTime = currentTime + duration;

        while (!events.isEmpty() && events.peek().getNextTickTime() <= targetTime) {
            TimerEvent event = events.poll();

            double delta = event.getNextTickTime() - currentTime;
            if (delta > 0) {
                currentTime += delta;
            }

            if (sim.getEnemy() != null) {
                sim.getEnemy().updateAuras(currentTime);
            }
            ExecutingEvent executing = new ExecutingEvent(event);
            executingEvents.push(executing);
            try {
                event.tick(sim);
                event.applyTargetHitlag(executing.targetHitlagDuration);
            } finally {
                executingEvents.pop();
            }

            if (!event.isFinished(currentTime)) {
                events.add(event);
            }
        }

        if (currentTime < targetTime) {
            currentTime = targetTime;
        }
        rotationTime = currentTime;
    }
}

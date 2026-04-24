package simulation.runtime;

import java.util.Comparator;
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

            event.tick(sim);

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

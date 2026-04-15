package simulation.runtime;

import simulation.CombatSimulator;

/**
 * Owns simulator-facing access to transient reaction state such as
 * Electro-Charged scheduling flags and Thundercloud expiry.
 */
public class ReactionStateController {
    private final CombatSimulator sim;
    private final ReactionState reactionState;

    /**
     * Creates a controller bound to the given simulator and state holder.
     *
     * @param sim active simulator
     * @param reactionState mutable transient reaction state
     */
    public ReactionStateController(CombatSimulator sim, ReactionState reactionState) {
        this.sim = sim;
        this.reactionState = reactionState;
    }

    /**
     * Sets whether an EC-related timer is active.
     *
     * @param running new EC timer state
     */
    public void setEcTimerRunning(boolean running) {
        reactionState.setEcTimerRunning(running);
    }

    /**
     * Returns whether an EC-related timer is active.
     *
     * @return {@code true} if active
     */
    public boolean isEcTimerRunning() {
        return reactionState.isEcTimerRunning();
    }

    /**
     * Returns whether Thundercloud is active at the current time.
     *
     * @return {@code true} if active
     */
    public boolean isThundercloudActive() {
        return sim.getCurrentTime() < reactionState.getThundercloudEndTime();
    }

    /**
     * Returns the Thundercloud expiry time.
     *
     * @return expiry time in seconds
     */
    public double getThundercloudEndTime() {
        return reactionState.getThundercloudEndTime();
    }

    /**
     * Sets the Thundercloud expiry time.
     *
     * @param endTime expiry time in seconds
     */
    public void setThundercloudEndTime(double endTime) {
        reactionState.setThundercloudEndTime(endTime);
    }
}

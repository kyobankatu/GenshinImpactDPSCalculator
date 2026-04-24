package simulation.runtime;

/**
 * Holds transient simulation state for Electro-Charged and Thundercloud handling.
 */
public class ReactionState {
    private boolean ecTimerRunning = false;
    private double thundercloudEndTime = -1.0;

    /**
     * Returns whether an EC-related timer is active.
     *
     * @return {@code true} if active
     */
    public boolean isEcTimerRunning() {
        return ecTimerRunning;
    }

    /**
     * Sets whether an EC-related timer is active.
     *
     * @param running new active state
     */
    public void setEcTimerRunning(boolean running) {
        this.ecTimerRunning = running;
    }

    /**
     * Returns the Thundercloud expiry time.
     *
     * @return absolute simulation time in seconds
     */
    public double getThundercloudEndTime() {
        return thundercloudEndTime;
    }

    /**
     * Sets the Thundercloud expiry time.
     *
     * @param thundercloudEndTime absolute simulation time in seconds
     */
    public void setThundercloudEndTime(double thundercloudEndTime) {
        this.thundercloudEndTime = thundercloudEndTime;
    }
}

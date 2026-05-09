package mechanics.rl.bridge;

/**
 * Binary protocol constants shared by the Java rollout service and the Python learner.
 */
public final class BatchProtocol {
    public static final int VERSION = 10;

    public static final int CMD_HELLO = 1;
    public static final int CMD_CREATE_RUNNER = 2;
    public static final int CMD_RESET_RUNNER = 3;
    public static final int CMD_STEP_RUNNER = 4;
    public static final int CMD_CLOSE_RUNNER = 5;
    public static final int CMD_SHUTDOWN = 6;
    /** Branch rollout: runner_id, snapshot_id, K, H, gamma → Q_MC per action (NaN if invalid). */
    public static final int CMD_BRANCH_ROLLOUT = 7;
    /** Release all unconsumed vine snapshots for the runner; bool ack returned. */
    public static final int CMD_RELEASE_SNAPSHOTS = 8;

    private BatchProtocol() {
    }
}

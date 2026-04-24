package mechanics.rl.bridge;

/**
 * Binary protocol constants shared by the Java rollout service and the Python learner.
 */
public final class BatchProtocol {
    public static final int VERSION = 1;

    public static final int CMD_HELLO = 1;
    public static final int CMD_CREATE_RUNNER = 2;
    public static final int CMD_RESET_RUNNER = 3;
    public static final int CMD_STEP_RUNNER = 4;
    public static final int CMD_CLOSE_RUNNER = 5;
    public static final int CMD_SHUTDOWN = 6;

    private BatchProtocol() {
    }
}

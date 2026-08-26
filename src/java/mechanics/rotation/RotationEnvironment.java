package mechanics.rotation;

/**
 * Narrow environment contract consumed by rotation search implementations.
 */
public interface RotationEnvironment extends AutoCloseable {
    /** Starts a fresh deterministic episode. */
    RotationStep reset();

    /** Executes one policy action. */
    RotationStep step(int actionId);

    /** Returns the most recent persistent state. */
    RotationStep current();

    /** Captures simulator and environment-side state for a search branch. */
    Snapshot snapshot();

    /** Restores a branch captured by this environment and reset generation. */
    RotationStep restore(Snapshot snapshot);

    /** Returns simulator action calls required to restore this branch. */
    default int restoreSimulatorCallCost(Snapshot snapshot) {
        return 0;
    }

    /** Returns this environment's immutable scenario. */
    RotationScenario scenario();

    /** Releases this environment and rejects subsequent access. */
    @Override
    void close();

    /** Opaque search snapshot metadata. */
    interface Snapshot {
        String getScenarioFingerprint();

        long getStateHash();
    }
}

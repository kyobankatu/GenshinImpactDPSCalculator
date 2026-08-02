package model.entity;

/**
 * Optional capability for weapons with mutable runtime state that must roll back.
 */
public interface SnapshotAwareWeaponEffect {
    /** Marker for an immutable captured weapon state. */
    interface State {
    }

    /**
     * Captures the weapon's complete mutable runtime state.
     *
     * @return immutable state owned by this weapon implementation
     */
    State captureWeaponState();

    /**
     * Restores a state previously returned by this weapon implementation.
     *
     * @param state captured weapon state
     */
    void restoreWeaponState(State state);
}

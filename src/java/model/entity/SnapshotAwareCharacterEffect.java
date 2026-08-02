package model.entity;

import simulation.CombatSimulator;

/**
 * Opt-in contract for character-owned state not covered by core snapshots.
 *
 * <p>Implementations capture immutable value state only. Restore runs after
 * the simulator clock, core character state, and all buff lists are restored,
 * allowing a character to reconstruct its own future events without cloning
 * arbitrary timer-event objects.
 */
public interface SnapshotAwareCharacterEffect {
    /** Marker for immutable character-owned snapshot payloads. */
    interface State {
    }

    /**
     * Captures immutable character-owned state.
     *
     * @return non-null state payload; use an explicit empty state when inactive
     */
    State captureCharacterState();

    /**
     * Checks payload ownership before any simulator state is mutated.
     *
     * @param state candidate payload
     * @return {@code true} when this implementation can restore the payload
     */
    boolean acceptsCharacterState(State state);

    /**
     * Restores character-owned state and any future events it requires.
     *
     * @param state state produced by this character implementation
     * @param simulator restored simulator receiving reconstructed events
     */
    void restoreCharacterState(State state, CombatSimulator simulator);
}

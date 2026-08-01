package model.entity;

import simulation.CombatSimulator;

/**
 * Capability for artifacts that initialize runtime state when their owner joins
 * a simulator party.
 *
 * <p>The simulator invokes this once after party insertion. Implementations can
 * distinguish an owner who starts on field from one who already satisfies an
 * off-field condition without adding lifecycle methods to every artifact set.
 */
public interface SimulatorInitializedArtifactEffect {
    /**
     * Initializes simulator-bound artifact state.
     *
     * @param owner owner equipped with this artifact
     * @param sim simulator that now contains the owner
     * @param startsActive whether the owner is currently the active character
     */
    void initializeForSimulator(Character owner, CombatSimulator sim, boolean startsActive);
}

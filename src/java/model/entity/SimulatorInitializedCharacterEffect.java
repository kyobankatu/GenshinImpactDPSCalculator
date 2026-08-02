package model.entity;

import simulation.CombatSimulator;

/**
 * Character lifecycle hook invoked when a character is added to a simulator.
 *
 * <p>Implement this contract for character mechanics that must register
 * listeners or bind simulator-owned context before the first party action.
 */
public interface SimulatorInitializedCharacterEffect {
    /**
     * Initializes character-owned simulator state.
     *
     * @param sim simulator receiving the character
     */
    void initializeForSimulator(CombatSimulator sim);
}

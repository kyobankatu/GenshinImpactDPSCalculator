package model.entity;

import simulation.CombatSimulator;

/**
 * Artifact behavior hook for character switch events.
 */
public interface SwitchAwareArtifact {
    /**
     * Applies artifact behavior when the owner becomes the active character.
     */
    void onSwitchIn(CombatSimulator sim, Character owner);

    /**
     * Applies artifact behavior when the owner leaves the active field.
     */
    void onSwitchOut(CombatSimulator sim, Character owner);
}

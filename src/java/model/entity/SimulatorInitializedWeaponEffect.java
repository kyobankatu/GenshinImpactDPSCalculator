package model.entity;

import simulation.CombatSimulator;

/**
 * Capability for weapons that register time-driven behavior when equipped.
 *
 * <p>The simulator invokes this once after the owner has joined the party, so
 * implementations can schedule events without adding optional lifecycle hooks
 * to every {@link Weapon}.</p>
 */
public interface SimulatorInitializedWeaponEffect {
    /**
     * Registers this weapon's simulator-bound behavior.
     *
     * @param owner owner equipped with this weapon
     * @param sim simulator that now contains the owner
     */
    void initializeForSimulator(Character owner, CombatSimulator sim);
}

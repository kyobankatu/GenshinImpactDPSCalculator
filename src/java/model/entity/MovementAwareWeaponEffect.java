package model.entity;

import simulation.CombatSimulator;

/** Weapon capability that consumes explicit simulated movement distance. */
public interface MovementAwareWeaponEffect {
    /**
     * Records distance completed by the owner at the current simulator time.
     *
     * @param owner equipped owner
     * @param distanceMeters non-negative movement distance in meters
     * @param currentTime simulator time at which movement completed
     * @param sim active simulator
     */
    void onMovement(
            Character owner,
            double distanceMeters,
            double currentTime,
            CombatSimulator sim);
}

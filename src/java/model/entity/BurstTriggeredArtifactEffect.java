package model.entity;

import simulation.CombatSimulator;

/**
 * Capability for artifact sets whose 4pc bonus triggers on Elemental Burst use
 * (e.g. Noblesse Oblige 4pc).
 */
public interface BurstTriggeredArtifactEffect {
    /**
     * Invoked when the equipping character casts their Elemental Burst.
     *
     * @param sim active combat simulator
     */
    void onBurst(CombatSimulator sim);
}

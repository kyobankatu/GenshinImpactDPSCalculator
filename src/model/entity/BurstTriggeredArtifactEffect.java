package model.entity;

import simulation.CombatSimulator;

public interface BurstTriggeredArtifactEffect {
    void onBurst(CombatSimulator sim);
}

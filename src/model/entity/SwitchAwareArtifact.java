package model.entity;

import simulation.CombatSimulator;

public interface SwitchAwareArtifact {
    void onSwitchIn(CombatSimulator sim, Character owner);

    void onSwitchOut(CombatSimulator sim, Character owner);
}

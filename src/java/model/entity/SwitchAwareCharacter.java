package model.entity;

import simulation.CombatSimulator;

/**
 * Capability for characters with explicit switch-out behavior.
 */
public interface SwitchAwareCharacter {
    void onSwitchOut(CombatSimulator sim);
}

package model.entity;

import simulation.CombatSimulator;

/**
 * Capability for weapons with passives that react when the owner leaves the field.
 */
public interface SwitchAwareWeaponEffect {
    void onSwitchOut(Character user, CombatSimulator sim);
}

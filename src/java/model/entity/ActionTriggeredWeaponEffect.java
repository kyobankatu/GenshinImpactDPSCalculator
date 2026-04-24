package model.entity;

import simulation.CombatSimulator;
import simulation.action.CharacterActionRequest;

/**
 * Capability for weapons with passives that trigger when the owner performs an action.
 */
public interface ActionTriggeredWeaponEffect {
    void onAction(Character user, CharacterActionRequest request, CombatSimulator sim);
}

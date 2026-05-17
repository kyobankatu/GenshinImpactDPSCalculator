package model.entity;

import simulation.CombatSimulator;
import simulation.action.CharacterActionRequest;

/**
 * Capability for weapons with passives that trigger when the owner performs an action.
 */
public interface ActionTriggeredWeaponEffect {
    /**
     * Invoked when the equipping character performs an action.
     *
     * @param user    weapon owner
     * @param request action request describing the action just performed
     * @param sim     active combat simulator
     */
    void onAction(Character user, CharacterActionRequest request, CombatSimulator sim);
}

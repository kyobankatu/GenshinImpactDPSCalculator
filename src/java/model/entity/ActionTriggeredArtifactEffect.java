package model.entity;

import simulation.CombatSimulator;
import simulation.action.CharacterActionRequest;

/**
 * Capability for artifact sets with passives that trigger when the owner
 * performs an action.
 */
public interface ActionTriggeredArtifactEffect {
    /**
     * Invoked after action gates pass and before the character resolves the
     * action.
     *
     * @param user artifact owner
     * @param request action request describing the action being performed
     * @param sim active combat simulator
     */
    void onAction(Character user, CharacterActionRequest request, CombatSimulator sim);
}

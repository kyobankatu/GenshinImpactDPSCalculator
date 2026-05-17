package model.entity;

import mechanics.reaction.ReactionResult;
import simulation.CombatSimulator;

/**
 * Artifact behavior hook for elemental reaction events.
 */
public interface ReactionAwareArtifact {
    /**
     * Applies artifact behavior after a reaction resolves.
     */
    void onReaction(CombatSimulator sim, ReactionResult result, Character triggerCharacter, Character owner);
}

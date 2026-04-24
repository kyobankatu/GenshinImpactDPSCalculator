package model.entity;

import mechanics.reaction.ReactionResult;
import simulation.CombatSimulator;

public interface ReactionAwareArtifact {
    void onReaction(CombatSimulator sim, ReactionResult result, Character triggerCharacter, Character owner);
}

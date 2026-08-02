package model.entity;

import mechanics.reaction.ReactionResult;
import simulation.CombatSimulator;

/**
 * Capability for weapons that react only to actual owner-triggered reactions.
 *
 * <p>Derived reaction damage notifications remain available to general
 * observers but do not enter this equipment-specific callback.</p>
 */
public interface ElementalReactionTriggeredWeaponEffect {
    /**
     * Handles one actual elemental reaction.
     *
     * @param result resolved reaction
     * @param source character attributed with the reaction
     * @param time reaction time in seconds
     * @param sim active simulator
     */
    void onElementalReaction(
            ReactionResult result,
            Character source,
            double time,
            CombatSimulator sim);
}

package model.entity;

import simulation.CombatSimulator;
import simulation.action.AttackAction;

/**
 * Capability for artifact sets whose 4pc bonus triggers when the wearer deals
 * damage (e.g. damage-based stack counters).
 */
public interface DamageTriggeredArtifactEffect {
    /**
     * Invoked after a damage instance from the wearer has been resolved.
     *
     * @param sim    active combat simulator
     * @param action attack action that produced the damage
     * @param damage final post-mitigation damage value
     * @param owner  artifact wearer
     */
    void onDamage(CombatSimulator sim, AttackAction action, double damage, Character owner);
}

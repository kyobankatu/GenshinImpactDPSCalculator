package model.entity;

import simulation.CombatSimulator;
import simulation.action.AttackAction;

/**
 * Capability for weapons with passives that trigger when the owner deals damage.
 */
public interface DamageTriggeredWeaponEffect {
    void onDamage(Character user, AttackAction action, double currentTime, CombatSimulator sim);
}

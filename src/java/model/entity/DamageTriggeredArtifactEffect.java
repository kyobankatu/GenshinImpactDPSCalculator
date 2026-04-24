package model.entity;

import simulation.CombatSimulator;
import simulation.action.AttackAction;

public interface DamageTriggeredArtifactEffect {
    void onDamage(CombatSimulator sim, AttackAction action, double damage, Character owner);
}

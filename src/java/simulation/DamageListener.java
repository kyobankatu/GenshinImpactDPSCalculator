package simulation;

import model.entity.Character;
import simulation.action.AttackAction;

/**
 * Observer for resolved direct damage from any simulator attack path.
 *
 * <p>Unlike {@link ActionListener}, this listener also receives attacks resolved
 * through no-time-advance paths such as periodic and coordinated effects.
 */
@FunctionalInterface
public interface DamageListener {
    /**
     * Handles one resolved direct-damage result.
     *
     * @param actor character responsible for the damage
     * @param action resolved attack action
     * @param damage final direct damage dealt by the action
     * @param time simulation time in seconds
     */
    void onDamage(Character actor, AttackAction action, double damage, double time);
}

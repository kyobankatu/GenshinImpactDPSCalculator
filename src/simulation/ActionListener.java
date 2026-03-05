package simulation;

import simulation.action.AttackAction;

/**
 * Observer interface for monitoring discrete attack actions within the combat simulation.
 * Implementations are registered via {@link CombatSimulator#addListener} and are notified
 * each time a character executes an {@link AttackAction} through
 * {@link CombatSimulator#performAction(String, AttackAction)}.
 */
public interface ActionListener {
    /**
     * Invoked after a character performs an {@link AttackAction} and before simulation time
     * is advanced for that action's animation duration.
     *
     * @param actorName the name of the character who performed the action
     * @param action    the {@link AttackAction} that was executed
     * @param time      the simulation time (in seconds) at which the action occurred
     */
    void onAction(String actorName, AttackAction action, double time);
}

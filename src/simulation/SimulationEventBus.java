package simulation;

import java.util.Collection;

import mechanics.reaction.ReactionResult;
import model.entity.Character;
import model.type.Element;
import simulation.action.AttackAction;

/**
 * Abstraction for simulation event subscription and dispatch.
 *
 * <p>The simulator emits action, particle, and reaction events through this API
 * rather than depending directly on a concrete listener container.
 */
public interface SimulationEventBus {
    /**
     * Registers an action listener.
     *
     * @param listener listener to register
     */
    void addActionListener(ActionListener listener);

    /**
     * Registers a particle listener.
     *
     * @param listener listener to register
     */
    void addParticleListener(ParticleListener listener);

    /**
     * Registers a reaction listener.
     *
     * @param listener listener to register
     */
    void addReactionListener(CombatSimulator.ReactionListener listener);

    /**
     * Dispatches an executed action event.
     *
     * @param actorName acting character name
     * @param action    executed attack action
     * @param time      simulation time in seconds
     */
    void notifyAction(String actorName, AttackAction action, double time);

    /**
     * Dispatches a particle-generation event.
     *
     * @param element particle element
     * @param count   number of particles generated
     * @param time    simulation time in seconds
     */
    void notifyParticle(Element element, double count, double time);

    /**
     * Dispatches a reaction event and forwards it to artifact listeners as needed.
     *
     * @param result       resolved reaction result
     * @param trigger      triggering character
     * @param time         simulation time in seconds
     * @param sim          active simulator
     * @param partyMembers current party members whose artifacts may react
     */
    void notifyReaction(ReactionResult result, Character trigger, double time,
            CombatSimulator sim, Collection<Character> partyMembers);
}

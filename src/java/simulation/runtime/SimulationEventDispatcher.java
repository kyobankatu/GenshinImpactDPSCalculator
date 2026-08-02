package simulation.runtime;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import mechanics.reaction.ReactionResult;
import model.entity.ArtifactSet;
import model.entity.Character;
import model.entity.ElementalReactionTriggeredWeaponEffect;
import model.entity.ReactionAwareArtifact;
import model.type.Element;
import simulation.ActionListener;
import simulation.CombatSimulator;
import simulation.DamageListener;
import simulation.IndirectDamageListener;
import simulation.ParticleListener;
import simulation.SimulationEventBus;
import simulation.action.AttackAction;

/**
 * Owns listener registration and notification for simulation-side events.
 */
public class SimulationEventDispatcher implements SimulationEventBus {
    private final List<ActionListener> actionListeners = new ArrayList<>();
    private final List<DamageListener> damageListeners = new ArrayList<>();
    private final List<IndirectDamageListener> indirectDamageListeners =
            new ArrayList<>();
    private final List<ParticleListener> particleListeners = new ArrayList<>();
    private final List<CombatSimulator.ReactionListener> reactionListeners = new ArrayList<>();
    private final List<ElementalReactionTriggeredWeaponEffect>
            elementalReactionWeaponEffects = new ArrayList<>();

    /**
     * Registers an action listener that will be notified after attack actions are executed.
     *
     * @param listener the listener to register
     */
    @Override
    public void addActionListener(ActionListener listener) {
        actionListeners.add(listener);
    }

    /**
     * Registers a listener for resolved direct damage.
     *
     * @param listener the listener to register
     */
    @Override
    public void addDamageListener(DamageListener listener) {
        damageListeners.add(listener);
    }

    /** Registers a listener for resolved indirect damage. */
    @Override
    public void addIndirectDamageListener(IndirectDamageListener listener) {
        indirectDamageListeners.add(listener);
    }

    /**
     * Registers a particle listener that will be notified when particles are emitted.
     *
     * @param listener the listener to register
     */
    @Override
    public void addParticleListener(ParticleListener listener) {
        particleListeners.add(listener);
    }

    /**
     * Registers a reaction listener that will be notified when elemental reactions occur.
     *
     * @param listener the listener to register
     */
    @Override
    public void addReactionListener(CombatSimulator.ReactionListener listener) {
        reactionListeners.add(listener);
    }

    /** Registers a weapon listener for actual elemental reactions only. */
    public void addElementalReactionTriggeredWeaponEffect(
            ElementalReactionTriggeredWeaponEffect effect) {
        elementalReactionWeaponEffects.add(effect);
    }

    /**
     * Notifies all registered action listeners of an executed attack action.
     *
     * @param actor the acting character
     * @param action    the executed attack action
     * @param time      simulation time in seconds
     */
    @Override
    public void notifyAction(Character actor, AttackAction action, double time) {
        for (ActionListener listener : new ArrayList<>(actionListeners)) {
            listener.onAction(actor, action, time);
        }
    }

    /**
     * Notifies all registered listeners of resolved direct damage.
     *
     * @param actor acting character
     * @param action resolved attack action
     * @param damage final direct damage
     * @param time simulation time in seconds
     */
    @Override
    public void notifyDamage(Character actor, AttackAction action, double damage, double time) {
        for (DamageListener listener : new ArrayList<>(damageListeners)) {
            listener.onDamage(actor, action, damage, time);
        }
    }

    /** Notifies all registered listeners of resolved indirect damage. */
    @Override
    public void notifyIndirectDamage(Character owner, double damage, double time) {
        for (IndirectDamageListener listener
                : new ArrayList<>(indirectDamageListeners)) {
            listener.onIndirectDamage(owner, damage, time);
        }
    }

    /**
     * Notifies all registered particle listeners of a particle-generation event.
     *
     * @param element the particle element
     * @param count   the number of particles generated
     * @param time    simulation time in seconds
     */
    @Override
    public void notifyParticle(Element element, double count, double time) {
        for (ParticleListener listener : particleListeners) {
            listener.onParticle(element, count, time);
        }
    }

    /**
     * Notifies reaction listeners and forwards the reaction event to all artifact
     * sets equipped by current party members.
     *
     * @param result      the resolved reaction result
     * @param trigger     the character whose attack triggered the reaction
     * @param time        simulation time in seconds
     * @param sim         the active simulator
     * @param partyMembers current party members whose artifacts may react globally
     */
    @Override
    public void notifyReaction(
            ReactionResult result,
            Character trigger,
            double time,
            CombatSimulator sim,
            Collection<Character> partyMembers) {
        for (ElementalReactionTriggeredWeaponEffect effect
                : elementalReactionWeaponEffects) {
            effect.onElementalReaction(result, trigger, time, sim);
        }
        notifyReactionObservers(result, trigger, time, sim, partyMembers);
    }

    /**
     * Notifies existing reaction observers while excluding equipment sigil hooks.
     */
    public void notifyDerivedReaction(
            ReactionResult result,
            Character trigger,
            double time,
            CombatSimulator sim,
            Collection<Character> partyMembers) {
        notifyReactionObservers(result, trigger, time, sim, partyMembers);
    }

    private void notifyReactionObservers(
            ReactionResult result,
            Character trigger,
            double time,
            CombatSimulator sim,
            Collection<Character> partyMembers) {
        for (CombatSimulator.ReactionListener listener : reactionListeners) {
            listener.onReaction(result, trigger, time, sim);
        }

        for (Character member : partyMembers) {
            if (member.getArtifacts() == null) {
                continue;
            }
            for (ArtifactSet artifact : member.getArtifacts()) {
                if (artifact instanceof ReactionAwareArtifact) {
                    ((ReactionAwareArtifact) artifact).onReaction(sim, result, trigger, member);
                }
            }
        }
    }
}

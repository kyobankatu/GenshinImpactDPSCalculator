package simulation;

import model.type.Element;

/**
 * Observer interface for monitoring elemental particle generation events.
 * Implementations are registered via {@link CombatSimulator#addParticleListener} and are
 * notified whenever particles are emitted through
 * {@link CombatSimulator#notifyParticle(Element, double)}.
 * Particles feed into the {@code EnergyManager} to fill character energy.
 */
public interface ParticleListener {
    /**
     * Invoked when elemental particles are generated during the simulation.
     *
     * @param element the element of the generated particles (determines off-field ER efficiency)
     * @param count   the number of particles generated
     * @param time    the simulation time (in seconds) at which the particles were generated
     */
    void onParticle(Element element, double count, double time);
}

package mechanics.energy;

import simulation.CombatSimulator;
import model.type.Element;

/**
 * Handles particle/orb energy distribution and scheduled enemy particle drops
 * for the combat simulation.
 *
 * <p>Energy calculation follows the standard Genshin Impact particle mechanic:
 * <pre>
 *   Energy gained = count * baseValue * rangeMult * (1 + ER%)
 * </pre>
 * where:
 * <ul>
 *   <li>{@code baseValue} is determined by the particle's element and
 *       {@link ParticleType} (same-element vs. different-element values).</li>
 *   <li>{@code rangeMult} is {@code 1.0} for the active character. Off-field
 *       characters use {@code 0.8}, {@code 0.7}, or {@code 0.6} in a party of
 *       two, three, or four or more members, respectively.</li>
 * </ul>
 */
public class EnergyManager {

    /**
     * Distributes elemental particles or orbs to every party member.
     *
     * <p>For neutral (Physical / null element) particles the base value is
     * {@code 2.0} for particles and {@code 6.0} for orbs, irrespective of
     * element matching.  For elemental particles the base value is read from
     * {@link ParticleType#getValue(boolean)}: {@code 3.0} same-element /
     * {@code 1.0} different-element for a PARTICLE, and {@code 9.0} /
     * {@code 3.0} for an ORB.
     *
     * <p>After distributing energy the simulator's
     * {@link CombatSimulator#notifyParticle} hook is called so passive listeners
     * (e.g. Raiden passive) can react.
     *
     * @param particleElement element of the particle; {@code null} or
     *                        {@link Element#PHYSICAL} is treated as neutral
     * @param count           number of particles/orbs generated
     * @param type            {@link ParticleType#PARTICLE} or {@link ParticleType#ORB}
     * @param sim             the running combat simulator
     */
    public static void distributeParticles(Element particleElement, double count, ParticleType type,
            CombatSimulator sim) {
        sim.getEnergyDistributor().distributeParticles(particleElement, count, type);
    }

    /**
     * Distributes flat energy to all party members.
     *
     * <p>Flat energy bypasses the ER multiplier and the active/off-field
     * range penalty; every character receives exactly {@code amount} energy.
     *
     * @param amount flat energy to give each character
     * @param sim    the running combat simulator
     */
    public static void distributeFlatEnergy(double amount, CombatSimulator sim) {
        sim.getEnergyDistributor().distributeFlatEnergy(amount);
    }

    /**
     * Schedules the standard KQM enemy particle drops as a
     * {@link simulation.event.TimerEvent} on the simulator.
     *
     * <p>The legacy no-argument form uses a 21-second rotation. Prefer
     * {@link #scheduleKQMSEnemyParticles(CombatSimulator, double)} when the
     * scenario declares its cycle duration.
     *
     * @param sim the simulator on which the event is registered
     */
    public static void scheduleKQMSEnemyParticles(CombatSimulator sim) {
        sim.getEnergyDistributor().scheduleKQMSEnemyParticles();
    }

    /**
     * Schedules KQMS enemy Energy at exactly three Clear Orbs per 90 seconds,
     * distributed once at the midpoint of each repeated rotation.
     *
     * @param sim             simulator on which the event is registered
     * @param rotationSeconds positive duration of one repeated rotation
     */
    public static void scheduleKQMSEnemyParticles(
            CombatSimulator sim,
            double rotationSeconds) {
        sim.getEnergyDistributor().scheduleKQMSEnemyParticles(rotationSeconds);
    }
}

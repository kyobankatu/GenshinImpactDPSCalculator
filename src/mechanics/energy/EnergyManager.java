package mechanics.energy;

import simulation.CombatSimulator;
import model.entity.Character;
import model.type.Element;
import model.type.StatType;

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
 *   <li>{@code rangeMult} is {@code 1.0} for the active character and
 *       {@code 0.6} for off-field characters in a 4-member party.</li>
 * </ul>
 */
public class EnergyManager {

    // Standard Genshin constants
    private static final double NEUTRAL_SAME = 2.0;
    private static final double NEUTRAL_DIFF = 2.0;
    private static final double OFF_FIELD_PENALTY = 0.6; // For 4-char party

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
        try {
            System.out.println("   [Energy] Distributing " + count + " " + particleElement + " particles...");
            Character activeChar = sim.getPartyMembers().stream()
                    .filter(c -> c == sim.getActiveCharacter()) // Assuming sim exposes active directly or we filter
                    .findFirst().orElse(null);

            if (activeChar == null) {
                System.out.println("   [Energy] No active character found!");
                return;
            }

            for (Character c : sim.getPartyMembers()) {
                boolean isActive = (c == activeChar);
                boolean isSameElement = (c.getElement() == particleElement);

                double baseValue = 0.0;

                if (particleElement == null || particleElement == Element.PHYSICAL) {
                    // Neutral Logic
                    double neutralBase = 2.0;
                    double sizeMult = (type == ParticleType.ORB) ? 3.0 : 1.0;
                    baseValue = neutralBase * sizeMult;
                } else {
                    // Elemental Particle
                    baseValue = type.getValue(isSameElement);
                }

                double rangeMuliplier = isActive ? 1.0 : OFF_FIELD_PENALTY;

                // Final Energy = Count * Base * Range * ER%
                double er = c.getEffectiveStats(sim.getCurrentTime()).get(StatType.ENERGY_RECHARGE);
                double particleBase = count * baseValue * rangeMuliplier;

                c.receiveParticleEnergy(particleBase, er);
            }
            // Notify Simulator listeners (e.g. Raiden Passive)
            sim.notifyParticle(particleElement, count);
        } catch (Exception e) {
            System.out.println("[ERROR] Crash in EnergyManager:");
            e.printStackTrace();
        }
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
        for (Character c : sim.getPartyMembers()) {
            c.receiveFlatEnergy(amount);
        }
    }

    /**
     * Schedules the standard KQM enemy particle drops as a
     * {@link simulation.event.TimerEvent} on the simulator.
     *
     * <p>KQM Standard: 3 Neutral Orbs per 90 s rotation (~1 Orb every 30 s).
     * Scaled to a ~21 s window this equates to roughly 4.2 energy, approximated
     * here as 2 Neutral Particles (2 × 2 energy = 4 energy) delivered at
     * {@code T=10 s}.
     *
     * @param sim the simulator on which the event is registered
     */
    public static void scheduleKQMSEnemyParticles(CombatSimulator sim) {
        sim.registerEvent(new simulation.event.TimerEvent() {
            double[] dropTimes = { 10.0 };
            int idx = 0;

            @Override
            public void tick(CombatSimulator s) {
                distributeParticles(model.type.Element.PHYSICAL, 2.0, ParticleType.PARTICLE, s);
                idx++;
            }

            @Override
            public boolean isFinished(double t) {
                return idx >= dropTimes.length;
            }

            @Override
            public double getNextTickTime() {
                return idx < dropTimes.length ? dropTimes[idx] : -1;
            }
        });
    }
}

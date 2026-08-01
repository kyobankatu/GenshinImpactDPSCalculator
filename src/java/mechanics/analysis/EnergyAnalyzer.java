package mechanics.analysis;

import simulation.CombatSimulator;
import model.entity.Character;
import model.type.CharacterId;

/**
 * Post-simulation utility that calculates the minimum Energy Recharge (ER)
 * required for each party member to cast their burst every rotation.
 *
 * <p>The analysis uses a cyclic start-full/end-full model. Each requested
 * Burst closes one energy-income window. The analyzer replays those windows
 * with the character's energy cap and post-Burst carry, joining the final tail
 * to the income before the first request to find a sustainable target.
 */
public class EnergyAnalyzer {
    private static final double MAX_ER_SENTINEL = 9.99;
    private static final double ENERGY_EPSILON = 1e-9;
    private static final int ER_SEARCH_STEPS = 60;

    /**
     * Analyzes energy generation after a simulation run and returns required ER
     * map.
     *
     * <p>The chronological replay starts immediately after the preloaded first
     * Burst, preserves unused energy between requests, applies the energy cap,
     * and requires the next cycle to return to at least the same state. Binary
     * search finds the smallest feasible multiplier. Characters with no
     * requested Burst receive {@code 1.0}; an infeasible trace receives the
     * existing {@code 9.99} sentinel.
     *
     * @param sim the completed combat simulator whose characters hold energy tracking data
     * @return map from character id to the minimum ER multiplier (e.g. {@code 1.30} for 130%)
     */
    public static java.util.Map<CharacterId, Double> calculateERRequirements(CombatSimulator sim) {
        java.util.Map<CharacterId, Double> erMap = new java.util.HashMap<>();

        for (Character c : sim.getPartyMembers()) {
            java.util.List<double[]> windows = c.getBurstEnergyWindows();
            double requiredER = 1.0;

            if (!windows.isEmpty()) {
                double recordedParticles = 0.0;
                double recordedFlat = 0.0;
                for (double[] window : windows) {
                    recordedParticles += window[0];
                    recordedFlat += window[1];
                }

                double tailParticles = Math.max(0.0, c.getTotalParticleEnergy() - recordedParticles);
                double tailFlat = Math.max(0.0, c.getTotalFlatEnergy() - recordedFlat);
                requiredER = calculateRequiredER(
                        windows, tailParticles, tailFlat, c.getMaxEnergy());
            }

            erMap.put(c.getCharacterId(), requiredER);
        }
        return erMap;
    }

    /**
     * Finds the minimum ER that can replay all Burst windows indefinitely.
     *
     * @param windows requested Burst windows in chronological order
     * @param tailParticles particle energy after the final request
     * @param tailFlat fixed energy after the final request
     * @param maxEnergy character energy cap
     * @return required ER multiplier, or 9.99 when the trace is infeasible
     */
    private static double calculateRequiredER(
            java.util.List<double[]> windows,
            double tailParticles,
            double tailFlat,
            double maxEnergy) {
        if (canSustainBurstCycle(windows, tailParticles, tailFlat, maxEnergy, 1.0)) {
            return 1.0;
        }
        if (!canSustainBurstCycle(windows, tailParticles, tailFlat, maxEnergy, MAX_ER_SENTINEL)) {
            return MAX_ER_SENTINEL;
        }

        double lower = 1.0;
        double upper = MAX_ER_SENTINEL;
        for (int i = 0; i < ER_SEARCH_STEPS; i++) {
            double candidate = (lower + upper) / 2.0;
            if (canSustainBurstCycle(windows, tailParticles, tailFlat, maxEnergy, candidate)) {
                upper = candidate;
            } else {
                lower = candidate;
            }
        }
        return upper;
    }

    /**
     * Replays one cyclic sequence while preserving energy carry between Bursts.
     *
     * @param windows requested Burst windows in chronological order
     * @param tailParticles particle energy after the final request
     * @param tailFlat fixed energy after the final request
     * @param maxEnergy character energy cap
     * @param er candidate Energy Recharge multiplier
     * @return true when every request and the cyclic boundary are sustainable
     */
    private static boolean canSustainBurstCycle(
            java.util.List<double[]> windows,
            double tailParticles,
            double tailFlat,
            double maxEnergy,
            double er) {
        double[] firstWindow = windows.get(0);
        double firstCost = firstWindow[2];
        if (maxEnergy + ENERGY_EPSILON < firstCost) {
            return false;
        }

        double initialEnergy = maxEnergy - firstCost;
        double energy = initialEnergy;
        for (int i = 1; i < windows.size(); i++) {
            double[] window = windows.get(i);
            energy = Math.min(maxEnergy, energy + window[0] * er + window[1]);
            if (energy + ENERGY_EPSILON < window[2]) {
                return false;
            }
            energy -= window[2];
        }

        energy = Math.min(maxEnergy,
                energy + (tailParticles + firstWindow[0]) * er + tailFlat + firstWindow[1]);
        if (energy + ENERGY_EPSILON < firstCost) {
            return false;
        }
        energy -= firstCost;
        return energy + ENERGY_EPSILON >= initialEnergy;
    }
}

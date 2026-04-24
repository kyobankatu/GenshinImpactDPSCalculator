package mechanics.analysis;

import simulation.CombatSimulator;
import model.entity.Character;

/**
 * Post-simulation utility that calculates the minimum Energy Recharge (ER)
 * required for each party member to cast their burst every rotation.
 *
 * <p>The analysis uses a "start full, end full" model: the first burst is
 * considered pre-loaded, so the total particle energy collected must cover
 * all {@code N} burst costs (the initial pre-load cancels out with the
 * end-of-rotation refill, leaving {@code N * cost} as the net requirement).
 */
public class EnergyAnalyzer {

    /**
     * Analyzes energy generation after a simulation run and returns required ER
     * map.
     *
     * <p>For each character the formula applied is:
     * <pre>
     *   requiredER = max(1.0, (totalBurstCost - totalFlatEnergy) / totalParticleEnergy)
     * </pre>
     * Characters who never cast their burst in the recorded rotation receive a
     * required ER of {@code 1.0} (base).
     *
     * @param sim the completed combat simulator whose characters hold energy tracking data
     * @return map from character name to the minimum ER multiplier (e.g. {@code 1.30} for 130%)
     */
    public static java.util.Map<String, Double> calculateERRequirements(CombatSimulator sim) {
        java.util.Map<String, Double> erMap = new java.util.HashMap<>();

        for (Character c : sim.getPartyMembers()) {
            java.util.List<double[]> windows = c.getBurstEnergyWindows();
            double requiredER;

            if (!windows.isEmpty()) {
                // Global "start full, end full" model:
                //   Characters pre-load one burst worth of energy, so the first burst is free.
                //   Particles must cover all remaining burst costs (N-1 bursts) plus the final refill.
                //   Total particle energy needed  = N * cost  (N bursts × cost, minus the 1 pre-loaded,
                //   plus 1 end-of-rotation refill — the ±1 cancels out).
                //   Formula: ER >= (totalBurstCost - totalFlat) / totalParticles
                double totalBurstCost = 0;
                for (double[] w : windows)
                    totalBurstCost += w[2];

                double totalFlat = c.getTotalFlatEnergy();
                double totalParticles = c.getTotalParticleEnergy(); // all particles over full rotation

                if (totalParticles > 0) {
                    requiredER = Math.max(1.0, (totalBurstCost - totalFlat) / totalParticles);
                } else {
                    requiredER = (totalFlat >= totalBurstCost) ? 1.0 : 9.99;
                }
            } else {
                // No burst used in rotation — no ER requirement beyond base
                requiredER = 1.0;
            }

            erMap.put(c.getName(), requiredER);
        }
        return erMap;
    }
}

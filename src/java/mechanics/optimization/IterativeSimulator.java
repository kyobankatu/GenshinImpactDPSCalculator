package mechanics.optimization;

import simulation.CombatSimulator;
import mechanics.analysis.EnergyAnalyzer;

import java.util.Map;
import java.util.HashMap;
import java.util.function.Function;
import java.util.function.Consumer;

/**
 * Provides two independent iterative optimization loops:
 *
 * <ol>
 *   <li>{@link #optimizeER} – converges on the minimum Energy Recharge needed
 *       for each character to burst on rotation.  It re-runs the full rotation
 *       simulation each pass, reads actual energy deltas via
 *       {@link EnergyAnalyzer}, and updates the per-character ER targets until
 *       consecutive passes agree within 1%.</li>
 *   <li>{@link #optimizeJointPartyCrit} – a coordinated hill-climbing loop over
 *       all DPS characters.  ER rolls are pre-reserved (excluded from the
 *       hill-climbing budget) so the optimizer cannot trade away ER to gain
 *       more crit.  Each character is optimized in turn while the rest of the
 *       party holds their current best rolls; the outer loop repeats until no
 *       further improvement is found.</li>
 * </ol>
 */
public class IterativeSimulator {

    /**
     * Runs an iterative simulation to converge on Energy Recharge requirements.
     *
     * <p>Each iteration:
     * <ol>
     *   <li>Builds a fresh {@link CombatSimulator} with the current ER targets via
     *       {@code simFactory}.</li>
     *   <li>Executes the full rotation via {@code rotationRunner}.</li>
     *   <li>Derives the next ER targets from actual energy data using
     *       {@link EnergyAnalyzer#calculateERRequirements}.</li>
     *   <li>Stops early when every character's ER target changes by less than 1%
     *       (absolute) between two consecutive iterations.</li>
     * </ol>
     *
     * @param simFactory      factory that accepts a map of {@code charName -> minER}
     *                        and returns a configured simulator
     * @param rotationRunner  consumer that drives a complete rotation on the simulator
     * @param maxIterations   upper bound on iterations before returning unconverged
     * @return converged (or best-so-far) map of {@code charName -> required ER}
     */
    public static Map<String, Double> optimizeER(
            Function<Map<String, Double>, CombatSimulator> simFactory,
            Consumer<CombatSimulator> rotationRunner,
            int maxIterations) {

        System.out.println("--- Starting Iterative ER Optimization (Max " + maxIterations + ") ---");

        Map<String, Double> currentTargets = new HashMap<>();

        for (int i = 0; i < maxIterations; i++) {

            CombatSimulator sim = simFactory.apply(currentTargets);
            sim.setLoggingEnabled(false);
            rotationRunner.accept(sim);
            Map<String, Double> nextTargets = EnergyAnalyzer.calculateERRequirements(sim);

            boolean converged = true;
            for (String name : nextTargets.keySet()) {
                double prev = currentTargets.getOrDefault(name, 0.0);
                double curr = nextTargets.get(name);
                double diff = Math.abs(curr - prev);
                if (diff > 0.01) {
                    converged = false;
                }
            }

            currentTargets = nextTargets;

            if (converged && i > 0) {
                break;
            }
        }

        System.out.println(">>> ER Result: "
                + currentTargets.entrySet().stream()
                        .map(e -> e.getKey() + "=" + String.format("%.0f%%", e.getValue() * 100))
                        .collect(java.util.stream.Collectors.joining(", "))
                + " <<<");
        return currentTargets;
    }

    /**
     * Optimizes DPS substat distribution for multiple characters simultaneously.
     *
     * <p><b>ER pre-reservation:</b> The liquid roll budget for each character is
     * {@code 20 - erRollsPerChar[charName]}.  ER rolls are merged back into the
     * full roll map before every simulation call so the resulting artifact stats
     * are always correct, but the hill-climber never considers ER as a swap
     * candidate.  Characters absent from {@code erRollsPerChar} retain the full
     * 20-roll budget.
     *
     * <p><b>Hill-climbing strategy:</b> Characters are optimized one at a time in
     * the order defined by {@code targetCharsMap}.  While one character is being
     * optimized, all others use their current best rolls.  The outer loop repeats
     * up to 3 times until a full pass produces no change across any character.
     *
     * @param simFactory       factory that accepts the full party roll map and returns
     *                         a configured simulator
     * @param rotationRunner   consumer that drives a complete rotation on the simulator
     * @param targetCharsMap   map of {@code charName -> stats to optimize} defining
     *                         which characters participate and which substats to vary
     * @param erRollsPerChar   pre-computed liquid ER roll counts per character;
     *                         characters absent from this map receive the full 20-roll
     *                         budget; may be {@code null}
     * @return map of {@code charName -> optimal liquid roll distribution} (ER rolls
     *         are NOT included here; merge them separately for the final simulation)
     */
    public static Map<String, Map<model.type.StatType, Integer>> optimizeJointPartyCrit(
            Function<Map<String, Map<model.type.StatType, Integer>>, CombatSimulator> simFactory,
            Consumer<CombatSimulator> rotationRunner,
            Map<String, java.util.List<model.type.StatType>> targetCharsMap,
            Map<String, Integer> erRollsPerChar) {

        System.out.println("\n--- Starting Joint Party Substat Optimization (N-Dimensional) ---");
        Map<String, Map<model.type.StatType, Integer>> masterMap = new HashMap<>();

        int maxJointIterations = 3;
        for (int i = 0; i < maxJointIterations; i++) {
            boolean changed = false;

            for (String charName : targetCharsMap.keySet()) {
                java.util.List<model.type.StatType> optimizeStats = targetCharsMap.get(charName);
                Map<model.type.StatType, Integer> prevBest = masterMap.getOrDefault(charName, new HashMap<>());

                // ER rolls reserved for this character (excluded from hill-climbing budget)
                int erRolls = (erRollsPerChar != null) ? erRollsPerChar.getOrDefault(charName, 0) : 0;
                int dpsRollBudget = 20 - erRolls;

                // The factory receives dpsRolls merged with ER rolls so the artifact stats are complete
                final Map<String, Map<model.type.StatType, Integer>> currentGlobalState = new HashMap<>(masterMap);
                final int charERRolls = erRolls;

                Function<Map<model.type.StatType, Integer>, CombatSimulator> charSpecificFactory = (dpsRolls) -> {
                    Map<String, Map<model.type.StatType, Integer>> tempState = new HashMap<>(currentGlobalState);
                    // Merge DPS rolls with the pre-computed ER rolls
                    Map<model.type.StatType, Integer> fullRolls = new HashMap<>(dpsRolls);
                    if (charERRolls > 0) {
                        fullRolls.put(model.type.StatType.ENERGY_RECHARGE, charERRolls);
                    }
                    tempState.put(charName, fullRolls);
                    return simFactory.apply(tempState);
                };

                Map<model.type.StatType, Integer> newBest = optimizeSubstatsNDim(
                        charSpecificFactory,
                        rotationRunner,
                        charName,
                        optimizeStats,
                        dpsRollBudget);

                masterMap.put(charName, newBest);

                if (!prevBest.equals(newBest)) {
                    changed = true;
                }
            }

            if (!changed && i > 0) {
                System.out.println(">>> Joint Optimization Converged <<<");
                break;
            }
        }
        return masterMap;
    }

    /**
     * Finds the optimal liquid roll distribution for a single character using
     * balanced initialization followed by single-roll hill-climbing swaps.
     *
     * <p><b>Algorithm:</b>
     * <ol>
     *   <li><b>Initialization</b> – rolls are spread as evenly as possible across
     *       all stats in {@code statsToOptimize} (remainder goes to the first stats
     *       in list order).</li>
     *   <li><b>Hill climbing</b> – every possible (source, target) pair is evaluated
     *       by moving one roll from source to target.  The best-improving swap is
     *       committed, and the loop repeats until no swap improves DPS or
     *       {@code maxSteps} (100) is reached.  Each stat is capped at 10 rolls.</li>
     * </ol>
     *
     * @param simFactory       factory that accepts a roll map and returns a configured
     *                         simulator; receives only the DPS rolls (ER excluded)
     * @param rotationRunner   consumer that drives a complete rotation on the simulator
     * @param charName         name of the character being optimized (for logging)
     * @param statsToOptimize  the substats the hill-climber is allowed to vary
     * @param totalRolls       liquid roll budget available (20 minus pre-reserved ER rolls)
     * @return optimal roll distribution across {@code statsToOptimize}
     */
    public static Map<model.type.StatType, Integer> optimizeSubstatsNDim(
            Function<Map<model.type.StatType, Integer>, CombatSimulator> simFactory,
            Consumer<CombatSimulator> rotationRunner,
            String charName,
            java.util.List<model.type.StatType> statsToOptimize,
            int totalRolls) {

        System.out.println("   [Opt] Optimizing " + charName + " across " + statsToOptimize
                + " (budget: " + totalRolls + " rolls)");

        // 1. Balanced initialization
        Map<model.type.StatType, Integer> currentRolls = new HashMap<>();
        int nStats = statsToOptimize.size();
        int base = totalRolls / nStats;
        int remainder = totalRolls % nStats;

        for (int i = 0; i < nStats; i++) {
            currentRolls.put(statsToOptimize.get(i), base + (i < remainder ? 1 : 0));
        }

        // 2. Hill Climbing (swap 1 roll at a time)
        boolean improved = true;
        int maxSteps = 100;
        int step = 0;
        int ROLL_CAP = 10;

        double currentBestDPS = evaluateDPS(simFactory, rotationRunner, currentRolls);

        while (improved && step < maxSteps) {
            improved = false;
            step++;

            model.type.StatType bestSource = null;
            model.type.StatType bestTarget = null;
            double bestSwapDPS = currentBestDPS;

            for (model.type.StatType source : statsToOptimize) {
                if (currentRolls.get(source) <= 0)
                    continue;

                for (model.type.StatType target : statsToOptimize) {
                    if (source == target)
                        continue;
                    if (currentRolls.get(target) >= ROLL_CAP)
                        continue;

                    currentRolls.put(source, currentRolls.get(source) - 1);
                    currentRolls.put(target, currentRolls.get(target) + 1);

                    double dps = evaluateDPS(simFactory, rotationRunner, currentRolls);

                    if (dps > bestSwapDPS) {
                        bestSwapDPS = dps;
                        bestSource = source;
                        bestTarget = target;
                    }

                    currentRolls.put(target, currentRolls.get(target) - 1);
                    currentRolls.put(source, currentRolls.get(source) + 1);
                }
            }

            if (bestSource != null) {
                currentRolls.put(bestSource, currentRolls.get(bestSource) - 1);
                currentRolls.put(bestTarget, currentRolls.get(bestTarget) + 1);
                currentBestDPS = bestSwapDPS;
                improved = true;
            }
        }

        System.out.printf("      Result: %s => DPS: %.0f%n", currentRolls, currentBestDPS);
        return currentRolls;
    }

    /**
     * Builds a simulator from {@code rollsStub}, runs the rotation, and returns
     * the resulting DPS figure.
     *
     * @param simFactory     factory that produces a configured simulator
     * @param rotationRunner consumer that executes the rotation on the simulator
     * @param rollsStub      the substat roll map to evaluate
     * @return DPS value reported by the simulator after the rotation completes
     */
    private static double evaluateDPS(
            Function<Map<model.type.StatType, Integer>, CombatSimulator> simFactory,
            Consumer<CombatSimulator> rotationRunner,
            Map<model.type.StatType, Integer> rollsStub) {

        CombatSimulator sim = simFactory.apply(rollsStub);
        sim.setLoggingEnabled(false);
        rotationRunner.accept(sim);
        return sim.getDPS();
    }
}

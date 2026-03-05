package mechanics.optimization;

import simulation.CombatSimulator;
import model.type.StatType;

import java.util.Map;
import java.util.HashMap;
import java.util.List;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.BiFunction;

/**
 * Orchestrates the two-phase artifact optimization pipeline.
 *
 * <strong>Phase 1 – ER Calibration</strong>
 * <p>
 * {@link IterativeSimulator#optimizeER} is called with a sim factory that
 * ignores the DPS roll map ({@code null}) so the heuristic artifact generator
 * drives substat allocation. After convergence the pipeline inspects the
 * resulting simulator to extract the exact number of liquid ER rolls each
 * character required. These counts are stored in {@code erRollsMap} and are
 * used to shrink each character's DPS roll budget in Phase 2.
 *
 * <strong>Phase 2 – DPS Hill-Climbing</strong>
 * <p>
 * {@link IterativeSimulator#optimizeJointPartyCrit} performs coordinated
 * single-roll swaps across all DPS characters. The sim factory passed to
 * Phase 2 always merges the hill-climber's DPS rolls with the pre-reserved ER
 * rolls via {@link #mergeDPSAndER}, ensuring artifact stats are complete
 * without allowing the optimizer to cannibalize ER budget.
 *
 * <p>
 * The final {@link TotalOptimizationResult} contains the converged ER
 * targets and the fully merged party roll map ready for the final simulation.
 */
public class OptimizerPipeline {

        /**
         * Runs the full optimization pipeline:
         * <ol>
         * <li>Phase 1 – ER calibration via iterative rotation simulation.</li>
         * <li>Phase 2 – DPS hill-climbing with ER rolls pre-reserved per
         * character.</li>
         * </ol>
         *
         * <p>
         * ER roll counts are extracted automatically from the heuristic artifacts
         * produced by Phase 1 — no extra configuration needed. Each character's
         * liquid roll budget in Phase 2 equals {@code 20 - erRolls[char]}.
         *
         * @param simFactory       factory that accepts an ER target map and a DPS roll
         *                         map and returns a fully configured simulator; either
         *                         argument may be {@code null} during calibration
         * @param rotationRunner   consumer that drives a complete rotation on the
         *                         simulator
         * @param dpsCharsAndStats map of {@code charName -> substats to optimize}
         *                         defining
         *                         which characters participate in Phase 2 and which
         *                         substats the hill-climber may vary
         * @return a {@link TotalOptimizationResult} containing the converged ER targets
         *         and the final merged party roll map
         */
        public static TotalOptimizationResult run(
                        BiFunction<Map<String, Double>, Map<String, Map<StatType, Integer>>, CombatSimulator> simFactory,
                        Consumer<CombatSimulator> rotationRunner,
                        Map<String, List<StatType>> dpsCharsAndStats) {

                // Phase 1: ER Calibration
                System.out.println("\n--- Starting Energy Calibration ---");
                Function<Map<String, Double>, CombatSimulator> erSimFactory = (targets) -> simFactory.apply(targets,
                                null);
                Map<String, Double> requiredER = IterativeSimulator.optimizeER(erSimFactory, rotationRunner, 3);

                // Extract the liquid ER roll counts from the heuristic artifacts Phase 1
                // produced.
                // This tells us how many of the 20-roll budget are reserved for ER per
                // character.
                Map<String, Integer> erRollsMap = new HashMap<>();
                CombatSimulator heuristicSim = simFactory.apply(requiredER, null);
                for (model.entity.Character c : heuristicSim.getPartyMembers()) {
                        int erRolls = c.getArtifactRolls().getOrDefault(StatType.ENERGY_RECHARGE, 0);
                        if (erRolls > 0) {
                                erRollsMap.put(c.getName(), erRolls);
                        }
                }
                System.out.println("[Pipeline] Reserved ER rolls: " + erRollsMap);

                // Phase 2: DPS Optimization
                // The manualSimFactory merges the hill-climbing's DPS rolls with the ER rolls.
                System.out.println("\n--- Re-Optimizing Party Artifacts (Joint Crit Optimization) ---");
                Function<Map<String, Map<StatType, Integer>>, CombatSimulator> manualSimFactory = (dpsRolls) -> {
                        Map<String, Map<StatType, Integer>> mergedRolls = mergeDPSAndER(dpsRolls, erRollsMap);
                        return simFactory.apply(requiredER, mergedRolls);
                };

                Map<String, Map<StatType, Integer>> bestDPSRolls = IterativeSimulator.optimizeJointPartyCrit(
                                manualSimFactory,
                                rotationRunner,
                                dpsCharsAndStats,
                                erRollsMap);

                // Merge ER rolls into the final party rolls for the final simulation
                Map<String, Map<StatType, Integer>> finalPartyRolls = mergeDPSAndER(bestDPSRolls, erRollsMap);

                return new TotalOptimizationResult(requiredER, finalPartyRolls);
        }

        /**
         * Merges per-character ER roll counts into a DPS roll map without mutating
         * either input.
         *
         * <p>
         * For each character in {@code erRollsMap} with a positive roll count, the
         * corresponding entry in the returned map will have
         * {@code ENERGY_RECHARGE -> erRolls} added (or overwritten) so that the full
         * artifact stat block is correct when passed to a simulator factory.
         *
         * @param dpsRolls   the DPS-optimized roll map produced by the hill-climber
         * @param erRollsMap the pre-reserved ER roll counts per character name
         * @return a new map with ER rolls merged into the DPS rolls for each character
         */
        private static Map<String, Map<StatType, Integer>> mergeDPSAndER(
                        Map<String, Map<StatType, Integer>> dpsRolls,
                        Map<String, Integer> erRollsMap) {
                Map<String, Map<StatType, Integer>> result = new HashMap<>(dpsRolls);
                for (Map.Entry<String, Integer> entry : erRollsMap.entrySet()) {
                        if (entry.getValue() <= 0)
                                continue;
                        Map<StatType, Integer> charRolls = new HashMap<>(
                                        result.getOrDefault(entry.getKey(), new HashMap<>()));
                        charRolls.put(StatType.ENERGY_RECHARGE, entry.getValue());
                        result.put(entry.getKey(), charRolls);
                }
                return result;
        }
}

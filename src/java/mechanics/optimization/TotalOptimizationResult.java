package mechanics.optimization;

import java.util.Map;
import model.type.StatType;

/**
 * Holds the complete results of the {@link OptimizerPipeline} after both
 * Phase 1 (ER calibration) and Phase 2 (DPS hill-climbing) have finished.
 *
 * <p>Pass this result to the final simulation factory to reproduce the
 * optimized party configuration.
 */
public class TotalOptimizationResult {

    /**
     * Converged minimum Energy Recharge targets per character name.
     * Values are fractional (e.g. {@code 2.50} represents 250% ER).
     */
    public final Map<String, Double> erTargets;

    /**
     * Final merged liquid roll allocation per character name.
     * Each inner map contains {@code StatType -> rollCount} pairs and
     * includes both the DPS-optimized rolls and the pre-reserved ER rolls.
     */
    public final Map<String, Map<StatType, Integer>> partyRolls;

    /**
     * @param erTargets   converged ER targets produced by Phase 1
     * @param partyRolls  final merged roll map produced after Phase 2
     */
    public TotalOptimizationResult(Map<String, Double> erTargets, Map<String, Map<StatType, Integer>> partyRolls) {
        this.erTargets = erTargets;
        this.partyRolls = partyRolls;
    }
}

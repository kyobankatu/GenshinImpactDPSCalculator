package mechanics.optimization;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

import model.type.CharacterId;
import simulation.CombatSimulator;
import simulation.party.PartyDefinition;

/**
 * Resolves and caches one immutable optimized build per exact party rotation.
 *
 * <p>
 * Cache identity includes loadout, cycle duration, and baseline action IDs.
 * Every simulator reset receives the same frozen ER and roll maps while still
 * constructing fresh simulator and character instances.
 */
public final class PartyBuildResolver {
    private static final Map<String, TotalOptimizationResult> BUILDS = new HashMap<>();

    private PartyBuildResolver() {
    }

    /** Returns the process-wide frozen build for one exact party scenario. */
    public static TotalOptimizationResult require(PartyDefinition definition) {
        if (definition == null) {
            throw new IllegalArgumentException("Party definition must not be null");
        }
        return require(
                definition,
                "baseline:" + java.util.Arrays.toString(definition.baselinePolicyActions()),
                definition::executeRotation);
    }

    /** Returns a frozen build calibrated against an explicit rotation identity. */
    public static TotalOptimizationResult require(
            PartyDefinition definition,
            String rotationFingerprint,
            Consumer<CombatSimulator> rotationRunner) {
        if (definition == null) {
            throw new IllegalArgumentException("Party definition must not be null");
        }
        if (rotationFingerprint == null || rotationFingerprint.isBlank() || rotationRunner == null) {
            throw new IllegalArgumentException("Rotation fingerprint and runner are required");
        }
        String key = cacheKey(definition, rotationFingerprint);
        synchronized (BUILDS) {
            TotalOptimizationResult existing = BUILDS.get(key);
            if (existing != null) {
                return existing;
            }
            if (definition.optimizationTargets() == null || definition.optimizationTargets().isEmpty()) {
                throw new IllegalArgumentException(
                        "Trainable party has no artifact optimization targets: " + definition.name());
            }
            TotalOptimizationResult optimized = OptimizerPipeline.run(
                    definition::createSimulator,
                    rotationRunner,
                    definition.optimizationTargets());
            Map<CharacterId, Double> mergedErTargets = new LinkedHashMap<>(optimized.erTargets);
            for (Map.Entry<CharacterId, Double> entry
                    : definition.minimumEnergyRechargeTargets().entrySet()) {
                mergedErTargets.merge(entry.getKey(), entry.getValue(), Math::max);
            }
            if (!mergedErTargets.equals(optimized.erTargets)) {
                optimized = new TotalOptimizationResult(mergedErTargets, optimized.partyRolls);
            }
            BUILDS.put(key, optimized);
            return optimized;
        }
    }

    private static String cacheKey(PartyDefinition definition, String rotationFingerprint) {
        return definition.loadoutFingerprint()
                + ":cycle=" + Double.toHexString(definition.rotationCycleSeconds())
                + ":rotation=" + rotationFingerprint;
    }
}

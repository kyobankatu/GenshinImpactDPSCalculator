package mechanics.optimization;

import java.util.HashMap;
import java.util.Map;

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
        String key = cacheKey(definition);
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
                    definition::executeRotation,
                    definition.optimizationTargets());
            BUILDS.put(key, optimized);
            return optimized;
        }
    }

    private static String cacheKey(PartyDefinition definition) {
        return definition.loadoutFingerprint()
                + ":cycle=" + Double.toHexString(definition.rotationCycleSeconds())
                + ":actions=" + java.util.Arrays.toString(definition.baselinePolicyActions());
    }
}

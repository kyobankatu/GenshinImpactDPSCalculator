package mechanics.rotation;

import mechanics.rl.EpisodeConfig;
import mechanics.rl.GenericRLSimulatorFactory;
import mechanics.rl.RLEpisodeFactory;
import mechanics.rl.SinglePartyRLEpisodeFactory;
import mechanics.optimization.PartyBuildResolver;
import mechanics.optimization.TotalOptimizationResult;
import simulation.party.PartyDefinition;

/**
 * Immutable identity and runtime configuration for one rotation-search case.
 */
public final class RotationScenario {
    private final String fingerprint;
    private final RLEpisodeFactory episodeFactory;
    private final int preferredPartyId;
    private final double cycleDurationSeconds;
    private final int cycleCount;
    private final long seed;
    private final RotationObjective objective;

    /**
     * Creates a scenario around an already configured episode factory.
     *
     * @param fingerprint stable party/loadout identity
     * @param episodeFactory factory whose episode horizon matches this scenario
     * @param preferredPartyId requested party id, or -1
     * @param cycleDurationSeconds duration of one intended cycle
     * @param cycleCount number of cycles in the fixed horizon
     * @param seed deterministic search seed
     * @param objective terminal objective
     */
    public RotationScenario(
            String fingerprint,
            RLEpisodeFactory episodeFactory,
            int preferredPartyId,
            double cycleDurationSeconds,
            int cycleCount,
            long seed,
            RotationObjective objective) {
        if (fingerprint == null || fingerprint.isBlank()) {
            throw new IllegalArgumentException("fingerprint must not be blank");
        }
        if (episodeFactory == null) {
            throw new IllegalArgumentException("episodeFactory must not be null");
        }
        if (!Double.isFinite(cycleDurationSeconds) || cycleDurationSeconds <= 0.0) {
            throw new IllegalArgumentException("cycleDurationSeconds must be finite and positive");
        }
        if (cycleCount <= 0) {
            throw new IllegalArgumentException("cycleCount must be positive");
        }
        if (!Double.isFinite(cycleDurationSeconds * cycleCount)) {
            throw new IllegalArgumentException("total horizon must be finite");
        }
        if (objective == null) {
            throw new IllegalArgumentException("objective must not be null");
        }
        this.fingerprint = fingerprint;
        this.episodeFactory = episodeFactory;
        this.preferredPartyId = preferredPartyId;
        this.cycleDurationSeconds = cycleDurationSeconds;
        this.cycleCount = cycleCount;
        this.seed = seed;
        this.objective = objective;
    }

    /**
     * Builds a fixed single-party scenario from the shared party catalog.
     *
     * @param definition exact party and loadout definition
     * @param baseConfig base RL configuration
     * @param cycleDurationSeconds duration of one intended cycle
     * @param cycleCount number of cycles to evaluate
     * @param seed deterministic search seed
     * @param objective terminal objective
     * @return configured scenario
     */
    public static RotationScenario forParty(
            PartyDefinition definition,
            EpisodeConfig baseConfig,
            double cycleDurationSeconds,
            int cycleCount,
            long seed,
            RotationObjective objective) {
        if (definition == null) {
            throw new IllegalArgumentException("definition must not be null");
        }
        if (baseConfig == null) {
            throw new IllegalArgumentException("baseConfig must not be null");
        }
        TotalOptimizationResult build = PartyBuildResolver.require(definition);
        return forPartyBuild(
                definition,
                build,
                baseConfig,
                cycleDurationSeconds,
                cycleCount,
                seed,
                objective);
    }

    /** Builds a fixed single-party scenario from an explicitly frozen build. */
    public static RotationScenario forPartyBuild(
            PartyDefinition definition,
            TotalOptimizationResult build,
            EpisodeConfig baseConfig,
            double cycleDurationSeconds,
            int cycleCount,
            long seed,
            RotationObjective objective) {
        if (definition == null || build == null) {
            throw new IllegalArgumentException("Party definition and optimized build are required");
        }
        if (baseConfig == null) {
            throw new IllegalArgumentException("baseConfig must not be null");
        }
        double horizon = cycleDurationSeconds * cycleCount;
        EpisodeConfig scenarioConfig = baseConfig
                .withPartyOrder(definition.partyOrder())
                .withMaxEpisodeTime(horizon);
        RLEpisodeFactory factory = new SinglePartyRLEpisodeFactory(
                GenericRLSimulatorFactory.spec(definition, build), scenarioConfig);
        String fingerprint = definition.loadoutFingerprint() + ":cycles=" + cycleCount
                + ":cycleSeconds=" + Double.toHexString(cycleDurationSeconds)
                + ":build=" + build.getBuildFingerprint()
                + ":fillEnergyOnReset=" + scenarioConfig.fillEnergyOnReset;
        return new RotationScenario(
                fingerprint,
                factory,
                0,
                cycleDurationSeconds,
                cycleCount,
                seed,
                objective);
    }

    public String getFingerprint() {
        return fingerprint;
    }

    public RLEpisodeFactory getEpisodeFactory() {
        return episodeFactory;
    }

    public int getPreferredPartyId() {
        return preferredPartyId;
    }

    public double getCycleDurationSeconds() {
        return cycleDurationSeconds;
    }

    public int getCycleCount() {
        return cycleCount;
    }

    public double getHorizonSeconds() {
        return cycleDurationSeconds * cycleCount;
    }

    public long getSeed() {
        return seed;
    }

    public RotationObjective getObjective() {
        return objective;
    }
}

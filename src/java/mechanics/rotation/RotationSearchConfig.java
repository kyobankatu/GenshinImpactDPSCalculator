package mechanics.rotation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BooleanSupplier;

/** Immutable bounded search configuration shared by teacher strategies. */
public final class RotationSearchConfig {
    public final int simulatorCallBudget;
    public final int maxActions;
    public final int archiveSize;
    public final int populationSize;
    public final int eliteCount;
    public final int maxWaitRunLength;
    public final double explorationConstant;
    public final long seed;
    public final ExpertPolicyPrior prior;
    public final PolicyValueAdvisor advisor;
    public final long advisorTimeoutMillis;
    public final boolean advisorGuidanceEnabled;
    public final BooleanSupplier cancellation;
    private final List<int[]> initialSeeds;

    /** Creates a fully explicit search configuration. */
    public RotationSearchConfig(
            int simulatorCallBudget,
            int maxActions,
            int archiveSize,
            int populationSize,
            int eliteCount,
            double explorationConstant,
            long seed,
            ExpertPolicyPrior prior,
            BooleanSupplier cancellation,
            List<int[]> initialSeeds) {
        this(
                simulatorCallBudget,
                maxActions,
                archiveSize,
                populationSize,
                eliteCount,
                explorationConstant,
                seed,
                prior,
                cancellation,
                initialSeeds,
                1,
                PolicyValueAdvisor.fromPrior(prior),
                50L,
                false);
    }

    /** Creates a fully explicit search configuration including Wait macros. */
    public RotationSearchConfig(
            int simulatorCallBudget,
            int maxActions,
            int archiveSize,
            int populationSize,
            int eliteCount,
            double explorationConstant,
            long seed,
            ExpertPolicyPrior prior,
            BooleanSupplier cancellation,
            List<int[]> initialSeeds,
            int maxWaitRunLength) {
        this(
                simulatorCallBudget,
                maxActions,
                archiveSize,
                populationSize,
                eliteCount,
                explorationConstant,
                seed,
                prior,
                cancellation,
                initialSeeds,
                maxWaitRunLength,
                PolicyValueAdvisor.fromPrior(prior),
                50L,
                false);
    }

    private RotationSearchConfig(
            int simulatorCallBudget,
            int maxActions,
            int archiveSize,
            int populationSize,
            int eliteCount,
            double explorationConstant,
            long seed,
            ExpertPolicyPrior prior,
            BooleanSupplier cancellation,
            List<int[]> initialSeeds,
            int maxWaitRunLength,
            PolicyValueAdvisor advisor,
            long advisorTimeoutMillis,
            boolean advisorGuidanceEnabled) {
        if (simulatorCallBudget <= 0) {
            throw new IllegalArgumentException("simulatorCallBudget must be positive");
        }
        if (maxActions <= 0) {
            throw new IllegalArgumentException("maxActions must be positive");
        }
        if (archiveSize <= 0) {
            throw new IllegalArgumentException("archiveSize must be positive");
        }
        if (populationSize < 2) {
            throw new IllegalArgumentException("populationSize must be at least two");
        }
        if (eliteCount <= 0 || eliteCount >= populationSize) {
            throw new IllegalArgumentException("eliteCount must be within the population");
        }
        if (maxWaitRunLength <= 0 || maxWaitRunLength > maxActions) {
            throw new IllegalArgumentException(
                    "maxWaitRunLength must be within maxActions");
        }
        if (!Double.isFinite(explorationConstant) || explorationConstant < 0.0) {
            throw new IllegalArgumentException("explorationConstant must be finite and non-negative");
        }
        if (prior == null || advisor == null || cancellation == null || initialSeeds == null
                || advisorTimeoutMillis <= 0L) {
            throw new IllegalArgumentException(
                    "prior, advisor, cancellation, and initialSeeds are required");
        }
        List<int[]> seedCopy = copySeeds(initialSeeds);
        int longestSeed = 0;
        for (int[] seedActions : seedCopy) {
            longestSeed = Math.max(longestSeed, seedActions.length);
        }
        this.simulatorCallBudget = simulatorCallBudget;
        this.maxActions = Math.max(maxActions, longestSeed);
        this.archiveSize = archiveSize;
        this.populationSize = populationSize;
        this.eliteCount = eliteCount;
        this.maxWaitRunLength = maxWaitRunLength;
        this.explorationConstant = explorationConstant;
        this.seed = seed;
        this.prior = prior;
        this.advisor = advisor;
        this.advisorTimeoutMillis = advisorTimeoutMillis;
        this.advisorGuidanceEnabled = advisorGuidanceEnabled;
        this.cancellation = cancellation;
        this.initialSeeds = seedCopy;
    }

    /** Returns a practical deterministic configuration for bounded local search. */
    public static RotationSearchConfig defaults(long seed, int simulatorCallBudget) {
        return new RotationSearchConfig(
                simulatorCallBudget,
                64,
                8,
                12,
                3,
                Math.sqrt(2.0),
                seed,
                ExpertPolicyPrior.uniform(),
                () -> false,
                Collections.emptyList());
    }

    /** Returns a copy with optional human or prior-search sequence seeds. */
    public RotationSearchConfig withInitialSeeds(List<int[]> seeds) {
        return new RotationSearchConfig(
                simulatorCallBudget,
                maxActions,
                archiveSize,
                populationSize,
                eliteCount,
                explorationConstant,
                seed,
                prior,
                cancellation,
                seeds,
                maxWaitRunLength,
                advisor,
                advisorTimeoutMillis,
                advisorGuidanceEnabled);
    }

    /** Returns a copy using a validated model or recorded policy prior. */
    public RotationSearchConfig withPrior(ExpertPolicyPrior policyPrior) {
        return new RotationSearchConfig(
                simulatorCallBudget,
                maxActions,
                archiveSize,
                populationSize,
                eliteCount,
                explorationConstant,
                seed,
                policyPrior,
                cancellation,
                initialSeeds,
                maxWaitRunLength,
                PolicyValueAdvisor.fromPrior(policyPrior),
                50L,
                false);
    }

    /** Returns a copy using the versioned advisor through the legacy prior adapter. */
    public RotationSearchConfig withAdvisor(
            PolicyValueAdvisor policyValueAdvisor,
            long timeoutMillis) {
        PolicyValueAdvisor fallbackAdvisor = PolicyValueAdvisor.withUniformFallback(
                policyValueAdvisor);
        ExpertPolicyPrior policyPrior = ExpertPolicyPrior.fromAdvisor(
                fallbackAdvisor,
                timeoutMillis);
        return new RotationSearchConfig(
                simulatorCallBudget,
                maxActions,
                archiveSize,
                populationSize,
                eliteCount,
                explorationConstant,
                seed,
                policyPrior,
                cancellation,
                initialSeeds,
                maxWaitRunLength,
                fallbackAdvisor,
                timeoutMillis,
                true);
    }

    /** Returns a copy with one search-internal Wait run maximum. */
    public RotationSearchConfig withMaxWaitRunLength(int waitRunLength) {
        return new RotationSearchConfig(
                simulatorCallBudget,
                maxActions,
                archiveSize,
                populationSize,
                eliteCount,
                explorationConstant,
                seed,
                prior,
                cancellation,
                initialSeeds,
                waitRunLength,
                advisor,
                advisorTimeoutMillis,
                advisorGuidanceEnabled);
    }

    /** Returns defensive copies of optional human or prior-search seeds. */
    public List<int[]> getInitialSeeds() {
        return copySeeds(initialSeeds);
    }

    private static List<int[]> copySeeds(List<int[]> seeds) {
        List<int[]> copy = new ArrayList<>();
        Set<String> sequenceKeys = new HashSet<>();
        for (int[] seedActions : seeds) {
            if (seedActions == null) {
                throw new IllegalArgumentException("initial seed must not be null");
            }
            int[] cloned = seedActions.clone();
            for (int actionId : cloned) {
                PolicyAction.fromId(actionId);
            }
            if (!sequenceKeys.add(Arrays.toString(cloned))) {
                throw new IllegalArgumentException("initial seeds must be unique");
            }
            copy.add(cloned);
        }
        return Collections.unmodifiableList(copy);
    }
}

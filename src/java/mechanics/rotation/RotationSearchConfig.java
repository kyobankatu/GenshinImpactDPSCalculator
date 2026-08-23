package mechanics.rotation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.BooleanSupplier;

/** Immutable bounded search configuration shared by teacher strategies. */
public final class RotationSearchConfig {
    public final int simulatorCallBudget;
    public final int maxActions;
    public final int archiveSize;
    public final int populationSize;
    public final int eliteCount;
    public final double explorationConstant;
    public final long seed;
    public final ExpertPolicyPrior prior;
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
        if (!Double.isFinite(explorationConstant) || explorationConstant < 0.0) {
            throw new IllegalArgumentException("explorationConstant must be finite and non-negative");
        }
        if (prior == null || cancellation == null || initialSeeds == null) {
            throw new IllegalArgumentException("prior, cancellation, and initialSeeds are required");
        }
        this.simulatorCallBudget = simulatorCallBudget;
        this.maxActions = maxActions;
        this.archiveSize = archiveSize;
        this.populationSize = populationSize;
        this.eliteCount = eliteCount;
        this.explorationConstant = explorationConstant;
        this.seed = seed;
        this.prior = prior;
        this.cancellation = cancellation;
        this.initialSeeds = copySeeds(initialSeeds);
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
                seeds);
    }

    /** Returns defensive copies of optional human or prior-search seeds. */
    public List<int[]> getInitialSeeds() {
        return copySeeds(initialSeeds);
    }

    private static List<int[]> copySeeds(List<int[]> seeds) {
        List<int[]> copy = new ArrayList<>();
        for (int[] seedActions : seeds) {
            if (seedActions == null) {
                throw new IllegalArgumentException("initial seed must not be null");
            }
            int[] cloned = seedActions.clone();
            for (int actionId : cloned) {
                PolicyAction.fromId(actionId);
            }
            copy.add(cloned);
        }
        return Collections.unmodifiableList(copy);
    }
}

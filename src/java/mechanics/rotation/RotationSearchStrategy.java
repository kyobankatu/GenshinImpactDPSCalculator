package mechanics.rotation;

import java.util.List;
import java.util.function.Supplier;

/** Strategy boundary for bounded model-independent and policy-guided search. */
public interface RotationSearchStrategy {
    /** Searches one deterministic environment factory under the supplied budget. */
    Result search(
            Supplier<? extends RotationEnvironment> environmentFactory,
            RotationSearchConfig config);

    /** Immutable bounded search result. */
    final class Result {
        public final ExpertTrajectory best;
        public final List<ExpertTrajectory> archive;
        public final int simulatorCalls;
        public final boolean cancelled;

        /** Creates one search result with a non-empty archive. */
        public Result(
                ExpertTrajectory best,
                List<ExpertTrajectory> archive,
                int simulatorCalls,
                boolean cancelled) {
            if (best == null || archive == null || archive.isEmpty()) {
                throw new IllegalArgumentException("best and non-empty archive are required");
            }
            if (simulatorCalls <= 0) {
                throw new IllegalArgumentException("simulatorCalls must be positive");
            }
            this.best = best;
            this.archive = List.copyOf(archive);
            this.simulatorCalls = simulatorCalls;
            this.cancelled = cancelled;
        }
    }
}

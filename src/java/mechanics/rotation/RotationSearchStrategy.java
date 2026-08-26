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
        public final List<ExpertTrajectory> diagnosticArchive;
        public final int simulatorCalls;
        public final boolean cancelled;
        public final boolean publishable;
        public final RotationSearchStatistics statistics;

        /** Creates one search result with feasible labels or a diagnostic fallback. */
        public Result(
                List<ExpertTrajectory> feasibleArchive,
                List<ExpertTrajectory> diagnosticArchive,
                RotationSearchStatistics statistics,
                boolean cancelled) {
            if (feasibleArchive == null
                    || diagnosticArchive == null
                    || statistics == null) {
                throw new IllegalArgumentException("archives and statistics are required");
            }
            if (feasibleArchive.isEmpty() && diagnosticArchive.isEmpty()) {
                throw new IllegalArgumentException("at least one search trajectory is required");
            }
            this.publishable = !feasibleArchive.isEmpty();
            this.archive = List.copyOf(feasibleArchive);
            this.diagnosticArchive = List.copyOf(diagnosticArchive);
            this.best = publishable ? this.archive.get(0) : this.diagnosticArchive.get(0);
            this.statistics = statistics;
            this.simulatorCalls = statistics.simulatorCalls;
            this.cancelled = cancelled;
        }
    }
}

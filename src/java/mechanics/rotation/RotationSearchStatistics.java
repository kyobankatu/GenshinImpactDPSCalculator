package mechanics.rotation;

/** Immutable accounting for one bounded teacher-search invocation. */
public final class RotationSearchStatistics {
    public final int simulatorCalls;
    public final int evaluatedTrajectories;
    public final int completedTrajectories;
    public final int completedPopulations;
    public final int completedGenerations;
    public final int evaluatedSeeds;
    public final int repairedActions;
    public final int rejectedTrajectories;
    public final int diagnosticTrajectories;

    private RotationSearchStatistics(Mutable mutable, int simulatorCalls) {
        this.simulatorCalls = simulatorCalls;
        this.evaluatedTrajectories = mutable.evaluatedTrajectories;
        this.completedTrajectories = mutable.completedTrajectories;
        this.completedPopulations = mutable.completedPopulations;
        this.completedGenerations = mutable.completedGenerations;
        this.evaluatedSeeds = mutable.evaluatedSeeds;
        this.repairedActions = mutable.repairedActions;
        this.rejectedTrajectories = mutable.rejectedTrajectories;
        this.diagnosticTrajectories = mutable.diagnosticTrajectories;
    }

    /** Returns whether an evolutionary run completed initialization and mutation. */
    public boolean completedProductionEvolution() {
        return completedPopulations > 0 && completedGenerations > 0;
    }

    /** Mutable package-private accumulator owned by one search invocation. */
    static final class Mutable {
        private int evaluatedTrajectories;
        private int completedTrajectories;
        private int completedPopulations;
        private int completedGenerations;
        private int evaluatedSeeds;
        private int repairedActions;
        private int rejectedTrajectories;
        private int diagnosticTrajectories;

        void recordEvaluation(
                ExpertTrajectory trajectory,
                int repairs,
                boolean seed,
                boolean diagnostic) {
            evaluatedTrajectories++;
            if (trajectory.isComplete()) {
                completedTrajectories++;
            }
            if (seed) {
                evaluatedSeeds++;
            }
            repairedActions += repairs;
            if (diagnostic) {
                diagnosticTrajectories++;
            }
        }

        void recordRejectedTrajectory() {
            rejectedTrajectories++;
        }

        void recordCompletedPopulation() {
            completedPopulations++;
        }

        void recordCompletedGeneration() {
            completedGenerations++;
        }

        RotationSearchStatistics freeze(int simulatorCalls) {
            return new RotationSearchStatistics(this, simulatorCalls);
        }
    }
}

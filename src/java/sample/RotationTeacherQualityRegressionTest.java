package sample;

import java.util.Arrays;
import java.util.List;

import mechanics.rotation.EvolutionaryRotationSearcher;
import mechanics.rotation.ExpertPolicyPrior;
import mechanics.rotation.ExpertTrajectory;
import mechanics.rotation.MctsRotationSearcher;
import mechanics.rotation.PolicyAction;
import mechanics.rotation.RotationEnvironment;
import mechanics.rotation.RotationEvaluationMode;
import mechanics.rotation.RotationObjective;
import mechanics.rotation.RotationScenario;
import mechanics.rotation.RotationSearchConfig;
import mechanics.rotation.RotationSearchStrategy;
import mechanics.rotation.RotationStep;
import mechanics.rotation.RotationTrajectoryRanker;
import mechanics.rotation.TopKTrajectoryArchive;

/** Regression checks for strict seeds and feasibility-first teacher labels. */
public class RotationTeacherQualityRegressionTest {
    private static final int WAIT = PolicyAction.WAIT_SHORT.getId();
    private static final int ILLEGAL = PolicyAction.NORMAL.getId();

    public static void main(String[] args) {
        assertFeasibilityFirstOrdering();
        assertStrictSeedRetained(new EvolutionaryRotationSearcher());
        assertStrictSeedRetained(new MctsRotationSearcher());
        assertLongSeedExpandsActionLimit();
        assertIllegalStrictSeedRejected(new EvolutionaryRotationSearcher());
        assertIllegalStrictSeedRejected(new MctsRotationSearcher());
        assertFallbackNotPublished();
        assertIncompleteGenerationReported();
        assertDuplicateSeedRejected();
        System.out.println("RotationTeacherQualityRegressionTest passed");
    }

    private static void assertFeasibilityFirstOrdering() {
        TopKTrajectoryArchive archive = new TopKTrajectoryArchive(4);
        ExpertTrajectory feasible = trajectory(
                new int[] {WAIT},
                new RotationObjective(0.0, 0.0, 0.0, 0.0)
                        .evaluate(100.0, 1.0, 0.0, 0),
                true);
        ExpertTrajectory incomplete = trajectory(
                new int[] {WAIT, WAIT},
                new RotationObjective(0.0, 0.0, 0.0, 0.0)
                        .evaluate(1000.0, 1.0, 0.0, 0),
                false);
        ExpertTrajectory energyInfeasible = trajectory(
                new int[] {WAIT, WAIT, WAIT},
                new RotationObjective(1.0, 0.0, 0.0, 0.0)
                        .evaluate(2000.0, 1.0, 1.0, 0),
                true);
        ExpertTrajectory illegal = trajectory(
                new int[] {ILLEGAL},
                new RotationObjective(0.0, 0.0, 0.0, 0.0)
                        .evaluate(3000.0, 1.0, 0.0, 1),
                true);
        archive.add(illegal);
        archive.add(incomplete);
        archive.add(energyInfeasible);
        archive.add(feasible);
        if (archive.best() != feasible
                || !RotationTrajectoryRanker.INSTANCE.isPublishable(feasible)
                || RotationTrajectoryRanker.INSTANCE.isPublishable(energyInfeasible)) {
            throw new AssertionError("Archive did not prioritize a publishable trajectory");
        }
    }

    private static void assertStrictSeedRetained(RotationSearchStrategy strategy) {
        int[] seed = waits(4);
        RotationSearchConfig config = config(240, 4, 4, List.of(seed));
        RotationSearchStrategy.Result result = strategy.search(
                () -> new WaitHorizonEnvironment(seed.length),
                config);
        assertArrayEquals(seed, result.best.getActions(), "strict seed");
        if (!result.publishable
                || result.statistics.evaluatedSeeds != 1
                || result.best.getEvaluationMode() != RotationEvaluationMode.STRICT
                || result.best.getRepairedActionCount() != 0) {
            throw new AssertionError("Search did not retain strict seed provenance");
        }
    }

    private static void assertLongSeedExpandsActionLimit() {
        int[] seed = waits(70);
        RotationSearchConfig config = config(1000, 4, 4, List.of(seed));
        if (config.maxActions != seed.length) {
            throw new AssertionError("Long seed did not expand maxActions");
        }
        RotationSearchStrategy.Result result = new EvolutionaryRotationSearcher().search(
                () -> new WaitHorizonEnvironment(seed.length),
                config);
        assertArrayEquals(seed, result.best.getActions(), "long strict seed");
        if (!result.statistics.completedProductionEvolution()) {
            throw new AssertionError("Production budget did not complete a mutation generation");
        }
    }

    private static void assertIllegalStrictSeedRejected(RotationSearchStrategy strategy) {
        RotationSearchConfig config = config(100, 2, 4, List.of(new int[] {ILLEGAL}));
        expectFailure(() -> strategy.search(
                () -> new WaitHorizonEnvironment(2),
                config), "illegal strict seed");
    }

    private static void assertIncompleteGenerationReported() {
        RotationSearchConfig config = config(8, 3, 6, List.of());
        RotationSearchStrategy.Result result = new EvolutionaryRotationSearcher().search(
                () -> new WaitHorizonEnvironment(3),
                config);
        if (result.statistics.completedPopulations != 0
                || result.statistics.completedGenerations != 0
                || result.statistics.completedProductionEvolution()) {
            throw new AssertionError("Partial population was reported as production search");
        }
    }

    private static void assertFallbackNotPublished() {
        RotationSearchConfig config = config(60, 2, 4, List.of());
        RotationSearchStrategy.Result result = new EvolutionaryRotationSearcher().search(
                () -> new WaitHorizonEnvironment(2, false),
                config);
        if (result.publishable
                || !result.archive.isEmpty()
                || result.diagnosticArchive.isEmpty()
                || RotationTrajectoryRanker.INSTANCE.isPublishable(result.best)) {
            throw new AssertionError("Diagnostic fallback leaked into publishable archive");
        }
    }

    private static void assertDuplicateSeedRejected() {
        int[] seed = waits(2);
        expectFailure(() -> config(100, 2, 4, List.of(seed, seed)), "duplicate seed");
    }

    private static RotationSearchConfig config(
            int budget,
            int maxActions,
            int populationSize,
            List<int[]> seeds) {
        return new RotationSearchConfig(
                budget,
                maxActions,
                4,
                populationSize,
                1,
                Math.sqrt(2.0),
                2468L,
                ExpertPolicyPrior.uniform(),
                () -> false,
                seeds);
    }

    private static ExpertTrajectory trajectory(
            int[] actions,
            RotationObjective.Score score,
            boolean complete) {
        return new ExpertTrajectory(actions, score, Arrays.hashCode(actions), complete, actions.length);
    }

    private static int[] waits(int length) {
        int[] actions = new int[length];
        Arrays.fill(actions, WAIT);
        return actions;
    }

    private static void assertArrayEquals(int[] expected, int[] actual, String message) {
        if (!Arrays.equals(expected, actual)) {
            throw new AssertionError(message + ": expected=" + Arrays.toString(expected)
                    + " actual=" + Arrays.toString(actual));
        }
    }

    private static void expectFailure(Runnable action, String message) {
        try {
            action.run();
            throw new AssertionError("Expected failure for " + message);
        } catch (IllegalArgumentException | IllegalStateException expected) {
            // Expected.
        }
    }

    private static final class WaitHorizonEnvironment implements RotationEnvironment {
        private final int horizon;
        private final boolean cyclicFeasible;
        private int stepCount;
        private long generation;
        private RotationStep current;
        private boolean closed;

        private WaitHorizonEnvironment(int horizon) {
            this(horizon, true);
        }

        private WaitHorizonEnvironment(int horizon, boolean cyclicFeasible) {
            this.horizon = horizon;
            this.cyclicFeasible = cyclicFeasible;
        }

        @Override
        public RotationStep reset() {
            ensureOpen();
            stepCount = 0;
            generation++;
            current = state(-1);
            return current.copy();
        }

        @Override
        public RotationStep step(int actionId) {
            ensureReady();
            if (current.done || actionId != WAIT) {
                throw new IllegalStateException("Wait fixture received unavailable action " + actionId);
            }
            stepCount++;
            current = state(actionId);
            return current.copy();
        }

        @Override
        public RotationStep current() {
            ensureReady();
            return current.copy();
        }

        @Override
        public Snapshot snapshot() {
            ensureReady();
            return new WaitSnapshot(generation, stepCount, current.stateHash);
        }

        @Override
        public RotationStep restore(Snapshot snapshot) {
            ensureReady();
            if (!(snapshot instanceof WaitSnapshot)) {
                throw new IllegalArgumentException("Foreign wait fixture snapshot");
            }
            WaitSnapshot waitSnapshot = (WaitSnapshot) snapshot;
            if (waitSnapshot.generation != generation) {
                throw new IllegalArgumentException("Stale wait fixture snapshot");
            }
            stepCount = waitSnapshot.stepCount;
            current = state(stepCount == 0 ? -1 : WAIT);
            if (current.stateHash != waitSnapshot.stateHash) {
                throw new IllegalStateException("Wait fixture snapshot hash mismatch");
            }
            return current.copy();
        }

        @Override
        public RotationScenario scenario() {
            return null;
        }

        @Override
        public void close() {
            closed = true;
            current = null;
        }

        private RotationStep state(int actionId) {
            boolean done = stepCount >= horizon;
            double[] mask = new double[PolicyAction.SIZE];
            if (!done) {
                mask[WAIT] = 1.0;
            }
            return new RotationStep(
                    new double[] {stepCount},
                    new double[0],
                    mask,
                    0.0,
                    done,
                    true,
                    1.0,
                    stepCount,
                    1.0,
                    actionId,
                    stepCount,
                    0,
                    stepCount,
                    cyclicFeasible
                            ? new RotationObjective(0.0, 0.0, 0.0, 0.0)
                                    .evaluate(stepCount, stepCount, 0.0, 0)
                            : new RotationObjective(1.0, 0.0, 0.0, 0.0)
                                    .evaluate(stepCount, stepCount, 1.0, 0));
        }

        private void ensureReady() {
            ensureOpen();
            if (current == null) {
                throw new IllegalStateException("Wait fixture must be reset");
            }
        }

        private void ensureOpen() {
            if (closed) {
                throw new IllegalStateException("Wait fixture is closed");
            }
        }
    }

    private static final class WaitSnapshot implements RotationEnvironment.Snapshot {
        private final long generation;
        private final int stepCount;
        private final long stateHash;

        private WaitSnapshot(long generation, int stepCount, long stateHash) {
            this.generation = generation;
            this.stepCount = stepCount;
            this.stateHash = stateHash;
        }

        @Override
        public String getScenarioFingerprint() {
            return "wait-horizon-fixture";
        }

        @Override
        public long getStateHash() {
            return stateHash;
        }
    }
}

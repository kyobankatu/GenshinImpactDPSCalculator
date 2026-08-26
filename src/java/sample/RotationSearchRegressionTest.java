package sample;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import mechanics.rotation.EvolutionaryRotationSearcher;
import mechanics.rotation.ExpertPolicyPrior;
import mechanics.rotation.ExpertTrajectory;
import mechanics.rotation.MctsRotationSearcher;
import mechanics.rotation.PolicyAction;
import mechanics.rotation.RotationEnvironment;
import mechanics.rotation.RotationObjective;
import mechanics.rotation.RotationScenario;
import mechanics.rotation.RotationSearchConfig;
import mechanics.rotation.RotationSearchStrategy;
import mechanics.rotation.RotationStep;
import mechanics.rotation.RotationWaitGene;
import mechanics.rotation.TopKTrajectoryArchive;

/** Deterministic regression checks for bounded expert rotation search. */
public class RotationSearchRegressionTest {
    private static final int SETUP = PolicyAction.NORMAL.getId();
    private static final int BURST = PolicyAction.CHARGE.getId();

    public static void main(String[] args) {
        assertDeterministicDelayedReward(new EvolutionaryRotationSearcher());
        assertDeterministicDelayedReward(new MctsRotationSearcher());
        assertArchiveRetainsEqualScoreDiversity();
        assertPriorOnlySeedMarker();
        assertWaitGeneRoundTrip();
        assertWaitOnlySearch();
        assertCancellationBounded();
        assertInvalidInputsRejected();
        assertCorruptedSnapshotRejected();
        System.out.println("RotationSearchRegressionTest passed");
    }

    private static void assertDeterministicDelayedReward(RotationSearchStrategy strategy) {
        RotationSearchConfig config = config(240, 2, 2468L, () -> false);
        RotationSearchStrategy.Result first = strategy.search(DelayedRewardEnvironment::new, config);
        RotationSearchStrategy.Result second = strategy.search(DelayedRewardEnvironment::new, config);
        assertResultEquals(first, second);
        assertArrayEquals(new int[] {SETUP, BURST}, first.best.getActions(), "delayed optimum");
        assertClose(1000.0, first.best.getObjective().objectiveScore, "delayed objective");
        if (first.simulatorCalls != config.simulatorCallBudget) {
            throw new AssertionError("Search did not consume its exact simulator-call budget");
        }
    }

    private static void assertPriorOnlySeedMarker() {
        RotationSearchConfig config = config(40, 2, 12L, () -> false)
                .withInitialSeeds(List.<int[]>of(new int[0]));
        RotationSearchStrategy.Result result = new EvolutionaryRotationSearcher().search(
                DelayedRewardEnvironment::new,
                config);
        if (result.statistics.evaluatedSeeds != 1
                || result.best.getEvaluationMode()
                        != mechanics.rotation.RotationEvaluationMode.REPAIR) {
            throw new AssertionError("Empty prior-only seed marker was not repaired");
        }
    }

    private static void assertArchiveRetainsEqualScoreDiversity() {
        TopKTrajectoryArchive archive = new TopKTrajectoryArchive(2);
        RotationObjective objective = objective();
        ExpertTrajectory first = trajectory(
                new int[] {SETUP}, objective.evaluate(100.0, 1.0, 0.0, 0));
        ExpertTrajectory second = trajectory(
                new int[] {BURST}, objective.evaluate(100.0, 1.0, 0.0, 0));
        if (!archive.add(second) || !archive.add(first) || archive.size() != 2) {
            throw new AssertionError("Archive discarded a distinct equal-score trajectory");
        }
        if (archive.add(first)) {
            throw new AssertionError("Archive retained an exact duplicate trajectory");
        }
        assertArrayEquals(new int[] {SETUP}, archive.best().getActions(), "archive tie order");
    }

    private static void assertWaitOnlySearch() {
        RotationSearchConfig config = config(8, 3, 99L, () -> false);
        RotationSearchStrategy.Result result = new EvolutionaryRotationSearcher().search(
                WaitOnlyEnvironment::new,
                config);
        for (int actionId : result.best.getActions()) {
            if (actionId != PolicyAction.WAIT_SHORT.getId()) {
                throw new AssertionError("WAIT-only search emitted action " + actionId);
            }
        }
    }

    private static void assertWaitGeneRoundTrip() {
        int wait = PolicyAction.WAIT_SHORT.getId();
        int[] actions = {
                SETUP,
                wait,
                wait,
                BURST,
                wait,
                wait,
                wait,
                wait,
                wait,
                wait,
                wait,
                wait,
                wait,
                wait,
                wait,
                wait
        };
        List<RotationWaitGene> genes = RotationWaitGene.compress(actions, 10);
        assertArrayEquals(
                actions,
                RotationWaitGene.expand(genes, actions.length, 10),
                "Wait gene round trip");
        if (genes.get(1).getRunLength() != 2
                || genes.get(3).getRunLength() != 10
                || genes.get(4).getRunLength() != 2) {
            throw new AssertionError("Wait runs were not split at the maximum");
        }
        assertArrayEquals(
                new int[0],
                RotationWaitGene.expand(
                        RotationWaitGene.compress(new int[0], 10), 0, 10),
                "empty Wait gene marker");
        assertArrayEquals(
                new int[] {wait},
                RotationWaitGene.expand(
                        List.of(RotationWaitGene.of(wait, 1)), 1, 1),
                "single Wait gene");
        expectFailure(
                () -> RotationWaitGene.of(wait, 0),
                "zero Wait run");
        expectFailure(
                () -> RotationWaitGene.of(SETUP, 2),
                "non-Wait repeated gene");
        expectFailure(
                () -> RotationWaitGene.of(PolicyAction.SIZE, 1),
                "unknown gene action");
        expectFailure(
                () -> RotationWaitGene.expand(genes, actions.length - 1, 10),
                "Wait expansion above maxActions");
        expectFailure(
                () -> RotationWaitGene.expand(
                        List.of(RotationWaitGene.of(wait, 2)), 2, 1),
                "Wait run above configured maximum");
        java.util.ArrayList<RotationWaitGene> nullGene =
                new java.util.ArrayList<>();
        nullGene.add(null);
        expectFailure(
                () -> RotationWaitGene.expand(nullGene, 1, 1),
                "null Wait gene");
    }

    private static void assertCancellationBounded() {
        AtomicBoolean cancel = new AtomicBoolean(false);
        RotationSearchConfig config = config(100, 2, 10L, () -> cancel.getAndSet(true));
        RotationSearchStrategy.Result result = new EvolutionaryRotationSearcher().search(
                DelayedRewardEnvironment::new,
                config);
        if (!result.cancelled || result.simulatorCalls <= 0 || result.simulatorCalls >= 100) {
            throw new AssertionError("Cancellation did not return bounded partial search results");
        }
    }

    private static void assertInvalidInputsRejected() {
        expectFailure(() -> config(0, 2, 1L, () -> false), "zero budget");
        RotationSearchConfig invalidPrior = new RotationSearchConfig(
                10,
                2,
                2,
                4,
                1,
                1.0,
                1L,
                step -> new double[1],
                () -> false,
                List.of());
        expectFailure(() -> new EvolutionaryRotationSearcher().search(
                DelayedRewardEnvironment::new,
                invalidPrior), "prior dimension");
        expectFailure(() -> new EvolutionaryRotationSearcher().search(() -> null,
                config(10, 2, 1L, () -> false)), "null environment");
    }

    private static void assertCorruptedSnapshotRejected() {
        expectFailure(() -> new MctsRotationSearcher().search(
                CorruptedSnapshotEnvironment::new,
                config(20, 2, 4L, () -> false)), "corrupted snapshot");
    }

    private static RotationSearchConfig config(
            int budget,
            int maxActions,
            long seed,
            java.util.function.BooleanSupplier cancellation) {
        return new RotationSearchConfig(
                budget,
                maxActions,
                4,
                6,
                2,
                Math.sqrt(2.0),
                seed,
                ExpertPolicyPrior.uniform(),
                cancellation,
                List.of());
    }

    private static ExpertTrajectory trajectory(int[] actions, RotationObjective.Score score) {
        return new ExpertTrajectory(actions, score, Arrays.hashCode(actions), true, actions.length);
    }

    private static RotationObjective objective() {
        return new RotationObjective(0.0, 0.0, 0.0, 0.0);
    }

    private static void assertResultEquals(
            RotationSearchStrategy.Result expected,
            RotationSearchStrategy.Result actual) {
        if (expected.simulatorCalls != actual.simulatorCalls
                || expected.archive.size() != actual.archive.size()
                || expected.publishable != actual.publishable
                || expected.statistics.evaluatedTrajectories
                        != actual.statistics.evaluatedTrajectories
                || expected.statistics.completedTrajectories
                        != actual.statistics.completedTrajectories
                || expected.statistics.completedPopulations
                        != actual.statistics.completedPopulations
                || expected.statistics.completedGenerations
                        != actual.statistics.completedGenerations
                || expected.statistics.repairedActions
                        != actual.statistics.repairedActions) {
            throw new AssertionError("Seeded search result metadata changed");
        }
        for (int index = 0; index < expected.archive.size(); index++) {
            ExpertTrajectory left = expected.archive.get(index);
            ExpertTrajectory right = actual.archive.get(index);
            assertArrayEquals(left.getActions(), right.getActions(), "seeded archive actions");
            assertClose(left.getObjective().objectiveScore,
                    right.getObjective().objectiveScore, "seeded archive objective");
        }
    }

    private static void assertArrayEquals(int[] expected, int[] actual, String message) {
        if (!Arrays.equals(expected, actual)) {
            throw new AssertionError(message + ": expected=" + Arrays.toString(expected)
                    + " actual=" + Arrays.toString(actual));
        }
    }

    private static void assertClose(double expected, double actual, String message) {
        if (Math.abs(expected - actual) > 1e-9) {
            throw new AssertionError(message + ": expected=" + expected + " actual=" + actual);
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

    private static class DelayedRewardEnvironment implements RotationEnvironment {
        private int stepCount;
        private boolean setup;
        private double damage;
        private long generation;
        private RotationStep current;
        private boolean closed;

        @Override
        public RotationStep reset() {
            ensureOpen();
            stepCount = 0;
            setup = false;
            damage = 0.0;
            generation++;
            current = state(-1, true);
            return current.copy();
        }

        @Override
        public RotationStep step(int actionId) {
            ensureReady();
            if (!isLegal(actionId) || current.done) {
                throw new IllegalStateException("Fixture received an illegal action " + actionId);
            }
            if (stepCount == 0 && actionId == SETUP) {
                setup = true;
            } else if (stepCount == 1 && actionId == BURST) {
                damage += setup ? 1000.0 : 100.0;
            } else if (actionId == BURST) {
                damage += 100.0;
            }
            stepCount++;
            current = state(actionId, true);
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
            return new FixtureSnapshot(generation, stepCount, setup, damage, current.stateHash);
        }

        @Override
        public RotationStep restore(Snapshot snapshot) {
            ensureReady();
            if (!(snapshot instanceof FixtureSnapshot)) {
                throw new IllegalArgumentException("Foreign fixture snapshot");
            }
            FixtureSnapshot fixture = (FixtureSnapshot) snapshot;
            if (fixture.generation != generation) {
                throw new IllegalArgumentException("Stale fixture snapshot");
            }
            stepCount = fixture.stepCount;
            setup = fixture.setup;
            damage = fixture.damage;
            current = state(stepCount == 0 ? -1 : SETUP, true);
            if (current.stateHash != fixture.stateHash) {
                throw new IllegalStateException("Fixture snapshot hash mismatch");
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

        protected RotationStep state(int actionId, boolean validAction) {
            boolean done = stepCount >= 2;
            double[] mask = new double[PolicyAction.SIZE];
            if (!done) {
                mask[SETUP] = 1.0;
                mask[BURST] = 1.0;
            }
            long hash = 31L * stepCount + (setup ? 7L : 0L)
                    + Double.doubleToLongBits(damage);
            return new RotationStep(
                    new double[] {stepCount, setup ? 1.0 : 0.0, damage},
                    new double[0],
                    mask,
                    0.0,
                    done,
                    validAction,
                    damage,
                    damage,
                    stepCount,
                    actionId,
                    stepCount,
                    0,
                    hash,
                    objective().evaluate(damage, stepCount, 0.0, 0));
        }

        protected void ensureReady() {
            ensureOpen();
            if (current == null) {
                throw new IllegalStateException("Fixture must be reset");
            }
        }

        private boolean isLegal(int actionId) {
            return actionId >= 0 && actionId < current.legalActionMask.length
                    && current.legalActionMask[actionId] > 0.5;
        }

        private void ensureOpen() {
            if (closed) {
                throw new IllegalStateException("Fixture is closed");
            }
        }
    }

    private static final class WaitOnlyEnvironment extends DelayedRewardEnvironment {
        @Override
        protected RotationStep state(int actionId, boolean validAction) {
            RotationStep base = super.state(actionId, validAction);
            double[] mask = new double[PolicyAction.SIZE];
            if (!base.done) {
                mask[PolicyAction.WAIT_SHORT.getId()] = 1.0;
            }
            return new RotationStep(
                    base.observation,
                    base.privilegedObservation,
                    mask,
                    base.reward,
                    base.done,
                    validAction,
                    base.damageDelta,
                    base.totalDamage,
                    base.timeDelta,
                    actionId,
                    base.stepCount,
                    base.partyId,
                    base.stateHash,
                    base.objective);
        }
    }

    private static final class CorruptedSnapshotEnvironment extends DelayedRewardEnvironment {
        @Override
        public RotationStep restore(Snapshot snapshot) {
            RotationStep restored = super.restore(snapshot);
            return new RotationStep(
                    restored.observation,
                    restored.privilegedObservation,
                    restored.legalActionMask,
                    restored.reward,
                    restored.done,
                    restored.validAction,
                    restored.damageDelta,
                    restored.totalDamage,
                    restored.timeDelta,
                    restored.actionId,
                    restored.stepCount,
                    restored.partyId,
                    restored.stateHash + 1L,
                    restored.objective);
        }
    }

    private static final class FixtureSnapshot implements RotationEnvironment.Snapshot {
        private final long generation;
        private final int stepCount;
        private final boolean setup;
        private final double damage;
        private final long stateHash;

        private FixtureSnapshot(
                long generation,
                int stepCount,
                boolean setup,
                double damage,
                long stateHash) {
            this.generation = generation;
            this.stepCount = stepCount;
            this.setup = setup;
            this.damage = damage;
            this.stateHash = stateHash;
        }

        @Override
        public String getScenarioFingerprint() {
            return "delayed-reward-fixture";
        }

        @Override
        public long getStateHash() {
            return stateHash;
        }
    }
}

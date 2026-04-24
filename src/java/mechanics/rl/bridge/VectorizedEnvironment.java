package mechanics.rl.bridge;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Supplier;

import mechanics.rl.ActionResult;
import mechanics.rl.BattleEnvironment;
import mechanics.rl.EpisodeConfig;
import mechanics.rl.QuietExecution;
import simulation.CombatSimulator;

/**
 * Manages many independent battle environments behind one runner id.
 */
public class VectorizedEnvironment {
    private final List<BattleEnvironment> environments = new ArrayList<>();
    private final double[] episodeRewards;
    private final double[] episodeDamages;
    private final ExecutorService executor;
    private final int workerCount;
    private long resetCalls;
    private long resetNanos;
    private long stepCalls;
    private long stepNanos;
    private long envSteps;
    private long completedEpisodes;

    public VectorizedEnvironment(int count, Supplier<CombatSimulator> simulatorFactory, EpisodeConfig config) {
        this.episodeRewards = new double[count];
        this.episodeDamages = new double[count];
        this.workerCount = Math.max(1, Math.min(count, Runtime.getRuntime().availableProcessors()));
        this.executor = workerCount > 1 ? Executors.newFixedThreadPool(workerCount) : null;
        for (int i = 0; i < count; i++) {
            environments.add(new BattleEnvironment(simulatorFactory, config));
        }
    }

    public int size() {
        return environments.size();
    }

    public RunnerResetResult reset(boolean generateReport) {
        if (!generateReport) {
            return QuietExecution.call(() -> resetInternal(false));
        }
        return resetInternal(true);
    }

    private RunnerResetResult resetInternal(boolean generateReport) {
        long start = System.nanoTime();
        double[][] observations = new double[size()][];
        double[][] actionMasks = new double[size()][];
        parallelForEach(index -> {
            episodeRewards[index] = 0.0;
            episodeDamages[index] = 0.0;
            BattleEnvironment.ResetResult reset = environments.get(index).reset(generateReport && index == 0);
            observations[index] = reset.observation;
            actionMasks[index] = reset.actionMask;
        });
        resetCalls++;
        resetNanos += System.nanoTime() - start;
        return new RunnerResetResult(observations, actionMasks);
    }

    public RunnerStepResult step(int[] actions) {
        return QuietExecution.call(() -> stepInternal(actions));
    }

    private RunnerStepResult stepInternal(int[] actions) {
        long start = System.nanoTime();
        double[][] observations = new double[size()][];
        double[][] actionMasks = new double[size()][];
        double[] rewards = new double[size()];
        boolean[] dones = new boolean[size()];
        boolean[] validActions = new boolean[size()];
        double[] damageDeltas = new double[size()];
        double[] totalDamages = new double[size()];
        double[] finishedEpisodeRewards = new double[size()];
        double[] finishedEpisodeDamages = new double[size()];
        int[] finishedEpisodeSteps = new int[size()];
        int[] liveSteps = new int[size()];

        parallelForEach(index -> {
            ActionResult result = environments.get(index).step(actions[index]);
            episodeRewards[index] += result.reward;
            episodeDamages[index] = result.totalDamage;

            rewards[index] = result.reward;
            dones[index] = result.done;
            validActions[index] = result.validAction;
            damageDeltas[index] = result.damageDelta;
            totalDamages[index] = result.totalDamage;
            liveSteps[index] = result.stepCount;

            if (result.done) {
                synchronized (this) {
                    completedEpisodes++;
                }
                finishedEpisodeRewards[index] = episodeRewards[index];
                finishedEpisodeDamages[index] = episodeDamages[index];
                finishedEpisodeSteps[index] = result.stepCount;
                BattleEnvironment.ResetResult reset = environments.get(index).reset(false);
                observations[index] = reset.observation;
                actionMasks[index] = reset.actionMask;
                episodeRewards[index] = 0.0;
                episodeDamages[index] = 0.0;
            } else {
                observations[index] = result.observation;
                actionMasks[index] = result.actionMask;
            }
        });
        stepCalls++;
        envSteps += size();
        stepNanos += System.nanoTime() - start;

        return new RunnerStepResult(
                observations,
                actionMasks,
                rewards,
                dones,
                validActions,
                damageDeltas,
                totalDamages,
                finishedEpisodeRewards,
                finishedEpisodeDamages,
                finishedEpisodeSteps,
                liveSteps);
    }

    public void close() {
        if (executor != null) {
            executor.shutdown();
        }
    }

    public MetricsSnapshot metricsSnapshot() {
        return new MetricsSnapshot(resetCalls, resetNanos, stepCalls, stepNanos, envSteps, completedEpisodes, workerCount);
    }

    private void parallelForEach(IndexConsumer consumer) {
        if (workerCount <= 1 || size() <= 1) {
            for (int i = 0; i < size(); i++) {
                consumer.accept(i);
            }
            return;
        }
        List<Callable<Void>> tasks = new ArrayList<>();
        int shardSize = Math.max(1, (size() + workerCount - 1) / workerCount);
        for (int start = 0; start < size(); start += shardSize) {
            final int from = start;
            final int to = Math.min(size(), start + shardSize);
            tasks.add(() -> {
                for (int index = from; index < to; index++) {
                    consumer.accept(index);
                }
                return null;
            });
        }
        try {
            List<Future<Void>> futures = executor.invokeAll(tasks);
            for (Future<Void> future : futures) {
                future.get();
            }
        } catch (Exception e) {
            throw new RuntimeException("Vectorized environment parallel execution failed", e);
        }
    }

    @FunctionalInterface
    private interface IndexConsumer {
        void accept(int index);
    }

    /**
     * Reset response.
     */
    public static class RunnerResetResult {
        public final double[][] observations;
        public final double[][] actionMasks;

        public RunnerResetResult(double[][] observations, double[][] actionMasks) {
            this.observations = observations;
            this.actionMasks = actionMasks;
        }
    }

    public static class MetricsSnapshot {
        public final long resetCalls;
        public final long resetNanos;
        public final long stepCalls;
        public final long stepNanos;
        public final long envSteps;
        public final long completedEpisodes;
        public final int workerCount;

        public MetricsSnapshot(long resetCalls, long resetNanos, long stepCalls, long stepNanos, long envSteps,
                long completedEpisodes, int workerCount) {
            this.resetCalls = resetCalls;
            this.resetNanos = resetNanos;
            this.stepCalls = stepCalls;
            this.stepNanos = stepNanos;
            this.envSteps = envSteps;
            this.completedEpisodes = completedEpisodes;
            this.workerCount = workerCount;
        }

        public double meanResetMillis() {
            return resetCalls == 0 ? 0.0 : (resetNanos / 1_000_000.0) / resetCalls;
        }

        public double meanStepMillis() {
            return stepCalls == 0 ? 0.0 : (stepNanos / 1_000_000.0) / stepCalls;
        }

        public double envStepsPerSecond() {
            return stepNanos == 0 ? 0.0 : envSteps / (stepNanos / 1_000_000_000.0);
        }

        public String toSummaryString() {
            return String.format(
                    "workers=%d resetCalls=%d meanResetMs=%.3f stepCalls=%d meanStepMs=%.3f envSteps=%d envSteps/s=%.1f completedEpisodes=%d",
                    workerCount, resetCalls, meanResetMillis(), stepCalls, meanStepMillis(), envSteps,
                    envStepsPerSecond(), completedEpisodes);
        }
    }
}

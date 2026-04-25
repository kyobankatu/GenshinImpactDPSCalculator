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
    private long resetDispatchNanos;
    private long resetWaitNanos;
    private long stepDispatchNanos;
    private long stepWaitNanos;

    public VectorizedEnvironment(int count, Supplier<CombatSimulator> simulatorFactory, EpisodeConfig config) {
        this(count, simulatorFactory, config, 0);
    }

    public VectorizedEnvironment(int count, Supplier<CombatSimulator> simulatorFactory, EpisodeConfig config, int requestedWorkers) {
        this.episodeRewards = new double[count];
        this.episodeDamages = new double[count];
        int autoWorkers = Math.max(1, Math.min(count, Runtime.getRuntime().availableProcessors()));
        this.workerCount = requestedWorkers > 0 ? Math.max(1, Math.min(count, requestedWorkers)) : autoWorkers;
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
        ParallelTiming timing = parallelForEach(index -> {
            episodeRewards[index] = 0.0;
            episodeDamages[index] = 0.0;
            BattleEnvironment.ResetResult reset = environments.get(index).reset(generateReport && index == 0);
            observations[index] = reset.observation;
            actionMasks[index] = reset.actionMask;
        });
        resetCalls++;
        resetDispatchNanos += timing.dispatchNanos;
        resetWaitNanos += timing.waitNanos;
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

        ParallelTiming timing = parallelForEach(index -> {
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
        stepDispatchNanos += timing.dispatchNanos;
        stepWaitNanos += timing.waitNanos;
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
        return new MetricsSnapshot(
                resetCalls,
                resetNanos,
                resetDispatchNanos,
                resetWaitNanos,
                stepCalls,
                stepNanos,
                stepDispatchNanos,
                stepWaitNanos,
                envSteps,
                completedEpisodes,
                workerCount);
    }

    private ParallelTiming parallelForEach(IndexConsumer consumer) {
        if (workerCount <= 1 || size() <= 1) {
            for (int i = 0; i < size(); i++) {
                consumer.accept(i);
            }
            return ParallelTiming.ZERO;
        }
        long dispatchStart = System.nanoTime();
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
            List<Future<Void>> futures = new ArrayList<>(tasks.size());
            for (Callable<Void> task : tasks) {
                futures.add(executor.submit(task));
            }
            long dispatchNanos = System.nanoTime() - dispatchStart;
            long waitStart = System.nanoTime();
            for (Future<Void> future : futures) {
                future.get();
            }
            long waitNanos = System.nanoTime() - waitStart;
            return new ParallelTiming(dispatchNanos, waitNanos);
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
        public final long resetDispatchNanos;
        public final long resetWaitNanos;
        public final long stepCalls;
        public final long stepNanos;
        public final long stepDispatchNanos;
        public final long stepWaitNanos;
        public final long envSteps;
        public final long completedEpisodes;
        public final int workerCount;

        public MetricsSnapshot(long resetCalls, long resetNanos, long resetDispatchNanos, long resetWaitNanos,
                long stepCalls, long stepNanos, long stepDispatchNanos, long stepWaitNanos, long envSteps,
                long completedEpisodes, int workerCount) {
            this.resetCalls = resetCalls;
            this.resetNanos = resetNanos;
            this.resetDispatchNanos = resetDispatchNanos;
            this.resetWaitNanos = resetWaitNanos;
            this.stepCalls = stepCalls;
            this.stepNanos = stepNanos;
            this.stepDispatchNanos = stepDispatchNanos;
            this.stepWaitNanos = stepWaitNanos;
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

        public double meanResetDispatchMillis() {
            return resetCalls == 0 ? 0.0 : (resetDispatchNanos / 1_000_000.0) / resetCalls;
        }

        public double meanResetWaitMillis() {
            return resetCalls == 0 ? 0.0 : (resetWaitNanos / 1_000_000.0) / resetCalls;
        }

        public double meanStepDispatchMillis() {
            return stepCalls == 0 ? 0.0 : (stepDispatchNanos / 1_000_000.0) / stepCalls;
        }

        public double meanStepWaitMillis() {
            return stepCalls == 0 ? 0.0 : (stepWaitNanos / 1_000_000.0) / stepCalls;
        }

        public double envStepsPerSecond() {
            return stepNanos == 0 ? 0.0 : envSteps / (stepNanos / 1_000_000_000.0);
        }

        public String toSummaryString() {
            return String.format(
                    "workers=%d resetCalls=%d meanResetMs=%.3f meanResetDispatchMs=%.3f meanResetWaitMs=%.3f stepCalls=%d meanStepMs=%.3f meanStepDispatchMs=%.3f meanStepWaitMs=%.3f envSteps=%d envSteps/s=%.1f completedEpisodes=%d",
                    workerCount, resetCalls, meanResetMillis(), meanResetDispatchMillis(), meanResetWaitMillis(),
                    stepCalls, meanStepMillis(), meanStepDispatchMillis(), meanStepWaitMillis(), envSteps,
                    envStepsPerSecond(), completedEpisodes);
        }
    }

    private static final class ParallelTiming {
        private static final ParallelTiming ZERO = new ParallelTiming(0L, 0L);

        private final long dispatchNanos;
        private final long waitNanos;

        private ParallelTiming(long dispatchNanos, long waitNanos) {
            this.dispatchNanos = dispatchNanos;
            this.waitNanos = waitNanos;
        }
    }
}

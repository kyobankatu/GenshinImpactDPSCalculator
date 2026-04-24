package mechanics.rl.bridge;

import java.util.ArrayList;
import java.util.List;
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
    private long resetCalls;
    private long resetNanos;
    private long stepCalls;
    private long stepNanos;
    private long envSteps;
    private long completedEpisodes;

    public VectorizedEnvironment(int count, Supplier<CombatSimulator> simulatorFactory, EpisodeConfig config) {
        this.episodeRewards = new double[count];
        this.episodeDamages = new double[count];
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
        for (int i = 0; i < size(); i++) {
            episodeRewards[i] = 0.0;
            episodeDamages[i] = 0.0;
            BattleEnvironment.ResetResult reset = environments.get(i).reset(generateReport && i == 0);
            observations[i] = reset.observation;
            actionMasks[i] = reset.actionMask;
        }
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

        for (int i = 0; i < size(); i++) {
            ActionResult result = environments.get(i).step(actions[i]);
            episodeRewards[i] += result.reward;
            episodeDamages[i] = result.totalDamage;

            rewards[i] = result.reward;
            dones[i] = result.done;
            validActions[i] = result.validAction;
            damageDeltas[i] = result.damageDelta;
            totalDamages[i] = result.totalDamage;
            liveSteps[i] = result.stepCount;

            if (result.done) {
                completedEpisodes++;
                finishedEpisodeRewards[i] = episodeRewards[i];
                finishedEpisodeDamages[i] = episodeDamages[i];
                finishedEpisodeSteps[i] = result.stepCount;
                BattleEnvironment.ResetResult reset = environments.get(i).reset(false);
                observations[i] = reset.observation;
                actionMasks[i] = reset.actionMask;
                episodeRewards[i] = 0.0;
                episodeDamages[i] = 0.0;
            } else {
                observations[i] = result.observation;
                actionMasks[i] = result.actionMask;
            }
        }
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

    public MetricsSnapshot metricsSnapshot() {
        return new MetricsSnapshot(resetCalls, resetNanos, stepCalls, stepNanos, envSteps, completedEpisodes);
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

        public MetricsSnapshot(long resetCalls, long resetNanos, long stepCalls, long stepNanos, long envSteps,
                long completedEpisodes) {
            this.resetCalls = resetCalls;
            this.resetNanos = resetNanos;
            this.stepCalls = stepCalls;
            this.stepNanos = stepNanos;
            this.envSteps = envSteps;
            this.completedEpisodes = completedEpisodes;
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
                    "resetCalls=%d meanResetMs=%.3f stepCalls=%d meanStepMs=%.3f envSteps=%d envSteps/s=%.1f completedEpisodes=%d",
                    resetCalls, meanResetMillis(), stepCalls, meanStepMillis(), envSteps, envStepsPerSecond(),
                    completedEpisodes);
        }
    }
}

package mechanics.rl.bridge;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import mechanics.rl.ActionResult;
import mechanics.rl.ActionSpace;
import mechanics.rl.BattleEnvironment;
import mechanics.rl.EpisodeConfig;
import mechanics.rl.ObservationEncoder;
import mechanics.rl.PrivilegedStateEncoder;
import mechanics.rl.QuietExecution;
import mechanics.rl.RLAction;
import mechanics.rl.RLEpisodeFactory;
import mechanics.rl.RewardFunction;
import mechanics.rl.EpisodeRoleSummary;
import simulation.CombatSimulator;
import simulation.SimulatorSnapshot;

/**
 * Manages many independent battle environments behind one runner id.
 */
public class VectorizedEnvironment {
    private final List<BattleEnvironment> environments = new ArrayList<>();
    private final double[] episodeRewards;
    private final double[] episodeDamages;
    private final ExecutorService executor;
    private final int workerCount;
    private final ConcurrentHashMap<Integer, SnapshotEntry> snapshotStore = new ConcurrentHashMap<>();
    private final AtomicInteger nextSnapshotId = new AtomicInteger(1);
    private final boolean vineEnabled;
    private BattleEnvironment branchEnv;
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

    /**
     * Creates a vectorized environment backed by simulator factories and default encoders.
     *
     * @param count number of parallel environments
     * @param simulatorFactory factory for combat simulators
     * @param config episode configuration
     */
    public VectorizedEnvironment(int count, Supplier<CombatSimulator> simulatorFactory, EpisodeConfig config) {
        this(count, simulatorFactory, config, 0);
    }

    /**
     * Creates a vectorized environment backed by simulator factories and default encoders.
     *
     * @param count number of parallel environments
     * @param simulatorFactory factory for combat simulators
     * @param config episode configuration
     * @param requestedWorkers number of worker threads to use, or {@code 0} for auto
     */
    public VectorizedEnvironment(int count, Supplier<CombatSimulator> simulatorFactory, EpisodeConfig config, int requestedWorkers) {
        this(count, simulatorFactory, config, requestedWorkers, new ObservationEncoder(), new PrivilegedStateEncoder(), false);
    }

    /**
     * Creates a vectorized environment backed by an episode factory.
     *
     * @param count number of parallel environments
     * @param episodeFactory factory for per-episode battle setups
     * @param requestedWorkers number of worker threads to use, or {@code 0} for auto
     * @param observationEncoder encoder shared by all environments
     */
    public VectorizedEnvironment(int count, RLEpisodeFactory episodeFactory, int requestedWorkers,
            ObservationEncoder observationEncoder) {
        this(count, episodeFactory, requestedWorkers, observationEncoder, new PrivilegedStateEncoder(), false);
    }

    /**
     * Creates a vectorized environment backed by an episode factory.
     *
     * @param count number of parallel environments
     * @param episodeFactory factory for per-episode battle setups
     * @param requestedWorkers number of worker threads to use, or {@code 0} for auto
     * @param observationEncoder encoder shared by all environments
     * @param privilegedStateEncoder privileged-state encoder shared by all environments
     */
    public VectorizedEnvironment(int count, RLEpisodeFactory episodeFactory, int requestedWorkers,
            ObservationEncoder observationEncoder, PrivilegedStateEncoder privilegedStateEncoder) {
        this(count, episodeFactory, requestedWorkers, observationEncoder, privilegedStateEncoder, false);
    }

    /**
     * Creates a vectorized environment backed by an episode factory.
     *
     * @param count number of parallel environments
     * @param episodeFactory factory for per-episode battle setups
     * @param requestedWorkers number of worker threads to use, or {@code 0} for auto
     * @param observationEncoder encoder shared by all environments
     * @param privilegedStateEncoder privileged-state encoder shared by all environments
     * @param vineEnabled if true, simulator snapshots are saved during step for VinePPO branch rollouts
     */
    public VectorizedEnvironment(int count, RLEpisodeFactory episodeFactory, int requestedWorkers,
            ObservationEncoder observationEncoder, PrivilegedStateEncoder privilegedStateEncoder, boolean vineEnabled) {
        this.episodeRewards = new double[count];
        this.episodeDamages = new double[count];
        this.vineEnabled = vineEnabled;
        int autoWorkers = Math.max(1, Math.min(count, Runtime.getRuntime().availableProcessors()));
        this.workerCount = requestedWorkers > 0 ? Math.max(1, Math.min(count, requestedWorkers)) : autoWorkers;
        this.executor = workerCount > 1 ? Executors.newFixedThreadPool(workerCount) : null;
        for (int i = 0; i < count; i++) {
            environments.add(new BattleEnvironment(
                    episodeFactory, new ActionSpace(), observationEncoder, privilegedStateEncoder, new RewardFunction()));
        }
        this.branchEnv = new BattleEnvironment(
                episodeFactory, new ActionSpace(), observationEncoder, privilegedStateEncoder, new RewardFunction());
    }

    /**
     * Constructs a vectorized environment with a shared {@link ObservationEncoder}.
     * Use this overload to avoid reloading capability profiles for each environment instance.
     *
     * @param count              number of parallel environments
     * @param simulatorFactory   factory for creating combat simulators
     * @param config             episode configuration
     * @param requestedWorkers   number of parallel worker threads (0 = auto)
     * @param observationEncoder shared observation encoder to use for all environments
     */
    public VectorizedEnvironment(int count, Supplier<CombatSimulator> simulatorFactory, EpisodeConfig config,
            int requestedWorkers, ObservationEncoder observationEncoder) {
        this(count, simulatorFactory, config, requestedWorkers, observationEncoder, new PrivilegedStateEncoder(), false);
    }

    /**
     * Creates a vectorized environment with shared encoders.
     *
     * @param count number of parallel environments
     * @param simulatorFactory factory for combat simulators
     * @param config episode configuration
     * @param requestedWorkers number of worker threads to use, or {@code 0} for auto
     * @param observationEncoder shared observation encoder to use for all environments
     * @param privilegedStateEncoder shared privileged-state encoder to use for all environments
     */
    public VectorizedEnvironment(int count, Supplier<CombatSimulator> simulatorFactory, EpisodeConfig config,
            int requestedWorkers, ObservationEncoder observationEncoder, PrivilegedStateEncoder privilegedStateEncoder) {
        this(count, simulatorFactory, config, requestedWorkers, observationEncoder, privilegedStateEncoder, false);
    }

    /**
     * Creates a vectorized environment with shared encoders.
     *
     * @param count number of parallel environments
     * @param simulatorFactory factory for combat simulators
     * @param config episode configuration
     * @param requestedWorkers number of worker threads to use, or {@code 0} for auto
     * @param observationEncoder shared observation encoder to use for all environments
     * @param privilegedStateEncoder shared privileged-state encoder to use for all environments
     * @param vineEnabled if true, simulator snapshots are saved during step for VinePPO branch rollouts
     */
    public VectorizedEnvironment(int count, Supplier<CombatSimulator> simulatorFactory, EpisodeConfig config,
            int requestedWorkers, ObservationEncoder observationEncoder, PrivilegedStateEncoder privilegedStateEncoder,
            boolean vineEnabled) {
        this.episodeRewards = new double[count];
        this.episodeDamages = new double[count];
        this.vineEnabled = vineEnabled;
        int autoWorkers = Math.max(1, Math.min(count, Runtime.getRuntime().availableProcessors()));
        this.workerCount = requestedWorkers > 0 ? Math.max(1, Math.min(count, requestedWorkers)) : autoWorkers;
        this.executor = workerCount > 1 ? Executors.newFixedThreadPool(workerCount) : null;
        for (int i = 0; i < count; i++) {
            environments.add(new BattleEnvironment(simulatorFactory, config,
                    new ActionSpace(), observationEncoder, privilegedStateEncoder, new RewardFunction()));
        }
        this.branchEnv = new BattleEnvironment(simulatorFactory, config,
                new ActionSpace(), observationEncoder, privilegedStateEncoder, new RewardFunction());
    }

    /**
     * Returns the number of managed environments.
     *
     * @return environment count
     */
    public int size() {
        return environments.size();
    }

    /**
     * Resets all environments.
     *
     * @param generateReport whether the first environment should emit a report
     * @return batched reset observations and masks
     */
    public RunnerResetResult reset(boolean generateReport) {
        return reset(generateReport, -1);
    }

    /**
     * Resets all environments, optionally preferring a specific party.
     *
     * @param generateReport whether the first environment should emit a report
     * @param preferredPartyId preferred party id, or {@code -1} for no preference
     * @return batched reset observations and masks
     */
    public RunnerResetResult reset(boolean generateReport, int preferredPartyId) {
        if (!generateReport) {
            return QuietExecution.call(() -> resetInternal(false, preferredPartyId));
        }
        return resetInternal(true, preferredPartyId);
    }

    private RunnerResetResult resetInternal(boolean generateReport, int preferredPartyId) {
        snapshotStore.clear();
        long start = System.nanoTime();
        double[][] observations = new double[size()][];
        double[][] privilegedObservations = new double[size()][];
        double[][] actionMasks = new double[size()][];
        int[] partyIds = new int[size()];
        ParallelTiming timing = parallelForEach(index -> {
            episodeRewards[index] = 0.0;
            episodeDamages[index] = 0.0;
            BattleEnvironment.ResetResult reset = environments.get(index).reset(generateReport && index == 0, preferredPartyId);
            observations[index] = reset.observation;
            privilegedObservations[index] = reset.privilegedObservation;
            actionMasks[index] = reset.actionMask;
            partyIds[index] = reset.partyId;
        });
        resetCalls++;
        resetDispatchNanos += timing.dispatchNanos;
        resetWaitNanos += timing.waitNanos;
        resetNanos += System.nanoTime() - start;
        return new RunnerResetResult(observations, privilegedObservations, actionMasks, partyIds);
    }

    /**
     * Steps every environment once with the provided action ids.
     *
     * @param actions one action id per environment
     * @return batched transition results
     */
    public RunnerStepResult step(int[] actions) {
        return QuietExecution.call(() -> stepInternal(actions));
    }

    private RunnerStepResult stepInternal(int[] actions) {
        long start = System.nanoTime();
        double[][] observations = new double[size()][];
        double[][] privilegedObservations = new double[size()][];
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
        int[] partyIds = new int[size()];
        int[] finishedEpisodePartyIds = new int[size()];
        double[] finishedEpisodeRoleAlignmentScores = new double[size()];
        double[] finishedEpisodeCarryAlignmentScores = new double[size()];
        double[] finishedEpisodeOffFieldAlignmentScores = new double[size()];
        double[] finishedEpisodeEntryAlignmentScores = new double[size()];
        double[] finishedEpisodeStayAlignmentScores = new double[size()];
        double[][] finishedEpisodeExpectedRoleVectors = new double[size()][EpisodeRoleSummary.VECTOR_SIZE];
        double[][] finishedEpisodeRealizedRoleVectors = new double[size()][EpisodeRoleSummary.VECTOR_SIZE];
        int[] vineSnapshotIds = new int[size()];
        java.util.Arrays.fill(vineSnapshotIds, -1);

        ParallelTiming timing = parallelForEach(index -> {
            if (vineEnabled && isVineSampleAction(actions[index])) {
                BattleEnvironment env = environments.get(index);
                SimulatorSnapshot snap = env.saveSnapshot();
                int snapId = nextSnapshotId.getAndIncrement();
                snapshotStore.put(snapId, new SnapshotEntry(
                        snap,
                        env.saveBranchState(),
                        env.getCurrentPartyId()));
                vineSnapshotIds[index] = snapId;
            }
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
                finishedEpisodePartyIds[index] = environments.get(index).getCurrentPartyId();
                if (result.episodeRoleSummary != null) {
                    finishedEpisodeRoleAlignmentScores[index] = result.episodeRoleSummary.roleAlignmentScore;
                    finishedEpisodeCarryAlignmentScores[index] = result.episodeRoleSummary.carryAlignmentScore;
                    finishedEpisodeOffFieldAlignmentScores[index] = result.episodeRoleSummary.offFieldAlignmentScore;
                    finishedEpisodeEntryAlignmentScores[index] = result.episodeRoleSummary.entryAlignmentScore;
                    finishedEpisodeStayAlignmentScores[index] = result.episodeRoleSummary.stayAlignmentScore;
                    finishedEpisodeExpectedRoleVectors[index] = result.episodeRoleSummary.expectedRoleVector.clone();
                    finishedEpisodeRealizedRoleVectors[index] = result.episodeRoleSummary.realizedRoleVector.clone();
                }
                BattleEnvironment.ResetResult reset = environments.get(index).reset(false);
                observations[index] = reset.observation;
                privilegedObservations[index] = reset.privilegedObservation;
                actionMasks[index] = reset.actionMask;
                partyIds[index] = reset.partyId;
                episodeRewards[index] = 0.0;
                episodeDamages[index] = 0.0;
            } else {
                observations[index] = result.observation;
                privilegedObservations[index] = result.privilegedObservation;
                actionMasks[index] = result.actionMask;
                partyIds[index] = environments.get(index).getCurrentPartyId();
            }
        });
        stepCalls++;
        stepDispatchNanos += timing.dispatchNanos;
        stepWaitNanos += timing.waitNanos;
        envSteps += size();
        stepNanos += System.nanoTime() - start;

        return new RunnerStepResult(
                observations,
                privilegedObservations,
                actionMasks,
                rewards,
                dones,
                validActions,
                damageDeltas,
                totalDamages,
                finishedEpisodeRewards,
                finishedEpisodeDamages,
                finishedEpisodeSteps,
                liveSteps,
                partyIds,
                finishedEpisodePartyIds,
                finishedEpisodeRoleAlignmentScores,
                finishedEpisodeCarryAlignmentScores,
                finishedEpisodeOffFieldAlignmentScores,
                finishedEpisodeEntryAlignmentScores,
                finishedEpisodeStayAlignmentScores,
                finishedEpisodeExpectedRoleVectors,
                finishedEpisodeRealizedRoleVectors,
                vineSnapshotIds);
    }

    /**
     * Discards all snapshots currently held in the vine snapshot store.
     * Called by the Python learner at the end of each update cycle to prevent
     * unbounded memory growth from snapshots that are saved but never consumed
     * (only {@code vine_max_points_per_update} of the saved snapshots are
     * actually evaluated each update).
     */
    public void releaseSnapshots() {
        snapshotStore.clear();
    }

    /**
     * Runs VinePPO branch rollouts for every valid action at a saved snapshot state.
     *
     * @param snapshotId ID returned by a prior step call in {@code vineSnapshotIds}
     * @param K number of independent branches per action
     * @param H horizon steps per branch
     * @param gamma discount factor
     * @return Monte Carlo Q estimates for each action, with {@code NaN} for invalid actions
     */
    public double[] branchRolloutMulti(int snapshotId, int K, int H, double gamma) {
        SnapshotEntry entry = snapshotStore.remove(snapshotId);
        if (entry == null) {
            throw new IllegalArgumentException("Unknown snapshot id: " + snapshotId);
        }
        branchEnv.reset(false, entry.partyId);
        return branchEnv.branchRolloutMultiAction(entry.snapshot, entry.branchState, K, H, gamma);
    }

    private static boolean isVineSampleAction(int actionId) {
        RLAction action = RLAction.fromId(actionId);
        return action == RLAction.SKILL || action == RLAction.BURST || action.isSwap();
    }

    /**
     * Shuts down worker threads owned by this vectorized environment.
     */
    public void close() {
        if (executor != null) {
            executor.shutdown();
        }
    }

    /**
     * Returns accumulated timing and throughput metrics for this runner.
     *
     * @return current metrics snapshot
     */
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
        public final double[][] privilegedObservations;
        public final double[][] actionMasks;
        public final int[] partyIds;

        /**
         * Creates a batched reset result.
         *
         * @param observations encoded observations for each environment
         * @param privilegedObservations privileged observations for each environment
         * @param actionMasks valid-action masks for each environment
         * @param partyIds selected party ids for each environment
         */
        public RunnerResetResult(double[][] observations, double[][] privilegedObservations,
                double[][] actionMasks, int[] partyIds) {
            this.observations = observations;
            this.privilegedObservations = privilegedObservations;
            this.actionMasks = actionMasks;
            this.partyIds = partyIds;
        }
    }

    /**
     * Immutable timing snapshot for a vectorized runner.
     */
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

        /**
         * Creates a metrics snapshot from accumulated counters.
         */
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

        /**
         * Returns the mean reset latency in milliseconds.
         *
         * @return average reset time
         */
        public double meanResetMillis() {
            return resetCalls == 0 ? 0.0 : (resetNanos / 1_000_000.0) / resetCalls;
        }

        /**
         * Returns the mean step latency in milliseconds.
         *
         * @return average step time
         */
        public double meanStepMillis() {
            return stepCalls == 0 ? 0.0 : (stepNanos / 1_000_000.0) / stepCalls;
        }

        /**
         * Returns the mean reset task submission time in milliseconds.
         *
         * @return average reset dispatch time
         */
        public double meanResetDispatchMillis() {
            return resetCalls == 0 ? 0.0 : (resetDispatchNanos / 1_000_000.0) / resetCalls;
        }

        /**
         * Returns the mean reset wait time in milliseconds.
         *
         * @return average reset wait time
         */
        public double meanResetWaitMillis() {
            return resetCalls == 0 ? 0.0 : (resetWaitNanos / 1_000_000.0) / resetCalls;
        }

        /**
         * Returns the mean step task submission time in milliseconds.
         *
         * @return average step dispatch time
         */
        public double meanStepDispatchMillis() {
            return stepCalls == 0 ? 0.0 : (stepDispatchNanos / 1_000_000.0) / stepCalls;
        }

        /**
         * Returns the mean step wait time in milliseconds.
         *
         * @return average step wait time
         */
        public double meanStepWaitMillis() {
            return stepCalls == 0 ? 0.0 : (stepWaitNanos / 1_000_000.0) / stepCalls;
        }

        /**
         * Returns aggregate environment steps per second.
         *
         * @return throughput in environment steps per second
         */
        public double envStepsPerSecond() {
            return stepNanos == 0 ? 0.0 : envSteps / (stepNanos / 1_000_000_000.0);
        }

        /**
         * Formats the metrics snapshot as a single-line summary.
         *
         * @return summary string
         */
        public String toSummaryString() {
            return String.format(
                    "workers=%d resetCalls=%d meanResetMs=%.3f meanResetDispatchMs=%.3f meanResetWaitMs=%.3f stepCalls=%d meanStepMs=%.3f meanStepDispatchMs=%.3f meanStepWaitMs=%.3f envSteps=%d envSteps/s=%.1f completedEpisodes=%d",
                    workerCount, resetCalls, meanResetMillis(), meanResetDispatchMillis(), meanResetWaitMillis(),
                    stepCalls, meanStepMillis(), meanStepDispatchMillis(), meanStepWaitMillis(), envSteps,
                    envStepsPerSecond(), completedEpisodes);
        }
    }

    private static final class SnapshotEntry {
        final SimulatorSnapshot snapshot;
        final BattleEnvironment.BranchStateSnapshot branchState;
        final int partyId;

        SnapshotEntry(SimulatorSnapshot snapshot, BattleEnvironment.BranchStateSnapshot branchState, int partyId) {
            this.snapshot = snapshot;
            this.branchState = branchState;
            this.partyId = partyId;
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

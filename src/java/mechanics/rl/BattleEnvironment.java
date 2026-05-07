package mechanics.rl;

import java.util.concurrent.atomic.LongAdder;
import java.util.function.Supplier;

import mechanics.analysis.StatsRecorder;
import model.entity.Character;
import simulation.CombatSimulator;
import simulation.action.CharacterActionRequest;
import visualization.HtmlReportGenerator;
import visualization.VisualLogger;

/**
 * Teacher-free RL environment backed by {@link CombatSimulator}.
 */
public class BattleEnvironment {
    private final RLEpisodeFactory episodeFactory;
    private final ActionSpace actionSpace;
    private final ObservationEncoder observationEncoder;
    private final PrivilegedStateEncoder privilegedStateEncoder;
    private final RewardFunction rewardFunction;

    private CombatSimulator simulator;
    private EpisodeConfig config;
    private int currentPartyId;
    private String currentPartyName;
    private double lastSwapTime = -999.0;
    private int previousActionId = -1;
    private int stepCount;
    private boolean generateReportOnDone;
    private StatsRecorder statsRecorder;
    private final double[] observationBuffer = new double[ObservationEncoder.OBSERVATION_SIZE];
    private final double[] privilegedObservationBuffer = new double[PrivilegedStateEncoder.STATE_SIZE];
    private final double[] actionMaskBuffer = new double[ActionSpace.SIZE];
    private final double[] preActionMaskBuffer = new double[ActionSpace.SIZE];
    private final double[] slotLastActiveTime = new double[ObservationEncoder.NUM_CHARS];
    private final double[] slotOnFieldTime = new double[ObservationEncoder.NUM_CHARS];

    private static final LongAdder STEP_CALLS = new LongAdder();
    private static final LongAdder STEP_NANOS = new LongAdder();
    private static final LongAdder EXECUTE_NANOS = new LongAdder();
    private static final LongAdder ENCODE_NANOS = new LongAdder();
    private static final LongAdder PRIVILEGED_ENCODE_NANOS = new LongAdder();
    private static final LongAdder MASK_NANOS = new LongAdder();
    private static final LongAdder RESET_CALLS = new LongAdder();
    private static final LongAdder RESET_NANOS = new LongAdder();

    public BattleEnvironment(Supplier<CombatSimulator> simulatorFactory, EpisodeConfig config) {
        this(simulatorFactory, config, new ActionSpace(), new ObservationEncoder(), new PrivilegedStateEncoder(),
                new RewardFunction());
    }

    public BattleEnvironment(RLEpisodeFactory episodeFactory) {
        this(episodeFactory, new ActionSpace(), new ObservationEncoder(), new PrivilegedStateEncoder(),
                new RewardFunction());
    }

    public BattleEnvironment(
            Supplier<CombatSimulator> simulatorFactory,
            EpisodeConfig config,
            ActionSpace actionSpace,
            ObservationEncoder observationEncoder,
            PrivilegedStateEncoder privilegedStateEncoder,
            RewardFunction rewardFunction) {
        this(new SinglePartyRLEpisodeFactory("SingleParty", config.partyOrder, simulatorFactory, config),
                actionSpace, observationEncoder, privilegedStateEncoder, rewardFunction);
    }

    public BattleEnvironment(
            RLEpisodeFactory episodeFactory,
            ActionSpace actionSpace,
            ObservationEncoder observationEncoder,
            PrivilegedStateEncoder privilegedStateEncoder,
            RewardFunction rewardFunction) {
        this.episodeFactory = episodeFactory;
        this.actionSpace = actionSpace;
        this.observationEncoder = observationEncoder;
        this.privilegedStateEncoder = privilegedStateEncoder;
        this.rewardFunction = rewardFunction;
    }

    public ResetResult reset(boolean generateReport) {
        return reset(generateReport, -1);
    }

    public ResetResult reset(boolean generateReport, int preferredPartyId) {
        long start = System.nanoTime();
        RLEpisodeFactory.EpisodeContext episode = episodeFactory.createEpisode(preferredPartyId);
        simulator = episode.simulator;
        config = episode.config;
        currentPartyId = episode.partyId;
        currentPartyName = episode.partyName;
        simulator.setLoggingEnabled(generateReport);
        if (generateReport) {
            VisualLogger.getInstance().clear();
            statsRecorder = new StatsRecorder(simulator, 0.5);
            statsRecorder.startRecording();
        } else {
            statsRecorder = null;
        }
        if (config.fillEnergyOnReset) {
            for (Character character : simulator.getPartyMembers()) {
                character.receiveFlatEnergy(character.getEnergyCost());
            }
        }
        lastSwapTime = -999.0;
        previousActionId = -1;
        stepCount = 0;
        generateReportOnDone = generateReport;
        java.util.Arrays.fill(slotLastActiveTime, -config.maxEpisodeTime);
        java.util.Arrays.fill(slotOnFieldTime, 0.0);

        fillObservationBuffer();
        fillPrivilegedObservationBuffer();
        fillActionMaskBuffer();
        RESET_CALLS.increment();
        RESET_NANOS.add(System.nanoTime() - start);
        return new ResetResult(observationBuffer, privilegedObservationBuffer, actionMaskBuffer, currentPartyId);
    }

    public ActionResult step(int actionId) {
        ensureReset();
        long stepStart = System.nanoTime();
        fillPreActionMaskBuffer();
        boolean validAction = actionSpace.isValid(actionId, preActionMaskBuffer);

        int activeSlotBefore = findActiveSlot();
        double timeBefore = simulator.getCurrentTime();
        double damageBefore = simulator.getTotalDamage();

        if (validAction) {
            long executeStart = System.nanoTime();
            execute(actionId);
            EXECUTE_NANOS.add(System.nanoTime() - executeStart);
        } else {
            simulator.advanceTime(config.failedActionTimeCost);
        }

        double timeDelta = simulator.getCurrentTime() - timeBefore;
        double damageDelta = simulator.getTotalDamage() - damageBefore;
        boolean done = simulator.getCurrentTime() >= config.maxEpisodeTime;
        double reward = rewardFunction.compute(
                config,
                actionId,
                previousActionId,
                validAction,
                damageDelta,
                simulator.getTotalDamage(),
                timeDelta,
                done);

        if (activeSlotBefore >= 0) {
            slotLastActiveTime[activeSlotBefore] = simulator.getCurrentTime();
            slotOnFieldTime[activeSlotBefore] += timeDelta;
        }
        previousActionId = actionId;
        stepCount++;

        if (done && generateReportOnDone) {
            String reportFile = buildPartyReportFileName(currentPartyName);
            HtmlReportGenerator.generate(
                    reportFile,
                    VisualLogger.getInstance().getRecords(),
                    simulator,
                    statsRecorder != null ? statsRecorder.getSnapshots() : null);
            generateReportOnDone = false;
        }

        fillObservationBuffer();
        fillPrivilegedObservationBuffer();
        fillActionMaskBuffer();
        STEP_CALLS.increment();
        STEP_NANOS.add(System.nanoTime() - stepStart);
        return new ActionResult(observationBuffer, privilegedObservationBuffer, actionMaskBuffer, reward, done,
                validAction, damageDelta,
                simulator.getTotalDamage(), timeDelta, actionId, stepCount);
    }

    /**
     * Returns the BattleEnvironment-tracked last swap time used for action masking.
     *
     * @return last swap time in seconds
     */
    public double getLastSwapTime() {
        return lastSwapTime;
    }

    /**
     * Saves a snapshot of the current simulator state for VinePPO branch rollouts.
     *
     * @return simulator snapshot
     */
    public simulation.SimulatorSnapshot saveSnapshot() {
        return simulator.saveSnapshot();
    }

    /**
     * Runs K independent Monte Carlo rollouts from a snapshot and returns their mean discounted return.
     *
     * <p>State is restored from {@code snap} before each branch. After the method returns the
     * environment state is undefined; the caller must reset or restore before using it again.
     *
     * @param snap        simulator state to branch from
     * @param snapLastSwapTime lastSwapTime captured at snapshot time
     * @param firstAction action ID to execute as the first step of each branch (-1 → random)
     * @param K           number of branches
     * @param H           horizon (max steps per branch, including firstAction)
     * @param gamma       discount factor
     * @return mean discounted return across K branches
     */
    public double branchRolloutMean(simulation.SimulatorSnapshot snap, double snapLastSwapTime,
            int firstAction, int K, int H, double gamma) {
        ensureReset();
        double totalReturn = 0.0;
        for (int k = 0; k < K; k++) {
            simulator.restoreSnapshot(snap);
            lastSwapTime = snapLastSwapTime;
            stepCount = 0;
            previousActionId = -1;
            generateReportOnDone = false;
            java.util.Arrays.fill(slotLastActiveTime, -config.maxEpisodeTime);
            java.util.Arrays.fill(slotOnFieldTime, 0.0);
            fillObservationBuffer();
            fillPrivilegedObservationBuffer();
            fillActionMaskBuffer();

            double discountedReturn = 0.0;
            double discount = 1.0;
            boolean done = false;

            int action = firstAction >= 0 ? firstAction : sampleRandomValidAction(actionMaskBuffer);
            ActionResult result = step(action);
            discountedReturn += discount * result.reward;
            discount *= gamma;
            done = result.done;

            for (int h = 1; h < H && !done; h++) {
                action = sampleRandomValidAction(actionMaskBuffer);
                result = step(action);
                discountedReturn += discount * result.reward;
                discount *= gamma;
                done = result.done;
            }
            totalReturn += discountedReturn;
        }
        return totalReturn / K;
    }

    private int sampleRandomValidAction(double[] mask) {
        int count = 0;
        for (double m : mask) {
            if (m > 0.5) {
                count++;
            }
        }
        if (count == 0) {
            return 0;
        }
        int pick = (int) (Math.random() * count);
        for (int i = 0; i < mask.length; i++) {
            if (mask[i] > 0.5) {
                if (pick == 0) {
                    return i;
                }
                pick--;
            }
        }
        return 0;
    }

    public CombatSimulator getSimulator() {
        return simulator;
    }

    public int getStepCount() {
        return stepCount;
    }

    public ActionSpace getActionSpace() {
        return actionSpace;
    }

    public EpisodeConfig getConfig() {
        return config;
    }

    public int getCurrentPartyId() {
        return currentPartyId;
    }

    public String getCurrentPartyName() {
        return currentPartyName;
    }

    private void execute(int actionId) {
        RLAction action = RLAction.fromId(actionId);
        if (action.isSwap()) {
            int slot = action.getTargetSlot();
            model.type.CharacterId targetId = config.partyOrder[slot];
            simulator.switchCharacter(targetId);
            lastSwapTime = simulator.getCurrentTime();
            return;
        }
        Character active = simulator.getActiveCharacter();
        if (active == null) {
            simulator.advanceTime(config.failedActionTimeCost);
            return;
        }
        simulator.performAction(active.getCharacterId(), CharacterActionRequest.of(action.getActionKey()));
    }

    private void ensureReset() {
        if (simulator == null) {
            reset(false);
        }
    }

    private String buildPartyReportFileName(String partyName) {
        if (partyName == null || partyName.isBlank()) {
            return "rl_report.html";
        }
        String slug = partyName
                .trim()
                .toLowerCase(java.util.Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
        return slug.isEmpty() ? "rl_report.html" : "rl_report_" + slug + ".html";
    }

    private void fillObservationBuffer() {
        long start = System.nanoTime();
        observationEncoder.fillObservation(simulator, config, lastSwapTime, slotLastActiveTime, slotOnFieldTime, observationBuffer);
        ENCODE_NANOS.add(System.nanoTime() - start);
    }

    private int findActiveSlot() {
        Character active = simulator.getActiveCharacter();
        if (active == null || config == null) {
            return -1;
        }
        for (int i = 0; i < config.partyOrder.length; i++) {
            if (config.partyOrder[i] == active.getCharacterId()) {
                return i;
            }
        }
        return -1;
    }

    private void fillPrivilegedObservationBuffer() {
        long start = System.nanoTime();
        privilegedStateEncoder.fillState(simulator, config, privilegedObservationBuffer);
        PRIVILEGED_ENCODE_NANOS.add(System.nanoTime() - start);
    }

    private void fillActionMaskBuffer() {
        long start = System.nanoTime();
        actionSpace.fillMask(simulator, lastSwapTime, config, actionMaskBuffer);
        MASK_NANOS.add(System.nanoTime() - start);
    }

    private void fillPreActionMaskBuffer() {
        long start = System.nanoTime();
        actionSpace.fillMask(simulator, lastSwapTime, config, preActionMaskBuffer);
        MASK_NANOS.add(System.nanoTime() - start);
    }

    public static TimingSnapshot timingSnapshot() {
        return new TimingSnapshot(
                STEP_CALLS.sum(),
                STEP_NANOS.sum(),
                EXECUTE_NANOS.sum(),
                ENCODE_NANOS.sum(),
                PRIVILEGED_ENCODE_NANOS.sum(),
                MASK_NANOS.sum(),
                RESET_CALLS.sum(),
                RESET_NANOS.sum());
    }

    /**
     * Reset return values.
     */
    public static class ResetResult {
        public final double[] observation;
        public final double[] privilegedObservation;
        public final double[] actionMask;
        public final int partyId;

        public ResetResult(double[] observation, double[] privilegedObservation, double[] actionMask, int partyId) {
            this.observation = observation;
            this.privilegedObservation = privilegedObservation;
            this.actionMask = actionMask;
            this.partyId = partyId;
        }
    }

    public static class TimingSnapshot {
        public final long stepCalls;
        public final long stepNanos;
        public final long executeNanos;
        public final long encodeNanos;
        public final long privilegedEncodeNanos;
        public final long maskNanos;
        public final long resetCalls;
        public final long resetNanos;

        public TimingSnapshot(long stepCalls, long stepNanos, long executeNanos, long encodeNanos,
                long privilegedEncodeNanos, long maskNanos, long resetCalls, long resetNanos) {
            this.stepCalls = stepCalls;
            this.stepNanos = stepNanos;
            this.executeNanos = executeNanos;
            this.encodeNanos = encodeNanos;
            this.privilegedEncodeNanos = privilegedEncodeNanos;
            this.maskNanos = maskNanos;
            this.resetCalls = resetCalls;
            this.resetNanos = resetNanos;
        }

        private double millis(long nanos) {
            return nanos / 1_000_000.0;
        }

        public String toSummaryString() {
            double meanStepMs = stepCalls == 0 ? 0.0 : millis(stepNanos) / stepCalls;
            double meanResetMs = resetCalls == 0 ? 0.0 : millis(resetNanos) / resetCalls;
            double executeShare = stepNanos == 0 ? 0.0 : executeNanos * 100.0 / stepNanos;
            double encodeShare = stepNanos == 0 ? 0.0 : encodeNanos * 100.0 / stepNanos;
            double privilegedEncodeShare = stepNanos == 0 ? 0.0 : privilegedEncodeNanos * 100.0 / stepNanos;
            double maskShare = stepNanos == 0 ? 0.0 : maskNanos * 100.0 / stepNanos;
            return String.format(
                    "resetCalls=%d meanResetMs=%.3f stepCalls=%d meanStepMs=%.3f executeShare=%.1f%% encodeShare=%.1f%% privilegedEncodeShare=%.1f%% maskShare=%.1f%%",
                    resetCalls, meanResetMs, stepCalls, meanStepMs, executeShare, encodeShare,
                    privilegedEncodeShare, maskShare);
        }
    }
}

package mechanics.rl;

import java.util.concurrent.atomic.LongAdder;
import java.util.function.Supplier;

import model.entity.Character;
import simulation.CombatSimulator;
import simulation.action.CharacterActionRequest;
import visualization.HtmlReportGenerator;
import visualization.VisualLogger;

/**
 * Teacher-free RL environment backed by {@link CombatSimulator}.
 */
public class BattleEnvironment {
    private final Supplier<CombatSimulator> simulatorFactory;
    private final EpisodeConfig config;
    private final ActionSpace actionSpace;
    private final ObservationEncoder observationEncoder;
    private final RewardFunction rewardFunction;

    private CombatSimulator simulator;
    private double lastSwapTime = -999.0;
    private int previousActionId = -1;
    private int stepCount;
    private boolean generateReportOnDone;
    private final double[] observationBuffer = new double[ObservationEncoder.OBSERVATION_SIZE];
    private final double[] actionMaskBuffer = new double[ActionSpace.SIZE];
    private final double[] preActionMaskBuffer = new double[ActionSpace.SIZE];

    private static final LongAdder STEP_CALLS = new LongAdder();
    private static final LongAdder STEP_NANOS = new LongAdder();
    private static final LongAdder EXECUTE_NANOS = new LongAdder();
    private static final LongAdder ENCODE_NANOS = new LongAdder();
    private static final LongAdder MASK_NANOS = new LongAdder();
    private static final LongAdder RESET_CALLS = new LongAdder();
    private static final LongAdder RESET_NANOS = new LongAdder();

    public BattleEnvironment(Supplier<CombatSimulator> simulatorFactory, EpisodeConfig config) {
        this(simulatorFactory, config, new ActionSpace(), new ObservationEncoder(), new RewardFunction());
    }

    public BattleEnvironment(
            Supplier<CombatSimulator> simulatorFactory,
            EpisodeConfig config,
            ActionSpace actionSpace,
            ObservationEncoder observationEncoder,
            RewardFunction rewardFunction) {
        this.simulatorFactory = simulatorFactory;
        this.config = config;
        this.actionSpace = actionSpace;
        this.observationEncoder = observationEncoder;
        this.rewardFunction = rewardFunction;
    }

    public ResetResult reset(boolean generateReport) {
        long start = System.nanoTime();
        simulator = simulatorFactory.get();
        simulator.setLoggingEnabled(generateReport);
        if (generateReport) {
            VisualLogger.getInstance().clear();
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

        fillObservationBuffer();
        fillActionMaskBuffer();
        RESET_CALLS.increment();
        RESET_NANOS.add(System.nanoTime() - start);
        return new ResetResult(observationBuffer, actionMaskBuffer);
    }

    public ActionResult step(int actionId) {
        ensureReset();
        long stepStart = System.nanoTime();
        fillPreActionMaskBuffer();
        boolean validAction = actionSpace.isValid(actionId, preActionMaskBuffer);

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

        previousActionId = actionId;
        stepCount++;

        if (done && generateReportOnDone) {
            HtmlReportGenerator.generate("rl_report.html",
                    VisualLogger.getInstance().getRecords(), simulator);
            generateReportOnDone = false;
        }

        fillObservationBuffer();
        fillActionMaskBuffer();
        STEP_CALLS.increment();
        STEP_NANOS.add(System.nanoTime() - stepStart);
        return new ActionResult(observationBuffer, actionMaskBuffer, reward, done, validAction, damageDelta,
                simulator.getTotalDamage(), timeDelta, actionId, stepCount);
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

    private void execute(int actionId) {
        RLAction action = RLAction.fromId(actionId);
        if (action.isSwap()) {
            simulator.switchCharacter(action.getTargetCharacterId());
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

    private void fillObservationBuffer() {
        long start = System.nanoTime();
        observationEncoder.fillObservation(simulator, config, lastSwapTime, observationBuffer);
        ENCODE_NANOS.add(System.nanoTime() - start);
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
                MASK_NANOS.sum(),
                RESET_CALLS.sum(),
                RESET_NANOS.sum());
    }

    /**
     * Reset return values.
     */
    public static class ResetResult {
        public final double[] observation;
        public final double[] actionMask;

        public ResetResult(double[] observation, double[] actionMask) {
            this.observation = observation;
            this.actionMask = actionMask;
        }
    }

    public static class TimingSnapshot {
        public final long stepCalls;
        public final long stepNanos;
        public final long executeNanos;
        public final long encodeNanos;
        public final long maskNanos;
        public final long resetCalls;
        public final long resetNanos;

        public TimingSnapshot(long stepCalls, long stepNanos, long executeNanos, long encodeNanos, long maskNanos,
                long resetCalls, long resetNanos) {
            this.stepCalls = stepCalls;
            this.stepNanos = stepNanos;
            this.executeNanos = executeNanos;
            this.encodeNanos = encodeNanos;
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
            double maskShare = stepNanos == 0 ? 0.0 : maskNanos * 100.0 / stepNanos;
            return String.format(
                    "resetCalls=%d meanResetMs=%.3f stepCalls=%d meanStepMs=%.3f executeShare=%.1f%% encodeShare=%.1f%% maskShare=%.1f%%",
                    resetCalls, meanResetMs, stepCalls, meanStepMs, executeShare, encodeShare, maskShare);
        }
    }
}

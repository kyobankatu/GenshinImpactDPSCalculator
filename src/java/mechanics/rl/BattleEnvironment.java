package mechanics.rl;

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

        double[] observation = observationEncoder.encode(simulator, config, lastSwapTime);
        double[] actionMask = actionSpace.createMask(simulator, lastSwapTime, config);
        return new ResetResult(observation, actionMask);
    }

    public ActionResult step(int actionId) {
        ensureReset();
        double[] preMask = actionSpace.createMask(simulator, lastSwapTime, config);
        boolean validAction = actionSpace.isValid(actionId, preMask);

        double timeBefore = simulator.getCurrentTime();
        double damageBefore = simulator.getTotalDamage();

        if (validAction) {
            execute(actionId);
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

        double[] observation = observationEncoder.encode(simulator, config, lastSwapTime);
        double[] actionMask = actionSpace.createMask(simulator, lastSwapTime, config);
        return new ActionResult(observation, actionMask, reward, done, validAction, damageDelta,
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
}

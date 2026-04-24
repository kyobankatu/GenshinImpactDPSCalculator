package mechanics.rl;

/**
 * Result returned by one Java-native RL environment step.
 */
public class RLStepResult {
    public final double[] state;
    public final double reward;
    public final boolean done;
    public final boolean validAction;
    public final double damageDelta;
    public final double totalDamage;
    public final int executedActionId;
    public final int stepCount;

    public RLStepResult(
            double[] state,
            double reward,
            boolean done,
            boolean validAction,
            double damageDelta,
            double totalDamage,
            int executedActionId,
            int stepCount) {
        this.state = state;
        this.reward = reward;
        this.done = done;
        this.validAction = validAction;
        this.damageDelta = damageDelta;
        this.totalDamage = totalDamage;
        this.executedActionId = executedActionId;
        this.stepCount = stepCount;
    }
}

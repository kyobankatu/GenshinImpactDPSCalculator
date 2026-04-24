package mechanics.rl;

/**
 * Result of one environment step.
 */
public class ActionResult {
    public final double[] observation;
    public final double[] actionMask;
    public final double reward;
    public final boolean done;
    public final boolean validAction;
    public final double damageDelta;
    public final double totalDamage;
    public final double timeDelta;
    public final int executedActionId;
    public final int stepCount;

    public ActionResult(
            double[] observation,
            double[] actionMask,
            double reward,
            boolean done,
            boolean validAction,
            double damageDelta,
            double totalDamage,
            double timeDelta,
            int executedActionId,
            int stepCount) {
        this.observation = observation;
        this.actionMask = actionMask;
        this.reward = reward;
        this.done = done;
        this.validAction = validAction;
        this.damageDelta = damageDelta;
        this.totalDamage = totalDamage;
        this.timeDelta = timeDelta;
        this.executedActionId = executedActionId;
        this.stepCount = stepCount;
    }
}

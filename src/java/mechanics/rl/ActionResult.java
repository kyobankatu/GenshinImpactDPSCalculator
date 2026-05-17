package mechanics.rl;

/**
 * Result of one environment step.
 */
public class ActionResult {
    /** Next observation vector exposed to the policy. */
    public final double[] observation;
    /** Privileged (critic-only) observation vector, may be null when not used. */
    public final double[] privilegedObservation;
    /** Action mask (1.0 = legal, 0.0 = illegal) for the next step. */
    public final double[] actionMask;
    /** Shaped reward emitted for this step. */
    public final double reward;
    /** Whether the episode terminated after this step. */
    public final boolean done;
    /** Whether the requested action was legal under the action mask. */
    public final boolean validAction;
    /** Damage dealt during this step. */
    public final double damageDelta;
    /** Cumulative damage dealt within the current episode after this step. */
    public final double totalDamage;
    /** Simulator time elapsed during this step (seconds). */
    public final double timeDelta;
    /** Action id that was actually executed (may differ on no-op). */
    public final int executedActionId;
    /** Number of completed steps in the episode so far. */
    public final int stepCount;
    /** Per-role episode-level summary, populated when {@code done == true}. */
    public final EpisodeRoleSummary episodeRoleSummary;

    /**
     * Constructs a snapshot of one environment step result.
     *
     * @param observation next observation vector
     * @param privilegedObservation privileged critic observation (nullable)
     * @param actionMask legal-action mask for the next step
     * @param reward shaped reward for this step
     * @param done true if the episode terminated
     * @param validAction true if the requested action was legal
     * @param damageDelta damage dealt during this step
     * @param totalDamage cumulative episode damage after this step
     * @param timeDelta simulator time advanced this step (seconds)
     * @param executedActionId action id actually executed
     * @param stepCount completed step count after this step
     * @param episodeRoleSummary terminal role summary, or null if not done
     */
    public ActionResult(
            double[] observation,
            double[] privilegedObservation,
            double[] actionMask,
            double reward,
            boolean done,
            boolean validAction,
            double damageDelta,
            double totalDamage,
            double timeDelta,
            int executedActionId,
            int stepCount,
            EpisodeRoleSummary episodeRoleSummary) {
        this.observation = observation;
        this.privilegedObservation = privilegedObservation;
        this.actionMask = actionMask;
        this.reward = reward;
        this.done = done;
        this.validAction = validAction;
        this.damageDelta = damageDelta;
        this.totalDamage = totalDamage;
        this.timeDelta = timeDelta;
        this.executedActionId = executedActionId;
        this.stepCount = stepCount;
        this.episodeRoleSummary = episodeRoleSummary;
    }
}

package mechanics.rl;

/**
 * Computes the reward for one transition.
 */
public class RewardFunction {
    /**
     * Computes the shaped reward for one environment step.
     *
     * @param config episode configuration providing reward scales and penalties
     * @param actionId action id chosen this step
     * @param previousActionId previous step's action id (or negative if none)
     * @param validAction true when the chosen action was legal
     * @param damageDelta damage dealt this step
     * @param totalDamage cumulative episode damage after this step
     * @param timeDelta simulator time advanced this step
     * @param done true when this step terminates the episode
     * @return shaped reward value
     */
    public double compute(
            EpisodeConfig config,
            int actionId,
            int previousActionId,
            boolean validAction,
            double damageDelta,
            double totalDamage,
            double timeDelta,
            boolean done) {
        double reward = damageDelta / config.damageRewardScale;
        reward -= timeDelta * config.idleTimePenaltyPerSecond;
        if (!validAction) {
            reward -= config.invalidActionPenalty;
        }
        if (isRepeatedSwap(actionId, previousActionId)) {
            reward -= config.repeatedSwapPenalty;
        }
        if (done) {
            reward += totalDamage / config.terminalDamageScale;
        }
        return reward;
    }

    private boolean isRepeatedSwap(int actionId, int previousActionId) {
        if (actionId < 0 || previousActionId < 0) {
            return false;
        }
        return RLAction.fromId(actionId).isSwap() && actionId == previousActionId;
    }
}

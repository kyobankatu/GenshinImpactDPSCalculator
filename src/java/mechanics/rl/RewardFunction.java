package mechanics.rl;

/**
 * Computes the reward for one transition.
 */
public class RewardFunction {
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

package mechanics.rl.policy;

/**
 * Java-native RL policy contract.
 */
public interface RLPolicy {
    int chooseAction(double[] state, boolean training);

    default void observe(double[] state, int action, double reward, double[] nextState, boolean done) {
        // Optional for non-learning policies.
    }

    default void onEpisodeStart() {
        // Optional hook.
    }

    default void onEpisodeEnd(double totalReward, double totalDamage) {
        // Optional hook.
    }
}

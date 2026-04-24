package mechanics.rl.policy;

import java.util.Random;

import mechanics.rl.RLAction;

/**
 * Uniform random baseline policy.
 */
public class RandomPolicy implements RLPolicy {
    private final Random random;

    public RandomPolicy(long seed) {
        this.random = new Random(seed);
    }

    @Override
    public int chooseAction(double[] state, boolean training) {
        return random.nextInt(RLAction.SIZE);
    }
}

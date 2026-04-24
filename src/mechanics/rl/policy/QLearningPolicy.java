package mechanics.rl.policy;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import mechanics.rl.RLAction;

/**
 * Small tabular Q-learning baseline over a discretized RL state.
 */
public class QLearningPolicy implements RLPolicy {
    private final Map<String, double[]> qTable = new HashMap<>();
    private final Random random;
    private double epsilon;
    private final double minEpsilon;
    private final double epsilonDecay;
    private final double learningRate;
    private final double discount;

    public QLearningPolicy(long seed) {
        this(seed, 0.35, 0.03, 0.995, 0.15, 0.90);
    }

    public QLearningPolicy(
            long seed,
            double epsilon,
            double minEpsilon,
            double epsilonDecay,
            double learningRate,
            double discount) {
        this.random = new Random(seed);
        this.epsilon = epsilon;
        this.minEpsilon = minEpsilon;
        this.epsilonDecay = epsilonDecay;
        this.learningRate = learningRate;
        this.discount = discount;
    }

    @Override
    public int chooseAction(double[] state, boolean training) {
        if (training && random.nextDouble() < epsilon) {
            return random.nextInt(RLAction.SIZE);
        }
        return bestAction(qValues(state));
    }

    @Override
    public void observe(double[] state, int action, double reward, double[] nextState, boolean done) {
        double[] values = qValues(state);
        double[] nextValues = qValues(nextState);
        double target = reward + (done ? 0.0 : discount * max(nextValues));
        values[action] = values[action] + learningRate * (target - values[action]);
    }

    @Override
    public void onEpisodeEnd(double totalReward, double totalDamage) {
        epsilon = Math.max(minEpsilon, epsilon * epsilonDecay);
    }

    public Map<String, double[]> getQTable() {
        return qTable;
    }

    public double getEpsilon() {
        return epsilon;
    }

    private double[] qValues(double[] state) {
        return qTable.computeIfAbsent(stateKey(state), key -> new double[RLAction.SIZE]);
    }

    private int bestAction(double[] values) {
        int best = 0;
        for (int i = 1; i < values.length; i++) {
            if (values[i] > values[best]) {
                best = i;
            }
        }
        return best;
    }

    private double max(double[] values) {
        return Arrays.stream(values).max().orElse(0.0);
    }

    private String stateKey(double[] state) {
        StringBuilder key = new StringBuilder();
        int active = indexOfOneHot(state, 1, 5, 4);
        int target = indexOfOneHot(state, 22, 1, 4);
        int suggested = indexOfOneHot(state, 26, 1, 3);
        int timeBucket = (int) Math.floor((1.0 - state[21]) * 10.0);
        key.append("a=").append(active);
        key.append("|t=").append(target);
        key.append("|s=").append(suggested);
        key.append("|tb=").append(timeBucket);
        key.append("|swap=").append(state[20] > 0.5 ? 1 : 0);
        key.append("|skill=");
        for (int i = 0; i < 4; i++) {
            key.append(state[i * 5 + 2] > 0.5 ? '1' : '0');
        }
        key.append("|burst=");
        for (int i = 0; i < 4; i++) {
            key.append(state[i * 5 + 3] > 0.5 ? '1' : '0');
        }
        key.append("|bursting=");
        for (int i = 0; i < 4; i++) {
            key.append(state[i * 5 + 4] > 0.5 ? '1' : '0');
        }
        return key.toString();
    }

    private int indexOfOneHot(double[] state, int start, int stride, int count) {
        for (int i = 0; i < count; i++) {
            if (state[start + i * stride] > 0.5) {
                return i;
            }
        }
        return -1;
    }
}

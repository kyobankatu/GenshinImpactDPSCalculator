package mechanics.rl;

import java.util.SplittableRandom;

/**
 * Policy outputs for one environment slot.
 */
public class PolicyInference {
    public final double[] nextHidden;
    public final double[] probabilities;
    public final double[] logits;
    public final double value;
    public final double entropy;

    public PolicyInference(double[] nextHidden, double[] probabilities, double[] logits, double value, double entropy) {
        this.nextHidden = nextHidden;
        this.probabilities = probabilities;
        this.logits = logits;
        this.value = value;
        this.entropy = entropy;
    }

    public int sampleAction(SplittableRandom random) {
        double draw = random.nextDouble();
        double cumulative = 0.0;
        for (int i = 0; i < probabilities.length; i++) {
            cumulative += probabilities[i];
            if (draw <= cumulative) {
                return i;
            }
        }
        return probabilities.length - 1;
    }

    public int greedyAction() {
        int best = 0;
        for (int i = 1; i < probabilities.length; i++) {
            if (probabilities[i] > probabilities[best]) {
                best = i;
            }
        }
        return best;
    }

    public double logProbability(int action) {
        double probability = Math.max(1e-9, probabilities[action]);
        return Math.log(probability);
    }
}

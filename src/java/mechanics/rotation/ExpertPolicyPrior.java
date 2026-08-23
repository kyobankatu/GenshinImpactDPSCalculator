package mechanics.rotation;

/** Supplies optional policy probabilities to a model-independent searcher. */
@FunctionalInterface
public interface ExpertPolicyPrior {
    /** Returns one non-negative finite weight per policy action. */
    double[] weights(RotationStep state);

    /** Returns a prior assigning equal weight to every action identity. */
    static ExpertPolicyPrior uniform() {
        return state -> {
            double[] weights = new double[state.legalActionMask.length];
            java.util.Arrays.fill(weights, 1.0);
            return weights;
        };
    }
}

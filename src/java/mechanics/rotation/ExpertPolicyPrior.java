package mechanics.rotation;

import java.util.List;

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

    /** Adapts a batched policy-value advisor for legacy stateless search callers. */
    static ExpertPolicyPrior fromAdvisor(PolicyValueAdvisor advisor, long timeoutMillis) {
        if (advisor == null || timeoutMillis <= 0L) {
            throw new IllegalArgumentException("advisor and positive timeout are required");
        }
        return state -> {
            PolicyValueAdvisor.Query query = new PolicyValueAdvisor.Query(
                    0L,
                    state,
                    new double[0]);
            List<PolicyValueEstimate> estimates = advisor.advise(
                    List.of(query),
                    timeoutMillis);
            if (estimates == null || estimates.size() != 1
                    || estimates.get(0).getRequestId() != 0L) {
                throw new IllegalArgumentException("advisor compatibility response mismatch");
            }
            return estimates.get(0).getPolicyPrior();
        };
    }
}

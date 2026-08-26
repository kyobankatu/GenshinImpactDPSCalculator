package mechanics.rotation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import mechanics.rl.ObservationEncoder;

/** Versioned, batched policy-value inference boundary owned outside the simulator. */
@FunctionalInterface
public interface PolicyValueAdvisor extends AutoCloseable {
    int SCHEMA_VERSION = 1;
    int MODEL_REVISION = 2;

    /**
     * Returns one response per query in the same order before the supplied timeout.
     *
     * <p>The optional value is advisory for non-terminal ordering only. The Java
     * simulator remains the authority for legality, completion, and terminal score.
     */
    List<PolicyValueEstimate> advise(List<Query> queries, long timeoutMillis);

    @Override
    default void close() {
    }

    /** Returns deterministic normalized uniform mass over each simulator legal mask. */
    static PolicyValueAdvisor uniform() {
        return (queries, timeoutMillis) -> {
            requireBatch(queries, timeoutMillis);
            List<PolicyValueEstimate> estimates = new ArrayList<>();
            for (Query query : queries) {
                double[] weights = query.getStep().legalActionMask.clone();
                for (int actionId = 0; actionId < weights.length; actionId++) {
                    weights[actionId] = weights[actionId] > 0.5 ? 1.0 : 0.0;
                }
                estimates.add(PolicyValueEstimate.validated(
                        query.getRequestId(),
                        weights,
                        null,
                        query.getRecurrentState(),
                        query.getStep().legalActionMask));
            }
            return Collections.unmodifiableList(estimates);
        };
    }

    /** Adapts the legacy stateless prior to the versioned batched boundary. */
    static PolicyValueAdvisor fromPrior(ExpertPolicyPrior prior) {
        if (prior == null) {
            throw new IllegalArgumentException("prior must not be null");
        }
        return (queries, timeoutMillis) -> {
            requireBatch(queries, timeoutMillis);
            List<PolicyValueEstimate> estimates = new ArrayList<>();
            for (Query query : queries) {
                RotationStep step = query.getStep();
                double[] weights = prior.weights(step.copy());
                if (weights != null && weights.length == step.legalActionMask.length) {
                    for (int actionId = 0; actionId < weights.length; actionId++) {
                        if (step.legalActionMask[actionId] <= 0.5) {
                            weights[actionId] = 0.0;
                        }
                    }
                }
                estimates.add(PolicyValueEstimate.validated(
                        query.getRequestId(),
                        weights,
                        null,
                        query.getRecurrentState(),
                        step.legalActionMask));
            }
            return Collections.unmodifiableList(estimates);
        };
    }

    /**
     * Wraps an advisor with deterministic uniform fallback and typed diagnostics.
     *
     * <p>Remote implementations must still honor the deadline internally; this
     * wrapper detects late synchronous responses but cannot interrupt arbitrary code.
     */
    static PolicyValueAdvisor withUniformFallback(PolicyValueAdvisor advisor) {
        if (advisor == null) {
            throw new IllegalArgumentException("advisor must not be null");
        }
        return (queries, timeoutMillis) -> {
            requireBatch(queries, timeoutMillis);
            long started = System.nanoTime();
            try {
                List<PolicyValueEstimate> estimates = advisor.advise(queries, timeoutMillis);
                long elapsedMillis = (System.nanoTime() - started) / 1_000_000L;
                if (elapsedMillis >= timeoutMillis) {
                    return fallbacks(
                            queries,
                            PolicyValueEstimate.Diagnostic.TIMEOUT,
                            "advisor deadline exceeded");
                }
                validateResponseOrder(queries, estimates);
                return estimates;
            } catch (AdvisorException exception) {
                return fallbacks(queries, exception.getDiagnostic(), exception.getMessage());
            } catch (RuntimeException exception) {
                return fallbacks(
                        queries,
                        PolicyValueEstimate.Diagnostic.INVALID_RESPONSE,
                        exception.getMessage());
            }
        };
    }

    private static void requireBatch(List<Query> queries, long timeoutMillis) {
        if (queries == null || queries.isEmpty() || timeoutMillis <= 0L) {
            throw new IllegalArgumentException("Non-empty queries and positive timeout are required");
        }
        for (Query query : queries) {
            if (query == null) {
                throw new IllegalArgumentException("Policy-value query must not be null");
            }
        }
    }

    private static void validateResponseOrder(
            List<Query> queries,
            List<PolicyValueEstimate> estimates) {
        if (estimates == null || estimates.size() != queries.size()) {
            throw new AdvisorException(
                    PolicyValueEstimate.Diagnostic.INVALID_RESPONSE,
                    "advisor response batch size mismatch");
        }
        for (int index = 0; index < queries.size(); index++) {
            PolicyValueEstimate estimate = estimates.get(index);
            if (estimate == null
                    || estimate.getRequestId()
                            != queries.get(index).getRequestId()) {
                throw new AdvisorException(
                        PolicyValueEstimate.Diagnostic.INVALID_RESPONSE,
                        "advisor response order mismatch");
            }
            PolicyValueEstimate.validated(
                    estimate.getRequestId(),
                    estimate.getPolicyPrior(),
                    estimate.getValueEstimate(),
                    estimate.getRecurrentState(),
                    queries.get(index).getStep().legalActionMask);
        }
    }

    private static List<PolicyValueEstimate> fallbacks(
            List<Query> queries,
            PolicyValueEstimate.Diagnostic diagnostic,
            String detail) {
        List<PolicyValueEstimate> estimates = new ArrayList<>();
        for (Query query : queries) {
            estimates.add(PolicyValueEstimate.uniformFallback(query, diagnostic, detail));
        }
        return Collections.unmodifiableList(estimates);
    }

    /** Immutable state and recurrent-state input for one ordered batch element. */
    final class Query {
        private final long requestId;
        private final RotationStep step;
        private final double[] recurrentState;

        /** Creates one query while retaining recurrent-state ownership in the caller. */
        public Query(long requestId, RotationStep step, double[] recurrentState) {
            if (requestId < 0L || step == null
                    || step.observation.length != ObservationEncoder.OBSERVATION_SIZE
                    || step.legalActionMask.length != PolicyAction.SIZE) {
                throw new IllegalArgumentException("Invalid policy-value query dimensions");
            }
            this.requestId = requestId;
            this.step = step.copy();
            this.recurrentState = recurrentState == null
                    ? new double[0] : recurrentState.clone();
            for (double value : this.recurrentState) {
                if (!Double.isFinite(value)) {
                    throw new IllegalArgumentException("Invalid query recurrent state");
                }
            }
        }

        public long getRequestId() {
            return requestId;
        }

        public RotationStep getStep() {
            return step.copy();
        }

        public double[] getRecurrentState() {
            return recurrentState.clone();
        }
    }

    /** Typed fail-closed advisor failure suitable for configured fallback. */
    final class AdvisorException extends RuntimeException {
        private final PolicyValueEstimate.Diagnostic diagnostic;

        public AdvisorException(
                PolicyValueEstimate.Diagnostic diagnostic,
                String message) {
            super(message);
            if (diagnostic == null || diagnostic == PolicyValueEstimate.Diagnostic.NONE) {
                throw new IllegalArgumentException("Advisor failure diagnostic is required");
            }
            this.diagnostic = diagnostic;
        }

        public PolicyValueEstimate.Diagnostic getDiagnostic() {
            return diagnostic;
        }
    }
}

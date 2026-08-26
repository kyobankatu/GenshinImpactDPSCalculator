package mechanics.rotation;

import java.util.Arrays;

/** Immutable, legality-normalized policy and optional non-terminal value estimate. */
public final class PolicyValueEstimate {
    /** Typed outcome used to distinguish guidance from deterministic fallback. */
    public enum Diagnostic {
        NONE,
        UNAVAILABLE,
        TIMEOUT,
        INVALID_RESPONSE
    }

    private static final double MASKED_MASS_TOLERANCE = 1.0e-12;

    private final long requestId;
    private final double[] policyPrior;
    private final Double valueEstimate;
    private final double[] recurrentState;
    private final Diagnostic diagnostic;
    private final String detail;

    private PolicyValueEstimate(
            long requestId,
            double[] policyPrior,
            Double valueEstimate,
            double[] recurrentState,
            Diagnostic diagnostic,
            String detail) {
        this.requestId = requestId;
        this.policyPrior = policyPrior;
        this.valueEstimate = valueEstimate;
        this.recurrentState = recurrentState;
        this.diagnostic = diagnostic;
        this.detail = detail;
    }

    /**
     * Validates and normalizes one advisor response against the simulator mask.
     *
     * <p>Masked entries at floating-point noise scale are forced to zero. Larger
     * masked mass is rejected because the advisor is not allowed to alter legality.
     */
    public static PolicyValueEstimate validated(
            long requestId,
            double[] rawPolicyPrior,
            Double valueEstimate,
            double[] recurrentState,
            double[] legalActionMask) {
        if (requestId < 0L || rawPolicyPrior == null || legalActionMask == null
                || rawPolicyPrior.length != legalActionMask.length
                || rawPolicyPrior.length != PolicyAction.SIZE) {
            throw new IllegalArgumentException("Policy-value response dimension mismatch");
        }
        double[] normalized = rawPolicyPrior.clone();
        double total = 0.0;
        for (int actionId = 0; actionId < normalized.length; actionId++) {
            double weight = normalized[actionId];
            if (!Double.isFinite(weight) || weight < 0.0) {
                throw new IllegalArgumentException("Policy-value response has invalid weight");
            }
            if (legalActionMask[actionId] <= 0.5) {
                if (weight > MASKED_MASS_TOLERANCE) {
                    throw new IllegalArgumentException(
                            "Policy-value response assigns masked probability");
                }
                normalized[actionId] = 0.0;
            } else {
                total += weight;
            }
        }
        if (!Double.isFinite(total) || total <= 0.0) {
            throw new IllegalArgumentException("Policy-value response has no legal mass");
        }
        for (int actionId = 0; actionId < normalized.length; actionId++) {
            normalized[actionId] /= total;
        }
        if (valueEstimate != null && !Double.isFinite(valueEstimate)) {
            throw new IllegalArgumentException("Policy-value response has invalid value");
        }
        double[] nextState = recurrentState == null ? new double[0] : recurrentState.clone();
        for (double value : nextState) {
            if (!Double.isFinite(value)) {
                throw new IllegalArgumentException(
                        "Policy-value response has invalid recurrent state");
            }
        }
        return new PolicyValueEstimate(
                requestId,
                normalized,
                valueEstimate,
                nextState,
                Diagnostic.NONE,
                "");
    }

    /** Returns deterministic uniform legal guidance with a typed fallback reason. */
    public static PolicyValueEstimate uniformFallback(
            PolicyValueAdvisor.Query query,
            Diagnostic diagnostic,
            String detail) {
        if (query == null || diagnostic == null || diagnostic == Diagnostic.NONE) {
            throw new IllegalArgumentException("Fallback query and diagnostic are required");
        }
        double[] legalActionMask = query.getStep().legalActionMask;
        double[] weights = Arrays.stream(legalActionMask)
                .map(value -> value > 0.5 ? 1.0 : 0.0)
                .toArray();
        PolicyValueEstimate estimate = validated(
                query.getRequestId(),
                weights,
                null,
                query.getRecurrentState(),
                legalActionMask);
        return new PolicyValueEstimate(
                estimate.requestId,
                estimate.policyPrior,
                null,
                estimate.recurrentState,
                diagnostic,
                detail == null ? "" : detail);
    }

    public long getRequestId() {
        return requestId;
    }

    public double[] getPolicyPrior() {
        return policyPrior.clone();
    }

    public Double getValueEstimate() {
        return valueEstimate;
    }

    public double[] getRecurrentState() {
        return recurrentState.clone();
    }

    public Diagnostic getDiagnostic() {
        return diagnostic;
    }

    public String getDetail() {
        return detail;
    }

    public boolean isFallback() {
        return diagnostic != Diagnostic.NONE;
    }
}

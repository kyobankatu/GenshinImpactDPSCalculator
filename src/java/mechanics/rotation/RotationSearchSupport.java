package mechanics.rotation;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Package-private legal sampling, budget, and rollout support for strategies. */
final class RotationSearchSupport {
    private RotationSearchSupport() {
    }

    static ExpertTrajectory evaluate(
            RotationEnvironment environment,
            int[] proposedActions,
            RotationSearchConfig config,
            Random random,
            Budget budget) {
        int callsBefore = budget.used();
        RotationStep step = environment.reset();
        List<Integer> executed = new ArrayList<>();
        int proposedIndex = 0;
        while (!step.done && executed.size() < config.maxActions && budget.consume(1)) {
            int actionId = proposedIndex < proposedActions.length
                    ? proposedActions[proposedIndex++] : -1;
            if (!isLegal(step, actionId)) {
                actionId = sampleLegal(step, config.prior, random);
            }
            step = environment.step(actionId);
            if (!step.validAction) {
                throw new IllegalStateException("search executed an illegal action " + actionId);
            }
            executed.add(actionId);
        }
        if (executed.isEmpty()) {
            throw new IllegalStateException("search budget did not allow one simulator step");
        }
        return new ExpertTrajectory(
                executed.stream().mapToInt(Integer::intValue).toArray(),
                step.objective,
                step.stateHash,
                step.done,
                budget.used() - callsBefore);
    }

    static int sampleLegal(RotationStep step, ExpertPolicyPrior prior, Random random) {
        double[] weights = validatedWeights(step, prior);
        return sampleFromActions(step, legalActions(step), weights, random);
    }

    static double[] validatedWeights(RotationStep step, ExpertPolicyPrior prior) {
        double[] weights = prior.weights(step.copy());
        if (weights == null || weights.length != step.legalActionMask.length) {
            throw new IllegalArgumentException("policy prior action dimension mismatch");
        }
        double total = 0.0;
        for (int actionId = 0; actionId < weights.length; actionId++) {
            if (!Double.isFinite(weights[actionId]) || weights[actionId] < 0.0) {
                throw new IllegalArgumentException("policy prior contains invalid weight");
            }
            if (step.legalActionMask[actionId] > 0.5) {
                total += weights[actionId];
            }
        }
        if (!Double.isFinite(total) || total <= 0.0) {
            throw new IllegalArgumentException("policy prior has no legal probability mass");
        }
        return weights;
    }

    static int sampleFromActions(
            RotationStep step,
            List<Integer> actions,
            double[] weights,
            Random random) {
        if (actions.isEmpty()) {
            throw new IllegalArgumentException("candidate action list must not be empty");
        }
        double total = 0.0;
        for (int actionId : actions) {
            if (!isLegal(step, actionId)) {
                throw new IllegalArgumentException("candidate action is not legal: " + actionId);
            }
            total += weights[actionId];
        }
        if (total <= 0.0) {
            return actions.get(random.nextInt(actions.size()));
        }
        double draw = random.nextDouble() * total;
        double cumulative = 0.0;
        for (int actionId : actions) {
            cumulative += weights[actionId];
            if (draw < cumulative) {
                return actionId;
            }
        }
        return actions.get(actions.size() - 1);
    }

    static List<Integer> legalActions(RotationStep step) {
        List<Integer> actions = new ArrayList<>();
        for (int actionId = 0; actionId < step.legalActionMask.length; actionId++) {
            if (step.legalActionMask[actionId] > 0.5) {
                actions.add(actionId);
            }
        }
        if (actions.isEmpty()) {
            throw new IllegalStateException("rotation state has no legal action");
        }
        return actions;
    }

    static boolean isLegal(RotationStep step, int actionId) {
        return actionId >= 0
                && actionId < step.legalActionMask.length
                && step.legalActionMask[actionId] > 0.5;
    }

    /** Exact simulator-work budget including snapshot replay depth. */
    static final class Budget {
        private final int limit;
        private int used;

        Budget(int limit) {
            this.limit = limit;
        }

        boolean consume(int calls) {
            if (calls < 0) {
                throw new IllegalArgumentException("budget calls must be non-negative");
            }
            if (used + calls > limit) {
                return false;
            }
            used += calls;
            return true;
        }

        int used() {
            return used;
        }

        int remaining() {
            return limit - used;
        }
    }
}

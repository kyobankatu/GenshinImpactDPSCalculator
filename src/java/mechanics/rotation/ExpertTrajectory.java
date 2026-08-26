package mechanics.rotation;

import java.util.Arrays;

/** Immutable complete or budget-truncated trajectory retained by expert search. */
public final class ExpertTrajectory {
    private final int[] actions;
    private final RotationObjective.Score objective;
    private final long finalStateHash;
    private final boolean complete;
    private final int simulatorCalls;
    private final RotationEvaluationMode evaluationMode;
    private final int repairedActionCount;

    /** Creates one validated trajectory record. */
    public ExpertTrajectory(
            int[] actions,
            RotationObjective.Score objective,
            long finalStateHash,
            boolean complete,
            int simulatorCalls) {
        this(
                actions,
                objective,
                finalStateHash,
                complete,
                simulatorCalls,
                RotationEvaluationMode.REPAIR,
                0);
    }

    /** Creates one trajectory with explicit proposal-evaluation provenance. */
    public ExpertTrajectory(
            int[] actions,
            RotationObjective.Score objective,
            long finalStateHash,
            boolean complete,
            int simulatorCalls,
            RotationEvaluationMode evaluationMode,
            int repairedActionCount) {
        if (actions == null || objective == null) {
            throw new IllegalArgumentException("actions and objective are required");
        }
        if (actions.length == 0) {
            throw new IllegalArgumentException("trajectory must contain at least one action");
        }
        for (int actionId : actions) {
            PolicyAction.fromId(actionId);
        }
        if (!Double.isFinite(objective.objectiveScore)) {
            throw new IllegalArgumentException("trajectory objective must be finite");
        }
        if (simulatorCalls <= 0) {
            throw new IllegalArgumentException("simulatorCalls must be positive");
        }
        if (evaluationMode == null || repairedActionCount < 0) {
            throw new IllegalArgumentException(
                    "evaluationMode and non-negative repairedActionCount are required");
        }
        this.actions = actions.clone();
        this.objective = objective;
        this.finalStateHash = finalStateHash;
        this.complete = complete;
        this.simulatorCalls = simulatorCalls;
        this.evaluationMode = evaluationMode;
        this.repairedActionCount = repairedActionCount;
    }

    public int[] getActions() {
        return actions.clone();
    }

    public RotationObjective.Score getObjective() {
        return objective;
    }

    public long getFinalStateHash() {
        return finalStateHash;
    }

    public boolean isComplete() {
        return complete;
    }

    public int getSimulatorCalls() {
        return simulatorCalls;
    }

    public RotationEvaluationMode getEvaluationMode() {
        return evaluationMode;
    }

    public int getRepairedActionCount() {
        return repairedActionCount;
    }

    /** Returns exact action-sequence identity for archive duplicate suppression. */
    String sequenceKey() {
        return Arrays.toString(actions);
    }
}

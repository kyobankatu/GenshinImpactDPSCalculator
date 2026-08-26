package mechanics.rotation;

import java.util.Comparator;

/** Orders rotation trajectories by publishability before terminal objective. */
public final class RotationTrajectoryRanker implements Comparator<ExpertTrajectory> {
    /** Shared deterministic ordering used by teacher archives. */
    public static final RotationTrajectoryRanker INSTANCE = new RotationTrajectoryRanker();

    private RotationTrajectoryRanker() {
    }

    @Override
    public int compare(ExpertTrajectory left, ExpertTrajectory right) {
        int legal = Boolean.compare(isLegal(right), isLegal(left));
        if (legal != 0) {
            return legal;
        }
        int complete = Boolean.compare(right.isComplete(), left.isComplete());
        if (complete != 0) {
            return complete;
        }
        int cyclic = Boolean.compare(isCyclicFeasible(right), isCyclicFeasible(left));
        if (cyclic != 0) {
            return cyclic;
        }
        int score = Double.compare(
                right.getObjective().objectiveScore,
                left.getObjective().objectiveScore);
        if (score != 0) {
            return score;
        }
        int[] leftActions = left.getActions();
        int[] rightActions = right.getActions();
        int length = Math.min(leftActions.length, rightActions.length);
        for (int index = 0; index < length; index++) {
            int action = Integer.compare(leftActions[index], rightActions[index]);
            if (action != 0) {
                return action;
            }
        }
        return Integer.compare(leftActions.length, rightActions.length);
    }

    /** Returns whether the simulator accepted every executed action. */
    public boolean isLegal(ExpertTrajectory trajectory) {
        return trajectory.getObjective().invalidActionCount == 0;
    }

    /** Returns whether a trajectory is legal, terminal, and cyclically feasible. */
    public boolean isPublishable(ExpertTrajectory trajectory) {
        return isLegal(trajectory)
                && trajectory.isComplete()
                && isCyclicFeasible(trajectory);
    }

    private boolean isCyclicFeasible(ExpertTrajectory trajectory) {
        return trajectory.getObjective().cyclicEnergyFeasible;
    }
}

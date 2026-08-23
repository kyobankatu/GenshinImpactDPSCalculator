package mechanics.rotation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Deterministic score-ordered archive with exact duplicate suppression. */
public final class TopKTrajectoryArchive {
    private static final Comparator<ExpertTrajectory> ORDER = (left, right) -> {
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
    };

    private final int capacity;
    private final List<ExpertTrajectory> trajectories = new ArrayList<>();
    private final Set<String> sequenceKeys = new HashSet<>();

    /** Creates a fixed-capacity archive. */
    public TopKTrajectoryArchive(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("archive capacity must be positive");
        }
        this.capacity = capacity;
    }

    /** Adds a unique trajectory and returns whether it remains in the archive. */
    public boolean add(ExpertTrajectory trajectory) {
        if (trajectory == null) {
            throw new IllegalArgumentException("trajectory must not be null");
        }
        if (!sequenceKeys.add(trajectory.sequenceKey())) {
            return false;
        }
        trajectories.add(trajectory);
        trajectories.sort(ORDER);
        if (trajectories.size() > capacity) {
            ExpertTrajectory removed = trajectories.remove(trajectories.size() - 1);
            sequenceKeys.remove(removed.sequenceKey());
            return removed != trajectory;
        }
        return true;
    }

    public ExpertTrajectory best() {
        if (trajectories.isEmpty()) {
            throw new IllegalStateException("trajectory archive is empty");
        }
        return trajectories.get(0);
    }

    public List<ExpertTrajectory> trajectories() {
        return List.copyOf(trajectories);
    }

    public int size() {
        return trajectories.size();
    }
}

package mechanics.rotation;

import java.util.ArrayList;
import java.util.List;

/**
 * Suppresses exact and near-duplicate action traces within one loadout.
 *
 * <p>
 * Traces at no more than five percent Levenshtein distance are treated as the
 * same training example. The caller owns the loadout boundary so unrelated
 * party action semantics are never compared.
 */
public final class RotationTraceDeduplicator {
    private final List<int[]> retained = new ArrayList<>();

    /**
     * Retains a defensive copy when no prior trace is within five percent edits.
     *
     * @param candidate non-empty action sequence for one loadout
     * @return {@code true} when the candidate is distinct and retained
     */
    public boolean tryRetain(int[] candidate) {
        if (candidate == null || candidate.length == 0) {
            throw new IllegalArgumentException("Candidate action trace must not be empty");
        }
        for (int[] existing : retained) {
            int limit = Math.max(
                    1,
                    (int) Math.ceil(Math.max(candidate.length, existing.length) * 0.05));
            if (editDistance(candidate, existing) <= limit) {
                return false;
            }
        }
        retained.add(candidate.clone());
        return true;
    }

    private static int editDistance(int[] first, int[] second) {
        int[] previous = new int[second.length + 1];
        int[] current = new int[second.length + 1];
        for (int index = 0; index <= second.length; index++) {
            previous[index] = index;
        }
        for (int firstIndex = 1; firstIndex <= first.length; firstIndex++) {
            current[0] = firstIndex;
            for (int secondIndex = 1; secondIndex <= second.length; secondIndex++) {
                int substitution = previous[secondIndex - 1]
                        + (first[firstIndex - 1] == second[secondIndex - 1] ? 0 : 1);
                current[secondIndex] = Math.min(
                        Math.min(previous[secondIndex] + 1, current[secondIndex - 1] + 1),
                        substitution);
            }
            int[] swap = previous;
            previous = current;
            current = swap;
        }
        return previous[second.length];
    }
}

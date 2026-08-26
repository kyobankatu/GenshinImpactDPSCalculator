package mechanics.rotation;

import java.util.ArrayList;
import java.util.List;

/** Immutable search-internal action gene with run-length encoded short waits. */
public final class RotationWaitGene {
    private final int actionId;
    private final int runLength;

    private RotationWaitGene(int actionId, int runLength) {
        PolicyAction action = PolicyAction.fromId(actionId);
        if (runLength <= 0) {
            throw new IllegalArgumentException("runLength must be positive");
        }
        if (!action.isWait() && runLength != 1) {
            throw new IllegalArgumentException(
                    "Only WAIT_SHORT may have a runLength above one");
        }
        this.actionId = actionId;
        this.runLength = runLength;
    }

    /** Creates one validated gene. */
    public static RotationWaitGene of(int actionId, int runLength) {
        return new RotationWaitGene(actionId, runLength);
    }

    /** Compresses consecutive waits, splitting runs at the configured maximum. */
    public static List<RotationWaitGene> compress(
            int[] actions,
            int maxWaitRunLength) {
        if (actions == null) {
            throw new IllegalArgumentException("actions must not be null");
        }
        requireMaximum(maxWaitRunLength);
        List<RotationWaitGene> genes = new ArrayList<>();
        int index = 0;
        while (index < actions.length) {
            PolicyAction action = PolicyAction.fromId(actions[index]);
            if (!action.isWait()) {
                genes.add(of(actions[index], 1));
                index++;
                continue;
            }
            int runLength = 0;
            while (index < actions.length
                    && actions[index] == PolicyAction.WAIT_SHORT.getId()) {
                runLength++;
                index++;
            }
            while (runLength > 0) {
                int chunk = Math.min(runLength, maxWaitRunLength);
                genes.add(of(PolicyAction.WAIT_SHORT.getId(), chunk));
                runLength -= chunk;
            }
        }
        return List.copyOf(genes);
    }

    /** Expands genes to the unchanged policy-action representation. */
    public static int[] expand(
            List<RotationWaitGene> genes,
            int maxActions,
            int maxWaitRunLength) {
        if (genes == null) {
            throw new IllegalArgumentException("genes must not be null");
        }
        if (maxActions < 0) {
            throw new IllegalArgumentException("maxActions must be non-negative");
        }
        requireMaximum(maxWaitRunLength);
        long expandedLength = 0L;
        for (RotationWaitGene gene : genes) {
            validateGene(gene, maxWaitRunLength);
            expandedLength += gene.runLength;
            if (expandedLength > maxActions || expandedLength > Integer.MAX_VALUE) {
                throw new IllegalArgumentException(
                        "Expanded wait genes exceed maxActions");
            }
        }
        int[] actions = new int[(int) expandedLength];
        int offset = 0;
        for (RotationWaitGene gene : genes) {
            for (int repeat = 0; repeat < gene.runLength; repeat++) {
                actions[offset++] = gene.actionId;
            }
        }
        return actions;
    }

    public int getActionId() {
        return actionId;
    }

    public int getRunLength() {
        return runLength;
    }

    private static void validateGene(
            RotationWaitGene gene,
            int maxWaitRunLength) {
        if (gene == null) {
            throw new IllegalArgumentException("wait gene must not be null");
        }
        if (gene.runLength > maxWaitRunLength) {
            throw new IllegalArgumentException(
                    "Wait run exceeds the configured maximum");
        }
    }

    private static void requireMaximum(int maxWaitRunLength) {
        if (maxWaitRunLength <= 0) {
            throw new IllegalArgumentException(
                    "maxWaitRunLength must be positive");
        }
    }
}

package mechanics.rotation;

import java.util.ArrayList;
import java.util.List;

/**
 * Completes a source action trace to its fixed horizon with legal short waits.
 *
 * <p>
 * Human seed replay permits the final action to finish within the cycle-end
 * tolerance, while dataset trajectories require the environment's terminal
 * flag. This helper preserves the sourced actions and appends only the short
 * waits needed to satisfy that stricter contract.
 */
public final class RotationTraceCompletion {
    private RotationTraceCompletion() {
    }

    /**
     * Returns the unchanged source actions plus only the terminal waits required.
     *
     * @param scenario fixed-horizon scenario used by search and dataset replay
     * @param sourceActions legal sourced action trace
     * @return a legal trace whose final step is terminal
     */
    public static int[] complete(RotationScenario scenario, int[] sourceActions) {
        if (scenario == null || sourceActions == null || sourceActions.length == 0) {
            throw new IllegalArgumentException("Scenario and source actions are required");
        }
        List<Integer> completed = new ArrayList<>();
        try (BattleRotationEnvironment environment =
                new BattleRotationEnvironment(scenario)) {
            RotationStep step = environment.reset();
            for (int actionId : sourceActions) {
                if (step.done || !isLegal(step, actionId)) {
                    throw new IllegalArgumentException(
                            "Source trace cannot be completed at action " + completed.size());
                }
                step = environment.step(actionId);
                completed.add(actionId);
            }
            int waitAction = PolicyAction.WAIT_SHORT.getId();
            int maximumWaits = (int) Math.ceil(scenario.getHorizonSeconds() / 0.1) + 2;
            int waits = 0;
            while (!step.done && waits < maximumWaits) {
                if (!isLegal(step, waitAction)) {
                    throw new IllegalStateException(
                            "Short wait is unavailable while completing source trace");
                }
                step = environment.step(waitAction);
                completed.add(waitAction);
                waits++;
            }
            if (!step.done) {
                throw new IllegalStateException("Source trace did not reach its fixed horizon");
            }
        }
        return completed.stream().mapToInt(Integer::intValue).toArray();
    }

    private static boolean isLegal(RotationStep step, int actionId) {
        return actionId >= 0
                && actionId < step.legalActionMask.length
                && step.legalActionMask[actionId] > 0.5;
    }
}

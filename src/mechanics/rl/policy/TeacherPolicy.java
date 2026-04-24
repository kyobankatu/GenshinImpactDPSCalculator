package mechanics.rl.policy;

import mechanics.rl.RLAction;

/**
 * Reads teacher guidance from the 29-dimensional RL state vector.
 */
public class TeacherPolicy implements RLPolicy {
    @Override
    public int chooseAction(double[] state, boolean training) {
        int activeIndex = activeCharacterIndex(state);
        int targetIndex = targetCharacterIndex(state);
        if (targetIndex >= 0 && activeIndex != targetIndex) {
            return RLAction.SWAP_FLINS.getId() + targetIndex;
        }
        int suggested = suggestedAction(state);
        return suggested >= 0 ? suggested : RLAction.ATTACK.getId();
    }

    private int activeCharacterIndex(double[] state) {
        for (int i = 0; i < 4; i++) {
            if (state[i * 5 + 1] > 0.5) {
                return i;
            }
        }
        return -1;
    }

    private int targetCharacterIndex(double[] state) {
        int base = 22;
        for (int i = 0; i < 4; i++) {
            if (state[base + i] > 0.5) {
                return i;
            }
        }
        return -1;
    }

    private int suggestedAction(double[] state) {
        if (state[26] > 0.5) {
            return RLAction.ATTACK.getId();
        }
        if (state[27] > 0.5) {
            return RLAction.SKILL.getId();
        }
        if (state[28] > 0.5) {
            return RLAction.BURST.getId();
        }
        return -1;
    }
}

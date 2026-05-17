package mechanics.rl;

import model.entity.Character;
import model.type.CharacterId;
import simulation.CombatSimulator;

/**
 * Discrete action space and validity masking for RL.
 */
public class ActionSpace {
    /** Total number of discrete actions exposed to the policy. */
    public static final int SIZE = RLAction.SIZE;

    /**
     * Allocates and returns a freshly built action mask for the current simulator state.
     *
     * @param sim active combat simulator
     * @param lastSwapTime simulator time of the last successful swap
     * @param config episode configuration providing party order and swap cooldown
     * @return new double array of length {@code SIZE} where 1.0 marks a legal action
     */
    public double[] createMask(CombatSimulator sim, double lastSwapTime, EpisodeConfig config) {
        double[] mask = new double[SIZE];
        fillMask(sim, lastSwapTime, config, mask);
        return mask;
    }

    /**
     * Fills the provided array with legality flags for each action id.
     *
     * @param sim active combat simulator
     * @param lastSwapTime simulator time of the last successful swap
     * @param config episode configuration
     * @param mask output buffer of length {@code SIZE}; entries set to 1.0 (legal) or 0.0 (illegal)
     */
    public void fillMask(CombatSimulator sim, double lastSwapTime, EpisodeConfig config, double[] mask) {
        Character active = sim.getActiveCharacter();
        double now = sim.getCurrentTime();

        mask[RLAction.ATTACK.getId()] = active != null ? 1.0 : 0.0;
        mask[RLAction.SKILL.getId()] = active != null && active.canSkill(now) ? 1.0 : 0.0;
        mask[RLAction.BURST.getId()] = active != null && active.canBurst(now) ? 1.0 : 0.0;

        for (RLAction action : RLAction.values()) {
            if (!action.isSwap()) {
                continue;
            }
            int slot = action.getTargetSlot();
            CharacterId targetId = slot < config.partyOrder.length ? config.partyOrder[slot] : null;
            Character target = targetId != null ? sim.getCharacter(targetId) : null;
            boolean swapReady = now - lastSwapTime >= config.swapCooldown;
            boolean valid = target != null && active != null
                    && active.getCharacterId() != target.getCharacterId()
                    && swapReady;
            mask[action.getId()] = valid ? 1.0 : 0.0;
        }
    }

    /**
     * Tests whether the given action id is legal under the supplied mask.
     *
     * @param actionId candidate action id
     * @param mask legality mask from {@link #createMask}
     * @return true when the id is in range and the mask entry exceeds 0.5
     */
    public boolean isValid(int actionId, double[] mask) {
        return actionId >= 0 && actionId < mask.length && mask[actionId] > 0.5;
    }
}

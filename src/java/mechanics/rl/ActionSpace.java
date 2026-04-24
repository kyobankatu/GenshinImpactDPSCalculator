package mechanics.rl;

import model.entity.Character;
import simulation.CombatSimulator;

/**
 * Discrete action space and validity masking for RL.
 */
public class ActionSpace {
    public static final int SIZE = RLAction.SIZE;

    public double[] createMask(CombatSimulator sim, double lastSwapTime, EpisodeConfig config) {
        double[] mask = new double[SIZE];
        fillMask(sim, lastSwapTime, config, mask);
        return mask;
    }

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
            Character target = sim.getCharacter(action.getTargetCharacterId());
            boolean swapReady = now - lastSwapTime >= config.swapCooldown;
            boolean valid = target != null && active != null
                    && active.getCharacterId() != target.getCharacterId()
                    && swapReady;
            mask[action.getId()] = valid ? 1.0 : 0.0;
        }
    }

    public boolean isValid(int actionId, double[] mask) {
        return actionId >= 0 && actionId < mask.length && mask[actionId] > 0.5;
    }
}

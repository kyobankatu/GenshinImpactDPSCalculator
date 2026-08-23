package mechanics.rl;

import mechanics.rotation.ActionCapabilityStore;
import mechanics.rotation.PolicyAction;
import model.entity.Character;
import model.type.CharacterId;
import simulation.CombatSimulator;

/**
 * Discrete action space and validity masking for RL.
 */
public class ActionSpace {
    /** Total number of discrete actions exposed to the policy. */
    public static final int SIZE = RLAction.SIZE;
    private static final ActionCapabilityStore DEFAULT_CAPABILITIES = new ActionCapabilityStore();
    private final ActionCapabilityStore capabilityStore;

    /** Creates an action space backed by the tracked strict capability store. */
    public ActionSpace() {
        this(DEFAULT_CAPABILITIES);
    }

    /** Creates an action space with an explicit capability store for tests/tools. */
    public ActionSpace(ActionCapabilityStore capabilityStore) {
        if (capabilityStore == null) {
            throw new IllegalArgumentException("capabilityStore must not be null");
        }
        this.capabilityStore = capabilityStore;
    }

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

        java.util.Arrays.fill(mask, 0.0);
        if (active != null) {
            CharacterId activeId = active.getCharacterId();
            for (RLAction action : RLAction.values()) {
                if (action.isSwap() || action.isWait()) {
                    continue;
                }
                mask[action.getId()] = supported(active, activeId, action, now);
            }
        }
        mask[RLAction.WAIT_SHORT.getId()] = 1.0;

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

    private double supported(
            Character active,
            CharacterId characterId,
            RLAction action,
            double currentTime) {
        PolicyAction policyAction = PolicyAction.fromId(action.getId());
        return capabilityStore.supports(characterId, policyAction)
                && active.canPerformAction(
                        action.getActionRequest(), currentTime)
                ? 1.0 : 0.0;
    }
}

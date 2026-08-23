package mechanics.rl;

import mechanics.rotation.PolicyAction;
import simulation.action.CharacterActionKey;
import simulation.action.CharacterActionRequest;

/**
 * RL-facing aliases for the canonical versioned policy action space.
 */
public enum RLAction {
    /** Normal attack with the active character. */
    NORMAL(PolicyAction.NORMAL),
    /** Charged attack with the active character. */
    CHARGE(PolicyAction.CHARGE),
    /** Plunging attack with the active character. */
    PLUNGE(PolicyAction.PLUNGE),
    /** Press Elemental Skill with the active character. */
    SKILL_PRESS(PolicyAction.SKILL_PRESS),
    /** Hold Elemental Skill with the active character. */
    SKILL_HOLD(PolicyAction.SKILL_HOLD),
    /** Elemental Burst with the active character. */
    BURST(PolicyAction.BURST),
    /** Advance a short deterministic interval without a character action. */
    WAIT_SHORT(PolicyAction.WAIT_SHORT),
    /** Swap to the character at party slot 0. */
    SWAP_SLOT_0(PolicyAction.SWAP_SLOT_0),
    /** Swap to the character at party slot 1. */
    SWAP_SLOT_1(PolicyAction.SWAP_SLOT_1),
    /** Swap to the character at party slot 2. */
    SWAP_SLOT_2(PolicyAction.SWAP_SLOT_2),
    /** Swap to the character at party slot 3. */
    SWAP_SLOT_3(PolicyAction.SWAP_SLOT_3);

    /** Total number of RL actions in this enum. */
    public static final int SIZE = values().length;

    private final PolicyAction policyAction;

    RLAction(PolicyAction policyAction) {
        this.policyAction = policyAction;
    }

    /**
     * @return integer action id used over the wire and in the policy output
     */
    public int getId() {
        return policyAction.getId();
    }

    /**
     * @return underlying combat action key for skill/burst/normal, or null for swaps
     */
    public CharacterActionKey getActionKey() {
        CharacterActionRequest request = policyAction.getActionRequest();
        return request == null ? null : request.getKey();
    }

    /** Returns the complete typed action request, including Skill mode. */
    public CharacterActionRequest getActionRequest() {
        return policyAction.getActionRequest();
    }

    /**
     * Returns the party slot index (0-3) for swap actions, or -1 for non-swap actions.
     *
     * @return target party slot for swaps, or -1 when this action is not a swap
     */
    public int getTargetSlot() {
        return policyAction.getTargetSlot();
    }

    /**
     * @return true when this action is a swap (i.e. {@code targetSlot} is non-negative)
     */
    public boolean isSwap() {
        return policyAction.isSwap();
    }

    /** Returns whether this action advances time without dispatching a character action. */
    public boolean isWait() {
        return policyAction.isWait();
    }

    /**
     * Looks up the enum constant that has the given integer id.
     *
     * @param id action id
     * @return matching RLAction constant
     * @throws IllegalArgumentException if no action has the given id
     */
    public static RLAction fromId(int id) {
        for (RLAction action : values()) {
            if (action.getId() == id) {
                return action;
            }
        }
        throw new IllegalArgumentException("Unknown RL action id: " + id);
    }
}

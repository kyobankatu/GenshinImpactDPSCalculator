package mechanics.rl;

import simulation.action.CharacterActionKey;

/**
 * Fixed Java-native RL action space.
 */
public enum RLAction {
    /** Normal attack with the active character. */
    ATTACK(0, CharacterActionKey.NORMAL, -1),
    /** Elemental skill with the active character. */
    SKILL(1, CharacterActionKey.SKILL, -1),
    /** Elemental burst with the active character. */
    BURST(2, CharacterActionKey.BURST, -1),
    /** Swap to the character at party slot 0. */
    SWAP_SLOT_0(3, null, 0),
    /** Swap to the character at party slot 1. */
    SWAP_SLOT_1(4, null, 1),
    /** Swap to the character at party slot 2. */
    SWAP_SLOT_2(5, null, 2),
    /** Swap to the character at party slot 3. */
    SWAP_SLOT_3(6, null, 3);

    /** Total number of RL actions in this enum. */
    public static final int SIZE = values().length;

    private final int id;
    private final CharacterActionKey actionKey;
    private final int targetSlot;

    RLAction(int id, CharacterActionKey actionKey, int targetSlot) {
        this.id = id;
        this.actionKey = actionKey;
        this.targetSlot = targetSlot;
    }

    /**
     * @return integer action id used over the wire and in the policy output
     */
    public int getId() {
        return id;
    }

    /**
     * @return underlying combat action key for skill/burst/normal, or null for swaps
     */
    public CharacterActionKey getActionKey() {
        return actionKey;
    }

    /**
     * Returns the party slot index (0-3) for swap actions, or -1 for non-swap actions.
     *
     * @return target party slot for swaps, or -1 when this action is not a swap
     */
    public int getTargetSlot() {
        return targetSlot;
    }

    /**
     * @return true when this action is a swap (i.e. {@code targetSlot} is non-negative)
     */
    public boolean isSwap() {
        return targetSlot >= 0;
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
            if (action.id == id) {
                return action;
            }
        }
        throw new IllegalArgumentException("Unknown RL action id: " + id);
    }
}

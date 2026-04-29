package mechanics.rl;

import simulation.action.CharacterActionKey;

/**
 * Fixed Java-native RL action space.
 */
public enum RLAction {
    ATTACK(0, CharacterActionKey.NORMAL, -1),
    SKILL(1, CharacterActionKey.SKILL, -1),
    BURST(2, CharacterActionKey.BURST, -1),
    SWAP_SLOT_0(3, null, 0),
    SWAP_SLOT_1(4, null, 1),
    SWAP_SLOT_2(5, null, 2),
    SWAP_SLOT_3(6, null, 3);

    public static final int SIZE = values().length;

    private final int id;
    private final CharacterActionKey actionKey;
    private final int targetSlot;

    RLAction(int id, CharacterActionKey actionKey, int targetSlot) {
        this.id = id;
        this.actionKey = actionKey;
        this.targetSlot = targetSlot;
    }

    public int getId() {
        return id;
    }

    public CharacterActionKey getActionKey() {
        return actionKey;
    }

    /**
     * Returns the party slot index (0-3) for swap actions, or -1 for non-swap actions.
     */
    public int getTargetSlot() {
        return targetSlot;
    }

    public boolean isSwap() {
        return targetSlot >= 0;
    }

    public static RLAction fromId(int id) {
        for (RLAction action : values()) {
            if (action.id == id) {
                return action;
            }
        }
        throw new IllegalArgumentException("Unknown RL action id: " + id);
    }
}

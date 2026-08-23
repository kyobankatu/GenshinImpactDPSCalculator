package mechanics.rotation;

import simulation.action.CharacterActionKey;
import simulation.action.CharacterActionRequest;
import simulation.action.SkillActionMode;

/** Versioned action identities shared by search, RL, datasets, and policies. */
public enum PolicyAction {
    NORMAL(0, CharacterActionRequest.of(CharacterActionKey.NORMAL), -1),
    CHARGE(1, CharacterActionRequest.of(CharacterActionKey.CHARGE), -1),
    PLUNGE(2, CharacterActionRequest.of(CharacterActionKey.PLUNGE), -1),
    SKILL_PRESS(3, CharacterActionRequest.skill(SkillActionMode.PRESS), -1),
    SKILL_HOLD(4, CharacterActionRequest.skill(SkillActionMode.HOLD), -1),
    BURST(5, CharacterActionRequest.of(CharacterActionKey.BURST), -1),
    WAIT_SHORT(6, null, -1),
    SWAP_SLOT_0(7, null, 0),
    SWAP_SLOT_1(8, null, 1),
    SWAP_SLOT_2(9, null, 2),
    SWAP_SLOT_3(10, null, 3);

    /** Revision written to protocol handshakes, datasets, and checkpoints. */
    public static final int LAYOUT_REVISION = 2;
    /** Number of stable action IDs. */
    public static final int SIZE = values().length;

    private final int id;
    private final CharacterActionRequest actionRequest;
    private final int targetSlot;

    PolicyAction(int id, CharacterActionRequest actionRequest, int targetSlot) {
        this.id = id;
        this.actionRequest = actionRequest;
        this.targetSlot = targetSlot;
    }

    public int getId() {
        return id;
    }

    public CharacterActionRequest getActionRequest() {
        return actionRequest;
    }

    public int getTargetSlot() {
        return targetSlot;
    }

    public boolean isSwap() {
        return targetSlot >= 0;
    }

    public boolean isWait() {
        return this == WAIT_SHORT;
    }

    public boolean requiresCharacterCapability() {
        return actionRequest != null;
    }

    /** Returns the action with the supplied stable ID. */
    public static PolicyAction fromId(int id) {
        for (PolicyAction action : values()) {
            if (action.id == id) {
                return action;
            }
        }
        throw new IllegalArgumentException("Unknown policy action id: " + id);
    }
}

package mechanics.rl;

import model.type.CharacterId;
import simulation.action.CharacterActionKey;

/**
 * Fixed Java-native RL action space.
 */
public enum RLAction {
    ATTACK(0, CharacterActionKey.NORMAL, null),
    SKILL(1, CharacterActionKey.SKILL, null),
    BURST(2, CharacterActionKey.BURST, null),
    SWAP_FLINS(3, null, CharacterId.FLINS),
    SWAP_INEFFA(4, null, CharacterId.INEFFA),
    SWAP_COLUMBINA(5, null, CharacterId.COLUMBINA),
    SWAP_SUCROSE(6, null, CharacterId.SUCROSE);

    public static final int SIZE = values().length;

    private final int id;
    private final CharacterActionKey actionKey;
    private final CharacterId targetCharacterId;

    RLAction(int id, CharacterActionKey actionKey, CharacterId targetCharacterId) {
        this.id = id;
        this.actionKey = actionKey;
        this.targetCharacterId = targetCharacterId;
    }

    public int getId() {
        return id;
    }

    public CharacterActionKey getActionKey() {
        return actionKey;
    }

    public CharacterId getTargetCharacterId() {
        return targetCharacterId;
    }

    public boolean isSwap() {
        return targetCharacterId != null;
    }

    public static RLAction fromId(int id) {
        for (RLAction action : values()) {
            if (action.id == id) {
                return action;
            }
        }
        throw new IllegalArgumentException("Unknown RL action id: " + id);
    }

    public static int swapActionId(CharacterId id) {
        for (RLAction action : values()) {
            if (action.targetCharacterId == id) {
                return action.id;
            }
        }
        return -1;
    }
}

package mechanics.optimization;

import simulation.action.CharacterActionKey;

/**
 * Typed action commands used by optimizer profiles and scripted combat routines.
 */
public enum ProfileAction {
    ATTACK(CharacterActionKey.NORMAL),
    SKILL(CharacterActionKey.SKILL),
    BURST(CharacterActionKey.BURST),
    ATTACK_UNTIL_END(CharacterActionKey.NORMAL);

    private final CharacterActionKey key;

    ProfileAction(CharacterActionKey key) {
        this.key = key;
    }

    public CharacterActionKey getKey() {
        return key;
    }

    public static ProfileAction parse(String raw) {
        return ProfileAction.valueOf(raw.trim().toUpperCase());
    }
}

package simulation.action;

import java.util.Objects;

/**
 * Typed request for a non-hit character action.
 *
 * <p>This is the simulator's canonical dispatch payload for top-level character
 * actions such as Normal, Skill, or Burst.
 */
public final class CharacterActionRequest {
    private final CharacterActionKey key;

    private CharacterActionRequest(CharacterActionKey key) {
        this.key = Objects.requireNonNull(key, "key");
    }

    public static CharacterActionRequest of(CharacterActionKey key) {
        return new CharacterActionRequest(key);
    }

    public CharacterActionKey getKey() {
        return key;
    }

    public String getLogLabel() {
        switch (key) {
            case NORMAL:
                return "attack";
            case CHARGE:
                return "charge";
            case SKILL:
                return "skill";
            case BURST:
                return "burst";
            case DASH:
                return "dash";
            case PLUNGE:
                return "plunge";
            default:
                return key.name().toLowerCase();
        }
    }
}

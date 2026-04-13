package simulation.action;

import java.util.Locale;
import java.util.Objects;

/**
 * Typed request for a non-hit character action.
 *
 * <p>This acts as the compatibility boundary between legacy string commands
 * such as {@code "attack"} or {@code "Q"} and the typed dispatch API.
 */
public final class CharacterActionRequest {
    private final CharacterActionKey key;
    private final String legacyActionKey;

    private CharacterActionRequest(CharacterActionKey key, String legacyActionKey) {
        this.key = Objects.requireNonNull(key, "key");
        this.legacyActionKey = Objects.requireNonNull(legacyActionKey, "legacyActionKey");
    }

    public static CharacterActionRequest of(CharacterActionKey key) {
        switch (key) {
            case NORMAL:
                return new CharacterActionRequest(key, "attack");
            case CHARGE:
                return new CharacterActionRequest(key, "charge");
            case SKILL:
                return new CharacterActionRequest(key, "skill");
            case BURST:
                return new CharacterActionRequest(key, "burst");
            case DASH:
                return new CharacterActionRequest(key, "dash");
            case PLUNGE:
                return new CharacterActionRequest(key, "plunge");
            default:
                throw new IllegalArgumentException("Unsupported action key: " + key);
        }
    }

    public static CharacterActionRequest fromLegacy(String actionKey) {
        if (actionKey == null) {
            throw new IllegalArgumentException("Action key must not be null");
        }

        String normalized = actionKey.trim().toLowerCase(Locale.ROOT);
        switch (normalized) {
            case "attack":
            case "normal":
            case "na":
            case "n":
                return of(CharacterActionKey.NORMAL);
            case "charge":
            case "charged":
            case "ca":
            case "c":
                return of(CharacterActionKey.CHARGE);
            case "skill":
            case "e":
                return of(CharacterActionKey.SKILL);
            case "burst":
            case "q":
                return of(CharacterActionKey.BURST);
            case "dash":
            case "sprint":
                return of(CharacterActionKey.DASH);
            case "plunge":
            case "low_plunge":
            case "high_plunge":
                return of(CharacterActionKey.PLUNGE);
            default:
                throw new IllegalArgumentException("Unknown legacy action key: " + actionKey);
        }
    }

    public CharacterActionKey getKey() {
        return key;
    }

    public String getLegacyActionKey() {
        return legacyActionKey;
    }
}

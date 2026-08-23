package mechanics.rotation;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import model.type.CharacterId;

/** Loads strict typed character action capabilities from tracked JSON data. */
public final class ActionCapabilityStore {
    public static final int SCHEMA_REVISION = 1;
    public static final Path DEFAULT_PATH = Path.of("config", "action_capabilities.json");

    private final Map<CharacterId, EnumSet<PolicyAction>> capabilities;

    /** Loads the tracked default capability file. */
    public ActionCapabilityStore() {
        this(DEFAULT_PATH);
    }

    /** Loads and validates one capability file. */
    public ActionCapabilityStore(Path path) {
        if (path == null) {
            throw new IllegalArgumentException("capability path must not be null");
        }
        try {
            Parser parser = new Parser(Files.readString(path));
            this.capabilities = parser.parse();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load action capabilities from " + path, e);
        }
    }

    /** Returns whether a character supports an active-character combat action. */
    public boolean supports(CharacterId characterId, PolicyAction action) {
        if (characterId == null) {
            throw new IllegalArgumentException("characterId must not be null");
        }
        if (action == null || !action.requiresCharacterCapability()) {
            throw new IllegalArgumentException("action must be a character capability");
        }
        EnumSet<PolicyAction> actions = capabilities.get(characterId);
        if (actions == null) {
            throw new IllegalStateException("Missing action capabilities for " + characterId);
        }
        return actions.contains(action);
    }

    /** Returns whether this store contains a strict entry for the character. */
    public boolean contains(CharacterId characterId) {
        return capabilities.containsKey(characterId);
    }

    private static final class Parser {
        private final String text;
        private int index;

        private Parser(String text) {
            this.text = text;
        }

        private Map<CharacterId, EnumSet<PolicyAction>> parse() {
            Map<CharacterId, EnumSet<PolicyAction>> parsed = new EnumMap<>(CharacterId.class);
            Integer revision = null;
            boolean foundCharacters = false;
            Set<String> rootKeys = new HashSet<>();
            expect('{');
            while (!peek('}')) {
                String key = readString();
                if (!rootKeys.add(key)) {
                    fail("duplicate root key " + key);
                }
                expect(':');
                if ("revision".equals(key)) {
                    revision = readInteger();
                } else if ("characters".equals(key)) {
                    parseCharacters(parsed);
                    foundCharacters = true;
                } else {
                    fail("unknown root key " + key);
                }
                if (!consume(',')) {
                    break;
                }
            }
            expect('}');
            skipWhitespace();
            if (index != text.length()) {
                fail("trailing content");
            }
            if (revision == null || revision != SCHEMA_REVISION) {
                fail("expected revision " + SCHEMA_REVISION);
            }
            if (!foundCharacters || parsed.isEmpty()) {
                fail("characters must not be empty");
            }
            return parsed;
        }

        private void parseCharacters(Map<CharacterId, EnumSet<PolicyAction>> parsed) {
            expect('{');
            while (!peek('}')) {
                String characterName = readString();
                CharacterId characterId;
                try {
                    characterId = CharacterId.valueOf(characterName);
                } catch (IllegalArgumentException e) {
                    fail("unknown character " + characterName);
                    return;
                }
                if (parsed.containsKey(characterId)) {
                    fail("duplicate character " + characterName);
                }
                expect(':');
                parsed.put(characterId, parseActions(characterId));
                if (!consume(',')) {
                    break;
                }
            }
            expect('}');
        }

        private EnumSet<PolicyAction> parseActions(CharacterId characterId) {
            EnumSet<PolicyAction> actions = EnumSet.noneOf(PolicyAction.class);
            expect('[');
            while (!peek(']')) {
                String actionName = readString();
                PolicyAction action;
                try {
                    action = PolicyAction.valueOf(actionName);
                } catch (IllegalArgumentException e) {
                    fail("unknown action " + actionName + " for " + characterId);
                    return actions;
                }
                if (!action.requiresCharacterCapability()) {
                    fail("non-character action " + actionName + " for " + characterId);
                }
                if (!actions.add(action)) {
                    fail("duplicate action " + actionName + " for " + characterId);
                }
                if (!consume(',')) {
                    break;
                }
            }
            expect(']');
            if (!actions.contains(PolicyAction.NORMAL)) {
                fail("NORMAL capability is required for " + characterId);
            }
            return actions;
        }

        private int readInteger() {
            skipWhitespace();
            int start = index;
            while (index < text.length() && java.lang.Character.isDigit(text.charAt(index))) {
                index++;
            }
            if (start == index) {
                fail("expected integer");
            }
            return Integer.parseInt(text.substring(start, index));
        }

        private String readString() {
            skipWhitespace();
            expectRaw('"');
            StringBuilder value = new StringBuilder();
            while (index < text.length()) {
                char current = text.charAt(index++);
                if (current == '"') {
                    return value.toString();
                }
                if (current == '\\') {
                    if (index >= text.length()) {
                        fail("unterminated escape");
                    }
                    char escaped = text.charAt(index++);
                    if (escaped != '"' && escaped != '\\') {
                        fail("unsupported escape");
                    }
                    value.append(escaped);
                } else {
                    value.append(current);
                }
            }
            fail("unterminated string");
            return "";
        }

        private boolean peek(char expected) {
            skipWhitespace();
            return index < text.length() && text.charAt(index) == expected;
        }

        private boolean consume(char expected) {
            if (!peek(expected)) {
                return false;
            }
            index++;
            return true;
        }

        private void expect(char expected) {
            skipWhitespace();
            expectRaw(expected);
        }

        private void expectRaw(char expected) {
            if (index >= text.length() || text.charAt(index) != expected) {
                fail("expected '" + expected + "'");
            }
            index++;
        }

        private void skipWhitespace() {
            while (index < text.length() && java.lang.Character.isWhitespace(text.charAt(index))) {
                index++;
            }
        }

        private void fail(String message) {
            throw new IllegalArgumentException(
                    "Invalid action capability JSON at offset " + index + ": " + message);
        }
    }
}

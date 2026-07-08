package simulation.party;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Registry of simulator party definitions shared by sample runners and RL.
 */
public final class PartyCatalog {
    private static final Map<String, PartyDefinition> DEFINITIONS = new LinkedHashMap<>();

    static {
        register(new FlinsParty2Definition());
        register(new RaidenPartyDefinition());
        register(new FlinsPartyDefinition());
    }

    private PartyCatalog() {
    }

    public static void register(PartyDefinition definition) {
        DEFINITIONS.put(normalize(definition.name()), definition);
    }

    public static PartyDefinition require(String partyName) {
        PartyDefinition definition = find(partyName);
        if (definition == null) {
            throw new IllegalArgumentException("Unknown party: " + partyName + ". Available parties: "
                    + availablePartyNames());
        }
        return definition;
    }

    public static PartyDefinition find(String partyName) {
        if (partyName == null || partyName.isBlank()) {
            return null;
        }
        return DEFINITIONS.get(normalize(partyName));
    }

    public static Collection<PartyDefinition> all() {
        return new ArrayList<>(DEFINITIONS.values());
    }

    public static List<PartyDefinition> rlEnabled() {
        return DEFINITIONS.values().stream()
                .filter(PartyDefinition::rlEnabled)
                .collect(Collectors.toList());
    }

    public static String availablePartyNames() {
        return DEFINITIONS.values().stream()
                .map(PartyDefinition::name)
                .collect(Collectors.joining(", "));
    }

    private static String normalize(String value) {
        return value.toLowerCase(Locale.ROOT);
    }
}

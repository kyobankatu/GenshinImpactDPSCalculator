package mechanics.rl;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Central registry for RL-available party variants.
 */
public final class RLPartyRegistry {
    public static final String DEFAULT_TRAINING_SELECTION = "default";
    public static final String DEFAULT_SINGLE_PARTY = "FlinsParty2";
    public static final String ALL_PARTIES_SELECTION = "all";

    private static final Map<String, RLPartySpec> SPECS = new LinkedHashMap<>();
    private static final List<String> DEFAULT_TRAINING_PARTIES = List.of(
            "FlinsParty2",
            "RaidenParty");

    static {
        register(FlinsParty2RLSimulatorFactory.spec());
        register(RaidenPartyRLSimulatorFactory.spec());
    }

    private RLPartyRegistry() {
    }

    public static void register(RLPartySpec spec) {
        SPECS.put(spec.getPartyName(), spec);
    }

    public static RLPartySpec require(String partyName) {
        RLPartySpec spec = SPECS.get(partyName);
        if (spec == null) {
            throw new IllegalArgumentException("Unknown RL party: " + partyName + ". Available: " + SPECS.keySet());
        }
        return spec;
    }

    public static List<RLPartySpec> resolveSelection(String selection) {
        String normalized = selection == null || selection.isBlank()
                ? DEFAULT_SINGLE_PARTY
                : selection.trim();
        String lower = normalized.toLowerCase(Locale.ROOT);
        if (DEFAULT_TRAINING_SELECTION.equals(lower)) {
            return resolveNamedList(DEFAULT_TRAINING_PARTIES);
        }
        if (ALL_PARTIES_SELECTION.equals(lower)) {
            return allSpecs();
        }
        String[] tokens = normalized.split(",");
        List<String> names = new ArrayList<>();
        for (String token : tokens) {
            String name = token.trim();
            if (!name.isEmpty()) {
                names.add(name);
            }
        }
        if (names.isEmpty()) {
            return resolveNamedList(List.of(DEFAULT_SINGLE_PARTY));
        }
        return resolveNamedList(names);
    }

    public static List<RLPartySpec> defaultTrainingSpecs() {
        return resolveNamedList(DEFAULT_TRAINING_PARTIES);
    }

    public static List<RLPartySpec> allSpecs() {
        return new ArrayList<>(SPECS.values());
    }

    public static String availablePartyNames() {
        return String.join(", ", SPECS.keySet());
    }

    public static RLEpisodeFactory createEpisodeFactory(EpisodeConfig baseConfig, String selection) {
        return createEpisodeFactory(baseConfig, resolveSelection(selection));
    }

    public static RLEpisodeFactory createEpisodeFactory(EpisodeConfig baseConfig, List<RLPartySpec> specs) {
        if (specs.size() == 1) {
            return new SinglePartyRLEpisodeFactory(specs.get(0), baseConfig);
        }
        return new MultiPartyRLSimulatorFactory(baseConfig, specs);
    }

    private static List<RLPartySpec> resolveNamedList(List<String> names) {
        List<RLPartySpec> specs = new ArrayList<>();
        for (String name : names) {
            specs.add(require(name));
        }
        return specs;
    }
}

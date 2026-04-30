package sample;

import mechanics.rl.CapabilityProfiler;
import mechanics.rl.EpisodeConfig;
import mechanics.rl.RLPartyRegistry;
import mechanics.rl.RLPartySpec;
import model.type.CharacterId;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Entry point for generating capability profiles for one or more registered RL parties.
 *
 * <p>Writes {@code config/capability_profiles/profiles.json}, which is loaded by
 * {@link mechanics.rl.ObservationEncoder} to populate the static capability features
 * in the RL observation vector.
 *
 * <p>Run via: {@code ./gradlew ProfileCapabilities}
 */
public class ProfileCharacterCapabilities {
    public static void main(String[] args) throws Exception {
        String outputPath = args.length > 0 ? args[0] : "config/capability_profiles/profiles.json";
        String selection = args.length > 1 ? args[1] : RLPartyRegistry.ALL_PARTIES_SELECTION;
        Map<CharacterId, double[]> mergedProfiles = new LinkedHashMap<>();
        List<RLPartySpec> specs = RLPartyRegistry.resolveSelection(selection);
        for (RLPartySpec spec : specs) {
            CapabilityProfiler profiler = new CapabilityProfiler(
                    spec.getSimulatorSupplier(),
                    new EpisodeConfig().withPartyOrder(spec.getPartyOrder()));
            profiler.runAll();
            mergedProfiles.putAll(profiler.getResults());
        }

        CapabilityProfiler.writeJson(outputPath, mergedProfiles);
    }
}

package sample;

import mechanics.rl.CapabilityProfiler;
import mechanics.rl.EpisodeConfig;
import mechanics.rl.FlinsParty2RLSimulatorFactory;
import mechanics.rl.RaidenPartyRLSimulatorFactory;
import model.type.CharacterId;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Entry point for generating capability profiles for the FlinsParty2 characters.
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
        Map<CharacterId, double[]> mergedProfiles = new LinkedHashMap<>();

        CapabilityProfiler flinsProfiler = new CapabilityProfiler(
                FlinsParty2RLSimulatorFactory.supplier(), new EpisodeConfig());
        flinsProfiler.runAll();
        mergedProfiles.putAll(flinsProfiler.getResults());

        CapabilityProfiler raidenProfiler = new CapabilityProfiler(
                RaidenPartyRLSimulatorFactory.supplier(),
                new EpisodeConfig(RaidenPartyRLSimulatorFactory.PARTY_ORDER, 20.0, 0.1, 1.0, 1000.0, 0.35, 0.10, 0.03, 25000.0, true));
        raidenProfiler.runAll();
        mergedProfiles.putAll(raidenProfiler.getResults());

        CapabilityProfiler.writeJson(outputPath, mergedProfiles);
    }
}

package sample;

import mechanics.rl.CapabilityProfiler;
import mechanics.rl.EpisodeConfig;
import mechanics.rl.RLPartyRegistry;
import mechanics.rl.RLPartySpec;
import model.type.CharacterId;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

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
    /**
     * Profiles one or more registered RL parties and writes the merged capability JSON.
     *
     * @param args command-line arguments in the order output path and party selection
     * @throws Exception if profile generation or writing fails
     */
    public static void main(String[] args) throws Exception {
        String outputPath = args.length > 0 ? args[0] : "config/capability_profiles/profiles.json";
        String selection = args.length > 1 ? args[1] : RLPartyRegistry.ALL_PARTIES_SELECTION;
        Map<CharacterId, double[]> mergedProfiles = new LinkedHashMap<>();
        List<RLPartySpec> specs = RLPartyRegistry.resolveSelection(selection);
        int workerCount = Math.max(1, Math.min(
                specs.size(),
                Integer.getInteger("rotation.profile.workers", 8)));
        ExecutorService executor = Executors.newFixedThreadPool(workerCount);
        List<Future<ProfileBatch>> futures = new ArrayList<>();
        try {
            for (int index = 0; index < specs.size(); index++) {
                final int catalogIndex = index;
                final RLPartySpec spec = specs.get(index);
                futures.add(executor.submit(() -> profile(catalogIndex, spec)));
            }
            ProfileBatch[] batches = new ProfileBatch[specs.size()];
            for (Future<ProfileBatch> future : futures) {
                ProfileBatch batch = future.get();
                batches[batch.catalogIndex] = batch;
            }
            for (ProfileBatch batch : batches) {
                mergedProfiles.putAll(batch.profiles);
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Capability profiling was interrupted", exception);
        } catch (ExecutionException exception) {
            throw new IllegalStateException("Capability profiling worker failed", exception.getCause());
        } finally {
            executor.shutdownNow();
        }

        CapabilityProfiler.writeJson(outputPath, mergedProfiles);
    }

    private static ProfileBatch profile(int catalogIndex, RLPartySpec spec) {
        CapabilityProfiler profiler = new CapabilityProfiler(
                spec.getSimulatorSupplier(),
                new EpisodeConfig().withPartyOrder(spec.getPartyOrder()));
        profiler.runAll();
        return new ProfileBatch(catalogIndex, profiler.getResults());
    }

    /** One independently computed party profile retained in catalog order. */
    private static final class ProfileBatch {
        private final int catalogIndex;
        private final Map<CharacterId, double[]> profiles;

        private ProfileBatch(int catalogIndex, Map<CharacterId, double[]> profiles) {
            this.catalogIndex = catalogIndex;
            this.profiles = profiles;
        }
    }
}

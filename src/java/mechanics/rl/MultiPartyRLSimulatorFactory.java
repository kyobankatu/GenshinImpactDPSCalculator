package mechanics.rl;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Samples uniformly from multiple party simulators for RL training.
 */
public class MultiPartyRLSimulatorFactory implements RLEpisodeFactory {
    private final EpisodeConfig baseConfig;
    private final List<RLPartySpec> variants;

    public MultiPartyRLSimulatorFactory(EpisodeConfig baseConfig, List<RLPartySpec> variants) {
        if (variants == null || variants.isEmpty()) {
            throw new IllegalArgumentException("variants must not be empty");
        }
        this.baseConfig = baseConfig;
        this.variants = new ArrayList<>(variants);
    }

    public static MultiPartyRLSimulatorFactory defaultFactory(EpisodeConfig baseConfig) {
        return new MultiPartyRLSimulatorFactory(baseConfig, RLPartyRegistry.defaultTrainingSpecs());
    }

    @Override
    public EpisodeContext createEpisode(int preferredPartyId) {
        int partyId = preferredPartyId >= 0 && preferredPartyId < variants.size()
                ? preferredPartyId
                : ThreadLocalRandom.current().nextInt(variants.size());
        RLPartySpec variant = variants.get(partyId);
        return new EpisodeContext(
                variant.getSimulatorSupplier().get(),
                baseConfig.withPartyOrder(variant.getPartyOrder()),
                partyId,
                variant.getPartyName());
    }

    @Override
    public String[] getPartyNames() {
        String[] names = new String[variants.size()];
        for (int i = 0; i < variants.size(); i++) {
            names[i] = variants.get(i).getPartyName();
        }
        return names;
    }
}

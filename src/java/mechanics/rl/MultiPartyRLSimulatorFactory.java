package mechanics.rl;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

import model.type.CharacterId;
import simulation.CombatSimulator;

/**
 * Samples uniformly from multiple party simulators for RL training.
 */
public class MultiPartyRLSimulatorFactory implements RLEpisodeFactory {
    private final EpisodeConfig baseConfig;
    private final List<PartyVariant> variants;

    public MultiPartyRLSimulatorFactory(EpisodeConfig baseConfig, List<PartyVariant> variants) {
        if (variants == null || variants.isEmpty()) {
            throw new IllegalArgumentException("variants must not be empty");
        }
        this.baseConfig = baseConfig;
        this.variants = new ArrayList<>(variants);
    }

    public static MultiPartyRLSimulatorFactory defaultFactory(EpisodeConfig baseConfig) {
        List<PartyVariant> variants = new ArrayList<>();
        variants.add(new PartyVariant(
                "FlinsParty2",
                FlinsParty2RLSimulatorFactory.PARTY_ORDER,
                FlinsParty2RLSimulatorFactory.supplier()));
        variants.add(new PartyVariant(
                "RaidenParty",
                RaidenPartyRLSimulatorFactory.PARTY_ORDER,
                RaidenPartyRLSimulatorFactory.supplier()));
        return new MultiPartyRLSimulatorFactory(baseConfig, variants);
    }

    @Override
    public EpisodeContext createEpisode(int preferredPartyId) {
        int partyId = preferredPartyId >= 0 && preferredPartyId < variants.size()
                ? preferredPartyId
                : ThreadLocalRandom.current().nextInt(variants.size());
        PartyVariant variant = variants.get(partyId);
        return new EpisodeContext(
                variant.simulatorSupplier.get(),
                baseConfig.withPartyOrder(variant.partyOrder),
                partyId,
                variant.partyName);
    }

    @Override
    public String[] getPartyNames() {
        String[] names = new String[variants.size()];
        for (int i = 0; i < variants.size(); i++) {
            names[i] = variants.get(i).partyName;
        }
        return names;
    }

    public static final class PartyVariant {
        private final String partyName;
        private final CharacterId[] partyOrder;
        private final Supplier<CombatSimulator> simulatorSupplier;

        public PartyVariant(String partyName, CharacterId[] partyOrder, Supplier<CombatSimulator> simulatorSupplier) {
            this.partyName = partyName;
            this.partyOrder = partyOrder.clone();
            this.simulatorSupplier = simulatorSupplier;
        }
    }
}

package mechanics.rl;

import java.util.function.Supplier;

import model.type.CharacterId;
import simulation.CombatSimulator;

/**
 * Adapts one fixed simulator supplier into an {@link RLEpisodeFactory}.
 */
public class SinglePartyRLEpisodeFactory implements RLEpisodeFactory {
    private final RLPartySpec partySpec;
    private final EpisodeConfig baseConfig;

    /**
     * Creates a single-party episode factory from explicit party fields.
     */
    public SinglePartyRLEpisodeFactory(
            String partyName,
            CharacterId[] partyOrder,
            Supplier<CombatSimulator> simulatorSupplier,
            EpisodeConfig baseConfig) {
        this(new RLPartySpec(partyName, partyOrder, simulatorSupplier), baseConfig);
    }

    /**
     * Creates a single-party episode factory from one registered-style spec.
     */
    public SinglePartyRLEpisodeFactory(RLPartySpec partySpec, EpisodeConfig baseConfig) {
        this.partySpec = partySpec;
        this.baseConfig = baseConfig;
    }

    /**
     * Creates one episode for the configured party.
     */
    @Override
    public EpisodeContext createEpisode(int preferredPartyId) {
        return new EpisodeContext(
                partySpec.getSimulatorSupplier().get(),
                baseConfig.withPartyOrder(partySpec.getPartyOrder()),
                0,
                partySpec.getPartyName());
    }

    /**
     * Returns the single party name exposed by this factory.
     */
    @Override
    public String[] getPartyNames() {
        return new String[]{partySpec.getPartyName()};
    }
}

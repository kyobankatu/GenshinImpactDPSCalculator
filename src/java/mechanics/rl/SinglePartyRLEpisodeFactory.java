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

    public SinglePartyRLEpisodeFactory(
            String partyName,
            CharacterId[] partyOrder,
            Supplier<CombatSimulator> simulatorSupplier,
            EpisodeConfig baseConfig) {
        this(new RLPartySpec(partyName, partyOrder, simulatorSupplier), baseConfig);
    }

    public SinglePartyRLEpisodeFactory(RLPartySpec partySpec, EpisodeConfig baseConfig) {
        this.partySpec = partySpec;
        this.baseConfig = baseConfig;
    }

    @Override
    public EpisodeContext createEpisode(int preferredPartyId) {
        return new EpisodeContext(
                partySpec.getSimulatorSupplier().get(),
                baseConfig.withPartyOrder(partySpec.getPartyOrder()),
                0,
                partySpec.getPartyName());
    }

    @Override
    public String[] getPartyNames() {
        return new String[]{partySpec.getPartyName()};
    }
}

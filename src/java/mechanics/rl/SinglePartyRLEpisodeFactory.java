package mechanics.rl;

import java.util.function.Supplier;

import model.type.CharacterId;
import simulation.CombatSimulator;

/**
 * Adapts one fixed simulator supplier into an {@link RLEpisodeFactory}.
 */
public class SinglePartyRLEpisodeFactory implements RLEpisodeFactory {
    private final String partyName;
    private final CharacterId[] partyOrder;
    private final Supplier<CombatSimulator> simulatorSupplier;
    private final EpisodeConfig baseConfig;

    public SinglePartyRLEpisodeFactory(
            String partyName,
            CharacterId[] partyOrder,
            Supplier<CombatSimulator> simulatorSupplier,
            EpisodeConfig baseConfig) {
        this.partyName = partyName;
        this.partyOrder = partyOrder.clone();
        this.simulatorSupplier = simulatorSupplier;
        this.baseConfig = baseConfig;
    }

    @Override
    public EpisodeContext createEpisode(int preferredPartyId) {
        return new EpisodeContext(
                simulatorSupplier.get(),
                baseConfig.withPartyOrder(partyOrder),
                0,
                partyName);
    }

    @Override
    public String[] getPartyNames() {
        return new String[]{partyName};
    }
}

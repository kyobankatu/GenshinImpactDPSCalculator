package mechanics.rl;

import simulation.CombatSimulator;

/**
 * Creates simulator episodes together with the party-specific metadata required by RL.
 */
public interface RLEpisodeFactory {
    EpisodeContext createEpisode(int preferredPartyId);

    String[] getPartyNames();

    /**
     * One fully specified episode instance.
     */
    final class EpisodeContext {
        public final CombatSimulator simulator;
        public final EpisodeConfig config;
        public final int partyId;
        public final String partyName;

        public EpisodeContext(CombatSimulator simulator, EpisodeConfig config, int partyId, String partyName) {
            this.simulator = simulator;
            this.config = config;
            this.partyId = partyId;
            this.partyName = partyName;
        }
    }
}

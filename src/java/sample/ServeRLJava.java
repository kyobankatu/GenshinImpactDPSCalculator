package sample;

import mechanics.rl.EpisodeConfig;
import mechanics.rl.RLPartyRegistry;
import mechanics.rl.bridge.RolloutService;

/**
 * Starts the local Java rollout service used by the Python learner.
 */
public class ServeRLJava {
    public static void main(String[] args) throws Exception {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 5005;
        String bindHost = args.length > 1 ? args[1] : "127.0.0.1";
        int rolloutWorkers = args.length > 2 ? Integer.parseInt(args[2]) : 0;
        String selection = args.length > 3 ? normalizeSelectionArg(args[3]) : RLPartyRegistry.DEFAULT_SINGLE_PARTY;
        EpisodeConfig config = new EpisodeConfig();
        RolloutService service = new RolloutService(
                port,
                bindHost,
                RLPartyRegistry.createEpisodeFactory(config, selection),
                rolloutWorkers);
        service.serveForever();
    }

    private static String normalizeSelectionArg(String rawArg) {
        if ("true".equalsIgnoreCase(rawArg)) {
            return RLPartyRegistry.DEFAULT_TRAINING_SELECTION;
        }
        if ("false".equalsIgnoreCase(rawArg)) {
            return RLPartyRegistry.DEFAULT_SINGLE_PARTY;
        }
        return rawArg;
    }
}

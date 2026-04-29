package sample;

import mechanics.rl.EpisodeConfig;
import mechanics.rl.MultiPartyRLSimulatorFactory;
import mechanics.rl.bridge.RolloutService;

/**
 * Starts the local Java rollout service used by the Python learner.
 */
public class ServeRLJava {
    public static void main(String[] args) throws Exception {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 5005;
        String bindHost = args.length > 1 ? args[1] : "127.0.0.1";
        int rolloutWorkers = args.length > 2 ? Integer.parseInt(args[2]) : 0;
        boolean useMultiParty = args.length > 3 && Boolean.parseBoolean(args[3]);
        EpisodeConfig config = new EpisodeConfig();
        RolloutService service = useMultiParty
                ? new RolloutService(
                        port,
                        bindHost,
                        MultiPartyRLSimulatorFactory.defaultFactory(config),
                        rolloutWorkers)
                : new RolloutService(port, bindHost, config, rolloutWorkers);
        service.serveForever();
    }
}

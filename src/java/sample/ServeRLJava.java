package sample;

import mechanics.rl.EpisodeConfig;
import mechanics.rl.bridge.RolloutService;

/**
 * Starts the local Java rollout service used by the Python learner.
 */
public class ServeRLJava {
    public static void main(String[] args) throws Exception {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 5005;
        RolloutService service = new RolloutService(port, new EpisodeConfig());
        service.serveForever();
    }
}

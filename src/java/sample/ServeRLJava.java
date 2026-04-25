package sample;

import mechanics.rl.EpisodeConfig;
import mechanics.rl.bridge.RolloutService;

/**
 * Starts the local Java rollout service used by the Python learner.
 */
public class ServeRLJava {
    public static void main(String[] args) throws Exception {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 5005;
        String bindHost = args.length > 1 ? args[1] : "127.0.0.1";
        RolloutService service = new RolloutService(port, bindHost, new EpisodeConfig());
        service.serveForever();
    }
}

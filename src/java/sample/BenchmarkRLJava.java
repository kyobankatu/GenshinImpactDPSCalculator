package sample;

import mechanics.rl.BattleEnvironment;
import mechanics.rl.EpisodeConfig;
import mechanics.rl.RLPartyRegistry;
import mechanics.rl.bridge.VectorizedEnvironment;

/**
 * Local benchmark for vectorized Java environment stepping.
 */
public class BenchmarkRLJava {
    public static void main(String[] args) {
        int environments = args.length > 0 ? Integer.parseInt(args[0]) : 4;
        int steps = args.length > 1 ? Integer.parseInt(args[1]) : 128;
        int workers = args.length > 2 ? Integer.parseInt(args[2]) : 0;
        String selection = args.length > 3 ? normalizeSelectionArg(args[3]) : RLPartyRegistry.DEFAULT_SINGLE_PARTY;

        EpisodeConfig config = new EpisodeConfig();
        VectorizedEnvironment environment = new VectorizedEnvironment(
                environments,
                RLPartyRegistry.createEpisodeFactory(config, selection),
                workers,
                new mechanics.rl.ObservationEncoder());
        environment.reset(false);
        int[] actions = new int[environments];

        long start = System.currentTimeMillis();
        for (int step = 0; step < steps; step++) {
            for (int i = 0; i < environments; i++) {
                actions[i] = step % 3;
            }
            environment.step(actions);
        }
        long durationMillis = Math.max(1, System.currentTimeMillis() - start);
        double envStepsPerSecond = (environments * (double) steps) / (durationMillis / 1000.0);
        System.out.printf("Java rollout benchmark: envs=%d steps=%d workers=%s parties=%s duration=%dms envSteps/s=%.1f%n",
                environments, environments * steps, workers > 0 ? Integer.toString(workers) : "auto",
                selection, durationMillis, envStepsPerSecond);
        System.out.println("Java rollout metrics: " + environment.metricsSnapshot().toSummaryString());
        System.out.println("Battle environment metrics: " + BattleEnvironment.timingSnapshot().toSummaryString());
        environment.close();
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

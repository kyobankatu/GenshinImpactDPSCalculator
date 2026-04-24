package sample;

import java.io.OutputStream;
import java.io.PrintStream;
import java.util.List;

import mechanics.rl.FlinsParty2RLSimulatorFactory;
import mechanics.rl.FlinsParty2Rotation;
import mechanics.rl.RLEnvironment;
import mechanics.rl.RLTrainingConfig;
import mechanics.rl.RotationPhase;
import mechanics.rl.policy.PolicyStore;
import mechanics.rl.policy.QLearningPolicy;
import mechanics.rl.training.JavaRLTrainer;
import mechanics.rl.training.TrainingStats;

/**
 * Java-native RL training entry point.
 */
public class TrainRLJava {
    public static void main(String[] args) {
        int episodes = args.length > 0 ? Integer.parseInt(args[0]) : 200;
        long seed = args.length > 1 ? Long.parseLong(args[1]) : 1234L;

        PrintStream originalOut = System.out;
        PrintStream mutedOut = new PrintStream(new OutputStream() {
            @Override
            public void write(int b) {
                // Discard simulator chatter during training.
            }
        });

        RLTrainingConfig config = new RLTrainingConfig();
        List<RotationPhase> rotation = FlinsParty2Rotation.build();
        RLEnvironment environment = new RLEnvironment(FlinsParty2RLSimulatorFactory.supplier(), rotation, config);
        QLearningPolicy policy = new QLearningPolicy(seed);
        JavaRLTrainer trainer = new JavaRLTrainer(environment, policy);

        originalOut.printf("Starting Java RL training: episodes=%d seed=%d%n", episodes, seed);
        long start = System.currentTimeMillis();
        TrainingStats last = null;
        System.setOut(mutedOut);
        try {
            for (int episode = 1; episode <= episodes; episode++) {
                last = trainer.trainEpisode(episode);
                JavaRLTrainer.appendCsv("output/java_rl_training_log.csv", last);
                if (episode % 25 == 0) {
                    System.setOut(originalOut);
                    originalOut.printf("Episode %d: reward=%.2f damage=%,.0f steps=%d epsilon=%.3f%n",
                            episode, last.totalReward, last.totalDamage, last.steps, policy.getEpsilon());
                    System.setOut(mutedOut);
                }
            }
        } finally {
            System.setOut(originalOut);
        }

        PolicyStore.saveQLearningPolicy(policy, "output/java_rl_policy.csv");
        long duration = System.currentTimeMillis() - start;
        if (last != null) {
            originalOut.printf("Training complete: best/current policy states=%d lastDamage=%,.0f duration=%dms%n",
                    policy.getQTable().size(), last.totalDamage, duration);
        }
        originalOut.println("Saved output/java_rl_policy.csv and output/java_rl_training_log.csv");
    }
}

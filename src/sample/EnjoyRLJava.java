package sample;

import java.io.File;
import java.util.List;

import mechanics.rl.FlinsParty2RLSimulatorFactory;
import mechanics.rl.FlinsParty2Rotation;
import mechanics.rl.RLEnvironment;
import mechanics.rl.RLTrainingConfig;
import mechanics.rl.RotationPhase;
import mechanics.rl.policy.PolicyStore;
import mechanics.rl.policy.QLearningPolicy;
import mechanics.rl.policy.TeacherPolicy;
import mechanics.rl.training.JavaRLTrainer;
import mechanics.rl.training.TrainingStats;

/**
 * Java-native RL evaluation entry point.
 */
public class EnjoyRLJava {
    public static void main(String[] args) {
        RLTrainingConfig config = new RLTrainingConfig();
        List<RotationPhase> rotation = FlinsParty2Rotation.build();
        RLEnvironment environment = new RLEnvironment(FlinsParty2RLSimulatorFactory.supplier(), rotation, config);
        JavaRLTrainer trainer = new JavaRLTrainer(environment, loadPolicy());

        TrainingStats stats = trainer.evaluateEpisode(true);
        System.out.printf("Java RL evaluation complete: reward=%.2f damage=%,.0f steps=%d duration=%dms%n",
                stats.totalReward, stats.totalDamage, stats.steps, stats.durationMillis);
        System.out.println("Generated output/rl_report.html");
    }

    private static mechanics.rl.policy.RLPolicy loadPolicy() {
        File policyFile = new File("output/java_rl_policy.csv");
        if (policyFile.exists()) {
            QLearningPolicy policy = PolicyStore.loadQLearningPolicy(policyFile.getPath(), 1234L);
            System.out.println("Loaded output/java_rl_policy.csv");
            return policy;
        }
        System.out.println("No Java policy found; using teacher policy.");
        return new TeacherPolicy();
    }
}

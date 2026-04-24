package mechanics.rl.training;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;

import mechanics.rl.RLEnvironment;
import mechanics.rl.RLStepResult;
import mechanics.rl.policy.RLPolicy;

/**
 * Runs Java-native RL episodes in-process.
 */
public class JavaRLTrainer {
    private final RLEnvironment environment;
    private final RLPolicy policy;

    public JavaRLTrainer(RLEnvironment environment, RLPolicy policy) {
        this.environment = environment;
        this.policy = policy;
    }

    public TrainingStats trainEpisode(int episode) {
        long start = System.currentTimeMillis();
        double[] state = environment.reset(false);
        double totalReward = 0.0;
        RLStepResult result = null;

        policy.onEpisodeStart();
        while (true) {
            int action = policy.chooseAction(state, true);
            result = environment.step(action);
            policy.observe(state, result.executedActionId, result.reward, result.state, result.done);
            totalReward += result.reward;
            state = result.state;
            if (result.done) {
                break;
            }
        }

        double totalDamage = environment.getCurrentSim().getTotalDamage();
        policy.onEpisodeEnd(totalReward, totalDamage);
        return new TrainingStats(episode, result.stepCount, totalReward, totalDamage,
                System.currentTimeMillis() - start);
    }

    public TrainingStats evaluateEpisode(boolean generateReport) {
        long start = System.currentTimeMillis();
        double[] state = environment.reset(generateReport);
        double totalReward = 0.0;
        RLStepResult result = null;

        policy.onEpisodeStart();
        while (true) {
            int action = policy.chooseAction(state, false);
            result = environment.step(action);
            totalReward += result.reward;
            state = result.state;
            if (result.done) {
                break;
            }
        }

        double totalDamage = environment.getCurrentSim().getTotalDamage();
        policy.onEpisodeEnd(totalReward, totalDamage);
        return new TrainingStats(1, result.stepCount, totalReward, totalDamage,
                System.currentTimeMillis() - start);
    }

    public static void appendCsv(String path, TrainingStats stats) {
        File file = new File(path);
        File parent = file.getParentFile();
        if (parent != null) {
            parent.mkdirs();
        }
        boolean writeHeader = !file.exists();
        try (PrintWriter out = new PrintWriter(new FileWriter(file, true))) {
            if (writeHeader) {
                out.println("episode,steps,total_reward,total_damage,duration_ms");
            }
            out.printf("%d,%d,%.6f,%.0f,%d%n",
                    stats.episode, stats.steps, stats.totalReward, stats.totalDamage, stats.durationMillis);
        } catch (IOException e) {
            throw new RuntimeException("Failed to append training stats: " + path, e);
        }
    }
}

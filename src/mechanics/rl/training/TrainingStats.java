package mechanics.rl.training;

/**
 * One Java-native RL episode summary.
 */
public class TrainingStats {
    public final int episode;
    public final int steps;
    public final double totalReward;
    public final double totalDamage;
    public final long durationMillis;

    public TrainingStats(int episode, int steps, double totalReward, double totalDamage, long durationMillis) {
        this.episode = episode;
        this.steps = steps;
        this.totalReward = totalReward;
        this.totalDamage = totalDamage;
        this.durationMillis = durationMillis;
    }
}

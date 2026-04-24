package mechanics.rl.bridge;

/**
 * Batched environment step response returned by the rollout service.
 */
public class RunnerStepResult {
    public final double[][] observations;
    public final double[][] actionMasks;
    public final double[] rewards;
    public final boolean[] dones;
    public final boolean[] validActions;
    public final double[] damageDeltas;
    public final double[] totalDamages;
    public final double[] episodeRewards;
    public final double[] episodeDamages;
    public final int[] episodeSteps;
    public final int[] liveSteps;

    public RunnerStepResult(
            double[][] observations,
            double[][] actionMasks,
            double[] rewards,
            boolean[] dones,
            boolean[] validActions,
            double[] damageDeltas,
            double[] totalDamages,
            double[] episodeRewards,
            double[] episodeDamages,
            int[] episodeSteps,
            int[] liveSteps) {
        this.observations = observations;
        this.actionMasks = actionMasks;
        this.rewards = rewards;
        this.dones = dones;
        this.validActions = validActions;
        this.damageDeltas = damageDeltas;
        this.totalDamages = totalDamages;
        this.episodeRewards = episodeRewards;
        this.episodeDamages = episodeDamages;
        this.episodeSteps = episodeSteps;
        this.liveSteps = liveSteps;
    }
}

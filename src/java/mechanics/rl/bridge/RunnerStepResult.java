package mechanics.rl.bridge;

/**
 * Batched environment step response returned by the rollout service.
 */
public class RunnerStepResult {
    public final double[][] observations;
    public final double[][] privilegedObservations;
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
    public final int[] partyIds;
    public final int[] episodePartyIds;
    /**
     * Vine snapshot IDs saved before SKILL/BURST/SWAP actions, one per env.
     * Value is -1 when no vine snapshot was saved for that env in this step.
     */
    public final int[] vineSnapshotIds;

    public RunnerStepResult(
            double[][] observations,
            double[][] privilegedObservations,
            double[][] actionMasks,
            double[] rewards,
            boolean[] dones,
            boolean[] validActions,
            double[] damageDeltas,
            double[] totalDamages,
            double[] episodeRewards,
            double[] episodeDamages,
            int[] episodeSteps,
            int[] liveSteps,
            int[] partyIds,
            int[] episodePartyIds,
            int[] vineSnapshotIds) {
        this.observations = observations;
        this.privilegedObservations = privilegedObservations;
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
        this.partyIds = partyIds;
        this.episodePartyIds = episodePartyIds;
        this.vineSnapshotIds = vineSnapshotIds;
    }
}

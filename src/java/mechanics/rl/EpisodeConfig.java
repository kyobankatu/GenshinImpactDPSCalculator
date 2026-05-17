package mechanics.rl;

import model.type.CharacterId;

/**
 * Runtime configuration for one RL episode.
 */
public class EpisodeConfig {
    /** Default party composition (Flins / Ineffa / Columbina / Sucrose). */
    public static final CharacterId[] DEFAULT_PARTY = {
            CharacterId.FLINS,
            CharacterId.INEFFA,
            CharacterId.COLUMBINA,
            CharacterId.SUCROSE
    };

    /** Ordered party slots (length 4). */
    public final CharacterId[] partyOrder;
    /** Maximum episode duration in simulator seconds. */
    public final double maxEpisodeTime;
    /** Simulator time advanced when an illegal action is selected. */
    public final double failedActionTimeCost;
    /** Minimum interval (seconds) between consecutive swaps. */
    public final double swapCooldown;
    /** Reward scale applied to per-step damage. */
    public final double damageRewardScale;
    /** Penalty applied when the agent selects an illegal action. */
    public final double invalidActionPenalty;
    /** Penalty applied when swapping back to a recently active character. */
    public final double repeatedSwapPenalty;
    /** Penalty per second of simulator idle time. */
    public final double idleTimePenaltyPerSecond;
    /** Reward scale applied to cumulative damage at terminal step. */
    public final double terminalDamageScale;
    /** Whether all bursts start with full energy on reset. */
    public final boolean fillEnergyOnReset;
    /** Whether to add role-alignment shaping bonus. */
    public final boolean enableRoleAlignmentBonus;
    /** Weight of the role-alignment shaping bonus. */
    public final double roleAlignmentBonusWeight;

    /** Constructs the default episode configuration with the default party. */
    public EpisodeConfig() {
        this(DEFAULT_PARTY, 20.0, 0.1, 1.0, 1000.0, 0.35, 0.10, 0.03, 25000.0, true, false, 0.0);
    }

    /**
     * Constructs an episode configuration with explicit values.
     *
     * @param partyOrder ordered party slots
     * @param maxEpisodeTime maximum episode duration (seconds)
     * @param failedActionTimeCost time advanced on invalid action
     * @param swapCooldown minimum swap interval (seconds)
     * @param damageRewardScale per-step damage reward scale
     * @param invalidActionPenalty penalty for invalid actions
     * @param repeatedSwapPenalty penalty for repeated swaps
     * @param idleTimePenaltyPerSecond penalty per idle second
     * @param terminalDamageScale terminal damage reward scale
     * @param fillEnergyOnReset whether to fill energy on reset
     * @param enableRoleAlignmentBonus whether to apply role alignment shaping
     * @param roleAlignmentBonusWeight weight of role alignment shaping
     */
    public EpisodeConfig(
            CharacterId[] partyOrder,
            double maxEpisodeTime,
            double failedActionTimeCost,
            double swapCooldown,
            double damageRewardScale,
            double invalidActionPenalty,
            double repeatedSwapPenalty,
            double idleTimePenaltyPerSecond,
            double terminalDamageScale,
            boolean fillEnergyOnReset,
            boolean enableRoleAlignmentBonus,
            double roleAlignmentBonusWeight) {
        this.partyOrder = partyOrder.clone();
        this.maxEpisodeTime = maxEpisodeTime;
        this.failedActionTimeCost = failedActionTimeCost;
        this.swapCooldown = swapCooldown;
        this.damageRewardScale = damageRewardScale;
        this.invalidActionPenalty = invalidActionPenalty;
        this.repeatedSwapPenalty = repeatedSwapPenalty;
        this.idleTimePenaltyPerSecond = idleTimePenaltyPerSecond;
        this.terminalDamageScale = terminalDamageScale;
        this.fillEnergyOnReset = fillEnergyOnReset;
        this.enableRoleAlignmentBonus = enableRoleAlignmentBonus;
        this.roleAlignmentBonusWeight = roleAlignmentBonusWeight;
    }

    /**
     * Returns a copy with the party order replaced.
     *
     * @param nextPartyOrder replacement party order
     * @return new EpisodeConfig identical to this one but with the supplied party order
     */
    public EpisodeConfig withPartyOrder(CharacterId[] nextPartyOrder) {
        return new EpisodeConfig(
                nextPartyOrder,
                maxEpisodeTime,
                failedActionTimeCost,
                swapCooldown,
                damageRewardScale,
                invalidActionPenalty,
                repeatedSwapPenalty,
                idleTimePenaltyPerSecond,
                terminalDamageScale,
                fillEnergyOnReset,
                enableRoleAlignmentBonus,
                roleAlignmentBonusWeight);
    }

    /**
     * Returns a copy with the maximum episode time replaced.
     *
     * @param nextMaxEpisodeTime replacement maximum episode time in seconds
     * @return new EpisodeConfig with the updated value
     */
    public EpisodeConfig withMaxEpisodeTime(double nextMaxEpisodeTime) {
        return new EpisodeConfig(
                partyOrder,
                nextMaxEpisodeTime,
                failedActionTimeCost,
                swapCooldown,
                damageRewardScale,
                invalidActionPenalty,
                repeatedSwapPenalty,
                idleTimePenaltyPerSecond,
                terminalDamageScale,
                fillEnergyOnReset,
                enableRoleAlignmentBonus,
                roleAlignmentBonusWeight);
    }

    /**
     * Returns a copy with role-alignment shaping reconfigured.
     *
     * @param nextEnableRoleAlignmentBonus enable flag
     * @param nextRoleAlignmentBonusWeight weight value
     * @return new EpisodeConfig with the supplied role-alignment configuration
     */
    public EpisodeConfig withRoleAlignmentBonus(boolean nextEnableRoleAlignmentBonus, double nextRoleAlignmentBonusWeight) {
        return new EpisodeConfig(
                partyOrder,
                maxEpisodeTime,
                failedActionTimeCost,
                swapCooldown,
                damageRewardScale,
                invalidActionPenalty,
                repeatedSwapPenalty,
                idleTimePenaltyPerSecond,
                terminalDamageScale,
                fillEnergyOnReset,
                nextEnableRoleAlignmentBonus,
                nextRoleAlignmentBonusWeight);
    }
}

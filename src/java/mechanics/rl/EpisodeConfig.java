package mechanics.rl;

import model.type.CharacterId;

/**
 * Runtime configuration for one RL episode.
 */
public class EpisodeConfig {
    public static final CharacterId[] DEFAULT_PARTY = {
            CharacterId.FLINS,
            CharacterId.INEFFA,
            CharacterId.COLUMBINA,
            CharacterId.SUCROSE
    };

    public final CharacterId[] partyOrder;
    public final double maxEpisodeTime;
    public final double failedActionTimeCost;
    public final double swapCooldown;
    public final double damageRewardScale;
    public final double invalidActionPenalty;
    public final double repeatedSwapPenalty;
    public final double idleTimePenaltyPerSecond;
    public final double terminalDamageScale;
    public final boolean fillEnergyOnReset;

    public EpisodeConfig() {
        this(DEFAULT_PARTY, 20.0, 0.1, 1.0, 1000.0, 0.35, 0.10, 0.03, 25000.0, true);
    }

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
            boolean fillEnergyOnReset) {
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
    }

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
                fillEnergyOnReset);
    }
}

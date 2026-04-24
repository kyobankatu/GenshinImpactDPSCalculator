package mechanics.rl;

import model.type.CharacterId;

/**
 * Configuration for Java-native RL episodes.
 */
public class RLTrainingConfig {
    public CharacterId[] partyOrder = {
            CharacterId.FLINS,
            CharacterId.INEFFA,
            CharacterId.COLUMBINA,
            CharacterId.SUCROSE
    };
    public double maxEpisodeTime = 20.0;
    public double failedActionTimeCost = 0.1;
    public double swapCooldown = 1.0;
    public double damageRewardScale = 10000.0;
    public double invalidActionPenalty = -50.0;
    public double missingCharacterPenalty = -10.0;
    public double teacherMatchBonus = 10.0;
    public double teacherCorrectionPenalty = -5.0;
    public boolean teacherForcing = true;
    public boolean fillEnergyOnReset = true;
}

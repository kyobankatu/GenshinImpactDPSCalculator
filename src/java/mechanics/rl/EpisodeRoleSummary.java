package mechanics.rl;

/**
 * Episode-level summary comparing realized role usage against expected static role priors.
 */
public class EpisodeRoleSummary {
    public static final int FEATURES_PER_SLOT = 5;
    public static final int ON_FIELD_SHARE = 0;
    public static final int DAMAGE_SHARE = 1;
    public static final int OFF_FIELD_DAMAGE_SHARE = 2;
    public static final int ENTRY_SHARE = 3;
    public static final int STAY_SHARE = 4;
    public static final int VECTOR_SIZE = ObservationEncoder.NUM_CHARS * FEATURES_PER_SLOT;

    public final double roleAlignmentScore;
    public final double carryAlignmentScore;
    public final double offFieldAlignmentScore;
    public final double entryAlignmentScore;
    public final double stayAlignmentScore;
    public final double[] expectedRoleVector;
    public final double[] realizedRoleVector;

    public EpisodeRoleSummary(
            double roleAlignmentScore,
            double carryAlignmentScore,
            double offFieldAlignmentScore,
            double entryAlignmentScore,
            double stayAlignmentScore,
            double[] expectedRoleVector,
            double[] realizedRoleVector) {
        this.roleAlignmentScore = roleAlignmentScore;
        this.carryAlignmentScore = carryAlignmentScore;
        this.offFieldAlignmentScore = offFieldAlignmentScore;
        this.entryAlignmentScore = entryAlignmentScore;
        this.stayAlignmentScore = stayAlignmentScore;
        this.expectedRoleVector = expectedRoleVector;
        this.realizedRoleVector = realizedRoleVector;
    }
}

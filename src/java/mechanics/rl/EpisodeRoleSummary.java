package mechanics.rl;

/**
 * Episode-level summary comparing realized role usage against expected static role priors.
 */
public class EpisodeRoleSummary {
    /** Number of features tracked per party slot. */
    public static final int FEATURES_PER_SLOT = 5;
    /** Feature index for time-on-field share. */
    public static final int ON_FIELD_SHARE = 0;
    /** Feature index for total-damage share. */
    public static final int DAMAGE_SHARE = 1;
    /** Feature index for off-field damage share. */
    public static final int OFF_FIELD_DAMAGE_SHARE = 2;
    /** Feature index for on-entry damage share. */
    public static final int ENTRY_SHARE = 3;
    /** Feature index for stay-time share after entry. */
    public static final int STAY_SHARE = 4;
    /** Flattened vector size for the per-slot role summary. */
    public static final int VECTOR_SIZE = ObservationEncoder.NUM_CHARS * FEATURES_PER_SLOT;

    /** Overall role alignment score. */
    public final double roleAlignmentScore;
    /** Alignment score for the carry/DPS role. */
    public final double carryAlignmentScore;
    /** Alignment score for off-field roles. */
    public final double offFieldAlignmentScore;
    /** Alignment score for entry timing. */
    public final double entryAlignmentScore;
    /** Alignment score for on-field stay duration. */
    public final double stayAlignmentScore;
    /** Expected (prior) role vector, length {@code VECTOR_SIZE}. */
    public final double[] expectedRoleVector;
    /** Realized role vector observed during the episode. */
    public final double[] realizedRoleVector;

    /**
     * Constructs an episode role summary.
     *
     * @param roleAlignmentScore overall alignment score
     * @param carryAlignmentScore carry-role alignment
     * @param offFieldAlignmentScore off-field-role alignment
     * @param entryAlignmentScore entry-timing alignment
     * @param stayAlignmentScore on-field-stay alignment
     * @param expectedRoleVector expected role feature vector
     * @param realizedRoleVector realized role feature vector
     */
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

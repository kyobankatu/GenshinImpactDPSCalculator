package mechanics.rl;

/**
 * Shared capability-profile layout used by profiling, observation encoding, and
 * privileged-state encoding.
 */
public final class CapabilityProfile {
    /** Index for off-field DPS ratio score. */
    public static final int OFF_FIELD_DPS_RATIO = 0;
    /** Index for team buff contribution score. */
    public static final int TEAM_BUFF_SCORE = 1;
    /** Index for self enhancement (personal buff) score. */
    public static final int SELF_ENHANCEMENT_SCORE = 2;
    /** Index for energy generation score (particles per second). */
    public static final int ENERGY_GENERATION_SCORE = 3;
    /** Index for on-entry value score (burst/skill snapshot impact). */
    public static final int ENTRY_VALUE_SCORE = 4;
    /** Index for sustained value over the next 3 actions. */
    public static final int SUSTAIN_VALUE_3_ACTIONS = 5;
    /** Index for sustained value over the next 6 actions. */
    public static final int SUSTAIN_VALUE_6_ACTIONS = 6;
    /** Index for exit (swap-out) cost score. */
    public static final int EXIT_COST_SCORE = 7;
    /** Index for re-entry cost score. */
    public static final int REENTRY_COST_SCORE = 8;
    /** Index for on-field DPS score. */
    public static final int ON_FIELD_DPS_SCORE = 9;
    /** Index for burst-window value score. */
    public static final int BURST_WINDOW_SCORE = 10;
    /** Total number of capability scores per character. */
    public static final int SIZE = 11;

    /** Private constructor to prevent instantiation of this utility holder. */
    private CapabilityProfile() {
    }

    /**
     * Returns a freshly allocated zero-filled capability vector of length {@code SIZE}.
     *
     * @return new double array of length {@code SIZE} filled with zero
     */
    public static double[] zeros() {
        return new double[SIZE];
    }
}

package mechanics.rl;

/**
 * Shared capability-profile layout used by profiling, observation encoding, and
 * privileged-state encoding.
 */
public final class CapabilityProfile {
    public static final int OFF_FIELD_DPS_RATIO = 0;
    public static final int TEAM_BUFF_SCORE = 1;
    public static final int SELF_ENHANCEMENT_SCORE = 2;
    public static final int ENERGY_GENERATION_SCORE = 3;
    public static final int ENTRY_VALUE_SCORE = 4;
    public static final int SUSTAIN_VALUE_3_ACTIONS = 5;
    public static final int SUSTAIN_VALUE_6_ACTIONS = 6;
    public static final int EXIT_COST_SCORE = 7;
    public static final int REENTRY_COST_SCORE = 8;
    public static final int ON_FIELD_DPS_SCORE = 9;
    public static final int BURST_WINDOW_SCORE = 10;
    public static final int SIZE = 11;

    private CapabilityProfile() {
    }

    public static double[] zeros() {
        return new double[SIZE];
    }
}

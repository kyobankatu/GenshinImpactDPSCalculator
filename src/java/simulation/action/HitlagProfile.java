package simulation.action;

/**
 * Immutable per-hit hitlag metadata derived from gcsim attack information.
 *
 * <p>Time is stored in seconds at the content boundary. The simulator converts
 * it to 60 FPS frames only inside the hitlag runtime so every character shares
 * one rounding policy.
 */
public final class HitlagProfile {
    private static final HitlagProfile NONE = new HitlagProfile(
            0.0, 1.0, false, false, false);

    private final double haltTimeSeconds;
    private final double factor;
    private final boolean defenseHalt;
    private final boolean deployable;
    private final boolean headshotOnly;

    /**
     * Creates validated metadata for one hit.
     *
     * @param haltTimeSeconds base hit-halt time in seconds
     * @param factor hitlag clock factor in the inclusive range {@code [0, 1]}
     * @param defenseHalt whether unbroken target defense adds 0.06 seconds
     * @param deployable whether the hit must not halt its owner
     * @param headshotOnly whether hitlag requires a weak-point hit
     */
    public HitlagProfile(
            double haltTimeSeconds,
            double factor,
            boolean defenseHalt,
            boolean deployable,
            boolean headshotOnly) {
        if (!Double.isFinite(haltTimeSeconds) || haltTimeSeconds < 0.0) {
            throw new IllegalArgumentException(
                    "Hitlag halt time must be finite and non-negative");
        }
        if (!Double.isFinite(factor) || factor < 0.0 || factor > 1.0) {
            throw new IllegalArgumentException(
                    "Hitlag factor must be finite and between zero and one");
        }
        this.haltTimeSeconds = haltTimeSeconds;
        this.factor = factor;
        this.defenseHalt = defenseHalt;
        this.deployable = deployable;
        this.headshotOnly = headshotOnly;
    }

    /** Returns a profile that produces no hitlag. */
    public static HitlagProfile none() {
        return NONE;
    }

    /** Returns the base halt time in seconds. */
    public double getHaltTimeSeconds() {
        return haltTimeSeconds;
    }

    /** Returns the hitlag clock factor. */
    public double getFactor() {
        return factor;
    }

    /** Returns whether Defense Halt is available for this hit. */
    public boolean canDefenseHalt() {
        return defenseHalt;
    }

    /** Returns whether only the target, not the owner, receives hitlag. */
    public boolean isDeployable() {
        return deployable;
    }

    /** Returns whether hitlag requires a weak-point hit. */
    public boolean isHeadshotOnly() {
        return headshotOnly;
    }
}

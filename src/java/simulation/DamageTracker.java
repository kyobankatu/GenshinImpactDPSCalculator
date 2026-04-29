package simulation;

/**
 * Abstraction for recording cumulative simulation damage and producing DPS summaries.
 */
public interface DamageTracker {
    /**
     * Records a damage instance under the given source name.
     *
     * @param sourceName the character or synthetic source to attribute the damage to
     * @param damage     the damage amount to accumulate
     */
    void recordDamage(String sourceName, double damage);

    /**
     * Returns the total accumulated damage across all sources.
     *
     * @return total damage
     */
    double getTotalDamage();

    /**
     * Returns the accumulated damage attributed to a specific source name.
     *
     * @param sourceName the source name as passed to {@link #recordDamage}
     * @return damage total for that source, or {@code 0.0} if not found
     */
    default double getDamageBySource(String sourceName) {
        return 0.0;
    }

    /**
     * Computes DPS using the currently accumulated total and the given rotation time.
     *
     * @param rotationTime simulated rotation duration in seconds
     * @return DPS value
     */
    double getDps(double rotationTime);

    /**
     * Prints a formatted damage summary for the given rotation duration.
     *
     * @param rotationTime simulated rotation duration in seconds
     */
    void printReport(double rotationTime);
}

package simulation.runtime;

import java.util.HashMap;
import java.util.Map;
import simulation.DamageTracker;

/**
 * Tracks cumulative damage dealt during a simulation run and formats summary output.
 */
public class DamageReport implements DamageTracker {
    private final Map<String, Double> damageBySource = new HashMap<>();
    private double totalDamage = 0.0;

    /**
     * Records a damage instance under the given source name and updates the total.
     *
     * @param sourceName the character or synthetic source to attribute the damage to
     * @param damage     the damage amount to accumulate
     */
    @Override
    public void recordDamage(String sourceName, double damage) {
        totalDamage += damage;
        damageBySource.put(sourceName, damageBySource.getOrDefault(sourceName, 0.0) + damage);
    }

    /**
     * Returns the cumulative total damage recorded so far.
     *
     * @return total damage across all sources
     */
    @Override
    public double getTotalDamage() {
        return totalDamage;
    }

    /**
     * Computes DPS from the currently accumulated total and the provided rotation time.
     *
     * @param rotationTime total simulated rotation duration in seconds
     * @return DPS value, or {@code 0.0} if {@code rotationTime <= 0}
     */
    @Override
    public double getDps(double rotationTime) {
        return rotationTime > 0 ? totalDamage / rotationTime : 0.0;
    }

    /**
     * Returns the accumulated damage attributed to a specific source name.
     *
     * @param sourceName the source name used in {@link #recordDamage}
     * @return damage total for that source, or {@code 0.0} if not found
     */
    @Override
    public double getDamageBySource(String sourceName) {
        return damageBySource.getOrDefault(sourceName, 0.0);
    }

    /**
     * Prints a formatted console summary showing per-source damage share and overall DPS.
     *
     * @param rotationTime total simulated rotation duration in seconds
     */
    @Override
    public void printReport(double rotationTime) {
        System.out.println("----------------------------------------------");
        System.out.println("DPS Breakdown:");
        for (String name : damageBySource.keySet()) {
            double damage = damageBySource.get(name);
            System.out.println(String.format(
                    "%-10s : %,8.0f (%.1f%%) - DPS: %,.0f",
                    name,
                    damage,
                    damage / totalDamage * 100,
                    damage / rotationTime));
        }
        System.out.println("----------------------------------------------");
        System.out.println("Total Rotation Damage: " + String.format("%,.0f", totalDamage));
        System.out.println(String.format("DPS (%.1fs): %,.0f", rotationTime, getDps(rotationTime)));
    }
}

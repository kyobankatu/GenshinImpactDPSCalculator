package model.entity.state;

import java.util.ArrayList;
import java.util.List;

/**
 * Holds runtime energy totals and burst-window accounting for a character.
 *
 * <p>Energy gains are split into two channels:
 * <ul>
 *   <li><b>Particle energy</b> – scaled by the character's Energy Recharge (ER)</li>
 *   <li><b>Flat energy</b> – fixed amounts (e.g. from Bennett's Burst restoration)
 *       that bypass ER scaling</li>
 * </ul>
 * Per-burst-window subtotals are recorded so the {@code EnergyManager} can
 * evaluate ER tuning quality after each burst usage.
 */
public class EnergyState {
    private double currentEnergy = 0.0;
    private double totalEnergyGained = 0.0;
    private double totalFlatEnergyGained = 0.0;
    private double totalParticleEnergyGained = 0.0;
    private double totalScaledParticleEnergyGained = 0.0;
    private double particleEnergyThisWindow = 0.0;
    private double flatEnergyThisWindow = 0.0;
    private double missedBurstCost = 0.0;
    private final List<double[]> burstEnergyWindows = new ArrayList<>();
    private final List<double[]> burstEnergyMarkers = new ArrayList<>();

    /**
     * Adds a generic energy amount, clamped to the maximum.
     *
     * @param amount   energy to add (post-ER if applicable)
     * @param maxEnergy energy cap (burst cost)
     */
    public void receiveEnergy(double amount, double maxEnergy) {
        totalEnergyGained += amount;
        currentEnergy = Math.min(maxEnergy, currentEnergy + amount);
    }

    /**
     * Records particle-based energy generation, applying ER scaling.
     *
     * @param baseAmount unscaled particle value
     * @param er         character's Energy Recharge multiplier (e.g. 1.5)
     * @param maxEnergy  energy cap (burst cost)
     */
    public void receiveParticleEnergy(double baseAmount, double er, double maxEnergy) {
        totalParticleEnergyGained += baseAmount;
        totalScaledParticleEnergyGained += baseAmount * er;
        particleEnergyThisWindow += baseAmount;
        receiveEnergy(baseAmount * er, maxEnergy);
    }

    /**
     * Records flat energy generation (not affected by ER).
     *
     * @param amount    flat energy amount
     * @param maxEnergy energy cap (burst cost)
     */
    public void receiveFlatEnergy(double amount, double maxEnergy) {
        totalFlatEnergyGained += amount;
        flatEnergyThisWindow += amount;
        receiveEnergy(amount, maxEnergy);
    }

    /**
     * Resets all energy accumulators and clears window history.
     *
     * @param initialEnergy starting energy after reset
     */
    public void reset(double initialEnergy) {
        totalEnergyGained = 0.0;
        currentEnergy = initialEnergy;
        totalFlatEnergyGained = 0.0;
        totalParticleEnergyGained = 0.0;
        totalScaledParticleEnergyGained = 0.0;
        particleEnergyThisWindow = 0.0;
        flatEnergyThisWindow = 0.0;
        missedBurstCost = 0.0;
        burstEnergyWindows.clear();
        burstEnergyMarkers.clear();
    }

    /**
     * Records that the burst has been used: snapshots the per-window subtotals,
     * resets them, and spends the supplied burst cost.
     *
     * @param burstCost   burst energy cost (recorded in the window entry)
     * @param maxEnergy   maximum energy bar value
     * @param currentTime simulation time of the burst use
     */
    public void markBurstUsed(double burstCost, double maxEnergy, double currentTime) {
        burstEnergyWindows.add(new double[] { particleEnergyThisWindow, flatEnergyThisWindow, burstCost });
        double preBurstPercent = maxEnergy > 0.0 ? Math.min(100.0, currentEnergy / maxEnergy * 100.0) : 0.0;
        burstEnergyMarkers.add(new double[] { currentTime, preBurstPercent });
        particleEnergyThisWindow = 0.0;
        flatEnergyThisWindow = 0.0;
        currentEnergy = Math.max(0.0, Math.min(maxEnergy, currentEnergy - burstCost));
    }

    /**
     * Records a scripted burst request that could not be executed because the
     * current energy was below the requested cost.
     *
     * @param burstCost burst cost that the rotation attempted to spend
     */
    public void recordMissedBurst(double burstCost) {
        missedBurstCost += burstCost;
    }

    /**
     * @return current energy on the character
     */
    public double getCurrentEnergy() {
        return currentEnergy;
    }

    /**
     * Directly sets the current energy level without updating totals.
     * Used only for snapshot restore.
     *
     * @param energy energy value to set
     */
    public void setCurrentEnergy(double energy) {
        this.currentEnergy = energy;
    }

    /**
     * @return cumulative energy added to the bar since last reset
     */
    public double getTotalEnergyGained() {
        return totalEnergyGained;
    }

    /**
     * @return cumulative flat energy received (pre-cap)
     */
    public double getTotalFlatEnergy() {
        return totalFlatEnergyGained;
    }

    /**
     * @return cumulative particle energy received before ER scaling
     */
    public double getTotalParticleEnergy() {
        return totalParticleEnergyGained;
    }

    /**
     * Returns total particle energy received after ER scaling (i.e. what was actually
     * added to the energy bar from particles, excluding flat energy and burst reset).
     *
     * @return ER-scaled particle energy total
     */
    public double getTotalScaledParticleEnergy() {
        return totalScaledParticleEnergyGained;
    }

    /**
     * Returns per-burst window subtotals.
     * <p>Each entry is {@code [particleEnergy, flatEnergy, burstCost]}.
     *
     * @return list of burst window subtotal arrays
     */
    public List<double[]> getBurstEnergyWindows() {
        return burstEnergyWindows;
    }

    /**
     * Returns report markers for successfully used bursts.
     *
     * @return list of {@code [time, preBurstEnergyPercent]} entries
     */
    public List<double[]> getBurstEnergyMarkers() {
        return burstEnergyMarkers;
    }

    /**
     * @return total burst cost requested by the rotation but skipped for energy
     */
    public double getMissedBurstCost() {
        return missedBurstCost;
    }
}

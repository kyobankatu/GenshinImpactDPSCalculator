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
 * Per-request Burst windows are recorded so the {@code EnergyAnalyzer} can
 * evaluate ER tuning quality at every scripted Burst boundary, including
 * requests that runtime energy gating skips.
 */
public class EnergyState {
    /** Immutable copy of runtime and analysis energy accounting. */
    public static final class State {
        private final double currentEnergy;
        private final double totalEnergyGained;
        private final double totalFlatEnergyGained;
        private final double totalParticleEnergyGained;
        private final double totalScaledParticleEnergyGained;
        private final double particleEnergyThisWindow;
        private final double flatEnergyThisWindow;
        private final double missedBurstCost;
        private final List<double[]> burstEnergyWindows;
        private final List<double[]> burstEnergyMarkers;

        private State(EnergyState source) {
            currentEnergy = source.currentEnergy;
            totalEnergyGained = source.totalEnergyGained;
            totalFlatEnergyGained = source.totalFlatEnergyGained;
            totalParticleEnergyGained = source.totalParticleEnergyGained;
            totalScaledParticleEnergyGained =
                    source.totalScaledParticleEnergyGained;
            particleEnergyThisWindow = source.particleEnergyThisWindow;
            flatEnergyThisWindow = source.flatEnergyThisWindow;
            missedBurstCost = source.missedBurstCost;
            burstEnergyWindows = copyEntries(source.burstEnergyWindows);
            burstEnergyMarkers = copyEntries(source.burstEnergyMarkers);
        }
    }

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

    /** Captures every runtime and analysis accumulator. */
    public State capture() {
        return new State(this);
    }

    /** Restores every runtime and analysis accumulator. */
    public void restore(State state) {
        if (state == null) {
            throw new IllegalArgumentException("Energy state must not be null");
        }
        currentEnergy = state.currentEnergy;
        totalEnergyGained = state.totalEnergyGained;
        totalFlatEnergyGained = state.totalFlatEnergyGained;
        totalParticleEnergyGained = state.totalParticleEnergyGained;
        totalScaledParticleEnergyGained =
                state.totalScaledParticleEnergyGained;
        particleEnergyThisWindow = state.particleEnergyThisWindow;
        flatEnergyThisWindow = state.flatEnergyThisWindow;
        missedBurstCost = state.missedBurstCost;
        burstEnergyWindows.clear();
        burstEnergyWindows.addAll(copyEntries(state.burstEnergyWindows));
        burstEnergyMarkers.clear();
        burstEnergyMarkers.addAll(copyEntries(state.burstEnergyMarkers));
    }

    private static List<double[]> copyEntries(List<double[]> entries) {
        List<double[]> result = new ArrayList<>();
        for (double[] entry : entries) {
            result.add(entry.clone());
        }
        return result;
    }

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
     * Spends runtime Energy without changing gain or Burst-window accounting.
     *
     * @param amount non-negative Energy amount to spend
     * @throws IllegalArgumentException when {@code amount} is negative or non-finite
     */
    public void spendEnergy(double amount) {
        if (!Double.isFinite(amount) || amount < 0.0) {
            throw new IllegalArgumentException("Energy spend must be finite and non-negative");
        }
        currentEnergy = Math.max(0.0, currentEnergy - amount);
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
        closeBurstEnergyWindow(burstCost);
        double preBurstPercent = maxEnergy > 0.0 ? Math.min(100.0, currentEnergy / maxEnergy * 100.0) : 0.0;
        burstEnergyMarkers.add(new double[] { currentTime, preBurstPercent });
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
        closeBurstEnergyWindow(burstCost);
    }

    /**
     * Closes one analysis window at a requested Burst boundary.
     *
     * <p>This method resets only accounting subtotals. A skipped Burst keeps
     * its runtime energy and cooldown state unchanged.
     *
     * @param burstCost cost requested at the boundary
     */
    private void closeBurstEnergyWindow(double burstCost) {
        burstEnergyWindows.add(new double[] { particleEnergyThisWindow, flatEnergyThisWindow, burstCost });
        particleEnergyThisWindow = 0.0;
        flatEnergyThisWindow = 0.0;
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
     * Returns per-request Burst window subtotals.
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

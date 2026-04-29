package model.entity.state;

import java.util.ArrayList;
import java.util.List;

/**
 * Holds runtime energy totals and burst-window accounting for a character.
 */
public class EnergyState {
    private double currentEnergy = 0.0;
    private double totalEnergyGained = 0.0;
    private double totalFlatEnergyGained = 0.0;
    private double totalParticleEnergyGained = 0.0;
    private double totalScaledParticleEnergyGained = 0.0;
    private double particleEnergyThisWindow = 0.0;
    private double flatEnergyThisWindow = 0.0;
    private final List<double[]> burstEnergyWindows = new ArrayList<>();

    public void receiveEnergy(double amount, double maxEnergy) {
        totalEnergyGained += amount;
        currentEnergy = Math.min(maxEnergy, currentEnergy + amount);
    }

    public void receiveParticleEnergy(double baseAmount, double er, double maxEnergy) {
        totalParticleEnergyGained += baseAmount;
        totalScaledParticleEnergyGained += baseAmount * er;
        particleEnergyThisWindow += baseAmount;
        receiveEnergy(baseAmount * er, maxEnergy);
    }

    public void receiveFlatEnergy(double amount, double maxEnergy) {
        totalFlatEnergyGained += amount;
        flatEnergyThisWindow += amount;
        receiveEnergy(amount, maxEnergy);
    }

    public void reset(double initialEnergy) {
        totalEnergyGained = 0.0;
        currentEnergy = initialEnergy;
        totalFlatEnergyGained = 0.0;
        totalParticleEnergyGained = 0.0;
        totalScaledParticleEnergyGained = 0.0;
        particleEnergyThisWindow = 0.0;
        flatEnergyThisWindow = 0.0;
        burstEnergyWindows.clear();
    }

    public void markBurstUsed(double burstCost) {
        burstEnergyWindows.add(new double[] { particleEnergyThisWindow, flatEnergyThisWindow, burstCost });
        particleEnergyThisWindow = 0.0;
        flatEnergyThisWindow = 0.0;
        currentEnergy = 0.0;
    }

    public double getCurrentEnergy() {
        return currentEnergy;
    }

    public double getTotalEnergyGained() {
        return totalEnergyGained;
    }

    public double getTotalFlatEnergy() {
        return totalFlatEnergyGained;
    }

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

    public List<double[]> getBurstEnergyWindows() {
        return burstEnergyWindows;
    }
}

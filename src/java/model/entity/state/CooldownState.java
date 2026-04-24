package model.entity.state;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Holds skill and burst cooldown state, including charge restoration.
 */
public class CooldownState {
    private double lastSkillTime = -999.0;
    private double lastBurstTime = -999.0;
    private double skillCD = 6.0;
    private double burstCD = 15.0;
    private int skillMaxCharges = 1;
    private final List<Double> chargeRestoreTimes = new ArrayList<>();

    public void setSkillCD(double skillCD) {
        this.skillCD = skillCD;
    }

    public void setBurstCD(double burstCD) {
        this.burstCD = burstCD;
    }

    public void setSkillMaxCharges(int skillMaxCharges) {
        this.skillMaxCharges = skillMaxCharges;
    }

    public boolean canSkill(double currentTime) {
        return getSkillCDRemaining(currentTime) <= 0.0;
    }

    public boolean canBurst(double currentTime, double currentEnergy, double energyCost) {
        return (currentTime - lastBurstTime) >= burstCD && currentEnergy >= energyCost;
    }

    public double getSkillCDRemaining(double currentTime) {
        if (skillMaxCharges > 1) {
            chargeRestoreTimes.removeIf(time -> time <= currentTime);
            int available = skillMaxCharges - chargeRestoreTimes.size();
            if (available > 0) {
                return 0.0;
            }
            return chargeRestoreTimes.get(0) - currentTime;
        }
        return Math.max(0.0, lastSkillTime + skillCD - currentTime);
    }

    public double getBurstCDRemaining(double currentTime) {
        return Math.max(0.0, lastBurstTime + burstCD - currentTime);
    }

    public void markSkillUsed(double currentTime) {
        if (skillMaxCharges > 1) {
            chargeRestoreTimes.removeIf(time -> time <= currentTime);
            chargeRestoreTimes.add(currentTime + skillCD);
            Collections.sort(chargeRestoreTimes);
        }
        lastSkillTime = currentTime;
    }

    public void markBurstUsed(double currentTime) {
        lastBurstTime = currentTime;
    }

    public void resetChargeState() {
        chargeRestoreTimes.clear();
    }

    public double getSkillCD() {
        return skillCD;
    }

    public double getBurstCD() {
        return burstCD;
    }

    public double getLastSkillTime() {
        return lastSkillTime;
    }

    public double getLastBurstTime() {
        return lastBurstTime;
    }
}

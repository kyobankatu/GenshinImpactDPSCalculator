package model.entity.state;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Holds skill and burst cooldown state, including charge restoration.
 *
 * <p>The state tracks the most recent skill/burst timestamps and, for skills
 * with multiple stored charges, an ordered list of pending charge-restore
 * times. All time values are simulation seconds.
 */
public class CooldownState {
    private double lastSkillTime = -999.0;
    private double lastBurstTime = -999.0;
    private double skillCooldownEndTime = -999.0;
    private double burstCooldownEndTime = -999.0;
    private double skillCD = 6.0;
    private double burstCD = 15.0;
    private int skillMaxCharges = 1;
    private double activeChargeCooldownDuration = 0.0;
    private final List<Double> chargeRestoreTimes = new ArrayList<>();

    /**
     * Sets the elemental skill cooldown length.
     *
     * @param skillCD cooldown in seconds
     */
    public void setSkillCD(double skillCD) {
        this.skillCD = skillCD;
    }

    /**
     * Sets the elemental burst cooldown length.
     *
     * @param burstCD cooldown in seconds
     */
    public void setBurstCD(double burstCD) {
        this.burstCD = burstCD;
    }

    /**
     * Sets how many simultaneous skill charges this character can store.
     *
     * @param skillMaxCharges maximum number of skill charges (1 for single-charge skills)
     */
    public void setSkillMaxCharges(int skillMaxCharges) {
        this.skillMaxCharges = skillMaxCharges;
    }

    /**
     * Tests whether the skill can be cast at the given time.
     *
     * @param currentTime current simulation time in seconds
     * @return {@code true} if at least one skill charge is available
     */
    public boolean canSkill(double currentTime) {
        return getSkillCDRemaining(currentTime) <= 0.0;
    }

    /**
     * Tests whether the burst can be cast at the given time and energy level.
     *
     * @param currentTime  current simulation time in seconds
     * @param currentEnergy current energy on the character
     * @param energyCost   burst energy cost
     * @return {@code true} if both cooldown has elapsed and energy is sufficient
     */
    public boolean canBurst(double currentTime, double currentEnergy, double energyCost) {
        return currentTime >= burstCooldownEndTime && currentEnergy >= energyCost;
    }

    /**
     * Returns the remaining skill cooldown.
     *
     * <p>For multi-charge skills, returns the time until the next charge is
     * restored (or 0 if a charge is already available).
     *
     * @param currentTime current simulation time in seconds
     * @return remaining cooldown in seconds (0 if ready)
     */
    public double getSkillCDRemaining(double currentTime) {
        if (skillMaxCharges > 1) {
            removeRestoredCharges(currentTime);
            int available = skillMaxCharges - chargeRestoreTimes.size();
            if (available > 0) {
                return 0.0;
            }
            return chargeRestoreTimes.get(0) - currentTime;
        }
        return Math.max(0.0, skillCooldownEndTime - currentTime);
    }

    /**
     * Returns the remaining burst cooldown.
     *
     * @param currentTime current simulation time in seconds
     * @return remaining cooldown in seconds (0 if ready)
     */
    public double getBurstCDRemaining(double currentTime) {
        return Math.max(0.0, burstCooldownEndTime - currentTime);
    }

    /**
     * Records that the skill has just been used at the given time, updating
     * either the last-skill timestamp or the charge restore queue.
     *
     * @param currentTime               current simulation time in seconds
     * @param effectiveCooldownDuration cooldown duration captured at use
     */
    public void markSkillUsed(double currentTime, double effectiveCooldownDuration) {
        double boundedDuration = Math.max(0.0, effectiveCooldownDuration);
        if (skillMaxCharges > 1) {
            removeRestoredCharges(currentTime);
            if (chargeRestoreTimes.isEmpty()) {
                activeChargeCooldownDuration = boundedDuration;
            }
            chargeRestoreTimes.add(currentTime + activeChargeCooldownDuration);
            Collections.sort(chargeRestoreTimes);
        } else {
            skillCooldownEndTime = currentTime + boundedDuration;
        }
        lastSkillTime = currentTime;
    }

    /**
     * Records that the burst has just been used at the given time.
     *
     * @param currentTime               current simulation time in seconds
     * @param effectiveCooldownDuration cooldown duration captured at use
     */
    public void markBurstUsed(double currentTime, double effectiveCooldownDuration) {
        lastBurstTime = currentTime;
        burstCooldownEndTime = currentTime + Math.max(0.0, effectiveCooldownDuration);
    }

    /**
     * Clears all pending skill charge restore timestamps. Used when resetting
     * the simulation state.
     */
    public void resetChargeState() {
        chargeRestoreTimes.clear();
        activeChargeCooldownDuration = 0.0;
    }

    /**
     * @return configured skill cooldown length in seconds
     */
    public double getSkillCD() {
        return skillCD;
    }

    /**
     * @return configured burst cooldown length in seconds
     */
    public double getBurstCD() {
        return burstCD;
    }

    /**
     * @return timestamp at which the skill was last used (large negative if never)
     */
    public double getLastSkillTime() {
        return lastSkillTime;
    }

    /**
     * @return timestamp at which the burst was last used (large negative if never)
     */
    public double getLastBurstTime() {
        return lastBurstTime;
    }

    /** @return captured single-charge Skill cooldown end time */
    public double getSkillCooldownEndTime() {
        return skillCooldownEndTime;
    }

    /** @return captured Burst cooldown end time */
    public double getBurstCooldownEndTime() {
        return burstCooldownEndTime;
    }

    /** @return captured duration shared by the active charge restore queue */
    public double getActiveChargeCooldownDuration() {
        return activeChargeCooldownDuration;
    }

    /**
     * Returns a copy of the current charge restore times list.
     *
     * @return copy of charge restore times
     */
    public List<Double> getChargeRestoreTimes() {
        return new ArrayList<>(chargeRestoreTimes);
    }

    /**
     * Restores cooldown state from previously captured values.
     *
     * @param savedLastSkillTime          last skill use time
     * @param savedLastBurstTime          last burst use time
     * @param savedSkillCooldownEndTime   single-charge Skill readiness boundary
     * @param savedBurstCooldownEndTime   Burst readiness boundary
     * @param savedChargeCooldownDuration active multi-charge queue duration
     * @param savedChargeRestoreTimes     charge restore schedule
     */
    public void restore(
            double savedLastSkillTime,
            double savedLastBurstTime,
            double savedSkillCooldownEndTime,
            double savedBurstCooldownEndTime,
            double savedChargeCooldownDuration,
            List<Double> savedChargeRestoreTimes) {
        this.lastSkillTime = savedLastSkillTime;
        this.lastBurstTime = savedLastBurstTime;
        this.skillCooldownEndTime = savedSkillCooldownEndTime;
        this.burstCooldownEndTime = savedBurstCooldownEndTime;
        this.activeChargeCooldownDuration = savedChargeCooldownDuration;
        this.chargeRestoreTimes.clear();
        this.chargeRestoreTimes.addAll(savedChargeRestoreTimes);
    }

    private void removeRestoredCharges(double currentTime) {
        chargeRestoreTimes.removeIf(time -> time <= currentTime);
        if (chargeRestoreTimes.isEmpty()) {
            activeChargeCooldownDuration = 0.0;
        }
    }
}

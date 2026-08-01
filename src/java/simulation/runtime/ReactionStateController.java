package simulation.runtime;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import model.type.CharacterId;
import simulation.CombatSimulator;

/**
 * Owns simulator-facing access to transient reaction state such as
 * Electro-Charged scheduling flags and Thundercloud expiry.
 */
public class ReactionStateController {
    private final CombatSimulator sim;
    private final ReactionState reactionState;

    /**
     * Creates a controller bound to the given simulator and state holder.
     *
     * @param sim active simulator
     * @param reactionState mutable transient reaction state
     */
    public ReactionStateController(CombatSimulator sim, ReactionState reactionState) {
        this.sim = sim;
        this.reactionState = reactionState;
    }

    /**
     * Attempts to accept Overload reaction damage at the current simulator time.
     *
     * @param ownerId character that owns the Overload reaction
     * @return {@code true} when target and owner cooldowns both permit damage
     */
    public boolean tryStartOverloadDamageCooldown(CharacterId ownerId) {
        return reactionState.tryStartOverloadDamageCooldown(
                ownerId, sim.getCurrentTime());
    }

    /** Returns the target-wide Overload damage cooldown end time. */
    public double getOverloadTargetDamageCooldownEndTime() {
        return reactionState.getOverloadTargetDamageCooldownEndTime();
    }

    /** Returns a defensive copy of owner-specific Overload cooldown end times. */
    public Map<CharacterId, Double> copyOverloadOwnerDamageCooldownEndTimes() {
        return reactionState.copyOverloadOwnerDamageCooldownEndTimes();
    }

    /** Restores both dimensions of Overload reaction-damage cooldown state. */
    public void restoreOverloadDamageCooldowns(
            double targetEndTime,
            Map<CharacterId, Double> ownerEndTimes) {
        reactionState.restoreOverloadDamageCooldowns(targetEndTime, ownerEndTimes);
    }

    /**
     * Sets whether an EC-related timer is active.
     *
     * @param running new EC timer state
     */
    public void setEcTimerRunning(boolean running) {
        reactionState.setEcTimerRunning(running);
    }

    /**
     * Returns whether an EC-related timer is active.
     *
     * @return {@code true} if active
     */
    public boolean isEcTimerRunning() {
        return reactionState.isEcTimerRunning();
    }

    /**
     * Returns whether Thundercloud is active at the current time.
     *
     * @return {@code true} if active
     */
    public boolean isThundercloudActive() {
        return sim.getCurrentTime() < reactionState.getThundercloudEndTime();
    }

    /**
     * Returns the Thundercloud expiry time.
     *
     * @return expiry time in seconds
     */
    public double getThundercloudEndTime() {
        return reactionState.getThundercloudEndTime();
    }

    /**
     * Sets the Thundercloud expiry time.
     *
     * @param endTime expiry time in seconds
     */
    public void setThundercloudEndTime(double endTime) {
        reactionState.setThundercloudEndTime(endTime);
    }

    public boolean isBurningTimerRunning() {
        return reactionState.isBurningTimerRunning();
    }

    public void setBurningTimerRunning(boolean running) {
        reactionState.setBurningTimerRunning(running);
    }

    public boolean isBurningActive() {
        ReactionState.BurningState state = reactionState.getBurningState();
        if (state != null) {
            return state.remainingFuelAt(sim.getCurrentTime()) > 0.0;
        }
        return sim.getCurrentTime() < reactionState.getBurningEndTime();
    }

    public double getBurningEndTime() {
        return reactionState.getBurningEndTime();
    }

    public void setBurningEndTime(double endTime) {
        reactionState.setBurningEndTime(endTime);
    }

    /** Starts a typed Burning generation at the current simulator time. */
    public ReactionState.BurningState startBurning(
            CharacterId ownerId,
            double preResistanceDamage,
            double fuelUnits,
            double fuelDecayRate) {
        return reactionState.startBurning(
                ownerId,
                preResistanceDamage,
                fuelUnits,
                fuelDecayRate,
                sim.getCurrentTime());
    }

    /** Replaces active Burning fuel at the current simulator time. */
    public ReactionState.BurningState replaceBurningFuel(
            double fuelUnits, double fuelDecayRate) {
        return reactionState.replaceBurningFuel(
                fuelUnits, fuelDecayRate, sim.getCurrentTime());
    }

    /** Refreshes active Burning damage ownership at the current simulator time. */
    public ReactionState.BurningState refreshBurningDamage(
            CharacterId ownerId, double preResistanceDamage) {
        return reactionState.refreshBurningDamage(
                ownerId, preResistanceDamage, sim.getCurrentTime());
    }

    /** Rebases active Burning fuel at the current simulator time. */
    public ReactionState.BurningState advanceBurning() {
        return reactionState.advanceBurning(sim.getCurrentTime());
    }

    /** Returns the current immutable Burning payload. */
    public ReactionState.BurningState getBurningState() {
        return reactionState.getBurningState();
    }

    /** Clears typed Burning fuel, damage, and timer state. */
    public void clearBurning() {
        reactionState.clearBurning();
    }

    public boolean isQuickenActive() {
        ReactionState.QuickenState state = reactionState.getQuickenState();
        if (state != null) {
            return state.remainingUnitsAt(sim.getCurrentTime()) > 0.0;
        }
        return sim.getCurrentTime() < reactionState.getQuickenEndTime();
    }

    public double getQuickenEndTime() {
        return reactionState.getQuickenEndTime();
    }

    public void setQuickenEndTime(double endTime) {
        reactionState.setQuickenEndTime(endTime);
    }

    /** Applies typed Quicken gauge at the current simulator time. */
    public ReactionState.QuickenState applyQuicken(double units) {
        return reactionState.applyQuicken(units, sim.getCurrentTime());
    }

    /** Consumes typed Quicken gauge at the current simulator time. */
    public ReactionState.QuickenState consumeQuicken(double units) {
        return reactionState.consumeQuicken(units, sim.getCurrentTime());
    }

    /** Returns the immutable current Quicken payload. */
    public ReactionState.QuickenState getQuickenState() {
        return reactionState.getQuickenState();
    }

    /** Clears typed and compatibility Quicken state. */
    public void clearQuicken() {
        reactionState.clearQuicken();
    }

    public int getMoondriftCount() {
        return reactionState.getMoondriftCount();
    }

    public void setMoondriftCount(int count) {
        reactionState.setMoondriftCount(count);
    }

    public int getLunarCrystallizeTriggerCount() {
        return reactionState.getLunarCrystallizeTriggerCount();
    }

    public void setLunarCrystallizeTriggerCount(int count) {
        reactionState.setLunarCrystallizeTriggerCount(count);
    }

    public int incrementLunarCrystallizeTriggerCount() {
        return reactionState.incrementLunarCrystallizeTriggerCount();
    }

    public ReactionState.DendroCoreState addDendroCore(CharacterId ownerId, double preResistanceDamage) {
        return reactionState.addDendroCore(ownerId, sim.getCurrentTime(), preResistanceDamage);
    }

    public List<ReactionState.DendroCoreState> getDendroCores() {
        return reactionState.getDendroCores();
    }

    public List<ReactionState.DendroCoreState> copyDendroCores() {
        return new ArrayList<>(reactionState.getDendroCores());
    }

    public ReactionState.DendroCoreState removeOldestDendroCore() {
        return reactionState.removeOldestDendroCore();
    }

    public boolean removeDendroCore(int coreId) {
        return reactionState.removeDendroCore(coreId);
    }

    public void clearDendroCores() {
        reactionState.clearDendroCores();
    }

    public int getNextDendroCoreId() {
        return reactionState.getNextDendroCoreId();
    }

    public void restoreDendroCores(List<ReactionState.DendroCoreState> cores, int nextCoreId) {
        reactionState.restoreDendroCores(cores, nextCoreId);
    }
}

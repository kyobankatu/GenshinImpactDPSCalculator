package simulation.runtime;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import model.type.CharacterId;
import model.type.Element;
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
     * Atomically consumes up to the requested number of Verdant Dew stacks.
     *
     * @param requested non-negative maximum stack count to consume
     * @return actual consumed stack count
     */
    public int consumeVerdantDewCount(int requested) {
        return reactionState.consumeVerdantDewCount(requested);
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

    /** Attempts to accept Superconduct damage at the current simulator time. */
    public boolean tryStartSuperconductDamageSequence(CharacterId ownerId) {
        return reactionState.tryStartSuperconductDamageSequence(
                ownerId, sim.getCurrentTime());
    }

    /** Returns the target-wide Superconduct damage cooldown end time. */
    public double getSuperconductTargetDamageCooldownEndTime() {
        return reactionState.getSuperconductTargetDamageCooldownEndTime();
    }

    /** Returns a defensive copy of owner-specific Superconduct sequence state. */
    public Map<CharacterId, ReactionState.FixedDamageSequenceState>
            copySuperconductOwnerDamageSequenceStates() {
        return reactionState.copySuperconductOwnerDamageSequenceStates();
    }

    /** Restores both dimensions of Superconduct damage-sequence state. */
    public void restoreSuperconductDamageSequence(
            double targetEndTime,
            Map<CharacterId, ReactionState.FixedDamageSequenceState>
                    ownerStates) {
        reactionState.restoreSuperconductDamageSequence(
                targetEndTime, ownerStates);
    }

    /** Attempts to accept Shatter damage at the current simulator time. */
    public boolean tryStartShatterDamageSequence(CharacterId ownerId) {
        return reactionState.tryStartShatterDamageSequence(
                ownerId, sim.getCurrentTime());
    }

    /** Returns the target-wide Shatter damage cooldown end time. */
    public double getShatterTargetDamageCooldownEndTime() {
        return reactionState.getShatterTargetDamageCooldownEndTime();
    }

    /** Returns a defensive copy of owner-specific Shatter sequence state. */
    public Map<CharacterId, ReactionState.FixedDamageSequenceState>
            copyShatterOwnerDamageSequenceStates() {
        return reactionState.copyShatterOwnerDamageSequenceStates();
    }

    /** Restores both dimensions of Shatter damage-sequence state. */
    public void restoreShatterDamageSequence(
            double targetEndTime,
            Map<CharacterId, ReactionState.FixedDamageSequenceState>
                    ownerStates) {
        reactionState.restoreShatterDamageSequence(targetEndTime, ownerStates);
    }

    /** Attempts to accept standard Crystallize at the current simulator time. */
    public boolean tryStartStandardCrystallizeCooldown() {
        return reactionState.tryStartStandardCrystallizeCooldown(
                sim.getCurrentTime());
    }

    /** Returns the target-wide standard Crystallize cooldown end time. */
    public double getStandardCrystallizeCooldownEndTime() {
        return reactionState.getStandardCrystallizeCooldownEndTime();
    }

    /** Restores the target-wide standard Crystallize cooldown boundary. */
    public void restoreStandardCrystallizeCooldown(double endTime) {
        reactionState.restoreStandardCrystallizeCooldown(endTime);
    }

    /** Attempts to accept Swirl damage at the current simulator time. */
    public boolean tryStartSwirlDamageSequence(
            CharacterId ownerId, Element swirledElement) {
        return reactionState.tryStartSwirlDamageSequence(
                ownerId, swirledElement, sim.getCurrentTime());
    }

    /** Returns a defensive copy of per-element Swirl target boundaries. */
    public Map<Element, Double> copySwirlTargetDamageCooldownEndTimes() {
        return reactionState.copySwirlTargetDamageCooldownEndTimes();
    }

    /** Returns a deep defensive copy of Swirl owner sequence state. */
    public Map<Element, Map<CharacterId, ReactionState.FixedDamageSequenceState>>
            copySwirlOwnerDamageSequenceStates() {
        return reactionState.copySwirlOwnerDamageSequenceStates();
    }

    /** Restores both dimensions of per-element Swirl damage state. */
    public void restoreSwirlDamageSequence(
            Map<Element, Double> targetEndTimes,
            Map<Element, Map<CharacterId, ReactionState.FixedDamageSequenceState>>
                    ownerStates) {
        reactionState.restoreSwirlDamageSequence(targetEndTimes, ownerStates);
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

    /** Returns whether a standard Electro-Charged sequence is active. */
    public boolean isStandardElectroChargedActive() {
        return reactionState.isEcTimerRunning()
                && reactionState.getStandardElectroChargedState() != null;
    }

    /** Returns the latest standard Electro-Charged tick payload. */
    public ReactionState.StandardElectroChargedState
            getStandardElectroChargedState() {
        return reactionState.getStandardElectroChargedState();
    }

    /** Replaces the next standard Electro-Charged tick payload. */
    public void updateStandardElectroChargedState(
            CharacterId ownerId, double preResistanceDamage) {
        reactionState.updateStandardElectroChargedState(
                ownerId, preResistanceDamage);
    }

    /** Clears standard Electro-Charged ownership state. */
    public void clearStandardElectroChargedState() {
        reactionState.clearStandardElectroChargedState();
    }

    /** Restores standard Electro-Charged ownership state. */
    public void restoreStandardElectroChargedState(
            ReactionState.StandardElectroChargedState state) {
        reactionState.restoreStandardElectroChargedState(state);
    }

    /** Attempts to accept standard Electro-Charged target damage. */
    public boolean tryStartStandardElectroChargedDamageCooldown() {
        return reactionState.tryStartStandardElectroChargedDamageCooldown(
                sim.getCurrentTime());
    }

    /** Returns the standard Electro-Charged target cooldown end time. */
    public double getStandardElectroChargedDamageCooldownEndTime() {
        return reactionState.getStandardElectroChargedDamageCooldownEndTime();
    }

    /** Returns the last successful standard Electro-Charged damage time. */
    public double getStandardElectroChargedLastDamageTime() {
        return reactionState.getStandardElectroChargedLastDamageTime();
    }

    /** Restores standard Electro-Charged target damage timing. */
    public void restoreStandardElectroChargedDamageCooldown(
            double cooldownEndTime, double lastDamageTime) {
        reactionState.restoreStandardElectroChargedDamageCooldown(
                cooldownEndTime, lastDamageTime);
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

    /** Attempts to accept Dendro Core damage at the current simulator time. */
    public boolean tryStartDendroCoreDamage() {
        return reactionState.tryStartDendroCoreDamage(sim.getCurrentTime());
    }

    /** Returns a defensive copy of active Dendro Core damage timestamps. */
    public List<Double> copyRecentDendroCoreDamageTimes() {
        return reactionState.copyRecentDendroCoreDamageTimes(
                sim.getCurrentTime());
    }

    /** Restores Dendro Core damage-cap history at the current simulator time. */
    public void restoreRecentDendroCoreDamageTimes(List<Double> damageTimes) {
        reactionState.restoreRecentDendroCoreDamageTimes(
                damageTimes, sim.getCurrentTime());
    }

    public int getNextDendroCoreId() {
        return reactionState.getNextDendroCoreId();
    }

    public void restoreDendroCores(List<ReactionState.DendroCoreState> cores, int nextCoreId) {
        reactionState.restoreDendroCores(cores, nextCoreId);
    }
}

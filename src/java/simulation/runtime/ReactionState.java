package simulation.runtime;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import model.type.CharacterId;

/** Holds snapshot-relevant transient state for reaction runtime policies. */
public class ReactionState {
    private static final double GAUGE_EPSILON = 1e-9;
    private static final double TIMING_EPSILON = 1e-9;
    private static final double OVERLOAD_TARGET_DAMAGE_GCD = 0.1;
    private static final double OVERLOAD_OWNER_DAMAGE_COOLDOWN = 0.5;
    private static final double STANDARD_CRYSTALLIZE_COOLDOWN = 1.0;
    /** Immutable consumable Quicken Aura payload. */
    public static final class QuickenState {
        /** Quicken gauge present at {@link #lastUpdateTime}. */
        public final double units;
        /** Linear Quicken decay in units per second. */
        public final double decayRate;
        /** Time at which {@link #units} was measured. */
        public final double lastUpdateTime;

        private QuickenState(double units, double decayRate, double lastUpdateTime) {
            this.units = units;
            this.decayRate = decayRate;
            this.lastUpdateTime = lastUpdateTime;
        }

        /** Returns current Quicken units after linear decay. */
        public double remainingUnitsAt(double currentTime) {
            double elapsed = Math.max(0.0, currentTime - lastUpdateTime);
            return Math.max(0.0, units - decayRate * elapsed);
        }

        /** Returns the exact absolute Quicken expiry time. */
        public double getEndTime() {
            return lastUpdateTime + units / decayRate;
        }
    }

    /** Immutable single-target Burning fuel and damage payload. */
    public static final class BurningState {
        /** Character credited with the next Burning damage tick. */
        public final CharacterId ownerId;
        /** Burning damage before impact-time resistance. */
        public final double preResistanceDamage;
        /** Fuel units present at {@link #lastUpdateTime}. */
        public final double fuelUnits;
        /** Special Burning fuel decay in units per second. */
        public final double fuelDecayRate;
        /** Time at which {@link #fuelUnits} was measured. */
        public final double lastUpdateTime;
        /** Event generation used to reject superseded timer events. */
        public final int generation;

        private BurningState(
                CharacterId ownerId,
                double preResistanceDamage,
                double fuelUnits,
                double fuelDecayRate,
                double lastUpdateTime,
                int generation) {
            this.ownerId = ownerId;
            this.preResistanceDamage = preResistanceDamage;
            this.fuelUnits = fuelUnits;
            this.fuelDecayRate = fuelDecayRate;
            this.lastUpdateTime = lastUpdateTime;
            this.generation = generation;
        }

        /**
         * Returns fuel remaining after continuous special decay.
         *
         * @param currentTime simulator time in seconds
         * @return remaining fuel units, never negative
         */
        public double remainingFuelAt(double currentTime) {
            double elapsed = Math.max(0.0, currentTime - lastUpdateTime);
            return Math.max(0.0, fuelUnits - fuelDecayRate * elapsed);
        }

        /**
         * Returns the exact absolute fuel depletion time.
         *
         * @return absolute simulator time in seconds
         */
        public double getEndTime() {
            return lastUpdateTime + fuelUnits / fuelDecayRate;
        }
    }

    /** Immutable delayed Dendro Core payload stored across snapshot/restore. */
    public static final class DendroCoreState {
        /** Stable runtime core identifier. */
        public final int id;
        /** Character credited when this core deals damage. */
        public final CharacterId ownerId;
        /** Core creation time. */
        public final double creationTime;
        /** Scheduled natural explosion time. */
        public final double expiryTime;
        /** Reaction damage before resistance is evaluated at impact. */
        public final double preResistanceDamage;

        /**
         * Creates an immutable delayed Dendro Core payload.
         *
         * @param id stable runtime identifier
         * @param ownerId character credited for later damage
         * @param creationTime core creation time
         * @param expiryTime natural explosion time
         * @param preResistanceDamage damage before impact-time resistance
         */
        public DendroCoreState(
                int id,
                CharacterId ownerId,
                double creationTime,
                double expiryTime,
                double preResistanceDamage) {
            this.id = id;
            this.ownerId = ownerId;
            this.creationTime = creationTime;
            this.expiryTime = expiryTime;
            this.preResistanceDamage = preResistanceDamage;
        }
    }

    private boolean ecTimerRunning = false;
    private double thundercloudEndTime = -1.0;
    private boolean burningTimerRunning = false;
    private double burningEndTime = -1.0;
    private BurningState burningState;
    private int nextBurningGeneration = 1;
    private double quickenEndTime = -1.0;
    private QuickenState quickenState;
    private double overloadTargetDamageCooldownEndTime = -1.0;
    private final Map<CharacterId, Double> overloadOwnerDamageCooldownEndTimes =
            new EnumMap<>(CharacterId.class);
    private double standardCrystallizeCooldownEndTime = -1.0;
    private int moondriftCount = 0;
    private int lunarCrystallizeTriggerCount = 0;
    private int verdantDewCount = 0;
    private int moonridgeDewCount = 0;
    private final List<DendroCoreState> dendroCores = new ArrayList<>();
    private int nextDendroCoreId = 1;

    /**
     * Attempts to start both Overload damage-sequence cooldowns.
     *
     * <p>The one-enemy simulator applies a 0.1-second target-wide gate before
     * the 0.5-second owner-specific gate. Blocked attempts do not start either
     * cooldown because no reaction-damage hit was accepted.
     *
     * @param ownerId character that owns the Overload reaction
     * @param currentTime simulator time in seconds
     * @return {@code true} when this Overload may deal damage
     */
    public boolean tryStartOverloadDamageCooldown(
            CharacterId ownerId, double currentTime) {
        if (ownerId == null || !Double.isFinite(currentTime)) {
            return false;
        }
        double ownerEndTime = overloadOwnerDamageCooldownEndTimes.getOrDefault(
                ownerId, -1.0);
        if (currentTime + TIMING_EPSILON < overloadTargetDamageCooldownEndTime
                || currentTime + TIMING_EPSILON < ownerEndTime) {
            return false;
        }
        overloadTargetDamageCooldownEndTime =
                currentTime + OVERLOAD_TARGET_DAMAGE_GCD;
        overloadOwnerDamageCooldownEndTimes.put(
                ownerId, currentTime + OVERLOAD_OWNER_DAMAGE_COOLDOWN);
        return true;
    }

    /** Returns the target-wide Overload damage cooldown end time. */
    public double getOverloadTargetDamageCooldownEndTime() {
        return overloadTargetDamageCooldownEndTime;
    }

    /** Returns a defensive copy of owner-specific Overload cooldown end times. */
    public Map<CharacterId, Double> copyOverloadOwnerDamageCooldownEndTimes() {
        return new EnumMap<>(overloadOwnerDamageCooldownEndTimes);
    }

    /**
     * Restores Overload damage-sequence state from a simulator snapshot.
     *
     * @param targetEndTime target-wide cooldown end time
     * @param ownerEndTimes owner-specific cooldown end times
     */
    public void restoreOverloadDamageCooldowns(
            double targetEndTime,
            Map<CharacterId, Double> ownerEndTimes) {
        overloadTargetDamageCooldownEndTime = Double.isFinite(targetEndTime)
                ? targetEndTime
                : -1.0;
        overloadOwnerDamageCooldownEndTimes.clear();
        if (ownerEndTimes == null) {
            return;
        }
        for (Map.Entry<CharacterId, Double> entry : ownerEndTimes.entrySet()) {
            CharacterId ownerId = entry.getKey();
            Double endTime = entry.getValue();
            if (ownerId != null && endTime != null && Double.isFinite(endTime)) {
                overloadOwnerDamageCooldownEndTimes.put(ownerId, endTime);
            }
        }
    }

    /**
     * Attempts to start the target-wide standard Crystallize cooldown.
     *
     * <p>Blocked attempts leave the existing boundary unchanged. Lunar-
     * Crystallize is excluded by the resolver before this policy is called.
     *
     * @param currentTime simulator time in seconds
     * @return {@code true} when standard Crystallize may trigger
     */
    public boolean tryStartStandardCrystallizeCooldown(double currentTime) {
        if (!Double.isFinite(currentTime)
                || currentTime + TIMING_EPSILON < standardCrystallizeCooldownEndTime) {
            return false;
        }
        standardCrystallizeCooldownEndTime =
                currentTime + STANDARD_CRYSTALLIZE_COOLDOWN;
        return true;
    }

    /** Returns the target-wide standard Crystallize cooldown end time. */
    public double getStandardCrystallizeCooldownEndTime() {
        return standardCrystallizeCooldownEndTime;
    }

    /** Restores the standard Crystallize cooldown boundary. */
    public void restoreStandardCrystallizeCooldown(double endTime) {
        standardCrystallizeCooldownEndTime = Double.isFinite(endTime)
                ? endTime
                : -1.0;
    }

    /**
     * Returns whether an EC-related timer is active.
     *
     * @return {@code true} if active
     */
    public boolean isEcTimerRunning() {
        return ecTimerRunning;
    }

    /**
     * Sets whether an EC-related timer is active.
     *
     * @param running new active state
     */
    public void setEcTimerRunning(boolean running) {
        this.ecTimerRunning = running;
    }

    /**
     * Returns the Thundercloud expiry time.
     *
     * @return absolute simulation time in seconds
     */
    public double getThundercloudEndTime() {
        return thundercloudEndTime;
    }

    /**
     * Sets the Thundercloud expiry time.
     *
     * @param thundercloudEndTime absolute simulation time in seconds
     */
    public void setThundercloudEndTime(double thundercloudEndTime) {
        this.thundercloudEndTime = thundercloudEndTime;
    }

    public boolean isBurningTimerRunning() {
        return burningTimerRunning;
    }

    public void setBurningTimerRunning(boolean burningTimerRunning) {
        this.burningTimerRunning = burningTimerRunning;
    }

    public double getBurningEndTime() {
        return burningEndTime;
    }

    public void setBurningEndTime(double burningEndTime) {
        this.burningEndTime = burningEndTime;
    }

    /**
     * Starts a new typed Burning generation.
     *
     * @param ownerId damage owner
     * @param preResistanceDamage damage before impact-time resistance
     * @param fuelUnits current Dendro fuel units
     * @param fuelDecayRate special fuel decay in units per second
     * @param currentTime simulator time in seconds
     * @return immutable state, or {@code null} when the payload is invalid
     */
    public BurningState startBurning(
            CharacterId ownerId,
            double preResistanceDamage,
            double fuelUnits,
            double fuelDecayRate,
            double currentTime) {
        if (!isValidBurningPayload(
                ownerId, preResistanceDamage, fuelUnits, fuelDecayRate, currentTime)) {
            clearBurning();
            return null;
        }
        burningState = new BurningState(
                ownerId,
                preResistanceDamage,
                fuelUnits,
                fuelDecayRate,
                currentTime,
                nextBurningGeneration++);
        burningEndTime = burningState.getEndTime();
        return burningState;
    }

    /**
     * Replaces active Dendro fuel while retaining the timer generation.
     *
     * @param fuelUnits replacement fuel units
     * @param fuelDecayRate replacement special decay rate
     * @param currentTime simulator refresh time
     * @return refreshed state, or {@code null} when no valid state remains
     */
    public BurningState replaceBurningFuel(
            double fuelUnits, double fuelDecayRate, double currentTime) {
        if (burningState == null || !isValidBurningPayload(
                burningState.ownerId,
                burningState.preResistanceDamage,
                fuelUnits,
                fuelDecayRate,
                currentTime)) {
            clearBurning();
            return null;
        }
        burningState = new BurningState(
                burningState.ownerId,
                burningState.preResistanceDamage,
                fuelUnits,
                fuelDecayRate,
                currentTime,
                burningState.generation);
        burningEndTime = burningState.getEndTime();
        return burningState;
    }

    /**
     * Updates the latest Burning damage owner without replacing its fuel.
     *
     * @param ownerId new damage owner
     * @param preResistanceDamage new damage before impact-time resistance
     * @param currentTime simulator refresh time
     * @return refreshed state, or {@code null} when no valid state remains
     */
    public BurningState refreshBurningDamage(
            CharacterId ownerId, double preResistanceDamage, double currentTime) {
        if (burningState == null) {
            return null;
        }
        double remainingFuel = burningState.remainingFuelAt(currentTime);
        if (!isValidBurningPayload(
                ownerId,
                preResistanceDamage,
                remainingFuel,
                burningState.fuelDecayRate,
                currentTime)) {
            clearBurning();
            return null;
        }
        burningState = new BurningState(
                ownerId,
                preResistanceDamage,
                remainingFuel,
                burningState.fuelDecayRate,
                currentTime,
                burningState.generation);
        burningEndTime = burningState.getEndTime();
        return burningState;
    }

    /**
     * Rebases active fuel at the requested time after continuous decay.
     *
     * @param currentTime simulator time in seconds
     * @return rebased state, or {@code null} after depletion
     */
    public BurningState advanceBurning(double currentTime) {
        if (burningState == null) {
            return null;
        }
        double remainingFuel = burningState.remainingFuelAt(currentTime);
        if (remainingFuel <= 0.0) {
            clearBurning();
            return null;
        }
        burningState = new BurningState(
                burningState.ownerId,
                burningState.preResistanceDamage,
                remainingFuel,
                burningState.fuelDecayRate,
                currentTime,
                burningState.generation);
        burningEndTime = burningState.getEndTime();
        return burningState;
    }

    /** Returns the immutable current Burning payload. */
    public BurningState getBurningState() {
        return burningState;
    }

    /** Returns the next timer generation identifier. */
    public int getNextBurningGeneration() {
        return nextBurningGeneration;
    }

    /** Clears typed Burning data and invalidates existing timer generations. */
    public void clearBurning() {
        burningState = null;
        burningEndTime = -1.0;
        burningTimerRunning = false;
        nextBurningGeneration++;
    }

    /**
     * Restores a captured typed Burning payload.
     *
     * @param state immutable captured state, or {@code null}
     * @param nextGeneration next generation counter from the snapshot
     */
    public void restoreBurning(BurningState state, int nextGeneration) {
        burningState = state == null
                ? null
                : new BurningState(
                        state.ownerId,
                        state.preResistanceDamage,
                        state.fuelUnits,
                        state.fuelDecayRate,
                        state.lastUpdateTime,
                        state.generation);
        nextBurningGeneration = Math.max(1, nextGeneration);
        burningEndTime = burningState != null ? burningState.getEndTime() : -1.0;
    }

    private boolean isValidBurningPayload(
            CharacterId ownerId,
            double preResistanceDamage,
            double fuelUnits,
            double fuelDecayRate,
            double currentTime) {
        return ownerId != null
                && Double.isFinite(preResistanceDamage)
                && preResistanceDamage >= 0.0
                && Double.isFinite(fuelUnits)
                && fuelUnits > 0.0
                && Double.isFinite(fuelDecayRate)
                && fuelDecayRate > 0.0
                && Double.isFinite(currentTime);
    }

    public double getQuickenEndTime() {
        return quickenState != null ? quickenState.getEndTime() : quickenEndTime;
    }

    public void setQuickenEndTime(double quickenEndTime) {
        quickenState = null;
        this.quickenEndTime = quickenEndTime;
    }

    /**
     * Creates or refreshes Quicken when the incoming gauge is not weaker.
     *
     * @param units incoming Quicken gauge units
     * @param currentTime simulator application time
     * @return active state, or {@code null} for invalid input
     */
    public QuickenState applyQuicken(double units, double currentTime) {
        if (!Double.isFinite(units) || units <= 0.0 || !Double.isFinite(currentTime)) {
            return null;
        }
        if (quickenState != null) {
            double remaining = quickenState.remainingUnitsAt(currentTime);
            if (remaining > 0.0 && units + GAUGE_EPSILON < remaining) {
                return quickenState;
            }
        }
        double duration = units * 5.0 + 6.0;
        quickenState = new QuickenState(units, units / duration, currentTime);
        quickenEndTime = quickenState.getEndTime();
        return quickenState;
    }

    /**
     * Consumes current Quicken gauge and preserves its selected decay rate.
     *
     * @param units gauge units to consume
     * @param currentTime simulator consumption time
     * @return remaining state, or {@code null} after exact/over-consumption
     */
    public QuickenState consumeQuicken(double units, double currentTime) {
        if (quickenState == null
                || !Double.isFinite(units)
                || units <= 0.0
                || !Double.isFinite(currentTime)) {
            return quickenState;
        }
        double remaining = quickenState.remainingUnitsAt(currentTime) - units;
        if (remaining <= 0.0) {
            clearQuicken();
            return null;
        }
        quickenState = new QuickenState(
                remaining, quickenState.decayRate, currentTime);
        quickenEndTime = quickenState.getEndTime();
        return quickenState;
    }

    /** Returns the immutable typed Quicken state. */
    public QuickenState getQuickenState() {
        return quickenState;
    }

    /** Clears typed and compatibility Quicken state. */
    public void clearQuicken() {
        quickenState = null;
        quickenEndTime = -1.0;
    }

    /** Restores an immutable Quicken payload from a simulator snapshot. */
    public void restoreQuicken(QuickenState state, double compatibilityEndTime) {
        quickenState = state == null
                ? null
                : new QuickenState(state.units, state.decayRate, state.lastUpdateTime);
        quickenEndTime = quickenState != null
                ? quickenState.getEndTime()
                : compatibilityEndTime;
    }

    public int getMoondriftCount() {
        return moondriftCount;
    }

    public void setMoondriftCount(int moondriftCount) {
        this.moondriftCount = moondriftCount;
    }

    public int getLunarCrystallizeTriggerCount() {
        return lunarCrystallizeTriggerCount;
    }

    public void setLunarCrystallizeTriggerCount(int lunarCrystallizeTriggerCount) {
        this.lunarCrystallizeTriggerCount = lunarCrystallizeTriggerCount;
    }

    public int incrementLunarCrystallizeTriggerCount() {
        lunarCrystallizeTriggerCount++;
        return lunarCrystallizeTriggerCount;
    }

    public int getVerdantDewCount() {
        return verdantDewCount;
    }

    public void setVerdantDewCount(int verdantDewCount) {
        this.verdantDewCount = verdantDewCount;
    }

    public int incrementVerdantDewCount() {
        verdantDewCount++;
        return verdantDewCount;
    }

    public int getMoonridgeDewCount() {
        return moonridgeDewCount;
    }

    public void setMoonridgeDewCount(int moonridgeDewCount) {
        this.moonridgeDewCount = moonridgeDewCount;
    }

    public int incrementMoonridgeDewCount() {
        moonridgeDewCount++;
        return moonridgeDewCount;
    }

    public DendroCoreState addDendroCore(
            CharacterId ownerId, double currentTime, double preResistanceDamage) {
        DendroCoreState core = new DendroCoreState(
                nextDendroCoreId++, ownerId, currentTime,
                currentTime + 6.0, preResistanceDamage);
        dendroCores.add(core);
        return core;
    }

    public List<DendroCoreState> getDendroCores() {
        return dendroCores;
    }

    public DendroCoreState removeOldestDendroCore() {
        if (dendroCores.isEmpty()) {
            return null;
        }
        return dendroCores.remove(0);
    }

    public boolean removeDendroCore(int coreId) {
        return dendroCores.removeIf(core -> core.id == coreId);
    }

    public void clearDendroCores() {
        dendroCores.clear();
    }

    public void restoreDendroCores(List<DendroCoreState> cores, int nextCoreId) {
        dendroCores.clear();
        dendroCores.addAll(cores);
        nextDendroCoreId = nextCoreId;
    }

    public int getNextDendroCoreId() {
        return nextDendroCoreId;
    }
}

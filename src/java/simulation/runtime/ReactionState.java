package simulation.runtime;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import model.stats.StatsContainer;
import model.type.CharacterId;
import model.type.Element;

/** Holds snapshot-relevant transient state for reaction runtime policies. */
public class ReactionState {
    private static final double GAUGE_EPSILON = 1e-9;
    private static final double TIMING_EPSILON = 1e-9;
    private static final double OVERLOAD_TARGET_DAMAGE_GCD = 0.1;
    private static final double OVERLOAD_OWNER_DAMAGE_COOLDOWN = 0.5;
    private static final double SUPERCONDUCT_TARGET_DAMAGE_GCD = 0.1;
    private static final double SUPERCONDUCT_OWNER_SEQUENCE_WINDOW = 0.5;
    private static final int SUPERCONDUCT_OWNER_DAMAGE_LIMIT = 2;
    private static final double SHATTER_TARGET_DAMAGE_GCD = 0.2;
    private static final double SHATTER_OWNER_SEQUENCE_WINDOW = 0.5;
    private static final int SHATTER_OWNER_DAMAGE_LIMIT = 2;
    private static final double STANDARD_CRYSTALLIZE_COOLDOWN = 1.0;
    private static final double STANDARD_EC_DAMAGE_COOLDOWN = 0.5;
    private static final double SWIRL_TARGET_DAMAGE_GCD = 0.1;
    private static final double SWIRL_OWNER_SEQUENCE_WINDOW = 0.5;
    private static final int SWIRL_OWNER_DAMAGE_LIMIT = 2;
    private static final double DENDRO_CORE_DAMAGE_WINDOW = 0.5;
    private static final int DENDRO_CORE_DAMAGE_LIMIT = 2;
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
        /** Independent non-decaying Burning Aura gauge. */
        public final double burningAuraUnits;
        /** Reaction stats snapshotted by the latest Burning applier. */
        private final StatsContainer reactionStats;

        private BurningState(
                CharacterId ownerId,
                double preResistanceDamage,
                double fuelUnits,
                double fuelDecayRate,
                double lastUpdateTime,
                int generation,
                double burningAuraUnits,
                StatsContainer reactionStats) {
            this.ownerId = ownerId;
            this.preResistanceDamage = preResistanceDamage;
            this.fuelUnits = fuelUnits;
            this.fuelDecayRate = fuelDecayRate;
            this.lastUpdateTime = lastUpdateTime;
            this.generation = generation;
            this.burningAuraUnits = burningAuraUnits;
            this.reactionStats = reactionStats == null
                    ? null
                    : reactionStats.merge(null);
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

        /** Returns a defensive copy of the latest applier's reaction stats. */
        public StatsContainer getReactionStats() {
            return reactionStats == null ? null : reactionStats.merge(null);
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

    /** Immutable owner-specific fixed damage-sequence payload. */
    public static final class FixedDamageSequenceState {
        /** Fixed window end time started by the first target-accepted attempt. */
        public final double windowEndTime;
        /** Number of target-accepted attempts observed in the fixed window. */
        public final int attemptCount;

        private FixedDamageSequenceState(
                double windowEndTime, int attemptCount) {
            this.windowEndTime = windowEndTime;
            this.attemptCount = attemptCount;
        }
    }

    /** Immutable owner and damage payload for the next standard EC tick. */
    public static final class StandardElectroChargedState {
        /** Character credited with the next standard Electro-Charged tick. */
        public final CharacterId ownerId;
        /** Tick damage before impact-time Electro resistance. */
        public final double preResistanceDamage;

        private StandardElectroChargedState(
                CharacterId ownerId, double preResistanceDamage) {
            this.ownerId = ownerId;
            this.preResistanceDamage = preResistanceDamage;
        }
    }

    private boolean ecTimerRunning = false;
    private StandardElectroChargedState standardElectroChargedState;
    private double standardElectroChargedDamageCooldownEndTime = -1.0;
    private double standardElectroChargedLastDamageTime = -1.0;
    private double thundercloudEndTime = -1.0;
    private boolean burningTimerRunning = false;
    private double burningEndTime = -1.0;
    private double burningNextTickTime = -1.0;
    private BurningState burningState;
    private int nextBurningGeneration = 1;
    private double burningPyroApplicationCooldownEndTime = -1.0;
    private double quickenEndTime = -1.0;
    private QuickenState quickenState;
    private double overloadTargetDamageCooldownEndTime = -1.0;
    private final Map<CharacterId, Double> overloadOwnerDamageCooldownEndTimes =
            new EnumMap<>(CharacterId.class);
    private double superconductTargetDamageCooldownEndTime = -1.0;
    private final Map<CharacterId, FixedDamageSequenceState>
            superconductOwnerDamageSequenceStates =
            new EnumMap<>(CharacterId.class);
    private double shatterTargetDamageCooldownEndTime = -1.0;
    private final Map<CharacterId, FixedDamageSequenceState>
            shatterOwnerDamageSequenceStates =
            new EnumMap<>(CharacterId.class);
    private double standardCrystallizeCooldownEndTime = -1.0;
    private final Map<Element, Double> swirlTargetDamageCooldownEndTimes =
            new EnumMap<>(Element.class);
    private final Map<Element, Map<CharacterId, FixedDamageSequenceState>>
            swirlOwnerDamageSequenceStates = new EnumMap<>(Element.class);
    private int moondriftCount = 0;
    private int lunarCrystallizeTriggerCount = 0;
    private int verdantDewCount = 0;
    private int moonridgeDewCount = 0;
    private final List<DendroCoreState> dendroCores = new ArrayList<>();
    private final List<Double> recentDendroCoreDamageTimes = new ArrayList<>();
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
     * Attempts to accept Superconduct reaction damage.
     *
     * <p>The target-wide GCD is evaluated first. A target-accepted attempt then
     * advances the owner's fixed sequence even when its damage is suppressed.
     *
     * @param ownerId character that owns the Superconduct reaction
     * @param currentTime simulator time in seconds
     * @return {@code true} when this Superconduct may deal damage
     */
    public boolean tryStartSuperconductDamageSequence(
            CharacterId ownerId, double currentTime) {
        if (ownerId == null || !Double.isFinite(currentTime)
                || currentTime + TIMING_EPSILON
                        < superconductTargetDamageCooldownEndTime) {
            return false;
        }
        superconductTargetDamageCooldownEndTime =
                currentTime + SUPERCONDUCT_TARGET_DAMAGE_GCD;

        return advanceFixedDamageSequence(
                superconductOwnerDamageSequenceStates,
                ownerId,
                currentTime,
                SUPERCONDUCT_OWNER_SEQUENCE_WINDOW,
                SUPERCONDUCT_OWNER_DAMAGE_LIMIT);
    }

    /** Returns the target-wide Superconduct damage cooldown end time. */
    public double getSuperconductTargetDamageCooldownEndTime() {
        return superconductTargetDamageCooldownEndTime;
    }

    /** Returns a defensive copy of owner-specific Superconduct sequence state. */
    public Map<CharacterId, FixedDamageSequenceState>
            copySuperconductOwnerDamageSequenceStates() {
        return new EnumMap<>(superconductOwnerDamageSequenceStates);
    }

    /** Restores both dimensions of Superconduct damage-sequence state. */
    public void restoreSuperconductDamageSequence(
            double targetEndTime,
            Map<CharacterId, FixedDamageSequenceState> ownerStates) {
        superconductTargetDamageCooldownEndTime = Double.isFinite(targetEndTime)
                ? targetEndTime
                : -1.0;
        restoreFixedDamageSequenceStates(
                superconductOwnerDamageSequenceStates,
                ownerStates,
                SUPERCONDUCT_OWNER_DAMAGE_LIMIT);
    }

    /**
     * Attempts to accept Shatter reaction damage.
     *
     * @param ownerId character that owns the Shatter reaction
     * @param currentTime simulator time in seconds
     * @return {@code true} when this Shatter may deal damage
     */
    public boolean tryStartShatterDamageSequence(
            CharacterId ownerId, double currentTime) {
        if (ownerId == null || !Double.isFinite(currentTime)
                || currentTime + TIMING_EPSILON
                        < shatterTargetDamageCooldownEndTime) {
            return false;
        }
        shatterTargetDamageCooldownEndTime =
                currentTime + SHATTER_TARGET_DAMAGE_GCD;
        return advanceFixedDamageSequence(
                shatterOwnerDamageSequenceStates,
                ownerId,
                currentTime,
                SHATTER_OWNER_SEQUENCE_WINDOW,
                SHATTER_OWNER_DAMAGE_LIMIT);
    }

    /** Returns the target-wide Shatter damage cooldown end time. */
    public double getShatterTargetDamageCooldownEndTime() {
        return shatterTargetDamageCooldownEndTime;
    }

    /** Returns a defensive copy of owner-specific Shatter sequence state. */
    public Map<CharacterId, FixedDamageSequenceState>
            copyShatterOwnerDamageSequenceStates() {
        return new EnumMap<>(shatterOwnerDamageSequenceStates);
    }

    /** Restores both dimensions of Shatter damage-sequence state. */
    public void restoreShatterDamageSequence(
            double targetEndTime,
            Map<CharacterId, FixedDamageSequenceState> ownerStates) {
        shatterTargetDamageCooldownEndTime = Double.isFinite(targetEndTime)
                ? targetEndTime
                : -1.0;
        restoreFixedDamageSequenceStates(
                shatterOwnerDamageSequenceStates,
                ownerStates,
                SHATTER_OWNER_DAMAGE_LIMIT);
    }

    private boolean advanceFixedDamageSequence(
            Map<CharacterId, FixedDamageSequenceState> ownerStates,
            CharacterId ownerId,
            double currentTime,
            double windowDuration,
            int damageLimit) {
        FixedDamageSequenceState state = ownerStates.get(ownerId);
        if (state == null
                || currentTime + TIMING_EPSILON >= state.windowEndTime) {
            ownerStates.put(
                    ownerId,
                    new FixedDamageSequenceState(
                            currentTime + windowDuration, 1));
            return true;
        }

        int attemptCount = Math.min(damageLimit + 1, state.attemptCount + 1);
        ownerStates.put(
                ownerId,
                new FixedDamageSequenceState(
                        state.windowEndTime, attemptCount));
        return attemptCount <= damageLimit;
    }

    private void restoreFixedDamageSequenceStates(
            Map<CharacterId, FixedDamageSequenceState> targetStates,
            Map<CharacterId, FixedDamageSequenceState> sourceStates,
            int damageLimit) {
        targetStates.clear();
        if (sourceStates == null) {
            return;
        }
        for (Map.Entry<CharacterId, FixedDamageSequenceState> entry
                : sourceStates.entrySet()) {
            CharacterId ownerId = entry.getKey();
            FixedDamageSequenceState state = entry.getValue();
            if (ownerId != null && state != null
                    && Double.isFinite(state.windowEndTime)
                    && state.attemptCount > 0) {
                targetStates.put(
                        ownerId,
                        new FixedDamageSequenceState(
                                state.windowEndTime,
                                Math.min(damageLimit + 1, state.attemptCount)));
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

    /** Attempts to accept Swirl damage for one owner and Swirled Element. */
    public boolean tryStartSwirlDamageSequence(
            CharacterId ownerId, Element swirledElement, double currentTime) {
        if (ownerId == null || !isSwirlElement(swirledElement)
                || !Double.isFinite(currentTime)) {
            return false;
        }
        double targetEndTime = swirlTargetDamageCooldownEndTimes.getOrDefault(
                swirledElement, -1.0);
        if (currentTime + TIMING_EPSILON < targetEndTime) {
            return false;
        }
        swirlTargetDamageCooldownEndTimes.put(
                swirledElement, currentTime + SWIRL_TARGET_DAMAGE_GCD);
        Map<CharacterId, FixedDamageSequenceState> ownerStates =
                swirlOwnerDamageSequenceStates.computeIfAbsent(
                        swirledElement,
                        ignored -> new EnumMap<>(CharacterId.class));
        return advanceFixedDamageSequence(
                ownerStates,
                ownerId,
                currentTime,
                SWIRL_OWNER_SEQUENCE_WINDOW,
                SWIRL_OWNER_DAMAGE_LIMIT);
    }

    /** Returns a defensive copy of per-element Swirl target boundaries. */
    public Map<Element, Double> copySwirlTargetDamageCooldownEndTimes() {
        return new EnumMap<>(swirlTargetDamageCooldownEndTimes);
    }

    /** Returns a deep defensive copy of Swirl owner sequence state. */
    public Map<Element, Map<CharacterId, FixedDamageSequenceState>>
            copySwirlOwnerDamageSequenceStates() {
        Map<Element, Map<CharacterId, FixedDamageSequenceState>> copy =
                new EnumMap<>(Element.class);
        for (Map.Entry<Element, Map<CharacterId, FixedDamageSequenceState>> entry
                : swirlOwnerDamageSequenceStates.entrySet()) {
            copy.put(entry.getKey(), new EnumMap<>(entry.getValue()));
        }
        return copy;
    }

    /** Restores both dimensions of per-element Swirl damage state. */
    public void restoreSwirlDamageSequence(
            Map<Element, Double> targetEndTimes,
            Map<Element, Map<CharacterId, FixedDamageSequenceState>> ownerStates) {
        swirlTargetDamageCooldownEndTimes.clear();
        if (targetEndTimes != null) {
            for (Map.Entry<Element, Double> entry : targetEndTimes.entrySet()) {
                if (isSwirlElement(entry.getKey()) && entry.getValue() != null
                        && Double.isFinite(entry.getValue())) {
                    swirlTargetDamageCooldownEndTimes.put(
                            entry.getKey(), entry.getValue());
                }
            }
        }

        swirlOwnerDamageSequenceStates.clear();
        if (ownerStates == null) {
            return;
        }
        for (Map.Entry<Element, Map<CharacterId, FixedDamageSequenceState>> entry
                : ownerStates.entrySet()) {
            if (!isSwirlElement(entry.getKey())) {
                continue;
            }
            Map<CharacterId, FixedDamageSequenceState> restored =
                    new EnumMap<>(CharacterId.class);
            restoreFixedDamageSequenceStates(
                    restored, entry.getValue(), SWIRL_OWNER_DAMAGE_LIMIT);
            if (!restored.isEmpty()) {
                swirlOwnerDamageSequenceStates.put(entry.getKey(), restored);
            }
        }
    }

    private boolean isSwirlElement(Element element) {
        return element == Element.PYRO
                || element == Element.HYDRO
                || element == Element.ELECTRO
                || element == Element.CRYO;
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

    /** Returns the latest standard Electro-Charged tick payload. */
    public StandardElectroChargedState getStandardElectroChargedState() {
        return standardElectroChargedState;
    }

    /** Replaces the next standard Electro-Charged tick payload. */
    public void updateStandardElectroChargedState(
            CharacterId ownerId, double preResistanceDamage) {
        if (ownerId == null || !Double.isFinite(preResistanceDamage)
                || preResistanceDamage < 0.0) {
            standardElectroChargedState = null;
            return;
        }
        standardElectroChargedState = new StandardElectroChargedState(
                ownerId, preResistanceDamage);
    }

    /** Clears standard Electro-Charged ownership when its sequence finishes. */
    public void clearStandardElectroChargedState() {
        standardElectroChargedState = null;
    }

    /** Restores a standard Electro-Charged payload from a simulator snapshot. */
    public void restoreStandardElectroChargedState(
            StandardElectroChargedState state) {
        if (state == null) {
            clearStandardElectroChargedState();
            return;
        }
        updateStandardElectroChargedState(
                state.ownerId, state.preResistanceDamage);
    }

    /** Attempts to accept target-wide standard Electro-Charged damage. */
    public boolean tryStartStandardElectroChargedDamageCooldown(
            double currentTime) {
        if (!Double.isFinite(currentTime)
                || currentTime + TIMING_EPSILON
                        < standardElectroChargedDamageCooldownEndTime) {
            return false;
        }
        standardElectroChargedDamageCooldownEndTime =
                currentTime + STANDARD_EC_DAMAGE_COOLDOWN;
        standardElectroChargedLastDamageTime = currentTime;
        return true;
    }

    /** Returns the standard Electro-Charged target cooldown end time. */
    public double getStandardElectroChargedDamageCooldownEndTime() {
        return standardElectroChargedDamageCooldownEndTime;
    }

    /** Returns the last successful standard Electro-Charged damage time. */
    public double getStandardElectroChargedLastDamageTime() {
        return standardElectroChargedLastDamageTime;
    }

    /** Restores standard Electro-Charged target damage timing. */
    public void restoreStandardElectroChargedDamageCooldown(
            double cooldownEndTime, double lastDamageTime) {
        standardElectroChargedDamageCooldownEndTime =
                Double.isFinite(cooldownEndTime) ? cooldownEndTime : -1.0;
        standardElectroChargedLastDamageTime =
                Double.isFinite(lastDamageTime) ? lastDamageTime : -1.0;
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

    /** Returns the absolute time of the next scheduled Burning damage tick. */
    public double getBurningNextTickTime() {
        return burningNextTickTime;
    }

    /** Records the absolute time of the next scheduled Burning damage tick. */
    public void setBurningNextTickTime(double burningNextTickTime) {
        this.burningNextTickTime = burningNextTickTime;
    }

    /**
     * Starts a new typed Burning generation.
     *
     * @param ownerId damage owner
     * @param preResistanceDamage damage before impact-time resistance
     * @param fuelUnits current Dendro fuel units
     * @param fuelDecayRate special fuel decay in units per second
     * @param currentTime simulator time in seconds
     * @param reactionStats latest applier's reaction-stat snapshot
     * @return immutable state, or {@code null} when the payload is invalid
     */
    public BurningState startBurning(
            CharacterId ownerId,
            double preResistanceDamage,
            double fuelUnits,
            double fuelDecayRate,
            double currentTime,
            StatsContainer reactionStats) {
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
                nextBurningGeneration++,
                2.0,
                reactionStats);
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
                burningState.generation,
                burningState.burningAuraUnits,
                burningState.reactionStats);
        burningEndTime = burningState.getEndTime();
        return burningState;
    }

    /**
     * Updates the latest Burning damage owner without replacing its fuel.
     *
     * @param ownerId new damage owner
     * @param preResistanceDamage new damage before impact-time resistance
     * @param currentTime simulator refresh time
     * @param reactionStats latest applier's reaction-stat snapshot
     * @return refreshed state, or {@code null} when no valid state remains
     */
    public BurningState refreshBurningDamage(
            CharacterId ownerId,
            double preResistanceDamage,
            double currentTime,
            StatsContainer reactionStats) {
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
                burningState.generation,
                burningState.burningAuraUnits,
                reactionStats);
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
                burningState.generation,
                burningState.burningAuraUnits,
                burningState.reactionStats);
        burningEndTime = burningState.getEndTime();
        return burningState;
    }

    /** Returns the immutable current Burning payload. */
    public BurningState getBurningState() {
        return burningState;
    }

    /**
     * Consumes the independent Burning Aura and returns trigger source gauge spent.
     *
     * @param sourceGaugeUnits available trigger gauge
     * @param modifier target-Aura consumption per source unit
     * @return consumed trigger source gauge
     */
    public double consumeBurningAura(
            double sourceGaugeUnits, double modifier) {
        if (burningState == null
                || sourceGaugeUnits <= 0.0
                || modifier <= 0.0) {
            return 0.0;
        }
        double auraConsumption = Math.min(
                burningState.burningAuraUnits,
                sourceGaugeUnits * modifier);
        double remainingAuraUnits = burningState.burningAuraUnits - auraConsumption;
        if (remainingAuraUnits <= 0.0) {
            clearBurning();
        } else {
            burningState = new BurningState(
                    burningState.ownerId,
                    burningState.preResistanceDamage,
                    burningState.fuelUnits,
                    burningState.fuelDecayRate,
                    burningState.lastUpdateTime,
                    burningState.generation,
                    remainingAuraUnits,
                    burningState.reactionStats);
        }
        return auraConsumption / modifier;
    }

    /**
     * Accepts one target-wide Burning Pyro application at the 2-second boundary.
     *
     * @param currentTime simulator time in seconds
     * @return {@code true} when this tick may apply Pyro
     */
    public boolean tryStartBurningPyroApplication(double currentTime) {
        if (currentTime < burningPyroApplicationCooldownEndTime) {
            return false;
        }
        burningPyroApplicationCooldownEndTime = currentTime + 2.0;
        return true;
    }

    /** Returns the target-wide Burning Pyro application cooldown end. */
    public double getBurningPyroApplicationCooldownEndTime() {
        return burningPyroApplicationCooldownEndTime;
    }

    /** Restores the target-wide Burning Pyro application cooldown end. */
    public void restoreBurningPyroApplicationCooldown(double endTime) {
        burningPyroApplicationCooldownEndTime = endTime;
    }

    /** Returns the next timer generation identifier. */
    public int getNextBurningGeneration() {
        return nextBurningGeneration;
    }

    /** Clears typed Burning data and invalidates existing timer generations. */
    public void clearBurning() {
        burningState = null;
        burningEndTime = -1.0;
        burningNextTickTime = -1.0;
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
                        state.generation,
                        state.burningAuraUnits,
                        state.reactionStats);
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

    /**
     * Atomically consumes up to the requested number of Verdant Dew stacks.
     *
     * @param requested non-negative maximum stack count to consume
     * @return actual consumed stack count
     */
    public int consumeVerdantDewCount(int requested) {
        if (requested < 0) {
            throw new IllegalArgumentException(
                    "Verdant Dew consumption must be non-negative");
        }
        int consumed = Math.min(requested, verdantDewCount);
        verdantDewCount -= consumed;
        return consumed;
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

    /** Attempts to accept Dendro Core damage in the current target window. */
    public boolean tryStartDendroCoreDamage(double currentTime) {
        if (!Double.isFinite(currentTime)) {
            return false;
        }
        recentDendroCoreDamageTimes.removeIf(
                time -> !isActiveDendroCoreDamageTime(time, currentTime));
        if (recentDendroCoreDamageTimes.size() >= DENDRO_CORE_DAMAGE_LIMIT) {
            return false;
        }
        recentDendroCoreDamageTimes.add(currentTime);
        return true;
    }

    /** Returns a defensive copy of active Dendro Core damage timestamps. */
    public List<Double> copyRecentDendroCoreDamageTimes(double currentTime) {
        List<Double> copy = new ArrayList<>();
        for (Double time : recentDendroCoreDamageTimes) {
            if (isActiveDendroCoreDamageTime(time, currentTime)) {
                copy.add(time);
            }
        }
        return copy;
    }

    /** Restores valid active Dendro Core damage timestamps. */
    public void restoreRecentDendroCoreDamageTimes(
            List<Double> damageTimes, double currentTime) {
        recentDendroCoreDamageTimes.clear();
        if (damageTimes == null || !Double.isFinite(currentTime)) {
            return;
        }
        for (Double time : damageTimes) {
            if (recentDendroCoreDamageTimes.size() >= DENDRO_CORE_DAMAGE_LIMIT) {
                break;
            }
            if (isActiveDendroCoreDamageTime(time, currentTime)) {
                recentDendroCoreDamageTimes.add(time);
            }
        }
    }

    private boolean isActiveDendroCoreDamageTime(
            Double damageTime, double currentTime) {
        return damageTime != null
                && Double.isFinite(damageTime)
                && Double.isFinite(currentTime)
                && damageTime <= currentTime + TIMING_EPSILON
                && currentTime - damageTime + TIMING_EPSILON
                        < DENDRO_CORE_DAMAGE_WINDOW;
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

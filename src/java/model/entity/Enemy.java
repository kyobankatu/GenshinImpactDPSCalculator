package model.entity;

import model.type.StatType;
import java.util.HashMap;
import java.util.Map;

/**
 * Represents an enemy target in the combat simulation.
 *
 * <p>Tracks the enemy's level, per-element resistances, and the live elemental
 * aura state used by {@link mechanics.reaction.ReactionResult} and
 * {@link simulation.CombatSimulator} to resolve elemental reactions.
 *
 * <p>Constructed with KQM-standard 10 % resistance across all elements for
 * reproducible benchmark comparisons.
 */
public class Enemy {
    private static final double AURA_TAX = 0.8;
    private int level;
    private Map<StatType, Double> resistances; // RES for each element
    private java.util.Map<model.type.Element, AuraState> auraGauge = new HashMap<>();
    private double freezeAuraUnits = 0.0;

    private static final double INFINITE_EXPIRY = Double.POSITIVE_INFINITY;

    /**
     * Time-aware elemental aura state. The simulator still uses a simplified
     * gauge model, but runtime-applied auras now decay continuously between
     * application and expiry.
     *
     * <p>The aura value at any time is derived by linear decay from
     * {@link #units} at {@link #applicationTime} down to zero at
     * {@link #expiryTime}, using a fixed {@link #decayRate} (units per second).
     * Auras created without a finite duration (legacy {@code setAura(element,
     * units)}) use a zero decay rate and an infinite expiry, so they keep their
     * stored units until they are explicitly cleared or consumed. This preserves
     * compatibility for test fixtures and snapshot restore paths.
     */
    private static final class AuraState {
        private final model.type.Element element;
        private double units;
        private double applicationTime;
        private double expiryTime;
        private double decayRate;

        private AuraState(model.type.Element element, double units, double applicationTime, double decayRate) {
            this.element = element;
            this.units = units;
            this.applicationTime = applicationTime;
            if (!Double.isFinite(decayRate) || decayRate <= 0.0) {
                this.decayRate = 0.0;
                this.expiryTime = INFINITE_EXPIRY;
            } else {
                this.decayRate = decayRate;
                this.expiryTime = applicationTime + units / decayRate;
            }
        }

        /**
         * Returns the remaining aura units at the given simulator time after
         * continuous natural decay. Infinite-expiry auras return their stored
         * units unchanged. Queries at or before the application time return the
         * full stored units (no decay has occurred yet).
         *
         * @param currentTime simulator time in seconds to evaluate
         * @return remaining units, never negative
         */
        private double currentUnitsAt(double currentTime) {
            if (Double.isInfinite(expiryTime) || decayRate <= 0.0) {
                return units;
            }
            if (currentTime <= applicationTime) {
                return units;
            }
            double decayed = units - decayRate * (currentTime - applicationTime);
            return decayed > 0.0 ? decayed : 0.0;
        }

        /**
         * Re-bases the aura so that {@code newUnits} are present at
         * {@code currentTime}, and natural decay continues from there at the
         * original decay rate. Infinite-expiry auras keep their infinite expiry.
         *
         * @param newUnits    remaining units after a discrete consumption event
         * @param currentTime simulator time of the consumption event
         */
        private void rebase(double newUnits, double currentTime) {
            this.units = newUnits;
            this.applicationTime = currentTime;
            if (decayRate <= 0.0) {
                this.expiryTime = INFINITE_EXPIRY;
            } else {
                this.expiryTime = currentTime + newUnits / decayRate;
            }
        }

        /**
         * Re-bases the aura with a new source-selected decay rate.
         *
         * @param newUnits    aura units present after the new application
         * @param currentTime simulator time of the application
         * @param newDecayRate new decay rate in aura units per second
         */
        private void rebase(double newUnits, double currentTime, double newDecayRate) {
            this.decayRate = newDecayRate;
            rebase(newUnits, currentTime);
        }
    }

    /**
     * Creates an enemy at the given level with KQM-standard 10 % resistance
     * applied to all eight elements.
     *
     * @param level enemy level used in the DEF multiplier formula
     */
    public Enemy(int level) {
        this.level = level;
        this.resistances = new HashMap<>();
        // Default KQMS Resistance
        // 10% All Res
        setRes(StatType.PYRO_DMG_BONUS, 0.10);
        setRes(StatType.HYDRO_DMG_BONUS, 0.10);
        setRes(StatType.CRYO_DMG_BONUS, 0.10);
        setRes(StatType.ELECTRO_DMG_BONUS, 0.10);
        setRes(StatType.ANEMO_DMG_BONUS, 0.10);
        setRes(StatType.GEO_DMG_BONUS, 0.10);
        setRes(StatType.DENDRO_DMG_BONUS, 0.10);
        setRes(StatType.PHYSICAL_DMG_BONUS, 0.10);
    }

    /**
     * Sets the elemental aura gauge for the given element to {@code units}.
     * If {@code units} is zero or negative the aura is removed entirely.
     *
     * @param element element whose aura gauge to set
     * @param units   gauge units to assign (must be positive to register an aura)
     */
    public void setAura(model.type.Element element, double units) {
        if (units <= 0) {
            auraGauge.remove(element);
        } else {
            auraGauge.put(element, new AuraState(element, units, 0.0, 0.0));
        }
    }

    /**
     * Sets a runtime elemental aura with natural expiry derived from gauge units.
     *
     * <p>Current combat logic uses common 1U/2U/4U durations in a simplified
     * form: {@code 6 + gauge * 5} seconds. This gives 1U=11s, 2U=16s, and
     * 4U=26s, matching the broad relative behavior needed by rotation tests.
     *
     * @param element     element whose aura gauge to set
     * @param units       gauge units to assign
     * @param currentTime current simulator time in seconds
     */
    public void setAura(model.type.Element element, double units, double currentTime) {
        if (units <= 0) {
            auraGauge.remove(element);
        } else {
            double duration = auraDuration(units);
            auraGauge.put(element, new AuraState(element, units, currentTime, units / duration));
        }
    }

    /**
     * Applies a standard elemental source as a finite enemy aura.
     *
     * <p>The source gauge is taxed by {@value #AURA_TAX}. A fresh aura receives
     * the source-selected decay rate derived from {@code 2.5 * U + 7} seconds.
     * Same-element applications replace the current amount only when the newly
     * taxed gauge is greater. Non-Pyro auras retain their first decay rate until
     * exhausted; Pyro adopts the new source rate whenever its amount changes.
     *
     * <p>Non-finite/non-positive values and elements that cannot persist as
     * enemy auras are ignored. This method does not model innate/self auras or
     * reaction-created special state.
     *
     * @param element          persistent aura element to apply
     * @param sourceGaugeUnits source gauge before Aura Tax
     * @param currentTime      simulator application time in seconds
     * @return {@code true} when a valid persistent elemental source was handled
     */
    public boolean applyAura(
            model.type.Element element,
            double sourceGaugeUnits,
            double currentTime) {
        if (!canPersistAsAura(element)
                || !Double.isFinite(sourceGaugeUnits)
                || sourceGaugeUnits <= 0.0
                || !Double.isFinite(currentTime)) {
            return false;
        }

        double taxedUnits = sourceGaugeUnits * AURA_TAX;
        double sourceDuration = 2.5 * sourceGaugeUnits + 7.0;
        double sourceDecayRate = taxedUnits / sourceDuration;
        AuraState state = auraGauge.get(element);
        if (state == null || state.currentUnitsAt(currentTime) <= 0.0) {
            auraGauge.put(element, new AuraState(element, taxedUnits, currentTime, sourceDecayRate));
            return true;
        }

        double currentUnits = state.currentUnitsAt(currentTime);
        if (taxedUnits <= currentUnits) {
            return true;
        }
        if (element == model.type.Element.PYRO) {
            state.rebase(taxedUnits, currentTime, sourceDecayRate);
        } else {
            state.rebase(taxedUnits, currentTime);
        }
        return true;
    }

    /**
     * Reduces the gauge of the given element by {@code decay} units, ignoring
     * natural decay.
     *
     * <p>This no-argument form is a compatibility wrapper that subtracts from
     * the stored units at the aura's last application time. Mechanic decisions
     * that depend on simulation time should call
     * {@link #reduceAura(model.type.Element, double, double)} so the decayed
     * current value is consumed instead.
     *
     * @param element element whose gauge to reduce
     * @param decay   amount to subtract from the current gauge
     */
    public void reduceAura(model.type.Element element, double decay) {
        if (auraGauge.containsKey(element)) {
            AuraState state = auraGauge.get(element);
            double current = state.units;
            double next = current - decay;
            if (next <= 0) {
                auraGauge.remove(element);
            } else {
                state.units = next;
            }
        }
    }

    /**
     * Reduces the gauge of the given element by {@code decay} units, consuming
     * the value remaining after continuous natural decay up to
     * {@code currentTime}.
     *
     * <p>If the remaining value after consumption drops to zero or below, the
     * aura is removed. Otherwise natural decay continues from the new remaining
     * value at the aura's original decay rate.
     *
     * @param element     element whose gauge to reduce
     * @param decay       amount to subtract from the decayed current gauge
     * @param currentTime current simulator time in seconds
     */
    public void reduceAura(model.type.Element element, double decay, double currentTime) {
        AuraState state = auraGauge.get(element);
        if (state == null) {
            return;
        }
        double current = state.currentUnitsAt(currentTime);
        double next = current - decay;
        if (next <= 0.0) {
            auraGauge.remove(element);
        } else {
            state.rebase(next, currentTime);
        }
    }

    /**
     * Returns the stored gauge units for the given element without applying
     * natural decay, or {@code 0.0} if no aura of that element is applied.
     *
     * <p>This no-argument form is a compatibility wrapper. Mechanic decisions
     * that depend on simulation time should call
     * {@link #getAuraUnits(model.type.Element, double)} to read the decayed
     * current value.
     *
     * @param element element to query
     * @return stored aura gauge units at the last application time
     */
    public double getAuraUnits(model.type.Element element) {
        AuraState state = auraGauge.get(element);
        return state != null ? state.units : 0.0;
    }

    /**
     * Returns the remaining gauge units for the given element at
     * {@code currentTime} after continuous natural decay, or {@code 0.0} if no
     * aura of that element is applied.
     *
     * @param element     element to query
     * @param currentTime current simulator time in seconds
     * @return decayed remaining aura gauge units, never negative
     */
    public double getAuraUnits(model.type.Element element, double currentTime) {
        AuraState state = auraGauge.get(element);
        return state != null ? state.currentUnitsAt(currentTime) : 0.0;
    }

    /**
     * Returns the absolute time when an active Aura will naturally decay to zero.
     *
     * <p>Non-decaying compatibility Auras and elements with no active future
     * expiry return positive infinity. Discrete consumption and same-element
     * application update the returned time through the Aura state's existing
     * rebase policy.
     *
     * @param element element whose natural expiry is requested
     * @param currentTime simulator time used to exclude already expired Auras
     * @return absolute natural expiry, or positive infinity when no finite future
     *         expiry exists
     */
    public double getAuraExpiryTime(model.type.Element element, double currentTime) {
        AuraState state = auraGauge.get(element);
        if (state == null
                || state.expiryTime <= currentTime
                || state.currentUnitsAt(currentTime) <= 0.0) {
            return INFINITE_EXPIRY;
        }
        return state.expiryTime;
    }

    /**
     * Removes runtime auras that have expired by the given simulator time,
     * either because their expiry has passed or their decayed value has reached
     * zero.
     *
     * @param currentTime current simulator time in seconds
     */
    public void updateAuras(double currentTime) {
        auraGauge.entrySet().removeIf(entry -> {
            AuraState state = entry.getValue();
            return state.expiryTime <= currentTime || state.currentUnitsAt(currentTime) <= 0.0;
        });
    }

    /**
     * Returns the primary active aura element.
     * If both {@link model.type.Element#HYDRO} and
     * {@link model.type.Element#ELECTRO} are simultaneously present
     * (Electro-Charged state), {@code HYDRO} is returned as the nominal
     * primary. Returns {@code null} when no aura is active.
     *
     * @return primary aura element, or {@code null} if the enemy has no aura
     */
    public model.type.Element getPrimaryAura() {
        // Return first non-zero aura (Simulate single aura for now unless EC)
        // EC (Electro-Charged) allows Hydro+Electro.
        if (auraGauge.containsKey(model.type.Element.HYDRO) && auraGauge.containsKey(model.type.Element.ELECTRO)) {
            return model.type.Element.HYDRO; // Return one of them? Or special EC state?
            // For now, simple return first key.
        }
        if (auraGauge.isEmpty())
            return null;
        return auraGauge.keySet().iterator().next();
    }

    /**
     * Returns the primary active aura element using decayed current units at the
     * given simulator time. Auras that have naturally decayed to zero are
     * ignored. As with {@link #getPrimaryAura()}, a simultaneous
     * {@link model.type.Element#HYDRO}/{@link model.type.Element#ELECTRO}
     * (Electro-Charged) state returns {@code HYDRO} as the nominal primary.
     *
     * @param currentTime current simulator time in seconds
     * @return primary aura element, or {@code null} if no aura remains
     */
    public model.type.Element getPrimaryAura(double currentTime) {
        boolean hydro = getAuraUnits(model.type.Element.HYDRO, currentTime) > 0.0;
        boolean electro = getAuraUnits(model.type.Element.ELECTRO, currentTime) > 0.0;
        if (hydro && electro) {
            return model.type.Element.HYDRO;
        }
        for (model.type.Element element : auraGauge.keySet()) {
            if (getAuraUnits(element, currentTime) > 0.0) {
                return element;
            }
        }
        return null;
    }

    /**
     * Returns the set of all elements currently applied as an aura on this enemy
     * without applying natural decay.
     *
     * <p>This no-argument form is a compatibility wrapper. Mechanic decisions
     * that depend on simulation time should call
     * {@link #getActiveAuras(double)} so naturally decayed auras are excluded.
     *
     * @return snapshot set of active aura elements
     */
    public java.util.Set<model.type.Element> getActiveAuras() {
        return new java.util.HashSet<>(auraGauge.keySet());
    }

    /**
     * Returns the set of elements whose decayed gauge is still positive at the
     * given simulator time.
     *
     * @param currentTime current simulator time in seconds
     * @return snapshot set of active aura elements after natural decay
     */
    public java.util.Set<model.type.Element> getActiveAuras(double currentTime) {
        java.util.Set<model.type.Element> active = new java.util.HashSet<>();
        for (model.type.Element element : auraGauge.keySet()) {
            if (getAuraUnits(element, currentTime) > 0.0) {
                active.add(element);
            }
        }
        return active;
    }

    /**
     * Returns a snapshot copy of the full aura gauge map (element -&gt; units)
     * using stored units without applying natural decay.
     *
     * <p>This no-argument form is a compatibility wrapper. Snapshot consumers
     * that depend on simulation time should call {@link #getAuraMap(double)} to
     * read decayed current values.
     *
     * @return copy of the aura gauge map
     */
    public java.util.Map<model.type.Element, Double> getAuraMap() {
        java.util.Map<model.type.Element, Double> snapshot = new java.util.HashMap<>();
        for (Map.Entry<model.type.Element, AuraState> entry : auraGauge.entrySet()) {
            snapshot.put(entry.getKey(), entry.getValue().units);
        }
        return snapshot;
    }

    /**
     * Returns a snapshot copy of the aura gauge map (element -&gt; units) using
     * decayed current units at the given simulator time. Elements whose decayed
     * value has reached zero are omitted.
     *
     * @param currentTime current simulator time in seconds
     * @return copy of the aura gauge map after natural decay
     */
    public java.util.Map<model.type.Element, Double> getAuraMap(double currentTime) {
        java.util.Map<model.type.Element, Double> snapshot = new java.util.HashMap<>();
        for (Map.Entry<model.type.Element, AuraState> entry : auraGauge.entrySet()) {
            double current = entry.getValue().currentUnitsAt(currentTime);
            if (current > 0.0) {
                snapshot.put(entry.getKey(), current);
            }
        }
        return snapshot;
    }

    /**
     * Captures the full per-element aura state for simulator snapshot/rollback.
     *
     * <p>Each entry holds {@code {units, applicationTime, decayRate}} so that
     * continuous natural decay can be resumed exactly after a restore, rather
     * than being flattened to a non-decaying value.
     *
     * @return a copy of the aura state keyed by element
     */
    public java.util.Map<model.type.Element, double[]> captureAuraState() {
        java.util.Map<model.type.Element, double[]> snapshot = new java.util.HashMap<>();
        for (Map.Entry<model.type.Element, AuraState> entry : auraGauge.entrySet()) {
            AuraState state = entry.getValue();
            snapshot.put(entry.getKey(),
                    new double[] { state.units, state.applicationTime, state.decayRate });
        }
        return snapshot;
    }

    /**
     * Restores aura state previously captured by {@link #captureAuraState()},
     * replacing any current auras. Future natural decay resumes from the restored
     * values, preserving the original decay behavior across rollback.
     *
     * @param state captured aura state keyed by element; {@code null} clears all auras
     */
    public void restoreAuraState(java.util.Map<model.type.Element, double[]> state) {
        auraGauge.clear();
        if (state == null) {
            return;
        }
        for (Map.Entry<model.type.Element, double[]> entry : state.entrySet()) {
            double[] values = entry.getValue();
            if (values == null || values.length < 3 || values[0] <= 0.0) {
                continue;
            }
            auraGauge.put(entry.getKey(),
                    new AuraState(entry.getKey(), values[0], values[1], values[2]));
        }
    }

    private boolean canPersistAsAura(model.type.Element element) {
        return element == model.type.Element.PYRO
                || element == model.type.Element.HYDRO
                || element == model.type.Element.CRYO
                || element == model.type.Element.ELECTRO
                || element == model.type.Element.DENDRO;
    }

    private double auraDuration(double units) {
        double normalized = Math.max(0.0, units);
        return 6.0 + normalized * 5.0;
    }

    /**
     * Sets the simplified Freeze Aura gauge used by single-target reaction logic.
     *
     * @param units Freeze Aura units; non-positive values clear the state
     */
    public void setFreezeAura(double units) {
        freezeAuraUnits = Math.max(0.0, units);
    }

    /**
     * Reduces the simplified Freeze Aura gauge.
     *
     * @param units amount to remove
     */
    public void reduceFreezeAura(double units) {
        freezeAuraUnits = Math.max(0.0, freezeAuraUnits - units);
    }

    /**
     * Clears the simplified Freeze Aura state.
     */
    public void clearFreezeAura() {
        freezeAuraUnits = 0.0;
    }

    /**
     * Returns whether the enemy currently has Freeze Aura.
     *
     * @return {@code true} if Frozen
     */
    public boolean isFrozen() {
        return freezeAuraUnits > 0.0;
    }

    /**
     * Returns the current simplified Freeze Aura units.
     *
     * @return Freeze Aura units
     */
    public double getFreezeAuraUnits() {
        return freezeAuraUnits;
    }

    /**
     * Sets the resistance value for the given element type.
     * Uses the {@link StatType} DMG_BONUS key as the resistance map key
     * (e.g. {@link StatType#PYRO_DMG_BONUS} for Pyro resistance).
     *
     * @param elementType the {@link StatType} DMG_BONUS key representing the element
     * @param value       resistance value as a decimal (e.g. {@code 0.10} for 10 %)
     */
    public void setRes(StatType elementType, double value) {
        resistances.put(elementType, value);
    }

    /**
     * Returns the resistance value for the given element type.
     * Defaults to 10 % ({@code 0.10}) if the element has no explicit entry.
     *
     * @param elementType the {@link StatType} DMG_BONUS key for the element
     * @return resistance as a decimal
     */
    public double getRes(StatType elementType) {
        // Map element bonus type (e.g. PYRO_DMG_BONUS) to resistance?
        // Or just allow passing the element type directly if StatType included generic
        // elements.
        // Assuming the input is the DMG_BONUS type or a specific element type.
        // For simplicity, let's use the DMG_BONUS type Key for resistance too.
        return resistances.getOrDefault(elementType, 0.10); // Default 10%
    }

    /**
     * Returns the enemy's level, used in the DEF multiplier formula.
     *
     * @return enemy level
     */
    public int getLevel() {
        return level;
    }
}

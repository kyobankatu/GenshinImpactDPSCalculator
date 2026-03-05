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
    private int level;
    private Map<StatType, Double> resistances; // RES for each element
    private java.util.Map<model.type.Element, Double> auraGauge = new HashMap<>();

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
            auraGauge.put(element, units);
        }
    }

    /**
     * Reduces the gauge of the given element by {@code decay} units.
     * If the gauge drops to zero or below the element's aura is removed.
     *
     * @param element element whose gauge to reduce
     * @param decay   amount to subtract from the current gauge
     */
    public void reduceAura(model.type.Element element, double decay) {
        if (auraGauge.containsKey(element)) {
            double current = auraGauge.get(element);
            double next = current - decay;
            if (next <= 0) {
                auraGauge.remove(element);
            } else {
                auraGauge.put(element, next);
            }
        }
    }

    /**
     * Returns the remaining gauge units for the given element, or {@code 0.0}
     * if no aura of that element is currently applied.
     *
     * @param element element to query
     * @return remaining aura gauge units
     */
    public double getAuraUnits(model.type.Element element) {
        return auraGauge.getOrDefault(element, 0.0);
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
     * Returns the set of all elements currently applied as an aura on this enemy.
     *
     * @return snapshot set of active aura elements
     */
    public java.util.Set<model.type.Element> getActiveAuras() {
        return new java.util.HashSet<>(auraGauge.keySet());
    }

    /**
     * Returns a snapshot copy of the full aura gauge map (element -> units).
     *
     * @return copy of the aura gauge map
     */
    public java.util.Map<model.type.Element, Double> getAuraMap() {
        return new java.util.HashMap<>(auraGauge);
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

package model.stats;

import model.type.StatType;

import java.util.EnumMap;

/**
 * A container that holds numeric values for each {@link StatType}.
 *
 * <p>
 * All stats are stored as raw doubles keyed by {@link StatType}. Values
 * default to {@code 0.0} when absent. The container is intentionally simple:
 * no layered base/percent separation is performed here — callers use the
 * composite helper methods ({@link #getTotalAtk()}, etc.) to resolve final
 * values from the stored base, percent, and flat components.
 *
 * <p>
 * Instances are not thread-safe and are expected to be used within a single
 * simulation thread.
 */
public class StatsContainer {
    private final EnumMap<StatType, Double> stats = new EnumMap<>(StatType.class);

    /**
     * Adds {@code value} to the current value of {@code type}, initialising
     * it to {@code 0.0} first if absent. Use this method when multiple sources
     * contribute to the same stat (e.g. different artifact pieces).
     *
     * @param type  the stat to modify
     * @param value amount to add (may be negative)
     */
    public void add(StatType type, double value) {
        stats.merge(type, value, Double::sum);
    }

    /**
     * Overwrites the stored value for {@code type} with {@code value},
     * regardless of any previously accumulated value. Use this for stats that
     * should not stack (e.g. base stats set once from the character CSV).
     *
     * @param type  the stat to overwrite
     * @param value new value to store
     */
    public void set(StatType type, double value) {
        stats.put(type, value);
    }

    /**
     * Returns the stored value for {@code type}, or {@code 0.0} if no value
     * has been recorded.
     *
     * @param type stat to retrieve
     * @return stored value, or {@code 0.0}
     */
    public double get(StatType type) {
        return stats.getOrDefault(type, 0.0);
    }

    /**
     * Computes and returns the final ATK value using the standard Genshin
     * formula: {@code BASE_ATK * (1 + ATK_PERCENT) + ATK_FLAT}.
     *
     * @return total ATK
     */
    public double getTotalAtk() {
        double base = get(StatType.BASE_ATK);
        double pct = get(StatType.ATK_PERCENT);
        double flat = get(StatType.ATK_FLAT);
        return base * (1.0 + pct) + flat;
    }

    /**
     * Computes and returns the final HP value:
     * {@code BASE_HP * (1 + HP_PERCENT) + HP_FLAT}.
     *
     * @return total HP
     */
    public double getTotalHp() {
        double base = get(StatType.BASE_HP);
        double pct = get(StatType.HP_PERCENT);
        double flat = get(StatType.HP_FLAT);
        return base * (1.0 + pct) + flat;
    }

    /**
     * Computes and returns the final DEF value:
     * {@code BASE_DEF * (1 + DEF_PERCENT) + DEF_FLAT}.
     *
     * @return total DEF
     */
    public double getTotalDef() {
        double base = get(StatType.BASE_DEF);
        double pct = get(StatType.DEF_PERCENT);
        double flat = get(StatType.DEF_FLAT);
        return base * (1.0 + pct) + flat;
    }

    /**
     * Creates and returns a new {@code StatsContainer} whose values are the
     * element-wise sum of this container and {@code other}. Neither operand is
     * modified. If {@code other} is {@code null} a copy of this container is
     * returned.
     *
     * @param other container to add; may be {@code null}
     * @return new container with merged values
     */
    public StatsContainer merge(StatsContainer other) {
        StatsContainer result = new StatsContainer();
        // Copy current stats
        this.stats.forEach(result::add);
        // Add other stats
        if (other != null) {
            other.stats.forEach(result::add);
        }
        return result;
    }

    /**
     * Iterates over all entries in this container, invoking {@code action} for
     * each (StatType, Double) pair that has been explicitly set or added.
     *
     * @param action callback to invoke for each entry
     */
    public void forEach(java.util.function.BiConsumer<StatType, Double> action) {
        stats.forEach(action);
    }
}

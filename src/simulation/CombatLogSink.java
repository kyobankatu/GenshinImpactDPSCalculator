package simulation;

import java.util.Map;

import model.type.Element;

/**
 * Abstraction for writing timeline-style combat logs during a simulation run.
 *
 * <p>This isolates the simulator from any specific logging backend so callers can
 * substitute a different sink for tests, analysis, or alternate report pipelines.
 */
public interface CombatLogSink {
    /**
     * Writes a combat log record without an attached formula string.
     *
     * @param time           simulation time in seconds
     * @param actor          actor or synthetic source name
     * @param action         action or event label
     * @param damage         primary damage value for the record
     * @param reactionType   reaction label, or {@code "None"} if not applicable
     * @param reactionDamage additional reaction damage associated with the record
     * @param enemyAura      snapshot of the enemy aura map at the time of logging
     */
    default void log(double time, String actor, String action, double damage,
            String reactionType, double reactionDamage,
            Map<Element, Double> enemyAura) {
        log(time, actor, action, damage, reactionType, reactionDamage, enemyAura, null);
    }

    /**
     * Writes a combat log record, optionally including a damage-formula debug string.
     *
     * @param time           simulation time in seconds
     * @param actor          actor or synthetic source name
     * @param action         action or event label
     * @param damage         primary damage value for the record
     * @param reactionType   reaction label, or {@code "None"} if not applicable
     * @param reactionDamage additional reaction damage associated with the record
     * @param enemyAura      snapshot of the enemy aura map at the time of logging
     * @param formula        optional formula debug text, or {@code null}
     */
    void log(double time, String actor, String action, double damage,
            String reactionType, double reactionDamage,
            Map<Element, Double> enemyAura, String formula);
}

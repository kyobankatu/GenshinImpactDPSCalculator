package simulation.runtime;

import java.util.Map;

import model.type.Element;
import simulation.CombatLogSink;
import visualization.VisualLogger;

/**
 * Default {@link CombatLogSink} implementation backed by {@link VisualLogger}.
 */
public class VisualLoggerSink implements CombatLogSink {
    private final VisualLogger visualLogger;

    /**
     * Creates a sink backed by the shared {@link VisualLogger} singleton.
     */
    public VisualLoggerSink() {
        this(VisualLogger.getInstance());
    }

    /**
     * Creates a sink backed by the provided {@link VisualLogger} instance.
     *
     * @param visualLogger logger used to store simulation records
     */
    public VisualLoggerSink(VisualLogger visualLogger) {
        this.visualLogger = visualLogger;
    }

    /**
     * Writes a combat record into the wrapped {@link VisualLogger}.
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
    @Override
    public void log(double time, String actor, String action, double damage,
            String reactionType, double reactionDamage,
            Map<Element, Double> enemyAura, String formula) {
        visualLogger.log(time, actor, action, damage, reactionType, reactionDamage, enemyAura, formula);
    }
}

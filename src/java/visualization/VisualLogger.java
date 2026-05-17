package visualization;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import model.type.Element;

/**
 * Singleton sink that buffers {@link SimulationRecord} entries produced during
 * a simulation run for later rendering by {@link HtmlReportGenerator}.
 */
public class VisualLogger {
    private static VisualLogger instance;
    private List<SimulationRecord> records = new ArrayList<>();

    private VisualLogger() {
    }

    /**
     * Returns the process-wide singleton instance, creating it on first call.
     *
     * @return the shared logger instance
     */
    public static VisualLogger getInstance() {
        if (instance == null) {
            instance = new VisualLogger();
        }
        return instance;
    }

    /**
     * Discards all buffered records. Call at the start of a fresh simulation run.
     */
    public void clear() {
        records.clear();
    }

    /**
     * Logs a simulation event without a formula debug string.
     *
     * @param time           simulation time in seconds
     * @param actor          actor display name
     * @param action         action label
     * @param damage         outgoing damage (0 if none)
     * @param reactionType   reaction name or {@code "None"}
     * @param reactionDamage separate transformative reaction damage
     * @param enemyAura      enemy aura snapshot after the action
     */
    public void log(double time, String actor, String action, double damage,
            String reactionType, double reactionDamage,
            Map<Element, Double> enemyAura) {
        log(time, actor, action, damage, reactionType, reactionDamage, enemyAura, null);
    }

    /**
     * Logs a simulation event, including a formula debug tooltip.
     *
     * @param time           simulation time in seconds
     * @param actor          actor display name
     * @param action         action label
     * @param damage         outgoing damage (0 if none)
     * @param reactionType   reaction name or {@code "None"}
     * @param reactionDamage separate transformative reaction damage
     * @param enemyAura      enemy aura snapshot after the action; defensively copied
     * @param formula        optional formula debug string
     */
    public void log(double time, String actor, String action, double damage,
            String reactionType, double reactionDamage,
            Map<Element, Double> enemyAura, String formula) {
        records.add(new SimulationRecord(time, actor, action, damage, reactionType, reactionDamage,
                new java.util.HashMap<>(enemyAura), formula));
    }

    /**
     * Returns the live list of buffered records.
     *
     * @return mutable backing list of records (do not modify externally)
     */
    public List<SimulationRecord> getRecords() {
        return records;
    }
}

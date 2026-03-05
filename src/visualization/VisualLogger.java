package visualization;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import model.type.Element;

public class VisualLogger {
    private static VisualLogger instance;
    private List<SimulationRecord> records = new ArrayList<>();

    private VisualLogger() {
    }

    public static VisualLogger getInstance() {
        if (instance == null) {
            instance = new VisualLogger();
        }
        return instance;
    }

    public void clear() {
        records.clear();
    }

    public void log(double time, String actor, String action, double damage,
            String reactionType, double reactionDamage,
            Map<Element, Double> enemyAura) {
        log(time, actor, action, damage, reactionType, reactionDamage, enemyAura, null);
    }

    public void log(double time, String actor, String action, double damage,
            String reactionType, double reactionDamage,
            Map<Element, Double> enemyAura, String formula) {
        records.add(new SimulationRecord(time, actor, action, damage, reactionType, reactionDamage,
                new java.util.HashMap<>(enemyAura), formula));
    }

    public List<SimulationRecord> getRecords() {
        return records;
    }
}

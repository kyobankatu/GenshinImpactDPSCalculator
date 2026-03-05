package visualization;

import java.util.Map;
import model.type.Element;

public class SimulationRecord {
    public double time;
    public String actor;
    public String action;
    public double damage;
    public String reactionType; // "Vaporize", "Overload", etc.
    public double reactionDamage; // If separate (Transformative)
    public Map<Element, Double> enemyAura; // Snapshot of aura AFTER action
    public String formula; // Debug formula

    public SimulationRecord(double time, String actor, String action, double damage,
            String reactionType, double reactionDamage,
            Map<Element, Double> enemyAura) {
        this(time, actor, action, damage, reactionType, reactionDamage, enemyAura, null);
    }

    public SimulationRecord(double time, String actor, String action, double damage,
            String reactionType, double reactionDamage,
            Map<Element, Double> enemyAura, String formula) {
        this.time = time;
        this.actor = actor;
        this.action = action;
        this.damage = damage;
        this.reactionType = reactionType;
        this.reactionDamage = reactionDamage;
        this.enemyAura = enemyAura;
        this.formula = formula;
    }
}

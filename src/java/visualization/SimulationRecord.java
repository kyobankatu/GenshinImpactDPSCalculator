package visualization;

import java.util.Map;
import model.type.Element;

/**
 * Single combat event captured for the timeline view of the HTML report.
 *
 * <p>Records are produced by {@link VisualLogger} during simulation and consumed
 * by {@link ReportHtmlRenderer} when rendering the timeline cards.
 */
public class SimulationRecord {
    /** Simulation time of the event in seconds. */
    public double time;
    /** Display name of the acting character (or "Swap"-style label). */
    public String actor;
    /** Action label such as the skill or normal attack name. */
    public String action;
    /** Outgoing damage for the event; zero if the event is not a hit. */
    public double damage;
    /** Reaction type name (e.g. {@code "Vaporize"}, {@code "Overload"}), or {@code "None"}. */
    public String reactionType;
    /** Separately tracked reaction damage (e.g. transformative reactions). */
    public double reactionDamage;
    /** Snapshot of enemy aura values keyed by {@link Element} after the action. */
    public Map<Element, Double> enemyAura;
    /** Optional formula debug string used as a tooltip in the report. */
    public String formula;

    /**
     * Creates a record without a formula debug string.
     *
     * @param time           simulation time in seconds
     * @param actor          actor display name
     * @param action         action label
     * @param damage         outgoing damage (0 if none)
     * @param reactionType   reaction name or {@code "None"}
     * @param reactionDamage separate transformative reaction damage
     * @param enemyAura      enemy aura snapshot after the action
     */
    public SimulationRecord(double time, String actor, String action, double damage,
            String reactionType, double reactionDamage,
            Map<Element, Double> enemyAura) {
        this(time, actor, action, damage, reactionType, reactionDamage, enemyAura, null);
    }

    /**
     * Creates a fully populated record.
     *
     * @param time           simulation time in seconds
     * @param actor          actor display name
     * @param action         action label
     * @param damage         outgoing damage (0 if none)
     * @param reactionType   reaction name or {@code "None"}
     * @param reactionDamage separate transformative reaction damage
     * @param enemyAura      enemy aura snapshot after the action
     * @param formula        optional formula debug string
     */
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

package mechanics.formula;

import java.util.List;

import mechanics.buff.Buff;
import model.entity.Character;
import model.entity.Enemy;
import model.stats.StatsContainer;
import simulation.CombatSimulator;
import simulation.action.AttackAction;

/**
 * Strategy for resolving final damage for a single {@link AttackAction}.
 */
public interface DamageStrategy {
    double calculate(
            Character attacker,
            Enemy target,
            AttackAction action,
            List<Buff> activeBuffs,
            double currentTime,
            double reactionMultiplier,
            CombatSimulator sim);

    double calculate(
            Character attacker,
            Enemy target,
            AttackAction action,
            List<Buff> activeBuffs,
            StatsContainer preResolvedStats,
            double currentTime,
            double reactionMultiplier,
            CombatSimulator sim);
}

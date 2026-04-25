package mechanics.formula;

import java.util.List;
import java.util.Map;

import mechanics.buff.Buff;
import model.entity.Character;
import model.entity.Enemy;
import model.stats.StatsContainer;
import model.type.ActionType;
import model.type.StatType;
import simulation.CombatSimulator;
import simulation.action.AttackAction;

/**
 * Standard Genshin damage path used by non-Lunar attacks.
 */
final class StandardDamageStrategy implements DamageStrategy {
    @Override
    public double calculate(
            Character attacker,
            Enemy target,
            AttackAction action,
            List<Buff> activeBuffs,
            double currentTime,
            double reactionMultiplier,
            CombatSimulator sim) {
        return calculate(attacker, target, action, activeBuffs, null, currentTime, reactionMultiplier, sim);
    }

    @Override
    public double calculate(
            Character attacker,
            Enemy target,
            AttackAction action,
            List<Buff> activeBuffs,
            StatsContainer preResolvedStats,
            double currentTime,
            double reactionMultiplier,
            CombatSimulator sim) {

        StatsContainer stats = DamageCalculator.resolveStats(attacker, action, activeBuffs, preResolvedStats, currentTime);

        double baseStatValue = action.getScalingStatValue(stats);
        double mv = action.getDamagePercent();
        double flatDmg = stats.get(StatType.FLAT_DMG_BONUS);
        double baseDmg = (baseStatValue * mv) + flatDmg;

        double dmgBonus = stats.get(StatType.DMG_BONUS_ALL)
                + stats.get(action.getElement().getBonusStatType())
                + (action.getBonusStat() != null ? stats.get(action.getBonusStat()) : 0.0);

        if (action.getExtraBonuses() != null) {
            for (Map.Entry<StatType, Double> entry : action.getExtraBonuses().entrySet()) {
                dmgBonus += entry.getValue();
            }
        }

        double critRate = stats.get(StatType.CRIT_RATE);
        if (action.getActionType() == ActionType.BURST || action.isCountsAsBurstDmg()) {
            critRate += stats.get(StatType.BURST_CRIT_RATE);
        } else if (action.getActionType() == ActionType.SKILL || action.isCountsAsSkillDmg()) {
            critRate += stats.get(StatType.SKILL_CRIT_RATE);
        }
        critRate = Math.min(1.0, critRate);
        double critDmg = stats.get(StatType.CRIT_DMG);
        double critMulti = 1.0 + (critRate * critDmg);

        int attackerLevel = 90;
        double totalDefIgnore = stats.get(StatType.DEF_IGNORE) + action.getDefenseIgnore();
        if (totalDefIgnore > 1.0) {
            totalDefIgnore = 1.0;
        }
        double defMulti = DamageCalculator.calculateDefMulti(attackerLevel, target.getLevel(), totalDefIgnore);
        double resMulti = ResistanceCalculator.calculateMultiplier(target, stats, action.getElement());

        double damage = baseDmg * (1 + dmgBonus) * critMulti * reactionMultiplier * defMulti * resMulti;
        DamageCalculator.notifyDamageHooks(attacker, action, currentTime, sim, damage);
        return damage;
    }
}

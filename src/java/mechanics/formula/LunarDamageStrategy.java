package mechanics.formula;

import java.util.List;

import mechanics.buff.Buff;
import model.entity.Character;
import model.entity.Enemy;
import model.stats.StatsContainer;
import model.type.StatType;
import simulation.CombatSimulator;
import simulation.action.AttackAction;

/**
 * Custom Lunar damage path used by Lunar-considered attacks.
 */
final class LunarDamageStrategy implements DamageStrategy {
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

        StatsContainer stats = DamageCalculator.resolveTargetStats(
                attacker, target, action, activeBuffs, preResolvedStats,
                currentTime, sim);

        StatType scaling = action.getScalingStat();
        if (scaling == null) {
            scaling = StatType.BASE_ATK;
        }

        double statVal;
        switch (scaling) {
            case BASE_ATK:
                statVal = stats.getTotalAtk();
                break;
            case BASE_HP:
                statVal = stats.getTotalHp();
                break;
            case BASE_DEF:
                statVal = stats.getTotalDef();
                break;
            default:
                statVal = stats.get(scaling);
                break;
        }

        double mv = action.getDamagePercent();
        double totalBaseBonus = stats.get(StatType.LUNAR_BASE_BONUS);
        double lunarMultiplier = 1.0 + stats.get(StatType.LUNAR_MULTIPLIER);
        double uniqueBonus = stats.get(StatType.LUNAR_UNIQUE_BONUS);
        double baseSection = 3.0 * (statVal * mv) * (1.0 + totalBaseBonus) * (1.0 + uniqueBonus);

        double em = stats.get(StatType.ELEMENTAL_MASTERY);
        double reactionBonus = (6.0 * em) / (em + 2000.0);
        double statGearBonus = getLunarReactionSpecificBonus(stats, action);
        double burstBonus = stats.get(StatType.LUNAR_REACTION_DMG_BONUS_ALL);
        double ecBonus = stats.get(StatType.LUNAR_MOONSIGN_BONUS);
        double totalGearBonus = statGearBonus + burstBonus + ecBonus;
        double multiplier = 1.0 + reactionBonus + totalGearBonus;

        double cr = stats.get(StatType.CRIT_RATE);
        double cd = stats.get(StatType.CRIT_DMG) + stats.get(StatType.LUNAR_REACTION_CRIT_DMG);
        double critMult = 1.0 + (Math.min(cr, 1.0) * cd);
        double resMult = ResistanceCalculator.calculateMultiplier(
                target, activeBuffs, currentTime, action.getElement());

        try {
            String formula = String.format(
                    "3.0 * (%.0f * %.2f) * (1 + %.3f + %.2f) * (1 + %.3f + (%.3f + %.3f + %.3f)) * %.3f * %.3f * %.3f (LunarMult)",
                    statVal, mv,
                    totalBaseBonus, uniqueBonus,
                    reactionBonus, statGearBonus, burstBonus, ecBonus,
                    critMult, resMult, lunarMultiplier);
            action.setDebugFormula(formula);

            if (sim.isLoggingEnabled()) {
                System.out.println("[FormulaDebug] " + action.getName() + ": " + formula);
                System.out.println(String.format(
                        "[FormulaValues] Stat:%.0f MV:%.2f BaseI:%.3f Uniq:%.2f React:%.3f Gear:%.3f Burst:%.3f Moonsign:%.3f Crit:%.3f Res:%.3f LunarMult:%.3f",
                        statVal, mv, totalBaseBonus, uniqueBonus, reactionBonus, statGearBonus, burstBonus,
                        ecBonus, critMult, resMult, lunarMultiplier));
            }
        } catch (Exception e) {
            System.err.println("[FormulaError] Failed to format: " + e.getMessage());
            e.printStackTrace();
        }

        return baseSection * multiplier * critMult * resMult * lunarMultiplier;
    }

    private double getLunarReactionSpecificBonus(StatsContainer stats, AttackAction action) {
        if (action.getLunarReactionType() == null) {
            return stats.get(StatType.LUNAR_CHARGED_DMG_BONUS);
        }
        switch (action.getLunarReactionType()) {
            case BLOOM:
                return stats.get(StatType.LUNAR_BLOOM_DMG_BONUS);
            case CRYSTALLIZE:
                return stats.get(StatType.LUNAR_CRYSTALLIZE_DMG_BONUS);
            case CHARGED:
            default:
                return stats.get(StatType.LUNAR_CHARGED_DMG_BONUS);
        }
    }
}

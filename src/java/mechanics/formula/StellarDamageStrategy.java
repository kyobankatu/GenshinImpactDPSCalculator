package mechanics.formula;

import java.util.List;

import mechanics.buff.Buff;
import model.entity.Character;
import model.entity.Enemy;
import model.stats.StatsContainer;
import model.type.Element;
import model.type.StatType;
import simulation.CombatSimulator;
import simulation.action.AttackAction;

/** Resolves direct Stellar damage without ordinary elemental DMG Bonus. */
final class StellarDamageStrategy implements DamageStrategy {
    @Override
    public double calculate(
            Character attacker,
            Enemy target,
            AttackAction action,
            List<Buff> activeBuffs,
            double currentTime,
            double reactionMultiplier,
            CombatSimulator sim) {
        return calculate(
                attacker, target, action, activeBuffs, null,
                currentTime, reactionMultiplier, sim);
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
                attacker,
                target,
                action,
                activeBuffs,
                preResolvedStats,
                currentTime,
                sim);
        double scalingValue = resolveScalingValue(stats, action.getScalingStat());
        double baseDamage = scalingValue * action.getDamagePercent();
        boolean conduct = action.getStellarReactionType()
                == AttackAction.StellarReactionType.CONDUCT;

        StatType damageBonusType = conduct
                ? StatType.STELLAR_CONDUCT_DMG_BONUS
                : StatType.STELLAR_SWIRL_DMG_BONUS;
        StatType baseBonusType = conduct
                ? StatType.STELLAR_CONDUCT_BASE_DMG_BONUS
                : StatType.STELLAR_SWIRL_BASE_DMG_BONUS;
        StatType critRateType = conduct
                ? StatType.STELLAR_CONDUCT_CRIT_RATE
                : StatType.STELLAR_SWIRL_CRIT_RATE;
        StatType critDamageType = conduct
                ? StatType.STELLAR_CONDUCT_CRIT_DMG
                : StatType.STELLAR_SWIRL_CRIT_DMG;
        StatType specialBonusType = conduct
                ? StatType.STELLAR_CONDUCT_SPECIAL_DMG_BONUS
                : StatType.STELLAR_SWIRL_SPECIAL_DMG_BONUS;
        StatType multiplierType = conduct
                ? StatType.STELLAR_CONDUCT_MULTIPLIER
                : StatType.STELLAR_SWIRL_MULTIPLIER;

        double em = stats.get(StatType.ELEMENTAL_MASTERY);
        double emBonus = (6.0 * em) / (2000.0 + em);
        double stellarSection = 1.0 + emBonus + stats.get(damageBonusType);
        double baseSection = 1.0 + stats.get(baseBonusType);

        double critRate = Math.min(
                1.0,
                stats.get(StatType.CRIT_RATE) + stats.get(critRateType));
        double critDamage = stats.get(StatType.CRIT_DMG)
                + stats.get(critDamageType)
                + getElementCritDamage(stats, action.getElement());
        double critMultiplier = 1.0 + critRate * critDamage;

        double defenseMultiplier = DamageCalculator.calculateDefMulti(
                90,
                target.getLevel(),
                stats.get(StatType.ENEMY_DEF_REDUCTION),
                stats.get(StatType.DEF_IGNORE) + action.getDefenseIgnore());
        double resistanceMultiplier = ResistanceCalculator.calculateMultiplier(
                target, activeBuffs, currentTime, action.getElement());
        double specialMultiplier = 1.0 + stats.get(specialBonusType);
        double finalMultiplier = 1.0 + stats.get(multiplierType);

        return baseDamage
                * baseSection
                * stellarSection
                * critMultiplier
                * specialMultiplier
                * defenseMultiplier
                * resistanceMultiplier
                * finalMultiplier;
    }

    private double resolveScalingValue(StatsContainer stats, StatType scalingStat) {
        if (scalingStat == null || scalingStat == StatType.BASE_ATK) {
            return stats.getTotalAtk();
        }
        if (scalingStat == StatType.BASE_HP) {
            return stats.getTotalHp();
        }
        if (scalingStat == StatType.BASE_DEF) {
            return stats.getTotalDef();
        }
        return stats.get(scalingStat);
    }

    private double getElementCritDamage(StatsContainer stats, Element element) {
        if (element == Element.CRYO) {
            return stats.get(StatType.CRYO_CRIT_DMG);
        }
        if (element == Element.ELECTRO) {
            return stats.get(StatType.ELECTRO_CRIT_DMG);
        }
        if (element == Element.ANEMO) {
            return stats.get(StatType.ANEMO_CRIT_DMG);
        }
        return 0.0;
    }
}

package mechanics.formula;

import model.stats.StatsContainer;
import model.type.StatType;
import model.entity.Character;
import model.entity.Enemy;
import java.util.List;
import mechanics.buff.Buff;

/**
 * Computes outgoing damage for a single attack action.
 *
 * <p>
 * Two independent code paths are implemented inside {@link #calculateDamage}:
 * <ol>
 * <li><b>Lunar path</b> – entered when {@code action.isLunarConsidered()} is
 * {@code true}. Uses the custom Lunar formula:
 * {@code 3 * (Stat * MV) * (1 + BaseBonus + UniqueBonus) * (1 + ReactionBonus + GearBonus) * Crit * Res * ColumbinaMultiplier}</li>
 * <li><b>Standard path</b> – follows the official Genshin Impact formula:
 * {@code (Stat * MV + FlatDmg) * (1 + DmgBonus%) * Crit * ReactionMulti * Def * Res}</li>
 * </ol>
 */
public class DamageCalculator {

    /**
     * Calculates the final damage dealt by {@code action}.
     *
     * <strong>Lunar path (action.isLunarConsidered() == true)</strong>
     * <ol>
     * <li>Resolve stats – snapshot or live, then apply active buffs.</li>
     * <li>Base section:
     * {@code 3 * (statVal * MV) * (1 + LUNAR_BASE_BONUS) * (1 + LUNAR_UNIQUE_BONUS)}</li>
     * <li>Reaction multiplier: {@code 1 + (6*EM)/(EM+2000) + GearBonuses} where
     * gear covers
     * {@code LUNAR_CHARGED_DMG_BONUS}, {@code LUNAR_REACTION_DMG_BONUS_ALL}, and
     * {@code LUNAR_MOONSIGN_BONUS}.</li>
     * <li>Crit: {@code 1 + min(CR,1) * CD}</li>
     * <li>Res: via {@link #calculateResMulti}.</li>
     * <li>Columbina multiplier: {@code 1 + LUNAR_MULTIPLIER} – applied last as an
     * independent
     * final multiplier, scaling with {@code (EM / 2000) * 1.5}.</li>
     * </ol>
     *
     * <strong>Standard path</strong>
     * <ol>
     * <li>Resolve stats – snapshot or live, then apply active buffs.</li>
     * <li>Base damage: {@code (scalingStat * MV) + FLAT_DMG_BONUS}</li>
     * <li>DMG Bonus%: sum of {@code DMG_BONUS_ALL}, element-specific bonus, action
     * bonus stat,
     * and any extra bonuses attached to the action.</li>
     * <li>Crit: {@code 1 + min(CR,1) * CD}; burst/skill crit-rate overrides are
     * added before
     * clamping.</li>
     * <li>Reaction multiplier passed in from {@code CombatSimulator}.</li>
     * <li>Defense: via {@link #calculateDefMulti} at attacker level 90.</li>
     * <li>Resistance: via {@link #calculateResMulti} after accumulating
     * element-specific shred.</li>
     * </ol>
     *
     * <p>
     * After computing damage on either path, weapon and artifact {@code onDamage}
     * hooks are
     * fired so stacking/proc mechanics can update their internal state.
     *
     * @param attacker           the attacking character
     * @param target             the enemy being hit
     * @param action             the attack action containing MV, element, scaling
     *                           stat, etc.
     * @param activeBuffs        team and self buffs currently active (may be
     *                           {@code null})
     * @param currentTime        simulation time in seconds at the moment of the hit
     * @param reactionMultiplier amplifying reaction multiplier pre-computed by the
     *                           simulator
     *                           (1.0 if no amplifying reaction)
     * @param sim                the running {@link simulation.CombatSimulator}
     *                           instance
     * @return final damage value after all multipliers
     */
    public static double calculateDamage(
            model.entity.Character attacker,
            model.entity.Enemy target,
            simulation.action.AttackAction action,
            java.util.List<mechanics.buff.Buff> activeBuffs,
            double currentTime,
            double reactionMultiplier,
            simulation.CombatSimulator sim) {

        // System.out.println("[DC_DEBUG] Enter calculateDamage for " +
        // action.getName());
        // System.out.println("[DC_DEBUG] UseSnapshot: " + action.isUseSnapshot() + ",
        // Buffs: "
        // + (activeBuffs == null ? "null" : activeBuffs.size()));

        // 1. Formula Debugger Setup
        StringBuilder formulaDebug = new StringBuilder();
        if (action.isLunarConsidered()) {
            // Lunar-Charged Considered Damage Formula
            // Val = (ATK * MV) * (1 + EM_React + Gear_React) * Crit * Res
            // Note: MV in action should already include: 3.0 * RawMV * (1+BaseBonus) *
            // (1+UniqueBonus)

            // 1. Stats
            // Snapshot check
            StatsContainer s = action.isUseSnapshot() ? attacker.getSnapshot()
                    : attacker.getEffectiveStats(currentTime);

            // Apply Buffs (Missing Link!) - FIXED
            if (!action.isUseSnapshot() && activeBuffs != null) {
                // System.out.println("[DC_DEBUG] Applying " + activeBuffs.size() + " buffs for
                // " + attacker.getName()
                // + " (Lunar Path)");
                for (mechanics.buff.Buff b : activeBuffs) {
                    if (!b.isExpired(currentTime)) {
                        b.apply(s, currentTime);
                    }
                }
            }

            // 2. Base (Stat * MV)
            model.type.StatType scaling = action.getScalingStat();
            if (scaling == null)
                scaling = model.type.StatType.BASE_ATK; // Default to ATK

            double statVal = 0.0;
            switch (scaling) {
                case BASE_ATK:
                    statVal = s.getTotalAtk();
                    break;
                case BASE_HP:
                    statVal = s.getTotalHp();
                    break;
                case BASE_DEF:
                    statVal = s.getTotalDef();
                    break;
                default:
                    statVal = s.get(scaling);
                    break;
            }

            double mv = action.getDamagePercent();

            // Refactored: Base Bonus now comes from Team Buffs (Ineffa/Flins)
            double totalBaseBonus = s.get(StatType.LUNAR_BASE_BONUS);

            // Refactored: Columbina Multiplier
            double columbinaMultiplier = 1.0 + s.get(StatType.LUNAR_MULTIPLIER);

            // Unique Bonus
            double uniqueBonus = s.get(StatType.LUNAR_UNIQUE_BONUS);

            double baseSection = 3.0 * (statVal * mv) * (1.0 + totalBaseBonus) * (1.0 + uniqueBonus);

            // 3. Reaction Bonus (EM + Equipment + Burst Buffs)
            double em = s.get(StatType.ELEMENTAL_MASTERY);
            // User Correction: Lunar Reaction Coefficient is 6.0, not 16.0
            double reactionBonus = (6.0 * em) / (em + 2000.0);

            // Gear Bonuses Breakdown
            double statGearBonus = s.get(StatType.LUNAR_CHARGED_DMG_BONUS);
            double burstBonus = s.get(StatType.LUNAR_REACTION_DMG_BONUS_ALL);
            double ecBonus = s.get(StatType.LUNAR_MOONSIGN_BONUS);

            double totalGearBonus = statGearBonus + burstBonus + ecBonus;

            double multipler = 1.0 + reactionBonus + totalGearBonus;

            // 4. Crit
            double cr = s.get(StatType.CRIT_RATE);
            double cd = s.get(StatType.CRIT_DMG);
            double critMult = 1.0 + (Math.min(cr, 1.0) * cd);

            // 5. Res
            double rawRes = target.getRes(action.getElement().getBonusStatType());
            double resShred = s.get(StatType.RES_SHRED);
            switch (action.getElement()) {
                case PYRO:
                    resShred += s.get(StatType.PYRO_RES_SHRED);
                    break;
                case HYDRO:
                    resShred += s.get(StatType.HYDRO_RES_SHRED);
                    break;
                case CRYO:
                    resShred += s.get(StatType.CRYO_RES_SHRED);
                    break;
                case ELECTRO:
                    resShred += s.get(StatType.ELECTRO_RES_SHRED);
                    break;
                case ANEMO:
                    resShred += s.get(StatType.ANEMO_RES_SHRED);
                    break;
                case GEO:
                    resShred += s.get(StatType.GEO_RES_SHRED);
                    break;
                case DENDRO:
                    resShred += s.get(StatType.DENDRO_RES_SHRED);
                    break;
                case PHYSICAL:
                    resShred += s.get(StatType.PHYS_RES_SHRED);
                    break;
            }
            double resMult = calculateResMulti(rawRes, resShred);

            // Debug Formula Construction
            try {
                // Formula updated to show Columbina Multiplier separately
                String formula = String.format(
                        "3.0 * (%.0f * %.2f) * (1 + %.3f + %.2f) * (1 + %.3f + (%.3f + %.3f + %.3f)) * %.3f * %.3f * %.3f (Columbina)",
                        statVal, mv,
                        totalBaseBonus, uniqueBonus,
                        reactionBonus, statGearBonus, burstBonus, ecBonus,
                        critMult, resMult, columbinaMultiplier);
                action.setDebugFormula(formula);

                if (sim.isLoggingEnabled()) {
                    System.out.println("[FormulaDebug] " + action.getName() + ": " + formula);
                    System.out.println(String.format(
                            "[FormulaValues] Stat:%.0f MV:%.2f BaseI:%.3f Uniq:%.2f React:%.3f Gear:%.3f Burst:%.3f Moonsign:%.3f Crit:%.3f Res:%.3f ColMult:%.3f",
                            statVal, mv, totalBaseBonus, uniqueBonus, reactionBonus, statGearBonus, burstBonus,
                            ecBonus, critMult, resMult, columbinaMultiplier));
                }
            } catch (Exception e) {
                System.err.println("[FormulaError] Failed to format: " + e.getMessage());
                e.printStackTrace();
            }

            // Notify Artifacts of Hit (Added for Silken Moon's Serenade)
            if (attacker.getArtifacts() != null) {
                for (model.entity.ArtifactSet artifact : attacker.getArtifacts()) {
                    if (artifact != null) {
                        artifact.onDamage(sim, action,
                                baseSection * multipler * critMult * resMult * columbinaMultiplier, attacker);
                    }
                }
            }

            return baseSection * multipler * critMult * resMult * columbinaMultiplier;
        }

        // 1. Stats Collection
        StatsContainer stats;

        if (action.isUseSnapshot()) {
            stats = attacker.getSnapshot();
        } else {
            stats = attacker.getEffectiveStats(currentTime);
            // 2. Apply Dynamic/Team Buffs
            if (activeBuffs != null) {
                // System.out.println("[DC_DEBUG] Applying " + activeBuffs.size() + " buffs for
                // " + attacker.getName());
                for (Buff b : activeBuffs) {
                    if (!b.isExpired(currentTime)) { // Assuming isActive is equivalent to !isExpired
                        b.apply(stats, currentTime);
                    }
                }
            }
        }

        // 2. Base Damage
        double baseStatValue = action.getScalingStatValue(stats);
        double mv = action.getDamagePercent(); // Fixed
        double flatDmg = stats.get(StatType.FLAT_DMG_BONUS);
        double baseDmg = (baseStatValue * mv) + flatDmg;

        // 3. Dmg Bonus
        double dmgBonus = stats.get(StatType.DMG_BONUS_ALL)
                + stats.get(action.getElement().getBonusStatType())
                + (action.getBonusStat() != null ? stats.get(action.getBonusStat()) : 0.0);

        // Add extra bonuses from Action
        if (action.getExtraBonuses() != null) {
            for (java.util.Map.Entry<StatType, Double> entry : action.getExtraBonuses().entrySet()) {
                dmgBonus += entry.getValue();
            }
        }

        // 4. Crit
        // 4. Crit
        double critRate = stats.get(StatType.CRIT_RATE);
        if (action.getActionType() == model.type.ActionType.BURST || action.isCountsAsBurstDmg()) {
            critRate += stats.get(StatType.BURST_CRIT_RATE);
        } else if (action.getActionType() == model.type.ActionType.SKILL || action.isCountsAsSkillDmg()) {
            critRate += stats.get(StatType.SKILL_CRIT_RATE);
        }
        critRate = Math.min(1.0, critRate);
        double critDmg = stats.get(StatType.CRIT_DMG);
        double critMulti = 1.0 + (critRate * critDmg);

        // 5. Reaction Multiplier (Passed from Simulator)
        double finalReactionMulti = reactionMultiplier;

        // 6. Def/Res
        int attackerLevel = 90;
        double totalDefIgnore = stats.get(StatType.DEF_IGNORE) + action.getDefenseIgnore();
        if (totalDefIgnore > 1.0)
            totalDefIgnore = 1.0;
        double defMulti = calculateDefMulti(attackerLevel, target.getLevel(), totalDefIgnore);

        // Res needs Target Res - Res Shred
        // Res needs Target Res - Res Shred
        double targetRes = target.getRes(action.getElement().getBonusStatType());
        double resShred = stats.get(StatType.RES_SHRED);

        // Add specific element shred
        switch (action.getElement()) {
            case PYRO:
                resShred += stats.get(StatType.PYRO_RES_SHRED);
                break;
            case HYDRO:
                resShred += stats.get(StatType.HYDRO_RES_SHRED);
                break;
            case CRYO:
                resShred += stats.get(StatType.CRYO_RES_SHRED);
                break;
            case ELECTRO:
                resShred += stats.get(StatType.ELECTRO_RES_SHRED);
                break;
            case ANEMO:
                resShred += stats.get(StatType.ANEMO_RES_SHRED);
                break;
            case GEO:
                resShred += stats.get(StatType.GEO_RES_SHRED);
                break;
            case DENDRO:
                resShred += stats.get(StatType.DENDRO_RES_SHRED);
                break;
            case PHYSICAL:
                resShred += stats.get(StatType.PHYS_RES_SHRED);
                break;
        }

        double resMulti = calculateResMulti(targetRes, resShred);

        double damage = baseDmg * (1 + dmgBonus) * critMulti * finalReactionMulti * defMulti * resMulti;

        // Notify Weapon of Hit (for Stacking mechanics like Wolf-Fang)
        if (attacker.getWeapon() != null) {
            attacker.getWeapon().onDamage(attacker, action, currentTime, sim);
        }

        // Notify Artifacts of Hit
        if (attacker.getArtifacts() != null) {
            for (model.entity.ArtifactSet artifact : attacker.getArtifacts()) {
                if (artifact != null) {
                    artifact.onDamage(sim, action, damage, attacker);
                }
            }
        }

        return damage;
    }

    /**
     * Computes the enemy defense damage multiplier using the standard Genshin
     * formula.
     *
     * <p>
     * Formula:
     * {@code (charLevel + 100) / [(enemyLevel + 100) * (1 - defIgnore) + (charLevel + 100)]}
     *
     * @param attackerLevel character level (typically 90)
     * @param enemyLevel    enemy level
     * @param defIgnore     total DEF ignore ratio clamped to [0, 1]
     * @return defense multiplier in the range (0, 1]
     */
    private static double calculateDefMulti(int attackerLevel, int enemyLevel, double defIgnore) {
        double charFactor = attackerLevel + 100.0;
        double enemyFactor = (enemyLevel + 100.0) * (1.0 - defIgnore);
        return charFactor / (charFactor + enemyFactor);
    }

    /**
     * Computes the resistance damage multiplier after applying resistance shred.
     *
     * <p>
     * The three-region piecewise function mirrors the official game:
     * <ul>
     * <li>{@code finalRes < 0}: {@code 1 - finalRes / 2}</li>
     * <li>{@code 0 <= finalRes < 0.75}: {@code 1 - finalRes}</li>
     * <li>{@code finalRes >= 0.75}: {@code 1 / (1 + 4 * finalRes)}</li>
     * </ul>
     *
     * @param baseRes  enemy base resistance for the relevant element (e.g. 0.10 for
     *                 10%)
     * @param resShred total resistance shred accumulated from all sources
     * @return resistance multiplier to apply to outgoing damage
     */
    public static double calculateResMulti(double baseRes, double resShred) {
        double finalRes = baseRes - resShred;
        if (finalRes < 0) {
            return 1.0 - (finalRes / 2.0);
        } else if (finalRes < 0.75) {
            return 1.0 - finalRes;
        } else {
            return 1.0 / (1.0 + 4.0 * finalRes);
        }
    }
}

package mechanics.formula;

import model.stats.StatsContainer;
import model.type.StatType;
import model.entity.Character;
import model.entity.DamageTriggeredWeaponEffect;
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
    private static final DamageStrategy STANDARD_STRATEGY = new StandardDamageStrategy();
    private static final DamageStrategy LUNAR_STRATEGY = new LunarDamageStrategy();

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
        DamageStrategy strategy = action.isLunarConsidered() ? LUNAR_STRATEGY : STANDARD_STRATEGY;
        return strategy.calculate(attacker, target, action, activeBuffs, currentTime, reactionMultiplier, sim);
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
    static double calculateDefMulti(int attackerLevel, int enemyLevel, double defIgnore) {
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
        return ResistanceCalculator.calculateResMulti(baseRes, resShred);
    }

    static StatsContainer resolveStats(
            Character attacker,
            simulation.action.AttackAction action,
            List<Buff> activeBuffs,
            double currentTime) {
        StatsContainer stats = action.isUseSnapshot() ? attacker.getSnapshot() : attacker.getEffectiveStats(currentTime);
        if (!action.isUseSnapshot() && activeBuffs != null) {
            for (Buff buff : activeBuffs) {
                if (!buff.isExpired(currentTime)) {
                    buff.apply(stats, currentTime);
                }
            }
        }
        return stats;
    }

    static void notifyDamageHooks(
            Character attacker,
            simulation.action.AttackAction action,
            double currentTime,
            simulation.CombatSimulator sim,
            double damage) {
        if (attacker.getWeapon() instanceof DamageTriggeredWeaponEffect) {
            ((DamageTriggeredWeaponEffect) attacker.getWeapon()).onDamage(attacker, action, currentTime, sim);
        }

        if (attacker.getArtifacts() != null) {
            for (model.entity.ArtifactSet artifact : attacker.getArtifacts()) {
                if (artifact != null) {
                    artifact.onDamage(sim, action, damage, attacker);
                }
            }
        }
    }
}

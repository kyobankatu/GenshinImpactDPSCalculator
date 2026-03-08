package model.weapon;

import model.entity.Weapon;
import model.entity.Character;
import model.stats.StatsContainer;
import model.type.StatType;
import model.type.ActionType;
import model.type.WeaponType;
import simulation.CombatSimulator;
import simulation.action.AttackAction;

/**
 * Sunny Morning Sleep-In (5-star Catalyst).
 *
 * <p><b>Base stats (Lv 90):</b> 542 Base ATK, 265 Elemental Mastery (substat).
 *
 * <p><b>Bathhouses, Hawks, and Narukami (R1):</b>
 * <ul>
 *   <li>EM +120 for 6 s after the wielder triggers a Swirl reaction.</li>
 *   <li>EM +96 for 9 s after the wielder's Elemental Skill hits an opponent.</li>
 *   <li>EM +32 for 30 s after the wielder's Elemental Burst hits an opponent.</li>
 * </ul>
 *
 * <p>Each buff is independent and can be active simultaneously.
 * The Swirl condition is detected via {@link CombatSimulator.ReactionListener}
 * (registered on first action), since Swirl damage bypasses
 * {@link mechanics.formula.DamageCalculator} and is not visible to
 * {@link #onDamage}. Skill and Burst buffs are applied through
 * {@link #onDamage} by checking the action's {@link ActionType}.
 */
public class SunnyMorningSleepIn extends Weapon implements CombatSimulator.ReactionListener {

    private static final double EM_SWIRL = 120.0;
    private static final double DURATION_SWIRL = 6.0;
    private static final double EM_SKILL = 96.0;
    private static final double DURATION_SKILL = 9.0;
    private static final double EM_BURST = 32.0;
    private static final double DURATION_BURST = 30.0;

    private boolean registeredListener = false;
    private Character ownerRef = null;

    /**
     * Constructs Sunny Morning Sleep-In with Lv 90 base stats.
     */
    public SunnyMorningSleepIn() {
        super("SunnyMorningSleepIn", new StatsContainer());
        StatsContainer s = this.getStats();
        s.add(StatType.BASE_ATK, 542);
        s.add(StatType.ELEMENTAL_MASTERY, 265);
        this.weaponType = WeaponType.CATALYST;
    }

    /**
     * Registers the weapon as a {@link CombatSimulator.ReactionListener} on the
     * first action the wielder performs.
     *
     * @param user the character wielding this weapon
     * @param key  action key string
     * @param sim  the active combat simulator
     */
    @Override
    public void onAction(Character user, String key, CombatSimulator sim) {
        if (!registeredListener) {
            ownerRef = user;
            sim.addReactionListener(this);
            registeredListener = true;
        }
    }

    /**
     * Applies the Skill EM +96 (9 s) or Burst EM +32 (30 s) buff when the
     * wielder's Elemental Skill or Burst hits an opponent.
     * The buff duration is refreshed on each subsequent hit of the same type.
     *
     * @param user        the character who dealt the damage
     * @param action      the attack action that triggered the damage event
     * @param currentTime simulation time in seconds at the damage event
     * @param sim         the active combat simulator
     */
    @Override
    public void onDamage(Character user, AttackAction action, double currentTime,
            CombatSimulator sim) {
        if (action.getActionType() == ActionType.SKILL) {
            user.removeBuff("SunnyMorningSleepIn: Skill EM");
            user.addBuff(new mechanics.buff.Buff("SunnyMorningSleepIn: Skill EM", DURATION_SKILL, currentTime) {
                @Override
                protected void applyStats(StatsContainer stats, double currentTime) {
                    stats.add(StatType.ELEMENTAL_MASTERY, EM_SKILL);
                }
            });
        } else if (action.getActionType() == ActionType.BURST) {
            user.removeBuff("SunnyMorningSleepIn: Burst EM");
            user.addBuff(new mechanics.buff.Buff("SunnyMorningSleepIn: Burst EM", DURATION_BURST, currentTime) {
                @Override
                protected void applyStats(StatsContainer stats, double currentTime) {
                    stats.add(StatType.ELEMENTAL_MASTERY, EM_BURST);
                }
            });
        }
    }

    /**
     * Applies the Swirl EM +120 buff for 6 s when the wielder triggers a Swirl
     * reaction. Filters events to only the weapon owner and only Swirl reactions
     * (reaction names beginning with {@code "Swirl-"}).
     *
     * @param result the reaction result
     * @param source the character who triggered the reaction
     * @param time   simulation time in seconds at the reaction
     * @param sim    the active combat simulator
     */
    @Override
    public void onReaction(mechanics.reaction.ReactionResult result, Character source, double time,
            CombatSimulator sim) {
        if (source != ownerRef || ownerRef == null) {
            return;
        }
        if (!result.getName().startsWith("Swirl-")) {
            return;
        }
        ownerRef.removeBuff("SunnyMorningSleepIn: Swirl EM");
        ownerRef.addBuff(new mechanics.buff.Buff("SunnyMorningSleepIn: Swirl EM", DURATION_SWIRL, time) {
            @Override
            protected void applyStats(StatsContainer stats, double currentTime) {
                stats.add(StatType.ELEMENTAL_MASTERY, EM_SWIRL);
            }
        });
    }
}

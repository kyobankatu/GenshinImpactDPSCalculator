package model.weapon;

import model.entity.Weapon;
import model.entity.Character;
import model.stats.StatsContainer;
import model.type.StatType;
import simulation.action.AttackAction;

/**
 * Primordial Jade Winged-Spear (5-star Polearm).
 *
 * <p>
 * <b>Base stats (Lv 90):</b> 674 Base ATK, 22.1% CRIT Rate (substat).
 *
 * <p>
 * <b>Eagle Spear of Justice (R1):</b>
 * <ul>
 * <li>On hit: gain a Eagle Spear of Justice stack (+3.2% ATK per stack), max 7
 * stacks.
 * Stack gain has a 0.3 s cooldown.</li>
 * <li>All stacks expire if no hit occurs within 6 s of the last hit.</li>
 * <li>At max 7 stacks: additionally gain +12% All DMG Bonus.</li>
 * </ul>
 */
public class PrimordialJadeWingedSpear extends Weapon {

    private int stacks = 0;
    private double lastHitTime = -999.0; // time of the most recent hit
    private double lastStackGainTime = -999.0; // time of the most recent stack gain

    private static final int MAX_STACKS = 7;
    private static final double ATK_PER_STACK = 0.032; // 3.2%
    private static final double STACK_DURATION = 6.0; // seconds before all stacks expire
    private static final double STACK_CD = 0.3; // minimum interval between stack gains
    private static final double MAX_STACK_DMG_BONUS = 0.12; // +12% All DMG at 7 stacks

    public PrimordialJadeWingedSpear() {
        super("Primordial Jade Winged-Spear", new StatsContainer());
        StatsContainer s = this.getStats();
        s.add(StatType.BASE_ATK, 674);
        s.add(StatType.CRIT_RATE, 0.221);
    }

    /**
     * On each damage instance:
     * <ol>
     * <li>If the gap since the last hit is ≥ 6 s, all stacks are reset to 0.</li>
     * <li>If the 0.3 s stack-gain cooldown has elapsed and stacks are below max,
     * gain 1 stack.</li>
     * <li>Updates {@link #lastHitTime}.</li>
     * </ol>
     */
    /**
     * On each damage instance:
     * <ol>
     *   <li>If the gap since the last hit is ≥ 6 s, all stacks are reset to 0.</li>
     *   <li>If the 0.3 s stack-gain cooldown has elapsed and stacks are below max,
     *       gain 1 stack.</li>
     *   <li>Re-registers the {@code "Eagle Spear of Justice"} {@link mechanics.buff.Buff}
     *       on the owner character with a refreshed 6 s expiration window so it
     *       appears in the Active Buffs report.</li>
     * </ol>
     */
    @Override
    public void onDamage(Character user, AttackAction action, double currentTime,
            simulation.CombatSimulator sim) {
        // Reset stacks if no hit in the last 6 s
        if (currentTime - lastHitTime >= STACK_DURATION) {
            stacks = 0;
        }

        // Gain a stack if off cooldown
        if (stacks < MAX_STACKS && currentTime - lastStackGainTime >= STACK_CD) {
            stacks++;
            lastStackGainTime = currentTime;
        }

        lastHitTime = currentTime;

        // Re-register the visible buff with a refreshed 6 s window.
        // applyStats reads stacks from the outer weapon instance at calc time.
        user.removeBuff("Eagle Spear of Justice");
        user.addBuff(new mechanics.buff.Buff("Eagle Spear of Justice", STACK_DURATION, currentTime) {
            @Override
            protected void applyStats(StatsContainer stats, double time) {
                stats.add(StatType.ATK_PERCENT, stacks * ATK_PER_STACK);
                if (stacks >= MAX_STACKS) {
                    stats.add(StatType.DMG_BONUS_ALL, MAX_STACK_DMG_BONUS);
                }
            }
        });
    }

    /**
     * Stack bonuses (+3.2% ATK per stack, +12% All DMG at 7 stacks) are applied
     * via the {@code "Eagle Spear of Justice"} Buff registered in {@link #onDamage},
     * ensuring visibility in the Active Buffs report.
     */
    @Override
    public void applyPassive(StatsContainer stats, double currentTime) {
        // Intentionally empty: all bonuses handled by the Eagle Spear of Justice Buff.
    }
}

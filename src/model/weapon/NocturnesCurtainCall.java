package model.weapon;

import model.entity.Weapon;
import model.entity.Character;
import mechanics.buff.BuffId;
import model.stats.StatsContainer;
import model.type.StatType;
import model.type.WeaponType;
import simulation.CombatSimulator;
import simulation.action.AttackAction;

/**
 * Nocturne's Curtain Call (5-star Catalyst).
 *
 * <p><b>Base stats (Lv 90):</b> 542 Base ATK, 88.2% CRIT DMG (substat).
 *
 * <p><b>Ballad of the Crossroads (R1):</b>
 * <ul>
 *   <li>Max HP +10% (constant, via {@link #applyPassive}).</li>
 *   <li>When triggering Lunar reactions or inflicting Lunar Reaction DMG on
 *       opponents, the equipping character recovers 14 Energy (at most once
 *       every 18 s, active even while off-field) and receives
 *       <em>Bountiful Sea's Sacred Wine</em> for 12 s:
 *       Max HP +14%, CRIT DMG from Lunar Reaction DMG +60%.</li>
 * </ul>
 *
 * <p>The CRIT DMG bonus is implemented via {@link StatType#LUNAR_REACTION_CRIT_DMG},
 * which is added to {@code CRIT_DMG} exclusively on the Lunar path in
 * {@link mechanics.formula.DamageCalculator}.
 */
public class NocturnesCurtainCall extends Weapon {

    private static final double HP_BASE = 0.10;
    private static final double HP_WINE = 0.14;
    private static final double LUNAR_CRIT_DMG_WINE = 0.60;
    private static final double ENERGY_RECOVER = 14;
    private static final double ENERGY_COOLDOWN = 18.0;
    private static final double WINE_DURATION = 12.0;

    private double energyCooldownNextTime = Double.NEGATIVE_INFINITY;

    /**
     * Constructs Nocturne's Curtain Call with Lv 90 base stats.
     */
    public NocturnesCurtainCall() {
        super("NocturnesCurtainCall", new StatsContainer());
        StatsContainer s = this.getStats();
        s.add(StatType.BASE_ATK, 542);
        s.add(StatType.CRIT_DMG, 0.882);
        this.weaponType = WeaponType.CATALYST;
    }

    /**
     * Applies the constant +10% Max HP bonus from the weapon passive.
     *
     * @param stats       the stats container to mutate in-place
     * @param currentTime simulation time in seconds
     */
    @Override
    public void applyPassive(StatsContainer stats, double currentTime) {
        stats.add(StatType.HP_PERCENT, HP_BASE);
    }

    /**
     * Fires the energy recovery and <em>Bountiful Sea's Sacred Wine</em> buff
     * when the equipped character inflicts Lunar Reaction DMG.
     *
     * <p>The energy recovery can trigger at most once every 18 s and fires
     * regardless of whether the character is currently on-field or off-field.
     * The Sacred Wine buff grants +14% Max HP and +60% CRIT DMG on Lunar
     * Reaction DMG for 12 s.
     *
     * @param user        the character who dealt the damage
     * @param action      the attack action that triggered the damage event
     * @param currentTime simulation time in seconds at the damage event
     * @param sim         the active combat simulator
     */
    @Override
    public void onDamage(Character user, AttackAction action, double currentTime,
            CombatSimulator sim) {
        if (!action.isLunarConsidered()) {
            return;
        }
        if (currentTime < energyCooldownNextTime) {
            return;
        }
        energyCooldownNextTime = currentTime + ENERGY_COOLDOWN;
        user.receiveFlatEnergy((int) ENERGY_RECOVER);

        user.removeBuff(BuffId.BOUNTIFUL_SEA_SACRED_WINE);
        user.addBuff(new mechanics.buff.Buff("Bountiful Sea's Sacred Wine", BuffId.BOUNTIFUL_SEA_SACRED_WINE,
                WINE_DURATION, currentTime) {
            @Override
            protected void applyStats(StatsContainer stats, double currentTime) {
                stats.add(StatType.HP_PERCENT, HP_WINE);
                stats.add(StatType.LUNAR_REACTION_CRIT_DMG, LUNAR_CRIT_DMG_WINE);
            }
        });
    }
}

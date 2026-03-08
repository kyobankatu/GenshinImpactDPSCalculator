package model.entity;

import model.stats.StatsContainer;
import model.type.WeaponType;

/**
 * Represents a weapon that can be equipped on a {@link Character}.
 *
 * <p>A weapon contributes flat stats (base ATK, substat) via {@link #getStats()},
 * and may additionally modify stats through {@link #applyPassive} and react to
 * simulation events via {@link #onAction}, {@link #onDamage}, and
 * {@link #getTeamBuffs}.
 *
 * <p>The default implementations of all hook methods are no-ops. Specific weapons
 * should subclass {@code Weapon} and override only the relevant hooks.
 *
 * <p>Each weapon subclass must set {@link #weaponType} in its constructor so that
 * the simulator can apply the correct NA energy generation rate.
 */
public class Weapon {
    private String name;
    private StatsContainer stats;

    /**
     * Weapon category; set by each subclass constructor.
     * Used to look up the expected flat energy generated per Normal/Charged Attack hit.
     */
    protected WeaponType weaponType;

    /**
     * Constructs a weapon with the given name and flat stat container.
     *
     * @param name  display name of the weapon
     * @param stats flat stats provided by the weapon (base ATK, substat, etc.)
     */
    public Weapon(String name, StatsContainer stats) {
        this.name = name;
        this.stats = stats;
    }

    /**
     * Returns the weapon's flat stat container (base ATK, substat).
     *
     * @return stats container
     */
    public StatsContainer getStats() {
        return stats;
    }

    /**
     * Returns the weapon's base ATK value.
     *
     * @return base ATK
     */
    public double getBaseAtk() {
        return stats.get(model.type.StatType.BASE_ATK);
    }

    /**
     * Returns the weapon's display name.
     *
     * @return weapon name
     */
    public String getName() {
        return name;
    }

    /**
     * Applies the weapon's passive effect to the provided stats container.
     * Called during stat compilation by {@link Character#getEffectiveStats(double)}
     * and {@link Character#getStructuralStats(double)}.
     * Default implementation is a no-op.
     *
     * @param stats       the stats container to mutate in-place
     * @param currentTime simulation time in seconds (for time-gated passives)
     */
    public void applyPassive(StatsContainer stats, double currentTime) {
        // Default: No passive
    }

    /**
     * Called by the simulator when the equipped character executes a named action
     * (e.g. {@code "E"}, {@code "Q"}). Use this hook to implement proc-based
     * weapon passives that trigger on specific actions.
     * Default implementation is a no-op.
     *
     * @param user action key string identifying the action
     * @param key  action key string
     * @param sim  the active combat simulator
     */
    public void onAction(Character user, String key, simulation.CombatSimulator sim) {
        // Default: No action logic
    }

    /**
     * Called by the simulator when the equipped character deals damage.
     * Use this hook to implement on-hit weapon passives.
     * Default implementation is a no-op.
     *
     * @param user        the character who dealt the damage
     * @param action      the attack action that triggered the damage event
     * @param currentTime simulation time in seconds at the damage event
     * @param sim         the active combat simulator
     */
    public void onDamage(Character user, simulation.action.AttackAction action, double currentTime,
            simulation.CombatSimulator sim) {
        // Default: No damage logic
    }

    /**
     * Returns team-wide buffs provided by this weapon's passive to all party
     * members. Called when team buffs are compiled for other characters.
     * Default implementation returns an empty list.
     *
     * @param owner the character who has this weapon equipped
     * @return list of team buffs, never {@code null}
     */
    /**
     * Called by the simulator when the equipped character switches off-field.
     * Use this hook to snapshot or freeze time-dependent passive state at the
     * exact moment the character leaves the field.
     * Default implementation is a no-op.
     *
     * @param user the character switching out
     * @param sim  the active combat simulator
     */
    public void onSwitchOut(Character user, simulation.CombatSimulator sim) {
        // Default: No switch-out logic
    }

    /**
     * Returns team-wide buffs provided by this weapon's passive to all party
     * members. Called when team buffs are compiled for other characters.
     * Default implementation returns an empty list.
     *
     * @param owner the character who has this weapon equipped
     * @return list of team buffs, never {@code null}
     */
    public java.util.List<mechanics.buff.Buff> getTeamBuffs(Character owner) {
        return new java.util.ArrayList<>();
    }

    /**
     * Returns the weapon category.
     *
     * @return weapon type, or {@code null} if not set by the subclass
     */
    public WeaponType getWeaponType() {
        return weaponType;
    }

    /**
     * Returns the expected flat energy generated per Normal or Charged Attack hit
     * based on the weapon type's probability table.
     *
     * <p>Uses the "start full, end full" expected-value model rather than random
     * rolls; the result is passed to {@link Character#receiveFlatEnergy} so it
     * bypasses Energy Recharge scaling.
     *
     * @return expected energy per hit (e.g. 1.0 / 6.95 for Polearm)
     */
    public double getExpectedNAEnergyPerHit() {
        if (weaponType == null) {
            return 0.0;
        }
        switch (weaponType) {
            case SWORD:    return 1.0 / 4.52;
            case BOW:      return 1.0 / 6.29;
            case CLAYMORE: return 1.0 / 4.66;
            case POLEARM:  return 1.0 / 6.95;
            case CATALYST: return 1.0 / 4.66;
            default:       return 0.0;
        }
    }
}

package model.entity;

import model.stats.StatsContainer;

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
 */
public class Weapon {
    private String name;
    private StatsContainer stats;

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
    public java.util.List<mechanics.buff.Buff> getTeamBuffs(Character owner) {
        return new java.util.ArrayList<>();
    }
}

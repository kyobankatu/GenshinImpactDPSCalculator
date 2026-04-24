package model.entity;

import model.stats.StatsContainer;
import model.type.WeaponType;

/**
 * Represents a weapon that can be equipped on a {@link Character}.
 *
 * <p>A weapon contributes flat stats (base ATK, substat) via {@link #getStats()},
 * and may additionally modify stats through {@link #applyPassive}. Optional runtime
 * behaviors are modeled through focused capability interfaces such as
 * {@link ActionTriggeredWeaponEffect}, {@link DamageTriggeredWeaponEffect},
 * {@link SwitchAwareWeaponEffect}, and {@link WeaponTeamBuffProvider}.
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

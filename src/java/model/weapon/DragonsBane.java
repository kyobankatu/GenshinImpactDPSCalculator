package model.weapon;

import model.entity.Weapon;
import model.stats.StatsContainer;
import model.type.StatType;
import model.type.WeaponType;

/**
 * Dragon's Bane polearm with an aura-conditional damage bonus passive.
 */
public class DragonsBane extends Weapon {
    /**
     * Constructs Dragon's Bane with Lv 90 base stats.
     */
    public DragonsBane() {
        super("Dragon's Bane", new StatsContainer());
        // Lv90 Base ATK 454, EM 221
        getStats().set(StatType.BASE_ATK, 454);
        getStats().set(StatType.ELEMENTAL_MASTERY, 221);
        this.weaponType = WeaponType.POLEARM;
    }

    /**
     * Applies the passive damage bonus used when the target is affected by
     * Hydro or Pyro.
     *
     * @param stats the stats container to mutate in-place
     * @param currentTime simulation time in seconds
     */
    @Override
    public void applyPassive(StatsContainer stats, double currentTime) {
        // R5: DMG vs Hydro/Pyro +36%
        // Assuming conditions met (Raiden National maintains Pyro/Hydro aura)
        stats.add(StatType.DMG_BONUS_ALL, 0.36);
    }
}

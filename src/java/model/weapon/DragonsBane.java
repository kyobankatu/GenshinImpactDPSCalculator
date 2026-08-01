package model.weapon;

import model.entity.Enemy;
import model.entity.TargetDependentWeaponEffect;
import model.entity.Weapon;
import model.stats.StatsContainer;
import model.type.Element;
import model.type.StatType;
import model.type.WeaponType;

/**
 * Dragon's Bane polearm with an aura-conditional damage bonus passive.
 */
public class DragonsBane extends Weapon implements TargetDependentWeaponEffect {
    private static final double R5_TARGET_DAMAGE_BONUS = 0.36;

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
     * Applies the R5 damage bonus when the target has a live Hydro or Pyro aura.
     *
     * <p>This enemy-state-dependent bonus is resolved for every hit and is not
     * included in persistent or snapshotted character stats.</p>
     *
     * @param stats       per-hit stats container to mutate
     * @param target      enemy being hit
     * @param currentTime simulation time in seconds used for aura decay
     */
    @Override
    public void applyTargetDependentStats(StatsContainer stats, Enemy target, double currentTime) {
        boolean affectedByHydro = target.getAuraUnits(Element.HYDRO, currentTime) > 0.0;
        boolean affectedByPyro = target.getAuraUnits(Element.PYRO, currentTime) > 0.0;
        if (affectedByHydro || affectedByPyro) {
            stats.add(StatType.DMG_BONUS_ALL, R5_TARGET_DAMAGE_BONUS);
        }
    }
}

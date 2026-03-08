package model.weapon;

import model.entity.Weapon;
import model.stats.StatsContainer;
import model.type.StatType;
import model.type.WeaponType;

public class SacrificialSword extends Weapon {
    public SacrificialSword() {
        super("Sacrificial Sword", new StatsContainer());
        StatsContainer s = this.getStats();
        s.add(StatType.BASE_ATK, 454);
        s.add(StatType.ENERGY_RECHARGE, 0.613);
        // Passive: CD Reset (handled in simulation logic if needed, primarily stats
        // here)
        this.weaponType = WeaponType.SWORD;
    }
}

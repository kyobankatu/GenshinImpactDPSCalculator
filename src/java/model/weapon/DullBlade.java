package model.weapon;

import model.entity.Weapon;
import model.stats.StatsContainer;
import model.type.StatType;
import model.type.WeaponType;

/** Dull Blade with its maximum-level one-star stats. */
public class DullBlade extends Weapon {
    /** Constructs Dull Blade at its maximum supported level. */
    public DullBlade() {
        super("Dull Blade", new StatsContainer());
        getStats().set(StatType.BASE_ATK, 185.0);
        this.weaponType = WeaponType.SWORD;
    }
}

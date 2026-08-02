package model.weapon;

import model.entity.Weapon;
import model.stats.StatsContainer;
import model.type.StatType;
import model.type.WeaponType;

/** Hunter's Bow with its maximum-level one-star stats. */
public class HuntersBow extends Weapon {
    /** Constructs Hunter's Bow at its maximum supported level. */
    public HuntersBow() {
        super("Hunter's Bow", new StatsContainer());
        getStats().set(StatType.BASE_ATK, 185.0);
        this.weaponType = WeaponType.BOW;
    }
}

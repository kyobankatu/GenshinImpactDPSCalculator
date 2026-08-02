package model.weapon;

import model.entity.Weapon;
import model.stats.StatsContainer;
import model.type.StatType;
import model.type.WeaponType;

/** Silver Sword with its maximum-level two-star stats. */
public class SilverSword extends Weapon {
    /** Constructs Silver Sword at its maximum supported level. */
    public SilverSword() {
        super("Silver Sword", new StatsContainer());
        getStats().set(StatType.BASE_ATK, 243.0);
        this.weaponType = WeaponType.SWORD;
    }
}

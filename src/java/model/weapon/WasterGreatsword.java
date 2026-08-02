package model.weapon;

import model.entity.Weapon;
import model.stats.StatsContainer;
import model.type.StatType;
import model.type.WeaponType;

/** Waster Greatsword with its maximum-level one-star stats. */
public class WasterGreatsword extends Weapon {
    /** Constructs Waster Greatsword at its maximum supported level. */
    public WasterGreatsword() {
        super("Waster Greatsword", new StatsContainer());
        getStats().set(StatType.BASE_ATK, 185.0);
        this.weaponType = WeaponType.CLAYMORE;
    }
}

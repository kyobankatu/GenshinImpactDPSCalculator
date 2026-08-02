package model.weapon;

import model.entity.Weapon;
import model.stats.StatsContainer;
import model.type.StatType;
import model.type.WeaponType;

/** Iron Point with its maximum-level two-star stats. */
public class IronPoint extends Weapon {
    /** Constructs Iron Point at its maximum supported level. */
    public IronPoint() {
        super("Iron Point", new StatsContainer());
        getStats().set(StatType.BASE_ATK, 243.0);
        this.weaponType = WeaponType.POLEARM;
    }
}

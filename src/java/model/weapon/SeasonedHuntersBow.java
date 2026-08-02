package model.weapon;

import model.entity.Weapon;
import model.stats.StatsContainer;
import model.type.StatType;
import model.type.WeaponType;

/** Seasoned Hunter's Bow with its maximum-level two-star stats. */
public class SeasonedHuntersBow extends Weapon {
    /** Constructs Seasoned Hunter's Bow at its maximum supported level. */
    public SeasonedHuntersBow() {
        super("Seasoned Hunter's Bow", new StatsContainer());
        getStats().set(StatType.BASE_ATK, 243.0);
        this.weaponType = WeaponType.BOW;
    }
}

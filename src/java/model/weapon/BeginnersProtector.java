package model.weapon;

import model.entity.Weapon;
import model.stats.StatsContainer;
import model.type.StatType;
import model.type.WeaponType;

/** Beginner's Protector with its maximum-level one-star stats. */
public class BeginnersProtector extends Weapon {
    /** Constructs Beginner's Protector at its maximum supported level. */
    public BeginnersProtector() {
        super("Beginner's Protector", new StatsContainer());
        getStats().set(StatType.BASE_ATK, 185.0);
        this.weaponType = WeaponType.POLEARM;
    }
}

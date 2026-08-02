package model.weapon;

import model.entity.Weapon;
import model.stats.StatsContainer;
import model.type.StatType;
import model.type.WeaponType;

/** Pocket Grimoire with its maximum-level two-star stats. */
public class PocketGrimoire extends Weapon {
    /** Constructs Pocket Grimoire at its maximum supported level. */
    public PocketGrimoire() {
        super("Pocket Grimoire", new StatsContainer());
        getStats().set(StatType.BASE_ATK, 243.0);
        this.weaponType = WeaponType.CATALYST;
    }
}

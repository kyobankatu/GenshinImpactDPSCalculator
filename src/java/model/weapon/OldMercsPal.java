package model.weapon;

import model.entity.Weapon;
import model.stats.StatsContainer;
import model.type.StatType;
import model.type.WeaponType;

/** Old Merc's Pal with its maximum-level two-star stats. */
public class OldMercsPal extends Weapon {
    /** Constructs Old Merc's Pal at its maximum supported level. */
    public OldMercsPal() {
        super("Old Merc's Pal", new StatsContainer());
        getStats().set(StatType.BASE_ATK, 243.0);
        this.weaponType = WeaponType.CLAYMORE;
    }
}

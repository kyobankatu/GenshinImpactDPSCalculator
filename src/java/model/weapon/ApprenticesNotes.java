package model.weapon;

import model.entity.Weapon;
import model.stats.StatsContainer;
import model.type.StatType;
import model.type.WeaponType;

/** Apprentice's Notes with its maximum-level one-star stats. */
public class ApprenticesNotes extends Weapon {
    /** Constructs Apprentice's Notes at its maximum supported level. */
    public ApprenticesNotes() {
        super("Apprentice's Notes", new StatsContainer());
        getStats().set(StatType.BASE_ATK, 185.0);
        this.weaponType = WeaponType.CATALYST;
    }
}

package model.weapon;

import model.entity.Weapon;
import model.stats.StatsContainer;
import model.type.StatType;
import model.type.WeaponType;

public class AlleyFlash extends Weapon {
    public AlleyFlash() {
        super("The Alley Flash", new StatsContainer());
        StatsContainer s = this.getStats();
        s.add(StatType.BASE_ATK, 620);
        s.add(StatType.ELEMENTAL_MASTERY, 55.0);
        s.add(StatType.DMG_BONUS_ALL, 0.12); // R1
        this.weaponType = WeaponType.SWORD;
    }
}

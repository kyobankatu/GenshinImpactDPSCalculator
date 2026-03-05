package model.weapon;

import model.entity.Weapon;
import model.stats.StatsContainer;
import model.type.StatType;

public class DragonsBane extends Weapon {
    public DragonsBane() {
        super("Dragon's Bane", new StatsContainer());
        // Lv90 Base ATK 454, EM 221
        getStats().set(StatType.BASE_ATK, 454);
        getStats().set(StatType.ELEMENTAL_MASTERY, 221);
    }

    @Override
    public void applyPassive(StatsContainer stats, double currentTime) {
        // R5: DMG vs Hydro/Pyro +36%
        // Assuming conditions met (Raiden National maintains Pyro/Hydro aura)
        stats.add(StatType.DMG_BONUS_ALL, 0.36);
    }
}

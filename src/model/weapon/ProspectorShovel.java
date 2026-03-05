package model.weapon;

import model.entity.Weapon;
import model.stats.StatsContainer;
import model.type.StatType;

public class ProspectorShovel extends Weapon {
    public ProspectorShovel() {
        super("Prospector's Shovel", new StatsContainer());
        StatsContainer s = this.getStats();
        s.add(StatType.BASE_ATK, 510);
        s.add(StatType.ATK_PERCENT, 0.413);
    }

    @Override
    public void applyPassive(StatsContainer stats, double currentTime) {
        // Refinement 5 "Swift and Sure"
        // Electro-Charged DMG +96%
        stats.add(StatType.ELECTRO_CHARGED_DMG_BONUS, 0.96);

        // Lunar-Charged DMG +24%
        // Moonsign: Ascendant Gleam: Lunar-Charged DMG +24%
        stats.add(StatType.LUNAR_CHARGED_DMG_BONUS, 0.48);
    }
}

package model.artifact;

import model.entity.ArtifactSet;
import model.stats.StatsContainer;
import model.type.StatType;

public class EmblemOfSeveredFate extends ArtifactSet {
    public EmblemOfSeveredFate(StatsContainer mainSubStats) {
        super("Emblem of Severed Fate", mainSubStats);
        // 2pc: +20% ER
        this.getStats().add(StatType.ENERGY_RECHARGE, 0.20);
    }

    @Override
    public void applyPassive(StatsContainer totalStats) {
        // 4pc: Burst Dmg = 25% of ER. Max 75%.
        double er = totalStats.get(StatType.ENERGY_RECHARGE);
        double bonus = Math.min(0.75, er * 0.25);
        totalStats.add(StatType.BURST_DMG_BONUS, bonus);
    }
}

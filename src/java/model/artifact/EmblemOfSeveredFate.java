package model.artifact;

import model.entity.ArtifactSet;
import model.stats.StatsContainer;
import model.type.StatType;

/**
 * Emblem of Severed Fate artifact set with Energy Recharge scaling burst
 * damage.
 */
public class EmblemOfSeveredFate extends ArtifactSet {
    /**
     * Constructs Emblem of Severed Fate with the supplied main/sub stats plus
     * the 2-piece Energy Recharge bonus.
     *
     * @param mainSubStats artifact main and sub stats
     */
    public EmblemOfSeveredFate(StatsContainer mainSubStats) {
        super("Emblem of Severed Fate", mainSubStats);
        // 2pc: +20% ER
        this.getStats().add(StatType.ENERGY_RECHARGE, 0.20);
    }

    /**
     * Applies the 4-piece burst damage bonus based on total Energy Recharge,
     * capped at 75%.
     *
     * @param totalStats the stats container to mutate in-place
     */
    @Override
    public void applyPassive(StatsContainer totalStats) {
        // 4pc: Burst Dmg = 25% of ER. Max 75%.
        double er = totalStats.get(StatType.ENERGY_RECHARGE);
        double bonus = Math.min(0.75, er * 0.25);
        totalStats.add(StatType.BURST_DMG_BONUS, bonus);
    }
}

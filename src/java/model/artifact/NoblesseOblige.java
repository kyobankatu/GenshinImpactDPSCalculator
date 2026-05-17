package model.artifact;

import mechanics.buff.BuffId;
import model.entity.ArtifactSet;
import model.entity.BurstTriggeredArtifactEffect;
import model.stats.StatsContainer;
import model.type.StatType;

/**
 * Noblesse Oblige artifact set with a burst damage bonus and party ATK buff.
 */
public class NoblesseOblige extends ArtifactSet implements BurstTriggeredArtifactEffect {
    /**
     * Constructs Noblesse Oblige with the supplied main/sub stats plus the
     * 2-piece burst damage bonus.
     *
     * @param mainSubStats artifact main and sub stats
     */
    public NoblesseOblige(StatsContainer mainSubStats) {
        super("Noblesse Oblige", mainSubStats);
        // 2pc: +20% Burst Dmg
        this.getStats().add(StatType.BURST_DMG_BONUS, 0.20);
    }

    // 4pc: Using Elemental Burst increases all party members' ATK by 20% for 12s.
    /**
     * Applies the 4-piece party-wide ATK buff when the wearer uses an elemental
     * burst.
     *
     * @param sim the active combat simulator
     */
    @Override
    public void onBurst(simulation.CombatSimulator sim) {
        // Check if 4pc? The simulation simplified sets to just "Noblesse Oblige" class
        // implying full set usually.
        // Assuming this object represents the active set bonus.

        sim.applyTeamBuff(new mechanics.buff.SimpleBuff("Noblesse Oblige (4pc)", BuffId.NOBLESSE_OBLIGE_4PC,
                12.0, sim.getCurrentTime(), s -> {
            s.add(StatType.ATK_PERCENT, 0.20);
        }));
        System.out.println("   [Artifact] Noblesse Oblige 4pc triggered!");
    }
}

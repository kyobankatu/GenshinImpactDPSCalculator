package model.artifact;

import model.stats.StatsContainer;
import model.type.StatType;
import mechanics.buff.Buff;
import simulation.CombatSimulator;
import simulation.CombatSimulator.Moonsign;

public class SilkenMoonsSerenade extends model.entity.ArtifactSet {

    public SilkenMoonsSerenade() {
        super("Silken Moon's Serenade", new StatsContainer());
        // 2-Piece Bonus: ER +20%
        this.getStats().add(StatType.ENERGY_RECHARGE, 0.20);
    }

    public SilkenMoonsSerenade(StatsContainer stats) {
        super("Silken Moon's Serenade", stats);
        this.getStats().add(StatType.ENERGY_RECHARGE, 0.20);
    }

    @Override
    public void onDamage(CombatSimulator sim, simulation.action.AttackAction action, double damage,
            model.entity.Character owner) {
        // 4-Piece Bonus
        // Trigger: When dealing Elemental DMG
        if (action.getElement() != model.type.Element.PHYSICAL) {
            // Effect: Gain "Gleaming Moon: Devotion" for 8s.
            // Team Buff: EM +60 (Nascent) / +120 (Ascendant).

            Moonsign sign = sim.getMoonsign();
            double emBonus = 0.0;
            if (sign == Moonsign.NASCENT_GLEAM)
                emBonus = 60.0;
            else if (sign == Moonsign.ASCENDANT_GLEAM)
                emBonus = 120.0;

            if (emBonus > 0) {
                final double finalBonus = emBonus;
                Buff devotionBuff = new Buff("Gleaming Moon: Devotion", 8.0, sim.getCurrentTime()) {
                    @Override
                    protected void applyStats(StatsContainer stats, double currentTime) {
                        stats.add(StatType.ELEMENTAL_MASTERY, finalBonus);
                    }
                };

                // Apply to ALL party members (Description: "Increases all party members' EM")
                // The *effect* is called "Gleaming Moon: Devotion".
                // It acts as a Team Buff.
                for (model.entity.Character m : sim.getPartyMembers()) {
                    // Start of Fix: Ensure uniqueness
                    if (m.hasBuff("Gleaming Moon: Devotion")) {
                        m.removeBuff("Gleaming Moon: Devotion");
                    }
                    m.addBuff(devotionBuff);
                }

                // Update Synergy
                // (Ideally we call this to refresh synergy whenever buffs change, but doing it
                // on trigger is safe enough)
                sim.updateGleamingMoonSynergy();
            }
        }
    }
}

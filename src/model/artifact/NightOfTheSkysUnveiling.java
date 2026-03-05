package model.artifact;

import model.stats.StatsContainer;
import model.type.StatType;
import mechanics.buff.Buff;
import simulation.CombatSimulator.Moonsign;

public class NightOfTheSkysUnveiling extends model.entity.ArtifactSet {

    public NightOfTheSkysUnveiling() {
        super("Night of the Sky's Unveiling", new StatsContainer());
        // 2-Piece Bonus: Increases Elemental Mastery by 80.
        this.getStats().add(StatType.ELEMENTAL_MASTERY, 80.0);
    }

    public NightOfTheSkysUnveiling(StatsContainer stats) {
        super("Night of the Sky's Unveiling", stats);
        this.getStats().add(StatType.ELEMENTAL_MASTERY, 80.0);
    }

    @Override
    public void onReaction(simulation.CombatSimulator sim, mechanics.reaction.ReactionResult result,
            model.entity.Character triggerCh, model.entity.Character owner) {
        // 4-Piece Bonus Logic
        // Trigger: "When nearby party members trigger Lunar Reactions" (implied: when
        // ANYONE triggers)
        String rType = result.getName();
        if (rType.startsWith("Lunar-")) {
            // Condition: "if the equipping character is on the field"
            boolean isOnField = (sim.getActiveCharacter() == owner);

            if (isOnField) {
                // Effect: Gain "Gleaming Moon: Intent" for 4s.
                // Self Buff: CRIT Rate.
                // 15% (Nascent) or 30% (Ascendant).

                Moonsign sign = sim.getMoonsign();
                double crBonus = 0.0;
                if (sign == Moonsign.NASCENT_GLEAM)
                    crBonus = 0.15;
                else if (sign == Moonsign.ASCENDANT_GLEAM)
                    crBonus = 0.30;
                // Assuming NONE gives 0.

                if (crBonus > 0) {
                    // Refresh (not stack) — remove any existing instance first
                    owner.removeBuff("Gleaming Moon: Intent");
                    final double finalBonus = crBonus;
                    owner.addBuff(new Buff("Gleaming Moon: Intent", 4.0, sim.getCurrentTime()) {
                        @Override
                        protected void applyStats(StatsContainer stats, double currentTime) {
                            stats.add(StatType.CRIT_RATE, finalBonus);
                        }
                    });

                }
            }
        }
    }
}

package model.artifact;

import model.stats.StatsContainer;
import model.type.StatType;
import mechanics.buff.Buff;
import mechanics.buff.BuffId;
import simulation.CombatSimulator.Moonsign;
import model.entity.ReactionAwareArtifact;

/**
 * Night of the Sky's Unveiling artifact set with Lunar reaction-triggered
 * self CRIT buffs.
 */
public class NightOfTheSkysUnveiling extends model.entity.ArtifactSet implements ReactionAwareArtifact {

    /**
     * Constructs Night of the Sky's Unveiling with the 2-piece Elemental
     * Mastery bonus.
     */
    public NightOfTheSkysUnveiling() {
        super("Night of the Sky's Unveiling", new StatsContainer());
        // 2-Piece Bonus: Increases Elemental Mastery by 80.
        this.getStats().add(StatType.ELEMENTAL_MASTERY, 80.0);
    }

    /**
     * Constructs Night of the Sky's Unveiling with the supplied main/sub stats
     * plus the 2-piece Elemental Mastery bonus.
     *
     * @param stats artifact main and sub stats
     */
    public NightOfTheSkysUnveiling(StatsContainer stats) {
        super("Night of the Sky's Unveiling", stats);
        this.getStats().add(StatType.ELEMENTAL_MASTERY, 80.0);
    }

    /**
     * Applies the 4-piece self CRIT Rate buff when a Lunar reaction is
     * triggered while the wearer is on-field.
     *
     * @param sim the active combat simulator
     * @param result the reaction result that was produced
     * @param triggerCh the character who triggered the reaction
     * @param owner the character equipping the set
     */
    @Override
    public void onReaction(simulation.CombatSimulator sim, mechanics.reaction.ReactionResult result,
            model.entity.Character triggerCh, model.entity.Character owner) {
        // 4-Piece Bonus Logic
        // Trigger: "When nearby party members trigger Lunar Reactions" (implied: when
        // ANYONE triggers)
        if (result.isLunarReaction() || result.isThundercloudStrike()) {
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
                    owner.removeBuff(BuffId.GLEAMING_MOON_INTENT);
                    final double finalBonus = crBonus;
                    owner.addBuff(new Buff("Gleaming Moon: Intent", BuffId.GLEAMING_MOON_INTENT, 4.0,
                            sim.getCurrentTime()) {
                        @Override
                        protected void applyStats(StatsContainer stats, double currentTime) {
                            stats.add(StatType.CRIT_RATE, finalBonus);
                        }
                    }.sourcedBy(owner.getCharacterId()));

                }
            }
        }
    }
}

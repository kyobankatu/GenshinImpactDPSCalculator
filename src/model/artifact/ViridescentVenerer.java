package model.artifact;

import model.stats.StatsContainer;
import model.type.StatType;
import mechanics.buff.Buff;
import mechanics.buff.BuffId;
import simulation.CombatSimulator;

public class ViridescentVenerer extends model.entity.ArtifactSet {

    public ViridescentVenerer() {
        super("Viridescent Venerer", new StatsContainer());
        // 2-Piece Bonus: Anemo DMG +15%
        this.getStats().add(StatType.ANEMO_DMG_BONUS, 0.15);
        // 4-Piece Passive: Swirl DMG +60%
        this.getStats().add(StatType.SWIRL_DMG_BONUS, 0.60);
    }

    public ViridescentVenerer(StatsContainer stats) {
        super("Viridescent Venerer", stats);
        this.getStats().add(StatType.ANEMO_DMG_BONUS, 0.15);
        // 4-Piece Passive: Swirl DMG +60%
        this.getStats().add(StatType.SWIRL_DMG_BONUS, 0.60);
    }

    @Override
    public void onReaction(CombatSimulator sim, mechanics.reaction.ReactionResult result,
            model.entity.Character triggerCh, model.entity.Character owner) {
        // 4-Piece Bonus:
        // Decreases opponent's Elemental RES to the element infused in the Swirl by 40%
        // for 10s.

        // Trigger condition: Swirl reaction
        if (result.isSwirl()) {
            // Check if owner is on field?
            // "The effect... can be triggered by the equipping character even if they are
            // not on the field?" -> Usually NO.
            // VV specifically requires the character to be ON FIELD to trigger the RES
            // shred.
            // (Common knowledge: VV only triggers on-field).
            // Let's implement ON FIELD requirement.

            if (sim.getActiveCharacter() == owner) {
                model.type.Element elem = result.getSwirlElement();
                if (elem != null) {
                    // Apply Team Buff for RES Shred against this element
                    // (Equivalent to Enemy Debuff in single-target sim)

                    StatType shredStat = null;
                    switch (elem) {
                        case PYRO:
                            shredStat = StatType.PYRO_RES_SHRED;
                            break;
                        case HYDRO:
                            shredStat = StatType.HYDRO_RES_SHRED;
                            break;
                        case CRYO:
                            shredStat = StatType.CRYO_RES_SHRED;
                            break;
                        case ELECTRO:
                            shredStat = StatType.ELECTRO_RES_SHRED;
                            break;
                        case ANEMO:
                        case GEO:
                        case DENDRO:
                        case PHYSICAL:
                            // Cannot swirl these
                            break;
                    }

                    if (shredStat != null) {
                        final StatType targetStat = shredStat;
                        final String buffName = "VV Shred: " + elem.toString();

                        Buff vvBuff = new Buff(buffName, getVvBuffId(elem), 10.0, sim.getCurrentTime()) {
                            @Override
                            protected void applyStats(StatsContainer stats, double currentTime) {
                                stats.add(targetStat, 0.40);
                            }
                        };

                        // Apply to ALL party members
                        for (model.entity.Character m : sim.getPartyMembers()) {
                            m.addBuff(vvBuff);
                        }
                    }
                }
            }
        }
    }

    private BuffId getVvBuffId(model.type.Element elem) {
        switch (elem) {
            case PYRO:
                return BuffId.VV_SHRED_PYRO;
            case HYDRO:
                return BuffId.VV_SHRED_HYDRO;
            case ELECTRO:
                return BuffId.VV_SHRED_ELECTRO;
            case CRYO:
                return BuffId.VV_SHRED_CRYO;
            default:
                throw new IllegalArgumentException("Unsupported VV element: " + elem);
        }
    }
}

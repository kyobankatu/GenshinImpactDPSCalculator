package model.artifact;

import java.util.Collections;
import java.util.List;

import mechanics.buff.Buff;
import mechanics.buff.BuffId;
import model.entity.ArtifactSet;
import model.entity.Character;
import model.stats.StatsContainer;
import model.type.StatType;
import simulation.CombatSimulator;

/**
 * Shared policy for the party-wide bonus produced by active Gleaming Moon
 * effects.
 */
final class GleamingMoonSynergy {
    private static final double BONUS_PER_DISTINCT_EFFECT = 0.10;

    private GleamingMoonSynergy() {
    }

    /**
     * Returns the dynamic synergy buff when the caller is the canonical artifact
     * provider for the party.
     *
     * @param provider artifact instance requesting provider status
     * @param owner character equipping the requesting artifact
     * @param sim active simulator whose party state is inspected
     * @return one dynamic synergy buff, or an empty list for a duplicate provider
     */
    static List<Buff> getArtifactTeamBuffs(ArtifactSet provider, Character owner, CombatSimulator sim) {
        if (findCanonicalProvider(sim) != provider) {
            return Collections.emptyList();
        }

        Buff synergy = new Buff("Gleaming Moon: Synergy", BuffId.GLEAMING_MOON_SYNERGY) {
            @Override
            protected void applyStats(StatsContainer stats, double currentTime) {
                double bonus = countDistinctEffects(sim, currentTime) * BONUS_PER_DISTINCT_EFFECT;
                stats.add(StatType.LUNAR_CHARGED_DMG_BONUS, bonus);
                stats.add(StatType.LUNAR_BLOOM_DMG_BONUS, bonus);
                stats.add(StatType.LUNAR_CRYSTALLIZE_DMG_BONUS, bonus);
            }
        }.sourcedBy(owner.getCharacterId());
        return Collections.singletonList(synergy);
    }

    private static ArtifactSet findCanonicalProvider(CombatSimulator sim) {
        ArtifactSet firstNight = null;
        for (Character member : sim.getPartyMembers()) {
            if (member.getArtifacts() == null) {
                continue;
            }
            for (ArtifactSet artifact : member.getArtifacts()) {
                if (artifact instanceof SilkenMoonsSerenade) {
                    return artifact;
                }
                if (firstNight == null && artifact instanceof NightOfTheSkysUnveiling) {
                    firstNight = artifact;
                }
            }
        }
        return firstNight;
    }

    private static int countDistinctEffects(CombatSimulator sim, double currentTime) {
        boolean hasDevotion = false;
        boolean hasIntent = false;
        for (Character member : sim.getPartyMembers()) {
            for (Buff buff : member.getActiveBuffs()) {
                if (buff.isExpired(currentTime)) {
                    continue;
                }
                hasDevotion |= buff.getId() == BuffId.GLEAMING_MOON_DEVOTION;
                hasIntent |= buff.getId() == BuffId.GLEAMING_MOON_INTENT;
            }
        }
        return (hasDevotion ? 1 : 0) + (hasIntent ? 1 : 0);
    }
}

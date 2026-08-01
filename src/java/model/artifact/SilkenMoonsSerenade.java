package model.artifact;

import java.util.Collections;
import java.util.List;

import model.stats.StatsContainer;
import model.type.StatType;
import mechanics.buff.Buff;
import mechanics.buff.BuffId;
import simulation.CombatSimulator;
import simulation.CombatSimulator.Moonsign;
import model.entity.ArtifactSet;
import model.entity.ArtifactTeamBuffProvider;
import model.entity.Character;
import model.entity.DamageTriggeredArtifactEffect;

/**
 * Silken Moon's Serenade artifact set with Moonsign-dependent team EM support.
 */
public class SilkenMoonsSerenade extends ArtifactSet
        implements DamageTriggeredArtifactEffect, ArtifactTeamBuffProvider {

    /**
     * Constructs Silken Moon's Serenade with the 2-piece Energy Recharge bonus.
     */
    public SilkenMoonsSerenade() {
        super("Silken Moon's Serenade", new StatsContainer());
        // 2-Piece Bonus: ER +20%
        this.getStats().add(StatType.ENERGY_RECHARGE, 0.20);
    }

    /**
     * Constructs Silken Moon's Serenade with the supplied main/sub stats plus
     * the 2-piece Energy Recharge bonus.
     *
     * @param stats artifact main and sub stats
     */
    public SilkenMoonsSerenade(StatsContainer stats) {
        super("Silken Moon's Serenade", stats);
        this.getStats().add(StatType.ENERGY_RECHARGE, 0.20);
    }

    /**
     * Grants the 4-piece team Elemental Mastery buff after elemental damage,
     * based on the current Moonsign state.
     *
     * @param sim the active combat simulator
     * @param action the attack action that dealt damage
     * @param damage the damage amount dealt
     * @param owner the character equipping the set
     */
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
            if (sign == Moonsign.NASCENT_GLEAM) {
                emBonus = 60.0;
            } else if (sign == Moonsign.ASCENDANT_GLEAM) {
                emBonus = 120.0;
            }

            if (emBonus > 0) {
                final double finalBonus = emBonus;
                Buff devotionBuff = new Buff("Gleaming Moon: Devotion", BuffId.GLEAMING_MOON_DEVOTION, 8.0,
                        sim.getCurrentTime()) {
                    @Override
                    protected void applyStats(StatsContainer stats, double currentTime) {
                        stats.add(StatType.ELEMENTAL_MASTERY, finalBonus);
                    }
                }.sourcedBy(owner.getCharacterId());

                // Apply to ALL party members (Description: "Increases all party members' EM")
                // The *effect* is called "Gleaming Moon: Devotion".
                // It acts as a Team Buff.
                for (model.entity.Character m : sim.getPartyMembers()) {
                    // Start of Fix: Ensure uniqueness
                    if (m.hasBuff(BuffId.GLEAMING_MOON_DEVOTION)) {
                        m.removeBuff(BuffId.GLEAMING_MOON_DEVOTION);
                    }
                    m.addBuff(devotionBuff);
                }

            }
        }
    }

    /**
     * Supplies the party-wide Lunar Reaction bonus derived from currently active
     * distinct Gleaming Moon effects.
     *
     * @param owner character equipping this artifact set
     * @param sim active simulator whose party statuses are inspected
     * @return one canonical dynamic team buff, or an empty list for duplicate sets
     */
    @Override
    public List<Buff> getArtifactTeamBuffs(Character owner, CombatSimulator sim) {
        if (!isCanonicalProvider(sim)) {
            return Collections.emptyList();
        }

        Buff synergy = new Buff("Gleaming Moon: Synergy", BuffId.GLEAMING_MOON_SYNERGY) {
            @Override
            protected void applyStats(StatsContainer stats, double currentTime) {
                double bonus = countDistinctGleamingMoonEffects(sim, currentTime) * 0.10;
                stats.add(StatType.LUNAR_CHARGED_DMG_BONUS, bonus);
                stats.add(StatType.LUNAR_BLOOM_DMG_BONUS, bonus);
                stats.add(StatType.LUNAR_CRYSTALLIZE_DMG_BONUS, bonus);
            }
        }.sourcedBy(owner.getCharacterId());
        return Collections.singletonList(synergy);
    }

    private boolean isCanonicalProvider(CombatSimulator sim) {
        for (Character member : sim.getPartyMembers()) {
            if (member.getArtifacts() == null) {
                continue;
            }
            for (ArtifactSet artifact : member.getArtifacts()) {
                if (artifact instanceof SilkenMoonsSerenade) {
                    return artifact == this;
                }
            }
        }
        return false;
    }

    private int countDistinctGleamingMoonEffects(CombatSimulator sim, double currentTime) {
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

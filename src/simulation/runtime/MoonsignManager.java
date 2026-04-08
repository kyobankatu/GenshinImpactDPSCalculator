package simulation.runtime;

import mechanics.buff.Buff;
import model.entity.Character;
import model.stats.StatsContainer;
import model.type.Element;
import model.type.StatType;
import simulation.CombatSimulator;

/**
 * Owns Moonsign-related party-state updates and derived Lunar buff application.
 *
 * <p>This extracts custom Lunar party mechanics from {@link CombatSimulator} so the
 * simulator can focus on combat orchestration rather than Moonsign-specific policy.
 */
public class MoonsignManager {
    private final CombatSimulator sim;

    /**
     * Creates a manager bound to the given simulator instance.
     *
     * @param sim active simulator whose party state and buff lists will be updated
     */
    public MoonsignManager(CombatSimulator sim) {
        this.sim = sim;
    }

    /**
     * Recalculates and reapplies the Gleaming Moon Synergy buff based on currently active
     * Gleaming Moon effects across the party.
     */
    public void updateGleamingMoonSynergy() {
        boolean hasIntent = false;
        boolean hasDevotion = false;

        for (Character member : sim.getPartyMembers()) {
            for (Buff buff : sim.getApplicableBuffs(member)) {
                if (buff.getName().equals("Gleaming Moon: Intent")) {
                    hasIntent = true;
                }
                if (buff.getName().equals("Gleaming Moon: Devotion")) {
                    hasDevotion = true;
                }
            }
        }

        int count = (hasIntent ? 1 : 0) + (hasDevotion ? 1 : 0);
        if (count <= 0) {
            return;
        }

        final double bonus = 0.10 * count;
        Buff synergyBuff = new Buff("Gleaming Moon: Synergy", 8.0, sim.getCurrentTime()) {
            @Override
            protected void applyStats(StatsContainer stats, double currentTime) {
                stats.add(StatType.LUNAR_CHARGED_DMG_BONUS, bonus);
                stats.add(StatType.LUNAR_BLOOM_DMG_BONUS, bonus);
                stats.add(StatType.LUNAR_CRYSTALLIZE_DMG_BONUS, bonus);
            }
        };

        for (Character member : sim.getPartyMembers()) {
            member.addBuff(synergyBuff);
        }
    }

    /**
     * Recomputes the current {@link CombatSimulator.Moonsign} state from the simulator's
     * party composition and updates the simulator accordingly.
     */
    public void updateMoonsign() {
        int lunarCount = 0;
        for (Character character : sim.getPartyMembers()) {
            if (character.isLunarCharacter()) {
                lunarCount++;
            }
        }

        if (lunarCount >= 2) {
            sim.setMoonsign(CombatSimulator.Moonsign.ASCENDANT_GLEAM);
            if (sim.isLoggingEnabled()) {
                System.out.println("[System] Moonsign updated to ASCENDANT_GLEAM (Lunar Chars: " + lunarCount + ")");
            }
        } else if (lunarCount == 1) {
            sim.setMoonsign(CombatSimulator.Moonsign.NASCENT_GLEAM);
            if (sim.isLoggingEnabled()) {
                System.out.println("[System] Moonsign updated to NASCENT_GLEAM (Lunar Chars: " + lunarCount + ")");
            }
        } else {
            sim.setMoonsign(CombatSimulator.Moonsign.NONE);
        }
    }

    /**
     * Applies Moonsign: Ascendant Blessing when an eligible non-Lunar character performs
     * a Skill or Burst while Ascendant Gleam is active.
     *
     * @param buffer the character whose stats determine the granted Lunar bonus
     */
    public void applyAscendantBlessing(Character buffer) {
        double bonus = calculateAscendantBlessingValue(buffer);
        for (Buff buff : sim.getTeamBuffList()) {
            if (buff.getName().equals("Moonsign: Ascendant Blessing")
                    && buff instanceof MoonsignBuff
                    && ((MoonsignBuff) buff).getValue() > bonus) {
                return;
            }
        }

        sim.removeTeamBuffsByName("Moonsign: Ascendant Blessing");
        if (sim.isLoggingEnabled()) {
            System.out.println(String.format(
                    "   [Buff] Ascendant Blessing Triggered by %s (+%.1f%% Lunar DMG) - Duration 20s",
                    buffer.getName(), bonus * 100));
        }
        sim.applyTeamBuff(new MoonsignBuff(bonus, sim.getCurrentTime()));
    }

    private double calculateAscendantBlessingValue(Character buffer) {
        StatsContainer stats = buffer.getEffectiveStats(sim.getCurrentTime());
        Element element = buffer.getElement();
        double bonus = 0.0;

        if (element == Element.PYRO || element == Element.ELECTRO || element == Element.CRYO) {
            bonus = (stats.getTotalAtk() / 100.0) * 0.009;
        } else if (element == Element.HYDRO) {
            bonus = (stats.getTotalHp() / 1000.0) * 0.006;
        } else if (element == Element.GEO) {
            bonus = (stats.getTotalDef() / 100.0) * 0.01;
        } else if (element == Element.ANEMO || element == Element.DENDRO) {
            bonus = (stats.get(StatType.ELEMENTAL_MASTERY) / 100.0) * 0.0225;
        }

        return Math.min(0.36, bonus);
    }

    /**
     * Buff implementation for Moonsign: Ascendant Blessing.
     */
    public static class MoonsignBuff extends Buff {
        private final double value;

        /**
         * Creates a timed Ascendant Blessing buff.
         *
         * @param value     granted Lunar DMG bonus
         * @param startTime simulation time at which the buff starts
         */
        public MoonsignBuff(double value, double startTime) {
            super("Moonsign: Ascendant Blessing", 20.0, startTime);
            this.value = value;
        }

        /**
         * Returns the granted Lunar DMG bonus value.
         *
         * @return buff magnitude
         */
        public double getValue() {
            return value;
        }

        /**
         * Applies the Ascendant Blessing Lunar bonus to the current stat snapshot.
         *
         * @param stats       stat container to mutate
         * @param currentTime current simulation time in seconds
         */
        @Override
        protected void applyStats(StatsContainer stats, double currentTime) {
            stats.add(StatType.LUNAR_MOONSIGN_BONUS, value);
        }
    }
}

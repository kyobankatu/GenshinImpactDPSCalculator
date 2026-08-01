package model.artifact;

import mechanics.buff.BuffId;
import model.entity.SimulatorInitializedArtifactEffect;
import model.stats.StatsContainer;
import model.type.StatType;
import mechanics.buff.Buff;
import simulation.CombatSimulator;
import simulation.CombatSimulator.Moonsign;
import model.entity.SwitchAwareArtifact;

/**
 * Aubade of Morningstar and Moon artifact set with switch-state Lunar reaction
 * buffs.
 */
public class AubadeOfMorningstarAndMoon extends model.entity.ArtifactSet
        implements SimulatorInitializedArtifactEffect, SwitchAwareArtifact {

    /**
     * Constructs Aubade of Morningstar and Moon with the 2-piece EM bonus.
     */
    public AubadeOfMorningstarAndMoon() {
        super("Aubade of Morningstar and Moon", new StatsContainer());
        this.getStats().add(StatType.ELEMENTAL_MASTERY, 80.0);
    }

    /**
     * Constructs Aubade of Morningstar and Moon with the supplied main/sub
     * stats plus the 2-piece EM bonus.
     *
     * @param stats artifact main and sub stats
     */
    public AubadeOfMorningstarAndMoon(StatsContainer stats) {
        super("Aubade of Morningstar and Moon", stats);
        this.getStats().add(StatType.ELEMENTAL_MASTERY, 80.0);
    }

    /**
     * Activates the owner-only bonus immediately when the owner starts off field.
     * An initially active owner has not yet met the activation condition.
     *
     * @param owner owner equipped with this set
     * @param sim simulator containing the owner
     * @param startsActive whether the owner starts as the active character
     */
    @Override
    public void initializeForSimulator(
            model.entity.Character owner,
            CombatSimulator sim,
            boolean startsActive) {
        if (!startsActive) {
            updateBuffState(sim, owner, false);
        }
    }

    /**
     * Switches the set into its off-field state, keeping the Lunar reaction buff
     * active until the owner returns on-field.
     *
     * @param sim the active combat simulator
     * @param owner the character equipping the set
     */
    @Override
    public void onSwitchOut(CombatSimulator sim, model.entity.Character owner) {
        updateBuffState(sim, owner, false);
    }

    /**
     * Switches the set into its lingering on-field-return state for 3 seconds.
     *
     * @param sim the active combat simulator
     * @param owner the character equipping the set
     */
    @Override
    public void onSwitchIn(CombatSimulator sim, model.entity.Character owner) {
        // When switching in:
        // Effect persists for 3s.
        updateBuffState(sim, owner, true);
    }

    private AubadeBuff activeBuff;

    private void updateBuffState(CombatSimulator sim, model.entity.Character owner, boolean isSwitchingIn) {
        if (activeBuff == null) {
            activeBuff = new AubadeBuff(sim);
            owner.addBuff(activeBuff);
        }

        if (isSwitchingIn) {
            activeBuff.setExpiration(sim.getCurrentTime() + 3.0);
        } else {
            activeBuff.setExpiration(Double.MAX_VALUE);
        }
    }

    private static class AubadeBuff extends Buff {
        private final CombatSimulator sim;

        private AubadeBuff(CombatSimulator sim) {
            super("Aubade Bonus", BuffId.AUBADE_BONUS);
            this.sim = sim;
        }

        private void setExpiration(double time) {
            this.expirationTime = time;
        }

        @Override
        protected void applyStats(StatsContainer stats, double currentTime) {
            double bonus = 0.20;
            if (sim.getMoonsign() == Moonsign.ASCENDANT_GLEAM) {
                bonus += 0.40;
            }

            stats.add(StatType.LUNAR_CHARGED_DMG_BONUS, bonus);
            stats.add(StatType.LUNAR_BLOOM_DMG_BONUS, bonus);
            stats.add(StatType.LUNAR_CRYSTALLIZE_DMG_BONUS, bonus);
        }
    }
}

package model.artifact;

import java.util.Objects;

import mechanics.buff.Buff;
import mechanics.buff.BuffId;
import model.entity.ArtifactSet;
import model.entity.Character;
import model.entity.SimulatorInitializedArtifactEffect;
import model.stats.StatsContainer;
import model.type.StatType;
import simulation.CombatSimulator;

/**
 * Disenchantment in Deep Shadow with its supported Superconduct effects.
 *
 * <p>The two-piece ATK bonus and four-piece Superconduct reaction bonus are
 * fixed stats. The 16% CRIT Rate is evaluated live from the simulator's
 * existing Superconduct Physical RES shred window. Stellar-Conduct is outside
 * the current reaction model and therefore remains inactive.</p>
 */
public class DisenchantmentInDeepShadow extends ArtifactSet
        implements SimulatorInitializedArtifactEffect {
    private static final double CRIT_RATE_BONUS = 0.16;

    private Character owner;
    private CombatSimulator simulator;

    /** Constructs the set with a fresh fixed-stat container. */
    public DisenchantmentInDeepShadow() {
        this(new StatsContainer());
    }

    /**
     * Constructs the set while preserving supplied main and sub stats.
     *
     * @param stats non-null artifact main and sub stats
     */
    public DisenchantmentInDeepShadow(StatsContainer stats) {
        super("Disenchantment in Deep Shadow",
                Objects.requireNonNull(stats, "stats"));
        getStats().add(StatType.ATK_PERCENT, 0.18);
        getStats().add(StatType.SUPERCONDUCT_DMG_BONUS, 0.80);
    }

    /**
     * Binds the live CRIT effect to one owner and simulator.
     *
     * <p>Repeated initialization for the identical binding is idempotent.
     * Reusing one stateful set instance across owners or simulators is rejected.</p>
     *
     * @param equippedOwner character carrying the set
     * @param sim simulator containing the owner
     * @param startsActive whether the owner starts on field
     */
    @Override
    public void initializeForSimulator(
            Character equippedOwner,
            CombatSimulator sim,
            boolean startsActive) {
        if (equippedOwner == null || sim == null) {
            throw new IllegalArgumentException(
                    "Artifact owner and simulator are required");
        }
        if (simulator != null) {
            if (owner != equippedOwner || simulator != sim) {
                throw new IllegalStateException(
                        "Disenchantment in Deep Shadow is already bound");
            }
            return;
        }

        owner = equippedOwner;
        simulator = sim;
        owner.removeBuff(BuffId.DISENCHANTMENT_SUPERCONDUCT_CRIT_RATE);
        owner.addBuff(new SuperconductCritRateBuff(sim)
                .sourcedBy(owner.getCharacterId()));
    }

    /** Owner-only live CRIT modifier backed by the Superconduct status buff. */
    private static final class SuperconductCritRateBuff extends Buff {
        private final CombatSimulator simulator;

        private SuperconductCritRateBuff(CombatSimulator simulator) {
            super("Disenchantment: Superconduct CRIT Rate",
                    BuffId.DISENCHANTMENT_SUPERCONDUCT_CRIT_RATE);
            this.simulator = simulator;
        }

        /** Adds CRIT Rate only while the shared Superconduct status is active. */
        @Override
        protected void applyStats(StatsContainer stats, double currentTime) {
            for (Buff buff : simulator.getTeamBuffList()) {
                if (buff.getId() == BuffId.SUPERCONDUCT_PHYS_RES_SHRED
                        && currentTime >= buff.getStartTime()
                        && !buff.isExpired(currentTime)) {
                    stats.add(StatType.CRIT_RATE, CRIT_RATE_BONUS);
                    return;
                }
            }
        }
    }
}

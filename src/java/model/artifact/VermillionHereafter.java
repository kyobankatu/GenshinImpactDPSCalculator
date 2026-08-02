package model.artifact;

import java.util.Objects;

import mechanics.buff.BuffId;
import mechanics.buff.SimpleBuff;
import model.entity.ArtifactSet;
import model.entity.BurstTriggeredArtifactEffect;
import model.entity.Character;
import model.entity.SimulatorInitializedArtifactEffect;
import model.entity.SwitchAwareArtifact;
import model.stats.StatsContainer;
import model.type.StatType;
import simulation.CombatSimulator;

/**
 * Vermillion Hereafter with its accepted-Burst Nascent Light window.
 *
 * <p>The fixed two-piece bonus grants 18% ATK. An accepted owner Burst grants
 * a further 8% ATK over the half-open interval
 * {@code [castTime, castTime + 16)}. Recasting replaces the original window,
 * and a standard owner switch-out dispels it immediately.</p>
 *
 * <p>The four current-HP-decrease stacks are intentionally inactive because
 * the simulator has no player current-HP or incoming-damage event contract.</p>
 */
public class VermillionHereafter extends ArtifactSet
        implements SimulatorInitializedArtifactEffect,
        BurstTriggeredArtifactEffect,
        SwitchAwareArtifact {
    private static final double WINDOW_DURATION = 16.0;
    private static final double WINDOW_ATK_BONUS = 0.08;

    private Character owner;
    private CombatSimulator simulator;

    /** Constructs Vermillion Hereafter with fresh fixed stats. */
    public VermillionHereafter() {
        this(new StatsContainer());
    }

    /**
     * Constructs the set while preserving supplied main and sub stats.
     *
     * @param stats non-null artifact main and sub stats
     */
    public VermillionHereafter(StatsContainer stats) {
        super("Vermillion Hereafter", Objects.requireNonNull(stats, "stats"));
        getStats().add(StatType.ATK_PERCENT, 0.18);
    }

    /**
     * Binds this stateful set to exactly one owner and simulator.
     *
     * <p>Repeating the identical binding is idempotent. Reusing one artifact
     * instance for another owner or simulator is rejected.</p>
     *
     * @param equippedOwner character carrying this set
     * @param sim simulator containing the owner
     * @param startsActive whether the owner starts active
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
                        "Vermillion Hereafter is already bound");
            }
            return;
        }
        owner = equippedOwner;
        simulator = sim;
    }

    /**
     * Applies or refreshes Nascent Light after an accepted owner Burst.
     *
     * <p>The Burst dispatcher invokes this only for the acting owner's
     * artifacts after energy and cooldown gates pass. Unbound, null, and
     * wrong-simulator callbacks are inert.</p>
     *
     * @param sim simulator dispatching the accepted Burst
     */
    @Override
    public void onBurst(CombatSimulator sim) {
        if (owner == null || simulator == null || sim != simulator) {
            return;
        }
        owner.removeBuff(BuffId.VERMILLION_HEREAFTER_NASCENT_LIGHT);
        owner.addBuff(new SimpleBuff(
                "Vermillion Hereafter: Nascent Light",
                BuffId.VERMILLION_HEREAFTER_NASCENT_LIGHT,
                WINDOW_DURATION,
                sim.getCurrentTime(),
                stats -> stats.add(StatType.ATK_PERCENT, WINDOW_ATK_BONUS))
                .sourcedBy(owner.getCharacterId()));
    }

    /**
     * Handles owner switch-in without creating or restoring Nascent Light.
     *
     * @param sim simulator dispatching the switch
     * @param callbackOwner artifact wearer supplied by the dispatcher
     */
    @Override
    public void onSwitchIn(CombatSimulator sim, Character callbackOwner) {
        // Nascent Light is only created by an accepted Burst.
    }

    /**
     * Dispels Nascent Light when the bound owner truthfully leaves the field.
     *
     * <p>Unbound, null, and mismatched callbacks are inert.</p>
     *
     * @param sim simulator dispatching the switch
     * @param callbackOwner artifact wearer supplied by the dispatcher
     */
    @Override
    public void onSwitchOut(CombatSimulator sim, Character callbackOwner) {
        if (owner == null
                || simulator == null
                || callbackOwner != owner
                || sim != simulator) {
            return;
        }
        owner.removeBuff(BuffId.VERMILLION_HEREAFTER_NASCENT_LIGHT);
    }
}

package model.artifact;

import java.util.Objects;

import mechanics.buff.Buff;
import mechanics.buff.BuffId;
import mechanics.buff.SimpleBuff;
import model.entity.ArtifactSet;
import model.entity.Character;
import model.entity.DamageTriggeredArtifactEffect;
import model.entity.SimulatorInitializedArtifactEffect;
import model.stats.StatsContainer;
import model.type.ActionType;
import model.type.StatType;
import simulation.CombatSimulator;
import simulation.action.AttackAction;

/**
 * Desert Pavilion Chronicle with a Charged-hit attack window.
 *
 * <p>The fixed two-piece bonus grants 15% Anemo DMG Bonus. After a positive
 * Charged Attack hit by the bound owner, a typed 15-second buff grants 10%
 * ATK SPD and 40% Normal, Charged, and Plunging Attack DMG. The window starts
 * immediately after the triggering hit, so that hit and its already-started
 * animation retain pre-trigger stats.</p>
 */
public class DesertPavilionChronicle extends ArtifactSet
        implements SimulatorInitializedArtifactEffect,
        DamageTriggeredArtifactEffect {
    private static final double ATTACK_WINDOW_DURATION = 15.0;

    private Character owner;
    private CombatSimulator simulator;

    /** Constructs Desert Pavilion Chronicle with fresh fixed stats. */
    public DesertPavilionChronicle() {
        this(new StatsContainer());
    }

    /**
     * Constructs Desert Pavilion Chronicle while preserving supplied stats.
     *
     * @param stats non-null artifact main and sub stats
     */
    public DesertPavilionChronicle(StatsContainer stats) {
        super(
                "Desert Pavilion Chronicle",
                Objects.requireNonNull(stats, "stats"));
        getStats().add(StatType.ANEMO_DMG_BONUS, 0.15);
    }

    /**
     * Binds this stateful set to exactly one owner and simulator.
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
                        "Desert Pavilion Chronicle is already bound");
            }
            return;
        }
        owner = equippedOwner;
        simulator = sim;
    }

    /**
     * Opens or refreshes the four-piece window after a positive Charged hit.
     *
     * @param sim simulator dispatching the hit
     * @param action resolved attack action
     * @param damage final direct damage
     * @param callbackOwner artifact owner supplied by the dispatcher
     */
    @Override
    public void onDamage(
            CombatSimulator sim,
            AttackAction action,
            double damage,
            Character callbackOwner) {
        if (simulator == null
                || sim != simulator
                || callbackOwner != owner
                || action == null
                || action.getActionType() != ActionType.CHARGE
                || !(damage > 0.0)) {
            return;
        }

        double hitTime = sim.getCurrentTime();
        owner.removeBuff(BuffId.DESERT_PAVILION_CHRONICLE_4PC);
        Buff attackWindow = new SimpleBuff(
                "Desert Pavilion Chronicle: Four-Piece Bonus",
                BuffId.DESERT_PAVILION_CHRONICLE_4PC,
                ATTACK_WINDOW_DURATION,
                hitTime,
                stats -> {
                    stats.add(StatType.ATK_SPD, 0.10);
                    stats.add(StatType.NORMAL_ATTACK_DMG_BONUS, 0.40);
                    stats.add(StatType.CHARGED_ATTACK_DMG_BONUS, 0.40);
                    stats.add(StatType.PLUNGING_ATTACK_DMG_BONUS, 0.40);
                }).sourcedBy(owner.getCharacterId());
        attackWindow.restoreTimes(
                Math.nextUp(hitTime),
                hitTime + ATTACK_WINDOW_DURATION);
        owner.addBuff(attackWindow);
    }
}

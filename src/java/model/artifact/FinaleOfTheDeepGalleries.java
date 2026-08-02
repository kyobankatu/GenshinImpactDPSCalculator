package model.artifact;

import java.util.Objects;

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
 * Finale of the Deep Galleries artifact set with zero-Energy attack bonuses.
 *
 * <p>The fixed two-piece bonus grants 15% Cryo DMG Bonus. At exactly zero
 * Energy, the four-piece bonus grants 60% Normal and Burst DMG Bonus. A
 * positive hit in either category suppresses the opposite category for the
 * half-open interval {@code [hit, hit + 6)}.</p>
 */
public class FinaleOfTheDeepGalleries extends ArtifactSet
        implements SimulatorInitializedArtifactEffect, DamageTriggeredArtifactEffect {
    private static final double OPPOSITE_CATEGORY_LOCK_DURATION = 6.0;

    private Character owner;
    private CombatSimulator simulator;
    private double normalBonusBlockedUntil = Double.NEGATIVE_INFINITY;
    private double burstBonusBlockedUntil = Double.NEGATIVE_INFINITY;

    /** Constructs Finale of the Deep Galleries with a fresh fixed-stat container. */
    public FinaleOfTheDeepGalleries() {
        this(new StatsContainer());
    }

    /**
     * Constructs Finale of the Deep Galleries while preserving supplied stats.
     *
     * @param stats non-null artifact main and sub stats
     */
    public FinaleOfTheDeepGalleries(StatsContainer stats) {
        super("Finale of the Deep Galleries", Objects.requireNonNull(stats, "stats"));
        getStats().add(StatType.CRYO_DMG_BONUS, 0.15);
    }

    /**
     * Binds the set's category locks to one owner and simulator.
     *
     * <p>Repeating the identical binding is idempotent. Reusing one artifact
     * instance for another owner or simulator is rejected.</p>
     *
     * @param equippedOwner character carrying this artifact set
     * @param sim simulator containing the equipped owner
     * @param startsActive whether the owner starts as the active character
     */
    @Override
    public void initializeForSimulator(
            Character equippedOwner,
            CombatSimulator sim,
            boolean startsActive) {
        if (equippedOwner == null || sim == null) {
            throw new IllegalArgumentException("Artifact owner and simulator are required");
        }
        if (simulator != null) {
            if (owner != equippedOwner || simulator != sim) {
                throw new IllegalStateException(
                        "Finale of the Deep Galleries is already bound to another simulator");
            }
            return;
        }
        owner = equippedOwner;
        simulator = sim;
    }

    /**
     * Refreshes the six-second lock on the opposite damage category.
     *
     * <p>Callbacks are accepted regardless of field state. Invalid bindings,
     * null actions, non-positive damage, and unrelated action categories are
     * inert.</p>
     *
     * @param sim simulator dispatching the resolved damage
     * @param action action that produced the damage
     * @param damage final post-mitigation damage
     * @param callbackOwner artifact wearer supplied by the dispatcher
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
                || !(damage > 0.0)) {
            return;
        }

        double currentTime = sim.getCurrentTime();
        if (action.getActionType() == ActionType.NORMAL) {
            burstBonusBlockedUntil = currentTime + OPPOSITE_CATEGORY_LOCK_DURATION;
        }
        if (action.getActionType() == ActionType.BURST || action.isCountsAsBurstDmg()) {
            normalBonusBlockedUntil = currentTime + OPPOSITE_CATEGORY_LOCK_DURATION;
        }
    }

    /**
     * Applies currently available four-piece bonuses at exactly zero Energy.
     *
     * <p>Energy state gates both bonuses without clearing category lock state.
     * A bonus returns when its half-open lock interval has elapsed.</p>
     *
     * @param totalStats aggregated stats container to mutate in place
     */
    @Override
    public void applyPassive(StatsContainer totalStats) {
        if (owner == null || simulator == null || owner.getCurrentEnergy() != 0.0) {
            return;
        }

        double currentTime = simulator.getCurrentTime();
        if (currentTime >= normalBonusBlockedUntil) {
            totalStats.add(StatType.NORMAL_ATTACK_DMG_BONUS, 0.60);
        }
        if (currentTime >= burstBonusBlockedUntil) {
            totalStats.add(StatType.BURST_DMG_BONUS, 0.60);
        }
    }
}

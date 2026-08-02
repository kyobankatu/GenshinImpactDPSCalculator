package model.artifact;

import java.util.Objects;

import model.entity.ActionTriggeredArtifactEffect;
import model.entity.ArtifactSet;
import model.entity.Character;
import model.entity.SimulatorInitializedArtifactEffect;
import model.stats.StatsContainer;
import model.type.StatType;
import simulation.CombatSimulator;
import simulation.action.CharacterActionKey;
import simulation.action.CharacterActionRequest;

/**
 * Heart of Depth artifact set with a Skill-activated attack bonus.
 *
 * <p>The fixed two-piece bonus grants 15% Hydro DMG Bonus. A successful
 * Elemental Skill action by the bound owner opens or refreshes a 15-second
 * window that grants 30% Normal and Charged Attack DMG Bonus over the
 * half-open interval {@code [cast, cast + 15)}.</p>
 */
public class HeartOfDepth extends ArtifactSet
        implements ActionTriggeredArtifactEffect, SimulatorInitializedArtifactEffect {
    private static final double ATTACK_BONUS_DURATION = 15.0;

    private Character owner;
    private CombatSimulator simulator;
    private double attackBonusUntil = Double.NEGATIVE_INFINITY;

    /** Constructs Heart of Depth with a fresh fixed-stat container. */
    public HeartOfDepth() {
        this(new StatsContainer());
    }

    /**
     * Constructs Heart of Depth while preserving supplied main and sub stats.
     *
     * @param stats non-null artifact main and sub stats
     */
    public HeartOfDepth(StatsContainer stats) {
        super("Heart of Depth", Objects.requireNonNull(stats, "stats"));
        getStats().add(StatType.HYDRO_DMG_BONUS, 0.15);
    }

    /**
     * Binds the set's action window to one owner and simulator.
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
                        "Heart of Depth is already bound to another simulator");
            }
            return;
        }
        owner = equippedOwner;
        simulator = sim;
    }

    /**
     * Opens or refreshes the attack bonus when the bound owner uses a Skill.
     *
     * <p>Unbound, null, or mismatched callbacks are inert. The simulator only
     * dispatches this callback after action gates pass, so an accepted callback
     * represents a successful action use.</p>
     *
     * @param user artifact owner supplied by the action dispatcher
     * @param request accepted typed action request
     * @param sim simulator dispatching the action
     */
    @Override
    public void onAction(
            Character user,
            CharacterActionRequest request,
            CombatSimulator sim) {
        if (owner == null
                || simulator == null
                || user != owner
                || sim != simulator
                || request == null
                || request.getKey() != CharacterActionKey.SKILL) {
            return;
        }
        attackBonusUntil = sim.getCurrentTime() + ATTACK_BONUS_DURATION;
    }

    /**
     * Applies the Normal and Charged Attack bonuses during the active window.
     *
     * @param totalStats aggregated stats container to mutate in place
     */
    @Override
    public void applyPassive(StatsContainer totalStats) {
        if (simulator == null || simulator.getCurrentTime() >= attackBonusUntil) {
            return;
        }
        totalStats.add(StatType.NORMAL_ATTACK_DMG_BONUS, 0.30);
        totalStats.add(StatType.CHARGED_ATTACK_DMG_BONUS, 0.30);
    }
}

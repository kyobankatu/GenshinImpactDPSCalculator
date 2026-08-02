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
 * Martial Artist artifact set with an Elemental Skill use damage window.
 *
 * <p>The fixed two-piece bonus grants 15% Normal and Charged Attack DMG Bonus.
 * A successful owner Skill action opens or refreshes the half-open interval
 * {@code [castTime, castTime + 8)}, during which the four-piece effect grants a
 * further 25% to both damage categories.</p>
 */
public class MartialArtist extends ArtifactSet
        implements ActionTriggeredArtifactEffect, SimulatorInitializedArtifactEffect {
    private static final double SKILL_WINDOW_DURATION = 8.0;
    private static final double TWO_PIECE_DAMAGE_BONUS = 0.15;
    private static final double FOUR_PIECE_DAMAGE_BONUS = 0.25;

    private Character owner;
    private CombatSimulator simulator;
    private double activeUntil = Double.NEGATIVE_INFINITY;

    /** Constructs Martial Artist with a fresh fixed-stat container. */
    public MartialArtist() {
        this(new StatsContainer());
    }

    /**
     * Constructs Martial Artist while preserving supplied main and sub stats.
     *
     * @param stats non-null artifact main and sub stats
     */
    public MartialArtist(StatsContainer stats) {
        super("Martial Artist", Objects.requireNonNull(stats, "stats"));
        getStats().add(StatType.NORMAL_ATTACK_DMG_BONUS, TWO_PIECE_DAMAGE_BONUS);
        getStats().add(StatType.CHARGED_ATTACK_DMG_BONUS, TWO_PIECE_DAMAGE_BONUS);
    }

    /**
     * Binds the action window to exactly one owner and simulator.
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
                        "Martial Artist is already bound to another simulator");
            }
            return;
        }
        owner = equippedOwner;
        simulator = sim;
    }

    /**
     * Opens or refreshes the eight-second four-piece window on owner Skill use.
     *
     * <p>Unbound, mismatched, null, and non-Skill callbacks are inert.</p>
     *
     * @param user character whose action passed simulator gates
     * @param request typed action request
     * @param sim simulator dispatching the action
     */
    @Override
    public void onAction(
            Character user,
            CharacterActionRequest request,
            CombatSimulator sim) {
        if (simulator == null
                || sim != simulator
                || user != owner
                || request == null
                || request.getKey() != CharacterActionKey.SKILL) {
            return;
        }
        activeUntil = sim.getCurrentTime() + SKILL_WINDOW_DURATION;
    }

    /**
     * Applies the four-piece Normal and Charged Attack bonuses while active.
     *
     * @param totalStats aggregated stats container to mutate in place
     */
    @Override
    public void applyPassive(StatsContainer totalStats) {
        if (owner == null || simulator == null
                || simulator.getCurrentTime() >= activeUntil) {
            return;
        }
        totalStats.add(StatType.NORMAL_ATTACK_DMG_BONUS, FOUR_PIECE_DAMAGE_BONUS);
        totalStats.add(StatType.CHARGED_ATTACK_DMG_BONUS, FOUR_PIECE_DAMAGE_BONUS);
    }
}

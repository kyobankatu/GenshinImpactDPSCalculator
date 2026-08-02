package model.artifact;

import java.util.Objects;

import mechanics.buff.BuffId;
import mechanics.buff.SimpleBuff;
import mechanics.reaction.ReactionResult;
import model.entity.ArtifactSet;
import model.entity.Character;
import model.entity.ReactionAwareArtifact;
import model.entity.SimulatorInitializedArtifactEffect;
import model.stats.StatsContainer;
import model.type.StatType;
import simulation.CombatSimulator;

/**
 * Instructor artifact set with an on-field reaction-triggered team EM buff.
 *
 * <p>The fixed two-piece bonus grants 80 Elemental Mastery. After the bound
 * owner triggers an elemental reaction while active, the four-piece effect
 * applies one non-stacking team buff granting 120 Elemental Mastery for the
 * half-open interval {@code [activation, activation + 8)}. Reaction artifact
 * callbacks run after resolution, so the triggering reaction is unaffected.</p>
 */
public class Instructor extends ArtifactSet
        implements SimulatorInitializedArtifactEffect, ReactionAwareArtifact {
    private static final double EFFECT_DURATION = 8.0;
    private static final double TEAM_ELEMENTAL_MASTERY_BONUS = 120.0;

    private Character owner;
    private CombatSimulator simulator;

    /** Constructs Instructor with a fresh fixed-stat container. */
    public Instructor() {
        this(new StatsContainer());
    }

    /**
     * Constructs Instructor while preserving supplied main/sub stats.
     *
     * @param stats non-null artifact main and sub stats
     */
    public Instructor(StatsContainer stats) {
        super("Instructor", Objects.requireNonNull(stats, "stats"));
        getStats().add(StatType.ELEMENTAL_MASTERY, 80.0);
    }

    /**
     * Binds the reaction effect to exactly one owner and simulator.
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
                        "Instructor is already bound to another simulator");
            }
            return;
        }
        owner = equippedOwner;
        simulator = sim;
    }

    /**
     * Applies or refreshes the team Elemental Mastery buff after an eligible reaction.
     *
     * @param sim simulator dispatching the resolved reaction
     * @param result resolved elemental reaction result
     * @param triggerCharacter character attributed as the reaction trigger
     * @param callbackOwner artifact wearer supplied by the dispatcher
     */
    @Override
    public void onReaction(
            CombatSimulator sim,
            ReactionResult result,
            Character triggerCharacter,
            Character callbackOwner) {
        if (simulator == null
                || sim != simulator
                || callbackOwner != owner
                || triggerCharacter != owner
                || sim.getActiveCharacter() != owner
                || result == null
                || result.getType() == null
                || result.getKind() == null
                || result.getType() == ReactionResult.Type.NONE
                || result.getKind() == ReactionResult.Kind.NONE) {
            return;
        }

        SimpleBuff teamElementalMastery = new SimpleBuff(
                "Instructor: Four-Piece Bonus",
                BuffId.INSTRUCTOR_4PC_TEAM_EM,
                EFFECT_DURATION,
                sim.getCurrentTime(),
                stats -> stats.add(
                        StatType.ELEMENTAL_MASTERY,
                        TEAM_ELEMENTAL_MASTERY_BONUS));
        sim.applyTeamBuffNoStack(teamElementalMastery.sourcedBy(owner.getCharacterId()));
    }
}

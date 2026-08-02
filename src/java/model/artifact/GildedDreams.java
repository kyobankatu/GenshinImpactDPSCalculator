package model.artifact;

import java.util.Objects;

import mechanics.buff.Buff;
import mechanics.reaction.ReactionResult;
import model.entity.ArtifactSet;
import model.entity.Character;
import model.entity.SimulatorInitializedArtifactEffect;
import model.stats.StatsContainer;
import model.type.Element;
import model.type.StatType;
import simulation.CombatSimulator;

/**
 * Gilded Dreams artifact set with an owner-reaction party-composition buff.
 *
 * <p>The two-piece bonus grants 80 Elemental Mastery. An eligible reaction
 * attributed to the owner snapshots up to three other party members and grants
 * 14% ATK per same-element ally plus 50 Elemental Mastery per different-element
 * ally for the half-open interval {@code [activation, activation + 8)}. The
 * listener runs after reaction resolution, so ordinary triggering reactions do
 * not consume the newly granted stats.</p>
 */
public class GildedDreams extends ArtifactSet
        implements SimulatorInitializedArtifactEffect, CombatSimulator.ReactionListener {
    private static final double EFFECT_DURATION = 8.0;
    private static final double ATTACK_BONUS_PER_MATCHING_ALLY = 0.14;
    private static final double ELEMENTAL_MASTERY_PER_DIFFERENT_ALLY = 50.0;
    private static final int MAX_ALLIES = 3;

    private Character owner;
    private CombatSimulator simulator;
    private double nextActivationTime = Double.NEGATIVE_INFINITY;

    /** Constructs Gilded Dreams with a fresh fixed-stat container. */
    public GildedDreams() {
        this(new StatsContainer());
    }

    /**
     * Constructs Gilded Dreams while preserving supplied main/sub stats.
     *
     * @param stats non-null artifact main and sub stats
     */
    public GildedDreams(StatsContainer stats) {
        super("Gilded Dreams", Objects.requireNonNull(stats, "stats"));
        getStats().add(StatType.ELEMENTAL_MASTERY, 80.0);
    }

    /**
     * Binds the set to one owner and registers its post-resolution listener.
     *
     * <p>Repeating the identical binding is idempotent. Reusing one stateful
     * artifact instance for another owner or simulator is rejected.</p>
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
                        "Gilded Dreams is already bound to another simulator");
            }
            return;
        }
        owner = equippedOwner;
        simulator = sim;
        sim.addReactionListener(this);
    }

    /**
     * Activates one composition snapshot after an attributed elemental reaction.
     *
     * <p>Invalid, unattributed, cross-simulator, and cooldown-period callbacks do
     * not consume the cooldown. Because this is a post-resolution listener, the
     * new stats affect subsequent calculations; Hyperbloom can still observe the
     * buff when its immediate seed damage is resolved after listener dispatch.</p>
     *
     * @param result resolved reaction result
     * @param source character attributed as the reaction trigger
     * @param time reaction time in simulation seconds
     * @param sim simulator dispatching the reaction
     */
    @Override
    public void onReaction(
            ReactionResult result,
            Character source,
            double time,
            CombatSimulator sim) {
        if (simulator == null
                || sim != simulator
                || source != owner
                || result == null
                || result.getType() == null
                || result.getKind() == null
                || result.getType() == ReactionResult.Type.NONE
                || result.getKind() == ReactionResult.Kind.NONE
                || time < nextActivationTime) {
            return;
        }

        int matchingAllies = 0;
        int differentAllies = 0;
        int consideredAllies = 0;
        Element ownerElement = owner.getElement();
        for (Character member : simulator.getPartyMembers()) {
            if (member == owner || consideredAllies >= MAX_ALLIES) {
                continue;
            }
            Element memberElement = member.getElement();
            if (ownerElement == null || memberElement == null) {
                continue;
            }
            if (memberElement == ownerElement) {
                matchingAllies++;
            } else {
                differentAllies++;
            }
            consideredAllies++;
        }

        owner.addBuff(new CompositionBuff(time, matchingAllies, differentAllies)
                .sourcedBy(owner.getCharacterId()));
        nextActivationTime = time + EFFECT_DURATION;
    }

    /** One immutable party-composition snapshot for a single effect window. */
    private static class CompositionBuff extends Buff {
        private final int matchingAllies;
        private final int differentAllies;

        private CompositionBuff(double activationTime, int matchingAllies, int differentAllies) {
            super("Gilded Dreams: Four-Piece Bonus", EFFECT_DURATION, activationTime);
            this.matchingAllies = matchingAllies;
            this.differentAllies = differentAllies;
        }

        @Override
        protected void applyStats(StatsContainer stats, double currentTime) {
            stats.add(StatType.ATK_PERCENT,
                    ATTACK_BONUS_PER_MATCHING_ALLY * matchingAllies);
            stats.add(StatType.ELEMENTAL_MASTERY,
                    ELEMENTAL_MASTERY_PER_DIFFERENT_ALLY * differentAllies);
        }
    }
}

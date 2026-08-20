package model.artifact;

import java.util.Objects;

import mechanics.buff.BuffId;
import mechanics.buff.SimpleBuff;
import mechanics.reaction.ReactionResult;
import model.entity.ArtifactSet;
import model.entity.Character;
import model.entity.ReactionAwareArtifact;
import model.stats.StatsContainer;
import model.type.StatType;
import simulation.CombatSimulator;

/** Scarlet Proof artifact set with an owner-triggered Stellar-Swirl window. */
public class ScarletProof extends ArtifactSet implements ReactionAwareArtifact {
    private static final double EFFECT_DURATION = 10.0;

    /** Constructs the set with only its fixed two-piece bonus. */
    public ScarletProof() {
        this(new StatsContainer());
    }

    /** Constructs the set while preserving supplied main and sub stats. */
    public ScarletProof(StatsContainer stats) {
        super("Scarlet Proof", Objects.requireNonNull(stats, "stats"));
        getStats().add(StatType.ATK_PERCENT, 0.18);
    }

    /** Applies or refreshes the owner-only four-piece window after Stellar-Swirl. */
    @Override
    public void onReaction(
            CombatSimulator sim,
            ReactionResult result,
            Character triggerCharacter,
            Character owner) {
        if (result == null
                || result.getKind() != ReactionResult.Kind.STELLAR_SWIRL
                || triggerCharacter != owner) {
            return;
        }
        owner.removeBuff(BuffId.SCARLET_PROOF_STELLAR_SWIRL);
        owner.addBuff(new SimpleBuff(
                "Scarlet Proof: Four-Piece Bonus",
                BuffId.SCARLET_PROOF_STELLAR_SWIRL,
                EFFECT_DURATION,
                sim.getCurrentTime(),
                stats -> {
                    stats.add(StatType.CRIT_RATE, 0.16);
                    stats.add(StatType.STELLAR_SWIRL_DMG_BONUS, 0.40);
                }).sourcedBy(owner.getCharacterId()));
    }
}

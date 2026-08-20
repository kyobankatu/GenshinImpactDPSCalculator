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

/** Heart of the Furnace artifact set with owner and party Stellar windows. */
public class HeartOfTheFurnace extends ArtifactSet
        implements ReactionAwareArtifact {
    private static final double EFFECT_DURATION = 12.0;

    /** Constructs the set with only its fixed two-piece bonus. */
    public HeartOfTheFurnace() {
        this(new StatsContainer());
    }

    /** Constructs the set while preserving supplied main and sub stats. */
    public HeartOfTheFurnace(StatsContainer stats) {
        super("Heart of the Furnace", Objects.requireNonNull(stats, "stats"));
        getStats().add(StatType.ATK_PERCENT, 0.18);
    }

    /** Refreshes both four-piece windows for Stellar events attributed to the owner. */
    @Override
    public void onReaction(
            CombatSimulator sim,
            ReactionResult result,
            Character triggerCharacter,
            Character owner) {
        if (result == null
                || !result.isStellarReaction()
                || triggerCharacter != owner) {
            return;
        }
        owner.removeBuff(BuffId.HEART_OF_THE_FURNACE_OWNER_ATK);
        owner.addBuff(new SimpleBuff(
                "Heart of the Furnace: Owner ATK",
                BuffId.HEART_OF_THE_FURNACE_OWNER_ATK,
                EFFECT_DURATION,
                sim.getCurrentTime(),
                stats -> stats.add(StatType.ATK_PERCENT, 0.12))
                .sourcedBy(owner.getCharacterId()));

        SimpleBuff teamStellarDamage = new SimpleBuff(
                "Heart of the Furnace: Team Stellar DMG",
                BuffId.HEART_OF_THE_FURNACE_TEAM_STELLAR_DMG,
                EFFECT_DURATION,
                sim.getCurrentTime(),
                stats -> {
                    stats.add(StatType.STELLAR_CONDUCT_DMG_BONUS, 0.50);
                    stats.add(StatType.STELLAR_SWIRL_DMG_BONUS, 0.50);
                });
        sim.applyTeamBuffNoStack(
                teamStellarDamage.sourcedBy(owner.getCharacterId()));
    }
}

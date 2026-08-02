package model.artifact;

import java.util.Objects;

import mechanics.buff.Buff;
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
 * Flower of Paradise Lost with independently expiring reaction stacks.
 *
 * <p>The fixed two-piece bonus grants 80 Elemental Mastery. Its four-piece
 * effect grants 40% Bloom, Hyperbloom, and Burgeon DMG plus 10% Lunar-Bloom
 * DMG. After the bound owner triggers one of those reactions, one ten-second
 * stack grants another quarter of each fixed bonus. Up to four stacks can be
 * gained, subject to a one-second trigger cooldown, even while off field.</p>
 */
public class FlowerOfParadiseLost extends ArtifactSet
        implements SimulatorInitializedArtifactEffect, ReactionAwareArtifact {
    private static final int MAX_STACKS = 4;
    private static final double STACK_DURATION = 10.0;
    private static final double TRIGGER_COOLDOWN = 1.0;
    private static final double STANDARD_BASE_BONUS = 0.40;
    private static final double LUNAR_BASE_BONUS = 0.10;
    private static final double STANDARD_STACK_BONUS = 0.10;
    private static final double LUNAR_STACK_BONUS = 0.025;

    private Character owner;
    private CombatSimulator simulator;

    /** Constructs Flower of Paradise Lost with fresh fixed stats. */
    public FlowerOfParadiseLost() {
        this(new StatsContainer());
    }

    /**
     * Constructs Flower of Paradise Lost while preserving supplied stats.
     *
     * @param stats non-null artifact main and sub stats
     */
    public FlowerOfParadiseLost(StatsContainer stats) {
        super("Flower of Paradise Lost", Objects.requireNonNull(stats, "stats"));
        getStats().add(StatType.ELEMENTAL_MASTERY, 80.0);
        getStats().add(StatType.BLOOM_DMG_BONUS, STANDARD_BASE_BONUS);
        getStats().add(StatType.HYPERBLOOM_DMG_BONUS, STANDARD_BASE_BONUS);
        getStats().add(StatType.BURGEON_DMG_BONUS, STANDARD_BASE_BONUS);
        getStats().add(StatType.LUNAR_BLOOM_DMG_BONUS, LUNAR_BASE_BONUS);
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
                        "Flower of Paradise Lost is already bound");
            }
            return;
        }
        owner = equippedOwner;
        simulator = sim;
    }

    /**
     * Adds one post-reaction stack after an eligible owner reaction.
     *
     * <p>The callback runs after reaction stats and damage are resolved, so the
     * triggering reaction receives only bonuses that were already active.</p>
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
        if (!matchesBinding(callbackOwner, sim)
                || triggerCharacter != owner
                || !isEligibleReaction(result)) {
            return;
        }

        double currentTime = sim.getCurrentTime();
        if (hasActiveBuff(
                BuffId.FLOWER_OF_PARADISE_LOST_TRIGGER_COOLDOWN,
                currentTime)) {
            return;
        }

        owner.addBuff(new SimpleBuff(
                "Flower of Paradise Lost: Trigger Cooldown",
                BuffId.FLOWER_OF_PARADISE_LOST_TRIGGER_COOLDOWN,
                TRIGGER_COOLDOWN,
                currentTime,
                stats -> {
                }).sourcedBy(owner.getCharacterId()));
        if (activeStackCount(currentTime) >= MAX_STACKS) {
            return;
        }

        owner.addBuff(new SimpleBuff(
                "Flower of Paradise Lost: Four-Piece Stack",
                BuffId.FLOWER_OF_PARADISE_LOST_STACK,
                STACK_DURATION,
                currentTime,
                stats -> {
                    stats.add(StatType.BLOOM_DMG_BONUS, STANDARD_STACK_BONUS);
                    stats.add(StatType.HYPERBLOOM_DMG_BONUS, STANDARD_STACK_BONUS);
                    stats.add(StatType.BURGEON_DMG_BONUS, STANDARD_STACK_BONUS);
                    stats.add(StatType.LUNAR_BLOOM_DMG_BONUS, LUNAR_STACK_BONUS);
                }).sourcedBy(owner.getCharacterId()));
    }

    /** Returns whether a callback belongs to the initialized artifact binding. */
    private boolean matchesBinding(
            Character callbackOwner,
            CombatSimulator callbackSimulator) {
        return owner != null
                && simulator != null
                && owner == callbackOwner
                && simulator == callbackSimulator;
    }

    /** Returns whether the resolved reaction can grant one four-piece stack. */
    private boolean isEligibleReaction(ReactionResult result) {
        if (result == null
                || result.getType() != ReactionResult.Type.TRANSFORMATIVE
                || result.getKind() == null) {
            return false;
        }
        switch (result.getKind()) {
            case BLOOM:
            case HYPERBLOOM:
            case BURGEON:
            case LUNAR_BLOOM:
                return true;
            default:
                return false;
        }
    }

    /** Counts independently expiring four-piece stacks active at one time. */
    private int activeStackCount(double currentTime) {
        int count = 0;
        for (Buff buff : owner.getActiveBuffs()) {
            if (buff.getId() == BuffId.FLOWER_OF_PARADISE_LOST_STACK
                    && !buff.isExpired(currentTime)) {
                count++;
            }
        }
        return count;
    }

    /** Returns whether one typed owner buff is active at the supplied time. */
    private boolean hasActiveBuff(BuffId id, double currentTime) {
        for (Buff buff : owner.getActiveBuffs()) {
            if (buff.getId() == id && !buff.isExpired(currentTime)) {
                return true;
            }
        }
        return false;
    }
}

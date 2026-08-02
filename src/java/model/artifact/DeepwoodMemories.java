package model.artifact;

import java.util.Objects;

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
 * Deepwood Memories artifact set with Skill/Burst-triggered Dendro RES shred.
 *
 * <p>The fixed two-piece bonus grants 15% Dendro DMG Bonus. After a Skill or
 * Burst hit from the bound owner, including an off-field or zero-damage hit,
 * the four-piece effect applies one non-stacking team buff granting 30% Dendro
 * RES shred for the half-open interval {@code [activation, activation + 8)}.
 * Damage callbacks run after resolution, so the triggering hit is unaffected.</p>
 */
public class DeepwoodMemories extends ArtifactSet
        implements SimulatorInitializedArtifactEffect, DamageTriggeredArtifactEffect {
    private static final double EFFECT_DURATION = 8.0;
    private static final double DENDRO_RESISTANCE_SHRED = 0.30;

    private Character owner;
    private CombatSimulator simulator;

    /** Constructs Deepwood Memories with a fresh fixed-stat container. */
    public DeepwoodMemories() {
        this(new StatsContainer());
    }

    /**
     * Constructs Deepwood Memories while preserving supplied main/sub stats.
     *
     * @param stats non-null artifact main and sub stats
     */
    public DeepwoodMemories(StatsContainer stats) {
        super("Deepwood Memories", Objects.requireNonNull(stats, "stats"));
        getStats().add(StatType.DENDRO_DMG_BONUS, 0.15);
    }

    /**
     * Binds the damage-triggered effect to exactly one owner and simulator.
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
                        "Deepwood Memories is already bound to another simulator");
            }
            return;
        }
        owner = equippedOwner;
        simulator = sim;
    }

    /**
     * Applies or refreshes Dendro RES shred after an eligible owner hit.
     *
     * @param sim simulator dispatching the resolved damage
     * @param action attack action that produced the hit
     * @param damage final post-mitigation damage, which may be zero
     * @param callbackOwner artifact wearer supplied by the damage dispatcher
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
                || !isSkillOrBurstDamage(action)) {
            return;
        }

        SimpleBuff dendroResistanceShred = new SimpleBuff(
                "Deepwood Memories: Four-Piece Bonus",
                BuffId.DEEPWOOD_MEMORIES_4PC_SHRED,
                EFFECT_DURATION,
                sim.getCurrentTime(),
                stats -> stats.add(
                        StatType.DENDRO_RES_SHRED,
                        DENDRO_RESISTANCE_SHRED));
        sim.applyTeamBuffNoStack(dendroResistanceShred.sourcedBy(owner.getCharacterId()));
    }

    /** Returns whether an action is classified as Skill or Burst damage. */
    private boolean isSkillOrBurstDamage(AttackAction action) {
        return action.getActionType() == ActionType.SKILL
                || action.getActionType() == ActionType.BURST
                || action.isCountsAsSkillDmg()
                || action.isCountsAsBurstDmg();
    }
}

package model.artifact;

import java.util.Objects;

import mechanics.buff.Buff;
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
 * Pale Flame artifact set with positive Elemental Skill hit stacks.
 *
 * <p>The two-piece bonus grants 25% Physical DMG Bonus. Eligible owner Skill
 * hits add up to two 9% ATK stacks with a shared 0.3-second trigger cooldown.
 * Every successful addition resets the shared half-open seven-second window;
 * reaching two stacks grants a further 25% Physical DMG Bonus.</p>
 */
public class PaleFlame extends ArtifactSet
        implements SimulatorInitializedArtifactEffect, DamageTriggeredArtifactEffect {
    private static final double STACK_COOLDOWN = 0.3;
    private static final double STACK_DURATION = 7.0;
    private static final double ATTACK_BONUS_PER_STACK = 0.09;
    private static final double MAX_STACK_PHYSICAL_BONUS = 0.25;
    private static final int MAX_STACKS = 2;

    private Character owner;
    private CombatSimulator simulator;
    private int stackCount;
    private double stackExpiration = Double.NEGATIVE_INFINITY;
    private double nextStackTime = Double.NEGATIVE_INFINITY;
    private StackBuff stackBuff;

    /** Constructs Pale Flame with a fresh fixed-stat container. */
    public PaleFlame() {
        this(new StatsContainer());
    }

    /**
     * Constructs Pale Flame while preserving supplied main/sub stats.
     *
     * @param stats non-null artifact main and sub stats
     */
    public PaleFlame(StatsContainer stats) {
        super("Pale Flame", Objects.requireNonNull(stats, "stats"));
        getStats().add(StatType.PHYSICAL_DMG_BONUS, 0.25);
    }

    /**
     * Binds the stateful stack counter to one owner and simulator.
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
                        "Pale Flame is already bound to another simulator");
            }
            return;
        }
        owner = equippedOwner;
        simulator = sim;
    }

    /**
     * Adds one stack after an attributed positive Elemental Skill hit.
     *
     * <p>Non-Skill and unclassified proc-style {@link ActionType#OTHER} actions,
     * non-positive damage, wrong bindings, cooldown callbacks, and hits at the
     * active cap are inert. Only an actual stack addition refreshes the shared
     * duration.</p>
     *
     * @param sim simulator dispatching the resolved damage
     * @param action action that produced the damage
     * @param damage final post-mitigation damage
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
                || !(damage > 0.0)
                || !isElementalSkillDamage(action)) {
            return;
        }

        double currentTime = sim.getCurrentTime();
        if (currentTime < nextStackTime) {
            return;
        }
        if (currentTime >= stackExpiration) {
            stackCount = 0;
        }
        if (stackCount >= MAX_STACKS) {
            return;
        }

        stackCount++;
        stackExpiration = currentTime + STACK_DURATION;
        nextStackTime = currentTime + STACK_COOLDOWN;
        if (stackBuff == null) {
            stackBuff = new StackBuff(currentTime);
            owner.addBuff(stackBuff.sourcedBy(owner.getCharacterId()));
        } else {
            stackBuff.refresh(currentTime);
        }
    }

    /** Returns whether an action is classified as Elemental Skill damage. */
    private boolean isElementalSkillDamage(AttackAction action) {
        return action.getActionType() == ActionType.SKILL || action.isCountsAsSkillDmg();
    }

    /** One reusable buff whose expiry follows the shared stack window. */
    private class StackBuff extends Buff {
        private StackBuff(double activationTime) {
            super("Pale Flame: Four-Piece Bonus", STACK_DURATION, activationTime);
        }

        private void refresh(double currentTime) {
            restoreTimes(currentTime, currentTime + STACK_DURATION);
        }

        @Override
        protected void applyStats(StatsContainer stats, double currentTime) {
            stats.add(StatType.ATK_PERCENT, ATTACK_BONUS_PER_STACK * stackCount);
            if (stackCount >= MAX_STACKS) {
                stats.add(StatType.PHYSICAL_DMG_BONUS, MAX_STACK_PHYSICAL_BONUS);
            }
        }
    }
}

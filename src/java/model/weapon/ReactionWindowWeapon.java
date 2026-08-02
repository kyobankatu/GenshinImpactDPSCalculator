package model.weapon;

import java.util.Objects;
import java.util.function.Predicate;

import mechanics.reaction.ReactionResult;
import model.entity.Character;
import model.entity.SimulatorInitializedWeaponEffect;
import model.entity.Weapon;
import model.stats.StatsContainer;
import model.type.StatType;
import model.type.WeaponType;
import simulation.CombatSimulator;

/**
 * Shared owner-reaction stat window with shared-duration stack handling.
 */
public abstract class ReactionWindowWeapon extends Weapon
        implements SimulatorInitializedWeaponEffect, CombatSimulator.ReactionListener {
    private final int refinement;
    private final Predicate<ReactionResult> eligibility;
    private final StatType[] bonusStats;
    private final double bonusPerStack;
    private final double duration;
    private final int maxStacks;

    private Character owner;
    private boolean initialized;
    private int stackCount;
    private double expiration = Double.NEGATIVE_INFINITY;

    /**
     * Constructs one reaction-window weapon.
     *
     * @param name weapon display name
     * @param weaponType weapon category
     * @param baseAtk Lv. 90 base ATK
     * @param secondaryStat secondary stat type
     * @param secondaryValue Lv. 90 secondary stat value
     * @param refinement refinement rank
     * @param maxRefinement highest supported refinement rank
     * @param eligibility eligible reaction predicate
     * @param bonusPerStack stat bonus supplied by each stack
     * @param duration shared stack duration in seconds
     * @param maxStacks maximum active stacks
     * @param bonusStats stats receiving the per-stack bonus
     */
    protected ReactionWindowWeapon(
            String name,
            WeaponType weaponType,
            double baseAtk,
            StatType secondaryStat,
            double secondaryValue,
            int refinement,
            int maxRefinement,
            Predicate<ReactionResult> eligibility,
            double bonusPerStack,
            double duration,
            int maxStacks,
            StatType... bonusStats) {
        super(name, new StatsContainer());
        if (refinement < 1 || refinement > maxRefinement) {
            throw new IllegalArgumentException(
                    "Reaction-window weapon refinement must be between 1 and "
                            + maxRefinement);
        }
        if (duration <= 0.0 || maxStacks < 1 || bonusStats.length == 0) {
            throw new IllegalArgumentException("Reaction-window definition must be positive");
        }
        this.refinement = refinement;
        this.eligibility = Objects.requireNonNull(eligibility, "eligibility");
        this.bonusStats = bonusStats.clone();
        this.bonusPerStack = bonusPerStack;
        this.duration = duration;
        this.maxStacks = maxStacks;
        this.weaponType = weaponType;
        getStats().set(StatType.BASE_ATK, baseAtk);
        getStats().set(secondaryStat, secondaryValue);
    }

    /**
     * Registers this weapon for attributed reaction events.
     *
     * @param equippedOwner owner equipped with this weapon
     * @param sim simulator containing the owner
     */
    @Override
    public final void initializeForSimulator(Character equippedOwner, CombatSimulator sim) {
        if (initialized) {
            return;
        }
        owner = equippedOwner;
        sim.addReactionListener(this);
        initialized = true;
    }

    /**
     * Returns this weapon's refinement rank.
     *
     * @return supported refinement rank
     */
    public final int getRefinement() {
        return refinement;
    }

    /**
     * Applies every active shared-duration stack.
     *
     * @param stats stats container to mutate
     * @param currentTime simulation time in seconds
     */
    @Override
    public final void applyPassive(StatsContainer stats, double currentTime) {
        if (currentTime >= expiration) {
            return;
        }
        for (StatType stat : bonusStats) {
            stats.add(stat, bonusPerStack * stackCount);
        }
    }

    /**
     * Gains and refreshes a stack for an eligible active-owner reaction.
     *
     * @param result reaction result
     * @param source triggering character
     * @param time reaction time in simulation seconds
     * @param sim active simulator
     */
    @Override
    public final void onReaction(
            ReactionResult result,
            Character source,
            double time,
            CombatSimulator sim) {
        if (source != owner
                || sim.getActiveCharacter() != owner
                || !eligibility.test(result)) {
            return;
        }
        if (time >= expiration) {
            stackCount = 0;
        }
        stackCount = Math.min(maxStacks, stackCount + 1);
        expiration = time + duration;
    }
}

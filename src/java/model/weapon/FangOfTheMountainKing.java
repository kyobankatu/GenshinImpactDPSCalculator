package model.weapon;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import mechanics.reaction.ReactionResult;
import model.entity.Character;
import model.entity.DamageTriggeredWeaponEffect;
import model.entity.ElementalReactionTriggeredWeaponEffect;
import model.entity.SimulatorInitializedWeaponEffect;
import model.entity.SnapshotAwareWeaponEffect;
import model.entity.Weapon;
import model.stats.StatsContainer;
import model.type.ActionType;
import model.type.StatType;
import model.type.WeaponType;
import simulation.CombatSimulator;
import simulation.action.AttackAction;

/** Fang of the Mountain King with independently expiring Canopy's Favor stacks. */
public final class FangOfTheMountainKing extends Weapon
        implements DamageTriggeredWeaponEffect,
        ElementalReactionTriggeredWeaponEffect,
        SimulatorInitializedWeaponEffect,
        SnapshotAwareWeaponEffect {
    private static final int MAX_STACKS = 6;
    private static final double STACK_DURATION = 6.0;
    private static final double SKILL_STACK_COOLDOWN = 0.5;
    private static final double REACTION_STACK_COOLDOWN = 2.0;

    private final int refinement;
    private final double damageBonusPerStack;
    private final List<Double> stackExpirations = new ArrayList<>();
    private Character owner;
    private CombatSimulator simulator;
    private double nextSkillStackAt = Double.NEGATIVE_INFINITY;
    private double nextReactionStackAt = Double.NEGATIVE_INFINITY;

    /** Constructs Fang of the Mountain King at refinement rank five. */
    public FangOfTheMountainKing() {
        this(5);
    }

    /**
     * Constructs Fang of the Mountain King at a selected refinement.
     *
     * @param refinement refinement rank in the inclusive range 1-5
     */
    public FangOfTheMountainKing(int refinement) {
        super("Fang of the Mountain King", new StatsContainer());
        if (refinement < 1 || refinement > 5) {
            throw new IllegalArgumentException(
                    "Fang refinement must be between 1 and 5");
        }
        this.refinement = refinement;
        this.damageBonusPerStack = 0.10 + 0.025 * (refinement - 1);
        weaponType = WeaponType.CLAYMORE;
        getStats().set(StatType.BASE_ATK, 741.0);
        getStats().set(StatType.CRIT_RATE, 0.110);
    }

    /** Returns this weapon's refinement rank. */
    public int getRefinement() {
        return refinement;
    }

    /** Returns the Skill and Burst damage bonus granted by each stack. */
    public double getDamageBonusPerStack() {
        return damageBonusPerStack;
    }

    /** Returns the number of stacks alive at the supplied time. */
    public int getStackCount(double currentTime) {
        expireStacks(currentTime);
        return stackExpirations.size();
    }

    /** Binds one instance and registers actual reaction notifications. */
    @Override
    public void initializeForSimulator(Character equippedOwner, CombatSimulator sim) {
        if (equippedOwner == null || sim == null) {
            throw new IllegalArgumentException("Weapon owner and simulator are required");
        }
        if (simulator != null) {
            if (owner != equippedOwner || simulator != sim) {
                throw new IllegalStateException(
                        "Fang is already bound to another simulator");
            }
            return;
        }
        if (equippedOwner.getWeapon() != this) {
            throw new IllegalArgumentException(
                    "Weapon owner must have this Fang equipped");
        }
        owner = equippedOwner;
        simulator = sim;
        sim.addElementalReactionTriggeredWeaponEffect(this);
    }

    /** Gains one stack after an on-field owner Skill hit, once per 0.5 seconds. */
    @Override
    public void onDamage(
            Character user,
            AttackAction action,
            double currentTime,
            CombatSimulator sim) {
        if (user == owner
                && sim == simulator
                && sim.getActiveCharacter() == owner
                && action != null
                && action.isHitEffectTrigger()
                && action.getDamagePercent() > 0.0
                && (action.getActionType() == ActionType.SKILL
                        || action.isCountsAsSkillDmg())
                && currentTime >= nextSkillStackAt) {
            addStacks(1, currentTime);
            nextSkillStackAt = currentTime + SKILL_STACK_COOLDOWN;
        }
    }

    /** Gains three stacks after any party member triggers Burning or Burgeon. */
    @Override
    public void onElementalReaction(
            ReactionResult result,
            Character source,
            double time,
            CombatSimulator sim) {
        if (sim != simulator
                || result == null
                || source == null
                || !sim.getPartyMembers().contains(source)
                || time < nextReactionStackAt
                || (result.getKind() != ReactionResult.Kind.BURNING
                        && result.getKind() != ReactionResult.Kind.BURGEON)) {
            return;
        }
        addStacks(3, time);
        nextReactionStackAt = time + REACTION_STACK_COOLDOWN;
    }

    /** Applies the active stack count to Skill and Burst damage. */
    @Override
    public void applyPassive(StatsContainer stats, double currentTime) {
        int stackCount = getStackCount(currentTime);
        stats.add(StatType.SKILL_DMG_BONUS, damageBonusPerStack * stackCount);
        stats.add(StatType.BURST_DMG_BONUS, damageBonusPerStack * stackCount);
    }

    /** Captures all independent expirations and both acquisition cooldowns. */
    @Override
    public State captureWeaponState() {
        return new FangState(
                this,
                stackExpirations,
                nextSkillStackAt,
                nextReactionStackAt);
    }

    /** Restores only immutable state captured from this exact weapon instance. */
    @Override
    public void restoreWeaponState(State state) {
        if (!(state instanceof FangState)) {
            throw new IllegalArgumentException("Fang state type is invalid");
        }
        FangState fangState = (FangState) state;
        if (fangState.source != this) {
            throw new IllegalArgumentException(
                    "Fang state belongs to another weapon instance");
        }
        stackExpirations.clear();
        stackExpirations.addAll(fangState.stackExpirations);
        nextSkillStackAt = fangState.nextSkillStackAt;
        nextReactionStackAt = fangState.nextReactionStackAt;
    }

    private void addStacks(int count, double currentTime) {
        expireStacks(currentTime);
        for (int i = 0; i < count; i++) {
            double expiration = currentTime + STACK_DURATION;
            if (stackExpirations.size() < MAX_STACKS) {
                stackExpirations.add(expiration);
            } else {
                stackExpirations.set(0, expiration);
            }
            Collections.sort(stackExpirations);
        }
    }

    private void expireStacks(double currentTime) {
        stackExpirations.removeIf(expiration -> currentTime >= expiration);
    }

    /** Immutable independent-stack state tied to one weapon instance. */
    private static final class FangState implements State {
        private final FangOfTheMountainKing source;
        private final List<Double> stackExpirations;
        private final double nextSkillStackAt;
        private final double nextReactionStackAt;

        private FangState(
                FangOfTheMountainKing source,
                List<Double> stackExpirations,
                double nextSkillStackAt,
                double nextReactionStackAt) {
            this.source = source;
            this.stackExpirations = new ArrayList<>(stackExpirations);
            this.nextSkillStackAt = nextSkillStackAt;
            this.nextReactionStackAt = nextReactionStackAt;
        }
    }
}

package model.weapon;

import model.entity.Character;
import model.entity.SimulatorInitializedWeaponEffect;
import model.entity.SnapshotAwareWeaponEffect;
import model.entity.Weapon;
import model.stats.StatsContainer;
import model.type.ActionType;
import model.type.StatType;
import model.type.WeaponType;
import simulation.CombatSimulator;
import simulation.DamageListener;
import simulation.action.AttackAction;

/**
 * The Daybreak Chronicles' non-Hexerei Stirring Dawn Breeze contract.
 *
 * <p>Lv. 90 metadata, category-isolated stacks, and R1-R5 values follow pinned
 * gcsim {@code ef41805d}. Normal, Skill, and Burst each start at six stacks,
 * lose one stack per second, and gain one stack after their owner's matching
 * positive hit while restarting that category's decay. Hexerei's two-stack
 * gain remains inactive because the simulator has no typed Hexerei party
 * state.</p>
 */
public final class TheDaybreakChronicles extends Weapon implements
        DamageListener,
        SimulatorInitializedWeaponEffect,
        SnapshotAwareWeaponEffect {
    private static final int MAX_STACKS = 6;
    private static final double DECAY_INTERVAL = 1.0;

    private final int refinement;
    private final double damageBonusPerStack;
    private Character owner;
    private CombatSimulator simulator;
    private CategoryState normalState = new CategoryState();
    private CategoryState skillState = new CategoryState();
    private CategoryState burstState = new CategoryState();

    /** Constructs The Daybreak Chronicles at refinement rank five. */
    public TheDaybreakChronicles() {
        this(5);
    }

    /**
     * Constructs The Daybreak Chronicles at the selected refinement rank.
     *
     * @param refinement refinement rank in the inclusive range 1-5
     */
    public TheDaybreakChronicles(int refinement) {
        super("The Daybreak Chronicles", new StatsContainer());
        if (refinement < 1 || refinement > 5) {
            throw new IllegalArgumentException(
                    "The Daybreak Chronicles refinement must be between 1 and 5");
        }
        this.refinement = refinement;
        damageBonusPerStack = 0.075 + 0.025 * refinement;
        weaponType = WeaponType.BOW;
        getStats().set(StatType.BASE_ATK, 674.0);
        getStats().set(StatType.CRIT_DMG, 0.441);
    }

    /** Returns the selected refinement rank. */
    public int getRefinement() {
        return refinement;
    }

    /** Returns generic category damage granted by one live stack. */
    public double getDamageBonusPerStack() {
        return damageBonusPerStack;
    }

    /** Returns live Normal stacks after applying elapsed decay. */
    public int getNormalStacks(double currentTime) {
        return normalize(normalState, currentTime);
    }

    /** Returns live Skill stacks after applying elapsed decay. */
    public int getSkillStacks(double currentTime) {
        return normalize(skillState, currentTime);
    }

    /** Returns live Burst stacks after applying elapsed decay. */
    public int getBurstStacks(double currentTime) {
        return normalize(burstState, currentTime);
    }

    /** Applies each category's independently decaying damage bonus. */
    @Override
    public void applyPassive(StatsContainer stats, double currentTime) {
        addCategoryBonus(
                stats,
                StatType.NORMAL_ATTACK_DMG_BONUS,
                getNormalStacks(currentTime));
        addCategoryBonus(
                stats,
                StatType.SKILL_DMG_BONUS,
                getSkillStacks(currentTime));
        addCategoryBonus(
                stats,
                StatType.BURST_DMG_BONUS,
                getBurstStacks(currentTime));
    }

    /** Binds one equipped owner and resolved-damage listener. */
    @Override
    public void initializeForSimulator(
            Character equippedOwner,
            CombatSimulator activeSimulator) {
        if (equippedOwner == null || activeSimulator == null) {
            throw new IllegalArgumentException(
                    "The Daybreak Chronicles owner and simulator are required");
        }
        if (simulator != null) {
            if (owner != equippedOwner || simulator != activeSimulator) {
                throw new IllegalStateException(
                        "The Daybreak Chronicles is already bound elsewhere");
            }
            return;
        }
        if (equippedOwner.getWeapon() != this) {
            throw new IllegalArgumentException(
                    "Owner must have The Daybreak Chronicles equipped");
        }
        if (!activeSimulator.getPartyMembers().contains(equippedOwner)) {
            throw new IllegalArgumentException(
                    "Owner must belong to the target simulator party");
        }
        owner = equippedOwner;
        simulator = activeSimulator;
        double firstDecayAt = activeSimulator.getCurrentTime()
                + DECAY_INTERVAL;
        normalState.nextDecayAt = firstDecayAt;
        skillState.nextDecayAt = firstDecayAt;
        burstState.nextDecayAt = firstDecayAt;
        activeSimulator.addDamageListener(this);
    }

    /** Adds one post-hit stack and restarts only the matching decay timer. */
    @Override
    public void onDamage(
            Character actor,
            AttackAction action,
            double damage,
            double currentTime) {
        if (!isBoundOwner(actor)
                || action == null
                || !action.isHitEffectTrigger()
                || action.getDamagePercent() <= 0.0
                || damage <= 0.0) {
            return;
        }
        CategoryState state = stateFor(action.getActionType());
        if (state == null) {
            return;
        }
        normalize(state, currentTime);
        state.stacks = Math.min(MAX_STACKS, state.stacks + 1);
        state.nextDecayAt = currentTime + DECAY_INTERVAL;
    }

    /** Captures all three category stack and decay boundaries. */
    @Override
    public State captureWeaponState() {
        return new DaybreakState(
                this, normalState, skillState, burstState);
    }

    /** Restores state captured from this exact weapon instance. */
    @Override
    public void restoreWeaponState(State state) {
        if (!(state instanceof DaybreakState)) {
            throw new IllegalArgumentException(
                    "The Daybreak Chronicles state type is invalid");
        }
        DaybreakState restored = (DaybreakState) state;
        if (restored.source != this) {
            throw new IllegalArgumentException(
                    "The Daybreak Chronicles state belongs to another instance");
        }
        normalState = restored.normalState.copy();
        skillState = restored.skillState.copy();
        burstState = restored.burstState.copy();
    }

    private void addCategoryBonus(
            StatsContainer stats,
            StatType stat,
            int stacks) {
        if (stacks > 0) {
            stats.add(stat, damageBonusPerStack * stacks);
        }
    }

    private int normalize(CategoryState state, double currentTime) {
        if (state.stacks == 0 || currentTime < state.nextDecayAt) {
            return state.stacks;
        }
        int elapsedTicks = (int) Math.floor(
                (currentTime - state.nextDecayAt) / DECAY_INTERVAL) + 1;
        int appliedTicks = Math.min(state.stacks, elapsedTicks);
        state.stacks -= appliedTicks;
        if (state.stacks == 0) {
            state.nextDecayAt = Double.POSITIVE_INFINITY;
        } else {
            state.nextDecayAt += DECAY_INTERVAL * appliedTicks;
        }
        return state.stacks;
    }

    private CategoryState stateFor(ActionType actionType) {
        if (actionType == ActionType.NORMAL) {
            return normalState;
        }
        if (actionType == ActionType.SKILL) {
            return skillState;
        }
        if (actionType == ActionType.BURST) {
            return burstState;
        }
        return null;
    }

    private boolean isBoundOwner(Character actor) {
        return simulator != null
                && actor == owner
                && owner.getWeapon() == this
                && simulator.getPartyMembers().contains(owner);
    }

    private static final class CategoryState {
        private int stacks = MAX_STACKS;
        private double nextDecayAt = Double.POSITIVE_INFINITY;

        private CategoryState copy() {
            CategoryState copy = new CategoryState();
            copy.stacks = stacks;
            copy.nextDecayAt = nextDecayAt;
            return copy;
        }
    }

    private static final class DaybreakState implements State {
        private final TheDaybreakChronicles source;
        private final CategoryState normalState;
        private final CategoryState skillState;
        private final CategoryState burstState;

        private DaybreakState(
                TheDaybreakChronicles source,
                CategoryState normalState,
                CategoryState skillState,
                CategoryState burstState) {
            this.source = source;
            this.normalState = normalState.copy();
            this.skillState = skillState.copy();
            this.burstState = burstState.copy();
        }
    }
}

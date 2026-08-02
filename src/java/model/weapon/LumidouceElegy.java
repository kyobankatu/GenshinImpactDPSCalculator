package model.weapon;

import mechanics.reaction.ReactionResult;
import model.entity.Character;
import model.entity.ElementalReactionTriggeredWeaponEffect;
import model.entity.SimulatorInitializedWeaponEffect;
import model.entity.SnapshotAwareWeaponEffect;
import model.entity.Weapon;
import model.stats.StatsContainer;
import model.type.Element;
import model.type.StatType;
import model.type.WeaponType;
import simulation.CombatSimulator;
import simulation.DamageListener;
import simulation.action.AttackAction;

/** Lumidouce Elegy with shared Burning stacks and Energy cooldown state. */
public final class LumidouceElegy extends Weapon
        implements DamageListener,
        ElementalReactionTriggeredWeaponEffect,
        SimulatorInitializedWeaponEffect,
        SnapshotAwareWeaponEffect {
    private static final int MAX_STACKS = 2;
    private static final double STACK_DURATION = 8.0;
    private static final double ENERGY_COOLDOWN = 12.0;

    private final int refinement;
    private final double attackBonus;
    private final double damageBonusPerStack;
    private final double energyRecovery;

    private Character owner;
    private CombatSimulator simulator;
    private int stackCount;
    private double stacksExpireAt = Double.NEGATIVE_INFINITY;
    private double nextEnergyTime = Double.NEGATIVE_INFINITY;
    private boolean pendingReactionHit;
    private double pendingReactionHitTime = Double.NEGATIVE_INFINITY;

    /** Constructs Lumidouce Elegy at refinement rank five. */
    public LumidouceElegy() {
        this(5);
    }

    /**
     * Constructs Lumidouce Elegy at a selected refinement.
     *
     * @param refinement refinement rank in the inclusive range 1-5
     */
    public LumidouceElegy(int refinement) {
        super("Lumidouce Elegy", new StatsContainer());
        validateRefinement(refinement);
        this.refinement = refinement;
        this.attackBonus = 0.11 + 0.04 * refinement;
        this.damageBonusPerStack = 0.13 + 0.05 * refinement;
        this.energyRecovery = 11.0 + refinement;
        weaponType = WeaponType.POLEARM;
        getStats().set(StatType.BASE_ATK, 608.0);
        getStats().set(StatType.CRIT_RATE, 0.331);
    }

    /** Returns this weapon's refinement rank. */
    public int getRefinement() {
        return refinement;
    }

    /** Returns the unconditional ATK bonus. */
    public double getAttackBonus() {
        return attackBonus;
    }

    /** Returns the all-damage bonus granted by each active stack. */
    public double getDamageBonusPerStack() {
        return damageBonusPerStack;
    }

    /** Returns the flat Energy restored at two stacks. */
    public double getEnergyRecovery() {
        return energyRecovery;
    }

    /** Returns the active stack count at the supplied simulation time. */
    public int getStackCount(double currentTime) {
        expireStacksAt(currentTime);
        return stackCount;
    }

    /** Binds this mutable weapon to one equipped owner and reaction dispatcher. */
    @Override
    public void initializeForSimulator(Character equippedOwner, CombatSimulator sim) {
        validateBinding(equippedOwner, sim);
        if (simulator != null) {
            if (owner != equippedOwner || simulator != sim) {
                throw new IllegalStateException(
                        "Lumidouce Elegy is already bound to another simulator");
            }
            return;
        }
        owner = equippedOwner;
        simulator = sim;
        sim.addElementalReactionTriggeredWeaponEffect(this);
        sim.addDamageListener(this);
    }

    /** Applies the permanent ATK bonus and active all-damage stacks. */
    @Override
    public void applyPassive(StatsContainer stats, double currentTime) {
        expireStacksAt(currentTime);
        stats.add(StatType.ATK_PERCENT, attackBonus);
        stats.add(StatType.DMG_BONUS_ALL, damageBonusPerStack * stackCount);
    }

    /** Gains a stack from an owner Dendro hit against an active Burning target. */
    @Override
    public void onDamage(
            Character actor,
            AttackAction action,
            double damage,
            double currentTime) {
        if (!isBoundActor(actor)
                || action == null) {
            return;
        }
        boolean duplicateReactionHit = pendingReactionHit
                && currentTime == pendingReactionHitTime;
        pendingReactionHit = false;
        if (!action.isHitEffectTrigger()
                || damage <= 0.0
                || action.getElement() != Element.DENDRO
                || !simulator.isBurningActive()
                || duplicateReactionHit) {
            return;
        }
        gainStack(currentTime);
    }

    /** Gains a stack only from an actual Burning reaction triggered by the owner. */
    @Override
    public void onElementalReaction(
            ReactionResult result,
            Character source,
            double time,
            CombatSimulator sim) {
        if (!isBoundCallback(source, sim)
                || result == null
                || result.getKind() != ReactionResult.Kind.BURNING) {
            return;
        }
        gainStack(time);
        pendingReactionHit = true;
        pendingReactionHitTime = time;
    }

    /** Captures stacks, expiry, Energy ICD, and same-hit de-duplication state. */
    @Override
    public State captureWeaponState() {
        return new ElegyState(
                this,
                stackCount,
                stacksExpireAt,
                nextEnergyTime,
                pendingReactionHit,
                pendingReactionHitTime);
    }

    /** Restores only state captured from this exact weapon instance. */
    @Override
    public void restoreWeaponState(State state) {
        if (!(state instanceof ElegyState)) {
            throw new IllegalArgumentException(
                    "Lumidouce Elegy state type is invalid");
        }
        ElegyState elegyState = (ElegyState) state;
        if (elegyState.source != this) {
            throw new IllegalArgumentException(
                    "Lumidouce Elegy state belongs to another weapon instance");
        }
        stackCount = elegyState.stackCount;
        stacksExpireAt = elegyState.stacksExpireAt;
        nextEnergyTime = elegyState.nextEnergyTime;
        pendingReactionHit = elegyState.pendingReactionHit;
        pendingReactionHitTime = elegyState.pendingReactionHitTime;
    }

    private void gainStack(double currentTime) {
        expireStacksAt(currentTime);
        if (stackCount < MAX_STACKS) {
            stackCount++;
        }
        stacksExpireAt = currentTime + STACK_DURATION;
        if (stackCount == MAX_STACKS
                && currentTime >= nextEnergyTime) {
            owner.receiveFlatEnergy(energyRecovery);
            nextEnergyTime = currentTime + ENERGY_COOLDOWN;
        }
    }

    private void expireStacksAt(double currentTime) {
        if (stackCount > 0 && currentTime >= stacksExpireAt) {
            stackCount = 0;
            stacksExpireAt = Double.NEGATIVE_INFINITY;
        }
    }

    private boolean isBoundCallback(Character user, CombatSimulator sim) {
        return isBoundActor(user) && sim == simulator;
    }

    private boolean isBoundActor(Character actor) {
        return simulator != null
                && actor == owner
                && owner.getWeapon() == this;
    }

    private void validateBinding(Character equippedOwner, CombatSimulator sim) {
        if (equippedOwner == null || sim == null) {
            throw new IllegalArgumentException(
                    "Weapon owner and simulator are required");
        }
        if (equippedOwner.getWeapon() != this) {
            throw new IllegalArgumentException(
                    "Weapon owner must have this Lumidouce Elegy equipped");
        }
    }

    private static void validateRefinement(int refinement) {
        if (refinement < 1 || refinement > 5) {
            throw new IllegalArgumentException(
                    "Lumidouce Elegy refinement must be between 1 and 5");
        }
    }

    /** Immutable runtime state tied to one weapon instance. */
    private static final class ElegyState implements State {
        private final LumidouceElegy source;
        private final int stackCount;
        private final double stacksExpireAt;
        private final double nextEnergyTime;
        private final boolean pendingReactionHit;
        private final double pendingReactionHitTime;

        private ElegyState(
                LumidouceElegy source,
                int stackCount,
                double stacksExpireAt,
                double nextEnergyTime,
                boolean pendingReactionHit,
                double pendingReactionHitTime) {
            this.source = source;
            this.stackCount = stackCount;
            this.stacksExpireAt = stacksExpireAt;
            this.nextEnergyTime = nextEnergyTime;
            this.pendingReactionHit = pendingReactionHit;
            this.pendingReactionHitTime = pendingReactionHitTime;
        }
    }
}

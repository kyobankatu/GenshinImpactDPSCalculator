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

/** Version 7.0 sword with independent Skill-hit stacks and Stellar Energy recovery. */
public final class WhitelakeFrostfeather extends Weapon
        implements DamageTriggeredWeaponEffect,
        ElementalReactionTriggeredWeaponEffect,
        SimulatorInitializedWeaponEffect,
        SnapshotAwareWeaponEffect {
    private static final int MAX_STACKS = 3;
    private static final double STACK_DURATION = 8.0;
    private static final double STACK_COOLDOWN = 0.1;
    private static final double ENERGY_COOLDOWN = 3.5;
    private static final double[] ATTACK_BONUS = {
        0.0, 0.08, 0.10, 0.12, 0.14, 0.16
    };
    private static final double[] STELLAR_CRIT_DMG = {
        0.0, 0.50, 0.65, 0.80, 0.95, 1.10
    };
    private static final double[] ENERGY_RECOVERY = {
        0.0, 4.0, 4.5, 5.0, 5.5, 6.0
    };

    private final int refinement;
    private final double attackBonusPerStack;
    private final double stellarCritDamage;
    private final double energyRecovery;
    private final List<Double> stackExpirations = new ArrayList<>();

    private Character owner;
    private CombatSimulator simulator;
    private double nextStackAt = Double.NEGATIVE_INFINITY;
    private double nextEnergyRecoveryAt = Double.NEGATIVE_INFINITY;

    /** Constructs Whitelake Frostfeather at refinement rank five. */
    public WhitelakeFrostfeather() {
        this(5);
    }

    /**
     * Constructs Whitelake Frostfeather at a selected refinement.
     *
     * @param refinement refinement rank in the inclusive range 1-5
     */
    public WhitelakeFrostfeather(int refinement) {
        super("Whitelake Frostfeather", new StatsContainer());
        validateRefinement(refinement);
        this.refinement = refinement;
        this.attackBonusPerStack = ATTACK_BONUS[refinement];
        this.stellarCritDamage = STELLAR_CRIT_DMG[refinement];
        this.energyRecovery = ENERGY_RECOVERY[refinement];
        weaponType = WeaponType.SWORD;
        getStats().set(StatType.BASE_ATK, 674.0);
        getStats().set(StatType.CRIT_RATE, 0.221);
    }

    /** Returns this weapon's refinement rank. */
    public int getRefinement() {
        return refinement;
    }

    /** Returns the ATK bonus granted by each Lake-Hued Lament stack. */
    public double getAttackBonusPerStack() {
        return attackBonusPerStack;
    }

    /** Returns the three-stack CRIT DMG bonus for both Stellar families. */
    public double getStellarCritDamage() {
        return stellarCritDamage;
    }

    /** Returns the flat Energy restored by an eligible Stellar event. */
    public double getEnergyRecovery() {
        return energyRecovery;
    }

    /** Returns the number of independently alive stacks at the supplied time. */
    public int getStackCount(double currentTime) {
        expireStacks(currentTime);
        return stackExpirations.size();
    }

    /** Returns the next time at which a Stellar event may restore Energy. */
    public double getNextEnergyRecoveryAt() {
        return nextEnergyRecoveryAt;
    }

    /** Binds this mutable weapon and registers actual Stellar reaction events. */
    @Override
    public void initializeForSimulator(Character equippedOwner, CombatSimulator sim) {
        validateBinding(equippedOwner, sim);
        if (simulator != null) {
            if (owner != equippedOwner || simulator != sim) {
                throw new IllegalStateException(
                        "Whitelake Frostfeather is already bound to another simulator");
            }
            return;
        }
        owner = equippedOwner;
        simulator = sim;
        sim.addElementalReactionTriggeredWeaponEffect(this);
    }

    /**
     * Handles owner Skill hits and direct Stellar damage.
     *
     * <p>Direct Stellar actions arrive through the damage hook, while actual
     * reaction creation arrives through {@link #onElementalReaction}; this
     * separation prevents one event from being counted twice.</p>
     */
    @Override
    public void onDamage(
            Character user,
            AttackAction action,
            double currentTime,
            CombatSimulator sim) {
        if (!isBoundCallback(user, sim)
                || action == null
                || !action.isHitEffectTrigger()
                || action.getDamagePercent() <= 0.0) {
            return;
        }
        if ((action.getActionType() == ActionType.SKILL
                || action.isCountsAsSkillDmg())
                && currentTime >= nextStackAt) {
            addStack(currentTime);
            nextStackAt = currentTime + STACK_COOLDOWN;
        }
        if (action.isStellarConsidered()) {
            recoverEnergyForStellarEvent(currentTime);
        }
    }

    /** Restores Energy for an owner-triggered actual Stellar reaction. */
    @Override
    public void onElementalReaction(
            ReactionResult result,
            Character source,
            double time,
            CombatSimulator sim) {
        if (!isBoundCallback(source, sim)
                || result == null
                || !result.isStellarReaction()) {
            return;
        }
        recoverEnergyForStellarEvent(time);
    }

    /** Applies stack ATK and the three-stack Stellar CRIT DMG bonuses. */
    @Override
    public void applyPassive(StatsContainer stats, double currentTime) {
        int stackCount = getStackCount(currentTime);
        stats.add(StatType.ATK_PERCENT, attackBonusPerStack * stackCount);
        if (stackCount == MAX_STACKS) {
            stats.add(StatType.STELLAR_CONDUCT_CRIT_DMG, stellarCritDamage);
            stats.add(StatType.STELLAR_SWIRL_CRIT_DMG, stellarCritDamage);
        }
    }

    /** Captures independent expirations and both internal cooldowns. */
    @Override
    public State captureWeaponState() {
        return new WhitelakeState(
                this,
                stackExpirations,
                nextStackAt,
                nextEnergyRecoveryAt);
    }

    /** Restores only immutable state captured from this exact weapon instance. */
    @Override
    public void restoreWeaponState(State state) {
        if (!(state instanceof WhitelakeState)) {
            throw new IllegalArgumentException(
                    "Whitelake Frostfeather state type is invalid");
        }
        WhitelakeState restored = (WhitelakeState) state;
        if (restored.source != this) {
            throw new IllegalArgumentException(
                    "Whitelake Frostfeather state belongs to another weapon instance");
        }
        stackExpirations.clear();
        stackExpirations.addAll(restored.stackExpirations);
        nextStackAt = restored.nextStackAt;
        nextEnergyRecoveryAt = restored.nextEnergyRecoveryAt;
    }

    private void addStack(double currentTime) {
        expireStacks(currentTime);
        double expiration = currentTime + STACK_DURATION;
        if (stackExpirations.size() < MAX_STACKS) {
            stackExpirations.add(expiration);
        } else {
            stackExpirations.set(0, expiration);
        }
        Collections.sort(stackExpirations);
    }

    private void expireStacks(double currentTime) {
        stackExpirations.removeIf(expiration -> currentTime >= expiration);
    }

    private void recoverEnergyForStellarEvent(double currentTime) {
        if (getStackCount(currentTime) != MAX_STACKS
                || currentTime < nextEnergyRecoveryAt) {
            return;
        }
        owner.receiveFlatEnergy(energyRecovery);
        nextEnergyRecoveryAt = currentTime + ENERGY_COOLDOWN;
    }

    private boolean isBoundCallback(Character source, CombatSimulator sim) {
        return simulator != null
                && sim == simulator
                && source == owner
                && owner.getWeapon() == this;
    }

    private void validateBinding(Character equippedOwner, CombatSimulator sim) {
        if (equippedOwner == null || sim == null) {
            throw new IllegalArgumentException(
                    "Weapon owner and simulator are required");
        }
        if (equippedOwner.getWeapon() != this) {
            throw new IllegalArgumentException(
                    "Weapon owner must have this Whitelake Frostfeather equipped");
        }
    }

    private static void validateRefinement(int refinement) {
        if (refinement < 1 || refinement > 5) {
            throw new IllegalArgumentException(
                    "Whitelake Frostfeather refinement must be between 1 and 5");
        }
    }

    /** Immutable independent-stack and cooldown state tied to one weapon instance. */
    private static final class WhitelakeState implements State {
        private final WhitelakeFrostfeather source;
        private final List<Double> stackExpirations;
        private final double nextStackAt;
        private final double nextEnergyRecoveryAt;

        private WhitelakeState(
                WhitelakeFrostfeather source,
                List<Double> stackExpirations,
                double nextStackAt,
                double nextEnergyRecoveryAt) {
            this.source = source;
            this.stackExpirations = new ArrayList<>(stackExpirations);
            this.nextStackAt = nextStackAt;
            this.nextEnergyRecoveryAt = nextEnergyRecoveryAt;
        }
    }
}

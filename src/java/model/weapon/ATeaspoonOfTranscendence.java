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
 * A Teaspoon of Transcendence claymore with owner-bound Transcendence stacks.
 *
 * <p>Lv. 90 metadata and R1-R5 values follow Genshin Optimizer commit
 * {@code 61c5556a}. The unconditional ATK bonus is fully represented. Positive
 * owner Charged Attack hits gain up to three stacks, refresh their shared
 * five-second duration, and observe the source's {@code 0.2}-second trigger
 * interval.</p>
 *
 * <p>The source-backed Stellar-Conduct DMG bonus is retained as typed weapon
 * data but intentionally does not leak into generic damage stats: this
 * baseline has no Stellar-Conduct-specific {@link StatType}.</p>
 */
public final class ATeaspoonOfTranscendence extends Weapon implements
        DamageListener,
        SimulatorInitializedWeaponEffect,
        SnapshotAwareWeaponEffect {
    private static final int MAX_STACKS = 3;
    private static final double STACK_DURATION = 5.0;
    private static final double TRIGGER_COOLDOWN = 0.2;

    private final int refinement;
    private final double attackBonus;
    private final double stellarConductBonusPerStack;
    private Character owner;
    private CombatSimulator simulator;
    private int stackCount;
    private double stacksActiveUntil = Double.NEGATIVE_INFINITY;
    private double nextStackAt = Double.NEGATIVE_INFINITY;

    /** Constructs A Teaspoon of Transcendence at refinement rank five. */
    public ATeaspoonOfTranscendence() {
        this(5);
    }

    /**
     * Constructs A Teaspoon of Transcendence at the selected refinement.
     *
     * @param refinement refinement rank in the inclusive range 1-5
     */
    public ATeaspoonOfTranscendence(int refinement) {
        super("A Teaspoon of Transcendence", new StatsContainer());
        validateRefinement(refinement);
        this.refinement = refinement;
        attackBonus = 0.21 + 0.07 * refinement;
        stellarConductBonusPerStack = 0.12 + 0.04 * refinement;
        weaponType = WeaponType.CLAYMORE;
        getStats().set(StatType.BASE_ATK, 674.0);
        getStats().set(StatType.CRIT_DMG, 0.441);
    }

    /** Returns the selected refinement rank. */
    public int getRefinement() {
        return refinement;
    }

    /** Returns the unconditional ATK bonus. */
    public double getAttackBonus() {
        return attackBonus;
    }

    /** Returns the source-backed Stellar-Conduct bonus granted per stack. */
    public double getStellarConductBonusPerStack() {
        return stellarConductBonusPerStack;
    }

    /** Returns whether a typed Stellar-Conduct damage stat is represented. */
    public boolean isStellarConductDamageRepresented() {
        return false;
    }

    /** Returns the active stack count at the supplied timestamp. */
    public int getStackCount(double currentTime) {
        expireStacks(currentTime);
        return stackCount;
    }

    /** Applies only the representable unconditional ATK bonus. */
    @Override
    public void applyPassive(StatsContainer stats, double currentTime) {
        stats.add(StatType.ATK_PERCENT, attackBonus);
    }

    /** Binds the mutable stack state to exactly one equipped owner. */
    @Override
    public void initializeForSimulator(
            Character equippedOwner,
            CombatSimulator activeSimulator) {
        if (equippedOwner == null || activeSimulator == null) {
            throw new IllegalArgumentException(
                    "A Teaspoon of Transcendence owner and simulator are required");
        }
        if (simulator != null) {
            if (owner != equippedOwner || simulator != activeSimulator) {
                throw new IllegalStateException(
                        "A Teaspoon of Transcendence is already bound elsewhere");
            }
            return;
        }
        validateBinding(equippedOwner, activeSimulator);
        owner = equippedOwner;
        simulator = activeSimulator;
        activeSimulator.addDamageListener(this);
    }

    /** Gains or refreshes Transcendence from a positive owner Charged hit. */
    @Override
    public void onDamage(
            Character actor,
            AttackAction action,
            double damage,
            double currentTime) {
        if (!isEligibleChargedHit(actor, action, damage)
                || currentTime < nextStackAt) {
            return;
        }
        expireStacks(currentTime);
        stackCount = Math.min(MAX_STACKS, stackCount + 1);
        stacksActiveUntil = currentTime + STACK_DURATION;
        nextStackAt = currentTime + TRIGGER_COOLDOWN;
    }

    /** Captures stack count, shared expiry, and the hit trigger gate. */
    @Override
    public State captureWeaponState() {
        return new TeaspoonState(
                this, stackCount, stacksActiveUntil, nextStackAt);
    }

    /** Restores state captured from this exact weapon instance. */
    @Override
    public void restoreWeaponState(State state) {
        if (!(state instanceof TeaspoonState)) {
            throw new IllegalArgumentException(
                    "A Teaspoon of Transcendence state type is invalid");
        }
        TeaspoonState restored = (TeaspoonState) state;
        if (restored.source != this) {
            throw new IllegalArgumentException(
                    "A Teaspoon of Transcendence state belongs to another instance");
        }
        stackCount = restored.stackCount;
        stacksActiveUntil = restored.stacksActiveUntil;
        nextStackAt = restored.nextStackAt;
    }

    private boolean isEligibleChargedHit(
            Character actor,
            AttackAction action,
            double damage) {
        return simulator != null
                && actor == owner
                && owner.getWeapon() == this
                && simulator.getPartyMembers().contains(owner)
                && action != null
                && action.isHitEffectTrigger()
                && action.getActionType() == ActionType.CHARGE
                && action.getDamagePercent() > 0.0
                && damage > 0.0;
    }

    private void expireStacks(double currentTime) {
        if (currentTime >= stacksActiveUntil) {
            stackCount = 0;
        }
    }

    private void validateBinding(
            Character equippedOwner,
            CombatSimulator activeSimulator) {
        if (equippedOwner.getWeapon() != this) {
            throw new IllegalArgumentException(
                    "Owner must have A Teaspoon of Transcendence equipped");
        }
        if (!activeSimulator.getPartyMembers().contains(equippedOwner)) {
            throw new IllegalArgumentException(
                    "Owner must belong to the target simulator party");
        }
    }

    private static void validateRefinement(int refinement) {
        if (refinement < 1 || refinement > 5) {
            throw new IllegalArgumentException(
                    "A Teaspoon of Transcendence refinement must be between 1 and 5");
        }
    }

    /** Immutable stack state tied to one weapon instance. */
    private static final class TeaspoonState implements State {
        private final ATeaspoonOfTranscendence source;
        private final int stackCount;
        private final double stacksActiveUntil;
        private final double nextStackAt;

        private TeaspoonState(
                ATeaspoonOfTranscendence source,
                int stackCount,
                double stacksActiveUntil,
                double nextStackAt) {
            this.source = source;
            this.stackCount = stackCount;
            this.stacksActiveUntil = stacksActiveUntil;
            this.nextStackAt = nextStackAt;
        }
    }
}

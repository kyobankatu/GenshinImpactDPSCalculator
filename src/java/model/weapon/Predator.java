package model.weapon;

import model.entity.Character;
import model.entity.SimulatorInitializedWeaponEffect;
import model.entity.SnapshotAwareWeaponEffect;
import model.entity.Weapon;
import model.stats.StatsContainer;
import model.type.CharacterId;
import model.type.Element;
import model.type.StatType;
import model.type.WeaponType;
import simulation.CombatSimulator;
import simulation.DamageListener;
import simulation.ElementalIndirectDamageListener;
import simulation.action.AttackAction;

/**
 * Predator with its owner Cryo-damage stacks and Aloy affinity.
 *
 * <p>Lv. 90 metadata and the fixed-R1 passive follow pinned KQM TCL
 * {@code 80ba6241} and gcsim {@code ef41805d}. Each positive direct Cryo
 * damage event by the active equipped owner gains one stack, up to two, and
 * refreshes one shared six-second window. Each stack grants 10% Normal and
 * Charged Attack DMG. Aloy additionally receives 66 flat ATK.</p>
 *
 * <p>The canonical passive is PlayStation-only. This simulator models the
 * platform passive as enabled because it has no runtime platform concept.
 * Direct Cryo damage remains eligible even when the action does not trigger
 * generic hit effects, matching the sourced damage-event contract.</p>
 */
public final class Predator extends Weapon implements
        DamageListener,
        ElementalIndirectDamageListener,
        SimulatorInitializedWeaponEffect,
        SnapshotAwareWeaponEffect {
    private static final int REFINEMENT = 1;
    private static final int MAX_STACKS = 2;
    private static final double STACK_DURATION = 6.0;
    private static final double DAMAGE_BONUS_PER_STACK = 0.10;
    private static final double ALOY_FLAT_ATTACK = 66.0;

    private Character owner;
    private CombatSimulator simulator;
    private int stackCount;
    private double stacksActiveUntil = Double.NEGATIVE_INFINITY;

    /** Constructs the fixed-R1 PlayStation weapon at Lv. 90. */
    public Predator() {
        super("Predator", new StatsContainer());
        weaponType = WeaponType.BOW;
        getStats().set(StatType.BASE_ATK, 510.0);
        getStats().set(StatType.ATK_PERCENT, 0.413);
    }

    /** Returns the weapon's fixed refinement rank. */
    public int getRefinement() {
        return REFINEMENT;
    }

    /** Returns the explicit simulator boundary for the platform passive. */
    public boolean isPlatformPassiveEnabled() {
        return true;
    }

    /** Returns the active stack count at the supplied simulation time. */
    public int getStackCount(double currentTime) {
        expireStacks(currentTime);
        return stackCount;
    }

    /** Applies active action bonuses and Aloy's fixed affinity ATK. */
    @Override
    public void applyPassive(StatsContainer stats, double currentTime) {
        int activeStacks = getStackCount(currentTime);
        double actionBonus = DAMAGE_BONUS_PER_STACK * activeStacks;
        stats.add(StatType.NORMAL_ATTACK_DMG_BONUS, actionBonus);
        stats.add(StatType.CHARGED_ATTACK_DMG_BONUS, actionBonus);
        if (owner != null && owner.getCharacterId() == CharacterId.ALOY) {
            stats.add(StatType.ATK_FLAT, ALOY_FLAT_ATTACK);
        }
    }

    /** Binds this stateful passive to exactly one equipped owner and simulator. */
    @Override
    public void initializeForSimulator(
            Character equippedOwner,
            CombatSimulator activeSimulator) {
        if (equippedOwner == null || activeSimulator == null) {
            throw new IllegalArgumentException(
                    "Predator owner and simulator are required");
        }
        if (simulator != null) {
            if (owner != equippedOwner || simulator != activeSimulator) {
                throw new IllegalStateException(
                        "Predator is already bound elsewhere");
            }
            return;
        }
        validateBinding(equippedOwner, activeSimulator);
        owner = equippedOwner;
        simulator = activeSimulator;
        activeSimulator.addDamageListener(this);
        activeSimulator.addElementalIndirectDamageListener(this);
    }

    /** Gains or refreshes one stack after eligible direct Cryo damage. */
    @Override
    public void onDamage(
            Character actor,
            AttackAction action,
            double damage,
            double currentTime) {
        if (!isEligibleCryoDamage(actor, action, damage)) {
            return;
        }
        gainStack(currentTime);
    }

    /** Gains one shared stack from accepted owner-attributed Cryo reaction damage. */
    @Override
    public void onElementalIndirectDamage(
            Character attributedOwner,
            Element damageElement,
            double damage,
            double currentTime) {
        if (!isEligibleOwnerDamage(attributedOwner, damage)
                || damageElement != Element.CRYO) {
            return;
        }
        gainStack(currentTime);
    }

    /** Captures the complete shared stack window. */
    @Override
    public State captureWeaponState() {
        return new PredatorState(this, stackCount, stacksActiveUntil);
    }

    /** Restores state captured from this exact Predator instance. */
    @Override
    public void restoreWeaponState(State state) {
        if (!(state instanceof PredatorState)) {
            throw new IllegalArgumentException("Predator state type is invalid");
        }
        PredatorState restored = (PredatorState) state;
        if (restored.source != this) {
            throw new IllegalArgumentException(
                    "Predator state belongs to another instance");
        }
        stackCount = restored.stackCount;
        stacksActiveUntil = restored.stacksActiveUntil;
    }

    private boolean isEligibleCryoDamage(
            Character actor,
            AttackAction action,
            double damage) {
        return isEligibleOwnerDamage(actor, damage)
                && action != null
                && action.getElement() == Element.CRYO;
    }

    private boolean isEligibleOwnerDamage(Character actor, double damage) {
        return simulator != null
                && owner != null
                && owner.getWeapon() == this
                && actor == owner
                && actor == simulator.getActiveCharacter()
                && simulator.getPartyMembers().contains(actor)
                && damage > 0.0;
    }

    private void gainStack(double currentTime) {
        expireStacks(currentTime);
        if (stackCount < MAX_STACKS) {
            stackCount++;
        }
        stacksActiveUntil = currentTime + STACK_DURATION;
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
                    "Owner must have this Predator equipped");
        }
        if (!activeSimulator.getPartyMembers().contains(equippedOwner)) {
            throw new IllegalArgumentException(
                    "Owner must belong to the target simulator party");
        }
    }

    /** Immutable mutable-window state tied to one Predator instance. */
    private static final class PredatorState implements State {
        private final Predator source;
        private final int stackCount;
        private final double stacksActiveUntil;

        private PredatorState(
                Predator source,
                int stackCount,
                double stacksActiveUntil) {
            this.source = source;
            this.stackCount = stackCount;
            this.stacksActiveUntil = stacksActiveUntil;
        }
    }
}

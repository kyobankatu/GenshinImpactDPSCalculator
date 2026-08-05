package model.weapon;

import model.entity.ActionTriggeredWeaponEffect;
import model.entity.Character;
import model.entity.SimulatorInitializedWeaponEffect;
import model.entity.SnapshotAwareWeaponEffect;
import model.entity.Weapon;
import model.stats.StatsContainer;
import model.type.StatType;
import model.type.WeaponType;
import simulation.CombatSimulator;
import simulation.action.CharacterActionKey;
import simulation.action.CharacterActionRequest;

/**
 * Finale of the Deep's representable pre-Bond ATK window.
 *
 * <p>An active-owner Skill grants R1-R5 ATK for 15 seconds on a 10-second
 * trigger cooldown. Bond of Life creation, clearing, and the debt-derived flat
 * ATK bonus remain excluded because player HP debt is unavailable.</p>
 */
public final class FinaleOfTheDeep extends Weapon implements
        ActionTriggeredWeaponEffect,
        SimulatorInitializedWeaponEffect,
        SnapshotAwareWeaponEffect {
    private static final double WINDOW_DURATION = 15.0;
    private static final double TRIGGER_COOLDOWN = 10.0;

    private final int refinement;
    private final double attackBonus;
    private Character owner;
    private CombatSimulator simulator;
    private double activeFrom = Double.POSITIVE_INFINITY;
    private double activeUntil = Double.NEGATIVE_INFINITY;
    private double nextTriggerAt = Double.NEGATIVE_INFINITY;

    /** Constructs the weapon at refinement rank five. */
    public FinaleOfTheDeep() {
        this(5);
    }

    /** Constructs the weapon at the selected refinement rank. */
    public FinaleOfTheDeep(int refinement) {
        super("Finale of the Deep", new StatsContainer());
        if (refinement < 1 || refinement > 5) {
            throw new IllegalArgumentException(
                    "Finale of the Deep refinement must be between 1 and 5");
        }
        this.refinement = refinement;
        attackBonus = 0.09 + 0.03 * refinement;
        weaponType = WeaponType.SWORD;
        getStats().set(StatType.BASE_ATK, 565.0);
        getStats().set(StatType.ATK_PERCENT, 0.276);
    }

    /** Returns the selected refinement rank. */
    public int getRefinement() {
        return refinement;
    }

    /** Returns the represented percentage ATK bonus. */
    public double getAttackBonus() {
        return attackBonus;
    }

    /** Returns whether the half-open window is active. */
    public boolean isWindowActive(double currentTime) {
        return currentTime >= activeFrom && currentTime < activeUntil;
    }

    /** Returns the current window expiration. */
    public double getActiveUntil() {
        return activeUntil;
    }

    /** Applies the live percentage ATK bonus. */
    @Override
    public void applyPassive(StatsContainer stats, double currentTime) {
        if (isWindowActive(currentTime)) {
            stats.add(StatType.ATK_PERCENT, attackBonus);
        }
    }

    /** Binds one equipped owner and simulator. */
    @Override
    public void initializeForSimulator(
            Character equippedOwner,
            CombatSimulator activeSimulator) {
        if (equippedOwner == null || activeSimulator == null) {
            throw new IllegalArgumentException(
                    "Finale of the Deep owner and simulator are required");
        }
        if (simulator != null) {
            if (owner != equippedOwner || simulator != activeSimulator) {
                throw new IllegalStateException(
                        "Finale of the Deep is already bound elsewhere");
            }
            return;
        }
        if (equippedOwner.getWeapon() != this) {
            throw new IllegalArgumentException(
                    "Owner must have Finale of the Deep equipped");
        }
        if (!activeSimulator.getPartyMembers().contains(equippedOwner)) {
            throw new IllegalArgumentException(
                    "Owner must belong to the target simulator party");
        }
        owner = equippedOwner;
        simulator = activeSimulator;
    }

    /** Opens or refreshes the window on eligible Skill use. */
    @Override
    public void onAction(
            Character user,
            CharacterActionRequest request,
            CombatSimulator activeSimulator) {
        if (!isBoundActiveOwner(user, activeSimulator)
                || request == null
                || request.getKey() != CharacterActionKey.SKILL) {
            return;
        }
        double currentTime = activeSimulator.getCurrentTime();
        if (currentTime < nextTriggerAt) {
            return;
        }
        activeFrom = currentTime;
        activeUntil = currentTime + WINDOW_DURATION;
        nextTriggerAt = currentTime + TRIGGER_COOLDOWN;
    }

    /** Captures window and trigger-cooldown state. */
    @Override
    public State captureWeaponState() {
        return new FinaleOfTheDeepState(
                this, activeFrom, activeUntil, nextTriggerAt);
    }

    /** Restores state captured from this exact weapon instance. */
    @Override
    public void restoreWeaponState(State state) {
        if (!(state instanceof FinaleOfTheDeepState)) {
            throw new IllegalArgumentException(
                    "Finale of the Deep state type is invalid");
        }
        FinaleOfTheDeepState restored = (FinaleOfTheDeepState) state;
        if (restored.source != this) {
            throw new IllegalArgumentException(
                    "Finale of the Deep state belongs to another instance");
        }
        activeFrom = restored.activeFrom;
        activeUntil = restored.activeUntil;
        nextTriggerAt = restored.nextTriggerAt;
    }

    private boolean isBoundActiveOwner(
            Character user,
            CombatSimulator activeSimulator) {
        return simulator != null
                && simulator == activeSimulator
                && owner == user
                && owner.getWeapon() == this
                && simulator.getActiveCharacter() == owner;
    }

    private static final class FinaleOfTheDeepState implements State {
        private final FinaleOfTheDeep source;
        private final double activeFrom;
        private final double activeUntil;
        private final double nextTriggerAt;

        private FinaleOfTheDeepState(
                FinaleOfTheDeep source,
                double activeFrom,
                double activeUntil,
                double nextTriggerAt) {
            this.source = source;
            this.activeFrom = activeFrom;
            this.activeUntil = activeUntil;
            this.nextTriggerAt = nextTriggerAt;
        }
    }
}

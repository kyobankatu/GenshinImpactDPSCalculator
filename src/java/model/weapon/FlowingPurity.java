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
 * Flowing Purity's representable pre-Bond elemental-damage window.
 *
 * <p>An active-owner Skill grants R1-R5 all-element damage for 15 seconds on a
 * 10-second trigger cooldown. Bond of Life creation, clearing, and the derived
 * second bonus remain excluded because player HP debt is unavailable.</p>
 */
public final class FlowingPurity extends Weapon implements
        ActionTriggeredWeaponEffect,
        SimulatorInitializedWeaponEffect,
        SnapshotAwareWeaponEffect {
    private static final double WINDOW_DURATION = 15.0;
    private static final double TRIGGER_COOLDOWN = 10.0;
    private static final StatType[] ELEMENTAL_BONUSES = {
        StatType.PYRO_DMG_BONUS,
        StatType.HYDRO_DMG_BONUS,
        StatType.ANEMO_DMG_BONUS,
        StatType.ELECTRO_DMG_BONUS,
        StatType.DENDRO_DMG_BONUS,
        StatType.CRYO_DMG_BONUS,
        StatType.GEO_DMG_BONUS
    };

    private final int refinement;
    private final double elementalDamageBonus;
    private Character owner;
    private CombatSimulator simulator;
    private double activeFrom = Double.POSITIVE_INFINITY;
    private double activeUntil = Double.NEGATIVE_INFINITY;
    private double nextTriggerAt = Double.NEGATIVE_INFINITY;

    /** Constructs the weapon at refinement rank five. */
    public FlowingPurity() {
        this(5);
    }

    /** Constructs the weapon at the selected refinement rank. */
    public FlowingPurity(int refinement) {
        super("Flowing Purity", new StatsContainer());
        if (refinement < 1 || refinement > 5) {
            throw new IllegalArgumentException(
                    "Flowing Purity refinement must be between 1 and 5");
        }
        this.refinement = refinement;
        elementalDamageBonus = 0.06 + 0.02 * refinement;
        weaponType = WeaponType.CATALYST;
        getStats().set(StatType.BASE_ATK, 565.0);
        getStats().set(StatType.ATK_PERCENT, 0.276);
    }

    /** Returns the selected refinement rank. */
    public int getRefinement() {
        return refinement;
    }

    /** Returns the represented all-element damage bonus. */
    public double getElementalDamageBonus() {
        return elementalDamageBonus;
    }

    /** Returns whether the half-open window is active. */
    public boolean isWindowActive(double currentTime) {
        return currentTime >= activeFrom && currentTime < activeUntil;
    }

    /** Returns the current window expiration. */
    public double getActiveUntil() {
        return activeUntil;
    }

    /** Applies the live bonus to all seven elemental damage stats. */
    @Override
    public void applyPassive(StatsContainer stats, double currentTime) {
        if (!isWindowActive(currentTime)) {
            return;
        }
        for (StatType stat : ELEMENTAL_BONUSES) {
            stats.add(stat, elementalDamageBonus);
        }
    }

    /** Binds one equipped owner and simulator. */
    @Override
    public void initializeForSimulator(
            Character equippedOwner,
            CombatSimulator activeSimulator) {
        if (equippedOwner == null || activeSimulator == null) {
            throw new IllegalArgumentException(
                    "Flowing Purity owner and simulator are required");
        }
        if (simulator != null) {
            if (owner != equippedOwner || simulator != activeSimulator) {
                throw new IllegalStateException(
                        "Flowing Purity is already bound elsewhere");
            }
            return;
        }
        if (equippedOwner.getWeapon() != this) {
            throw new IllegalArgumentException(
                    "Owner must have Flowing Purity equipped");
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
        return new FlowingPurityState(
                this, activeFrom, activeUntil, nextTriggerAt);
    }

    /** Restores state captured from this exact weapon instance. */
    @Override
    public void restoreWeaponState(State state) {
        if (!(state instanceof FlowingPurityState)) {
            throw new IllegalArgumentException(
                    "Flowing Purity state type is invalid");
        }
        FlowingPurityState restored = (FlowingPurityState) state;
        if (restored.source != this) {
            throw new IllegalArgumentException(
                    "Flowing Purity state belongs to another instance");
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

    private static final class FlowingPurityState implements State {
        private final FlowingPurity source;
        private final double activeFrom;
        private final double activeUntil;
        private final double nextTriggerAt;

        private FlowingPurityState(
                FlowingPurity source,
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

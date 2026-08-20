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
 * Covenant of Frost and Snow with its refinement-aware Skill-use EM window.
 *
 * <p>Lv. 90 metadata and R1-R5 values follow Genshin Optimizer commit
 * {@code d791814a}. An accepted owner Elemental Skill opens or refreshes a
 * 12-second Elemental Mastery window. The source passive specifies no trigger
 * cooldown, so every valid Skill use can refresh the half-open window.</p>
 */
public final class CovenantOfFrostAndSnow extends Weapon implements
        ActionTriggeredWeaponEffect,
        SimulatorInitializedWeaponEffect,
        SnapshotAwareWeaponEffect {
    private static final double WINDOW_DURATION = 12.0;

    private final int refinement;
    private final double elementalMasteryBonus;
    private Character owner;
    private CombatSimulator simulator;
    private double activeUntil = Double.NEGATIVE_INFINITY;

    /** Constructs Covenant of Frost and Snow at refinement rank five. */
    public CovenantOfFrostAndSnow() {
        this(5);
    }

    /**
     * Constructs Covenant of Frost and Snow at a selected refinement.
     *
     * @param refinement refinement rank in the inclusive range 1-5
     */
    public CovenantOfFrostAndSnow(int refinement) {
        super("Covenant of Frost and Snow", new StatsContainer());
        validateRefinement(refinement);
        this.refinement = refinement;
        elementalMasteryBonus = 90.0 + 30.0 * refinement;
        weaponType = WeaponType.BOW;
        getStats().set(StatType.BASE_ATK, 510.0);
        getStats().set(StatType.DEF_PERCENT, 0.517);
    }

    /** Returns the selected refinement rank. */
    public int getRefinement() {
        return refinement;
    }

    /** Returns the Elemental Mastery granted during the active window. */
    public double getElementalMasteryBonus() {
        return elementalMasteryBonus;
    }

    /** Returns whether the Skill-use window is active at the supplied time. */
    public boolean isWindowActive(double currentTime) {
        return currentTime < activeUntil;
    }

    /** Returns the current exclusive expiration timestamp. */
    public double getActiveUntil() {
        return activeUntil;
    }

    /** Binds the mutable window to one equipped owner and simulator. */
    @Override
    public void initializeForSimulator(
            Character equippedOwner,
            CombatSimulator activeSimulator) {
        if (equippedOwner == null || activeSimulator == null) {
            throw new IllegalArgumentException(
                    "Covenant owner and simulator are required");
        }
        if (simulator != null) {
            if (owner != equippedOwner || simulator != activeSimulator) {
                throw new IllegalStateException(
                        "Covenant is already bound to another simulator");
            }
            return;
        }
        if (equippedOwner.getWeapon() != this) {
            throw new IllegalArgumentException(
                    "Covenant owner must have this weapon equipped");
        }
        if (!activeSimulator.getPartyMembers().contains(equippedOwner)) {
            throw new IllegalArgumentException(
                    "Covenant owner must belong to the simulator party");
        }
        owner = equippedOwner;
        simulator = activeSimulator;
    }

    /** Opens or refreshes the window on a bound on-field owner Skill use. */
    @Override
    public void onAction(
            Character user,
            CharacterActionRequest request,
            CombatSimulator activeSimulator) {
        if (!isBoundOnFieldOwner(user, activeSimulator)
                || request == null
                || request.getKey() != CharacterActionKey.SKILL) {
            return;
        }
        activeUntil = activeSimulator.getCurrentTime() + WINDOW_DURATION;
    }

    /** Applies the EM bonus before the exact expiration boundary. */
    @Override
    public void applyPassive(StatsContainer stats, double currentTime) {
        if (isWindowActive(currentTime)) {
            stats.add(StatType.ELEMENTAL_MASTERY, elementalMasteryBonus);
        }
    }

    /** Captures the exact Skill-use window boundary. */
    @Override
    public State captureWeaponState() {
        return new CovenantState(this, activeUntil);
    }

    /** Restores state captured from this exact weapon instance. */
    @Override
    public void restoreWeaponState(State state) {
        if (!(state instanceof CovenantState)) {
            throw new IllegalArgumentException("Covenant state type is invalid");
        }
        CovenantState restored = (CovenantState) state;
        if (restored.source != this) {
            throw new IllegalArgumentException(
                    "Covenant state belongs to another weapon instance");
        }
        activeUntil = restored.activeUntil;
    }

    private boolean isBoundOnFieldOwner(
            Character user,
            CombatSimulator activeSimulator) {
        return simulator != null
                && simulator == activeSimulator
                && owner == user
                && owner.getWeapon() == this
                && simulator.getActiveCharacter() == owner;
    }

    private static void validateRefinement(int refinement) {
        if (refinement < 1 || refinement > 5) {
            throw new IllegalArgumentException(
                    "Covenant refinement must be between 1 and 5");
        }
    }

    /** Immutable window state tied to its originating weapon instance. */
    private static final class CovenantState implements State {
        private final CovenantOfFrostAndSnow source;
        private final double activeUntil;

        private CovenantState(
                CovenantOfFrostAndSnow source,
                double activeUntil) {
            this.source = source;
            this.activeUntil = activeUntil;
        }
    }
}

package model.weapon;

import model.entity.ActionTriggeredWeaponEffect;
import model.entity.Character;
import model.entity.DamageTriggeredWeaponEffect;
import model.entity.SimulatorInitializedWeaponEffect;
import model.entity.SnapshotAwareWeaponEffect;
import model.entity.Weapon;
import model.stats.StatsContainer;
import model.type.ActionType;
import model.type.StatType;
import model.type.WeaponType;
import simulation.CombatSimulator;
import simulation.action.AttackAction;
import simulation.action.CharacterActionKey;
import simulation.action.CharacterActionRequest;

/**
 * Clash of Kings with its Skill-use stat window and one-time extension.
 *
 * <p>Lv. 90 metadata and R1-R5 values follow Genshin Optimizer commit
 * {@code d791814a}. A bound owner Skill opens a six-second ATK and Elemental
 * Mastery window on a 12-second trigger cooldown. One qualifying Charged hit
 * while that window is active extends its existing expiration by six seconds.
 * The window is half-open and does not stack.</p>
 */
public final class ClashOfKings extends Weapon implements
        ActionTriggeredWeaponEffect,
        DamageTriggeredWeaponEffect,
        SimulatorInitializedWeaponEffect,
        SnapshotAwareWeaponEffect {
    private static final double WINDOW_DURATION = 6.0;
    private static final double ACTIVATION_COOLDOWN = 12.0;
    private static final double MAXIMUM_EXTENSION = 6.0;

    private final int refinement;
    private final double attackBonus;
    private final double elementalMasteryBonus;
    private Character owner;
    private CombatSimulator simulator;
    private double activeUntil = Double.NEGATIVE_INFINITY;
    private double nextActivationAt = Double.NEGATIVE_INFINITY;
    private boolean extensionAvailable;

    /** Constructs Clash of Kings at refinement rank five. */
    public ClashOfKings() {
        this(5);
    }

    /**
     * Constructs Clash of Kings at a selected refinement.
     *
     * @param refinement refinement rank in the inclusive range 1-5
     */
    public ClashOfKings(int refinement) {
        super("Clash of Kings", new StatsContainer());
        validateRefinement(refinement);
        this.refinement = refinement;
        attackBonus = 0.15 + 0.05 * refinement;
        elementalMasteryBonus = 75.0 + 25.0 * refinement;
        weaponType = WeaponType.CATALYST;
        getStats().set(StatType.BASE_ATK, 510.0);
        getStats().set(StatType.CRIT_RATE, 0.276);
    }

    /** Returns the selected refinement rank. */
    public int getRefinement() {
        return refinement;
    }

    /** Returns the ATK bonus granted during the active window. */
    public double getAttackBonus() {
        return attackBonus;
    }

    /** Returns the Elemental Mastery granted during the active window. */
    public double getElementalMasteryBonus() {
        return elementalMasteryBonus;
    }

    /** Returns whether the stat window is active at the supplied time. */
    public boolean isWindowActive(double currentTime) {
        return currentTime < activeUntil;
    }

    /** Returns the current exclusive expiration timestamp. */
    public double getActiveUntil() {
        return activeUntil;
    }

    /** Returns the next timestamp at which a Skill may activate the passive. */
    public double getNextActivationAt() {
        return nextActivationAt;
    }

    /** Returns whether the current activation may still be extended. */
    public boolean isExtensionAvailable() {
        return extensionAvailable;
    }

    /** Binds the mutable window to one equipped owner and simulator. */
    @Override
    public void initializeForSimulator(
            Character equippedOwner,
            CombatSimulator activeSimulator) {
        if (equippedOwner == null || activeSimulator == null) {
            throw new IllegalArgumentException(
                    "Clash of Kings owner and simulator are required");
        }
        if (simulator != null) {
            if (owner != equippedOwner || simulator != activeSimulator) {
                throw new IllegalStateException(
                        "Clash of Kings is already bound to another simulator");
            }
            return;
        }
        if (equippedOwner.getWeapon() != this) {
            throw new IllegalArgumentException(
                    "Clash of Kings owner must have this weapon equipped");
        }
        if (!activeSimulator.getPartyMembers().contains(equippedOwner)) {
            throw new IllegalArgumentException(
                    "Clash of Kings owner must belong to the simulator party");
        }
        owner = equippedOwner;
        simulator = activeSimulator;
    }

    /** Opens a fresh window when the Skill trigger cooldown is ready. */
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
        double currentTime = activeSimulator.getCurrentTime();
        if (currentTime < nextActivationAt) {
            return;
        }
        activeUntil = currentTime + WINDOW_DURATION;
        nextActivationAt = currentTime + ACTIVATION_COOLDOWN;
        extensionAvailable = true;
    }

    /** Extends the active window once after a positive owner Charged hit. */
    @Override
    public void onDamage(
            Character user,
            AttackAction action,
            double currentTime,
            CombatSimulator activeSimulator) {
        if (!isBoundOnFieldOwner(user, activeSimulator)
                || action == null
                || !action.isHitEffectTrigger()
                || action.getDamagePercent() <= 0.0
                || action.getActionType() != ActionType.CHARGE
                || !extensionAvailable
                || !isWindowActive(currentTime)) {
            return;
        }
        activeUntil += MAXIMUM_EXTENSION;
        extensionAvailable = false;
    }

    /** Applies both passive stats before the exact expiration boundary. */
    @Override
    public void applyPassive(StatsContainer stats, double currentTime) {
        if (isWindowActive(currentTime)) {
            stats.add(StatType.ATK_PERCENT, attackBonus);
            stats.add(StatType.ELEMENTAL_MASTERY, elementalMasteryBonus);
        }
    }

    /** Captures duration, cooldown, and extension-consumption state. */
    @Override
    public State captureWeaponState() {
        return new ClashState(
                this, activeUntil, nextActivationAt, extensionAvailable);
    }

    /** Restores state captured from this exact weapon instance. */
    @Override
    public void restoreWeaponState(State state) {
        if (!(state instanceof ClashState)) {
            throw new IllegalArgumentException(
                    "Clash of Kings state type is invalid");
        }
        ClashState restored = (ClashState) state;
        if (restored.source != this) {
            throw new IllegalArgumentException(
                    "Clash of Kings state belongs to another weapon instance");
        }
        activeUntil = restored.activeUntil;
        nextActivationAt = restored.nextActivationAt;
        extensionAvailable = restored.extensionAvailable;
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
                    "Clash of Kings refinement must be between 1 and 5");
        }
    }

    /** Immutable complete state tied to its originating weapon instance. */
    private static final class ClashState implements State {
        private final ClashOfKings source;
        private final double activeUntil;
        private final double nextActivationAt;
        private final boolean extensionAvailable;

        private ClashState(
                ClashOfKings source,
                double activeUntil,
                double nextActivationAt,
                boolean extensionAvailable) {
            this.source = source;
            this.activeUntil = activeUntil;
            this.nextActivationAt = nextActivationAt;
            this.extensionAvailable = extensionAvailable;
        }
    }
}

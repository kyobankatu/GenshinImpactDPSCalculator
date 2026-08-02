package model.weapon;

import mechanics.buff.BuffId;
import mechanics.buff.SimpleBuff;
import model.entity.Character;
import model.entity.SimulatorInitializedWeaponEffect;
import model.entity.SnapshotAwareWeaponEffect;
import model.entity.Weapon;
import model.stats.StatsContainer;
import model.type.StatType;
import model.type.WeaponType;
import simulation.CombatSimulator;

/**
 * Shared sigil, lockout, team ATK, binding, and snapshot state for Millennial Movement.
 */
abstract class MillennialMovementWeapon extends Weapon
        implements SimulatorInitializedWeaponEffect, SnapshotAwareWeaponEffect {
    private static final double MOVEMENT_DURATION = 12.0;
    private static final double SIGIL_LOCK_DURATION = 20.0;

    private final int refinement;
    private final int requiredSigils;
    private final double sigilCooldown;
    private final double movementAttackBonus;
    private final String uniqueMovementName;
    private final BuffId uniqueMovementId;

    private Character owner;
    private CombatSimulator simulator;
    private int sigilCount;
    private double nextSigilTime = Double.NEGATIVE_INFINITY;
    private double sigilLockUntil = Double.NEGATIVE_INFINITY;

    /**
     * Constructs one Millennial Movement weapon.
     *
     * @param name weapon display name
     * @param weaponType weapon category
     * @param baseAtk Lv. 90 base ATK
     * @param secondaryStat Lv. 90 secondary stat
     * @param secondaryValue Lv. 90 secondary stat value
     * @param refinement refinement rank in the inclusive range 1-5
     * @param requiredSigils sigils consumed per movement activation
     * @param sigilCooldown minimum seconds between sigil gains
     * @param movementAttackBonus party ATK bonus during the movement window
     * @param uniqueMovementName display name for the weapon-specific movement buff
     * @param uniqueMovementId typed id for the weapon-specific movement buff
     */
    protected MillennialMovementWeapon(
            String name,
            WeaponType weaponType,
            double baseAtk,
            StatType secondaryStat,
            double secondaryValue,
            int refinement,
            int requiredSigils,
            double sigilCooldown,
            double movementAttackBonus,
            String uniqueMovementName,
            BuffId uniqueMovementId) {
        super(name, new StatsContainer());
        if (refinement < 1 || refinement > 5) {
            throw new IllegalArgumentException(
                    "Weapon refinement must be between 1 and 5");
        }
        if (requiredSigils < 1 || sigilCooldown < 0.0) {
            throw new IllegalArgumentException(
                    "Millennial Movement sigil definition is invalid");
        }
        if (uniqueMovementId == null
                || uniqueMovementId == BuffId.NONE
                || uniqueMovementId == BuffId.CUSTOM
                || uniqueMovementId == BuffId.MILLENNIAL_MOVEMENT_ATK) {
            throw new IllegalArgumentException(
                    "Millennial Movement unique buff id is invalid");
        }
        this.refinement = refinement;
        this.requiredSigils = requiredSigils;
        this.sigilCooldown = sigilCooldown;
        this.movementAttackBonus = movementAttackBonus;
        this.uniqueMovementName = uniqueMovementName;
        this.uniqueMovementId = uniqueMovementId;
        this.weaponType = weaponType;
        getStats().set(StatType.BASE_ATK, baseAtk);
        getStats().set(secondaryStat, secondaryValue);
    }

    /** Returns the selected refinement rank. */
    public final int getRefinement() {
        return refinement;
    }

    /** Returns the currently retained sigil count. */
    public final int getSigilCount() {
        return sigilCount;
    }

    /** Binds one weapon instance to one owner and simulator. */
    @Override
    public final void initializeForSimulator(
            Character equippedOwner,
            CombatSimulator sim) {
        if (equippedOwner == null || sim == null) {
            throw new IllegalArgumentException(
                    "Weapon owner and simulator are required");
        }
        if (simulator != null) {
            if (owner != equippedOwner || simulator != sim) {
                throw new IllegalStateException(
                        getName() + " is already bound to another simulator");
            }
            return;
        }
        owner = equippedOwner;
        simulator = sim;
        onInitialized(sim);
    }

    /** Allows a concrete reaction-driven weapon to register its listener. */
    protected void onInitialized(CombatSimulator sim) {
        // Most Millennial Movement weapons use damage hooks only.
    }

    /** Returns the bound owner for concrete trigger validation. */
    protected final Character getOwner() {
        return owner;
    }

    /** Returns the bound simulator for concrete trigger validation. */
    protected final CombatSimulator getSimulator() {
        return simulator;
    }

    /**
     * Attempts to gain one sigil and emits both movement buff components at cap.
     *
     * @param source character attributed with the trigger
     * @param activeSimulator simulator dispatching the trigger
     * @param currentTime trigger time in seconds
     */
    protected final void tryGainSigil(
            Character source,
            CombatSimulator activeSimulator,
            double currentTime) {
        if (source != owner
                || activeSimulator != simulator
                || currentTime < sigilLockUntil
                || currentTime < nextSigilTime) {
            return;
        }

        nextSigilTime = currentTime + sigilCooldown;
        sigilCount++;
        if (sigilCount < requiredSigils) {
            return;
        }

        sigilCount = 0;
        sigilLockUntil = currentTime + SIGIL_LOCK_DURATION;
        SimpleBuff attackBuff = new SimpleBuff(
                "Millennial Movement ATK",
                BuffId.MILLENNIAL_MOVEMENT_ATK,
                MOVEMENT_DURATION,
                currentTime,
                stats -> stats.add(
                        StatType.ATK_PERCENT, movementAttackBonus));
        attackBuff.sourcedBy(owner.getCharacterId());
        activeSimulator.applyTeamBuffNoStack(attackBuff);

        SimpleBuff uniqueBuff = new SimpleBuff(
                uniqueMovementName,
                uniqueMovementId,
                MOVEMENT_DURATION,
                currentTime,
                this::applyUniqueMovementStats);
        uniqueBuff.sourcedBy(owner.getCharacterId());
        activeSimulator.applyTeamBuffNoStack(uniqueBuff);
    }

    /** Applies the concrete weapon's unique movement effect. */
    protected abstract void applyUniqueMovementStats(StatsContainer stats);

    /** Captures sigils, CT, and the post-activation lock. */
    @Override
    public final State captureWeaponState() {
        return new MovementState(
                getClass(), sigilCount, nextSigilTime, sigilLockUntil);
    }

    /** Restores only state captured from the same concrete weapon class. */
    @Override
    public final void restoreWeaponState(State state) {
        if (!(state instanceof MovementState)) {
            throw new IllegalArgumentException(
                    "Millennial Movement state type is invalid");
        }
        MovementState movementState = (MovementState) state;
        if (movementState.weaponClass != getClass()) {
            throw new IllegalArgumentException(
                    "Millennial Movement state belongs to another weapon class");
        }
        sigilCount = movementState.sigilCount;
        nextSigilTime = movementState.nextSigilTime;
        sigilLockUntil = movementState.sigilLockUntil;
    }

    /** Immutable state shared by the three concrete movement weapons. */
    private static final class MovementState implements State {
        private final Class<?> weaponClass;
        private final int sigilCount;
        private final double nextSigilTime;
        private final double sigilLockUntil;

        private MovementState(
                Class<?> weaponClass,
                int sigilCount,
                double nextSigilTime,
                double sigilLockUntil) {
            this.weaponClass = weaponClass;
            this.sigilCount = sigilCount;
            this.nextSigilTime = nextSigilTime;
            this.sigilLockUntil = sigilLockUntil;
        }
    }
}

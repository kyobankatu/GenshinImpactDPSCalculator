package model.weapon;

import mechanics.reaction.ReactionResult;
import model.entity.Character;
import model.entity.ElementalReactionTriggeredWeaponEffect;
import model.entity.SimulatorInitializedWeaponEffect;
import model.entity.SnapshotAwareWeaponEffect;
import model.entity.Weapon;
import model.stats.StatsContainer;
import model.type.StatType;
import model.type.WeaponType;
import simulation.CombatSimulator;
import simulation.event.SimpleTimerEvent;

/** Version 7.0 claymore with an automatic three-part Harmonic Movement cycle. */
public final class ForgedByTheGoldenMelody extends Weapon
        implements ElementalReactionTriggeredWeaponEffect,
        SimulatorInitializedWeaponEffect,
        SnapshotAwareWeaponEffect {
    /** Typed Harmonic Movement effect used by the original and copied windows. */
    public enum MovementType {
        /** No source-backed movement is active. */
        NONE,
        /** ATK-percent movement. */
        ATTACK,
        /** Elemental Mastery movement. */
        ELEMENTAL_MASTERY,
        /** Stellar-Conduct and Stellar-Swirl damage movement. */
        STELLAR_DAMAGE
    }

    private static final double MOVEMENT_INTERVAL = 10.0;
    private static final double MOVEMENT_DURATION = 10.0;
    private static final double COPIED_DURATION = 12.0;
    private static final double COPY_COOLDOWN = 12.0;
    private static final MovementType[] MOVEMENT_SEQUENCE = {
        MovementType.ATTACK,
        MovementType.ELEMENTAL_MASTERY,
        MovementType.STELLAR_DAMAGE
    };
    private static final double[] ATTACK_BONUS = {
        0.0, 0.18, 0.225, 0.27, 0.315, 0.36
    };
    private static final double[] ELEMENTAL_MASTERY_BONUS = {
        0.0, 120.0, 150.0, 180.0, 210.0, 240.0
    };
    private static final double[] STELLAR_DAMAGE_BONUS = {
        0.0, 0.28, 0.35, 0.42, 0.49, 0.56
    };

    private final int refinement;
    private final double attackBonus;
    private final double elementalMasteryBonus;
    private final double stellarDamageBonus;

    private Character owner;
    private CombatSimulator simulator;
    private MovementType movementType = MovementType.NONE;
    private MovementType copiedMovementType = MovementType.NONE;
    private int nextMovementIndex;
    private double movementActiveUntil = Double.NEGATIVE_INFINITY;
    private double copiedMovementActiveUntil = Double.NEGATIVE_INFINITY;
    private double nextMovementAt = Double.POSITIVE_INFINITY;
    private double nextCopyAt = Double.NEGATIVE_INFINITY;
    private long timerGeneration;

    /** Constructs Forged by the Golden Melody at refinement rank five. */
    public ForgedByTheGoldenMelody() {
        this(5);
    }

    /**
     * Constructs Forged by the Golden Melody at a selected refinement.
     *
     * @param refinement refinement rank in the inclusive range 1-5
     */
    public ForgedByTheGoldenMelody(int refinement) {
        super("Forged by the Golden Melody", new StatsContainer());
        validateRefinement(refinement);
        this.refinement = refinement;
        this.attackBonus = ATTACK_BONUS[refinement];
        this.elementalMasteryBonus = ELEMENTAL_MASTERY_BONUS[refinement];
        this.stellarDamageBonus = STELLAR_DAMAGE_BONUS[refinement];
        weaponType = WeaponType.CLAYMORE;
        getStats().set(StatType.BASE_ATK, 510.0);
        getStats().set(StatType.CRIT_RATE, 0.276);
    }

    /** Returns this weapon's refinement rank. */
    public int getRefinement() {
        return refinement;
    }

    /** Returns the ATK-percent value of the matching movement. */
    public double getAttackBonus() {
        return attackBonus;
    }

    /** Returns the Elemental Mastery value of the matching movement. */
    public double getElementalMasteryBonus() {
        return elementalMasteryBonus;
    }

    /** Returns the damage bonus applied to both Stellar reaction families. */
    public double getStellarDamageBonus() {
        return stellarDamageBonus;
    }

    /**
     * Returns whether an immediate movement at equipment time is assumed.
     *
     * <p>GO documents each movement as triggering every ten seconds but does
     * not establish an immediate initial proc. This implementation therefore
     * fails closed until the first source-backed ten-second TimerEvent.</p>
     */
    public boolean isImmediateInitialMovementAssumed() {
        return false;
    }

    /** Returns the currently active original movement, or {@link MovementType#NONE}. */
    public MovementType getMovementType(double currentTime) {
        if (currentTime >= movementActiveUntil) {
            return MovementType.NONE;
        }
        return movementType;
    }

    /** Returns the copied movement while its independent twelve-second window lives. */
    public MovementType getCopiedMovementType(double currentTime) {
        if (currentTime >= copiedMovementActiveUntil) {
            return MovementType.NONE;
        }
        return copiedMovementType;
    }

    /** Returns the next scheduled Harmonic Movement time. */
    public double getNextMovementAt() {
        return nextMovementAt;
    }

    /** Returns the next time at which Contrapuntal may be copied. */
    public double getNextCopyAt() {
        return nextCopyAt;
    }

    /** Binds this weapon, schedules its cycle, and registers actual Stellar reactions. */
    @Override
    public void initializeForSimulator(Character equippedOwner, CombatSimulator sim) {
        validateBinding(equippedOwner, sim);
        if (simulator != null) {
            if (owner != equippedOwner || simulator != sim) {
                throw new IllegalStateException(
                        "Forged by the Golden Melody is already bound to another simulator");
            }
            return;
        }
        owner = equippedOwner;
        simulator = sim;
        nextMovementAt = sim.getCurrentTime() + MOVEMENT_INTERVAL;
        sim.addElementalReactionTriggeredWeaponEffect(this);
        scheduleMovementTimer();
    }

    /** Copies the owner's active movement after an actual owner-triggered Stellar reaction. */
    @Override
    public void onElementalReaction(
            ReactionResult result,
            Character source,
            double time,
            CombatSimulator sim) {
        if (!isBoundCallback(source, sim)
                || result == null
                || !result.isStellarReaction()
                || time < nextCopyAt) {
            return;
        }
        MovementType activeMovement = getMovementType(time);
        if (activeMovement == MovementType.NONE) {
            return;
        }
        copiedMovementType = activeMovement;
        copiedMovementActiveUntil = time + COPIED_DURATION;
        nextCopyAt = time + COPY_COOLDOWN;
    }

    /** Applies the original movement and its independently expiring copied instance. */
    @Override
    public void applyPassive(StatsContainer stats, double currentTime) {
        applyMovement(stats, getMovementType(currentTime));
        applyMovement(stats, getCopiedMovementType(currentTime));
    }

    /** Captures cycle phase, copy window, and all scheduled boundaries. */
    @Override
    public State captureWeaponState() {
        return new ForgedState(
                this,
                movementType,
                copiedMovementType,
                nextMovementIndex,
                movementActiveUntil,
                copiedMovementActiveUntil,
                nextMovementAt,
                nextCopyAt);
    }

    /** Restores state and reconstructs the TimerEvent cleared by simulator rollback. */
    @Override
    public void restoreWeaponState(State state) {
        if (!(state instanceof ForgedState)) {
            throw new IllegalArgumentException(
                    "Forged by the Golden Melody state type is invalid");
        }
        ForgedState restored = (ForgedState) state;
        if (restored.source != this) {
            throw new IllegalArgumentException(
                    "Forged by the Golden Melody state belongs to another weapon instance");
        }
        movementType = restored.movementType;
        copiedMovementType = restored.copiedMovementType;
        nextMovementIndex = restored.nextMovementIndex;
        movementActiveUntil = restored.movementActiveUntil;
        copiedMovementActiveUntil = restored.copiedMovementActiveUntil;
        nextMovementAt = restored.nextMovementAt;
        nextCopyAt = restored.nextCopyAt;
        if (simulator != null) {
            scheduleMovementTimer();
        }
    }

    private void scheduleMovementTimer() {
        long scheduledGeneration = ++timerGeneration;
        simulator.registerEvent(new SimpleTimerEvent(
                nextMovementAt, MOVEMENT_INTERVAL) {
            @Override
            public void onTick(CombatSimulator activeSimulator) {
                if (scheduledGeneration != timerGeneration
                        || activeSimulator != simulator
                        || owner.getWeapon() != ForgedByTheGoldenMelody.this) {
                    finish();
                    return;
                }
                activateNextMovement(activeSimulator.getCurrentTime());
            }
        });
    }

    private void activateNextMovement(double currentTime) {
        movementType = MOVEMENT_SEQUENCE[nextMovementIndex];
        nextMovementIndex = (nextMovementIndex + 1) % MOVEMENT_SEQUENCE.length;
        movementActiveUntil = currentTime + MOVEMENT_DURATION;
        nextMovementAt = currentTime + MOVEMENT_INTERVAL;
    }

    private void applyMovement(StatsContainer stats, MovementType type) {
        switch (type) {
            case ATTACK:
                stats.add(StatType.ATK_PERCENT, attackBonus);
                break;
            case ELEMENTAL_MASTERY:
                stats.add(StatType.ELEMENTAL_MASTERY, elementalMasteryBonus);
                break;
            case STELLAR_DAMAGE:
                stats.add(StatType.STELLAR_CONDUCT_DMG_BONUS, stellarDamageBonus);
                stats.add(StatType.STELLAR_SWIRL_DMG_BONUS, stellarDamageBonus);
                break;
            case NONE:
            default:
                break;
        }
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
                    "Weapon owner must have this Forged by the Golden Melody equipped");
        }
    }

    private static void validateRefinement(int refinement) {
        if (refinement < 1 || refinement > 5) {
            throw new IllegalArgumentException(
                    "Forged by the Golden Melody refinement must be between 1 and 5");
        }
    }

    /** Immutable movement-cycle and copy-window state tied to one weapon instance. */
    private static final class ForgedState implements State {
        private final ForgedByTheGoldenMelody source;
        private final MovementType movementType;
        private final MovementType copiedMovementType;
        private final int nextMovementIndex;
        private final double movementActiveUntil;
        private final double copiedMovementActiveUntil;
        private final double nextMovementAt;
        private final double nextCopyAt;

        private ForgedState(
                ForgedByTheGoldenMelody source,
                MovementType movementType,
                MovementType copiedMovementType,
                int nextMovementIndex,
                double movementActiveUntil,
                double copiedMovementActiveUntil,
                double nextMovementAt,
                double nextCopyAt) {
            this.source = source;
            this.movementType = movementType;
            this.copiedMovementType = copiedMovementType;
            this.nextMovementIndex = nextMovementIndex;
            this.movementActiveUntil = movementActiveUntil;
            this.copiedMovementActiveUntil = copiedMovementActiveUntil;
            this.nextMovementAt = nextMovementAt;
            this.nextCopyAt = nextCopyAt;
        }
    }
}

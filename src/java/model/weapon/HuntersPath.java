package model.weapon;

import model.entity.Character;
import model.entity.DamageTriggeredWeaponEffect;
import model.entity.SimulatorInitializedWeaponEffect;
import model.entity.SnapshotAwareWeaponEffect;
import model.entity.Weapon;
import model.stats.StatsContainer;
import model.type.ActionType;
import model.type.Element;
import model.type.StatType;
import model.type.WeaponType;
import simulation.CombatSimulator;
import simulation.action.AttackAction;

/**
 * Hunter's Path with all-element damage and Tireless Hunt instance state.
 *
 * <p>A true Charged hit opens Tireless Hunt after the triggering hit resolves.
 * The next 12 Charged damage instances add live final EM to base damage until
 * ten seconds elapse. Acquisition has an independent 12-second cooldown.
 * Delayed owner Charged hits remain eligible after switching.
 */
public final class HuntersPath extends Weapon
        implements DamageTriggeredWeaponEffect,
        SimulatorInitializedWeaponEffect,
        SnapshotAwareWeaponEffect {
    private static final int MAX_INSTANCES = 12;
    private static final double DURATION = 10.0;
    private static final double ACQUISITION_COOLDOWN = 12.0;

    private final int refinement;
    private final double allElementalDamageBonus;
    private final double elementalMasteryConversionRatio;

    private Character owner;
    private CombatSimulator simulator;
    private int remainingInstances;
    private double activeUntil = Double.NEGATIVE_INFINITY;
    private double acquisitionReadyAt = Double.NEGATIVE_INFINITY;

    /** Constructs Hunter's Path at refinement rank five. */
    public HuntersPath() {
        this(5);
    }

    /**
     * Constructs Hunter's Path at a selected refinement.
     *
     * @param refinement refinement rank in the inclusive range 1-5
     */
    public HuntersPath(int refinement) {
        super("Hunter's Path", new StatsContainer());
        if (refinement < 1 || refinement > 5) {
            throw new IllegalArgumentException(
                    "Hunter's Path refinement must be between 1 and 5");
        }
        this.refinement = refinement;
        this.allElementalDamageBonus = 0.09 + 0.03 * refinement;
        this.elementalMasteryConversionRatio = 1.20 + 0.40 * refinement;
        weaponType = WeaponType.BOW;
        getStats().set(StatType.BASE_ATK, 542.0);
        getStats().set(StatType.CRIT_RATE, 0.441);
        for (Element element : Element.values()) {
            if (element != Element.PHYSICAL) {
                getStats().set(
                        element.getBonusStatType(), allElementalDamageBonus);
            }
        }
    }

    /** Returns the selected refinement rank. */
    public int getRefinement() {
        return refinement;
    }

    /** Returns the unconditional bonus granted to each elemental damage stat. */
    public double getAllElementalDamageBonus() {
        return allElementalDamageBonus;
    }

    /** Returns the live final-EM Charged conversion ratio. */
    public double getElementalMasteryConversionRatio() {
        return elementalMasteryConversionRatio;
    }

    /** Returns the number of Tireless Hunt Charged instances still available. */
    public int getRemainingInstances(double currentTime) {
        expireAt(currentTime);
        return remainingInstances;
    }

    /** Binds this mutable passive to exactly one equipped owner and simulator. */
    @Override
    public void initializeForSimulator(Character equippedOwner, CombatSimulator sim) {
        if (equippedOwner == null || sim == null) {
            throw new IllegalArgumentException("Weapon owner and simulator are required");
        }
        if (simulator != null) {
            if (owner != equippedOwner || simulator != sim) {
                throw new IllegalStateException(
                        "Hunter's Path is already bound to another simulator");
            }
            return;
        }
        if (equippedOwner.getWeapon() != this) {
            throw new IllegalArgumentException(
                    "Weapon owner must have this Hunter's Path equipped");
        }
        owner = equippedOwner;
        simulator = sim;
    }

    /** Applies Tireless Hunt's dynamic EM ratio while instances remain. */
    @Override
    public void applyPassive(StatsContainer stats, double currentTime) {
        expireAt(currentTime);
        if (simulator != null
                && remainingInstances > 0
                && currentTime < activeUntil) {
            stats.add(
                    StatType.ELEMENTAL_MASTERY_TO_CHARGED_FLAT_DMG_RATIO,
                    elementalMasteryConversionRatio);
        }
    }

    /** Acquires after one Charged hit or consumes one later Charged instance. */
    @Override
    public void onDamage(
            Character user,
            AttackAction action,
            double currentTime,
            CombatSimulator sim) {
        if (user != owner
                || sim != simulator
                || simulator == null
                || action == null
                || !action.isHitEffectTrigger()
                || action.getActionType() != ActionType.CHARGE) {
            return;
        }
        expireAt(currentTime);
        if (currentTime >= acquisitionReadyAt) {
            remainingInstances = MAX_INSTANCES;
            activeUntil = currentTime + DURATION;
            acquisitionReadyAt = currentTime + ACQUISITION_COOLDOWN;
            return;
        }
        if (remainingInstances > 0 && currentTime < activeUntil) {
            remainingInstances--;
            if (remainingInstances == 0) {
                activeUntil = Double.NEGATIVE_INFINITY;
            }
        }
    }

    /** Captures the complete instance, duration, and acquisition state. */
    @Override
    public State captureWeaponState() {
        return new HuntersPathState(
                this, remainingInstances, activeUntil, acquisitionReadyAt);
    }

    /** Restores only state captured from this exact weapon instance. */
    @Override
    public void restoreWeaponState(State state) {
        if (!(state instanceof HuntersPathState)) {
            throw new IllegalArgumentException("Hunter's Path state type is invalid");
        }
        HuntersPathState huntersPathState = (HuntersPathState) state;
        if (huntersPathState.source != this) {
            throw new IllegalArgumentException(
                    "Hunter's Path state belongs to another weapon instance");
        }
        remainingInstances = huntersPathState.remainingInstances;
        activeUntil = huntersPathState.activeUntil;
        acquisitionReadyAt = huntersPathState.acquisitionReadyAt;
    }

    private void expireAt(double currentTime) {
        if (remainingInstances > 0 && currentTime >= activeUntil) {
            remainingInstances = 0;
            activeUntil = Double.NEGATIVE_INFINITY;
        }
    }

    /** Immutable complete state tied to its originating weapon instance. */
    private static final class HuntersPathState implements State {
        private final HuntersPath source;
        private final int remainingInstances;
        private final double activeUntil;
        private final double acquisitionReadyAt;

        private HuntersPathState(
                HuntersPath source,
                int remainingInstances,
                double activeUntil,
                double acquisitionReadyAt) {
            this.source = source;
            this.remainingInstances = remainingInstances;
            this.activeUntil = activeUntil;
            this.acquisitionReadyAt = acquisitionReadyAt;
        }
    }
}

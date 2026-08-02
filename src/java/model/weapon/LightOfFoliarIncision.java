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
 * Light of Foliar Incision with an on-field, instance-limited EM conversion.
 *
 * <p>An elemental Normal hit opens the effect after that hit resolves. The
 * following 28 true Normal or Skill damage instances use live final EM and
 * consume one instance each. The effect and acquisition cooldown both last
 * 12 seconds and use half-open expiry boundaries.
 */
public final class LightOfFoliarIncision extends Weapon
        implements DamageTriggeredWeaponEffect,
        SimulatorInitializedWeaponEffect,
        SnapshotAwareWeaponEffect {
    private static final int MAX_INSTANCES = 28;
    private static final double DURATION = 12.0;
    private static final double ACQUISITION_COOLDOWN = 12.0;

    private final int refinement;
    private final double elementalMasteryConversionRatio;

    private Character owner;
    private CombatSimulator simulator;
    private int remainingInstances;
    private double activeUntil = Double.NEGATIVE_INFINITY;
    private double acquisitionReadyAt = Double.NEGATIVE_INFINITY;

    /** Constructs Light of Foliar Incision at refinement rank five. */
    public LightOfFoliarIncision() {
        this(5);
    }

    /**
     * Constructs Light of Foliar Incision at a selected refinement.
     *
     * @param refinement refinement rank in the inclusive range 1-5
     */
    public LightOfFoliarIncision(int refinement) {
        super("Light of Foliar Incision", new StatsContainer());
        if (refinement < 1 || refinement > 5) {
            throw new IllegalArgumentException(
                    "Light of Foliar Incision refinement must be between 1 and 5");
        }
        this.refinement = refinement;
        this.elementalMasteryConversionRatio = 0.90 + 0.30 * refinement;
        weaponType = WeaponType.SWORD;
        getStats().set(StatType.BASE_ATK, 542.0);
        getStats().set(StatType.CRIT_DMG, 0.882);
        getStats().set(StatType.CRIT_RATE, 0.03 + 0.01 * refinement);
    }

    /** Returns the selected refinement rank. */
    public int getRefinement() {
        return refinement;
    }

    /** Returns the live final-EM conversion ratio. */
    public double getElementalMasteryConversionRatio() {
        return elementalMasteryConversionRatio;
    }

    /** Returns the number of Foliar Incision damage instances still available. */
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
                        "Light of Foliar Incision is already bound to another simulator");
            }
            return;
        }
        if (equippedOwner.getWeapon() != this) {
            throw new IllegalArgumentException(
                    "Weapon owner must have this Light of Foliar Incision equipped");
        }
        owner = equippedOwner;
        simulator = sim;
    }

    /** Applies the dynamic EM ratio only while the bound owner is active. */
    @Override
    public void applyPassive(StatsContainer stats, double currentTime) {
        expireAt(currentTime);
        if (simulator != null
                && simulator.getActiveCharacter() == owner
                && remainingInstances > 0
                && currentTime < activeUntil) {
            stats.add(
                    StatType.ELEMENTAL_MASTERY_TO_NORMAL_SKILL_FLAT_DMG_RATIO,
                    elementalMasteryConversionRatio);
        }
    }

    /**
     * Acquires after an elemental Normal hit or consumes one later eligible hit.
     */
    @Override
    public void onDamage(
            Character user,
            AttackAction action,
            double currentTime,
            CombatSimulator sim) {
        if (user != owner
                || sim != simulator
                || simulator == null
                || sim.getActiveCharacter() != owner
                || action == null
                || !action.isHitEffectTrigger()) {
            return;
        }
        expireAt(currentTime);
        if (isElementalNormal(action) && currentTime >= acquisitionReadyAt) {
            remainingInstances = MAX_INSTANCES;
            activeUntil = currentTime + DURATION;
            acquisitionReadyAt = currentTime + ACQUISITION_COOLDOWN;
            return;
        }
        if (remainingInstances > 0
                && currentTime < activeUntil
                && isEligibleDamage(action)) {
            remainingInstances--;
            if (remainingInstances == 0) {
                activeUntil = Double.NEGATIVE_INFINITY;
            }
        }
    }

    /** Captures the complete instance, duration, and acquisition state. */
    @Override
    public State captureWeaponState() {
        return new FoliarState(
                this, remainingInstances, activeUntil, acquisitionReadyAt);
    }

    /** Restores only state captured from this exact weapon instance. */
    @Override
    public void restoreWeaponState(State state) {
        if (!(state instanceof FoliarState)) {
            throw new IllegalArgumentException(
                    "Light of Foliar Incision state type is invalid");
        }
        FoliarState foliarState = (FoliarState) state;
        if (foliarState.source != this) {
            throw new IllegalArgumentException(
                    "Light of Foliar Incision state belongs to another weapon instance");
        }
        remainingInstances = foliarState.remainingInstances;
        activeUntil = foliarState.activeUntil;
        acquisitionReadyAt = foliarState.acquisitionReadyAt;
    }

    private boolean isElementalNormal(AttackAction action) {
        return action.getActionType() == ActionType.NORMAL
                && action.getElement() != Element.PHYSICAL;
    }

    private boolean isEligibleDamage(AttackAction action) {
        return action.getActionType() == ActionType.NORMAL
                || action.getActionType() == ActionType.SKILL
                || action.isCountsAsSkillDmg();
    }

    private void expireAt(double currentTime) {
        if (remainingInstances > 0 && currentTime >= activeUntil) {
            remainingInstances = 0;
            activeUntil = Double.NEGATIVE_INFINITY;
        }
    }

    /** Immutable complete state tied to its originating weapon instance. */
    private static final class FoliarState implements State {
        private final LightOfFoliarIncision source;
        private final int remainingInstances;
        private final double activeUntil;
        private final double acquisitionReadyAt;

        private FoliarState(
                LightOfFoliarIncision source,
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

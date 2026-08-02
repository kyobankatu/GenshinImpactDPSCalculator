package model.weapon;

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

/**
 * Cinnabar Spindle with a live final-DEF Elemental Skill damage addition.
 *
 * <p>The first ready Skill hit receives the passive, then leaves the effect
 * available for another 0.1 seconds. Following current gcsim ordering, the
 * 1.5-second readiness period begins after that clearing interval. Delayed
 * owner Skill hits remain eligible while the owner is off-field.
 */
public final class CinnabarSpindle extends Weapon
        implements DamageTriggeredWeaponEffect,
        SimulatorInitializedWeaponEffect,
        SnapshotAwareWeaponEffect {
    private static final double CLEAR_DELAY = 0.1;
    private static final double READINESS_COOLDOWN = 1.5;

    private final int refinement;
    private final double defenseConversionRatio;

    private Character owner;
    private CombatSimulator simulator;
    private double effectClearsAt = Double.NEGATIVE_INFINITY;
    private double nextReadyAt = Double.NEGATIVE_INFINITY;

    /** Constructs Cinnabar Spindle at refinement rank five. */
    public CinnabarSpindle() {
        this(5);
    }

    /**
     * Constructs Cinnabar Spindle at a selected refinement.
     *
     * @param refinement refinement rank in the inclusive range 1-5
     */
    public CinnabarSpindle(int refinement) {
        super("Cinnabar Spindle", new StatsContainer());
        if (refinement < 1 || refinement > 5) {
            throw new IllegalArgumentException(
                    "Cinnabar Spindle refinement must be between 1 and 5");
        }
        this.refinement = refinement;
        this.defenseConversionRatio = 0.30 + 0.10 * refinement;
        weaponType = WeaponType.SWORD;
        getStats().set(StatType.BASE_ATK, 454.0);
        getStats().set(StatType.DEF_PERCENT, 0.690);
    }

    /** Returns the selected refinement rank. */
    public int getRefinement() {
        return refinement;
    }

    /** Returns the live final-DEF conversion ratio. */
    public double getDefenseConversionRatio() {
        return defenseConversionRatio;
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
                        "Cinnabar Spindle is already bound to another simulator");
            }
            return;
        }
        if (equippedOwner.getWeapon() != this) {
            throw new IllegalArgumentException(
                    "Weapon owner must have this Cinnabar Spindle equipped");
        }
        owner = equippedOwner;
        simulator = sim;
    }

    /**
     * Exposes the typed Skill conversion while the effect is retained or ready.
     *
     * <p>The typed formula evaluates final DEF after later stat sources merge.
     */
    @Override
    public void applyPassive(StatsContainer stats, double currentTime) {
        if (simulator != null
                && (currentTime < effectClearsAt || currentTime >= nextReadyAt)) {
            stats.add(StatType.DEF_TO_SKILL_FLAT_DMG_RATIO, defenseConversionRatio);
        }
    }

    /** Starts the post-hit clearing and readiness windows after an eligible hit. */
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
                || !isSkillHit(action)) {
            return;
        }
        if (currentTime < effectClearsAt || currentTime < nextReadyAt) {
            return;
        }
        effectClearsAt = currentTime + CLEAR_DELAY;
        nextReadyAt = effectClearsAt + READINESS_COOLDOWN;
    }

    /** Captures both half-open timing boundaries. */
    @Override
    public State captureWeaponState() {
        return new CinnabarState(this, effectClearsAt, nextReadyAt);
    }

    /** Restores only state captured from this exact weapon instance. */
    @Override
    public void restoreWeaponState(State state) {
        if (!(state instanceof CinnabarState)) {
            throw new IllegalArgumentException("Cinnabar Spindle state type is invalid");
        }
        CinnabarState cinnabarState = (CinnabarState) state;
        if (cinnabarState.source != this) {
            throw new IllegalArgumentException(
                    "Cinnabar Spindle state belongs to another weapon instance");
        }
        effectClearsAt = cinnabarState.effectClearsAt;
        nextReadyAt = cinnabarState.nextReadyAt;
    }

    private boolean isSkillHit(AttackAction action) {
        return action.getActionType() == ActionType.SKILL
                || action.isCountsAsSkillDmg();
    }

    /** Immutable timing state tied to its originating weapon instance. */
    private static final class CinnabarState implements State {
        private final CinnabarSpindle source;
        private final double effectClearsAt;
        private final double nextReadyAt;

        private CinnabarState(
                CinnabarSpindle source,
                double effectClearsAt,
                double nextReadyAt) {
            this.source = source;
            this.effectClearsAt = effectClearsAt;
            this.nextReadyAt = nextReadyAt;
        }
    }
}

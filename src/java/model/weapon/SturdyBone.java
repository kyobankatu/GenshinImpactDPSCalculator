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

/** Sturdy Bone with its post-Dash Normal Attack additive-damage window. */
public final class SturdyBone extends Weapon
        implements ActionTriggeredWeaponEffect,
        DamageTriggeredWeaponEffect,
        SimulatorInitializedWeaponEffect,
        SnapshotAwareWeaponEffect {
    private static final double DURATION = 7.0;
    private static final int MAX_TRIGGERS = 18;

    private final int refinement;
    private final double normalAttackRatio;
    private Character owner;
    private CombatSimulator simulator;
    private double activeUntil = Double.NEGATIVE_INFINITY;
    private int remainingTriggers;

    /** Constructs Sturdy Bone at refinement rank five. */
    public SturdyBone() {
        this(5);
    }

    /** Constructs Sturdy Bone at the selected refinement rank. */
    public SturdyBone(int refinement) {
        super("Sturdy Bone", new StatsContainer());
        if (refinement < 1 || refinement > 5) {
            throw new IllegalArgumentException(
                    "Sturdy Bone refinement must be between 1 and 5");
        }
        this.refinement = refinement;
        normalAttackRatio = 0.12 + 0.04 * refinement;
        weaponType = WeaponType.SWORD;
        getStats().set(StatType.BASE_ATK, 565.0);
        getStats().set(StatType.ATK_PERCENT, 0.276);
    }

    /** Returns the selected refinement rank. */
    public int getRefinement() {
        return refinement;
    }

    /** Returns the final-ATK ratio added to each eligible Normal hit. */
    public double getNormalAttackRatio() {
        return normalAttackRatio;
    }

    /** Returns eligible hits remaining before expiry at the supplied time. */
    public int getRemainingTriggers(double currentTime) {
        return currentTime < activeUntil ? remainingTriggers : 0;
    }

    /** Binds mutable window state to exactly one equipped owner and simulator. */
    @Override
    public void initializeForSimulator(
            Character equippedOwner,
            CombatSimulator sim) {
        if (equippedOwner == null || sim == null) {
            throw new IllegalArgumentException(
                    "Sturdy Bone owner and simulator are required");
        }
        if (equippedOwner.getWeapon() != this) {
            throw new IllegalArgumentException(
                    "Sturdy Bone owner must have this weapon equipped");
        }
        if (simulator != null) {
            if (owner != equippedOwner || simulator != sim) {
                throw new IllegalStateException(
                        "Sturdy Bone is already bound to another simulator");
            }
            return;
        }
        owner = equippedOwner;
        simulator = sim;
    }

    /** Refreshes the seven-second, eighteen-hit window after an owner Dash. */
    @Override
    public void onAction(
            Character user,
            CharacterActionRequest request,
            CombatSimulator sim) {
        if (user == owner
                && sim == simulator
                && request != null
                && request.getKey() == CharacterActionKey.DASH) {
            activeUntil = sim.getCurrentTime() + DURATION;
            remainingTriggers = MAX_TRIGGERS;
        }
    }

    /** Applies the additive Normal damage ratio while one trigger remains. */
    @Override
    public void applyPassive(StatsContainer stats, double currentTime) {
        if (currentTime < activeUntil && remainingTriggers > 0) {
            stats.add(
                    StatType.NORMAL_ATTACK_ATK_FLAT_DMG_RATIO,
                    normalAttackRatio);
        }
    }

    /** Consumes one trigger after an eligible owner Normal hit resolves. */
    @Override
    public void onDamage(
            Character user,
            AttackAction action,
            double currentTime,
            CombatSimulator sim) {
        if (user == owner
                && sim == simulator
                && action != null
                && action.isHitEffectTrigger()
                && action.getActionType() == ActionType.NORMAL
                && currentTime < activeUntil
                && remainingTriggers > 0) {
            remainingTriggers--;
        }
    }

    /** Captures the complete mutable trigger window. */
    @Override
    public State captureWeaponState() {
        return new SturdyBoneState(this, activeUntil, remainingTriggers);
    }

    /** Restores state captured from this exact weapon instance. */
    @Override
    public void restoreWeaponState(State state) {
        if (!(state instanceof SturdyBoneState)) {
            throw new IllegalArgumentException(
                    "Sturdy Bone state type is invalid");
        }
        SturdyBoneState sturdyState = (SturdyBoneState) state;
        if (sturdyState.source != this) {
            throw new IllegalArgumentException(
                    "Sturdy Bone state belongs to another weapon instance");
        }
        activeUntil = sturdyState.activeUntil;
        remainingTriggers = sturdyState.remainingTriggers;
    }

    /** Immutable mutable-state payload tied to one weapon instance. */
    private static final class SturdyBoneState implements State {
        private final SturdyBone source;
        private final double activeUntil;
        private final int remainingTriggers;

        private SturdyBoneState(
                SturdyBone source,
                double activeUntil,
                int remainingTriggers) {
            this.source = source;
            this.activeUntil = activeUntil;
            this.remainingTriggers = remainingTriggers;
        }
    }
}

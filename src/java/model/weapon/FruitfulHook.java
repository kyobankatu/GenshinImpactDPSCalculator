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

/** Fruitful Hook with Plunging CRIT Rate and a post-Plunge damage window. */
public final class FruitfulHook extends Weapon
        implements DamageTriggeredWeaponEffect,
        SimulatorInitializedWeaponEffect,
        SnapshotAwareWeaponEffect {
    private static final double DURATION = 10.0;

    private final int refinement;
    private final double actionBonus;
    private Character owner;
    private CombatSimulator simulator;
    private double activeUntil = Double.NEGATIVE_INFINITY;

    /** Constructs Fruitful Hook at refinement rank five. */
    public FruitfulHook() {
        this(5);
    }

    /**
     * Constructs Fruitful Hook at a selected refinement.
     *
     * @param refinement refinement rank in the inclusive range 1-5
     */
    public FruitfulHook(int refinement) {
        super("Fruitful Hook", new StatsContainer());
        validateRefinement(refinement);
        this.refinement = refinement;
        this.actionBonus = 0.12 + 0.04 * refinement;
        weaponType = WeaponType.CLAYMORE;
        getStats().set(StatType.BASE_ATK, 565.0);
        getStats().set(StatType.ATK_PERCENT, 0.276);
    }

    /** Returns this weapon's refinement rank. */
    public int getRefinement() {
        return refinement;
    }

    /** Returns both the Plunging CRIT Rate and action damage coefficient. */
    public double getActionBonus() {
        return actionBonus;
    }

    /** Binds this mutable passive to exactly one equipped owner and simulator. */
    @Override
    public void initializeForSimulator(Character equippedOwner, CombatSimulator sim) {
        validateBinding(equippedOwner, sim);
        if (simulator != null) {
            if (owner != equippedOwner || simulator != sim) {
                throw new IllegalStateException(
                        "Fruitful Hook is already bound to another simulator");
            }
            return;
        }
        owner = equippedOwner;
        simulator = sim;
    }

    /** Applies the unconditional CRIT Rate and active action damage bonuses. */
    @Override
    public void applyPassive(StatsContainer stats, double currentTime) {
        stats.add(StatType.PLUNGING_ATTACK_CRIT_RATE, actionBonus);
        if (currentTime < activeUntil) {
            stats.add(StatType.NORMAL_ATTACK_DMG_BONUS, actionBonus);
            stats.add(StatType.CHARGED_ATTACK_DMG_BONUS, actionBonus);
            stats.add(StatType.PLUNGING_ATTACK_DMG_BONUS, actionBonus);
        }
    }

    /** Opens the damage window after an owner Plunging hit resolves. */
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
                && action.getDamagePercent() > 0.0
                && action.getActionType() == ActionType.PLUNGE) {
            activeUntil = currentTime + DURATION;
        }
    }

    /** Captures the active window boundary. */
    @Override
    public State captureWeaponState() {
        return new HookState(this, activeUntil);
    }

    /** Restores an active window captured from this exact weapon instance. */
    @Override
    public void restoreWeaponState(State state) {
        if (!(state instanceof HookState)) {
            throw new IllegalArgumentException("Fruitful Hook state type is invalid");
        }
        HookState hookState = (HookState) state;
        if (hookState.source != this) {
            throw new IllegalArgumentException(
                    "Fruitful Hook state belongs to another weapon instance");
        }
        activeUntil = hookState.activeUntil;
    }

    private void validateBinding(Character equippedOwner, CombatSimulator sim) {
        if (equippedOwner == null || sim == null) {
            throw new IllegalArgumentException("Weapon owner and simulator are required");
        }
        if (equippedOwner.getWeapon() != this) {
            throw new IllegalArgumentException(
                    "Weapon owner must have this Fruitful Hook equipped");
        }
    }

    private static void validateRefinement(int refinement) {
        if (refinement < 1 || refinement > 5) {
            throw new IllegalArgumentException(
                    "Fruitful Hook refinement must be between 1 and 5");
        }
    }

    /** Immutable active-window state tied to one weapon instance. */
    private static final class HookState implements State {
        private final FruitfulHook source;
        private final double activeUntil;

        private HookState(FruitfulHook source, double activeUntil) {
            this.source = source;
            this.activeUntil = activeUntil;
        }
    }
}

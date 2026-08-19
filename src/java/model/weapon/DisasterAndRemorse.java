package model.weapon;

import model.entity.ActionTriggeredWeaponEffect;
import model.entity.Character;
import model.entity.SimulatorInitializedWeaponEffect;
import model.entity.SnapshotAwareWeaponEffect;
import model.entity.SwitchAwareWeaponEffect;
import model.entity.Weapon;
import model.stats.StatsContainer;
import model.type.ActionType;
import model.type.StatType;
import model.type.WeaponType;
import simulation.CombatSimulator;
import simulation.DamageListener;
import simulation.action.AttackAction;
import simulation.action.CharacterActionKey;
import simulation.action.CharacterActionRequest;

/**
 * Disaster and Remorse polearm with the owner-bound Path of Conflict state.
 *
 * <p>Lv. 90 metadata and R1-R5 values follow Genshin Optimizer commit
 * {@code 61c5556a}. An accepted owner Skill opens Path of Conflict for 17
 * seconds and both three-second damage windows on an 18-second activation
 * cooldown. Positive owner Normal or Charged hits extend Irreparable, while
 * Skill or Burst hits extend Unforgivable. The two extension routes have
 * independent {@code 0.1}-second trigger intervals.</p>
 *
 * <p>Hexerei: Secret Rite amplification intentionally fails closed because
 * this baseline has no typed Hexerei party-state contract.</p>
 */
public final class DisasterAndRemorse extends Weapon implements
        ActionTriggeredWeaponEffect,
        DamageListener,
        SimulatorInitializedWeaponEffect,
        SnapshotAwareWeaponEffect,
        SwitchAwareWeaponEffect {
    private static final double PATH_DURATION = 17.0;
    private static final double WINDOW_DURATION = 3.0;
    private static final double ACTIVATION_COOLDOWN = 18.0;
    private static final double EXTENSION_AMOUNT = 1.0;
    private static final double EXTENSION_COOLDOWN = 0.1;

    private final int refinement;
    private final double damageBonus;
    private Character owner;
    private CombatSimulator simulator;
    private double pathFrom = Double.POSITIVE_INFINITY;
    private double pathUntil = Double.NEGATIVE_INFINITY;
    private double unforgivableFrom = Double.POSITIVE_INFINITY;
    private double unforgivableUntil = Double.NEGATIVE_INFINITY;
    private double irreparableFrom = Double.POSITIVE_INFINITY;
    private double irreparableUntil = Double.NEGATIVE_INFINITY;
    private double nextActivationAt = Double.NEGATIVE_INFINITY;
    private double nextIrreparableExtensionAt = Double.NEGATIVE_INFINITY;
    private double nextUnforgivableExtensionAt = Double.NEGATIVE_INFINITY;

    /** Constructs Disaster and Remorse at refinement rank five. */
    public DisasterAndRemorse() {
        this(5);
    }

    /**
     * Constructs Disaster and Remorse at the selected refinement.
     *
     * @param refinement refinement rank in the inclusive range 1-5
     */
    public DisasterAndRemorse(int refinement) {
        super("Disaster and Remorse", new StatsContainer());
        validateRefinement(refinement);
        this.refinement = refinement;
        damageBonus = 0.30 + 0.10 * refinement;
        weaponType = WeaponType.POLEARM;
        getStats().set(StatType.BASE_ATK, 674.0);
        getStats().set(StatType.CRIT_RATE, 0.221);
    }

    /** Returns the selected refinement rank. */
    public int getRefinement() {
        return refinement;
    }

    /** Returns either base damage-window bonus before Hexerei amplification. */
    public double getDamageBonus() {
        return damageBonus;
    }

    /** Returns whether unsupported Hexerei amplification is active. */
    public boolean isHexereiAmplificationActive() {
        return false;
    }

    /** Returns whether Path of Conflict is active at the supplied timestamp. */
    public boolean isPathActive(double currentTime) {
        normalizeAt(currentTime);
        return currentTime >= pathFrom && currentTime < pathUntil;
    }

    /** Returns whether Unforgivable is active at the supplied timestamp. */
    public boolean isUnforgivableActive(double currentTime) {
        normalizeAt(currentTime);
        return currentTime >= unforgivableFrom
                && currentTime < unforgivableUntil;
    }

    /** Returns whether Irreparable is active at the supplied timestamp. */
    public boolean isIrreparableActive(double currentTime) {
        normalizeAt(currentTime);
        return currentTime >= irreparableFrom
                && currentTime < irreparableUntil;
    }

    /** Returns the current Unforgivable expiration timestamp. */
    public double getUnforgivableUntil() {
        return unforgivableUntil;
    }

    /** Returns the current Irreparable expiration timestamp. */
    public double getIrreparableUntil() {
        return irreparableUntil;
    }

    /** Applies the two independently timed action-type damage bonuses. */
    @Override
    public void applyPassive(StatsContainer stats, double currentTime) {
        if (isUnforgivableActive(currentTime)) {
            stats.add(StatType.NORMAL_ATTACK_DMG_BONUS, damageBonus);
            stats.add(StatType.CHARGED_ATTACK_DMG_BONUS, damageBonus);
        }
        if (isIrreparableActive(currentTime)) {
            stats.add(StatType.SKILL_DMG_BONUS, damageBonus);
            stats.add(StatType.BURST_DMG_BONUS, damageBonus);
        }
    }

    /** Binds the state and direct-hit listener to one equipped owner. */
    @Override
    public void initializeForSimulator(
            Character equippedOwner,
            CombatSimulator activeSimulator) {
        if (equippedOwner == null || activeSimulator == null) {
            throw new IllegalArgumentException(
                    "Disaster and Remorse owner and simulator are required");
        }
        if (simulator != null) {
            if (owner != equippedOwner || simulator != activeSimulator) {
                throw new IllegalStateException(
                        "Disaster and Remorse is already bound elsewhere");
            }
            return;
        }
        validateBinding(equippedOwner, activeSimulator);
        owner = equippedOwner;
        simulator = activeSimulator;
        activeSimulator.addDamageListener(this);
    }

    /** Opens Path and both windows after an accepted on-field owner Skill. */
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
        pathFrom = currentTime;
        pathUntil = currentTime + PATH_DURATION;
        unforgivableFrom = currentTime;
        unforgivableUntil = currentTime + WINDOW_DURATION;
        irreparableFrom = currentTime;
        irreparableUntil = currentTime + WINDOW_DURATION;
        nextActivationAt = currentTime + ACTIVATION_COOLDOWN;
        nextIrreparableExtensionAt = Double.NEGATIVE_INFINITY;
        nextUnforgivableExtensionAt = Double.NEGATIVE_INFINITY;
    }

    /** Extends the opposite damage window from qualifying positive owner hits. */
    @Override
    public void onDamage(
            Character actor,
            AttackAction action,
            double damage,
            double currentTime) {
        if (!isEligibleOwnerHit(actor, action, damage)
                || !isPathActive(currentTime)) {
            return;
        }
        if (isNormalOrCharged(action)
                && currentTime >= nextIrreparableExtensionAt) {
            irreparableUntil += EXTENSION_AMOUNT;
            nextIrreparableExtensionAt = currentTime + EXTENSION_COOLDOWN;
        }
        if (isSkillOrBurst(action)
                && currentTime >= nextUnforgivableExtensionAt) {
            unforgivableUntil += EXTENSION_AMOUNT;
            nextUnforgivableExtensionAt = currentTime + EXTENSION_COOLDOWN;
        }
    }

    /** Removes Path and both windows immediately when the owner leaves field. */
    @Override
    public void onSwitchOut(Character user, CombatSimulator activeSimulator) {
        if (simulator == null
                || user != owner
                || activeSimulator != simulator
                || owner.getWeapon() != this) {
            return;
        }
        clearActiveEffects();
    }

    /** Captures all windows, activation cooldown, and independent hit gates. */
    @Override
    public State captureWeaponState() {
        return new DisasterState(
                this,
                pathFrom,
                pathUntil,
                unforgivableFrom,
                unforgivableUntil,
                irreparableFrom,
                irreparableUntil,
                nextActivationAt,
                nextIrreparableExtensionAt,
                nextUnforgivableExtensionAt);
    }

    /** Restores state captured from this exact weapon instance. */
    @Override
    public void restoreWeaponState(State state) {
        if (!(state instanceof DisasterState)) {
            throw new IllegalArgumentException(
                    "Disaster and Remorse state type is invalid");
        }
        DisasterState restored = (DisasterState) state;
        if (restored.source != this) {
            throw new IllegalArgumentException(
                    "Disaster and Remorse state belongs to another instance");
        }
        pathFrom = restored.pathFrom;
        pathUntil = restored.pathUntil;
        unforgivableFrom = restored.unforgivableFrom;
        unforgivableUntil = restored.unforgivableUntil;
        irreparableFrom = restored.irreparableFrom;
        irreparableUntil = restored.irreparableUntil;
        nextActivationAt = restored.nextActivationAt;
        nextIrreparableExtensionAt = restored.nextIrreparableExtensionAt;
        nextUnforgivableExtensionAt = restored.nextUnforgivableExtensionAt;
    }

    private boolean isEligibleOwnerHit(
            Character actor,
            AttackAction action,
            double damage) {
        return simulator != null
                && actor == owner
                && owner.getWeapon() == this
                && simulator.getPartyMembers().contains(owner)
                && action != null
                && action.isHitEffectTrigger()
                && action.getDamagePercent() > 0.0
                && damage > 0.0;
    }

    private boolean isBoundOnFieldOwner(
            Character actor,
            CombatSimulator activeSimulator) {
        return simulator != null
                && activeSimulator == simulator
                && actor == owner
                && owner.getWeapon() == this
                && simulator.getActiveCharacter() == owner
                && simulator.getPartyMembers().contains(owner);
    }

    private static boolean isNormalOrCharged(AttackAction action) {
        return action.getActionType() == ActionType.NORMAL
                || action.getActionType() == ActionType.CHARGE;
    }

    private static boolean isSkillOrBurst(AttackAction action) {
        return action.getActionType() == ActionType.SKILL
                || action.getActionType() == ActionType.BURST
                || action.isCountsAsSkillDmg()
                || action.isCountsAsBurstDmg();
    }

    private void normalizeAt(double currentTime) {
        if (currentTime >= pathUntil) {
            clearActiveEffects();
        }
    }

    private void clearActiveEffects() {
        pathFrom = Double.POSITIVE_INFINITY;
        pathUntil = Double.NEGATIVE_INFINITY;
        unforgivableFrom = Double.POSITIVE_INFINITY;
        unforgivableUntil = Double.NEGATIVE_INFINITY;
        irreparableFrom = Double.POSITIVE_INFINITY;
        irreparableUntil = Double.NEGATIVE_INFINITY;
        nextIrreparableExtensionAt = Double.NEGATIVE_INFINITY;
        nextUnforgivableExtensionAt = Double.NEGATIVE_INFINITY;
    }

    private void validateBinding(
            Character equippedOwner,
            CombatSimulator activeSimulator) {
        if (equippedOwner.getWeapon() != this) {
            throw new IllegalArgumentException(
                    "Owner must have Disaster and Remorse equipped");
        }
        if (!activeSimulator.getPartyMembers().contains(equippedOwner)) {
            throw new IllegalArgumentException(
                    "Owner must belong to the target simulator party");
        }
    }

    private static void validateRefinement(int refinement) {
        if (refinement < 1 || refinement > 5) {
            throw new IllegalArgumentException(
                    "Disaster and Remorse refinement must be between 1 and 5");
        }
    }

    /** Immutable Path and extension state tied to one weapon instance. */
    private static final class DisasterState implements State {
        private final DisasterAndRemorse source;
        private final double pathFrom;
        private final double pathUntil;
        private final double unforgivableFrom;
        private final double unforgivableUntil;
        private final double irreparableFrom;
        private final double irreparableUntil;
        private final double nextActivationAt;
        private final double nextIrreparableExtensionAt;
        private final double nextUnforgivableExtensionAt;

        private DisasterState(
                DisasterAndRemorse source,
                double pathFrom,
                double pathUntil,
                double unforgivableFrom,
                double unforgivableUntil,
                double irreparableFrom,
                double irreparableUntil,
                double nextActivationAt,
                double nextIrreparableExtensionAt,
                double nextUnforgivableExtensionAt) {
            this.source = source;
            this.pathFrom = pathFrom;
            this.pathUntil = pathUntil;
            this.unforgivableFrom = unforgivableFrom;
            this.unforgivableUntil = unforgivableUntil;
            this.irreparableFrom = irreparableFrom;
            this.irreparableUntil = irreparableUntil;
            this.nextActivationAt = nextActivationAt;
            this.nextIrreparableExtensionAt = nextIrreparableExtensionAt;
            this.nextUnforgivableExtensionAt = nextUnforgivableExtensionAt;
        }
    }
}

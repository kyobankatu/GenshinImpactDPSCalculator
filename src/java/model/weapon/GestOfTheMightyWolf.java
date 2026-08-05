package model.weapon;

import model.entity.ActionTriggeredWeaponEffect;
import model.entity.Character;
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
 * Gest of the Mighty Wolf's non-Hexerei Four Winds' Hymn branch.
 *
 * <p>Values and trigger routes follow pinned gcsim {@code ef41805d}. The
 * permanent attack-speed bonus and generic damage stacks are represented;
 * Hexerei: Secret Rite's matching CRIT DMG stacks remain excluded because the
 * simulator has no typed Hexerei party state.</p>
 */
public final class GestOfTheMightyWolf extends Weapon implements
        ActionTriggeredWeaponEffect,
        SimulatorInitializedWeaponEffect,
        SnapshotAwareWeaponEffect {
    private static final double ATTACK_SPEED_BONUS = 0.10;
    private static final double WINDOW_DURATION = 4.0;
    private static final int MAX_STACKS = 4;

    private final int refinement;
    private final double damagePerStack;
    private Character owner;
    private CombatSimulator simulator;
    private int stacks;
    private double expiresAt = Double.NEGATIVE_INFINITY;

    /** Constructs the weapon at refinement rank five. */
    public GestOfTheMightyWolf() {
        this(5);
    }

    /** Constructs the weapon at the selected refinement rank. */
    public GestOfTheMightyWolf(int refinement) {
        super("Gest of the Mighty Wolf", new StatsContainer());
        if (refinement < 1 || refinement > 5) {
            throw new IllegalArgumentException(
                    "Gest of the Mighty Wolf refinement must be between 1 and 5");
        }
        this.refinement = refinement;
        damagePerStack = 0.055 + 0.020 * refinement;
        weaponType = WeaponType.CLAYMORE;
        getStats().set(StatType.BASE_ATK, 608.0);
        getStats().set(StatType.CRIT_RATE, 0.331);
    }

    /** Returns the selected refinement rank. */
    public int getRefinement() {
        return refinement;
    }

    /** Returns generic damage granted by one Hymn stack. */
    public double getDamagePerStack() {
        return damagePerStack;
    }

    /** Returns live stacks, treating the exact expiry as inactive. */
    public int getStacks(double currentTime) {
        return isWindowActive(currentTime) ? stacks : 0;
    }

    /** Returns the current half-open stack-window expiry. */
    public double getExpiresAt() {
        return expiresAt;
    }

    /** Applies permanent ATK SPD and live generic damage stacks. */
    @Override
    public void applyPassive(StatsContainer stats, double currentTime) {
        stats.add(StatType.ATK_SPD, ATTACK_SPEED_BONUS);
        int activeStacks = getStacks(currentTime);
        if (activeStacks > 0) {
            stats.add(StatType.DMG_BONUS_ALL,
                    damagePerStack * activeStacks);
        }
    }

    /** Binds the equipped owner and its resolved Normal-hit observer. */
    @Override
    public void initializeForSimulator(
            Character equippedOwner,
            CombatSimulator activeSimulator) {
        if (equippedOwner == null || activeSimulator == null) {
            throw new IllegalArgumentException(
                    "Gest owner and simulator are required");
        }
        if (simulator != null) {
            if (owner != equippedOwner || simulator != activeSimulator) {
                throw new IllegalStateException(
                        "Gest of the Mighty Wolf is already bound elsewhere");
            }
            return;
        }
        if (equippedOwner.getWeapon() != this) {
            throw new IllegalArgumentException(
                    "Owner must have Gest of the Mighty Wolf equipped");
        }
        if (!activeSimulator.getPartyMembers().contains(equippedOwner)) {
            throw new IllegalArgumentException(
                    "Owner must belong to the target simulator party");
        }
        owner = equippedOwner;
        simulator = activeSimulator;
        activeSimulator.addDamageListener((actor, action, damage, time) -> {
            if (action != null
                    && action.getActionType() == ActionType.NORMAL
                    && isBoundOwner(actor, activeSimulator)) {
                addStacks(1, time);
            }
        });
    }

    /** Adds two stacks on accepted active-owner Charged or Skill input. */
    @Override
    public void onAction(
            Character user,
            CharacterActionRequest request,
            CombatSimulator activeSimulator) {
        if (!isBoundActiveOwner(user, activeSimulator)
                || request == null) {
            return;
        }
        CharacterActionKey key = request.getKey();
        if (key == CharacterActionKey.CHARGE
                || key == CharacterActionKey.SKILL) {
            addStacks(2, activeSimulator.getCurrentTime());
        }
    }

    /** Captures the shared stack count and exact expiration boundary. */
    @Override
    public State captureWeaponState() {
        return new GestState(this, stacks, expiresAt);
    }

    /** Restores state captured from this exact weapon instance. */
    @Override
    public void restoreWeaponState(State state) {
        if (!(state instanceof GestState)) {
            throw new IllegalArgumentException("Gest state type is invalid");
        }
        GestState restored = (GestState) state;
        if (restored.source != this) {
            throw new IllegalArgumentException(
                    "Gest state belongs to another instance");
        }
        stacks = restored.stacks;
        expiresAt = restored.expiresAt;
    }

    private void addStacks(int amount, double currentTime) {
        if (!isWindowActive(currentTime)) {
            stacks = 0;
        }
        stacks = Math.min(MAX_STACKS, stacks + amount);
        expiresAt = currentTime + WINDOW_DURATION;
    }

    private boolean isWindowActive(double currentTime) {
        return stacks > 0 && currentTime < expiresAt;
    }

    private boolean isBoundActiveOwner(
            Character actor,
            CombatSimulator activeSimulator) {
        return isBoundOwner(actor, activeSimulator)
                && simulator.getActiveCharacter() == owner;
    }

    private boolean isBoundOwner(
            Character actor,
            CombatSimulator activeSimulator) {
        return simulator != null
                && simulator == activeSimulator
                && actor == owner
                && owner.getWeapon() == this;
    }

    private static final class GestState implements State {
        private final GestOfTheMightyWolf source;
        private final int stacks;
        private final double expiresAt;

        private GestState(
                GestOfTheMightyWolf source,
                int stacks,
                double expiresAt) {
            this.source = source;
            this.stacks = stacks;
            this.expiresAt = expiresAt;
        }
    }
}

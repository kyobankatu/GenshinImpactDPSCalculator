package model.weapon;

import model.entity.ActionTriggeredWeaponEffect;
import model.entity.Character;
import model.entity.DamageTriggeredWeaponEffect;
import model.entity.SimulatorInitializedWeaponEffect;
import model.entity.SnapshotAwareWeaponEffect;
import model.entity.SwitchAwareWeaponEffect;
import model.entity.Weapon;
import model.stats.StatsContainer;
import model.type.ActionType;
import model.type.StatType;
import model.type.WeaponType;
import simulation.CombatSimulator;
import simulation.action.AttackAction;
import simulation.action.CharacterActionKey;
import simulation.action.CharacterActionRequest;

/** Tulaytullah's Remembrance with passive-time and Normal-hit stacks. */
public final class TulaytullahsRemembrance extends Weapon
        implements ActionTriggeredWeaponEffect,
        DamageTriggeredWeaponEffect,
        SimulatorInitializedWeaponEffect,
        SwitchAwareWeaponEffect,
        SnapshotAwareWeaponEffect {
    private static final double DURATION = 14.0;
    private static final double PASSIVE_STACK_INTERVAL = 1.0;
    private static final double HIT_STACK_COOLDOWN = 0.3;
    private static final int MAX_STACKS = 10;

    private final int refinement;
    private final double normalSpeedBonus;
    private final double normalDamageBonusPerStack;
    private Character owner;
    private CombatSimulator simulator;
    private double activeFrom = Double.NEGATIVE_INFINITY;
    private double activeUntil = Double.NEGATIVE_INFINITY;
    private double nextHitStackAt = Double.NEGATIVE_INFINITY;
    private int hitStacks;

    /** Constructs Tulaytullah's Remembrance at refinement rank five. */
    public TulaytullahsRemembrance() {
        this(5);
    }

    /**
     * Constructs Tulaytullah's Remembrance at a selected refinement.
     *
     * @param refinement refinement rank in the inclusive range 1-5
     */
    public TulaytullahsRemembrance(int refinement) {
        super("Tulaytullah's Remembrance", new StatsContainer());
        if (refinement < 1 || refinement > 5) {
            throw new IllegalArgumentException(
                    "Tulaytullah's Remembrance refinement must be between 1 and 5");
        }
        this.refinement = refinement;
        this.normalSpeedBonus = 0.075 + 0.025 * refinement;
        this.normalDamageBonusPerStack = 0.036 + 0.012 * refinement;
        weaponType = WeaponType.CATALYST;
        getStats().set(StatType.BASE_ATK, 674.0);
        getStats().set(StatType.CRIT_DMG, 0.441);
        getStats().set(StatType.NORMAL_ATTACK_SPD, normalSpeedBonus);
    }

    /** Returns this weapon's refinement rank. */
    public int getRefinement() {
        return refinement;
    }

    /** Returns the unconditional Normal Attack speed bonus. */
    public double getNormalSpeedBonus() {
        return normalSpeedBonus;
    }

    /** Returns the Normal damage bonus represented by one stack. */
    public double getNormalDamageBonusPerStack() {
        return normalDamageBonusPerStack;
    }

    /** Returns current passive-time plus hit stacks, capped at ten. */
    public int getStackCount(double currentTime) {
        if (currentTime >= activeUntil) {
            return 0;
        }
        int passiveStacks = (int) Math.floor(
                Math.max(0.0, currentTime - activeFrom) / PASSIVE_STACK_INTERVAL);
        return Math.min(MAX_STACKS, passiveStacks + hitStacks);
    }

    /** Binds this mutable passive to exactly one owner and simulator. */
    @Override
    public void initializeForSimulator(Character equippedOwner, CombatSimulator sim) {
        if (equippedOwner == null || sim == null) {
            throw new IllegalArgumentException("Weapon owner and simulator are required");
        }
        if (simulator != null) {
            if (owner != equippedOwner || simulator != sim) {
                throw new IllegalStateException(
                        "Tulaytullah is already bound to another simulator");
            }
            return;
        }
        if (equippedOwner.getWeapon() != this) {
            throw new IllegalArgumentException(
                    "Weapon owner must have this Tulaytullah equipped");
        }
        owner = equippedOwner;
        simulator = sim;
    }

    /** Resets and opens the fourteen-second window on an on-field Skill use. */
    @Override
    public void onAction(
            Character user,
            CharacterActionRequest request,
            CombatSimulator sim) {
        if (user == owner
                && sim == simulator
                && sim.getActiveCharacter() == owner
                && request != null
                && request.getKey() == CharacterActionKey.SKILL) {
            activeFrom = sim.getCurrentTime();
            activeUntil = activeFrom + DURATION;
            nextHitStackAt = activeFrom;
            hitStacks = 0;
        }
    }

    /** Adds two stacks after an eligible Normal hit, once per 0.3 seconds. */
    @Override
    public void onDamage(
            Character user,
            AttackAction action,
            double currentTime,
            CombatSimulator sim) {
        if (user != owner
                || sim != simulator
                || sim.getActiveCharacter() != owner
                || action == null
                || !action.isHitEffectTrigger()
                || action.getDamagePercent() <= 0.0
                || action.getActionType() != ActionType.NORMAL
                || currentTime >= activeUntil
                || currentTime < nextHitStackAt) {
            return;
        }
        int passiveStacks = getStackCount(currentTime) - hitStacks;
        hitStacks = Math.min(MAX_STACKS - passiveStacks, hitStacks + 2);
        nextHitStackAt = currentTime + HIT_STACK_COOLDOWN;
    }

    /** Cancels the active window and every stack when the owner leaves the field. */
    @Override
    public void onSwitchOut(Character user, CombatSimulator sim) {
        if (user == owner && sim == simulator) {
            clearWindow();
        }
    }

    /** Applies the live Normal damage tier during the active window. */
    @Override
    public void applyPassive(StatsContainer stats, double currentTime) {
        int stackCount = getStackCount(currentTime);
        if (stackCount > 0) {
            stats.add(
                    StatType.NORMAL_ATTACK_DMG_BONUS,
                    normalDamageBonusPerStack * stackCount);
        }
    }

    /** Captures the complete active-window and hit-stack state. */
    @Override
    public State captureWeaponState() {
        return new TulaytullahState(
                this, activeFrom, activeUntil, nextHitStackAt, hitStacks);
    }

    /** Restores only state captured from this exact weapon instance. */
    @Override
    public void restoreWeaponState(State state) {
        if (!(state instanceof TulaytullahState)) {
            throw new IllegalArgumentException("Tulaytullah state type is invalid");
        }
        TulaytullahState weaponState = (TulaytullahState) state;
        if (weaponState.source != this) {
            throw new IllegalArgumentException(
                    "Tulaytullah state belongs to another weapon instance");
        }
        activeFrom = weaponState.activeFrom;
        activeUntil = weaponState.activeUntil;
        nextHitStackAt = weaponState.nextHitStackAt;
        hitStacks = weaponState.hitStacks;
    }

    private void clearWindow() {
        activeFrom = Double.NEGATIVE_INFINITY;
        activeUntil = Double.NEGATIVE_INFINITY;
        nextHitStackAt = Double.NEGATIVE_INFINITY;
        hitStacks = 0;
    }

    /** Immutable Tulaytullah state tied to one weapon instance. */
    private static final class TulaytullahState implements State {
        private final TulaytullahsRemembrance source;
        private final double activeFrom;
        private final double activeUntil;
        private final double nextHitStackAt;
        private final int hitStacks;

        private TulaytullahState(
                TulaytullahsRemembrance source,
                double activeFrom,
                double activeUntil,
                double nextHitStackAt,
                int hitStacks) {
            this.source = source;
            this.activeFrom = activeFrom;
            this.activeUntil = activeUntil;
            this.nextHitStackAt = nextHitStackAt;
            this.hitStacks = hitStacks;
        }
    }
}

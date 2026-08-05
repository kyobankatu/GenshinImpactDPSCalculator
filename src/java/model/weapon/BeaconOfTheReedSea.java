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
 * Beacon of the Reed Sea's supported Skill-hit and no-shield branches.
 *
 * <p>An owner Skill hit opens or refreshes an eight-second ATK window, even
 * while the owner is off field. The current runtime has no general player
 * shield state, so its supported state is unshielded and the corresponding
 * Max HP bonus remains active. Incoming player damage and its separate ATK
 * window are excluded rather than synthesized from outgoing events.</p>
 */
public final class BeaconOfTheReedSea extends Weapon
        implements DamageTriggeredWeaponEffect,
        SimulatorInitializedWeaponEffect,
        SnapshotAwareWeaponEffect {
    private static final double SKILL_WINDOW_DURATION = 8.0;

    private final int refinement;
    private final double skillHitAttackBonus;
    private final double noShieldHpBonus;
    private Character owner;
    private CombatSimulator simulator;
    private double skillWindowFrom = Double.POSITIVE_INFINITY;
    private double skillWindowUntil = Double.NEGATIVE_INFINITY;

    /** Constructs Beacon of the Reed Sea at refinement rank five. */
    public BeaconOfTheReedSea() {
        this(5);
    }

    /**
     * Constructs Beacon of the Reed Sea at a selected refinement rank.
     *
     * @param refinement refinement rank in the inclusive range 1-5
     */
    public BeaconOfTheReedSea(int refinement) {
        super("Beacon of the Reed Sea", new StatsContainer());
        if (refinement < 1 || refinement > 5) {
            throw new IllegalArgumentException(
                    "Beacon of the Reed Sea refinement must be between 1 and 5");
        }
        this.refinement = refinement;
        skillHitAttackBonus = 0.15 + 0.05 * refinement;
        noShieldHpBonus = 0.24 + 0.08 * refinement;
        weaponType = WeaponType.CLAYMORE;
        getStats().set(StatType.BASE_ATK, 608.0);
        getStats().set(StatType.CRIT_RATE, 0.331);
    }

    /** Returns the selected refinement rank. */
    public int getRefinement() {
        return refinement;
    }

    /** Returns the ATK bonus supplied by the supported Skill-hit window. */
    public double getSkillHitAttackBonus() {
        return skillHitAttackBonus;
    }

    /** Returns the Max HP bonus for the runtime's supported unshielded state. */
    public double getNoShieldHpBonus() {
        return noShieldHpBonus;
    }

    /** Returns whether the half-open Skill-hit window is active. */
    public boolean isSkillWindowActive(double currentTime) {
        return currentTime >= skillWindowFrom
                && currentTime < skillWindowUntil;
    }

    /** Returns the current Skill-hit window expiration time. */
    public double getSkillWindowUntil() {
        return skillWindowUntil;
    }

    /**
     * Applies the unshielded Max HP branch and any active Skill-hit ATK window.
     *
     * <p>The HP branch is unconditional until a general player shield state is
     * available to distinguish protected and unprotected owners.</p>
     */
    @Override
    public void applyPassive(StatsContainer stats, double currentTime) {
        stats.add(StatType.HP_PERCENT, noShieldHpBonus);
        if (isSkillWindowActive(currentTime)) {
            stats.add(StatType.ATK_PERCENT, skillHitAttackBonus);
        }
    }

    /** Binds this mutable passive to exactly one equipped owner and simulator. */
    @Override
    public void initializeForSimulator(
            Character equippedOwner,
            CombatSimulator activeSimulator) {
        if (equippedOwner == null || activeSimulator == null) {
            throw new IllegalArgumentException(
                    "Beacon owner and simulator are required");
        }
        if (simulator != null) {
            if (owner != equippedOwner || simulator != activeSimulator) {
                throw new IllegalStateException(
                        "Beacon of the Reed Sea is already bound elsewhere");
            }
            return;
        }
        if (equippedOwner.getWeapon() != this) {
            throw new IllegalArgumentException(
                    "Owner must have this Beacon of the Reed Sea equipped");
        }
        if (!activeSimulator.getPartyMembers().contains(equippedOwner)) {
            throw new IllegalArgumentException(
                    "Owner must belong to the target simulator party");
        }
        owner = equippedOwner;
        simulator = activeSimulator;
    }

    /** Opens or refreshes the eight-second ATK window after an owner Skill hit. */
    @Override
    public void onDamage(
            Character user,
            AttackAction action,
            double currentTime,
            CombatSimulator activeSimulator) {
        if (!isBoundOwner(user, activeSimulator)
                || action == null
                || !action.isHitEffectTrigger()
                || action.getDamagePercent() <= 0.0
                || (action.getActionType() != ActionType.SKILL
                        && !action.isCountsAsSkillDmg())) {
            return;
        }
        skillWindowFrom = currentTime;
        skillWindowUntil = currentTime + SKILL_WINDOW_DURATION;
    }

    /** Captures the complete Skill-hit window state. */
    @Override
    public State captureWeaponState() {
        return new BeaconState(
                this, skillWindowFrom, skillWindowUntil);
    }

    /** Restores only state captured from this exact weapon instance. */
    @Override
    public void restoreWeaponState(State state) {
        if (!(state instanceof BeaconState)) {
            throw new IllegalArgumentException(
                    "Beacon of the Reed Sea state type is invalid");
        }
        BeaconState beaconState = (BeaconState) state;
        if (beaconState.source != this) {
            throw new IllegalArgumentException(
                    "Beacon state belongs to another weapon instance");
        }
        skillWindowFrom = beaconState.skillWindowFrom;
        skillWindowUntil = beaconState.skillWindowUntil;
    }

    private boolean isBoundOwner(
            Character user,
            CombatSimulator activeSimulator) {
        return simulator != null
                && simulator == activeSimulator
                && owner == user
                && owner.getWeapon() == this;
    }

    /** Immutable Skill-window state tied to one Beacon instance. */
    private static final class BeaconState implements State {
        private final BeaconOfTheReedSea source;
        private final double skillWindowFrom;
        private final double skillWindowUntil;

        private BeaconState(
                BeaconOfTheReedSea source,
                double skillWindowFrom,
                double skillWindowUntil) {
            this.source = source;
            this.skillWindowFrom = skillWindowFrom;
            this.skillWindowUntil = skillWindowUntil;
        }
    }
}

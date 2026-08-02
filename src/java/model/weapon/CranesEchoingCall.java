package model.weapon;

import java.util.Collections;
import java.util.List;

import mechanics.buff.Buff;
import model.entity.Character;
import model.entity.SimulatorInitializedWeaponEffect;
import model.entity.SnapshotAwareWeaponEffect;
import model.entity.Weapon;
import model.entity.WeaponTeamBuffProvider;
import model.stats.StatsContainer;
import model.type.ActionType;
import model.type.StatType;
import model.type.WeaponType;
import simulation.CombatSimulator;
import simulation.DamageListener;
import simulation.action.AttackAction;

/**
 * Crane's Echoing Call catalyst with party Plunging damage and owner Energy recovery.
 *
 * <p>A positive party Plunging hit restores flat Energy to the owner once per
 * {@code 0.7} seconds, including while the owner is off-field. Owner-attributed
 * Plunging hits also open a party-wide Plunging DMG Bonus window for 20 seconds.
 * Both effects use resolved direct-hit notifications, so non-hit actions and
 * zero-damage events cannot trigger the passive.</p>
 */
public final class CranesEchoingCall extends Weapon
        implements DamageListener,
        SimulatorInitializedWeaponEffect,
        SnapshotAwareWeaponEffect,
        WeaponTeamBuffProvider {
    private static final double BUFF_DURATION = 20.0;
    private static final double ENERGY_COOLDOWN = 0.7;

    private final int refinement;
    private final double plungingDamageBonus;
    private final double energyRecovery;
    private final Buff teamBuff;
    private Character owner;
    private CombatSimulator simulator;
    private double buffActiveUntil = Double.NEGATIVE_INFINITY;
    private double nextEnergyRecoveryAt = Double.NEGATIVE_INFINITY;

    /** Constructs Crane's Echoing Call at refinement rank five. */
    public CranesEchoingCall() {
        this(5);
    }

    /**
     * Constructs Crane's Echoing Call at a selected refinement.
     *
     * @param refinement refinement rank in the inclusive range 1-5
     */
    public CranesEchoingCall(int refinement) {
        super("Crane's Echoing Call", new StatsContainer());
        validateRefinement(refinement);
        this.refinement = refinement;
        this.plungingDamageBonus = 0.15 + 0.13 * refinement;
        this.energyRecovery = 2.25 + 0.25 * refinement;
        this.weaponType = WeaponType.CATALYST;
        getStats().set(StatType.BASE_ATK, 741.0);
        getStats().set(StatType.ATK_PERCENT, 0.165);
        this.teamBuff = new Buff("Crane's Echoing Call (Party)") {
            @Override
            protected void applyStats(StatsContainer stats, double currentTime) {
                if (currentTime < buffActiveUntil) {
                    stats.add(StatType.PLUNGING_ATTACK_DMG_BONUS,
                            plungingDamageBonus);
                }
            }
        };
    }

    /** Returns this weapon's refinement rank. */
    public int getRefinement() {
        return refinement;
    }

    /** Returns the party Plunging DMG Bonus granted by an owner hit. */
    public double getPlungingDamageBonus() {
        return plungingDamageBonus;
    }

    /** Returns flat Energy restored by each eligible party hit. */
    public double getEnergyRecovery() {
        return energyRecovery;
    }

    /** Binds this stateful passive to exactly one equipped owner and simulator. */
    @Override
    public void initializeForSimulator(Character equippedOwner, CombatSimulator sim) {
        if (equippedOwner == null || sim == null) {
            throw new IllegalArgumentException("Weapon owner and simulator are required");
        }
        if (simulator != null) {
            if (owner != equippedOwner || simulator != sim) {
                throw new IllegalStateException(
                        "Crane's Echoing Call is already bound to another simulator");
            }
            return;
        }
        validateBinding(equippedOwner, sim);
        owner = equippedOwner;
        simulator = sim;
        teamBuff.sourcedBy(owner.getCharacterId());
        sim.addDamageListener(this);
    }

    /**
     * Handles one resolved hit and independently updates the buff and Energy effects.
     *
     * @param actor party member attributed with the hit
     * @param action resolved direct attack
     * @param damage positive final damage required to trigger
     * @param time hit time in simulation seconds
     */
    @Override
    public void onDamage(
            Character actor,
            AttackAction action,
            double damage,
            double time) {
        if (!isEligiblePartyPlunge(actor, action, damage)) {
            return;
        }
        if (actor == owner) {
            buffActiveUntil = time + BUFF_DURATION;
        }
        if (time >= nextEnergyRecoveryAt) {
            owner.receiveFlatEnergy(energyRecovery);
            nextEnergyRecoveryAt = time + ENERGY_COOLDOWN;
        }
    }

    /** Returns the live party buff only for this weapon's bound owner. */
    @Override
    public List<Buff> getTeamBuffs(Character equippedOwner) {
        if (simulator == null || equippedOwner != owner
                || equippedOwner.getWeapon() != this) {
            return Collections.emptyList();
        }
        return Collections.singletonList(teamBuff);
    }

    /** Captures the buff boundary and Energy internal cooldown. */
    @Override
    public State captureWeaponState() {
        return new CraneState(this, buffActiveUntil, nextEnergyRecoveryAt);
    }

    /** Restores state captured from this exact weapon instance. */
    @Override
    public void restoreWeaponState(State state) {
        if (!(state instanceof CraneState)) {
            throw new IllegalArgumentException(
                    "Crane's Echoing Call state type is invalid");
        }
        CraneState craneState = (CraneState) state;
        if (craneState.source != this) {
            throw new IllegalArgumentException(
                    "Crane's Echoing Call state belongs to another weapon instance");
        }
        buffActiveUntil = craneState.buffActiveUntil;
        nextEnergyRecoveryAt = craneState.nextEnergyRecoveryAt;
    }

    private boolean isEligiblePartyPlunge(
            Character actor,
            AttackAction action,
            double damage) {
        return simulator != null
                && owner != null
                && owner.getWeapon() == this
                && actor != null
                && simulator.getPartyMembers().contains(actor)
                && action != null
                && action.isHitEffectTrigger()
                && action.getDamagePercent() > 0.0
                && action.getActionType() == ActionType.PLUNGE
                && damage > 0.0;
    }

    private void validateBinding(Character equippedOwner, CombatSimulator sim) {
        if (equippedOwner.getWeapon() != this) {
            throw new IllegalArgumentException(
                    "Weapon owner must have this Crane's Echoing Call equipped");
        }
        if (!sim.getPartyMembers().contains(equippedOwner)) {
            throw new IllegalArgumentException(
                    "Weapon owner must belong to the target simulator party");
        }
    }

    private static void validateRefinement(int refinement) {
        if (refinement < 1 || refinement > 5) {
            throw new IllegalArgumentException(
                    "Crane's Echoing Call refinement must be between 1 and 5");
        }
    }

    /** Immutable passive state tied to one Crane's Echoing Call instance. */
    private static final class CraneState implements State {
        private final CranesEchoingCall source;
        private final double buffActiveUntil;
        private final double nextEnergyRecoveryAt;

        private CraneState(
                CranesEchoingCall source,
                double buffActiveUntil,
                double nextEnergyRecoveryAt) {
            this.source = source;
            this.buffActiveUntil = buffActiveUntil;
            this.nextEnergyRecoveryAt = nextEnergyRecoveryAt;
        }
    }
}

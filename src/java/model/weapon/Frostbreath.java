package model.weapon;

import mechanics.reaction.ReactionResult;
import model.entity.Character;
import model.entity.ElementalReactionTriggeredWeaponEffect;
import model.entity.SimulatorInitializedWeaponEffect;
import model.entity.SnapshotAwareWeaponEffect;
import model.entity.Weapon;
import model.stats.StatsContainer;
import model.type.Element;
import model.type.StatType;
import model.type.WeaponType;
import simulation.CombatSimulator;

/** Frostbreath polearm with a typed Cryo/Hydro reaction trigger. */
public final class Frostbreath extends Weapon
        implements ElementalReactionTriggeredWeaponEffect,
        SimulatorInitializedWeaponEffect,
        SnapshotAwareWeaponEffect {
    private static final double ATTACK_WINDOW_DURATION = 15.0;
    private static final double TRIGGER_COOLDOWN = 16.0;

    private final int refinement;
    private final double attackBonus;
    private final double partyEnergyRecovery;
    private Character owner;
    private CombatSimulator simulator;
    private double attackWindowUntil = Double.NEGATIVE_INFINITY;
    private double nextTriggerAt = Double.NEGATIVE_INFINITY;

    /** Constructs Frostbreath at refinement rank five. */
    public Frostbreath() {
        this(5);
    }

    /** Constructs Frostbreath at the selected refinement rank. */
    public Frostbreath(int refinement) {
        super("Frostbreath", new StatsContainer());
        validateRefinement(refinement);
        this.refinement = refinement;
        attackBonus = 0.15 + 0.05 * refinement;
        partyEnergyRecovery = 4.5 + 1.5 * refinement;
        weaponType = WeaponType.POLEARM;
        getStats().set(StatType.BASE_ATK, 510.0);
        getStats().set(StatType.ENERGY_RECHARGE, 0.459);
    }

    /** Returns the selected refinement rank. */
    public int getRefinement() {
        return refinement;
    }

    /** Binds this weapon and registers its actual-reaction callback. */
    @Override
    public void initializeForSimulator(Character equippedOwner, CombatSimulator sim) {
        validateBinding(equippedOwner, sim);
        if (simulator != null) {
            if (owner != equippedOwner || simulator != sim) {
                throw new IllegalStateException(
                        "Frostbreath is already bound to another simulator");
            }
            return;
        }
        owner = equippedOwner;
        simulator = sim;
        sim.addElementalReactionTriggeredWeaponEffect(this);
    }

    /** Activates after an owner-attributed reaction involving Cryo or Hydro. */
    @Override
    public void onElementalReaction(
            ReactionResult result,
            Character source,
            double time,
            CombatSimulator activeSimulator) {
        if (activeSimulator != simulator
                || source != owner
                || simulator.getActiveCharacter() != owner
                || owner.getWeapon() != this
                || result == null
                || time < nextTriggerAt
                || !involvesCryoOrHydro(result)) {
            return;
        }
        attackWindowUntil = time + ATTACK_WINDOW_DURATION;
        nextTriggerAt = time + TRIGGER_COOLDOWN;
        for (Character member : simulator.getPartyMembers()) {
            if (member != owner) {
                member.receiveFlatEnergy(partyEnergyRecovery);
            }
        }
    }

    /** Applies the owner ATK window. */
    @Override
    public void applyPassive(StatsContainer stats, double currentTime) {
        if (currentTime < attackWindowUntil) {
            stats.add(StatType.ATK_PERCENT, attackBonus);
        }
    }

    /** Returns whether the typed result has Cryo or Hydro as a reagent. */
    public static boolean involvesCryoOrHydro(ReactionResult result) {
        if (result == null) {
            return false;
        }
        if (result.getRelatedElement() == Element.CRYO
                || result.getRelatedElement() == Element.HYDRO) {
            return true;
        }
        switch (result.getKind()) {
            case VAPORIZE:
            case MELT:
            case SUPERCONDUCT:
            case STELLAR_CONDUCT:
            case FROZEN:
            case ELECTRO_CHARGED:
            case LUNAR_CHARGED:
            case BLOOM:
            case LUNAR_BLOOM:
            case STELLAR_SWIRL:
                return true;
            default:
                return false;
        }
    }

    /** Captures the ATK expiry and independent trigger cooldown. */
    @Override
    public State captureWeaponState() {
        return new FrostbreathState(this, attackWindowUntil, nextTriggerAt);
    }

    /** Restores state captured from this exact weapon instance. */
    @Override
    public void restoreWeaponState(State state) {
        if (!(state instanceof FrostbreathState)) {
            throw new IllegalArgumentException("Frostbreath state type is invalid");
        }
        FrostbreathState restored = (FrostbreathState) state;
        if (restored.source != this) {
            throw new IllegalArgumentException(
                    "Frostbreath state belongs to another weapon instance");
        }
        attackWindowUntil = restored.attackWindowUntil;
        nextTriggerAt = restored.nextTriggerAt;
    }

    private void validateBinding(Character equippedOwner, CombatSimulator sim) {
        if (equippedOwner == null || sim == null) {
            throw new IllegalArgumentException(
                    "Frostbreath owner and simulator are required");
        }
        if (equippedOwner.getWeapon() != this
                || !sim.getPartyMembers().contains(equippedOwner)) {
            throw new IllegalArgumentException(
                    "Frostbreath owner must equip this weapon in the target party");
        }
    }

    private static void validateRefinement(int refinement) {
        if (refinement < 1 || refinement > 5) {
            throw new IllegalArgumentException(
                    "Frostbreath refinement must be between 1 and 5");
        }
    }

    /** Immutable Frostbreath runtime state. */
    private static final class FrostbreathState implements State {
        private final Frostbreath source;
        private final double attackWindowUntil;
        private final double nextTriggerAt;

        private FrostbreathState(
                Frostbreath source,
                double attackWindowUntil,
                double nextTriggerAt) {
            this.source = source;
            this.attackWindowUntil = attackWindowUntil;
            this.nextTriggerAt = nextTriggerAt;
        }
    }
}

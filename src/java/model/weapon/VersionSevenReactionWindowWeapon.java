package model.weapon;

import mechanics.reaction.ReactionResult;
import model.entity.Character;
import model.entity.ElementalReactionTriggeredWeaponEffect;
import model.entity.SimulatorInitializedWeaponEffect;
import model.entity.SnapshotAwareWeaponEffect;
import model.entity.Weapon;
import model.stats.StatsContainer;
import model.type.StatType;
import model.type.WeaponType;
import simulation.CombatSimulator;

/**
 * Shared owner-attributed reaction windows for the Version 7.0 weapon family.
 *
 * <p>Every actual elemental reaction refreshes the ordinary twelve-second
 * window. Stellar-Conduct and Stellar-Swirl additionally refresh the Stellar
 * window. Optional flat Energy recovery uses an independent cooldown. All
 * windows are half-open and remain triggerable while the owner is off field.</p>
 */
abstract class VersionSevenReactionWindowWeapon extends Weapon implements
        ElementalReactionTriggeredWeaponEffect,
        SimulatorInitializedWeaponEffect,
        SnapshotAwareWeaponEffect {
    private static final double WINDOW_DURATION = 12.0;

    private final int refinement;
    private final StatType ordinaryBonusStat;
    private final double ordinaryBonus;
    private final StatType[] stellarBonusStats;
    private final double stellarBonus;
    private final double energyRecovery;
    private final double energyCooldown;

    private Character owner;
    private CombatSimulator simulator;
    private double ordinaryWindowFrom = Double.POSITIVE_INFINITY;
    private double ordinaryWindowUntil = Double.NEGATIVE_INFINITY;
    private double stellarWindowFrom = Double.POSITIVE_INFINITY;
    private double stellarWindowUntil = Double.NEGATIVE_INFINITY;
    private double nextEnergyRecoveryAt = Double.NEGATIVE_INFINITY;

    /**
     * Constructs one Version 7.0 reaction-window weapon.
     *
     * @param name weapon display name
     * @param weaponType weapon category
     * @param baseAtk Lv. 90 base ATK
     * @param secondaryStat Lv. 90 secondary stat
     * @param secondaryValue Lv. 90 secondary-stat value
     * @param refinement refinement rank in the inclusive range 1-5
     * @param ordinaryBonusStat stat granted after any elemental reaction, or null
     * @param ordinaryBonus ordinary-window value
     * @param stellarBonus Stellar-window value
     * @param energyRecovery flat Energy restored after any elemental reaction
     * @param energyCooldown independent Energy recovery cooldown in seconds
     * @param stellarBonusStats stats granted after a Stellar reaction
     */
    protected VersionSevenReactionWindowWeapon(
            String name,
            WeaponType weaponType,
            double baseAtk,
            StatType secondaryStat,
            double secondaryValue,
            int refinement,
            StatType ordinaryBonusStat,
            double ordinaryBonus,
            double stellarBonus,
            double energyRecovery,
            double energyCooldown,
            StatType... stellarBonusStats) {
        super(name, new StatsContainer());
        if (refinement < 1 || refinement > 5) {
            throw new IllegalArgumentException(
                    name + " refinement must be between 1 and 5");
        }
        if (ordinaryBonus < 0.0
                || stellarBonus < 0.0
                || energyRecovery < 0.0
                || energyCooldown < 0.0
                || (energyRecovery > 0.0 && energyCooldown == 0.0)) {
            throw new IllegalArgumentException(
                    name + " reaction-window definition is invalid");
        }
        this.refinement = refinement;
        this.ordinaryBonusStat = ordinaryBonusStat;
        this.ordinaryBonus = ordinaryBonus;
        this.stellarBonusStats = stellarBonusStats.clone();
        this.stellarBonus = stellarBonus;
        this.energyRecovery = energyRecovery;
        this.energyCooldown = energyCooldown;
        this.weaponType = weaponType;
        getStats().set(StatType.BASE_ATK, baseAtk);
        getStats().set(secondaryStat, secondaryValue);
    }

    /** Returns the selected refinement rank. */
    public final int getRefinement() {
        return refinement;
    }

    /** Returns the value granted by the ordinary reaction window. */
    public final double getOrdinaryBonus() {
        return ordinaryBonus;
    }

    /** Returns the value granted by the Stellar reaction window. */
    public final double getStellarBonus() {
        return stellarBonus;
    }

    /** Returns the flat Energy restored by an eligible reaction. */
    public final double getEnergyRecovery() {
        return energyRecovery;
    }

    /** Returns whether the half-open ordinary reaction window is active. */
    public final boolean isOrdinaryWindowActive(double currentTime) {
        return currentTime >= ordinaryWindowFrom
                && currentTime < ordinaryWindowUntil;
    }

    /** Returns whether the half-open Stellar reaction window is active. */
    public final boolean isStellarWindowActive(double currentTime) {
        return currentTime >= stellarWindowFrom
                && currentTime < stellarWindowUntil;
    }

    /** Returns the ordinary-window expiration timestamp. */
    public final double getOrdinaryWindowUntil() {
        return ordinaryWindowUntil;
    }

    /** Returns the Stellar-window expiration timestamp. */
    public final double getStellarWindowUntil() {
        return stellarWindowUntil;
    }

    /** Returns the next timestamp at which Energy recovery is permitted. */
    public final double getNextEnergyRecoveryAt() {
        return nextEnergyRecoveryAt;
    }

    /** Applies each independently active owner-only stat window. */
    @Override
    public final void applyPassive(StatsContainer stats, double currentTime) {
        if (ordinaryBonusStat != null
                && isOrdinaryWindowActive(currentTime)) {
            stats.add(ordinaryBonusStat, ordinaryBonus);
        }
        if (isStellarWindowActive(currentTime)) {
            for (StatType stat : stellarBonusStats) {
                stats.add(stat, stellarBonus);
            }
        }
    }

    /** Binds the equipped owner and registers the actual-reaction callback. */
    @Override
    public final void initializeForSimulator(
            Character equippedOwner,
            CombatSimulator activeSimulator) {
        if (equippedOwner == null || activeSimulator == null) {
            throw new IllegalArgumentException(
                    getName() + " owner and simulator are required");
        }
        if (simulator != null) {
            if (owner != equippedOwner || simulator != activeSimulator) {
                throw new IllegalStateException(
                        getName() + " is already bound to another simulator");
            }
            return;
        }
        if (equippedOwner.getWeapon() != this) {
            throw new IllegalArgumentException(
                    "Owner must have " + getName() + " equipped");
        }
        if (!activeSimulator.getPartyMembers().contains(equippedOwner)) {
            throw new IllegalArgumentException(
                    "Owner must belong to the target simulator party");
        }
        owner = equippedOwner;
        simulator = activeSimulator;
        activeSimulator.addElementalReactionTriggeredWeaponEffect(this);
    }

    /**
     * Refreshes the owner windows and optional Energy ICD from an actual reaction.
     */
    @Override
    public final void onElementalReaction(
            ReactionResult result,
            Character source,
            double time,
            CombatSimulator activeSimulator) {
        if (!isBoundOwner(source, activeSimulator)
                || result == null
                || result.getKind() == ReactionResult.Kind.NONE) {
            return;
        }
        ordinaryWindowFrom = time;
        ordinaryWindowUntil = time + WINDOW_DURATION;
        if (result.isStellarReaction()) {
            stellarWindowFrom = time;
            stellarWindowUntil = time + WINDOW_DURATION;
        }
        if (energyRecovery > 0.0 && time >= nextEnergyRecoveryAt) {
            owner.receiveFlatEnergy(energyRecovery);
            nextEnergyRecoveryAt = time + energyCooldown;
        }
    }

    /** Captures both windows and the independent Energy cooldown. */
    @Override
    public final State captureWeaponState() {
        return new ReactionWindowState(
                this,
                ordinaryWindowFrom,
                ordinaryWindowUntil,
                stellarWindowFrom,
                stellarWindowUntil,
                nextEnergyRecoveryAt);
    }

    /** Restores state captured from this exact weapon instance. */
    @Override
    public final void restoreWeaponState(State state) {
        if (!(state instanceof ReactionWindowState)) {
            throw new IllegalArgumentException(
                    getName() + " state type is invalid");
        }
        ReactionWindowState restored = (ReactionWindowState) state;
        if (restored.source != this) {
            throw new IllegalArgumentException(
                    getName() + " state belongs to another weapon instance");
        }
        ordinaryWindowFrom = restored.ordinaryWindowFrom;
        ordinaryWindowUntil = restored.ordinaryWindowUntil;
        stellarWindowFrom = restored.stellarWindowFrom;
        stellarWindowUntil = restored.stellarWindowUntil;
        nextEnergyRecoveryAt = restored.nextEnergyRecoveryAt;
    }

    private boolean isBoundOwner(
            Character source,
            CombatSimulator activeSimulator) {
        return simulator != null
                && activeSimulator == simulator
                && source == owner
                && owner.getWeapon() == this
                && simulator.getPartyMembers().contains(owner);
    }

    /** Immutable reaction-window state tied to one exact weapon instance. */
    private static final class ReactionWindowState implements State {
        private final VersionSevenReactionWindowWeapon source;
        private final double ordinaryWindowFrom;
        private final double ordinaryWindowUntil;
        private final double stellarWindowFrom;
        private final double stellarWindowUntil;
        private final double nextEnergyRecoveryAt;

        private ReactionWindowState(
                VersionSevenReactionWindowWeapon source,
                double ordinaryWindowFrom,
                double ordinaryWindowUntil,
                double stellarWindowFrom,
                double stellarWindowUntil,
                double nextEnergyRecoveryAt) {
            this.source = source;
            this.ordinaryWindowFrom = ordinaryWindowFrom;
            this.ordinaryWindowUntil = ordinaryWindowUntil;
            this.stellarWindowFrom = stellarWindowFrom;
            this.stellarWindowUntil = stellarWindowUntil;
            this.nextEnergyRecoveryAt = nextEnergyRecoveryAt;
        }
    }
}

package model.weapon;

import mechanics.reaction.ReactionResult;
import model.entity.ActionTriggeredWeaponEffect;
import model.entity.Character;
import model.entity.ElementalReactionTriggeredWeaponEffect;
import model.entity.SimulatorInitializedWeaponEffect;
import model.entity.SnapshotAwareWeaponEffect;
import model.entity.Weapon;
import model.stats.StatsContainer;
import model.type.StatType;
import model.type.WeaponType;
import simulation.CombatSimulator;
import simulation.action.CharacterActionKey;
import simulation.action.CharacterActionRequest;

/**
 * Bloodsoaked Ruins with independent Lunar-Charged, CRIT, and Energy gates.
 *
 * <p>Lv. 90 metadata and R1-R5 values follow pinned KQM TCL
 * {@code 80ba6241} and gcsim {@code ef41805d}. Accepted on-field owner Bursts
 * open the Lunar-Charged window. Actual on-field owner Lunar-Charged reactions
 * refresh generic CRIT DMG and independently gate flat Energy recovery.</p>
 *
 * <p>Hitlag extension is unavailable, so all durations use exact half-open
 * simulation-time windows.</p>
 */
public final class BloodsoakedRuins extends Weapon implements
        ActionTriggeredWeaponEffect,
        ElementalReactionTriggeredWeaponEffect,
        SimulatorInitializedWeaponEffect,
        SnapshotAwareWeaponEffect {
    private static final double BURST_WINDOW_DURATION = 3.5;
    private static final double CRIT_WINDOW_DURATION = 6.0;
    private static final double ENERGY_COOLDOWN = 14.0;

    private final int refinement;
    private final double lunarChargedBonus;
    private final double critDamageBonus;
    private final double energyRecovery;
    private Character owner;
    private CombatSimulator simulator;
    private double burstWindowFrom = Double.POSITIVE_INFINITY;
    private double burstWindowUntil = Double.NEGATIVE_INFINITY;
    private double critWindowFrom = Double.POSITIVE_INFINITY;
    private double critWindowUntil = Double.NEGATIVE_INFINITY;
    private double nextEnergyRecoveryAt = Double.NEGATIVE_INFINITY;

    /** Constructs Bloodsoaked Ruins at refinement rank five. */
    public BloodsoakedRuins() {
        this(5);
    }

    /** Constructs Bloodsoaked Ruins at the selected refinement. */
    public BloodsoakedRuins(int refinement) {
        super("Bloodsoaked Ruins", new StatsContainer());
        if (refinement < 1 || refinement > 5) {
            throw new IllegalArgumentException(
                    "Bloodsoaked Ruins refinement must be between 1 and 5");
        }
        this.refinement = refinement;
        lunarChargedBonus = 0.24 + 0.12 * refinement;
        critDamageBonus = 0.21 + 0.07 * refinement;
        energyRecovery = 11.0 + refinement;
        weaponType = WeaponType.POLEARM;
        getStats().set(StatType.BASE_ATK, 674.0);
        getStats().set(StatType.CRIT_RATE, 0.221);
    }

    /** Returns this weapon's refinement rank. */
    public int getRefinement() {
        return refinement;
    }

    /** Returns the Burst-window Lunar-Charged DMG bonus. */
    public double getLunarChargedBonus() {
        return lunarChargedBonus;
    }

    /** Returns the reaction-window generic CRIT DMG bonus. */
    public double getCritDamageBonus() {
        return critDamageBonus;
    }

    /** Returns the flat Energy restored outside the independent cooldown. */
    public double getEnergyRecovery() {
        return energyRecovery;
    }

    /** Returns whether the Burst window is active at an exact timestamp. */
    public boolean isBurstWindowActive(double currentTime) {
        return currentTime >= burstWindowFrom
                && currentTime < burstWindowUntil;
    }

    /** Returns whether the CRIT window is active at an exact timestamp. */
    public boolean isCritWindowActive(double currentTime) {
        return currentTime >= critWindowFrom
                && currentTime < critWindowUntil;
    }

    /** Applies each independently active owner-only window. */
    @Override
    public void applyPassive(StatsContainer stats, double currentTime) {
        if (isBurstWindowActive(currentTime)) {
            stats.add(
                    StatType.LUNAR_CHARGED_DMG_BONUS,
                    lunarChargedBonus);
        }
        if (isCritWindowActive(currentTime)) {
            stats.add(StatType.CRIT_DMG, critDamageBonus);
        }
    }

    /** Binds one equipped owner and actual-reaction callback. */
    @Override
    public void initializeForSimulator(
            Character equippedOwner,
            CombatSimulator activeSimulator) {
        if (equippedOwner == null || activeSimulator == null) {
            throw new IllegalArgumentException(
                    "Bloodsoaked Ruins owner and simulator are required");
        }
        if (simulator != null) {
            if (owner != equippedOwner || simulator != activeSimulator) {
                throw new IllegalStateException(
                        "Bloodsoaked Ruins is already bound elsewhere");
            }
            return;
        }
        validateBinding(equippedOwner, activeSimulator);
        owner = equippedOwner;
        simulator = activeSimulator;
        activeSimulator.addElementalReactionTriggeredWeaponEffect(this);
    }

    /** Opens the owner-only Lunar-Charged window after an accepted Burst. */
    @Override
    public void onAction(
            Character user,
            CharacterActionRequest request,
            CombatSimulator activeSimulator) {
        if (!isBoundOnFieldOwner(user, activeSimulator)
                || request == null
                || request.getKey() != CharacterActionKey.BURST) {
            return;
        }
        double currentTime = activeSimulator.getCurrentTime();
        burstWindowFrom = currentTime;
        burstWindowUntil = currentTime + BURST_WINDOW_DURATION;
    }

    /** Refreshes CRIT and conditionally restores Energy on actual Lunar-Charged. */
    @Override
    public void onElementalReaction(
            ReactionResult result,
            Character source,
            double time,
            CombatSimulator activeSimulator) {
        if (!isBoundOnFieldOwner(source, activeSimulator)
                || result == null
                || result.getKind()
                        != ReactionResult.Kind.LUNAR_CHARGED) {
            return;
        }
        critWindowFrom = time;
        critWindowUntil = time + CRIT_WINDOW_DURATION;
        if (time < nextEnergyRecoveryAt) {
            return;
        }
        nextEnergyRecoveryAt = time + ENERGY_COOLDOWN;
        owner.receiveFlatEnergy(energyRecovery);
    }

    /** Captures both windows and the independent Energy cooldown. */
    @Override
    public State captureWeaponState() {
        return new BloodsoakedRuinsState(
                this,
                burstWindowFrom,
                burstWindowUntil,
                critWindowFrom,
                critWindowUntil,
                nextEnergyRecoveryAt);
    }

    /** Restores state captured from this exact weapon instance. */
    @Override
    public void restoreWeaponState(State state) {
        if (!(state instanceof BloodsoakedRuinsState)) {
            throw new IllegalArgumentException(
                    "Bloodsoaked Ruins state type is invalid");
        }
        BloodsoakedRuinsState restored =
                (BloodsoakedRuinsState) state;
        if (restored.source != this) {
            throw new IllegalArgumentException(
                    "Bloodsoaked Ruins state belongs to another instance");
        }
        burstWindowFrom = restored.burstWindowFrom;
        burstWindowUntil = restored.burstWindowUntil;
        critWindowFrom = restored.critWindowFrom;
        critWindowUntil = restored.critWindowUntil;
        nextEnergyRecoveryAt = restored.nextEnergyRecoveryAt;
    }

    private boolean isBoundOnFieldOwner(
            Character actor,
            CombatSimulator activeSimulator) {
        return simulator != null
                && activeSimulator == simulator
                && actor == owner
                && owner.getWeapon() == this
                && simulator.getActiveCharacter() == owner;
    }

    private void validateBinding(
            Character equippedOwner,
            CombatSimulator activeSimulator) {
        if (equippedOwner.getWeapon() != this) {
            throw new IllegalArgumentException(
                    "Owner must have this Bloodsoaked Ruins equipped");
        }
        if (!activeSimulator.getPartyMembers().contains(equippedOwner)) {
            throw new IllegalArgumentException(
                    "Owner must belong to the target simulator party");
        }
    }

    /** Immutable five-boundary state tied to one weapon instance. */
    private static final class BloodsoakedRuinsState implements State {
        private final BloodsoakedRuins source;
        private final double burstWindowFrom;
        private final double burstWindowUntil;
        private final double critWindowFrom;
        private final double critWindowUntil;
        private final double nextEnergyRecoveryAt;

        private BloodsoakedRuinsState(
                BloodsoakedRuins source,
                double burstWindowFrom,
                double burstWindowUntil,
                double critWindowFrom,
                double critWindowUntil,
                double nextEnergyRecoveryAt) {
            this.source = source;
            this.burstWindowFrom = burstWindowFrom;
            this.burstWindowUntil = burstWindowUntil;
            this.critWindowFrom = critWindowFrom;
            this.critWindowUntil = critWindowUntil;
            this.nextEnergyRecoveryAt = nextEnergyRecoveryAt;
        }
    }
}

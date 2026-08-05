package model.weapon;

import model.entity.ActionTriggeredWeaponEffect;
import model.entity.Character;
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
 * Silvershower Heartstrings with its representable Skill Remedy stack.
 *
 * <p>Lv. 90 metadata and R1-R5 values follow pinned gcsim
 * {@code ef41805d}. Active-owner Elemental Skill use grants one independently
 * refreshable Remedy stack for 25 seconds. The stack increases only the
 * equipped owner's HP and persists while that owner is off field.</p>
 *
 * <p>Bond-of-Life growth and healing Remedy stacks remain inactive because
 * this runtime has no typed player HP-debt or healing callback for weapons.
 * Their durations, the three-stack HP bonus, and the Burst-only CRIT Rate
 * value remain exposed as source-backed data. The Burst value is not applied
 * through generic {@link StatType#CRIT_RATE}, which would affect other action
 * types incorrectly.</p>
 */
public final class SilvershowerHeartstrings extends Weapon implements
        ActionTriggeredWeaponEffect,
        SimulatorInitializedWeaponEffect,
        SnapshotAwareWeaponEffect {
    private static final double SKILL_REMEDY_DURATION = 25.0;
    private static final double BOND_OF_LIFE_REMEDY_DURATION = 25.0;
    private static final double HEALING_REMEDY_DURATION = 20.0;

    private final int refinement;
    private final double remedyHpBonusPerStack;
    private final double threeStackAdditionalHpBonus;
    private final double burstCriticalRateBonus;

    private Character owner;
    private CombatSimulator simulator;
    private double skillStackFrom = Double.POSITIVE_INFINITY;
    private double skillStackUntil = Double.NEGATIVE_INFINITY;

    /** Constructs Silvershower Heartstrings at refinement rank five. */
    public SilvershowerHeartstrings() {
        this(5);
    }

    /**
     * Constructs Silvershower Heartstrings at the selected refinement rank.
     *
     * @param refinement refinement rank in the inclusive range 1-5
     */
    public SilvershowerHeartstrings(int refinement) {
        super("Silvershower Heartstrings", new StatsContainer());
        validateRefinement(refinement);
        this.refinement = refinement;
        remedyHpBonusPerStack = 0.09 + 0.03 * refinement;
        threeStackAdditionalHpBonus = 0.03 + 0.01 * refinement;
        burstCriticalRateBonus = 0.21 + 0.07 * refinement;
        weaponType = WeaponType.BOW;
        getStats().set(StatType.BASE_ATK, 542.0);
        getStats().set(StatType.HP_PERCENT, 0.662);
    }

    /** Returns the selected refinement rank. */
    public int getRefinement() {
        return refinement;
    }

    /** Returns the HP bonus supplied by each Remedy stack. */
    public double getRemedyHpBonusPerStack() {
        return remedyHpBonusPerStack;
    }

    /** Returns the represented Skill Remedy duration in seconds. */
    public double getSkillRemedyDuration() {
        return SKILL_REMEDY_DURATION;
    }

    /** Returns the inactive Bond-of-Life Remedy duration in seconds. */
    public double getBondOfLifeRemedyDuration() {
        return BOND_OF_LIFE_REMEDY_DURATION;
    }

    /** Returns the inactive healing Remedy duration in seconds. */
    public double getHealingRemedyDuration() {
        return HEALING_REMEDY_DURATION;
    }

    /** Returns the inactive additional HP bonus available at three stacks. */
    public double getThreeStackAdditionalHpBonus() {
        return threeStackAdditionalHpBonus;
    }

    /** Returns the inactive Burst-only CRIT Rate value. */
    public double getBurstCriticalRateBonus() {
        return burstCriticalRateBonus;
    }

    /** Returns whether the half-open Skill Remedy window is active. */
    public boolean isSkillRemedyActive(double currentTime) {
        return currentTime >= skillStackFrom && currentTime < skillStackUntil;
    }

    /** Returns the current half-open Skill Remedy expiration timestamp. */
    public double getSkillStackUntil() {
        return skillStackUntil;
    }

    /** Returns whether the unsupported Bond-of-Life Remedy route is active. */
    public boolean isBondOfLifeRemedyActive() {
        return false;
    }

    /** Returns whether the unsupported healing Remedy route is active. */
    public boolean isHealingRemedyActive() {
        return false;
    }

    /** Applies the one live Skill Remedy stack to the equipped owner. */
    @Override
    public void applyPassive(StatsContainer stats, double currentTime) {
        if (isSkillRemedyActive(currentTime)) {
            stats.add(StatType.HP_PERCENT, remedyHpBonusPerStack);
        }
    }

    /** Binds this mutable weapon to one equipped owner and simulator. */
    @Override
    public void initializeForSimulator(
            Character equippedOwner,
            CombatSimulator activeSimulator) {
        if (equippedOwner == null || activeSimulator == null) {
            throw new IllegalArgumentException(
                    "Silvershower Heartstrings owner and simulator are required");
        }
        if (simulator != null) {
            if (owner != equippedOwner || simulator != activeSimulator) {
                throw new IllegalStateException(
                        "Silvershower Heartstrings is already bound elsewhere");
            }
            return;
        }
        if (equippedOwner.getWeapon() != this) {
            throw new IllegalArgumentException(
                    "Owner must have Silvershower Heartstrings equipped");
        }
        if (!activeSimulator.getPartyMembers().contains(equippedOwner)) {
            throw new IllegalArgumentException(
                    "Owner must belong to the target simulator party");
        }
        owner = equippedOwner;
        simulator = activeSimulator;
    }

    /** Opens or refreshes the Skill Remedy stack on active-owner Skill use. */
    @Override
    public void onAction(
            Character user,
            CharacterActionRequest request,
            CombatSimulator activeSimulator) {
        if (!isBoundActiveOwner(user, activeSimulator)
                || request == null
                || request.getKey() != CharacterActionKey.SKILL) {
            return;
        }
        skillStackFrom = activeSimulator.getCurrentTime();
        skillStackUntil = skillStackFrom + SKILL_REMEDY_DURATION;
    }

    /** Captures the exact Skill Remedy window boundaries. */
    @Override
    public State captureWeaponState() {
        return new HeartstringsState(
                this, skillStackFrom, skillStackUntil);
    }

    /** Restores state captured from this exact weapon instance. */
    @Override
    public void restoreWeaponState(State state) {
        if (!(state instanceof HeartstringsState)) {
            throw new IllegalArgumentException(
                    "Silvershower Heartstrings state type is invalid");
        }
        HeartstringsState restored = (HeartstringsState) state;
        if (restored.source != this) {
            throw new IllegalArgumentException(
                    "Silvershower Heartstrings state belongs to another instance");
        }
        skillStackFrom = restored.skillStackFrom;
        skillStackUntil = restored.skillStackUntil;
    }

    private boolean isBoundActiveOwner(
            Character user,
            CombatSimulator activeSimulator) {
        return simulator != null
                && simulator == activeSimulator
                && owner == user
                && owner.getWeapon() == this
                && simulator.getActiveCharacter() == owner;
    }

    private static void validateRefinement(int refinement) {
        if (refinement < 1 || refinement > 5) {
            throw new IllegalArgumentException(
                    "Silvershower Heartstrings refinement must be between 1 and 5");
        }
    }

    /** Immutable Skill Remedy state tied to one weapon instance. */
    private static final class HeartstringsState implements State {
        private final SilvershowerHeartstrings source;
        private final double skillStackFrom;
        private final double skillStackUntil;

        private HeartstringsState(
                SilvershowerHeartstrings source,
                double skillStackFrom,
                double skillStackUntil) {
            this.source = source;
            this.skillStackFrom = skillStackFrom;
            this.skillStackUntil = skillStackUntil;
        }
    }
}

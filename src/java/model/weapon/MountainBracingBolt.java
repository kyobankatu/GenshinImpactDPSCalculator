package model.weapon;

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
 * Mountain-Bracing Bolt with its owner-only Skill damage window.
 *
 * <p>Lv. 90 metadata and R1-R5 values follow pinned KQM TCL
 * {@code 80ba6241} and gcsim {@code ef41805d}. Another active party member's
 * accepted Skill input refreshes one additional eight-second Skill DMG copy.
 * Fixed-party members are treated as nearby. Climbing stamina reduction and
 * hitlag extension are outside the combat simulator.</p>
 */
public final class MountainBracingBolt extends Weapon implements
        SimulatorInitializedWeaponEffect,
        SnapshotAwareWeaponEffect {
    private static final double TRIGGERED_DURATION = 8.0;
    private static final double CLIMBING_STAMINA_REDUCTION = 0.15;

    private final int refinement;
    private final double skillDamageBonus;
    private Character owner;
    private CombatSimulator simulator;
    private double triggeredFrom = Double.POSITIVE_INFINITY;
    private double triggeredUntil = Double.NEGATIVE_INFINITY;

    /** Constructs Mountain-Bracing Bolt at refinement rank five. */
    public MountainBracingBolt() {
        this(5);
    }

    /** Constructs Mountain-Bracing Bolt at the selected refinement. */
    public MountainBracingBolt(int refinement) {
        super("Mountain-Bracing Bolt", new StatsContainer());
        if (refinement < 1 || refinement > 5) {
            throw new IllegalArgumentException(
                    "Mountain-Bracing Bolt refinement must be between 1 and 5");
        }
        this.refinement = refinement;
        skillDamageBonus = 0.09 + 0.03 * refinement;
        weaponType = WeaponType.POLEARM;
        getStats().set(StatType.BASE_ATK, 565.0);
        getStats().set(StatType.ENERGY_RECHARGE, 0.306);
    }

    /** Returns this weapon's refinement rank. */
    public int getRefinement() {
        return refinement;
    }

    /** Returns each permanent or triggered Skill DMG copy. */
    public double getSkillDamageBonus() {
        return skillDamageBonus;
    }

    /** Returns the canonical non-combat climbing stamina reduction. */
    public double getClimbingStaminaReduction() {
        return CLIMBING_STAMINA_REDUCTION;
    }

    /** Returns whether the triggered copy is active at an exact timestamp. */
    public boolean isTriggeredBonusActive(double currentTime) {
        return currentTime >= triggeredFrom
                && currentTime < triggeredUntil;
    }

    /** Applies the permanent copy and at most one active triggered copy. */
    @Override
    public void applyPassive(StatsContainer stats, double currentTime) {
        stats.add(StatType.SKILL_DMG_BONUS, skillDamageBonus);
        if (isTriggeredBonusActive(currentTime)) {
            stats.add(StatType.SKILL_DMG_BONUS, skillDamageBonus);
        }
    }

    /** Binds one owner/simulator and observes accepted typed Skill inputs. */
    @Override
    public void initializeForSimulator(
            Character equippedOwner,
            CombatSimulator activeSimulator) {
        if (equippedOwner == null || activeSimulator == null) {
            throw new IllegalArgumentException(
                    "Mountain-Bracing Bolt owner and simulator are required");
        }
        if (simulator != null) {
            if (owner != equippedOwner || simulator != activeSimulator) {
                throw new IllegalStateException(
                        "Mountain-Bracing Bolt is already bound elsewhere");
            }
            return;
        }
        validateBinding(equippedOwner, activeSimulator);
        owner = equippedOwner;
        simulator = activeSimulator;
        activeSimulator.addActionRequestListener(
                (actor, request, time) ->
                        onAcceptedAction(actor, request, time));
    }

    /** Captures both half-open window boundaries. */
    @Override
    public State captureWeaponState() {
        return new MountainBracingBoltState(
                this, triggeredFrom, triggeredUntil);
    }

    /** Restores state captured from this exact weapon instance. */
    @Override
    public void restoreWeaponState(State state) {
        if (!(state instanceof MountainBracingBoltState)) {
            throw new IllegalArgumentException(
                    "Mountain-Bracing Bolt state type is invalid");
        }
        MountainBracingBoltState restored =
                (MountainBracingBoltState) state;
        if (restored.source != this) {
            throw new IllegalArgumentException(
                    "Mountain-Bracing Bolt state belongs to another instance");
        }
        triggeredFrom = restored.triggeredFrom;
        triggeredUntil = restored.triggeredUntil;
    }

    private void onAcceptedAction(
            Character actor,
            CharacterActionRequest request,
            double time) {
        if (request == null
                || request.getKey() != CharacterActionKey.SKILL
                || actor == null
                || actor == owner
                || actor != simulator.getActiveCharacter()
                || !simulator.getPartyMembers().contains(actor)) {
            return;
        }
        triggeredFrom = time;
        triggeredUntil = time + TRIGGERED_DURATION;
    }

    private void validateBinding(
            Character equippedOwner,
            CombatSimulator activeSimulator) {
        if (equippedOwner.getWeapon() != this) {
            throw new IllegalArgumentException(
                    "Owner must have this Mountain-Bracing Bolt equipped");
        }
        if (!activeSimulator.getPartyMembers().contains(equippedOwner)) {
            throw new IllegalArgumentException(
                    "Owner must belong to the target simulator party");
        }
    }

    /** Immutable mutable-window state tied to one weapon instance. */
    private static final class MountainBracingBoltState implements State {
        private final MountainBracingBolt source;
        private final double triggeredFrom;
        private final double triggeredUntil;

        private MountainBracingBoltState(
                MountainBracingBolt source,
                double triggeredFrom,
                double triggeredUntil) {
            this.source = source;
            this.triggeredFrom = triggeredFrom;
            this.triggeredUntil = triggeredUntil;
        }
    }
}

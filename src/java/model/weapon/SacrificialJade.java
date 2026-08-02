package model.weapon;

import java.util.Objects;

import model.entity.Character;
import model.entity.SimulatorInitializedWeaponEffect;
import model.entity.SwitchAwareWeaponEffect;
import model.entity.Weapon;
import model.stats.StatsContainer;
import model.type.StatType;
import model.type.WeaponType;
import simulation.CombatSimulator;

/**
 * Sacrificial Jade catalyst with live off-field and on-field timing.
 *
 * <p>
 * Jade Circulation activates after five continuous seconds off-field. A
 * qualified effect remains for the first ten seconds after entering the field,
 * while leaving the field always starts a fresh qualification interval.
 */
public class SacrificialJade extends Weapon
        implements SimulatorInitializedWeaponEffect, SwitchAwareWeaponEffect {
    private static final double OFF_FIELD_REQUIREMENT = 5.0;
    private static final double ON_FIELD_DURATION = 10.0;

    private final int refinement;
    private final double hpBonus;
    private final double elementalMasteryBonus;
    private Character owner;
    private CombatSimulator simulator;
    private boolean observedOnField;
    private boolean retainedOnFieldEffect;
    private double stateStartedAt;

    /** Constructs Sacrificial Jade at refinement rank five. */
    public SacrificialJade() {
        this(5);
    }

    /**
     * Constructs Sacrificial Jade at a selected refinement.
     *
     * @param refinement refinement rank in the inclusive range 1-5
     * @throws IllegalArgumentException if {@code refinement} is outside 1-5
     */
    public SacrificialJade(int refinement) {
        super("Sacrificial Jade", new StatsContainer());
        if (refinement < 1 || refinement > 5) {
            throw new IllegalArgumentException(
                    "Weapon refinement must be between 1 and 5");
        }
        this.refinement = refinement;
        this.hpBonus = 0.24 + 0.08 * refinement;
        this.elementalMasteryBonus = 30.0 + 10.0 * refinement;
        this.weaponType = WeaponType.CATALYST;
        getStats().set(StatType.BASE_ATK, 454.0);
        getStats().set(StatType.CRIT_RATE, 0.368);
    }

    /**
     * Returns this weapon's refinement rank.
     *
     * @return refinement in the inclusive range 1-5
     */
    public int getRefinement() {
        return refinement;
    }

    /**
     * Binds the owner and records its actual initial field state.
     *
     * @param equippedOwner character carrying this weapon instance
     * @param sim simulator containing the equipped owner
     */
    @Override
    public void initializeForSimulator(Character equippedOwner, CombatSimulator sim) {
        Objects.requireNonNull(equippedOwner, "Weapon owner is required");
        Objects.requireNonNull(sim, "Weapon simulator is required");
        if (simulator != null) {
            if (owner != equippedOwner || simulator != sim) {
                throw new IllegalStateException(
                        "Sacrificial Jade is already bound to another simulator");
            }
            return;
        }
        owner = equippedOwner;
        simulator = sim;
        observedOnField = sim.getActiveCharacter() == equippedOwner;
        retainedOnFieldEffect = false;
        stateStartedAt = sim.getCurrentTime();
    }

    /**
     * Starts a fresh off-field qualification interval on a standard switch.
     *
     * @param user outgoing weapon owner
     * @param sim active simulator
     */
    @Override
    public void onSwitchOut(Character user, CombatSimulator sim) {
        if (simulator == null || user != owner || sim != simulator) {
            return;
        }
        observedOnField = false;
        retainedOnFieldEffect = false;
        stateStartedAt = sim.getCurrentTime();
    }

    /**
     * Retains a qualified off-field effect for the next ten on-field seconds.
     *
     * @param user incoming weapon owner
     * @param sim active simulator
     */
    @Override
    public void onSwitchIn(Character user, CombatSimulator sim) {
        if (simulator == null || user != owner || sim != simulator) {
            return;
        }
        double currentTime = sim.getCurrentTime();
        retainedOnFieldEffect = !observedOnField
                && currentTime - stateStartedAt >= OFF_FIELD_REQUIREMENT;
        observedOnField = true;
        stateStartedAt = currentTime;
    }

    /**
     * Applies Jade Circulation from the live field state and exact boundaries.
     *
     * <p>
     * Direct active-character setters intentionally emit no callbacks. A stat
     * query therefore reconciles any observed field-state change at the query
     * time before evaluating the passive.
     *
     * @param stats stats container receiving the passive
     * @param currentTime simulation time in seconds
     */
    @Override
    public void applyPassive(StatsContainer stats, double currentTime) {
        if (simulator == null) {
            return;
        }
        synchronizeDirectFieldChange(currentTime);
        boolean active;
        if (observedOnField) {
            active = retainedOnFieldEffect
                    && currentTime - stateStartedAt < ON_FIELD_DURATION;
        } else {
            active = currentTime - stateStartedAt >= OFF_FIELD_REQUIREMENT;
        }
        if (active) {
            stats.add(StatType.HP_PERCENT, hpBonus);
            stats.add(StatType.ELEMENTAL_MASTERY, elementalMasteryBonus);
        }
    }

    /** Reconciles callback-free direct field changes at the current stat query. */
    private void synchronizeDirectFieldChange(double currentTime) {
        boolean currentlyOnField = simulator.getActiveCharacter() == owner;
        if (currentlyOnField == observedOnField) {
            return;
        }
        if (currentlyOnField) {
            retainedOnFieldEffect = currentTime - stateStartedAt
                    >= OFF_FIELD_REQUIREMENT;
        } else {
            retainedOnFieldEffect = false;
        }
        observedOnField = currentlyOnField;
        stateStartedAt = currentTime;
    }
}

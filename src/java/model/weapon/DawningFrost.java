package model.weapon;

import model.entity.Character;
import model.entity.DamageTriggeredWeaponEffect;
import model.entity.SimulatorInitializedWeaponEffect;
import model.entity.Weapon;
import model.stats.StatsContainer;
import model.type.ActionType;
import model.type.StatType;
import model.type.WeaponType;
import simulation.CombatSimulator;
import simulation.action.AttackAction;

/** Dawning Frost catalyst with independent Charged and Skill hit EM windows. */
public class DawningFrost extends Weapon
        implements DamageTriggeredWeaponEffect, SimulatorInitializedWeaponEffect {
    private static final double WINDOW_DURATION = 10.0;

    private final int refinement;
    private final double chargedElementalMastery;
    private final double skillElementalMastery;
    private Character owner;
    private CombatSimulator simulator;
    private double chargedActiveUntil = Double.NEGATIVE_INFINITY;
    private double skillActiveUntil = Double.NEGATIVE_INFINITY;

    /** Constructs Dawning Frost at refinement rank five. */
    public DawningFrost() {
        this(5);
    }

    /**
     * Constructs Dawning Frost at a selected refinement.
     *
     * @param refinement refinement rank in the inclusive range 1-5
     */
    public DawningFrost(int refinement) {
        super("Dawning Frost", new StatsContainer());
        if (refinement < 1 || refinement > 5) {
            throw new IllegalArgumentException("Weapon refinement must be between 1 and 5");
        }
        this.refinement = refinement;
        this.chargedElementalMastery = 54.0 + 18.0 * refinement;
        this.skillElementalMastery = 36.0 + 12.0 * refinement;
        this.weaponType = WeaponType.CATALYST;
        getStats().set(StatType.BASE_ATK, 510.0);
        getStats().set(StatType.CRIT_DMG, 0.551);
    }

    /** Returns this weapon's refinement rank. */
    public int getRefinement() {
        return refinement;
    }

    /** Binds the active owner eligible to open the two hit windows. */
    @Override
    public void initializeForSimulator(Character equippedOwner, CombatSimulator sim) {
        if (simulator != null) {
            if (owner != equippedOwner || simulator != sim) {
                throw new IllegalStateException(
                        "Dawning Frost is already bound to another simulator");
            }
            return;
        }
        owner = equippedOwner;
        simulator = sim;
    }

    /** Refreshes the matching window after positive active-owner damage. */
    @Override
    public void onDamage(
            Character user,
            AttackAction action,
            double currentTime,
            CombatSimulator sim) {
        if (sim != simulator
                || user != owner
                || sim.getActiveCharacter() != owner
                || action.getDamagePercent() <= 0.0) {
            return;
        }
        if (action.getActionType() == ActionType.CHARGE) {
            chargedActiveUntil = currentTime + WINDOW_DURATION;
        }
        if (action.getActionType() == ActionType.SKILL
                || action.isCountsAsSkillDmg()) {
            skillActiveUntil = currentTime + WINDOW_DURATION;
        }
    }

    /** Applies both independently active Elemental Mastery bonuses. */
    @Override
    public void applyPassive(StatsContainer stats, double currentTime) {
        if (currentTime < chargedActiveUntil) {
            stats.add(StatType.ELEMENTAL_MASTERY, chargedElementalMastery);
        }
        if (currentTime < skillActiveUntil) {
            stats.add(StatType.ELEMENTAL_MASTERY, skillElementalMastery);
        }
    }
}

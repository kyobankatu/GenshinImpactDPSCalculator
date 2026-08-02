package model.weapon;

import mechanics.reaction.ReactionResult;
import model.entity.Character;
import model.entity.SimulatorInitializedWeaponEffect;
import model.entity.Weapon;
import model.stats.StatsContainer;
import model.type.StatType;
import model.type.WeaponType;
import simulation.CombatSimulator;

/**
 * Flame-Forged Insight claymore with reaction-driven Energy and EM recovery.
 */
public class FlameForgedInsight extends Weapon
        implements SimulatorInitializedWeaponEffect, CombatSimulator.ReactionListener {
    private static final double DURATION = 15.0;
    private static final double TRIGGER_COOLDOWN = 15.0;

    private final int refinement;
    private final double energyRecovery;
    private final double elementalMasteryBonus;
    private Character owner;
    private CombatSimulator simulator;
    private double activeUntil = Double.NEGATIVE_INFINITY;
    private double nextTriggerTime = Double.NEGATIVE_INFINITY;

    /** Constructs Flame-Forged Insight at refinement rank five. */
    public FlameForgedInsight() {
        this(5);
    }

    /**
     * Constructs Flame-Forged Insight at a selected refinement.
     *
     * @param refinement refinement rank in the inclusive range 1-5
     */
    public FlameForgedInsight(int refinement) {
        super("Flame-Forged Insight", new StatsContainer());
        if (refinement < 1 || refinement > 5) {
            throw new IllegalArgumentException("Weapon refinement must be between 1 and 5");
        }
        this.refinement = refinement;
        this.energyRecovery = 9.0 + 3.0 * refinement;
        this.elementalMasteryBonus = 45.0 + 15.0 * refinement;
        this.weaponType = WeaponType.CLAYMORE;
        getStats().set(StatType.BASE_ATK, 510.0);
        getStats().set(StatType.ELEMENTAL_MASTERY, 165.0);
    }

    /** Returns this weapon's refinement rank. */
    public int getRefinement() {
        return refinement;
    }

    /** Binds the owner and registers one attributed reaction listener. */
    @Override
    public void initializeForSimulator(Character equippedOwner, CombatSimulator sim) {
        if (simulator != null) {
            if (owner != equippedOwner || simulator != sim) {
                throw new IllegalStateException(
                        "Flame-Forged Insight is already bound to another simulator");
            }
            return;
        }
        owner = equippedOwner;
        simulator = sim;
        sim.addReactionListener(this);
    }

    /** Applies the reaction-window EM in addition to the static substat. */
    @Override
    public void applyPassive(StatsContainer stats, double currentTime) {
        if (currentTime < activeUntil) {
            stats.add(StatType.ELEMENTAL_MASTERY, elementalMasteryBonus);
        }
    }

    /** Restores flat Energy and refreshes EM at exact 15-second trigger CT. */
    @Override
    public void onReaction(
            ReactionResult result,
            Character source,
            double time,
            CombatSimulator sim) {
        if (sim != simulator
                || source != owner
                || time < nextTriggerTime
                || !isEligibleReaction(result)) {
            return;
        }
        owner.receiveFlatEnergy(energyRecovery);
        activeUntil = time + DURATION;
        nextTriggerTime = time + TRIGGER_COOLDOWN;
    }

    private static boolean isEligibleReaction(ReactionResult result) {
        switch (result.getKind()) {
            case ELECTRO_CHARGED:
            case LUNAR_CHARGED:
            case BLOOM:
            case LUNAR_BLOOM:
            case CRYSTALLIZE:
            case LUNAR_CRYSTALLIZE:
                return true;
            default:
                return false;
        }
    }
}

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
 * Shared off-field owner-reaction window whose bonus follows the live Moonsign.
 */
public abstract class MoonsignReactionWindowWeapon extends Weapon
        implements SimulatorInitializedWeaponEffect, CombatSimulator.ReactionListener {
    private final int refinement;
    private final StatType bonusStat;
    private final double baseBonus;
    private final double duration;

    private Character owner;
    private CombatSimulator simulator;
    private double activeUntil = Double.NEGATIVE_INFINITY;

    /**
     * Constructs one Moonsign-sensitive reaction-window weapon.
     *
     * @param name weapon display name
     * @param weaponType weapon category
     * @param baseAtk Lv. 90 base ATK
     * @param secondaryStat secondary stat type
     * @param secondaryValue Lv. 90 secondary stat value
     * @param refinement refinement rank in the inclusive range 1-5
     * @param bonusStat stat granted during the reaction window
     * @param baseBonus bonus outside Ascendant Gleam
     * @param duration active window duration in seconds
     */
    protected MoonsignReactionWindowWeapon(
            String name,
            WeaponType weaponType,
            double baseAtk,
            StatType secondaryStat,
            double secondaryValue,
            int refinement,
            StatType bonusStat,
            double baseBonus,
            double duration) {
        super(name, new StatsContainer());
        if (refinement < 1 || refinement > 5) {
            throw new IllegalArgumentException(
                    "Moonsign reaction-window refinement must be between 1 and 5");
        }
        if (duration <= 0.0) {
            throw new IllegalArgumentException(
                    "Moonsign reaction-window duration must be positive");
        }
        this.refinement = refinement;
        this.bonusStat = bonusStat;
        this.baseBonus = baseBonus;
        this.duration = duration;
        this.weaponType = weaponType;
        getStats().set(StatType.BASE_ATK, baseAtk);
        getStats().set(secondaryStat, secondaryValue);
    }

    /**
     * Returns this weapon's refinement rank.
     *
     * @return refinement in the inclusive range 1-5
     */
    public final int getRefinement() {
        return refinement;
    }

    /** Binds the equipped owner and registers one reaction listener. */
    @Override
    public final void initializeForSimulator(Character equippedOwner, CombatSimulator sim) {
        if (simulator != null) {
            if (owner != equippedOwner || simulator != sim) {
                throw new IllegalStateException(
                        "Moonsign reaction-window weapon is already bound to another simulator");
            }
            return;
        }
        owner = equippedOwner;
        simulator = sim;
        sim.addReactionListener(this);
    }

    /** Applies the active bonus, doubled under live Ascendant Gleam. */
    @Override
    public final void applyPassive(StatsContainer stats, double currentTime) {
        if (simulator == null || currentTime >= activeUntil) {
            return;
        }
        double multiplier = simulator.getMoonsign()
                == CombatSimulator.Moonsign.ASCENDANT_GLEAM ? 2.0 : 1.0;
        stats.add(bonusStat, baseBonus * multiplier);
    }

    /** Refreshes the window after any non-NONE reaction attributed to the owner. */
    @Override
    public final void onReaction(
            ReactionResult result,
            Character source,
            double time,
            CombatSimulator sim) {
        if (sim == simulator
                && source == owner
                && result.getKind() != ReactionResult.Kind.NONE) {
            activeUntil = time + duration;
        }
    }
}

package model.weapon;

import mechanics.reaction.ReactionResult;
import model.entity.Character;
import model.entity.SimulatorInitializedWeaponEffect;
import model.entity.Weapon;
import model.stats.StatsContainer;
import model.type.Element;
import model.type.StatType;
import model.type.WeaponType;
import simulation.CombatSimulator;

/**
 * Earth Shaker claymore with a party Pyro-reaction Skill damage window.
 */
public class EarthShaker extends Weapon
        implements SimulatorInitializedWeaponEffect, CombatSimulator.ReactionListener {
    private static final double DURATION = 8.0;

    private final int refinement;
    private final double skillDamageBonus;
    private Character owner;
    private CombatSimulator simulator;
    private double activeUntil = Double.NEGATIVE_INFINITY;

    /** Constructs Earth Shaker at refinement rank five. */
    public EarthShaker() {
        this(5);
    }

    /**
     * Constructs Earth Shaker at a selected refinement.
     *
     * @param refinement refinement rank in the inclusive range 1-5
     */
    public EarthShaker(int refinement) {
        super("Earth Shaker", new StatsContainer());
        if (refinement < 1 || refinement > 5) {
            throw new IllegalArgumentException("Weapon refinement must be between 1 and 5");
        }
        this.refinement = refinement;
        this.skillDamageBonus = 0.12 + 0.04 * refinement;
        this.weaponType = WeaponType.CLAYMORE;
        getStats().set(StatType.BASE_ATK, 565.0);
        getStats().set(StatType.ATK_PERCENT, 0.276);
    }

    /** Returns this weapon's refinement rank. */
    public int getRefinement() {
        return refinement;
    }

    /** Binds the owner and registers one party reaction listener. */
    @Override
    public void initializeForSimulator(Character equippedOwner, CombatSimulator sim) {
        if (simulator != null) {
            if (owner != equippedOwner || simulator != sim) {
                throw new IllegalStateException(
                        "Earth Shaker is already bound to another simulator");
            }
            return;
        }
        owner = equippedOwner;
        simulator = sim;
        sim.addReactionListener(this);
    }

    /** Applies Skill damage while the latest qualifying party reaction is active. */
    @Override
    public void applyPassive(StatsContainer stats, double currentTime) {
        if (currentTime < activeUntil) {
            stats.add(StatType.SKILL_DMG_BONUS, skillDamageBonus);
        }
    }

    /** Refreshes after a party member causes a Pyro-related reaction. */
    @Override
    public void onReaction(
            ReactionResult result,
            Character source,
            double time,
            CombatSimulator sim) {
        if (sim == simulator
                && source != null
                && sim.getPartyMembers().contains(source)
                && isPyroRelated(result)) {
            activeUntil = time + DURATION;
        }
    }

    private static boolean isPyroRelated(ReactionResult result) {
        switch (result.getKind()) {
            case VAPORIZE:
            case MELT:
            case OVERLOAD:
            case OVERLOADED:
            case BURNING:
            case BURGEON:
                return true;
            case SWIRL:
            case CRYSTALLIZE:
                return result.getRelatedElement() == Element.PYRO;
            default:
                return false;
        }
    }
}

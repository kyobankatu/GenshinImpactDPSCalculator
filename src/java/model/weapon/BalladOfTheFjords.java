package model.weapon;

import java.util.EnumSet;

import model.entity.Character;
import model.entity.SimulatorInitializedWeaponEffect;
import model.entity.Weapon;
import model.stats.StatsContainer;
import model.type.Element;
import model.type.StatType;
import model.type.WeaponType;
import simulation.CombatSimulator;

/**
 * Ballad of the Fjords polearm with a live party-element diversity passive.
 */
public class BalladOfTheFjords extends Weapon
        implements SimulatorInitializedWeaponEffect {
    private final int refinement;
    private final double elementalMasteryBonus;
    private CombatSimulator simulator;

    /** Constructs Ballad of the Fjords at refinement rank five. */
    public BalladOfTheFjords() {
        this(5);
    }

    /**
     * Constructs Ballad of the Fjords at a selected refinement.
     *
     * @param refinement refinement rank in the inclusive range 1-5
     */
    public BalladOfTheFjords(int refinement) {
        super("Ballad of the Fjords", new StatsContainer());
        if (refinement < 1 || refinement > 5) {
            throw new IllegalArgumentException("Weapon refinement must be between 1 and 5");
        }
        this.refinement = refinement;
        this.elementalMasteryBonus = 90.0 + 30.0 * refinement;
        this.weaponType = WeaponType.POLEARM;
        getStats().set(StatType.BASE_ATK, 510.0);
        getStats().set(StatType.CRIT_RATE, 0.276);
    }

    /**
     * Returns this weapon's refinement rank.
     *
     * @return refinement in the inclusive range 1-5
     */
    public int getRefinement() {
        return refinement;
    }

    /** Binds the live party used when evaluating elemental diversity. */
    @Override
    public void initializeForSimulator(Character owner, CombatSimulator sim) {
        if (simulator != null && simulator != sim) {
            throw new IllegalStateException(
                    "Ballad of the Fjords is already bound to another simulator");
        }
        simulator = sim;
    }

    /** Applies EM when at least three distinct playable elements are present. */
    @Override
    public void applyPassive(StatsContainer stats, double currentTime) {
        if (simulator == null) {
            return;
        }
        EnumSet<Element> partyElements = EnumSet.noneOf(Element.class);
        for (Character member : simulator.getPartyMembers()) {
            if (member.getElement() != Element.PHYSICAL) {
                partyElements.add(member.getElement());
            }
        }
        if (partyElements.size() >= 3) {
            stats.add(StatType.ELEMENTAL_MASTERY, elementalMasteryBonus);
        }
    }
}

package model.weapon;

import model.entity.Character;
import model.entity.SimulatorInitializedWeaponEffect;
import model.entity.Weapon;
import model.stats.StatsContainer;
import model.type.CharacterRegion;
import model.type.Element;
import model.type.StatType;
import model.type.WeaponType;
import simulation.CombatSimulator;

/** Chain Breaker with its Natlan-or-element party-composition passive. */
public final class ChainBreaker extends Weapon
        implements SimulatorInitializedWeaponEffect {
    private static final int MAX_MEMBERS = 4;

    private final int refinement;
    private Character owner;
    private CombatSimulator simulator;

    /** Constructs Chain Breaker at refinement rank five. */
    public ChainBreaker() {
        this(5);
    }

    /** Constructs Chain Breaker at the selected refinement rank. */
    public ChainBreaker(int refinement) {
        super("Chain Breaker", new StatsContainer());
        if (refinement < 1 || refinement > 5) {
            throw new IllegalArgumentException(
                    "Chain Breaker refinement must be between 1 and 5");
        }
        this.refinement = refinement;
        weaponType = WeaponType.BOW;
        getStats().set(StatType.BASE_ATK, 565.0);
        getStats().set(StatType.ATK_PERCENT, 0.276);
    }

    /** Returns the selected refinement rank. */
    public int getRefinement() {
        return refinement;
    }

    /**
     * Returns whether one member satisfies the passive's union condition.
     * UNKNOWN regions never satisfy the Natlan side of the union.
     */
    public static boolean isQualifyingMember(
            CharacterRegion region,
            Element ownerElement,
            Element memberElement) {
        boolean isNatlan = region == CharacterRegion.NATLAN;
        boolean hasDifferentElement = ownerElement != null
                && memberElement != null
                && ownerElement != memberElement;
        return isNatlan || hasDifferentElement;
    }

    /** Returns the capped number of party members in the passive union. */
    public int getQualifyingMemberCount() {
        if (simulator == null || owner == null) {
            return 0;
        }
        int count = 0;
        for (Character member : simulator.getPartyMembers()) {
            if (isQualifyingMember(
                    member.getCharacterId().getRegion(),
                    owner.getElement(),
                    member.getElement())) {
                count++;
            }
        }
        return Math.min(count, MAX_MEMBERS);
    }

    /** Binds the composition lookup to one equipped owner and simulator. */
    @Override
    public void initializeForSimulator(
            Character equippedOwner,
            CombatSimulator sim) {
        if (equippedOwner == null || sim == null) {
            throw new IllegalArgumentException(
                    "Chain Breaker owner and simulator are required");
        }
        if (equippedOwner.getWeapon() != this) {
            throw new IllegalArgumentException(
                    "Chain Breaker owner must have this weapon equipped");
        }
        if (simulator != null) {
            if (owner != equippedOwner || simulator != sim) {
                throw new IllegalStateException(
                        "Chain Breaker is already bound to another simulator");
            }
            return;
        }
        owner = equippedOwner;
        simulator = sim;
    }

    /** Applies ATK per qualifying member and EM at three or more members. */
    @Override
    public void applyPassive(StatsContainer stats, double currentTime) {
        int count = getQualifyingMemberCount();
        stats.add(StatType.ATK_PERCENT,
                count * (0.036 + 0.012 * refinement));
        if (count >= 3) {
            stats.add(StatType.ELEMENTAL_MASTERY, 18.0 + 6.0 * refinement);
        }
    }
}

package model.weapon;

import model.entity.Character;
import model.entity.SimulatorInitializedWeaponEffect;
import model.entity.Weapon;
import model.stats.StatsContainer;
import model.type.Element;
import model.type.StatType;
import model.type.WeaponType;
import simulation.CombatSimulator;

/**
 * Jade Vista with its refinement-aware party-element composition passive.
 *
 * <p>Lv. 90 metadata and R1-R5 values follow Genshin Optimizer commit
 * {@code d791814a}. Up to three teammates are counted, excluding the owner.
 * Same-element teammates grant Elemental Mastery and consume the shared cap
 * before different-element teammates, which grant ATK.</p>
 */
public final class JadeVista extends Weapon
        implements SimulatorInitializedWeaponEffect {
    private static final int MAX_STACKS = 3;

    private final int refinement;
    private final double elementalMasteryPerStack;
    private final double attackBonusPerStack;
    private Character owner;
    private CombatSimulator simulator;

    /** Constructs Jade Vista at refinement rank five. */
    public JadeVista() {
        this(5);
    }

    /**
     * Constructs Jade Vista at a selected refinement.
     *
     * @param refinement refinement rank in the inclusive range 1-5
     */
    public JadeVista(int refinement) {
        super("Jade Vista", new StatsContainer());
        validateRefinement(refinement);
        this.refinement = refinement;
        elementalMasteryPerStack = 48.0 + 16.0 * refinement;
        attackBonusPerStack = 0.09 + 0.03 * refinement;
        weaponType = WeaponType.BOW;
        getStats().set(StatType.BASE_ATK, 510.0);
        getStats().set(StatType.CRIT_RATE, 0.276);
    }

    /** Returns the selected refinement rank. */
    public int getRefinement() {
        return refinement;
    }

    /** Returns the Elemental Mastery granted by one same-element stack. */
    public double getElementalMasteryPerStack() {
        return elementalMasteryPerStack;
    }

    /** Returns the ATK bonus granted by one different-element stack. */
    public double getAttackBonusPerStack() {
        return attackBonusPerStack;
    }

    /** Returns the capped same-element stack count, evaluated first. */
    public int getSameElementStackCount() {
        return getStackCounts()[0];
    }

    /** Returns the remaining different-element stack count. */
    public int getDifferentElementStackCount() {
        return getStackCounts()[1];
    }

    /** Binds the composition lookup to one equipped owner and simulator. */
    @Override
    public void initializeForSimulator(
            Character equippedOwner,
            CombatSimulator activeSimulator) {
        if (equippedOwner == null || activeSimulator == null) {
            throw new IllegalArgumentException(
                    "Jade Vista owner and simulator are required");
        }
        if (simulator != null) {
            if (owner != equippedOwner || simulator != activeSimulator) {
                throw new IllegalStateException(
                        "Jade Vista is already bound to another simulator");
            }
            return;
        }
        if (equippedOwner.getWeapon() != this) {
            throw new IllegalArgumentException(
                    "Jade Vista owner must have this weapon equipped");
        }
        if (!activeSimulator.getPartyMembers().contains(equippedOwner)) {
            throw new IllegalArgumentException(
                    "Jade Vista owner must belong to the simulator party");
        }
        owner = equippedOwner;
        simulator = activeSimulator;
    }

    /** Applies same-element EM stacks before remaining different-element ATK. */
    @Override
    public void applyPassive(StatsContainer stats, double currentTime) {
        int[] stackCounts = getStackCounts();
        stats.add(StatType.ELEMENTAL_MASTERY,
                stackCounts[0] * elementalMasteryPerStack);
        stats.add(StatType.ATK_PERCENT,
                stackCounts[1] * attackBonusPerStack);
    }

    private int[] getStackCounts() {
        if (simulator == null || owner == null || owner.getElement() == null) {
            return new int[] { 0, 0 };
        }
        int sameElement = 0;
        int differentElement = 0;
        Element ownerElement = owner.getElement();
        for (Character member : simulator.getPartyMembers()) {
            if (member == owner || member.getElement() == null) {
                continue;
            }
            if (member.getElement() == ownerElement) {
                sameElement++;
            } else {
                differentElement++;
            }
        }
        int sameStacks = Math.min(sameElement, MAX_STACKS);
        int differentStacks = Math.min(
                differentElement, MAX_STACKS - sameStacks);
        return new int[] { sameStacks, differentStacks };
    }

    private static void validateRefinement(int refinement) {
        if (refinement < 1 || refinement > 5) {
            throw new IllegalArgumentException(
                    "Jade Vista refinement must be between 1 and 5");
        }
    }
}
